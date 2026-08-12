package mx.utng.sintonia.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
import mx.utng.sintonia.data.remote.YouTubeRepository
import mx.utng.sintonia.ui.screens.YouTubeVideo

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val jamendoRepo = JamendoRepository()
    private val spotifyRepository = SpotifyRepository()
    private val firebaseRepo = FirebaseRepository()
    private val radioRepo = RadioRepository()
    private val exoPlayer = ExoPlayer.Builder(application).build()
    private val spotifyPlayer = SpotifyPlayerManager(application)
    private val appContext: Context = application.applicationContext

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

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue

    private val _favorites = MutableStateFlow<List<Song>>(emptyList())
    val favorites: StateFlow<List<Song>> = _favorites

    // Con los otros repos
    private val youtubeRepo = YouTubeRepository()
    private val YOUTUBE_API_KEY = "AIzaSyAMi-MKr8r8eeaA_lMFaDeI1JoUmi5YulM"

    private val _youtubeVideos = MutableStateFlow<List<YouTubeVideo>>(emptyList())
    val youtubeVideos: StateFlow<List<YouTubeVideo>> = _youtubeVideos

    // ============ TV ============
    private val _playOnTv = MutableStateFlow(false)
    val playOnTv: StateFlow<Boolean> = _playOnTv

    /**
     * true  -> el audio real debe sonar en la TV (el cel NO debe reproducir localmente)
     * false -> el audio real debe sonar en el cel (comportamiento normal)
     */
    private fun isLocal(): Boolean = !_playOnTv.value

    /**
     * Cambia entre reproducir en el cel o en la TV.
     * - Al activar TV: pausa el audio local (Exo/Spotify) pero deja isPlaying=true en Firebase
     *   para que la TV arranque a reproducir desde ahí.
     * - Al desactivar TV: retoma el audio local desde donde se haya quedado el estado.
     */
    fun togglePlayOnTv() {
        val activandoTv = !_playOnTv.value
        _playOnTv.value = activandoTv
        firebaseRepo.updatePlayOnTv(activandoTv)

        if (_playbackState.value.source == "spotify") {
            // Spotify Connect mueve la reproducción él solo entre dispositivos;
            // no tocamos exoPlayer ni spotifyPlayer.pause/resume aquí.
            transferSpotifyPlayback(toTv = activandoTv)
            return
        }

        if (activandoTv) {
            // Silenciar el cel, el audio ahora "vive" en la TV
            exoPlayer.pause()
        } else {
            // Recuperar el audio en el cel si se supone que estaba sonando
            if (_playbackState.value.isPlaying) {
                when (_playbackState.value.source) {
                    "radio" -> {
                        val streamUrl = _playbackState.value.currentSong.audioUrl
                        exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                    }
                    else -> {
                        // Si el reproductor no tenía nada cargado (p.ej. la canción cambió
                        // mientras estabas en modo TV), lo recargamos desde audioUrl
                        if (exoPlayer.currentMediaItem == null) {
                            exoPlayer.setMediaItem(
                                MediaItem.fromUri(_playbackState.value.currentSong.audioUrl)
                            )
                            exoPlayer.prepare()
                        }
                        exoPlayer.playWhenReady = true
                        exoPlayer.play()
                    }
                }
            }
        }
    }

    /**
     * Busca los dispositivos donde el usuario tiene Spotify abierto y transfiere
     * la reproducción a la TV (toTv=true) o de regreso al cel (toTv=false).
     *
     * El matching por "type" es lo estándar que regresa Spotify (Smartphone, TV,
     * Computer, Speaker, AVR...). Si tu TV aparece con otro type/name distinto,
     * revisa el log "SpotifyRepository" -> "Dispositivos encontrados" y ajusta
     * el filtro de abajo.
     */
    private fun transferSpotifyPlayback(toTv: Boolean) {
        val token = _spotifyToken.value ?: run {
            Log.e("PlayerViewModel", "No hay token de Spotify, no se puede transferir")
            return
        }
        viewModelScope.launch {
            val devices = spotifyRepository.getAvailableDevices(token)
            if (devices.isEmpty()) {
                Log.e("PlayerViewModel", "Spotify no regresó ningún dispositivo disponible")
                return@launch
            }

            val target = if (toTv) {
                devices.firstOrNull { it.type.equals("TV", ignoreCase = true) }
                    ?: devices.firstOrNull { it.name.contains("TV", ignoreCase = true) }
            } else {
                devices.firstOrNull { it.type.equals("Smartphone", ignoreCase = true) }
                    ?: devices.firstOrNull { !it.type.equals("TV", ignoreCase = true) }
            }

            if (target?.id == null) {
                Log.e(
                    "PlayerViewModel",
                    "No se encontró dispositivo ${if (toTv) "de TV" else "del cel"} entre: " +
                            devices.joinToString { "${it.name} (${it.type})" }
                )
                return@launch
            }

            val ok = spotifyRepository.transferPlayback(
                token = token,
                deviceId = target.id,
                play = _playbackState.value.isPlaying
            )
            Log.d("PlayerViewModel", "Transferencia a '${target.name}' exitosa=$ok")
        }
    }
    // ============ FIN TV ============

    fun searchYouTubeVideos(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _youtubeVideos.value = youtubeRepo.searchVideos(query, YOUTUBE_API_KEY)
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error YouTube: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
    init {
        val savedToken = prefs.getString("66f7b9f9a86343ca966251fde4b8bbca", null)
        if (!savedToken.isNullOrEmpty()) {
            _spotifyToken.value = savedToken
            spotifyPlayer.connect()
        }
        loadPopularTracks()
        listenForWearCommands()
        listenForDownloads()
        listenForFavorites()
        trackProgress()
        observeSpotifyState()
        setupExoPlayerListener()
        listenForTvCommands()
        listenForPlayOnTvFromFirebase()
    }

    /**
     * Firebase es la fuente de verdad de playOnTv. Sin esto, si cierras la app
     * con el modo TV activado, al reabrir el cel arranca creyendo que está en
     * false mientras Firebase sigue en true -> termina sonando en los dos lados.
     */
    private fun listenForPlayOnTvFromFirebase() {
        FirebaseDatabase.getInstance().reference
            .child("playback").child("playOnTv")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val valorRemoto = snapshot.getValue(Boolean::class.java) ?: false
                    if (valorRemoto != _playOnTv.value) {
                        _playOnTv.value = valorRemoto
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun listenForTvCommands() {
        FirebaseDatabase.getInstance().reference
            .child("playback").child("tvCommand")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val command = snapshot.getValue(String::class.java) ?: return
                    when (command) {
                        "pause" -> {
                            when (_playbackState.value.source) {
                                "spotify" -> spotifyPlayer.pause()
                                "radio" -> exoPlayer.stop()
                                else -> exoPlayer.pause()
                            }
                            _playbackState.value = _playbackState.value.copy(isPlaying = false)
                            firebaseRepo.updateIsPlaying(false)
                            snapshot.ref.setValue(null)
                        }
                        "play" -> {
                            when (_playbackState.value.source) {
                                "spotify" -> spotifyPlayer.resume()
                                "radio" -> {
                                    val streamUrl = _playbackState.value.currentSong.audioUrl
                                    exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
                                    exoPlayer.prepare()
                                    exoPlayer.playWhenReady = true
                                }
                                else -> exoPlayer.play()
                            }
                            _playbackState.value = _playbackState.value.copy(isPlaying = true)
                            firebaseRepo.updateIsPlaying(true)
                            snapshot.ref.setValue(null)
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun setupExoPlayerListener() {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    nextSong()
                }
            }
        })
    }

    private fun observeSpotifyState() {
        viewModelScope.launch {
            spotifyPlayer.isConnected.collect { connected ->
                _spotifyConnected.value = connected
            }
        }
        viewModelScope.launch {
            spotifyPlayer.progress.collect { progress ->
                _spotifyProgress.value = progress
                if (_playbackState.value.source == "spotify") {
                    firebaseRepo.updateProgress(progress)  // ← agrega esto
                }
            }
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
        viewModelScope.launch {
            spotifyPlayer.currentTrackName.collect { trackName ->
                if (trackName.isNotEmpty() && _playbackState.value.source == "spotify") {
                    if (trackName != _playbackState.value.currentSong.title) {
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
        viewModelScope.launch {
            spotifyPlayer.onTrackFinished.collect { finished ->
                if (finished && _playbackState.value.source == "spotify") {
                    spotifyPlayer.resetTrackFinished()
                    if (_queue.value.isNotEmpty()) {
                        val nextSong = _queue.value.first()
                        // Pequeño delay para que Spotify termine correctamente
                        delay(500)
                        playFromQueue(nextSong)
                    }
                }
            }
        }
        viewModelScope.launch {
            spotifyPlayer.onTrackChanged.collect { newUri ->
                if (newUri.isNotEmpty() && _playbackState.value.source == "spotify") {
                    val isInOurQueue = _queue.value.any { it.audioUrl == newUri }
                    val isCurrentSong = newUri == _playbackState.value.currentSong.audioUrl

                    // Si Spotify cambió a una canción que no está en nuestra cola
                    // y no es la canción actual, pausar y reproducir la nuestra
                    if (!isInOurQueue && !isCurrentSong && _queue.value.isNotEmpty()) {
                        delay(300)
                        val nextSong = _queue.value.first()
                        playFromQueue(nextSong)
                    }
                }
            }
        }
    }

    private fun trackProgress() {
        viewModelScope.launch {
            while (true) {
                if (exoPlayer.isPlaying && exoPlayer.duration > 0) {
                    val p = exoPlayer.currentPosition.toFloat() / exoPlayer.duration.toFloat()
                    _progress.value = p
                    firebaseRepo.updateProgress(p)
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
                        "next" -> {
                            when (_playbackState.value.source) {
                                "radio" -> nextRadioStation()
                                else -> nextSong()
                            }
                            snapshot.ref.setValue(null)
                        }
                        "previous" -> {
                            when (_playbackState.value.source) {
                                "radio" -> previousRadioStation()
                                else -> previousSong()
                            }
                            snapshot.ref.setValue(null)
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("WEAR_CMD", "Error: ${error.message}")
                }
            })
    }

    fun playYouTubeVideo(video: YouTubeVideo) {
        // ← Detener todo lo que esté sonando localmente
        if (_playbackState.value.source == "spotify") {
            spotifyPlayer.pause()
        }
        exoPlayer.stop()
        exoPlayer.clearMediaItems()

        val song = Song(
            id = video.id,
            title = video.title,
            artist = video.channel,
            albumCover = video.thumbnail,
            audioUrl = video.youtubeUrl,
            source = "youtube"
        )
        val newState = PlaybackState(
            isPlaying = true,
            currentSong = song,
            source = "youtube"
        )
        _playbackState.value = newState
        _currentSource.value = "youtube"
        firebaseRepo.updatePlaybackState(newState)
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
        stopAll()
        // Solo conectamos/reproducimos Spotify localmente si el audio vive en el cel
        if (isLocal()) {
            if (spotifyPlayer.isConnected.value) {
                spotifyPlayer.playSong(song.audioUrl)
                spotifyPlayer.clearSpotifyQueue()
            } else {
                spotifyPlayer.connect()
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(song.audioUrl)
                    putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://${context.packageName}"))
                }
                context.startActivity(intent)
            }
        }
        val newState = PlaybackState(isPlaying = true, currentSong = song, source = "spotify")
        _playbackState.value = newState
        _currentSource.value = "spotify"
        firebaseRepo.updatePlaybackState(newState)
    }

    fun connectSpotifyPlayer() { spotifyPlayer.connect() }
    fun disconnectSpotifyPlayer() { spotifyPlayer.disconnect() }

    private fun stopAll() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        if (spotifyPlayer.isConnected.value) {
            spotifyPlayer.pause()
        }
    }

    fun playSong(song: Song) {
        // ← Pausar Spotify si estaba activo
        if (_playbackState.value.source == "spotify") {
            spotifyPlayer.pause()
        }

        // Solo tocamos el ExoPlayer local si el audio debe sonar en el cel.
        // Si está en modo TV, nomás actualizamos el estado y la TV lo recoge.
        if (isLocal()) {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            val mediaItem = MediaItem.fromUri(song.audioUrl)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
        _progress.value = 0f

        val newState = PlaybackState(
            isPlaying = true,
            currentSong = song.copy(source = "jamendo"),
            source = "jamendo"
        )
        _playbackState.value = newState
        _currentSource.value = "jamendo"
        firebaseRepo.updatePlaybackState(newState)
    }

    fun playRadioStation(id: String, name: String, city: String, streamUrl: String) {
        stopAll() // ← detiene spotify y jamendo antes de reproducir radio

        if (isLocal()) {
            exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
        _progress.value = 0f

        val radioSong = Song(
            id = id, title = name, artist = city,
            albumCover = "", audioUrl = streamUrl, source = "radio"
        )
        val newState = PlaybackState(isPlaying = true, currentSong = radioSong, source = "radio")
        _playbackState.value = newState
        _currentSource.value = "radio"
        firebaseRepo.updatePlaybackState(newState)
    }

    fun addToQueue(song: Song) {
        if (_queue.value.none { it.id == song.id }) {
            _queue.value = _queue.value + song
            firebaseRepo.updateQueue(_queue.value)
        }
    }

    fun removeFromQueue(songId: String) {
        _queue.value = _queue.value.filter { it.id != songId }
        firebaseRepo.updateQueue(_queue.value)
    }

    fun clearQueue() {
        _queue.value = emptyList()
        firebaseRepo.updateQueue(emptyList())
    }


    fun playFromQueue(song: Song) {
        when (song.source) {
            "spotify" -> {
                stopAll()
                if (isLocal()) {
                    if (spotifyPlayer.isConnected.value) {
                        spotifyPlayer.playSong(song.audioUrl)
                    } else {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse(song.audioUrl)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra(
                                Intent.EXTRA_REFERRER,
                                Uri.parse("android-app://${appContext.packageName}")
                            )
                        }
                        appContext.startActivity(intent)
                    }
                }
                val newState = PlaybackState(
                    isPlaying = true, currentSong = song, source = "spotify"
                )
                _playbackState.value = newState
                _currentSource.value = "spotify"
                firebaseRepo.updatePlaybackState(newState)
            }
            "radio" -> {
                playRadioStation(song.id, song.title, song.artist, song.audioUrl)
            }
            else -> {
                // ← Si venía de Spotify, pausarlo primero
                if (_playbackState.value.source == "spotify") {
                    spotifyPlayer.pause()
                }

                if (isLocal()) {
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                    exoPlayer.setMediaItem(MediaItem.fromUri(song.audioUrl))
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                }
                _progress.value = 0f

                val newState = PlaybackState(
                    isPlaying = true,
                    currentSong = song,
                    source = song.source
                )
                _playbackState.value = newState
                _currentSource.value = song.source
                firebaseRepo.updatePlaybackState(newState)
            }
        }
        removeFromQueue(song.id)
    }

    fun toggleFavorite(song: Song) {
        // No mutamos _favorites directo: escribimos a Firebase y dejamos que
        // listenForFavorites() actualice el StateFlow cuando llegue el cambio.
        if (_favorites.value.any { it.id == song.id }) {
            firebaseRepo.removeFavorite(song.id)
        } else {
            firebaseRepo.saveFavorite(song)
        }
    }

    private fun listenForFavorites() {
        viewModelScope.launch {
            firebaseRepo.observeFavorites().collect { list ->
                _favorites.value = list
            }
        }
    }

    fun togglePlayPause() {
        val nuevoIsPlaying = !_playbackState.value.isPlaying

        if (isLocal()) {
            // Comportamiento normal: controla el audio en el cel
            if (_playbackState.value.source == "spotify") {
                if (nuevoIsPlaying) spotifyPlayer.resume() else spotifyPlayer.pause()
            } else if (_playbackState.value.source == "radio") {
                if (!nuevoIsPlaying) {
                    exoPlayer.stop()
                } else {
                    val streamUrl = _playbackState.value.currentSong.audioUrl
                    exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                }
            } else {
                if (nuevoIsPlaying) exoPlayer.play() else exoPlayer.pause()
            }
        }
        // Si NO es local (playOnTv = true), no tocamos exoPlayer/spotifyPlayer:
        // solo actualizamos el estado y la TV reacciona a este cambio en Firebase.

        _playbackState.value = _playbackState.value.copy(isPlaying = nuevoIsPlaying)
        firebaseRepo.updateIsPlaying(nuevoIsPlaying)
    }

    fun nextSong() {
        // ← Primero siempre revisa la cola, sin importar la fuente
        if (_queue.value.isNotEmpty()) {
            playFromQueue(_queue.value.first())
            return
        }

        // Solo si la cola está vacía, usa el control nativo de cada fuente
        if (_playbackState.value.source == "spotify") {
            if (isLocal()) spotifyPlayer.skipNext()
            return
        }

        val currentList = _songs.value
        if (currentList.isEmpty()) return
        val currentIndex = currentList.indexOfFirst {
            it.id == _playbackState.value.currentSong.id
        }
        if (currentIndex != -1) {
            playSong(currentList[(currentIndex + 1) % currentList.size])
        }
    }

    fun previousSong() {
        if (_playbackState.value.source == "spotify") {
            if (isLocal()) spotifyPlayer.skipPrevious()
            return
        }
        val currentList = _songs.value
        if (currentList.isEmpty()) return
        val currentIndex = currentList.indexOfFirst {
            it.id == _playbackState.value.currentSong.id
        }
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

    fun nextRadioStation() {
        val stationList = _radioStations.value
        if (stationList.isEmpty()) return
        val currentIndex = stationList.indexOfFirst { it.id == _playbackState.value.currentSong.id }
        val nextIndex = (currentIndex + 1) % stationList.size
        val next = stationList[nextIndex]
        playRadioStation(next.id, next.name, next.city, next.streamUrl)
    }

    fun previousRadioStation() {
        val stationList = _radioStations.value
        if (stationList.isEmpty()) return
        val currentIndex = stationList.indexOfFirst { it.id == _playbackState.value.currentSong.id }
        val prevIndex = if (currentIndex <= 0) stationList.size - 1 else currentIndex - 1
        val prev = stationList[prevIndex]
        playRadioStation(prev.id, prev.name, prev.city, prev.streamUrl)
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