package mx.utng.tv

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Modelo de estado del reproductor tal como lo necesita la TV.
 * Es una copia local (inmutable) de lo que existe en el nodo
 * "playback" de Firebase, pensada para pintar la UI de TvDashboardScreen.
 *
 * @param source fuente activa: "jamendo", "radio", "spotify" o "youtube"
 * @param isPlaying true si el audio/video se está reproduciendo
 * @param currentTitle título de la canción/video actual
 * @param currentArtist artista o canal actual
 * @param currentCoverUrl URL de la portada del álbum (o vacío si no hay)
 * @param volume volumen actual (0-100), reflejado desde el teléfono
 * @param queue lista de próximas canciones como pares (título, artista)
 * @param audioUrl URL directa del audio/stream a reproducir en la TV
 * @param duration duración de la pista en segundos
 * @param progress progreso de reproducción normalizado (0f a 1f)
 * @param playOnTv true si el teléfono decidió que el audio debe sonar en la TV
 *   (y no en el propio teléfono)
 */
data class TvPlayerState(
    val source: String = "jamendo",
    val isPlaying: Boolean = false,
    val currentTitle: String = "",
    val currentArtist: String = "",
    val currentCoverUrl: String = "",
    val volume: Int = 70,
    val queue: List<Pair<String, String>> = emptyList(),
    val audioUrl: String = "",
    val duration: Int = 0,
    val progress: Float = 0f,
    val playOnTv: Boolean = false
)

object FirebaseTvSync {
    private val playerRef = FirebaseDatabase.getInstance()
        .reference.child("playback")

    /**
     * Se suscribe al nodo "playback" de Firebase y emite un nuevo
     * TvPlayerState cada vez que cambia algo en el servidor.
     *
     * Por qué existe: la TV no tiene forma de saber qué está pasando en el
     * teléfono más que "escuchando" Firebase; este Flow es el puente entre
     * la base de datos y la UI de Compose (que lo consume con `collect`
     * dentro de un LaunchedEffect en TvDashboardScreen).
     *
     * Además de traducir el snapshot a un TvPlayerState, esta función decide
     * si debe iniciar o detener la reproducción de audio en la propia TV
     * (llamando a TvPlayer.play/stop) según los campos audioUrl, isPlaying
     * y playOnTv — es decir, aquí vive la regla de negocio de "cuándo debe
     * sonar el audio en la TV".
     *
     * @return Flow<TvPlayerState> que emite cada actualización del estado
     */
    fun observePlayerState(): Flow<TvPlayerState> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val queueSnapshot = snapshot.child("queue")
                    val queue = mutableListOf<Pair<String, String>>()
                    queueSnapshot.children.forEach { item ->
                        val title = item.child("title").getValue(String::class.java) ?: ""
                        val artist = item.child("artist").getValue(String::class.java) ?: ""
                        if (title.isNotEmpty()) queue.add(title to artist)
                    }

                    val playOnTv = snapshot.child("playOnTv").getValue(Boolean::class.java) ?: false
                    val isPlaying = snapshot.child("isPlaying").getValue(Boolean::class.java) ?: false
                    val audioUrl = snapshot.child("currentSong").child("audioUrl").getValue(String::class.java) ?: ""
                    val source = snapshot.child("source").getValue(String::class.java) ?: "jamendo"
                    val progress = snapshot.child("progress").getValue(Float::class.java) ?: 0f
                    val duration = snapshot.child("currentSong").child("duration")
                        .getValue(Int::class.java) ?: 0
                    // ← Agrega aquí los logs
                    android.util.Log.d("TV_PLAYER", "AudioUrl: $audioUrl")
                    android.util.Log.d("TV_PLAYER", "Source: $source")
                    android.util.Log.d("TV_PLAYER", "IsPlaying: $isPlaying")

                    // Reproducir en la TV según la fuente
                    if (audioUrl.isNotEmpty() && isPlaying && playOnTv) {
                        when (source) {
                            "jamendo", "radio" -> TvPlayer.play(audioUrl)
                            else -> TvPlayer.stop()
                        }
                    } else {
                        TvPlayer.stop()
                    }
                    val state = TvPlayerState(
                        source = source,
                        isPlaying = isPlaying,
                        currentTitle = snapshot.child("currentSong").child("title").getValue(String::class.java) ?: "",
                        currentArtist = snapshot.child("currentSong").child("artist").getValue(String::class.java) ?: "",
                        currentCoverUrl = snapshot.child("currentSong").child("albumCover").getValue(String::class.java) ?: "",
                        volume = snapshot.child("volume").getValue(Int::class.java) ?: 70,
                        queue = queue,
                        audioUrl = audioUrl,
                        duration = duration,
                        progress = progress
                    )
                    trySend(state)
                } catch (e: Exception) {
                    android.util.Log.e("FirebaseTvSync", "Error: ${e.message}")
                }
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        playerRef.addValueEventListener(listener)
        awaitClose { playerRef.removeEventListener(listener) }
    }

    // Comandos que el smartphone escucha
    /**
     * Envía al teléfono la orden de pausar o reanudar la reproducción,
     * escribiendo en playback/tvCommand.
     *
     * Por qué existe: la TV no controla el audio del teléfono directamente;
     * solo puede "pedirle" un cambio de estado a través de Firebase, y es
     * el teléfono quien realmente ejecuta la acción y actualiza `isPlaying`.
     *
     * @param isCurrentlyPlaying estado actual reportado por la TV; se usa
     *   para decidir si el comando a enviar es "pause" o "play" (se invierte)
     */
    fun sendPlayPause(isCurrentlyPlaying: Boolean) {
        FirebaseDatabase.getInstance().reference
            .child("playback").child("tvCommand").setValue(
                if (isCurrentlyPlaying) "pause" else "play"
            )
    }

    /**
     * Solicita al teléfono avanzar a la siguiente canción de la cola,
     * escribiendo "next" en playback/skipSong.
     *
     * Por qué existe: igual que sendPlayPause, la TV no controla la cola
     * directamente; delega la acción real en el teléfono, que es quien
     * gestiona la lista de reproducción.
     */
    fun sendSkipNext() {
        playerRef.child("skipSong").setValue("next")
    }

    /**
     * Solicita al teléfono retroceder a la canción anterior, escribiendo
     * "previous" en playback/skipSong.
     *
     * Por qué existe: análoga a sendSkipNext, pero en sentido contrario.
     */
    fun sendSkipPrevious() {
        playerRef.child("skipSong").setValue("previous")
    }
}