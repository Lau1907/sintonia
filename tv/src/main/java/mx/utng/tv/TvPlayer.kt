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