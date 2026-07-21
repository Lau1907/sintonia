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
import mx.utng.sintonia.data.remote.SpotifyPlayerManager
import mx.utng.sintonia.data.remote.SpotifyRepository
import mx.utng.sintonia.ui.screens.RadioStation

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val jamendoRepo = JamendoRepository()
    private val spotifyRepository = SpotifyRepository()
    private val firebaseRepo = FirebaseRepository()
    private val radioRepo = RadioRepository()
    private val exoPlayer = ExoPlayer.Builder(application).build()
    private val spotifyPlayer = SpotifyPlayerManager(application)

    private val prefs = application.getSharedPreferences("sintonia_spotify_prefs", Context.MODE_PRIVATE)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    private val _spotifySongs = MutableStateFlow<List<Song>>(emptyList())
    val spotifySongs: StateFlow<List<Song>> = _spotifySongs

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

    private val _spotifyProgress = MutableStateFlow(0f)
    val spotifyProgress: StateFlow<Float> = _spotifyProgress

    private val _spotifyDuration = MutableStateFlow(0L)
    val spotifyDuration: StateFlow<Long> = _spotifyDuration

    private val _spotifyConnected = MutableStateFlow(false)
    val spotifyConnected: StateFlow<Boolean> = _spotifyConnected

    init {
        val savedToken = prefs.getString("66f7b9f9a86343ca966251fde4b8bbca", null)
        if (!savedToken.isNullOrEmpty()) {
            _spotifyToken.value = savedToken
            spotifyPlayer.connect()
        }
        loadPopularTracks()
        listenForWearCommands()
        listenForDownloads()
        trackProgress()
        observeSpotifyState()
    }

    private fun observeSpotifyState() {
        viewModelScope.launch {
            spotifyPlayer.isConnected.collect { connected ->
                _spotifyConnected.value = connected
            }
        }
        viewModelScope.launch {
            spotifyPlayer.progress.collect { _spotifyProgress.value = it }
        }
        viewModelScope.launch {
            spotifyPlayer.duration.collect { _spotifyDuration.value = it }
        }
        viewModelScope.launch {
            spotifyPlayer.isPaused.collect { paused ->
                if (_playbackState.value.source == "spotify") {
                    _playbackState.value = _playbackState.value.copy(isPlaying = !paused)
                    firebaseRepo.updateIsPlaying(!paused)
                }
            }
        }
        // Escuchar cambio de canción automático
        viewModelScope.launch {
            spotifyPlayer.currentTrackName.collect { trackName ->
                if (trackName.isNotEmpty() && _playbackState.value.source == "spotify") {
                    val updatedSong = _playbackState.value.currentSong.copy(
                        title = trackName,
                        artist = spotifyPlayer.currentArtist.value,
                        audioUrl = spotifyPlayer.currentTrackUri.value,
                        albumCover = spotifyPlayer.currentAlbumCover.value
                    )
                    _playbackState.value = _playbackState.value.copy(currentSong = updatedSong)
                    firebaseRepo.updateCurrentSong(updatedSong)
                }
            }
        }
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
        FirebaseDatabase.getInstance().reference
            .child("playback").child("skipSong")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val value = snapshot.getValue(String::class.java)
                    when (value) {
                        "next" -> { nextSong(); snapshot.ref.setValue(null) }
                        "previous" -> { previousSong(); snapshot.ref.setValue(null) }
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("WEAR_CMD", "Error: ${error.message}")
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
                Log.e("RADIO", "Error cargando estaciones: ${e.message}")
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
                Log.e("RADIO", "Error búsqueda: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSpotifyToken(token: String) {
        _spotifyToken.value = token
        _currentSource.value = "spotify"
        prefs.edit().putString("66f7b9f9a86343ca966251fde4b8bbca", token).apply()
        spotifyPlayer.connect()
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _spotifySongs.value = spotifyRepository.getFeaturedTracks(token)
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error cargando featured: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logoutSpotify() {
        _spotifyToken.value = null
        _spotifySongs.value = emptyList()
        _currentSource.value = "jamendo"
        prefs.edit().remove("66f7b9f9a86343ca966251fde4b8bbca").apply()
        spotifyPlayer.disconnect()
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
                _spotifySongs.value = spotifyRepository.searchTracks(query, token)
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error buscando en Spotify: ${e.message}")
                _spotifySongs.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun playSongSpotify(song: Song, context: Context) {
        if (spotifyPlayer.isConnected.value) {
            spotifyPlayer.playSong(song.audioUrl)
            exoPlayer.stop()
        } else {
            spotifyPlayer.connect()
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(song.audioUrl)
                putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://${context.packageName}"))
            }
            context.startActivity(intent)
        }
        val newState = PlaybackState(isPlaying = true, currentSong = song, source = "spotify")
        _playbackState.value = newState
        firebaseRepo.updatePlaybackState(newState)
    }

    fun connectSpotifyPlayer() { spotifyPlayer.connect() }
    fun disconnectSpotifyPlayer() { spotifyPlayer.disconnect() }

    fun playSong(song: Song) {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        val mediaItem = MediaItem.fromUri(song.audioUrl)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        _progress.value = 0f

        val newState = PlaybackState(isPlaying = true, currentSong = song, source = "jamendo")
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

        val radioSong = Song(id = id, title = name, artist = city,
            albumCover = "", audioUrl = streamUrl, source = "radio")
        val newState = PlaybackState(isPlaying = true, currentSong = radioSong, source = "radio")
        _playbackState.value = newState
        firebaseRepo.updatePlaybackState(newState)
    }

    fun togglePlayPause() {
        if (_playbackState.value.source == "spotify") {
            if (_playbackState.value.isPlaying) {
                spotifyPlayer.pause()
            } else {
                spotifyPlayer.resume()
            }
            _playbackState.value = _playbackState.value.copy(
                isPlaying = !_playbackState.value.isPlaying
            )
            firebaseRepo.updateIsPlaying(_playbackState.value.isPlaying)
        } else {
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
    }

    fun nextSong() {
        if (_playbackState.value.source == "spotify") {
            spotifyPlayer.skipNext()
        } else {
            val currentList = _songs.value
            if (currentList.isEmpty()) return
            val currentIndex = currentList.indexOfFirst { it.id == _playbackState.value.currentSong.id }
            if (currentIndex != -1) {
                playSong(currentList[(currentIndex + 1) % currentList.size])
            }
        }
    }

    fun previousSong() {
        if (_playbackState.value.source == "spotify") {
            spotifyPlayer.skipPrevious()
        } else {
            val currentList = _songs.value
            if (currentList.isEmpty()) return
            val currentIndex = currentList.indexOfFirst { it.id == _playbackState.value.currentSong.id }
            if (currentIndex != -1) {
                val prevIndex = if (currentIndex <= 0) currentList.size - 1 else currentIndex - 1
                playSong(currentList[prevIndex])
            }
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

    fun cancelarDescarga(songId: String) { firebaseRepo.removeDownload(songId) }
    fun eliminarDescarga(songId: String) { firebaseRepo.removeDownload(songId) }
    fun setSource(source: String) { _currentSource.value = source }
    fun getPorcentajeUso(): Float = (_storageUsedMb.value / storageTotalMb) * 100f

    override fun onCleared() {
        super.onCleared()
        exoPlayer.release()
        spotifyPlayer.disconnect()
    }
}