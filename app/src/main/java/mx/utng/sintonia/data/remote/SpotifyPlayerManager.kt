package mx.utng.sintonia.data.remote

import android.content.Context
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SpotifyPlayerManager(private val context: Context) {

    private var spotifyAppRemote: SpotifyAppRemote? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    private var lastPlaybackPosition = 0L
    private var lastEventTime = 0L
    private var lastTrackUri = ""

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _currentTrackName = MutableStateFlow("")
    val currentTrackName: StateFlow<String> = _currentTrackName

    private val _currentArtist = MutableStateFlow("")
    val currentArtist: StateFlow<String> = _currentArtist

    private val _currentTrackUri = MutableStateFlow("")
    val currentTrackUri: StateFlow<String> = _currentTrackUri

    private val _currentAlbumCover = MutableStateFlow("")
    val currentAlbumCover: StateFlow<String> = _currentAlbumCover

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused

    private val _onTrackChanged = MutableStateFlow("")
    val onTrackChanged: StateFlow<String> = _onTrackChanged

    private val _onTrackFinished = MutableStateFlow(false)
    val onTrackFinished: StateFlow<Boolean> = _onTrackFinished

    fun connect() {
        val connectionParams = ConnectionParams.Builder(SpotifyAuthManager.CLIENT_ID)
            .setRedirectUri(SpotifyAuthManager.REDIRECT_URI)
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(context, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
                _isConnected.value = true
                Log.d("SpotifyPlayer", "Conectado a Spotify App Remote")

                appRemote.playerApi.subscribeToPlayerState().setEventCallback { state ->
                    val trackName = state.track?.name ?: ""
                    val artist = state.track?.artist?.name ?: ""
                    val uri = state.track?.uri ?: ""
                    val dur = state.track?.duration ?: 0L
                    val isPaused = state.isPaused

                    // Detectar cambio de canción
                    if (uri.isNotEmpty() && uri != lastTrackUri) {
                        lastTrackUri = uri
                        _onTrackChanged.value = uri
                        // Resetear finished al cambiar canción
                        _onTrackFinished.value = false
                    }

                    _currentArtist.value = artist
                    _currentTrackUri.value = uri
                    _duration.value = dur
                    _isPaused.value = isPaused

                    lastPlaybackPosition = state.playbackPosition
                    lastEventTime = System.currentTimeMillis()

                    if (dur > 0) {
                        _progress.value = lastPlaybackPosition.toFloat() / dur.toFloat()
                    }

                    state.track?.imageUri?.let { imageUri ->
                        appRemote.imagesApi.getImage(imageUri).setResultCallback { _ ->
                            _currentAlbumCover.value = imageUri.raw ?: ""
                        }
                    }

                    _currentTrackName.value = trackName

                    progressJob?.cancel()
                    if (!isPaused && dur > 0) {
                        startProgressTimer(dur)
                    }
                }
            }

            override fun onFailure(throwable: Throwable) {
                _isConnected.value = false
                Log.e("SpotifyPlayer", "Error conectando: ${throwable.message}")
            }
        })
    }

    private fun startProgressTimer(duration: Long) {
        progressJob = scope.launch {
            while (isActive) {
                delay(500)
                if (!_isPaused.value && duration > 0) {
                    val elapsed = System.currentTimeMillis() - lastEventTime
                    val estimatedPosition = lastPlaybackPosition + elapsed
                    val progress = (estimatedPosition.toFloat() / duration.toFloat())
                        .coerceIn(0f, 1f)
                    _progress.value = progress

                    // Detectar cuando la canción está terminando (98%)
                    if (progress >= 0.98f && !_onTrackFinished.value) {
                        _onTrackFinished.value = true
                        progressJob?.cancel()
                    }
                }
            }
        }
    }

    fun resetTrackFinished() {
        _onTrackFinished.value = false
    }

    fun addToQueue(spotifyUri: String) {
        spotifyAppRemote?.playerApi?.queue(spotifyUri)
            ?: Log.e("SpotifyPlayer", "No conectado a Spotify")
    }

    fun playSong(spotifyUri: String) {
        spotifyAppRemote?.playerApi?.play(spotifyUri)
            ?: Log.e("SpotifyPlayer", "No conectado a Spotify")
    }

    fun skipNext() {
        spotifyAppRemote?.playerApi?.skipNext()
    }

    fun skipPrevious() {
        spotifyAppRemote?.playerApi?.skipPrevious()
    }

    fun pause() {
        progressJob?.cancel()
        spotifyAppRemote?.playerApi?.pause()
    }

    fun resume() {
        spotifyAppRemote?.playerApi?.resume()
        val dur = _duration.value
        if (dur > 0) {
            lastEventTime = System.currentTimeMillis()
            startProgressTimer(dur)
        }
    }

    fun clearSpotifyQueue() {
        // Reproducir un silencio o track vacío para limpiar el contexto
        spotifyAppRemote?.playerApi?.setShuffle(false)
        spotifyAppRemote?.playerApi?.setRepeat(0) // 0 = no repeat
    }
    fun disconnect() {
        progressJob?.cancel()
        scope.cancel()
        SpotifyAppRemote.disconnect(spotifyAppRemote)
        _isConnected.value = false
    }
}