package mx.utng.sintonia.data.remote

import android.content.Context
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SpotifyPlayerManager(private val context: Context) {

    private var spotifyAppRemote: SpotifyAppRemote? = null

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
                    _currentTrackName.value = state.track?.name ?: ""
                    _currentArtist.value = state.track?.artist?.name ?: ""
                    _currentTrackUri.value = state.track?.uri ?: ""
                    _duration.value = state.track?.duration ?: 0L
                    _isPaused.value = state.isPaused

                    val dur = state.track?.duration ?: 0L
                    if (dur > 0) {
                        _progress.value = state.playbackPosition.toFloat() / dur.toFloat()
                    }

                    // Cargar imagen del álbum
                    state.track?.imageUri?.let { imageUri ->
                        appRemote.imagesApi.getImage(imageUri).setResultCallback { _ ->
                            _currentAlbumCover.value = imageUri.raw ?: ""
                        }
                    }
                }
            }

            override fun onFailure(throwable: Throwable) {
                _isConnected.value = false
                Log.e("SpotifyPlayer", "Error conectando: ${throwable.message}")
            }
        })
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
        spotifyAppRemote?.playerApi?.pause()
    }

    fun resume() {
        spotifyAppRemote?.playerApi?.resume()
    }

    fun disconnect() {
        SpotifyAppRemote.disconnect(spotifyAppRemote)
        _isConnected.value = false
    }
}