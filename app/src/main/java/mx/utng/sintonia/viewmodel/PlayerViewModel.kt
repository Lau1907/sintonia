package mx.utng.sintonia.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import mx.utng.sintonia.data.firebase.FirebaseRepository
import mx.utng.sintonia.data.model.PlaybackState
import mx.utng.sintonia.data.model.Song
import mx.utng.sintonia.data.remote.JamendoRepository
import mx.utng.sintonia.data.remote.SpotifyRepository

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val jamendoRepo = JamendoRepository()
    private val spotifyRepo = SpotifyRepository()
    private val firebaseRepo = FirebaseRepository()
    private val exoPlayer = ExoPlayer.Builder(application).build()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // --- Descargas (equivalente a GestorDescargas del modelo) ---
    private val _downloads = MutableStateFlow<List<Song>>(emptyList())
    val downloads: StateFlow<List<Song>> = _downloads

    private val _storageUsedMb = MutableStateFlow(0f)
    val storageUsedMb: StateFlow<Float> = _storageUsedMb

    val storageTotalMb = 1024f // 1 GB fijo para la demo

    private val _spotifyToken = MutableStateFlow<String?>(null)
    val spotifyToken: StateFlow<String?> = _spotifyToken

    private val _currentSource = MutableStateFlow("jamendo")
    val currentSource: StateFlow<String> = _currentSource

    init {
        loadPopularTracks()
        listenForWearCommands()
        listenForDownloads()
    }

    private fun listenForDownloads() {
        viewModelScope.launch {
            firebaseRepo.observeDownloads().collect { list ->
                _downloads.value = list
                _storageUsedMb.value = list
                    .filter { it.descargada }
                    .sumOf { it.tamanoMb.toDouble() }
                    .toFloat()
            }
        }
    }

    private fun listenForWearCommands() {
        android.util.Log.d("WEAR_CMD", "Registrando listener...")
        FirebaseDatabase.getInstance().reference
            .child("playback").child("skipSong")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val value = snapshot.getValue(String::class.java)
                    android.util.Log.d("WEAR_CMD", "Valor recibido: $value")
                    when (value) {
                        "next" -> {
                            nextSong()
                            snapshot.ref.setValue(null)
                        }
                        "previous" -> {
                            previousSong()
                            snapshot.ref.setValue(null)
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    android.util.Log.e("WEAR_CMD", "Error: ${error.message}")
                }
            })
    }

    fun loadPopularTracks() {
        viewModelScope.launch {
            _isLoading.value = true
            _songs.value = jamendoRepo.getPopularTracks()
            _isLoading.value = false
        }
    }

    fun searchTracks(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _songs.value = jamendoRepo.searchTracks(query)
            _isLoading.value = false
        }
    }

    fun setSpotifyToken(token: String) {
        _spotifyToken.value = token
        _currentSource.value = "spotify"
    }

    fun searchSpotifyTracks(query: String) {
        val token = _spotifyToken.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _songs.value = spotifyRepo.searchTracks(query, token)
            _isLoading.value = false
        }
    }

    fun playSongSpotify(song: Song, context: android.content.Context) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(song.audioUrl)
            putExtra(Intent.EXTRA_REFERRER,
                Uri.parse("android-app://${context.packageName}"))
        }
        context.startActivity(intent)

        val newState = PlaybackState(
            isPlaying = true,
            currentSong = song,
            source = "spotify"
        )
        _playbackState.value = newState
        firebaseRepo.updatePlaybackState(newState)
    }

    fun playSong(song: Song) {
        val mediaItem = MediaItem.fromUri(song.audioUrl)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()

        val newState = PlaybackState(
            isPlaying = true,
            currentSong = song,
            source = "jamendo"
        )
        _playbackState.value = newState
        firebaseRepo.updatePlaybackState(newState)
    }

    fun playRadioStation(id: String, name: String, city: String, streamUrl: String) {
        try {
            val mediaItem = MediaItem.fromUri(streamUrl)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        } catch (e: Exception) {
            android.util.Log.e("RADIO", "Error al reproducir: ${e.message}")
        }

        val radioSong = Song(
            id = id,
            title = name,
            artist = city,
            albumCover = "",
            audioUrl = streamUrl,
            source = "radio"
        )

        val newState = PlaybackState(
            isPlaying = true,
            currentSong = radioSong,
            source = "radio"
        )
        _playbackState.value = newState
        firebaseRepo.updatePlaybackState(newState)
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
            firebaseRepo.updateIsPlaying(false)
            _playbackState.value = _playbackState.value.copy(isPlaying = false)
        } else {
            exoPlayer.play()
            firebaseRepo.updateIsPlaying(true)
            _playbackState.value = _playbackState.value.copy(isPlaying = true)
        }
    }

    fun nextSong() {
        val currentList = _songs.value
        if (currentList.isEmpty()) return
        val currentIndex = currentList.indexOfFirst {
            it.id == _playbackState.value.currentSong.id
        }
        val nextIndex = (currentIndex + 1) % currentList.size
        playSong(currentList[nextIndex])
    }

    fun previousSong() {
        val currentList = _songs.value
        if (currentList.isEmpty()) return
        val currentIndex = currentList.indexOfFirst {
            it.id == _playbackState.value.currentSong.id
        }
        val prevIndex = if (currentIndex <= 0) currentList.size - 1 else currentIndex - 1
        playSong(currentList[prevIndex])
    }

    /**
     * Simula el progreso de descarga y lo va guardando en Firebase
     * paso a paso. La pantalla de Descargas se actualiza sola gracias
     * al listener de listenForDownloads().
     */
    fun downloadSong(song: Song) {
        if (_downloads.value.any { it.id == song.id }) return

        viewModelScope.launch {
            firebaseRepo.saveDownload(song.copy(progresoDescarga = 0, descargada = false))

            for (progreso in 10..100 step 10) {
                delay(300)
                firebaseRepo.saveDownload(
                    song.copy(progresoDescarga = progreso, descargada = progreso == 100)
                )
            }
        }
    }

    fun cancelarDescarga(songId: String) {
        firebaseRepo.removeDownload(songId)
    }

    fun eliminarDescarga(songId: String) {
        firebaseRepo.removeDownload(songId)
    }

    fun setSource(source: String) {
        _currentSource.value = source
    }

    fun getPorcentajeUso(): Float = (_storageUsedMb.value / storageTotalMb) * 100f

    override fun onCleared() {
        super.onCleared()
        exoPlayer.release()
    }
}