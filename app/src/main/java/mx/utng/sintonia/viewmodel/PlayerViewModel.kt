package mx.utng.sintonia.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
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
import mx.utng.sintonia.data.remote.RadioRepository
import mx.utng.sintonia.data.remote.SpotifyRepository
import mx.utng.sintonia.ui.screens.RadioStation

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val jamendoRepo = JamendoRepository()
    private val spotifyRepository = SpotifyRepository()
    private val firebaseRepo = FirebaseRepository()
    private val radioRepo = RadioRepository()
    private val exoPlayer = ExoPlayer.Builder(application).build()

    // Manejo de preferencias para guardar el token de Spotify localmente
    private val prefs = application.getSharedPreferences("sintonia_spotify_prefs", Context.MODE_PRIVATE)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _downloads = MutableStateFlow<List<Song>>(emptyList())
    val downloads: StateFlow<List<Song>> = _downloads

    private val _storageUsedMb = MutableStateFlow(0f)
    val storageUsedMb: StateFlow<Float> = _storageUsedMb

    val storageTotalMb = 1024f

    private val _spotifyToken = MutableStateFlow<String?>(null)
    val spotifyToken: StateFlow<String?> = _spotifyToken

    private val _currentSource = MutableStateFlow("jamendo")
    val currentSource: StateFlow<String> = _currentSource

    private val _radioStations = MutableStateFlow<List<RadioStation>>(emptyList())
    val radioStations: StateFlow<List<RadioStation>> = _radioStations

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    init {
        // Cargar token guardado de Spotify si existe
        val savedToken = prefs.getString("66f7b9f9a86343ca966251fde4b8bbca", null)
        if (!savedToken.isNullOrEmpty()) {
            _spotifyToken.value = savedToken
        }

        loadPopularTracks()
        listenForWearCommands()
        listenForDownloads()
        trackProgress()
    }

    private fun trackProgress() {
        viewModelScope.launch {
            while (true) {
                if (exoPlayer.isPlaying && exoPlayer.duration > 0) {
                    _progress.value = exoPlayer.currentPosition.toFloat() / exoPlayer.duration.toFloat()
                }
                delay(500)
            }
        }
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

    fun loadTopRadioStations() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _radioStations.value = radioRepo.getTopStations()
            } catch (e: Exception) {
                android.util.Log.e("RADIO", "Error cargando estaciones: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchRadioStations(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _radioStations.value = radioRepo.searchStations(query)
            } catch (e: Exception) {
                android.util.Log.e("RADIO", "Error búsqueda: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSpotifyToken(token: String) {
        _spotifyToken.value = token
        _currentSource.value = "spotify"
        // Guardar token en preferencias para persistencia entre ejecuciones
        prefs.edit().putString("66f7b9f9a86343ca966251fde4b8bbca", token).apply()
    }

    fun logoutSpotify() {
        _spotifyToken.value = null
        prefs.edit().remove("66f7b9f9a86343ca966251fde4b8bbca").apply()
    }

    fun searchSpotifyTracks(query: String) {
        val token = _spotifyToken.value
        if (query.isBlank() || token.isNullOrEmpty()) {
            Log.e("PlayerViewModel", "No se puede buscar: Query vacía o Token nulo")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Llamamos al repositorio
                val result = spotifyRepository.searchTracks(query, token)
                _songs.value = result
            } catch (e: Exception) {
                // Si algo sale mal (HTTP 400, sin internet, etc.), atrapamos el error aquí
                Log.e("PlayerViewModel", "Error buscando en Spotify desde ViewModel: ${e.message}")
                _songs.value = emptyList() // Limpiamos la lista para no mostrar basura
            } finally {
                // Se ejecuta SIEMPRE, haya error o éxito, quitando el icono de carga
                _isLoading.value = false
            }
        }
    }

    fun playSongSpotify(song: Song, context: Context) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(song.audioUrl)
            putExtra(
                Intent.EXTRA_REFERRER,
                Uri.parse("android-app://${context.packageName}")
            )
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
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        val mediaItem = MediaItem.fromUri(song.audioUrl)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        _progress.value = 0f

        val newState = PlaybackState(
            isPlaying = true,
            currentSong = song,
            source = "jamendo"
        )
        _playbackState.value = newState
        firebaseRepo.updatePlaybackState(newState)
    }

    fun playRadioStation(id: String, name: String, city: String, streamUrl: String) {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        _progress.value = 0f

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
        val currentSongId = _playbackState.value.currentSong?.id ?: return
        if (currentList.isEmpty()) return

        val currentIndex = currentList.indexOfFirst { it.id == currentSongId }
        if (currentIndex != -1) {
            val nextIndex = (currentIndex + 1) % currentList.size
            playSong(currentList[nextIndex])
        }
    }

    fun previousSong() {
        val currentList = _songs.value
        val currentSongId = _playbackState.value.currentSong?.id ?: return
        if (currentList.isEmpty()) return

        val currentIndex = currentList.indexOfFirst { it.id == currentSongId }
        if (currentIndex != -1) {
            val prevIndex = if (currentIndex <= 0) currentList.size - 1 else currentIndex - 1
            playSong(currentList[prevIndex])
        }
    }

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