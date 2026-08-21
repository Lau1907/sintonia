# Módulo `tv` — Sintonía (Android TV)

Dashboard de Android TV que refleja en tiempo real la reproducción que ocurre en el smartphone, y que además reproduce el audio directamente en la TV (fuentes Jamendo/Radio) cuando el teléfono así lo indica. Toda la sincronización se hace a través de **Firebase Realtime Database**, sobre el nodo `playback`.

---

## 📁 Estructura del módulo

```
tv/
├── build.gradle.kts                     #archivo de configuración de dependencias y build de este módulo
├── google-services.json                 #archivo de configuración de Firebase para este módulo
├── proguard-rules.pro                   #archivo de reglas de ofuscación/minificación para el build de release
└── src/main/
    ├── AndroidManifest.xml              #archivo que declara la Activity principal, permisos y tema de la app
    ├── java/mx/utng/tv/
    │   ├── FirebaseTvSync.kt            #archivo para la sincronización en tiempo real con Firebase (leer estado, enviar comandos)
    │   ├── MainActivity.kt              #archivo de la actividad principal (punto de entrada de la app)
    │   ├── TvPlayer.kt                  #archivo del reproductor de audio local de la TV (envoltura de ExoPlayer)
    │   └── TvDashboardScreen.kt         #archivo de toda la UI en Compose del dashboard (pantallas y componentes visuales)
    └── res/
        ├── mipmap-*/ic_launcher.webp    #archivos de ícono de la app en distintas resoluciones
        ├── values/strings.xml           #archivo de textos/strings de la app
        ├── values/themes.xml            #archivo de tema visual (Material) de la app
        └── xml/network_security_config.xml  #archivo de configuración de seguridad de red (permite tráfico necesario para streams)
```

Este README documenta los **4 archivos de código Kotlin** del módulo (los que contienen lógica y funciones): `FirebaseTvSync.kt`, `MainActivity.kt`, `TvPlayer.kt` y `TvDashboardScreen.kt`. El código de cada uno se muestra completo, tal como está en el proyecto, con la documentación (KDoc `/** */` y `@param`) insertada directamente arriba de cada función — así se lee todo en un solo bloque, sin cortes.

---

## `FirebaseTvSync.kt` — #archivo para la sincronización en tiempo real con Firebase

Objeto singleton encargado de **leer** el estado de reproducción desde Firebase y de **enviar comandos** (play/pause/skip) de vuelta hacia el smartphone. La TV nunca controla al teléfono directamente: solo escribe "peticiones" en la base de datos y el teléfono es quien las ejecuta.

```kotlin
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
```

> **Nota de limpieza sugerida:** dentro de `observePlayerState()` quedaron tres líneas de `android.util.Log.d(...)` marcadas con el comentario `// ← Agrega aquí los logs`. Son útiles para depurar, pero conviene quitarlas (o dejarlas detrás de un flag de debug) antes de la entrega final.

---

## `MainActivity.kt` — #archivo de la actividad principal (punto de entrada de la app)

```kotlin
package mx.utng.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Actividad principal (única) de la app de Android TV.
 * Es el punto de entrada del proceso.
 */
class MainActivity : ComponentActivity() {
    /**
     * Inicializa TvPlayer (crea el ExoPlayer) y define el contenido de la
     * pantalla con Compose: un Box de fondo oscuro que contiene
     * TvDashboardScreen, el único "screen" de este módulo (Android TV no
     * necesita navegación entre pantallas para este caso de uso).
     *
     * @param savedInstanceState estado previo de la actividad (estándar de Android)
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TvPlayer.initialize(this)
        setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0A0A0A))
                ) {
                    TvDashboardScreen()
                }
            }
        }
    }

    /**
     * Libera los recursos del reproductor (ExoPlayer) cuando la actividad
     * se destruye, para no dejar el reproductor de audio corriendo en
     * segundo plano ni fugar memoria.
     *
     * Por qué existe: ExoPlayer mantiene recursos nativos (decodificadores,
     * buffers); si no se llama a release(), pueden quedarse reservados aunque
     * la Activity ya no exista.
     */
    override fun onDestroy() {
        super.onDestroy()
        TvPlayer.release()
    }
}
```

---

## `TvPlayer.kt` — #archivo del reproductor de audio local de la TV

Objeto singleton que envuelve un **ExoPlayer** para reproducir el audio directamente en la Smart TV (fuentes Jamendo y Radio).

```kotlin
package mx.utng.tv

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Objeto singleton que envuelve un ExoPlayer para reproducir el audio
 * directamente en la Smart TV (usado para las fuentes Jamendo y Radio).
 */
object TvPlayer {

    private var exoPlayer: ExoPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    // TrustManager que acepta todos los certificados
    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    private val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, trustAllCerts, SecureRandom())
    }

    private val okHttpClient = OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
        .hostnameVerifier { _, _ -> true }
        .build()

    /**
     * Prepara el ExoPlayer si todavía no existe.
     *
     * Por qué existe: ExoPlayer necesita un Context de Android para
     * construirse, y solo debe crearse una vez (patrón singleton perezoso);
     * llamar dos veces a initialize() no debe crear un segundo reproductor.
     *
     * @param context contexto de Android usado para construir el ExoPlayer
     */
    fun initialize(context: Context) {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build()
        }
    }

    /**
     * Reproduce un audio a partir de una URL, reemplazando lo que se
     * estuviera reproduciendo antes.
     *
     * Por qué existe: es el único punto de entrada para "sonar algo" en
     * la TV. Detiene y limpia el media anterior, arma una fuente
     * ProgressiveMediaSource usando un cliente OkHttp configurado para
     * aceptar cualquier certificado SSL (necesario porque algunos streams
     * de radio usan certificados que el validador por defecto rechaza),
     * y arranca la reproducción automáticamente (playWhenReady = true).
     *
     * @param url URL directa del stream de audio a reproducir
     */
    fun play(url: String) {
        exoPlayer?.let { player ->
            player.stop()
            player.clearMediaItems()

            val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(url))

            player.setMediaSource(mediaSource)
            player.prepare()
            player.playWhenReady = true
            _isPlaying.value = true
        }
    }

    /**
     * Pausa la reproducción actual sin perder la posición ni la cola.
     *
     * Por qué existe: separar pause() de stop() permite reanudar
     * exactamente donde se quedó (resume()), en vez de tener que
     * recargar el media desde cero.
     */
    fun pause() {
        exoPlayer?.pause()
        _isPlaying.value = false
    }

    /**
     * Reanuda la reproducción desde donde se pausó.
     *
     * Por qué existe: complemento directo de pause(); usa el mismo
     * media ya cargado en el ExoPlayer.
     */
    fun resume() {
        exoPlayer?.play()
        _isPlaying.value = true
    }

    /**
     * Detiene por completo la reproducción actual.
     *
     * Por qué existe: a diferencia de pause(), se usa cuando ya no hay
     * nada que reproducir (por ejemplo, cuando Firebase indica que la
     * fuente activa no debe sonar en la TV) — deja al reproductor listo
     * para un nuevo play() en vez de simplemente en pausa.
     */
    fun stop() {
        exoPlayer?.stop()
        _isPlaying.value = false
    }

    /**
     * Calcula el progreso actual de reproducción como fracción (0f–1f).
     *
     * Por qué existe: la UI (TvDashboardScreen) necesita pintar una barra
     * de progreso; esta función traduce currentPosition/duration del
     * ExoPlayer a un valor normalizado fácil de usar en Compose.
     *
     * @return progreso entre 0f y 1f, o 0f si no hay reproductor o
     *   la duración es desconocida (<= 0)
     */
    fun getProgress(): Float {
        val player = exoPlayer ?: return 0f
        if (player.duration <= 0) return 0f
        return player.currentPosition.toFloat() / player.duration.toFloat()
    }

    /**
     * Devuelve la duración total del audio actual en milisegundos.
     *
     * Por qué existe: junto con getProgress(), permite calcular tiempos
     * "transcurrido / total" para mostrarlos en pantalla.
     *
     * @return duración en ms, o 0L si no hay reproductor inicializado
     */
    fun getDuration(): Long {
        return exoPlayer?.duration ?: 0L
    }

    /**
     * Libera el ExoPlayer y limpia la referencia.
     *
     * Por qué existe: debe llamarse desde el ciclo de vida de la Activity
     * (onDestroy) para evitar fugas de memoria y procesos de audio
     * huérfanos; después de llamarla, initialize() puede volver a crear
     * un reproductor nuevo si la app se reanuda.
     */
    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }
}
```

---

## `TvDashboardScreen.kt` — #archivo de toda la UI en Compose del dashboard

Contiene toda la UI (Jetpack Compose) del dashboard: pantalla principal, layout de música, layout de YouTube (WebView embebido) y componentes visuales de apoyo (visualizador de radio, barra de progreso, indicadores de estado).

```kotlin
package mx.utng.tv

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

val TvBackground = Color(0xFF0A0A0A)
val TvSurface = Color(0xFF1A1A1A)
val TvGreen = Color(0xFF1DB954)
val TvPink = Color(0xFFE91E8C)
val TvBlue = Color(0xFF4A9EFF)
val TvRed = Color(0xFFFF0000)
val TvSubtext = Color(0xFF888888)

val youtubeQueue = listOf(
    Triple("Starboy", "The Weeknd", "4:05"),
    Triple("Save Your Tears", "The Weeknd", "3:35"),
    Triple("Heartless", "The Weeknd", "3:29"),
)

/**
 * Pantalla principal del dashboard de Android TV.
 *
 * Se suscribe a FirebaseTvSync.observePlayerState() para mantener el
 * estado (`state`) siempre actualizado, y además corre un temporizador
 * local (LaunchedEffect con delay(500)) que avanza `localProgress`
 * poco a poco entre actualizaciones de Firebase — esto evita que la
 * barra de progreso se vea "congelada" o con saltos bruscos mientras
 * se espera el siguiente evento del servidor.
 *
 * Según el estado, decide qué layout mostrar:
 * - Sin canción actual → mensaje de "esperando reproducción"
 * - source == "youtube" → TvYouTubeLayout
 * - cualquier otra fuente → TvMusicLayout
 */
@Composable
fun TvDashboardScreen() {
    var state by remember { mutableStateOf(TvPlayerState()) }
    var localProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        FirebaseTvSync.observePlayerState().collect { newState ->
            if (newState.currentTitle != state.currentTitle) {
                localProgress = 0f
            }
            state = newState
            localProgress = newState.progress
        }
    }

    // Timer local que avanza el progreso sin depender de Firebase
    LaunchedEffect(state.isPlaying, state.currentTitle) {
        while (state.isPlaying && state.duration > 0) {
            delay(500)
            val increment = 0.5f / state.duration.toFloat()
            localProgress = (localProgress + increment).coerceIn(0f, 1f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
    ) {
        if (state.currentCoverUrl.isNotEmpty()) {
            AsyncImage(
                model = state.currentCoverUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.06f
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            TvBackground.copy(alpha = 0.7f),
                            TvBackground
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "SINFONÍA — ANDROID TV DASHBOARD",
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvStatusDot(
                        label = state.source.replaceFirstChar { it.uppercase() },
                        color = when (state.source) {
                            "spotify" -> TvGreen
                            "radio" -> TvPink
                            "youtube" -> TvRed
                            else -> TvBlue
                        }
                    )
                    TvStatusDot(label = "Phone", color = Color.Cyan)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (state.currentTitle.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎵", fontSize = 72.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Esperando reproducción...",
                            color = TvSubtext, fontSize = 28.sp, fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Abre Sinfonía en tu teléfono para comenzar",
                            color = TvSubtext.copy(alpha = 0.5f), fontSize = 18.sp
                        )
                    }
                }
            } else if (state.source == "youtube") {
                TvYouTubeLayout(state = state)
            } else {
                TvMusicLayout(state = state, progress = localProgress)
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "Controlado desde: Smartphone · Firebase",
                    color = TvSubtext.copy(alpha = 0.3f), fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Layout principal para fuentes de audio "normales" (Jamendo, Spotify,
 * Radio): portada/visualizador a la izquierda, controles de
 * reproducción al centro y cola de reproducción a la derecha.
 *
 * Por qué existe: separa la lógica visual de música del resto de
 * TvDashboardScreen para que esta última solo decida "qué layout
 * mostrar" y no cómo se ve cada uno.
 *
 * @param state estado actual del reproductor (título, artista, cola, etc.)
 * @param progress progreso de reproducción (0f–1f) ya suavizado por el
 *   temporizador local de TvDashboardScreen
 */
@Composable
fun TvMusicLayout(state: TvPlayerState, progress: Float) {
    val accentColor = when (state.source) {
        "spotify" -> TvGreen
        "radio" -> TvPink
        else -> TvBlue
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Columna izquierda ─────────────────────────────────────────────────
        Column(
            modifier = Modifier.weight(1.5f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Portada
                if (state.source == "radio") {
                    TvRadioVisualizer(isPlaying = state.isPlaying)
                } else {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(TvSurface)
                    ) {
                        AsyncImage(
                            model = state.currentCoverUrl.ifEmpty { null },
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                // Info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = accentColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            when (state.source) {
                                "spotify" -> "● Spotify"
                                "radio" -> "● Radio en vivo"
                                else -> "● Jamendo"
                            },
                            color = accentColor, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Text(
                        state.currentTitle,
                        color = Color.White, fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 44.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        state.currentArtist,
                        color = TvSubtext, fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )

                    // Barra de progreso + tiempo
                    if (state.source == "radio") {
                        TvRadioProgressBar()
                    } else {
                        Column {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = accentColor,
                                trackColor = TvSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    formatTvTime((progress * state.duration * 1000L).toLong()),
                                    color = TvSubtext, fontSize = 12.sp
                                )
                                Text(
                                    formatTvTime(state.duration * 1000L),
                                    color = TvSubtext, fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Controles
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            color = TvSurface, shape = CircleShape,
                            modifier = Modifier
                                .size(44.dp)
                                .clickable { FirebaseTvSync.sendSkipPrevious() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("⏮", fontSize = 18.sp, color = TvSubtext)
                            }
                        }

                        Surface(
                            color = accentColor, shape = CircleShape,
                            modifier = Modifier
                                .size(52.dp)
                                .clickable { FirebaseTvSync.sendPlayPause(state.isPlaying) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    if (state.isPlaying) "⏸" else "▶",
                                    fontSize = 20.sp, color = Color.Black
                                )
                            }
                        }

                        Surface(
                            color = TvSurface, shape = CircleShape,
                            modifier = Modifier
                                .size(44.dp)
                                .clickable { FirebaseTvSync.sendSkipNext() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("⏭", fontSize = 18.sp, color = TvSubtext)
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (state.isPlaying) accentColor else TvSubtext)
                            )
                            Text(
                                if (state.isPlaying) "Reproduciendo" else "Pausado",
                                color = if (state.isPlaying) accentColor else TvSubtext,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // ── Columna derecha — Cola ────────────────────────────────────────────
        Column(
            modifier = Modifier.weight(0.8f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "COLA DE REPRODUCCIÓN",
                color = TvSubtext, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Canción actual
            Surface(
                color = accentColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            state.currentTitle,
                            color = accentColor, fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            state.currentArtist,
                            color = TvSubtext, fontSize = 12.sp, maxLines = 1
                        )
                    }
                    Text("▶", color = accentColor, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (state.queue.isEmpty()) {
                Text(
                    "No hay canciones en cola",
                    color = TvSubtext.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            } else {
                state.queue.forEach { (title, artist) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                title,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(artist, color = TvSubtext, fontSize = 11.sp)
                        }
                    }
                    HorizontalDivider(color = TvSurface, thickness = 0.5.dp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Controlado desde\nSmartphone · Firebase",
                color = TvSubtext.copy(alpha = 0.4f),
                fontSize = 11.sp, lineHeight = 16.sp
            )
        }
    }
}

/**
 * Convierte una duración en milisegundos a formato "m:ss" para
 * mostrarla en pantalla (por ejemplo, 125000L → "2:05").
 *
 * Por qué existe: es una utilidad de formato reutilizada en la barra
 * de progreso de TvMusicLayout; evita repetir el cálculo de
 * minutos/segundos en cada lugar donde se muestra un tiempo.
 *
 * @param ms duración en milisegundos
 * @return cadena con formato "minutos:segundos" (segundos con 2 dígitos),
 *   o "0:00" si ms es menor o igual a 0
 */
fun formatTvTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val min = totalSeconds / 60
    val sec = totalSeconds % 60
    return "%d:%02d".format(min, sec)
}

/**
 * Layout para la fuente "youtube": reproduce el video embebido en un
 * WebView (usando el reproductor oficial de YouTube vía iframe) y
 * muestra la cola de "a continuación" junto con datos del canal.
 *
 * Extrae el videoId a partir de audioUrl (quitando el prefijo de la URL
 * completa o el prefijo "youtube:") y arma la URL de embed con
 * autoplay habilitado. El WebView se configura con JavaScript activado
 * y sin requerir gesto del usuario para reproducir automáticamente.
 *
 * Por qué existe: YouTube no permite reproducir su contenido con
 * ExoPlayer directamente (términos de servicio), así que se usa un
 * WebView con el iframe oficial como alternativa legal.
 *
 * @param state estado actual del reproductor; se usa su audioUrl,
 *   currentTitle, currentArtist e isPlaying
 */
@Composable
fun TvYouTubeLayout(state: TvPlayerState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Column(
            modifier = Modifier.weight(1.5f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val videoId = state.audioUrl
                .removePrefix("https://www.youtube.com/watch?v=")
                .removePrefix("youtube:")
                .take(11)

            if (videoId.isNotEmpty()) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            webViewClient = WebViewClient()
                            webChromeClient = WebChromeClient()
                            settings.apply {
                                javaScriptEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                domStorageEnabled = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                userAgentString = "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36"
                            }
                            loadUrl("https://www.youtube.com/embed/$videoId?autoplay=1&controls=1")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TvSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Text("▶", color = TvRed, fontSize = 48.sp)
                }
            }

            Text(
                state.currentTitle, color = Color.White, fontSize = 22.sp,
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(state.currentArtist, color = TvSubtext, fontSize = 14.sp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = TvRed, shape = CircleShape,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { FirebaseTvSync.sendPlayPause(state.isPlaying) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            if (state.isPlaying) "⏸" else "▶",
                            fontSize = 20.sp, color = Color.White
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "A CONTINUACIÓN", color = TvSubtext, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            youtubeQueue.forEachIndexed { index, (title, channel, duration) ->
                Surface(
                    color = if (index == 0) TvRed.copy(alpha = 0.2f) else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                title,
                                color = if (index == 0) TvRed else Color.White,
                                fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text("$channel · $duration", color = TvSubtext, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Canal", color = TvSubtext, fontSize = 12.sp)
            Text(
                state.currentArtist, color = Color.White,
                fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
            Text("23.4M suscriptores", color = TvSubtext, fontSize = 12.sp)
        }
    }
}

/**
 * Pequeño indicador visual (punto de color + etiqueta) usado en el
 * encabezado del dashboard para mostrar, por ejemplo, la fuente activa
 * o el estado de conexión con el teléfono.
 *
 * Por qué existe: evita repetir la misma combinación de Box circular +
 * Text cada vez que se necesita un "status dot" en la UI.
 *
 * @param label texto que acompaña al punto de color
 * @param color color del punto indicador
 */
@Composable
fun TvStatusDot(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(label, color = Color.LightGray, fontSize = 14.sp)
    }
}

/**
 * Visualizador animado de barras (estilo ecualizador) que se muestra
 * en vez de una portada cuando la fuente activa es "radio" (la radio
 * no tiene portada de álbum).
 *
 * Las barras animan su altura de forma infinita y escalonada
 * (duración distinta por barra) mientras isPlaying es true; si está
 * en pausa, se muestra el texto "En pausa" en su lugar.
 *
 * @param isPlaying controla si la animación de barras corre o se
 *   reemplaza por el texto de pausa
 */
@Composable
fun TvRadioVisualizer(isPlaying: Boolean) {
    Box(
        modifier = Modifier
            .size(240.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(TvPink.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("📻", fontSize = 72.sp)
            Spacer(modifier = Modifier.height(16.dp))
            if (isPlaying) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(36.dp)
                ) {
                    repeat(10) { index ->
                        val infiniteTransition =
                            rememberInfiniteTransition(label = "bar$index")
                        val height by infiniteTransition.animateFloat(
                            initialValue = 0.2f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(
                                    durationMillis = 300 + (index * 70),
                                    easing = FastOutSlowInEasing
                                ),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "h$index"
                        )
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .fillMaxHeight(height)
                                .clip(RoundedCornerShape(3.dp))
                                .background(TvPink)
                        )
                    }
                }
            } else {
                Text("En pausa", color = TvPink, fontSize = 14.sp)
            }
        }
    }
}

/**
 * Barra de progreso indeterminada (loop continuo) usada específicamente
 * para la fuente "radio", donde no existe un progreso real (es un
 * stream en vivo, no una pista con duración fija).
 *
 * Por qué existe: TvMusicLayout usa una LinearProgressIndicator con
 * progreso real para canciones, pero la radio necesita una barra que
 * solo comunique "esto sigue sonando en vivo", de ahí la animación en
 * bucle en vez de un valor calculado.
 */
@Composable
fun TvRadioProgressBar() {
    val infiniteTransition = rememberInfiniteTransition(label = "radioProgress")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bar"
    )
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp)),
        color = TvPink,
        trackColor = TvSurface
    )
}
```

---

## 🔄 Resumen del flujo de datos del módulo TV

1. El teléfono escribe el estado de reproducción en `playback` (Firebase).
2. `FirebaseTvSync.observePlayerState()` escucha esos cambios y emite un `TvPlayerState`.
3. `TvDashboardScreen` pinta ese estado (`TvMusicLayout` o `TvYouTubeLayout` según la fuente).
4. Si `playOnTv` es `true` y hay `audioUrl`, `TvPlayer` reproduce el audio localmente con ExoPlayer.
5. Las interacciones del usuario en la TV (play/pausa, siguiente, anterior) **no reproducen nada por sí solas**: solo escriben comandos en Firebase a través de `sendPlayPause`, `sendSkipNext` y `sendSkipPrevious`, y es el teléfono quien procesa esos comandos y actualiza el estado real.
