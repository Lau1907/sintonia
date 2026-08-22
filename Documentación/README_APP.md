# Módulo `app` — Sintonía (Smartphone)

App de Android (Jetpack Compose) que actúa como el "cerebro" de todo el sistema Sintonía: reproduce música desde 4 fuentes distintas (Jamendo, Spotify, Radio en vivo y YouTube), y sincroniza todo el estado de reproducción, favoritos y descargas hacia **Firebase Realtime Database**, para que los módulos `tv` y `wear` puedan reaccionar en tiempo real.

---

## 📁 Estructura del módulo

```
app/
├── build.gradle.kts                                    #archivo de configuración de dependencias y build de este módulo
├── google-services.json                                #archivo de configuración de Firebase para este módulo
├── proguard-rules.pro                                  #archivo de reglas de ofuscación/minificación para el build de release
└── src/main/
    ├── AndroidManifest.xml                             #archivo que declara la Activity principal, permisos e intents (deep link de Spotify)
    └── java/mx/utng/sintonia/
        ├── MainActivity.kt                             #archivo de la actividad principal (punto de entrada y receptor del login de Spotify)
        ├── data/
        │   ├── firebase/
        │   │   └── FirebaseRepository.kt               #archivo de sincronización en tiempo real con Firebase (playback, descargas, favoritos)
        │   ├── model/
        │   │   ├── Song.kt                             #archivo del modelo universal de canción usado por toda la app
        │   │   ├── PlaybackState.kt                     #archivo del modelo de estado de reproducción sincronizado con Firebase
        │   │   ├── RadioBrowserStation.kt               #archivo del modelo crudo de estación tal como lo regresa la API de Radio Browser
        │   │   └── YouTubeModels.kt                     #archivo de los modelos de respuesta de la YouTube Data API v3
        │   └── remote/
        │       ├── JamendoApi.kt                        #archivo de la definición Retrofit de los endpoints de Jamendo
        │       ├── JamendoRepository.kt                 #archivo que envuelve JamendoApi y convierte resultados al modelo Song
        │       ├── RadioBrowserService.kt               #archivo de la definición Retrofit de los endpoints de Radio Browser
        │       ├── RadioRepository.kt                   #archivo que envuelve RadioBrowserService y convierte resultados a RadioStation
        │       ├── SpotifyApi.kt                        #archivo de la definición Retrofit de los endpoints REST de Spotify
        │       ├── SpotifyAuthManager.kt                #archivo de configuración del login OAuth de Spotify
        │       ├── SpotifyPlayerManager.kt              #archivo que envuelve el SDK de Spotify App Remote (control directo de la app de Spotify)
        │       ├── SpotifyRepository.kt                 #archivo que envuelve la API REST de Spotify (búsqueda, dispositivos, transferencia)
        │       ├── YouTubeApi.kt                        #archivo de la definición Retrofit del endpoint de búsqueda de YouTube
        │       └── YouTubeRepository.kt                 #archivo que envuelve YouTubeApi y convierte resultados al modelo YouTubeVideo
        ├── ui/
        │   ├── components/
        │   │   ├── PlayerBar.kt                         #archivo vacío / sin uso actualmente (solo declara el package)
        │   │   └── SongCard.kt                           #archivo vacío / sin uso actualmente (solo declara el package)
        │   ├── navigation/
        │   │   ├── Navigation.kt                        #archivo que define las 5 pestañas de la barra de navegación inferior
        │   │   └── AppNavigation.kt                      #archivo raíz de navegación: arma el Scaffold, la barra inferior y el NavHost
        │   ├── screens/
        │   │   ├── HomeScreen.kt                         #archivo de la pantalla de inicio (selector de fuente + reproduciendo ahora)
        │   │   ├── JamendoScreen.kt                      #archivo de la pantalla de búsqueda y descarga de música gratuita (Jamendo)
        │   │   ├── SpotifyScreen.kt                      #archivo de la pantalla de login y búsqueda de Spotify
        │   │   ├── RadioScreen.kt                        #archivo de la pantalla de radio en vivo (Radio Browser)
        │   │   ├── YouTubeScreen.kt                      #archivo de la pantalla de búsqueda de videos de YouTube
        │   │   ├── QueueScreen.kt                        #archivo de la pantalla de la cola de reproducción
        │   │   ├── Favoritesscreen.kt                    #archivo de la pantalla de canciones favoritas
        │   │   ├── DownloadsScreen.kt                    #archivo de la pantalla de descargas y almacenamiento usado
        │   │   └── SettingsScreen.kt                     #archivo de la pantalla de configuración
        │   └── theme/
        │       ├── Color.kt                              #archivo de la paleta de colores de la app
        │       ├── Theme.kt                               #archivo del tema global (MaterialTheme oscuro)
        │       └── Type.kt                                #archivo de la tipografía de la app
        └── viewmodel/
            └── PlayerViewModel.kt                        #archivo del ViewModel central: coordina las 4 fuentes de audio y toda la sincronización con Firebase
```

Este README documenta el código completo del módulo, con la documentación (KDoc `/** */` y `@param`) insertada directamente arriba de cada función — igual que los README de `tv` y `wear`. Se recorre en el mismo orden que la estructura de arriba.

---

## `MainActivity.kt` — #archivo de la actividad principal

```kotlin
package mx.utng.sintonia

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationResponse
import mx.utng.sintonia.data.remote.SpotifyAuthManager
import mx.utng.sintonia.ui.navigation.AppNavigation
import mx.utng.sintonia.ui.theme.SintoniaTheme
import mx.utng.sintonia.viewmodel.PlayerViewModel

/**
 * Actividad principal (única) de la app de smartphone.
 * Es el punto de entrada del proceso y quien recibe el resultado del
 * login de Spotify (que se hace en una Activity externa del SDK).
 */
class MainActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()

    /**
     * Crea el PlayerViewModel (compartido con AppNavigation y todas las
     * pantallas) y monta la UI con Compose dentro del tema SintoniaTheme.
     *
     * @param savedInstanceState estado previo de la actividad (estándar de Android)
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SintoniaTheme {
                AppNavigation(viewModel = viewModel)
            }
        }
    }

    /**
     * Recibe el resultado del login de Spotify (AuthorizationClient),
     * que corre en una Activity separada del SDK de Spotify.
     *
     * Por qué existe: el flujo de OAuth de Spotify no regresa el token
     * como un resultado normal de Compose; regresa aquí, en el ciclo de
     * vida clásico de Activity, y de aquí se lo pasamos al ViewModel con
     * setSpotifyToken() para que el resto de la app ya lo pueda usar.
     *
     * @param requestCode código de la petición; se compara contra
     *   SpotifyAuthManager.REQUEST_CODE para saber si es esta respuesta
     * @param resultCode código de resultado estándar de Android
     * @param data intent con la respuesta de autorización de Spotify
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SpotifyAuthManager.REQUEST_CODE) {
            val response = AuthorizationClient.getResponse(resultCode, data)
            when (response.type) {
                AuthorizationResponse.Type.TOKEN -> {
                    viewModel.setSpotifyToken(response.accessToken)
                }
                AuthorizationResponse.Type.ERROR -> {
                    android.util.Log.e("SPOTIFY", "Error: ${response.error}")
                }
                else -> {}
            }
        }
    }
}
```

---

## `data/firebase/FirebaseRepository.kt` — #archivo de sincronización en tiempo real con Firebase

```kotlin
package mx.utng.sintonia.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import mx.utng.sintonia.data.model.PlaybackState
import mx.utng.sintonia.data.model.Song
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Punto único de entrada y salida de datos hacia Firebase Realtime
 * Database desde el teléfono. Todo lo que TV y Wear terminan mostrando
 * pasa antes por aquí. Usa tres nodos: "playback", "descargas" y
 * "favoritos".
 */
class FirebaseRepository {
    private val db = FirebaseDatabase.getInstance().reference.child("playback")
    private val dbDescargas = FirebaseDatabase.getInstance().reference.child("descargas")
    private val dbFavoritos = FirebaseDatabase.getInstance().reference.child("favoritos")

    /**
     * Se suscribe en tiempo real al nodo "playback" y emite un nuevo
     * PlaybackState cada vez que cambia algo en el servidor (lo cual
     * puede pasar desde este mismo teléfono, desde la TV o desde el reloj).
     *
     * @return Flow<PlaybackState> que emite cada actualización del estado
     */
    fun observePlaybackState(): Flow<PlaybackState> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val state = snapshot.getValue(PlaybackState::class.java)
                state?.let { trySend(it) }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.addValueEventListener(listener)
        awaitClose { db.removeEventListener(listener) }
    }

    /**
     * ANTES: db.setValue(state) reemplazaba TODO el nodo "playback",
     * borrando playOnTv, queue, y cualquier otro campo que no forme
     * parte de PlaybackState. Ahora solo tocamos los campos que
     * realmente cambian, usando updateChildren en vez de setValue.
     */
    fun updatePlaybackState(state: PlaybackState) {
        val updates = mapOf<String, Any?>(
            "isPlaying" to state.isPlaying,
            "currentSong" to state.currentSong,
            "source" to state.source
        )
        db.updateChildren(updates)
    }

    /**
     * Sube a Firebase solo el título y artista de las primeras 3 canciones
     * de la cola (playback/queue), que es lo que la TV muestra como
     * "a continuación". No se manda la cola completa para no gastar de más.
     *
     * @param songs cola de reproducción completa del teléfono
     */
    fun updateQueue(songs: List<Song>) {
        val queueData = songs.take(3).mapIndexed { index, song ->
            mapOf(
                "title" to song.title,
                "artist" to song.artist
            )
        }
        FirebaseDatabase.getInstance().reference
            .child("playback").child("queue").setValue(queueData)
    }
    /** Actualiza solo el campo playback/isPlaying, sin tocar el resto del nodo. */
    fun updateIsPlaying(isPlaying: Boolean) {
        db.child("isPlaying").setValue(isPlaying)
    }

    /**
     * Actualiza playback/playOnTv, la bandera que decide si el audio
     * debe sonar en el teléfono o en la TV. Tanto la TV como el propio
     * teléfono escuchan este valor para decidir su comportamiento.
     */
    fun updatePlayOnTv(playOnTv: Boolean) {
        db.child("playOnTv").setValue(playOnTv)
    }
    /** Actualiza playback/progress (0f-1f) para que TV y Wear pinten la barra de progreso. */
    fun updateProgress(progress: Float) {
        db.child("progress").setValue(progress)
    }
    /** Reemplaza por completo playback/currentSong (se usa, por ejemplo, cuando Spotify reporta un cambio de canción). */
    fun updateCurrentSong(song: Song) {
        db.child("currentSong").setValue(song)
    }

    // --- Descargas (persistencia de GestorDescargas) ---

    /**
     * Se suscribe al nodo "descargas" y emite la lista completa de
     * canciones descargadas/en progreso cada vez que cambia.
     *
     * @return Flow<List<Song>> con todas las descargas actuales
     */
    fun observeDownloads(): Flow<List<Song>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val songs = snapshot.children.mapNotNull { it.getValue(Song::class.java) }
                trySend(songs)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        dbDescargas.addValueEventListener(listener)
        awaitClose { dbDescargas.removeEventListener(listener) }
    }

    /** Guarda o actualiza una canción dentro de "descargas" (se usa también para actualizar su progreso). */
    fun saveDownload(song: Song) {
        dbDescargas.child(song.id).setValue(song)
    }

    /** Elimina una canción del nodo "descargas" por su id. */
    fun removeDownload(songId: String) {
        dbDescargas.child(songId).removeValue()
    }

    // --- Favoritos ---

    /**
     * Se suscribe al nodo "favoritos" y emite la lista completa de
     * canciones marcadas como favoritas cada vez que cambia.
     *
     * @return Flow<List<Song>> con todos los favoritos actuales
     */
    fun observeFavorites(): Flow<List<Song>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val songs = snapshot.children.mapNotNull { it.getValue(Song::class.java) }
                trySend(songs)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        dbFavoritos.addValueEventListener(listener)
        awaitClose { dbFavoritos.removeEventListener(listener) }
    }

    /** Guarda una canción como favorita en Firebase. */
    fun saveFavorite(song: Song) {
        dbFavoritos.child(song.id).setValue(song)
    }

    /** Quita una canción de favoritos en Firebase por su id. */
    fun removeFavorite(songId: String) {
        dbFavoritos.child(songId).removeValue()
    }
}
```

---

## `data/model/Song.kt` — #archivo del modelo universal de canción

```kotlin
package mx.utng.sintonia.data.model

/**
 * Modelo universal de canción/pista usado por toda la app, sin importar
 * la fuente (Jamendo, Spotify, Radio o YouTube). El campo `source`
 * indica de dónde viene, y campos como `tamanoMb`/`progresoDescarga`/
 * `descargada` solo se usan cuando la canción se descarga (Jamendo).
 *
 * @param id identificador único de la canción según su fuente
 * @param title título de la canción
 * @param artist artista o canal
 * @param albumCover URL de la portada
 * @param audioUrl URL de audio directa, o uri de Spotify (spotify:track:...)
 * @param duration duración en segundos
 * @param source fuente: "jamendo", "spotify", "radio" o "youtube"
 * @param tamanoMb tamaño estimado en MB (solo relevante para descargas)
 * @param progresoDescarga porcentaje de descarga (0-100)
 * @param descargada true si la descarga ya se completó
 */
data class Song(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val albumCover: String = "",
    val audioUrl: String = "",
    val duration: Int = 0,
    val source: String = "jamendo",
    val tamanoMb: Float = 0f,
    val progresoDescarga: Int = 0,
    val descargada: Boolean = false
)
```

## `data/model/PlaybackState.kt` — #archivo del modelo de estado de reproducción

```kotlin
package mx.utng.sintonia.data.model

import com.google.firebase.database.PropertyName

/**
 * Estado de reproducción que se sincroniza completo con Firebase
 * (nodo "playback"). @PropertyName asegura que isPlaying se serialice
 * igual en Kotlin y en el JSON de Firebase.
 *
 * @param isPlaying true si hay reproducción activa
 * @param currentSong canción actualmente en reproducción
 * @param volume volumen (0-100)
 * @param source fuente activa: "jamendo", "spotify", "radio" o "youtube"
 */
data class PlaybackState(
    @get:PropertyName("isPlaying")
    @set:PropertyName("isPlaying")
    var isPlaying: Boolean = false,
    var currentSong: Song = Song(),
    var volume: Int = 70,
    var source: String = "jamendo"
)
```

## `data/model/RadioBrowserStation.kt` — #archivo del modelo crudo de estación de radio

```kotlin
package mx.utng.sintonia.data.model

/**
 * Modelo tal como lo regresa la API pública de Radio Browser
 * (https://api.radio-browser.info), antes de convertirlo al modelo
 * propio RadioStation que usa el resto de la app.
 *
 * @param stationuuid identificador único de la estación
 * @param name nombre de la estación
 * @param country país de origen
 * @param tags etiquetas/géneros separados por coma
 * @param url_resolved URL del stream ya resuelta (lista para reproducir)
 * @param favicon ícono de la estación
 * @param votes votos de popularidad de la comunidad
 */
data class RadioBrowserStation(
    val stationuuid: String = "",
    val name: String = "",
    val country: String = "",
    val tags: String = "",
    val url_resolved: String = "",
    val favicon: String = "",
    val votes: Int = 0
)
```

## `data/model/YouTubeModels.kt` — #archivo de los modelos de la YouTube Data API

```kotlin
package mx.utng.sintonia

import com.google.gson.annotations.SerializedName

/**
 * Modelos de respuesta de la YouTube Data API v3 usados para parsear
 * el JSON de resultados de búsqueda con Gson (@SerializedName mapea
 * el nombre exacto que usa la API de Google).
 */
data class YouTubeSearchResponse(
    @SerializedName("items") val items: List<SearchResultItem> = emptyList()
)

data class SearchResultItem(
    @SerializedName("id") val id: ItemId,
    @SerializedName("snippet") val snippet: Snippet
)

data class ItemId(
    @SerializedName("kind") val kind: String,
    @SerializedName("videoId") val videoId: String? = null
)

data class Snippet(
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("channelTitle") val channelTitle: String,
    @SerializedName("thumbnails") val thumbnails: Thumbnails
)

data class Thumbnails(
    @SerializedName("medium") val medium: ThumbnailDetails
)

data class ThumbnailDetails(
    @SerializedName("url") val url: String
)
```

---

## `data/remote/JamendoApi.kt` — #archivo de la definición Retrofit de Jamendo

```kotlin
package mx.utng.sintonia.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

data class JamendoResponse(val results: List<JamendoTrack> = emptyList())
data class JamendoTrack(
    val id: String = "",
    val name: String = "",
    val artist_name: String = "",
    val image: String = "",
    val audio: String = "",
    val duration: Int = 0
)

/**
 * Definición Retrofit de los dos endpoints que usamos de Jamendo:
 * búsqueda por texto y canciones populares. Los parámetros con valor
 * por defecto (client_id, format, audioformat) casi nunca se
 * sobreescriben desde donde se llama.
 */
interface JamendoApi {
    @GET("tracks/")
    suspend fun searchTracks(
        @Query("client_id") clientId: String = "dc3bc61a",
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 50,
        @Query("namesearch") search: String,
        @Query("audioformat") audioFormat: String = "mp32",
        @Query("include") include: String = "musicinfo"
    ): JamendoResponse

    @GET("tracks/")
    suspend fun getPopularTracks(
        @Query("client_id") clientId: String = "dc3bc61a",
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 50,
        @Query("order") order: String = "buzzrate",
        @Query("audioformat") audioFormat: String = "mp32"
    ): JamendoResponse
}
```

## `data/remote/JamendoRepository.kt` — #archivo que envuelve JamendoApi

```kotlin
package mx.utng.sintonia.data.remote

import mx.utng.sintonia.data.model.Song
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class JamendoRepository {
    private val api: JamendoApi = Retrofit.Builder()
        .baseUrl("https://api.jamendo.com/v3.0/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(JamendoApi::class.java)

    /**
     * Jamendo no regresa el tamaño del archivo en MB directamente,
     * así que lo estimamos a partir de la duración asumiendo el
     * bitrate típico de streaming de Jamendo (128 kbps).
     */
    private fun calcularTamanoMb(durationSeconds: Int, bitrateKbps: Int = 128): Float {
        return (durationSeconds * bitrateKbps) / (8f * 1024f)
    }

    /**
     * Pide a Jamendo las pistas más populares (order=buzzrate) y las
     * convierte al modelo Song de la app, ya con tamaño en MB estimado.
     *
     * @return lista de canciones, o lista vacía si falla la petición
     */
    suspend fun getPopularTracks(): List<Song> {
        return try {
            api.getPopularTracks().results.map { track ->
                Song(
                    id = track.id,
                    title = track.name,
                    artist = track.artist_name,
                    albumCover = track.image,
                    audioUrl = track.audio,
                    duration = track.duration,
                    source = "jamendo",
                    tamanoMb = calcularTamanoMb(track.duration)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Busca canciones en Jamendo por texto libre y las convierte a Song.
     *
     * @param query texto de búsqueda (nombre de canción, artista, etc.)
     * @return lista de resultados, o lista vacía si falla la petición
     */
    suspend fun searchTracks(query: String): List<Song> {
        return try {
            api.searchTracks(search = query).results.map { track ->
                Song(
                    id = track.id,
                    title = track.name,
                    artist = track.artist_name,
                    albumCover = track.image,
                    audioUrl = track.audio,
                    duration = track.duration,
                    source = "jamendo",
                    tamanoMb = calcularTamanoMb(track.duration)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
```

## `data/remote/RadioBrowserService.kt` — #archivo de la definición Retrofit de Radio Browser

```kotlin
package mx.utng.sintonia.data.remote

import mx.utng.sintonia.data.model.RadioBrowserStation
import retrofit2.http.GET
import retrofit2.http.Query

interface RadioBrowserService {

    // Estaciones populares
    @GET("json/stations/topvote/20")
    suspend fun getTopStations(): List<RadioBrowserStation>

    // Buscar por nombre o país
    @GET("json/stations/search")
    suspend fun searchStations(
        @Query("name") name: String = "",
        @Query("country") country: String = "",
        @Query("limit") limit: Int = 20,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("order") order: String = "votes"
    ): List<RadioBrowserStation>

    // Estaciones de México específicamente
    @GET("json/stations/bycountry/Mexico")
    suspend fun getMexicanStations(
        @Query("limit") limit: Int = 20,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("order") order: String = "votes"
    ): List<RadioBrowserStation>
}
```

## `data/remote/RadioRepository.kt` — #archivo que envuelve RadioBrowserService

```kotlin
package mx.utng.sintonia.data.remote

import mx.utng.sintonia.ui.screens.RadioStation
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Envuelve RadioBrowserService y convierte sus resultados (RadioBrowserStation)
 * al modelo RadioStation que usa el resto de la app, filtrando estaciones
 * sin URL de stream resuelta.
 */
class RadioRepository {

    private val service: RadioBrowserService by lazy {
        Retrofit.Builder()
            .baseUrl("https://de1.api.radio-browser.info/")
            .client(
                OkHttpClient.Builder()
                    .addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    })
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RadioBrowserService::class.java)
    }

    /**
     * Obtiene las 20 estaciones con más votos globalmente.
     *
     * @return lista de RadioStation con stream válido
     */
    suspend fun getTopStations(): List<RadioStation> =
        service.getTopStations()
            .filter { it.url_resolved.isNotEmpty() }
            .map {
                RadioStation(
                    id = it.stationuuid,
                    name = it.name,
                    city = it.country,
                    genre = it.tags.split(",").firstOrNull()?.trim() ?: "Radio",
                    streamUrl = it.url_resolved
                )
            }

    /**
     * Busca estaciones de radio por nombre.
     *
     * @param query texto de búsqueda (nombre de la estación)
     * @return lista de RadioStation con stream válido
     */
    suspend fun searchStations(query: String): List<RadioStation> =
        service.searchStations(name = query)
            .filter { it.url_resolved.isNotEmpty() }
            .map {
                RadioStation(
                    id = it.stationuuid,
                    name = it.name,
                    city = it.country,
                    genre = it.tags.split(",").firstOrNull()?.trim() ?: "Radio",
                    streamUrl = it.url_resolved
                )
            }
}
```

## `data/remote/SpotifyApi.kt` — #archivo de la definición Retrofit de Spotify

```kotlin
package mx.utng.sintonia.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Query

data class SpotifySearchResponse(val tracks: SpotifyTracks? = null)
data class SpotifyTracks(val items: List<SpotifyTrack> = emptyList())
data class SpotifyTrack(
    val id: String = "",
    val name: String = "",
    val artists: List<SpotifyArtist> = emptyList(),
    val album: SpotifyAlbum = SpotifyAlbum(),
    val uri: String = "",
    val duration_ms: Int = 0
)
data class SpotifyArtist(val name: String = "")
data class SpotifyAlbum(
    val name: String = "",
    val images: List<SpotifyImage> = emptyList()
)
data class SpotifyImage(val url: String = "")

// ===== Spotify Connect =====

data class SpotifyDevicesResponse(val devices: List<SpotifyDevice> = emptyList())

data class SpotifyDevice(
    val id: String? = null,
    val is_active: Boolean = false,
    val is_private_session: Boolean = false,
    val is_restricted: Boolean = false,
    val name: String = "",
    // type típico: "Computer", "Smartphone", "Speaker", "TV", "AVR", etc.
    val type: String = "",
    val volume_percent: Int? = null
)

data class TransferPlaybackRequest(
    val device_ids: List<String>,
    val play: Boolean = true
)

/**
 * Definición Retrofit de los endpoints REST de Spotify que usamos:
 * búsqueda de canciones, listar dispositivos disponibles (Spotify
 * Connect) y transferir la reproducción activa a otro dispositivo.
 */
interface SpotifyApi {
    @GET("search")
    suspend fun searchTracks(
        @Header("Authorization") authHeader: String,
        @Query("q") query: String,
        @Query("type") type: String,
        @Query("limit") limit: Int
    ): SpotifySearchResponse

    /** Lista los dispositivos donde el usuario tiene Spotify abierto (cel, TV, etc.) */
    @GET("me/player/devices")
    suspend fun getAvailableDevices(
        @Header("Authorization") authHeader: String
    ): SpotifyDevicesResponse

    /**
     * Transfiere la reproducción activa al dispositivo indicado.
     * Spotify regresa 204 No Content si sale bien, por eso Response<Unit>.
     */
    @PUT("me/player")
    suspend fun transferPlayback(
        @Header("Authorization") authHeader: String,
        @Body body: TransferPlaybackRequest
    ): Response<Unit>
}
```

## `data/remote/SpotifyAuthManager.kt` — #archivo de configuración del login OAuth de Spotify

```kotlin
package mx.utng.sintonia.data.remote

import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse

/**
 * Configuración fija del login OAuth de Spotify (Authorization Code
 * con respuesta tipo TOKEN, ideal para apps móviles sin backend propio).
 */
object SpotifyAuthManager {
    const val CLIENT_ID = "63ea034767694ac388fb5837cc2f8369"
    const val REDIRECT_URI = "mx.utng.sintonia://callback"
    const val REQUEST_CODE = 1337

    /**
     * Arma la petición de autorización con los scopes necesarios para
     * controlar la reproducción (streaming, leer y modificar el estado
     * del reproductor, y saber qué está sonando).
     *
     * @return AuthorizationRequest lista para lanzar con AuthorizationClient
     */
    fun getAuthRequest(): AuthorizationRequest {
        return AuthorizationRequest.Builder(
            CLIENT_ID,
            AuthorizationResponse.Type.TOKEN,
            REDIRECT_URI
        )
            .setScopes(
                arrayOf(
                    "streaming",
                    "user-read-playback-state",
                    "user-modify-playback-state",
                    "user-read-currently-playing"
                )
            )
            .setShowDialog(true)
            .build()
    }
}
```

## `data/remote/SpotifyPlayerManager.kt` — #archivo que envuelve el SDK de Spotify App Remote

```kotlin
package mx.utng.sintonia.data.remote

import android.content.Context
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Envuelve el SDK de Spotify App Remote (control directo de la app
 * oficial de Spotify instalada en el teléfono, distinto de la API REST).
 * Expone el estado de reproducción como StateFlows para que el
 * PlayerViewModel los consuma.
 */
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

    /**
     * Se conecta a la app de Spotify instalada en el dispositivo y se
     * suscribe a subscribeToPlayerState(), que dispara el callback cada
     * vez que Spotify cambia de canción, pausa, o avanza. Ahí mismo se
     * detecta cambio de canción (comparando contra lastTrackUri) y se
     * dispara un timer local (startProgressTimer) para estimar el
     * progreso entre eventos, ya que Spotify no manda el progreso
     * continuamente.
     */
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

    /**
     * Timer local que estima el progreso de reproducción cada 500ms
     * interpolando desde la última posición conocida (lastPlaybackPosition)
     * más el tiempo transcurrido, ya que Spotify App Remote no reporta el
     * progreso en tiempo real. También detecta cuándo la canción está por
     * terminar (98%) para disparar el cambio a la siguiente en la cola.
     *
     * @param duration duración total de la pista actual en milisegundos
     */
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

    /** Reinicia la bandera de "canción terminada" después de procesarla (evita disparar el cambio de canción dos veces). */
    fun resetTrackFinished() {
        _onTrackFinished.value = false
    }

    /** Agrega una pista a la cola nativa de Spotify por su URI (spotify:track:...). */
    fun addToQueue(spotifyUri: String) {
        spotifyAppRemote?.playerApi?.queue(spotifyUri)
            ?: Log.e("SpotifyPlayer", "No conectado a Spotify")
    }

    /** Reproduce inmediatamente la pista indicada por su URI de Spotify. */
    fun playSong(spotifyUri: String) {
        spotifyAppRemote?.playerApi?.play(spotifyUri)
            ?: Log.e("SpotifyPlayer", "No conectado a Spotify")
    }

    /** Salta a la siguiente pista usando el control nativo de Spotify. */
    fun skipNext() {
        spotifyAppRemote?.playerApi?.skipNext()
    }

    /** Regresa a la pista anterior usando el control nativo de Spotify. */
    fun skipPrevious() {
        spotifyAppRemote?.playerApi?.skipPrevious()
    }

    /** Pausa Spotify y cancela el timer local de progreso. */
    fun pause() {
        progressJob?.cancel()
        spotifyAppRemote?.playerApi?.pause()
    }

    /** Reanuda Spotify y reinicia el timer local de progreso desde la posición actual. */
    fun resume() {
        spotifyAppRemote?.playerApi?.resume()
        val dur = _duration.value
        if (dur > 0) {
            lastEventTime = System.currentTimeMillis()
            startProgressTimer(dur)
        }
    }

    /** Desactiva shuffle y repeat en Spotify, para dejar la cola en un estado predecible antes de reproducir la siguiente canción propia. */
    fun clearSpotifyQueue() {
        // Reproducir un silencio o track vacío para limpiar el contexto
        spotifyAppRemote?.playerApi?.setShuffle(false)
        spotifyAppRemote?.playerApi?.setRepeat(0) // 0 = no repeat
    }
    /** Cancela el timer de progreso, cierra el scope de corutinas y desconecta el SpotifyAppRemote. */
    fun disconnect() {
        progressJob?.cancel()
        scope.cancel()
        SpotifyAppRemote.disconnect(spotifyAppRemote)
        _isConnected.value = false
    }
}
```

## `data/remote/SpotifyRepository.kt` — #archivo que envuelve la API REST de Spotify

```kotlin
package mx.utng.sintonia.data.remote

import android.util.Log
import mx.utng.sintonia.data.model.Song
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Envuelve la API REST de Spotify (búsqueda, dispositivos disponibles y
 * transferencia de reproducción) usando Retrofit + un token Bearer que
 * llega desde el login OAuth (SpotifyAuthManager).
 */
class SpotifyRepository {

    // 1. Creamos un logger para OkHttp
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC // Muestra la URL completa que se llama
    }

    // 2. Adjuntamos OkHttpClient a Retrofit
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    private val api: SpotifyApi = Retrofit.Builder()
        .baseUrl("https://api.spotify.com/v1/")
        .client(okHttpClient) // 👈 Agregamos el cliente configurado
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SpotifyApi::class.java)

    /** Arma el header Authorization, agregando el prefijo "Bearer " si no viene ya incluido en el token. */
    private fun buildAuthHeader(token: String): String =
        if (token.startsWith("Bearer ")) token else "Bearer $token"

    /**
     * Busca canciones en Spotify por texto y las convierte al modelo Song.
     * El audioUrl resultante es un URI de Spotify (spotify:track:id), no
     * una URL HTTP directa — Spotify no permite streaming directo fuera
     * de su SDK.
     *
     * @param query texto de búsqueda
     * @param token access token de Spotify (con o sin "Bearer ")
     * @return lista de resultados, o lista vacía si la query está vacía o falla la petición
     */
    suspend fun searchTracks(query: String, token: String): List<Song> {
        val cleanQuery = query.trim()

        if (cleanQuery.isEmpty()) return emptyList()

        return try {
            val authHeader = buildAuthHeader(token)

            Log.d("SpotifyRepository", "Buscando '$cleanQuery' con limit=20")

            val response = api.searchTracks(
                authHeader = authHeader,
                query = cleanQuery,
                type = "track",
                limit = 10
            )

            val items = response.tracks?.items ?: emptyList()
            Log.d("SpotifyRepository", "Resultados obtenidos: ${items.size}")

            items.map { track ->
                Song(
                    id = track.id,
                    title = track.name,
                    artist = track.artists.firstOrNull()?.name ?: "Artista desconocido",
                    albumCover = track.album.images.firstOrNull()?.url ?: "",
                    audioUrl = "spotify:track:${track.id}",
                    duration = track.duration_ms / 1000,
                    source = "spotify"
                )
            }
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Log.e("SpotifyRepository", "Error HTTP ${e.code()}: $errorBody")
            emptyList()
        } catch (e: Exception) {
            Log.e("SpotifyRepository", "Error general en la búsqueda: ${e.localizedMessage}", e)
            emptyList()
        }
    }

    /**
     * Trae una lista de "destacadas" simulando una búsqueda fija
     * ("top hits 2024"), ya que la API de Spotify para apps sin
     * aprobación extendida no expone un endpoint público de "featured
     * playlists" directo.
     *
     * @param token access token de Spotify
     * @return lista de canciones destacadas, o vacía si falla
     */
    suspend fun getFeaturedTracks(token: String): List<Song> {
        return try {
            val authHeader = buildAuthHeader(token)
            val response = api.searchTracks(
                authHeader = authHeader,
                query = "top hits 2024",
                type = "track",
                limit = 20
            )
            val items = response.tracks?.items ?: emptyList()
            items.map { track ->
                Song(
                    id = track.id,
                    title = track.name,
                    artist = track.artists.firstOrNull()?.name ?: "Artista desconocido",
                    albumCover = track.album.images.firstOrNull()?.url ?: "",
                    audioUrl = "spotify:track:${track.id}",
                    duration = track.duration_ms / 1000,
                    source = "spotify"
                )
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepository", "Error cargando featured: ${e.localizedMessage}")
            emptyList()
        }
    }

    // ===== Spotify Connect =====

    /** Regresa la lista de dispositivos donde el usuario tiene Spotify abierto (cel, TV, etc.) */
    /**
     * Lista los dispositivos donde el usuario tiene Spotify abierto
     * (celular, TV, bocina, etc.) — es el primer paso para poder
     * transferir la reproducción a la TV.
     *
     * @param token access token de Spotify
     * @return lista de dispositivos, o vacía si falla la petición
     */
    suspend fun getAvailableDevices(token: String): List<SpotifyDevice> {
        return try {
            val authHeader = buildAuthHeader(token)
            val response = api.getAvailableDevices(authHeader)
            Log.d("SpotifyRepository", "Dispositivos encontrados: ${response.devices.map { "${it.name} (${it.type})" }}")
            response.devices
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Log.e("SpotifyRepository", "Error HTTP obteniendo dispositivos ${e.code()}: $errorBody")
            emptyList()
        } catch (e: Exception) {
            Log.e("SpotifyRepository", "Error general obteniendo dispositivos: ${e.localizedMessage}", e)
            emptyList()
        }
    }

    /**
     * Transfiere la reproducción activa al dispositivo indicado.
     * Devuelve true si Spotify aceptó el cambio (204 No Content).
     */
    /**
     * Transfiere la reproducción activa de Spotify al dispositivo indicado.
     *
     * @param token access token de Spotify
     * @param deviceId id del dispositivo destino (obtenido con getAvailableDevices)
     * @param play si además de transferir debe seguir reproduciendo
     * @return true si Spotify aceptó el cambio (204 No Content)
     */
    suspend fun transferPlayback(token: String, deviceId: String, play: Boolean = true): Boolean {
        return try {
            val authHeader = buildAuthHeader(token)
            val response = api.transferPlayback(
                authHeader = authHeader,
                body = TransferPlaybackRequest(device_ids = listOf(deviceId), play = play)
            )
            Log.d("SpotifyRepository", "Transferir a $deviceId -> code ${response.code()}")
            response.isSuccessful
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Log.e("SpotifyRepository", "Error HTTP transfiriendo ${e.code()}: $errorBody")
            false
        } catch (e: Exception) {
            Log.e("SpotifyRepository", "Error general transfiriendo: ${e.localizedMessage}", e)
            false
        }
    }
}
```

## `data/remote/YouTubeApi.kt` — #archivo de la definición Retrofit de YouTube

```kotlin
package mx.utng.sintonia.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

data class YouTubeSearchResponse(
    val items: List<YouTubeItem> = emptyList()
)

data class YouTubeItem(
    val id: YouTubeItemId = YouTubeItemId(),
    val snippet: YouTubeSnippet = YouTubeSnippet()
)

data class YouTubeItemId(
    val videoId: String = ""
)

data class YouTubeSnippet(
    val title: String = "",
    val channelTitle: String = "",
    val thumbnails: YouTubeThumbnails = YouTubeThumbnails()
)

data class YouTubeThumbnails(
    val high: YouTubeThumbnail = YouTubeThumbnail()
)

data class YouTubeThumbnail(
    val url: String = ""
)

/**
 * Definición Retrofit del endpoint de búsqueda de la YouTube Data API v3.
 */
interface YouTubeApi {
    @GET("search")
    suspend fun searchVideos(
        @Query("part") part: String = "snippet",
        @Query("q") query: String,
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 10,
        @Query("key") apiKey: String
    ): YouTubeSearchResponse
}
```

## `data/remote/YouTubeRepository.kt` — #archivo que envuelve YouTubeApi

```kotlin
package mx.utng.sintonia.data.remote

import android.util.Log
import mx.utng.sintonia.ui.screens.YouTubeVideo
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class YouTubeRepository {

    private val api: YouTubeApi = Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/youtube/v3/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(YouTubeApi::class.java)

    /**
     * Busca videos en YouTube por texto y los convierte al modelo
     * YouTubeVideo que usa YouTubeScreen, armando también la URL
     * completa de reproducción (youtube.com/watch?v=...).
     *
     * @param query texto de búsqueda
     * @param apiKey API key de Google Cloud para la YouTube Data API
     * @return lista de videos encontrados, o vacía si falla la petición
     */
    suspend fun searchVideos(query: String, apiKey: String): List<YouTubeVideo> {
        return try {
            val response = api.searchVideos(query = query, apiKey = apiKey)
            response.items.map { item ->
                YouTubeVideo(
                    id = item.id.videoId,
                    title = item.snippet.title,
                    channel = item.snippet.channelTitle,
                    views = "",
                    thumbnail = item.snippet.thumbnails.high.url,
                    youtubeUrl = "https://www.youtube.com/watch?v=${item.id.videoId}"
                )
            }
        } catch (e: Exception) {
            Log.e("YouTubeRepository", "Error: ${e.message}")
            emptyList()
        }
    }
}
```

---

## `ui/components/PlayerBar.kt` y `ui/components/SongCard.kt` — #archivos sin uso actualmente

Ambos archivos solo contienen la declaración del paquete (`package mx.utng.sintonia.ui.components`), sin ninguna clase, función ni composable definido todavía. Los componentes equivalentes que sí se usan en la app (`PlayerBar`, `SongCard`) están definidos directamente dentro de `ui/screens/HomeScreen.kt` — puede valer la pena, como mejora futura, mover esas definiciones a estos archivos para que el nombre del archivo coincida con lo que contiene.

---

## `ui/navigation/Navigation.kt` — #archivo de las 5 pestañas de la barra inferior

```kotlin
package mx.utng.sintonia.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Define las 5 pestañas de la barra de navegación inferior (bottom nav):
 * ruta interna, etiqueta visible e ícono. Las pantallas que no están en
 * esta barra (Spotify, Radio, Jamendo detalle, YouTube, Favoritos) se
 * navegan por rutas sueltas definidas directo en AppNavigation.
 */
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Inicio", Icons.Default.Home)
    object Search : Screen("search", "Buscar", Icons.Default.Search)
    object Queue : Screen("queue", "Cola", Icons.Default.List)
    object Downloads : Screen("downloads", "Descarg", Icons.Default.Download)
    object Settings : Screen("settings", "Config", Icons.Default.Settings)
}
```

## `ui/navigation/AppNavigation.kt` — #archivo raíz de navegación

```kotlin
package mx.utng.sintonia.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import mx.utng.sintonia.ui.screens.DownloadsScreen
import mx.utng.sintonia.ui.screens.FavoritesScreen
import mx.utng.sintonia.ui.screens.HomeScreen
import mx.utng.sintonia.ui.screens.JamendoScreen
import mx.utng.sintonia.ui.screens.QueueScreen
import mx.utng.sintonia.ui.screens.RadioScreen
import mx.utng.sintonia.ui.screens.SettingsScreen
import mx.utng.sintonia.ui.screens.SpotifyScreen
import mx.utng.sintonia.ui.screens.YouTubeScreen
import mx.utng.sintonia.ui.theme.SintoniaDark
import mx.utng.sintonia.ui.theme.SintoniaGreen
import mx.utng.sintonia.ui.theme.SintoniaSubtext
import mx.utng.sintonia.viewmodel.PlayerViewModel

val screens = listOf(
    Screen.Home,
    Screen.Search,
    Screen.Queue,
    Screen.Downloads,
    Screen.Settings
)

/**
 * Composable raíz de navegación de la app. Arma el Scaffold con la
 * barra inferior (NavigationBar) de las 5 pantallas principales, y el
 * NavHost con todas las rutas — incluidas las que no están en la barra
 * (spotify, radio, jamendo, youtube, favorites), a las que se llega
 * navegando desde HomeScreen. El mismo PlayerViewModel se comparte con
 * todas las pantallas, para que el estado de reproducción sea uno solo
 * en toda la app.
 *
 * @param viewModel instancia única del PlayerViewModel de toda la app
 */
@Composable
fun AppNavigation(viewModel: PlayerViewModel) {
    val navController = rememberNavController()

    Scaffold(
        containerColor = SintoniaDark,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF1A1A1A)) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = {
                            Text(
                                screen.label,
                                fontSize = androidx.compose.ui.unit.TextUnit(
                                    10f,
                                    androidx.compose.ui.unit.TextUnitType.Sp
                                )
                            )
                        },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SintoniaGreen,
                            selectedTextColor = SintoniaGreen,
                            unselectedIconColor = SintoniaSubtext,
                            unselectedTextColor = SintoniaSubtext,
                            indicatorColor = SintoniaGreen.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(viewModel = viewModel, navController = navController)
            }
            composable(Screen.Search.route) {
                JamendoScreen(viewModel = viewModel, navController = navController)
            }
            composable(Screen.Queue.route) {
                QueueScreen(viewModel = viewModel)
            }
            composable(Screen.Downloads.route) {
                DownloadsScreen(viewModel = viewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
            composable("spotify") {
                SpotifyScreen(viewModel = viewModel, navController = navController)
            }
            composable("radio") {
                RadioScreen(viewModel = viewModel, navController = navController)
            }
            composable("jamendo") {
                JamendoScreen(viewModel = viewModel, navController = navController)
            }
            composable("youtube") {
                YouTubeScreen(navController = navController, viewModel = viewModel)
            }
            composable("favorites") {
                FavoritesScreen(viewModel = viewModel, navController = navController)
            }
        }
    }
}
```

---

## `ui/screens/HomeScreen.kt` — #archivo de la pantalla de inicio

```kotlin
package mx.utng.sintonia.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import mx.utng.sintonia.data.model.Song
import mx.utng.sintonia.ui.theme.SintoniaCard
import mx.utng.sintonia.ui.theme.SintoniaDark
import mx.utng.sintonia.ui.theme.SintoniaGreen
import mx.utng.sintonia.ui.theme.SintoniaPink
import mx.utng.sintonia.ui.theme.SintoniaSubtext
import mx.utng.sintonia.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay

/**
 * Pantalla de inicio de la app. Muestra los 4 botones de fuente
 * (Spotify, Jamendo, Radio, YouTube) y, si hay algo sonando, una
 * tarjeta "Reproduciendo ahora" con controles. Mantiene un progreso
 * local (`localProgress`) que avanza suavemente cada 500ms entre
 * actualizaciones reales de Firebase/Spotify, para que la barra no se
 * vea con saltos.
 *
 * @param viewModel ViewModel compartido de toda la app
 * @param navController controlador de navegación, usado para ir a cada fuente
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: PlayerViewModel = viewModel(),
    navController: NavController? = null,
    modifier: Modifier = Modifier
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val currentSource by viewModel.currentSource.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val spotifyProgress by viewModel.spotifyProgress.collectAsState()
    val spotifyDuration by viewModel.spotifyDuration.collectAsState()

    // Progreso local que avanza suavemente sin depender de Firebase
    var localProgress by remember { mutableStateOf(0f) }

    // Cuando cambia la canción reinicia el progreso
    LaunchedEffect(playbackState.currentSong.id) {
        localProgress = if (currentSource == "spotify") spotifyProgress else progress
    }

    // Timer local — avanza cada 500ms
    LaunchedEffect(playbackState.isPlaying, playbackState.currentSong.id, currentSource) {
        while (playbackState.isPlaying) {
            delay(500)
            when (currentSource) {
                "spotify" -> {
                    if (spotifyDuration > 0) {
                        val increment = 0.5f / (spotifyDuration / 1000f)
                        localProgress = (localProgress + increment).coerceIn(0f, 1f)
                    }
                }
                "jamendo" -> {
                    val duration = playbackState.currentSong.duration.toFloat()
                    if (duration > 0) {
                        val increment = 0.5f / duration
                        localProgress = (localProgress + increment).coerceIn(0f, 1f)
                    }
                }
            }
        }
    }

    // Sincroniza cuando llega un update real de Firebase o Spotify
    LaunchedEffect(progress) {
        if (currentSource != "spotify") localProgress = progress
    }
    LaunchedEffect(spotifyProgress) {
        if (currentSource == "spotify") localProgress = spotifyProgress
    }

    Scaffold(
        modifier = modifier,
        containerColor = SintoniaDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "SINTONÍA", fontWeight = FontWeight.Bold,
                            color = SintoniaGreen, fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { navController?.navigate("favorites") }) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = "Favoritos",
                                tint = SintoniaPink
                            )
                        }
                        Surface(
                            color = SintoniaGreen.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Wifi, contentDescription = null,
                                    tint = SintoniaGreen, modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("En vivo", color = SintoniaGreen, fontSize = 11.sp)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SintoniaDark)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "FUENTE DE REPRODUCCIÓN", color = SintoniaSubtext,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SourceButton(
                            label = "Spotify",
                            sublabel = "Conectado",
                            icon = Icons.Default.MusicNote,
                            color = SintoniaGreen,
                            selected = currentSource == "spotify",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.setSource("spotify")
                                navController?.navigate("spotify")
                            }
                        )
                        SourceButton(
                            label = "Jamendo",
                            sublabel = "Gratuito",
                            icon = Icons.Default.LibraryMusic,
                            color = Color(0xFF4A9EFF),
                            selected = currentSource == "jamendo",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.setSource("jamendo")
                                viewModel.loadPopularTracks()
                                navController?.navigate("jamendo")
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SourceButton(
                            label = "Radio Garden",
                            sublabel = "Radio en vivo",
                            icon = Icons.Default.Radio,
                            color = SintoniaPink,
                            selected = currentSource == "radio",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.setSource("radio")
                                navController?.navigate("radio")
                            }
                        )
                        SourceButton(
                            label = "YouTube",
                            sublabel = "Video",
                            icon = Icons.Default.PlayCircle,
                            color = Color(0xFFFF0000),
                            selected = currentSource == "youtube",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.setSource("youtube")
                                navController?.navigate("youtube")
                            }
                        )
                    }
                }
            }

            if (playbackState.currentSong.title.isNotEmpty()) {
                item {
                    Text(
                        "REPRODUCIENDO AHORA", color = SintoniaSubtext,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    NowPlayingCard(
                        song = playbackState.currentSong,
                        isPlaying = playbackState.isPlaying,
                        source = currentSource,
                        progress = localProgress,
                        duration = if (currentSource == "spotify")
                            (spotifyDuration / 1000).toInt()
                        else
                            playbackState.currentSong.duration,
                        onTogglePlay = { viewModel.togglePlayPause() },
                        onNext = { viewModel.nextSong() },
                        onPrevious = { viewModel.previousSong() }
                    )
                }
            }
        }
    }
}

/**
 * Botón grande de selección de fuente (Spotify/Jamendo/Radio/YouTube),
 * con ícono, etiqueta y sub-etiqueta, resaltado cuando está seleccionado.
 *
 * @param label nombre de la fuente
 * @param sublabel descripción corta ("Conectado", "Gratuito", etc.)
 * @param icon ícono representativo
 * @param color color de acento de esa fuente
 * @param selected true si es la fuente actualmente activa
 * @param onClick se invoca al tocar el botón (cambia de fuente y navega)
 */
@Composable
fun SourceButton(
    label: String,
    sublabel: String,
    icon: ImageVector,
    color: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(120.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) color.copy(alpha = 0.25f) else SintoniaCard
        ),
        shape = RoundedCornerShape(16.dp),
        border = if (selected) BorderStroke(1.5.dp, color) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon, contentDescription = null, tint = color,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                label, color = Color.White, fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                sublabel, color = color, fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/**
 * Tarjeta "Reproduciendo ahora" de la pantalla de inicio: portada,
 * título/artista, etiqueta de fuente, barra de progreso (indeterminada
 * y en bucle si es radio, normal con tiempos si no) y controles de
 * anterior/play-pausa/siguiente.
 *
 * @param song canción actual
 * @param isPlaying true si está reproduciéndose
 * @param source fuente activa
 * @param progress progreso normalizado (0f-1f)
 * @param duration duración total en segundos
 * @param onTogglePlay se invoca al presionar play/pausa
 * @param onNext se invoca al presionar siguiente
 * @param onPrevious se invoca al presionar anterior
 */
@Composable
fun NowPlayingCard(
    song: Song,
    isPlaying: Boolean,
    source: String,
    progress: Float,
    duration: Int = 0,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SintoniaCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = song.albumCover,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song.title, color = Color.White, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.artist, color = SintoniaSubtext, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    color = when (source) {
                        "spotify" -> SintoniaGreen
                        "radio" -> SintoniaPink
                        "youtube" -> Color(0xFFFF0000)
                        else -> Color(0xFF4A9EFF)
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        source.replaceFirstChar { it.uppercase() },
                        color = Color.White, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Barra de progreso
            if (source == "radio") {
                val infiniteTransition = rememberInfiniteTransition(label = "radio")
                val radioProgress by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(3000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "radioBar"
                )
                LinearProgressIndicator(
                    progress = { radioProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = SintoniaPink,
                    trackColor = SintoniaDark
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("● En vivo", color = SintoniaPink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("Radio", color = SintoniaSubtext, fontSize = 10.sp)
                }
            } else {
                val accentColor = if (source == "spotify") SintoniaGreen else Color(0xFF4A9EFF)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = accentColor,
                    trackColor = SintoniaDark
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatTime((progress * duration).toInt()),
                        color = SintoniaSubtext, fontSize = 10.sp
                    )
                    Text(
                        formatTime(duration),
                        color = SintoniaSubtext, fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(
                        Icons.Default.SkipPrevious, contentDescription = null,
                        tint = SintoniaSubtext, modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                FloatingActionButton(
                    onClick = onTogglePlay,
                    containerColor = SintoniaGreen,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(onClick = onNext) {
                    Icon(
                        Icons.Default.SkipNext, contentDescription = null,
                        tint = SintoniaSubtext, modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

/**
 * Convierte segundos a formato "m:ss" (ej. 125 -> "2:05"), usado en
 * varias pantallas para mostrar tiempos de reproducción.
 *
 * @param seconds cantidad de segundos
 * @return cadena con formato "minutos:segundos", o "0:00" si seconds <= 0
 */
fun formatTime(seconds: Int): String {
    if (seconds <= 0) return "0:00"
    val min = seconds / 60
    val sec = seconds % 60
    return "%d:%02d".format(min, sec)
}

/**
 * Chip pequeño de selección (usado para filtros de fuente en listas),
 * resaltado con el color de la fuente cuando está seleccionado.
 *
 * @param label texto del chip
 * @param selected true si está seleccionado
 * @param color color de acento cuando está seleccionado
 * @param onClick se invoca al tocar el chip
 */
@Composable
fun SourceChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) color.copy(alpha = 0.2f) else SintoniaCard,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (selected) color else Color.Transparent)
    ) {
        Text(
            label,
            color = if (selected) color else SintoniaSubtext,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/**
 * Tarjeta genérica de canción con portada, título/artista, indicador de
 * estado de descarga (botón, progreso circular o check) y de
 * reproducción. Es una versión más simple/genérica que JamendoSongCard,
 * pensada para reutilizarse en otras listas.
 *
 * @param song canción a mostrar
 * @param isPlaying true si es la canción actualmente en reproducción
 * @param downloadStatus estado de descarga, o null si no aplica
 * @param onClick se invoca al tocar la tarjeta
 * @param onDownloadClick se invoca al presionar el botón de descarga
 */
@Composable
fun SongCard(
    song: Song,
    isPlaying: Boolean,
    downloadStatus: Song?,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) SintoniaGreen.copy(alpha = 0.2f) else SintoniaCard
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = song.albumCover,
                contentDescription = null,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title, color = Color.White, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artist, color = SintoniaSubtext, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            when {
                downloadStatus == null -> {
                    IconButton(onClick = onDownloadClick) {
                        Icon(
                            Icons.Default.Download, contentDescription = "Descargar",
                            tint = SintoniaSubtext
                        )
                    }
                }
                !downloadStatus.descargada -> {
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { downloadStatus.progresoDescarga / 100f },
                            color = SintoniaGreen,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                else -> {
                    Icon(
                        Icons.Default.CheckCircle, contentDescription = "Descargada",
                        tint = SintoniaGreen
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            if (isPlaying) {
                Icon(Icons.Default.Pause, contentDescription = null, tint = SintoniaGreen)
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = SintoniaSubtext)
            }
        }
    }
}

/**
 * Barra de reproducción inferior genérica (usada en Jamendo, Radio y
 * Favoritos): portada, título/artista, botón de destino TV/teléfono,
 * controles de anterior/play-pausa/siguiente y barra de progreso con
 * tiempos (o indicador "En vivo" si la fuente es radio).
 *
 * @param song canción actual
 * @param isPlaying true si está reproduciéndose
 * @param progress progreso normalizado (0f-1f)
 * @param playOnTv true si el audio se está mandando a la TV
 * @param onTogglePlay se invoca al presionar play/pausa
 * @param onNext se invoca al presionar siguiente
 * @param onPrevious se invoca al presionar anterior
 * @param onToggleTv se invoca al presionar el botón de destino (tel/TV)
 */
@Composable
fun PlayerBar(
    song: Song,
    isPlaying: Boolean,
    progress: Float = 0f,
    playOnTv: Boolean = false,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleTv: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = SintoniaCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = song.albumCover,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, color = Color.White, fontSize = 13.sp,
                        fontWeight = FontWeight.Medium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = SintoniaSubtext, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Botón TV
                IconButton(onClick = onToggleTv) {
                    Icon(
                        Icons.Default.Tv,
                        contentDescription = "Reproducir en TV",
                        tint = if (playOnTv) SintoniaGreen else SintoniaSubtext,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = null,
                        tint = SintoniaSubtext)
                }
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = SintoniaGreen,
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = null,
                        tint = SintoniaSubtext)
                }
            }

            // Barra de progreso
            if (song.source == "radio") {
                val infiniteTransition = rememberInfiniteTransition(label = "radioBar")
                val radioProgress by infiniteTransition.animateFloat(
                    initialValue = 0f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(3000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ), label = "radioProgress"
                )
                LinearProgressIndicator(
                    progress = { radioProgress },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = SintoniaPink, trackColor = SintoniaDark
                )
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = SintoniaGreen, trackColor = SintoniaDark
                )
            }

            // Tiempo
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (song.source == "radio") {
                    Text("● En vivo", color = SintoniaPink, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold)
                    Text("Radio", color = SintoniaSubtext, fontSize = 10.sp)
                } else {
                    Text(formatTime((progress * song.duration).toInt()),
                        color = SintoniaSubtext, fontSize = 10.sp)
                    Text(formatTime(song.duration),
                        color = SintoniaSubtext, fontSize = 10.sp)
                }
            }
        }
    }
}
```

## `ui/screens/JamendoScreen.kt` — #archivo de la pantalla de Jamendo

```kotlin
package mx.utng.sintonia.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import mx.utng.sintonia.data.model.Song
import mx.utng.sintonia.ui.theme.SintoniaCard
import mx.utng.sintonia.ui.theme.SintoniaDark
import mx.utng.sintonia.ui.theme.SintoniaGreen
import mx.utng.sintonia.ui.theme.SintoniaSubtext
import mx.utng.sintonia.viewmodel.PlayerViewModel

/**
 * Pantalla de búsqueda y reproducción de música gratuita bajo licencia
 * Creative Commons (Jamendo). Incluye barra de búsqueda, lista de
 * resultados con opción de descarga, y el PlayerBar inferior cuando
 * hay algo sonando.
 *
 * @param viewModel ViewModel compartido de toda la app
 * @param navController controlador de navegación (para el botón "Atrás")
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JamendoScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    navController: NavController? = null
) {
    val songs by viewModel.songs.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val queue by viewModel.queue.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = SintoniaDark,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController?.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack, contentDescription = "Atrás",
                            tint = Color.White
                        )
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Jamendo", fontWeight = FontWeight.Bold,
                            color = Color.White, fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = SintoniaGreen.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Creative Commons", color = SintoniaGreen, fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SintoniaDark)
            )
        },
        bottomBar = {
            if (playbackState.currentSong.title.isNotEmpty()) {
                val progress by viewModel.progress.collectAsState()
                val playOnTv by viewModel.playOnTv.collectAsState()
                PlayerBar(
                    song = playbackState.currentSong,
                    isPlaying = playbackState.isPlaying,
                    progress = progress,
                    playOnTv = playOnTv,
                    onTogglePlay = { viewModel.togglePlayPause() },
                    onNext = { viewModel.nextSong() },
                    onPrevious = { viewModel.previousSong() },
                    onToggleTv = { viewModel.togglePlayOnTv() }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar música gratuita...", color = SintoniaSubtext) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = SintoniaGreen)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SintoniaGreen,
                    unfocusedBorderColor = SintoniaCard,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = SintoniaGreen
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        TextButton(onClick = { viewModel.searchTracks(searchQuery) }) {
                            Text("Buscar", color = SintoniaGreen)
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "RESULTADOS", color = SintoniaSubtext,
                fontSize = 11.sp, fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SintoniaGreen)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(songs) { song ->
                        JamendoSongCard(
                            song = song,
                            isPlaying = playbackState.currentSong.id == song.id && playbackState.isPlaying,
                            downloadStatus = downloads.find { it.id == song.id },
                            isInQueue = queue.any { it.id == song.id },
                            onClick = { viewModel.playSong(song) },
                            onDownloadClick = { viewModel.downloadSong(song) },
                            onAddToQueue = {
                                viewModel.addToQueue(song)
                                snackbarMessage = "\"${song.title}\" agregada a la cola"
                            }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                color = SintoniaGreen.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "ⓘ Descarga legal bajo licencia Creative Commons",
                                    color = SintoniaGreen, fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta de una canción de Jamendo con dos filas: la primera con
 * portada/título/artista/duración y botones de cola y play; la segunda
 * dedicada al estado de descarga (botón, porcentaje o "Descargada").
 *
 * @param song canción de Jamendo a mostrar
 * @param isPlaying true si es la canción actualmente en reproducción
 * @param downloadStatus estado de descarga de esta canción, o null si no se ha descargado
 * @param isInQueue true si esta canción ya está en la cola
 * @param onClick se invoca al tocar la tarjeta para reproducirla
 * @param onDownloadClick se invoca al presionar "Descargar"
 * @param onAddToQueue se invoca al presionar el botón de agregar a la cola
 */
@Composable
fun JamendoSongCard(
    song: Song,
    isPlaying: Boolean,
    downloadStatus: Song?,
    isInQueue: Boolean,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onAddToQueue: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) SintoniaGreen.copy(alpha = 0.2f) else SintoniaCard
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = song.albumCover,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song.title, color = Color.White, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (isPlaying) "En reproducción · ${song.duration / 60}:${String.format("%02d", song.duration % 60)}"
                        else "${song.artist} · ${song.duration / 60}:${String.format("%02d", song.duration % 60)}",
                        color = if (isPlaying) SintoniaGreen else SintoniaSubtext,
                        fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                // Botón cola
                IconButton(
                    onClick = onAddToQueue,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (isInQueue) Icons.Default.QueueMusic else Icons.Default.AddToQueue,
                        contentDescription = if (isInQueue) "En cola" else "Agregar a cola",
                        tint = if (isInQueue) SintoniaGreen else SintoniaSubtext,
                        modifier = Modifier.size(20.dp)
                    )
                }
                // Play/Pause
                if (isPlaying) {
                    Icon(Icons.Default.Pause, contentDescription = null,
                        tint = SintoniaGreen, modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null,
                        tint = SintoniaSubtext, modifier = Modifier.size(20.dp))
                }
            }

            // Segunda fila para descarga
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    downloadStatus == null -> {
                        TextButton(
                            onClick = onDownloadClick,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Download, contentDescription = null,
                                tint = SintoniaSubtext, modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Descargar", color = SintoniaSubtext, fontSize = 11.sp)
                        }
                    }
                    !downloadStatus.descargada -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                progress = { downloadStatus.progresoDescarga / 100f },
                                color = SintoniaGreen, strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "${downloadStatus.progresoDescarga}%",
                                color = SintoniaSubtext, fontSize = 11.sp
                            )
                        }
                    }
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle, contentDescription = null,
                                tint = SintoniaGreen, modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Descargada", color = SintoniaGreen, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Barra de progreso si está reproduciendo
            if (isPlaying) {
                LinearProgressIndicator(
                    progress = { 0.45f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = SintoniaGreen,
                    trackColor = SintoniaDark
                )
            }
        }
    }
}
```

## `ui/screens/SpotifyScreen.kt` — #archivo de la pantalla de Spotify

```kotlin
package mx.utng.sintonia.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationResponse
import mx.utng.sintonia.data.model.Song
import mx.utng.sintonia.data.remote.SpotifyAuthManager
import mx.utng.sintonia.ui.theme.SintoniaCard
import mx.utng.sintonia.ui.theme.SintoniaDark
import mx.utng.sintonia.ui.theme.SintoniaGreen
import mx.utng.sintonia.ui.theme.SintoniaSubtext
import mx.utng.sintonia.viewmodel.PlayerViewModel

/**
 * Pantalla de Spotify. Si no hay sesión iniciada, muestra un botón de
 * login OAuth (lanza AuthorizationClient y recibe el token vía
 * rememberLauncherForActivityResult). Ya logueado, permite buscar
 * canciones y controla la reproducción a través de Spotify App Remote.
 *
 * @param viewModel ViewModel compartido de toda la app
 * @param navController controlador de navegación (para el botón "Atrás")
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    navController: NavController? = null
) {
    val context = LocalContext.current
    val songs by viewModel.spotifySongs.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val spotifyToken by viewModel.spotifyToken.collectAsState()
    val spotifyProgress by viewModel.spotifyProgress.collectAsState()
    val spotifyDuration by viewModel.spotifyDuration.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    val spotifyAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val response = AuthorizationClient.getResponse(result.resultCode, result.data)
        when (response.type) {
            AuthorizationResponse.Type.TOKEN -> viewModel.setSpotifyToken(response.accessToken)
            AuthorizationResponse.Type.ERROR -> android.util.Log.e("SpotifyAuth", "Error: ${response.error}")
            else -> {}
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = SintoniaDark,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController?.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack, contentDescription = "Atrás",
                            tint = Color.White
                        )
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Spotify", fontWeight = FontWeight.Bold,
                            color = SintoniaGreen, fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (spotifyToken != null) {
                            Surface(
                                color = SintoniaGreen.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "Conectado", color = SintoniaGreen, fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (spotifyToken != null) {
                        IconButton(onClick = { viewModel.logoutSpotify() }) {
                            Icon(
                                Icons.Default.ExitToApp, contentDescription = "Cerrar sesión",
                                tint = SintoniaSubtext
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SintoniaDark)
            )
        },
        bottomBar = {
            if (playbackState.currentSong.title.isNotEmpty() && playbackState.source == "spotify") {
                val playOnTv by viewModel.playOnTv.collectAsState()
                SpotifyPlayerBar(
                    song = playbackState.currentSong,
                    isPlaying = playbackState.isPlaying,
                    progress = spotifyProgress,
                    duration = spotifyDuration,
                    playOnTv = playOnTv,
                    onTogglePlay = { viewModel.togglePlayPause() },
                    onNext = { viewModel.nextSong() },
                    onPrevious = { viewModel.previousSong() },
                    onToggleTv = { viewModel.togglePlayOnTv() }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (spotifyToken == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.MusicNote, contentDescription = null,
                            tint = SintoniaGreen, modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Conecta tu cuenta de Spotify",
                            color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Necesitas cuenta Premium para reproducir",
                            color = SintoniaSubtext, fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                val request = SpotifyAuthManager.getAuthRequest()
                                val intent = AuthorizationClient.createLoginActivityIntent(
                                    context as Activity, request
                                )
                                spotifyAuthLauncher.launch(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SintoniaGreen),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.MusicNote, contentDescription = null,
                                tint = Color.Black
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Iniciar sesión con Spotify", color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar en Spotify...", color = SintoniaSubtext) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = SintoniaGreen)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SintoniaGreen,
                        unfocusedBorderColor = SintoniaCard,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = SintoniaGreen
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchQuery.isNotBlank()) {
                                viewModel.setSource("spotify")
                                viewModel.searchSpotifyTracks(searchQuery)
                            }
                        }
                    ),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            TextButton(onClick = {
                                viewModel.setSource("spotify")
                                viewModel.searchSpotifyTracks(searchQuery)
                            }) {
                                Text("Buscar", color = SintoniaGreen)
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (searchQuery.isEmpty()) "CANCIONES POPULARES" else "RESULTADOS",
                    color = SintoniaSubtext, fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SintoniaGreen)
                    }
                } else if (songs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Search, contentDescription = null,
                                tint = SintoniaSubtext, modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Busca una canción en Spotify", color = SintoniaSubtext)
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(songs) { song ->
                            SpotifySongCard(
                                song = song,
                                isPlaying = playbackState.currentSong.id == song.id && playbackState.isPlaying,
                                isInQueue = queue.any { it.id == song.id },
                                isFavorite = favorites.any { it.id == song.id },
                                onClick = { viewModel.playSongSpotify(song, context) },
                                onAddToQueue = {
                                    viewModel.addToQueue(song)
                                    snackbarMessage = "\"${song.title}\" agregada a la cola"
                                },
                                onFavorite = {
                                    viewModel.toggleFavorite(song)
                                    snackbarMessage = if (favorites.any { it.id == song.id })
                                        "\"${song.title}\" quitada de favoritos"
                                    else
                                        "\"${song.title}\" guardada en favoritos"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Barra de reproducción inferior específica de Spotify: portada,
 * título/artista, botón para alternar destino (teléfono/TV), barra de
 * progreso con tiempos, y controles de anterior/play-pausa/siguiente.
 *
 * @param song canción de Spotify actualmente en reproducción
 * @param isPlaying true si está reproduciéndose
 * @param progress progreso normalizado (0f-1f)
 * @param duration duración total en milisegundos
 * @param playOnTv true si el audio se está mandando a la TV
 * @param onTogglePlay se invoca al presionar play/pausa
 * @param onNext se invoca al presionar siguiente
 * @param onPrevious se invoca al presionar anterior
 * @param onToggleTv se invoca al presionar el botón de destino (tel/TV)
 */
@Composable
fun SpotifyPlayerBar(
    song: Song,
    isPlaying: Boolean,
    progress: Float,
    duration: Long,
    playOnTv: Boolean,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleTv: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = SintoniaCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = song.albumCover,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song.title, color = Color.White, fontWeight = FontWeight.Bold,
                        fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.artist, color = SintoniaSubtext, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                // Botón de destino de reproducción (phone / tv)
                IconButton(onClick = onToggleTv) {
                    Icon(
                        if (playOnTv) Icons.Default.Tv else Icons.Default.Smartphone,
                        contentDescription = if (playOnTv) "Reproduciendo en TV" else "Reproduciendo en teléfono",
                        tint = if (playOnTv) SintoniaGreen else SintoniaSubtext
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Surface(
                    color = SintoniaGreen,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "Spotify", color = Color.Black, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = SintoniaGreen,
                trackColor = SintoniaDark
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatTime((progress * (duration / 1000f)).toInt()),
                    color = SintoniaSubtext, fontSize = 10.sp
                )
                Text(
                    formatTime((duration / 1000).toInt()),
                    color = SintoniaSubtext, fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(
                        Icons.Default.SkipPrevious, contentDescription = null,
                        tint = SintoniaSubtext, modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                FloatingActionButton(
                    onClick = onTogglePlay,
                    containerColor = SintoniaGreen,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(onClick = onNext) {
                    Icon(
                        Icons.Default.SkipNext, contentDescription = null,
                        tint = SintoniaSubtext, modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
/**
 * Tarjeta de resultado de búsqueda de Spotify: portada, título,
 * artista, botón de favorito y botón de agregar a la cola.
 *
 * @param song canción de Spotify a mostrar
 * @param isPlaying true si es la canción actualmente en reproducción
 * @param isInQueue true si ya está en la cola
 * @param isFavorite true si ya está marcada como favorita
 * @param onClick se invoca al tocar la tarjeta para reproducirla
 * @param onAddToQueue se invoca al presionar agregar a la cola
 * @param onFavorite se invoca al presionar el ícono de favorito
 */
@Composable
fun SpotifySongCard(
    song: Song,
    isPlaying: Boolean,
    isInQueue: Boolean = false,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    onAddToQueue: () -> Unit = {},
    onFavorite: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) SintoniaGreen.copy(alpha = 0.2f) else SintoniaCard
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = song.albumCover,
                contentDescription = null,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title, color = Color.White, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artist, color = SintoniaSubtext, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            // Botón favorito
            IconButton(onClick = onFavorite) {
                Icon(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFavorite) "Quitar de favoritos" else "Guardar en favoritos",
                    tint = if (isFavorite) SintoniaGreen else SintoniaSubtext
                )
            }
            // Botón agregar a cola
            IconButton(onClick = onAddToQueue) {
                Icon(
                    if (isInQueue) Icons.Default.QueueMusic else Icons.Default.AddToQueue,
                    contentDescription = if (isInQueue) "En cola" else "Agregar a cola",
                    tint = if (isInQueue) SintoniaGreen else SintoniaSubtext
                )
            }
            if (isPlaying) {
                Icon(Icons.Default.Pause, contentDescription = null, tint = SintoniaGreen)
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = SintoniaSubtext)
            }
        }
    }
}
```

## `ui/screens/RadioScreen.kt` — #archivo de la pantalla de radio en vivo

```kotlin
package mx.utng.sintonia.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import mx.utng.sintonia.ui.theme.SintoniaCard
import mx.utng.sintonia.ui.theme.SintoniaDark
import mx.utng.sintonia.ui.theme.SintoniaPink
import mx.utng.sintonia.ui.theme.SintoniaSubtext
import mx.utng.sintonia.viewmodel.PlayerViewModel

/**
 * Modelo de estación de radio ya normalizado para la UI (a diferencia
 * de RadioBrowserStation, que es el formato crudo de la API externa).
 */
data class RadioStation(
    val id: String,
    val name: String,
    val city: String,
    val genre: String,
    val streamUrl: String
)

/**
 * Pantalla de radio en vivo (Radio Browser). Al entrar carga las
 * estaciones más populares; la barra de búsqueda filtra por
 * ciudad/país/nombre. Si hay una estación sonando, muestra además un
 * visualizador de onda animado (AudioWaveVisualizer).
 *
 * @param viewModel ViewModel compartido de toda la app
 * @param navController controlador de navegación (para el botón "Atrás")
 * @param onBack callback alterno de regreso (no usado activamente, queda por compatibilidad)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    navController: NavController? = null,
    onBack: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    val playbackState by viewModel.playbackState.collectAsState()
    val stations by viewModel.radioStations.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val progress by viewModel.progress.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadTopRadioStations()
    }

    Scaffold(
        modifier = modifier,
        containerColor = SintoniaDark,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController?.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack, contentDescription = "Atrás",
                            tint = Color.White
                        )
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Radio Garden", fontWeight = FontWeight.Bold,
                            color = Color.White, fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = SintoniaPink.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "En vivo", color = SintoniaPink, fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SintoniaDark)
            )
        },
        bottomBar = {
            if (playbackState.currentSong.title.isNotEmpty() && playbackState.source == "radio") {
                val playOnTv by viewModel.playOnTv.collectAsState()
                PlayerBar(
                    song = playbackState.currentSong,
                    isPlaying = playbackState.isPlaying,
                    progress = progress,
                    playOnTv = playOnTv,
                    onTogglePlay = { viewModel.togglePlayPause() },
                    onNext = { viewModel.nextSong() },
                    onPrevious = { viewModel.previousSong() },
                    onToggleTv = { viewModel.togglePlayOnTv() }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { query ->
                    searchQuery = query
                    if (query.isEmpty()) {
                        viewModel.loadTopRadioStations()
                    } else {
                        viewModel.searchRadioStations(query)
                    }
                },
                placeholder = { Text("Ciudad, país o estación...", color = SintoniaSubtext) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = SintoniaPink)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SintoniaPink,
                    unfocusedBorderColor = SintoniaCard,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = SintoniaPink
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                if (searchQuery.isEmpty()) "ESTACIONES POPULARES" else "RESULTADOS",
                color = SintoniaSubtext,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SintoniaPink)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(stations) { station ->
                        RadioStationCard(
                            station = station,
                            isPlaying = playbackState.currentSong.id == station.id && playbackState.isPlaying,
                            onClick = {
                                viewModel.playRadioStation(
                                    station.id, station.name, station.city, station.streamUrl
                                )
                            }
                        )
                    }

                    if (playbackState.source == "radio" && playbackState.isPlaying) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "ONDA DE AUDIO — ${playbackState.currentSong.title.uppercase()}",
                                color = SintoniaSubtext, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            AudioWaveVisualizer()
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Ecualizador visual de 20 barras animándose en bucle infinito con
 * duraciones escalonadas, puramente decorativo — se muestra mientras
 * suena una estación de radio para reforzar la sensación de "en vivo".
 */
@Composable
fun AudioWaveVisualizer() {
    val barCount = 20
    val animations = List(barCount) { index ->
        val infiniteTransition = rememberInfiniteTransition(label = "wave$index")
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 400 + (index * 50),
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar$index"
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        animations.forEach { anim ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(anim.value)
            ) {
                HorizontalDivider(
                    modifier = Modifier.fillMaxSize(),
                    color = SintoniaPink,
                    thickness = 4.dp
                )
            }
        }
    }
}

/**
 * Tarjeta de una estación de radio: ícono, nombre, ciudad/género, y una
 * etiqueta "En vivo" cuando es la estación que está sonando.
 *
 * @param station estación a mostrar
 * @param isPlaying true si esta estación es la que está sonando
 * @param onClick se invoca al tocar la tarjeta para sintonizarla
 */
@Composable
fun RadioStationCard(station: RadioStation, isPlaying: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) SintoniaPink.copy(alpha = 0.15f) else SintoniaCard
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = if (isPlaying) SintoniaPink.copy(alpha = 0.3f) else SintoniaCard,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Radio, contentDescription = null,
                        tint = if (isPlaying) SintoniaPink else SintoniaSubtext,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    station.name, color = Color.White, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${station.city} · ${station.genre}",
                    color = SintoniaSubtext, fontSize = 12.sp
                )
            }
            if (isPlaying) {
                Surface(
                    color = SintoniaPink,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "● En vivo", color = Color.White, fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
```

## `ui/screens/YouTubeScreen.kt` — #archivo de la pantalla de YouTube

```kotlin
package mx.utng.sintonia.ui.screens

import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import mx.utng.sintonia.ui.theme.SintoniaCard
import mx.utng.sintonia.ui.theme.SintoniaDark
import mx.utng.sintonia.ui.theme.SintoniaSubtext
import mx.utng.sintonia.viewmodel.PlayerViewModel

/** Modelo de video de YouTube normalizado para la UI de la app. */
data class YouTubeVideo(
    val id: String,
    val title: String,
    val channel: String,
    val views: String,
    val thumbnail: String,
    val youtubeUrl: String
)

/**
 * Lista de videos de ejemplo (hardcodeada) que se muestra mientras el
 * usuario no ha hecho ninguna búsqueda todavía — así la pantalla no
 * arranca vacía.
 */
val sampleVideos = listOf(
    YouTubeVideo(
        "4NRXx6U8ABQ", "Blinding Lights — Official Music Video",
        "The Weeknd", "1.2B vistas",
        "https://img.youtube.com/vi/4NRXx6U8ABQ/hqdefault.jpg",
        "https://www.youtube.com/watch?v=4NRXx6U8ABQ"
    ),
    YouTubeVideo(
        "34Na4j8AVgA", "Starboy — Official Music Video",
        "The Weeknd", "890M vistas",
        "https://img.youtube.com/vi/34Na4j8AVgA/hqdefault.jpg",
        "https://www.youtube.com/watch?v=34Na4j8AVgA"
    ),
    YouTubeVideo(
        "XXYlFuWEuKI", "Save Your Tears — Official Music Video",
        "The Weeknd", "720M vistas",
        "https://img.youtube.com/vi/XXYlFuWEuKI/hqdefault.jpg",
        "https://www.youtube.com/watch?v=XXYlFuWEuKI"
    ),
    YouTubeVideo(
        "H5v3kku4y6Q", "As It Was — Official Video",
        "Harry Styles", "900M vistas",
        "https://img.youtube.com/vi/H5v3kku4y6Q/hqdefault.jpg",
        "https://www.youtube.com/watch?v=H5v3kku4y6Q"
    ),
    YouTubeVideo(
        "G7KNmW9a75Y", "Flowers — Official Video",
        "Miley Cyrus", "650M vistas",
        "https://img.youtube.com/vi/G7KNmW9a75Y/hqdefault.jpg",
        "https://www.youtube.com/watch?v=G7KNmW9a75Y"
    ),
    YouTubeVideo(
        "b1kbLwvqugk", "Anti-Hero — Official Music Video",
        "Taylor Swift", "580M vistas",
        "https://img.youtube.com/vi/b1kbLwvqugk/hqdefault.jpg",
        "https://www.youtube.com/watch?v=b1kbLwvqugk"
    ),
)

/**
 * Pantalla de búsqueda de YouTube. A diferencia de las demás fuentes,
 * el video NO se reproduce dentro de la app: al tocar un resultado se
 * actualiza Firebase (playYouTubeVideo) para que la TV lo muestre
 * embebido, y además se abre el video en una Chrome Custom Tab del
 * propio teléfono.
 *
 * @param navController controlador de navegación (para el botón "Atrás")
 * @param viewModel ViewModel compartido de toda la app
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeScreen(
    modifier: Modifier = Modifier,
    navController: NavController? = null,
    viewModel: PlayerViewModel
) {
    val context = LocalContext.current
    val youtubeVideos by viewModel.youtubeVideos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val videosToShow = if (youtubeVideos.isEmpty()) sampleVideos else youtubeVideos

    Scaffold(
        modifier = modifier,
        containerColor = SintoniaDark,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController?.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Atrás",
                            tint = Color.White
                        )
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "YouTube", fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF0000), fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = Color(0xFFFF0000).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Google Sign-In", color = Color(0xFFFF0000), fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SintoniaDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar videos o música...", color = SintoniaSubtext) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search, contentDescription = null,
                        tint = Color(0xFFFF0000)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF0000),
                    unfocusedBorderColor = SintoniaCard,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFFFF0000)
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        TextButton(onClick = { viewModel.searchYouTubeVideos(searchQuery) }) {
                            Text("Buscar", color = Color(0xFFFF0000))
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFFF0000))
                }
            } else {
                Text(
                    if (youtubeVideos.isEmpty()) "VIDEOS POPULARES" else "RESULTADOS",
                    color = SintoniaSubtext, fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(videosToShow) { video ->
                        YouTubeVideoCard(
                            video = video,
                            onClick = {
                                // ← Actualiza Firebase para que la TV muestre el video
                                viewModel.playYouTubeVideo(video)

                                try {
                                    val customTabIntent = CustomTabsIntent.Builder()
                                        .setShowTitle(true)
                                        .setDefaultColorSchemeParams(
                                            CustomTabColorSchemeParams.Builder()
                                                .setToolbarColor(SintoniaDark.toArgb())
                                                .setNavigationBarColor(SintoniaDark.toArgb())
                                                .build()
                                        )
                                        .build()
                                    customTabIntent.intent.setPackage("com.android.chrome")
                                    customTabIntent.launchUrl(context, Uri.parse(video.youtubeUrl))
                                } catch (e: Exception) {
                                    val customTabIntent = CustomTabsIntent.Builder()
                                        .setShowTitle(true)
                                        .build()
                                    customTabIntent.launchUrl(context, Uri.parse(video.youtubeUrl))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta de un video de YouTube: miniatura grande con ícono de play
 * superpuesto, etiqueta "Ver aquí", título y canal/vistas.
 *
 * @param video video a mostrar
 * @param onClick se invoca al tocar la tarjeta (dispara Firebase + Custom Tab)
 */
@Composable
fun YouTubeVideoCard(video: YouTubeVideo, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = SintoniaCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Box {
                AsyncImage(
                    model = video.thumbnail,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    contentScale = ContentScale.Crop
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.PlayArrow, contentDescription = null,
                            tint = Color.White, modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Surface(
                    color = Color(0xFFFF0000),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        "▶ Ver aquí", color = Color.White, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    video.title, color = Color.White, fontWeight = FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (video.views.isEmpty()) video.channel
                    else "${video.channel} · ${video.views}",
                    color = SintoniaSubtext, fontSize = 12.sp
                )
            }
        }
    }
}
```

## `ui/screens/QueueScreen.kt` — #archivo de la pantalla de la cola de reproducción

```kotlin
package mx.utng.sintonia.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.utng.sintonia.data.model.Song
import mx.utng.sintonia.ui.theme.SintoniaCard
import mx.utng.sintonia.ui.theme.SintoniaDark
import mx.utng.sintonia.ui.theme.SintoniaGreen
import mx.utng.sintonia.ui.theme.SintoniaPink
import mx.utng.sintonia.ui.theme.SintoniaSubtext
import mx.utng.sintonia.viewmodel.PlayerViewModel

/**
 * Pantalla de la cola de reproducción: muestra la canción actual
 * arriba y la lista de "próximas canciones" debajo, con opción de
 * reproducir cualquiera de inmediato, quitarla de la cola, o limpiar
 * la cola completa.
 *
 * @param viewModel ViewModel compartido de toda la app
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(viewModel: PlayerViewModel, modifier: Modifier = Modifier) {
    val queue by viewModel.queue.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    Scaffold(
        modifier = modifier,
        containerColor = SintoniaDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Cola de reproducción",
                            fontWeight = FontWeight.Bold, color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (queue.isNotEmpty()) {
                            Surface(
                                color = SintoniaGreen.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "${queue.size} canciones",
                                    color = SintoniaGreen, fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (queue.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearQueue() }) {
                            Text("Limpiar", color = SintoniaSubtext, fontSize = 13.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SintoniaDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Canción actual
            if (playbackState.currentSong.title.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "REPRODUCIENDO AHORA", color = SintoniaSubtext,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                CurrentSongCard(song = playbackState.currentSong, source = playbackState.source)
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                "PRÓXIMAS CANCIONES", color = SintoniaSubtext,
                fontSize = 11.sp, fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (queue.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.QueueMusic, contentDescription = null,
                            tint = SintoniaSubtext, modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No hay canciones en la cola",
                            color = SintoniaSubtext, fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Usa el botón + en Jamendo o Spotify",
                            color = SintoniaSubtext.copy(alpha = 0.6f), fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(queue) { index, song ->
                        QueueSongCard(
                            index = index + 1,
                            song = song,
                            onPlay = { viewModel.playFromQueue(song) },
                            onRemove = { viewModel.removeFromQueue(song.id) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta que resalta la canción que está sonando ahora mismo, con una
 * etiqueta de color según la fuente (Spotify/Radio/otra).
 *
 * @param song canción actual
 * @param source fuente activa ("jamendo", "spotify", "radio", "youtube")
 */
@Composable
fun CurrentSongCard(song: Song, source: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = SintoniaGreen.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = SintoniaGreen.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PlayArrow, contentDescription = null,
                        tint = SintoniaGreen, modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title, color = Color.White, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artist, color = SintoniaSubtext, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                color = when (source) {
                    "spotify" -> SintoniaGreen
                    "radio" -> SintoniaPink
                    else -> Color(0xFF4A9EFF)
                },
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    source.replaceFirstChar { it.uppercase() },
                    color = Color.White, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/**
 * Tarjeta de una canción dentro de la cola: número de posición, ícono
 * según fuente, título/artista, botón de reproducir ahora y botón de
 * quitar de la cola.
 *
 * @param index posición de la canción en la cola (1, 2, 3...)
 * @param song canción a mostrar
 * @param onPlay se invoca al presionar reproducir (la saca de la cola y la reproduce)
 * @param onRemove se invoca al presionar quitar (solo la elimina de la cola)
 */
@Composable
fun QueueSongCard(
    index: Int,
    song: Song,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SintoniaCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.DragHandle, contentDescription = null,
                tint = SintoniaSubtext, modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "$index.", color = SintoniaSubtext, fontSize = 13.sp,
                modifier = Modifier.width(24.dp)
            )
            Surface(
                color = SintoniaDark,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.MusicNote, contentDescription = null,
                        tint = when (song.source) {
                            "spotify" -> SintoniaGreen
                            "radio" -> SintoniaPink
                            else -> Color(0xFF4A9EFF)
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title, color = Color.White, fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${song.artist} · ${song.source.replaceFirstChar { it.uppercase() }}",
                    color = SintoniaSubtext, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            // Reproducir
            IconButton(onClick = onPlay) {
                Icon(
                    Icons.Default.PlayArrow, contentDescription = "Reproducir",
                    tint = SintoniaGreen
                )
            }
            // Quitar de la cola
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close, contentDescription = "Quitar",
                    tint = SintoniaSubtext
                )
            }
        }
    }
}
```

## `ui/screens/Favoritesscreen.kt` — #archivo de la pantalla de favoritos

```kotlin
package mx.utng.sintonia.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import mx.utng.sintonia.data.model.Song
import mx.utng.sintonia.ui.theme.SintoniaCard
import mx.utng.sintonia.ui.theme.SintoniaDark
import mx.utng.sintonia.ui.theme.SintoniaGreen
import mx.utng.sintonia.ui.theme.SintoniaPink
import mx.utng.sintonia.ui.theme.SintoniaSubtext
import mx.utng.sintonia.viewmodel.PlayerViewModel

/**
 * Pantalla de canciones favoritas. Muestra un estado vacío si no hay
 * ninguna, o la lista completa si existen, delegando en el ViewModel
 * la reproducción según la fuente de cada canción (Spotify, radio o
 * genérica).
 *
 * @param viewModel ViewModel compartido de toda la app
 * @param navController controlador de navegación (para el botón "Atrás")
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    navController: NavController? = null
) {
    val favorites by viewModel.favorites.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    Scaffold(
        modifier = modifier,
        containerColor = SintoniaDark,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController?.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack, contentDescription = "Atrás",
                            tint = Color.White
                        )
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Favoritos", fontWeight = FontWeight.Bold,
                            color = Color.White, fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = SintoniaPink.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "${favorites.size}", color = SintoniaPink, fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SintoniaDark)
            )
        },
        bottomBar = {
            if (playbackState.currentSong.title.isNotEmpty()) {
                val progress by viewModel.progress.collectAsState()
                val playOnTv by viewModel.playOnTv.collectAsState()
                PlayerBar(
                    song = playbackState.currentSong,
                    isPlaying = playbackState.isPlaying,
                    progress = progress,
                    playOnTv = playOnTv,
                    onTogglePlay = { viewModel.togglePlayPause() },
                    onNext = { viewModel.nextSong() },
                    onPrevious = { viewModel.previousSong() },
                    onToggleTv = { viewModel.togglePlayOnTv() }
                )
            }
        }
    ) { padding ->
        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FavoriteBorder, contentDescription = null,
                        tint = SintoniaSubtext, modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Aún no tienes favoritos",
                        color = SintoniaSubtext, fontSize = 14.sp
                    )
                    Text(
                        "Toca el corazón en cualquier canción para guardarla aquí",
                        color = SintoniaSubtext, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }
                items(favorites) { song ->
                    FavoriteSongCard(
                        song = song,
                        isPlaying = playbackState.currentSong.id == song.id && playbackState.isPlaying,
                        onClick = {
                            when (song.source) {
                                "spotify" -> viewModel.playSongSpotify(song, navController!!.context)
                                "radio" -> viewModel.playRadioStation(
                                    song.id, song.title, song.artist, song.audioUrl
                                )
                                else -> viewModel.playSong(song)
                            }
                        },
                        onRemoveFavorite = { viewModel.toggleFavorite(song) }
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }
}

/**
 * Tarjeta de una canción favorita: portada, título, artista, una
 * etiqueta con la fuente de origen, botón para quitar de favoritos y
 * un ícono que indica si está sonando en ese momento.
 *
 * @param song canción favorita a mostrar
 * @param isPlaying true si es la canción actualmente en reproducción
 * @param onClick se invoca al tocar la tarjeta para reproducirla
 * @param onRemoveFavorite se invoca al presionar el corazón para quitarla de favoritos
 */
@Composable
fun FavoriteSongCard(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onRemoveFavorite: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) SintoniaGreen.copy(alpha = 0.15f) else SintoniaCard
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = song.albumCover,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title, color = Color.White, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        song.artist, color = SintoniaSubtext, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = when (song.source) {
                            "spotify" -> SintoniaGreen.copy(alpha = 0.2f)
                            "radio" -> SintoniaPink.copy(alpha = 0.2f)
                            else -> Color(0xFF4A9EFF).copy(alpha = 0.2f)
                        },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            song.source.replaceFirstChar { it.uppercase() },
                            color = when (song.source) {
                                "spotify" -> SintoniaGreen
                                "radio" -> SintoniaPink
                                else -> Color(0xFF4A9EFF)
                            },
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            IconButton(onClick = onRemoveFavorite) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = "Quitar de favoritos",
                    tint = SintoniaPink
                )
            }
            if (isPlaying) {
                Icon(Icons.Default.Pause, contentDescription = null, tint = SintoniaGreen)
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = SintoniaSubtext)
            }
        }
    }
}
```

## `ui/screens/DownloadsScreen.kt` — #archivo de la pantalla de descargas

```kotlin
package mx.utng.sintonia.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.utng.sintonia.data.model.Song
import mx.utng.sintonia.ui.theme.SintoniaCard
import mx.utng.sintonia.ui.theme.SintoniaDark
import mx.utng.sintonia.ui.theme.SintoniaGreen
import mx.utng.sintonia.ui.theme.SintoniaSubtext
import mx.utng.sintonia.viewmodel.PlayerViewModel

/**
 * Pantalla de "Mis descargas": muestra el espacio usado (barra de
 * almacenamiento sobre 1 GB total) y la lista de canciones descargadas
 * o en progreso, leídas en tiempo real desde el ViewModel.
 *
 * @param viewModel ViewModel compartido de toda la app
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: PlayerViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val downloads by viewModel.downloads.collectAsState()
    val storageUsedMb by viewModel.storageUsedMb.collectAsState()
    val storageTotalMb = viewModel.storageTotalMb

    Scaffold(
        modifier = modifier,
        containerColor = SintoniaDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Mis descargas", fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = SintoniaGreen.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Jamendo CC",
                                color = SintoniaGreen,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SintoniaDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = SintoniaCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Almacenamiento ${storageUsedMb.toInt()} MB / 1 GB",
                        color = SintoniaSubtext,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (storageUsedMb / storageTotalMb).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = SintoniaGreen,
                        trackColor = SintoniaDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (downloads.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aún no tienes descargas", color = SintoniaSubtext)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(downloads) { song ->
                        DownloadCard(
                            song = song,
                            onCancel = { viewModel.cancelarDescarga(song.id) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta individual de una descarga: si ya terminó muestra un check
 * verde; si sigue en progreso muestra el porcentaje y un botón para
 * cancelarla.
 *
 * @param song canción con su estado de descarga (progresoDescarga, descargada)
 * @param onCancel se invoca al cancelar una descarga en progreso
 */
@Composable
fun DownloadCard(song: Song, onCancel: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SintoniaCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, color = Color.White, fontWeight = FontWeight.Medium)
                    Text(
                        if (song.descargada) "${song.tamanoMb} MB"
                        else "Descargando ${song.progresoDescarga}%",
                        color = SintoniaSubtext,
                        fontSize = 12.sp
                    )
                }
                if (song.descargada) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Descargada", tint = SintoniaGreen)
                } else {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancelar descarga", tint = SintoniaSubtext)
                    }
                }
            }
            if (!song.descargada) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { song.progresoDescarga / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = SintoniaGreen,
                    trackColor = SintoniaDark
                )
            }
        }
    }
}
```

## `ui/screens/SettingsScreen.kt` — #archivo de la pantalla de configuración

```kotlin
package mx.utng.sintonia.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.utng.sintonia.ui.theme.SintoniaCard
import mx.utng.sintonia.ui.theme.SintoniaDark
import mx.utng.sintonia.ui.theme.SintoniaGreen
import mx.utng.sintonia.ui.theme.SintoniaSubtext

/**
 * Pantalla de configuración. Los switches (notificaciones, WiFi-only,
 * autoplay) son visuales/locales por ahora — no están conectados a
 * ninguna lógica real del ViewModel todavía, solo reflejan el estado
 * de sus propios `remember { mutableStateOf(...) }`.
 *
 * @param modifier modificador de Compose estándar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    var notificationsEnabled by remember { mutableStateOf(true) }
    var wifiOnlyDownload by remember { mutableStateOf(true) }
    var autoPlay by remember { mutableStateOf(true) }

    Scaffold(
        modifier = modifier,
        containerColor = SintoniaDark,
        topBar = {
            TopAppBar(
                title = {
                    Text("Configuración", fontWeight = FontWeight.Bold,
                        color = Color.White, fontSize = 20.sp)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SintoniaDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Perfil
            Card(
                colors = CardDefaults.cardColors(containerColor = SintoniaCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null,
                        tint = SintoniaGreen, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Usuario Sintonía", color = Color.White,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Jamendo · Creative Commons", color = SintoniaSubtext,
                            fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("DISPOSITIVOS", color = SintoniaSubtext,
                fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            SettingsItem(
                icon = Icons.Default.Watch,
                title = "Smartwatch",
                subtitle = "Wear OS conectado via Firebase",
                iconTint = SintoniaGreen
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsItem(
                icon = Icons.Default.BrightnessAuto,
                title = "Android TV",
                subtitle = "Dashboard en tiempo real activo",
                iconTint = SintoniaGreen
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("PREFERENCIAS", color = SintoniaSubtext,
                fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            SettingsToggle(
                icon = Icons.Default.Notifications,
                title = "Notificaciones",
                subtitle = "Avisar cuando cambia la canción",
                checked = notificationsEnabled,
                onCheckedChange = { notificationsEnabled = it }
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsToggle(
                icon = Icons.Default.Wifi,
                title = "Descargar solo con WiFi",
                subtitle = "Evitar uso de datos móviles",
                checked = wifiOnlyDownload,
                onCheckedChange = { wifiOnlyDownload = it }
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsToggle(
                icon = Icons.Default.Download,
                title = "Reproducción automática",
                subtitle = "Siguiente canción al terminar",
                checked = autoPlay,
                onCheckedChange = { autoPlay = it }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("ACERCA DE", color = SintoniaSubtext,
                fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            SettingsItem(
                icon = Icons.Default.Info,
                title = "Sintonía v1.0",
                subtitle = "Desarrollo para Dispositivos Inteligentes · UTNG",
                iconTint = SintoniaSubtext
            )
        }
    }
}

/**
 * Fila de configuración informativa (sin acción de toggle), con ícono,
 * título, subtítulo y una flecha decorativa a la derecha.
 *
 * @param icon ícono representativo de la opción
 * @param title título de la opción
 * @param subtitle descripción breve
 * @param iconTint color del ícono
 */
@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color = SintoniaGreen
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SintoniaCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = iconTint,
                modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Medium)
                Text(subtitle, color = SintoniaSubtext, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null,
                tint = SintoniaSubtext)
        }
    }
}

/**
 * Fila de configuración con un Switch para activar/desactivar una opción.
 *
 * @param icon ícono representativo de la opción
 * @param title título de la opción
 * @param subtitle descripción breve
 * @param checked estado actual del switch
 * @param onCheckedChange se invoca cuando el usuario cambia el switch
 */
@Composable
fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SintoniaCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = SintoniaGreen,
                modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Medium)
                Text(subtitle, color = SintoniaSubtext, fontSize = 12.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SintoniaGreen,
                    uncheckedThumbColor = SintoniaSubtext,
                    uncheckedTrackColor = SintoniaCard
                )
            )
        }
    }
}
```

---

## `ui/theme/Color.kt` — #archivo de la paleta de colores

```kotlin
package mx.utng.sintonia.ui.theme

import androidx.compose.ui.graphics.Color

/** Paleta de colores de la app, inspirada en Spotify (verde) con acentos rosa. */
val SintoniaGreen = Color(0xFF1DB954)
val SintoniaPink = Color(0xFFFF6B9D)
val SintoniaDark = Color(0xFF121212)
val SintoniaCard = Color(0xFF1E1E1E)
val SintoniaText = Color(0xFFFFFFFF)
val SintoniaSubtext = Color(0xFFB3B3B3)
```

## `ui/theme/Theme.kt` — #archivo del tema global

```kotlin
package mx.utng.sintonia.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SintoniaGreen,
    secondary = SintoniaPink,
    background = SintoniaDark,
    surface = SintoniaCard,
    onPrimary = SintoniaDark,
    onBackground = SintoniaText,
    onSurface = SintoniaText
)

/**
 * Tema global de la app: siempre usa el esquema oscuro (DarkColorScheme),
 * sin soportar modo claro ni colores dinámicos de Android 12+, para
 * mantener la identidad visual de la marca en todos los dispositivos.
 *
 * @param content contenido Composable que se pinta dentro del tema
 */
@Composable
fun SintoniaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
```

## `ui/theme/Type.kt` — #archivo de la tipografía

```kotlin
package mx.utng.sintonia.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)
```

---

## `viewmodel/PlayerViewModel.kt` — #archivo del ViewModel central

Es el archivo más importante del módulo: coordina las 4 fuentes de audio (Jamendo/ExoPlayer, Spotify App Remote, Radio/ExoPlayer, YouTube), la cola, favoritos, descargas, el modo "reproducir en TV", y toda la sincronización con Firebase.

```kotlin
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

/**
 * ViewModel central de la app — es el "cerebro" de todo Sintonía.
 * Coordina las 4 fuentes de audio (Jamendo/ExoPlayer, Spotify App
 * Remote, Radio/ExoPlayer, YouTube vía navegador), mantiene la cola,
 * favoritos y descargas, y sincroniza todo hacia Firebase para que TV
 * y Wear puedan reaccionar.
 */
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
    /**
     * Cambia entre reproducir en el teléfono o en la TV.
     * - Al activar TV: pausa el audio local (ExoPlayer o Spotify) pero
     *   deja isPlaying=true en Firebase para que la TV arranque desde ahí.
     * - Al desactivar TV: retoma el audio local desde donde se haya
     *   quedado el estado (recargando el ExoPlayer si hace falta).
     * - Si la fuente es Spotify, delega todo en transferSpotifyPlayback(),
     *   porque Spotify Connect mueve la reproducción él solo entre
     *   dispositivos.
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
    /**
     * Busca los dispositivos donde el usuario tiene Spotify abierto y
     * transfiere la reproducción a la TV (toTv=true) o de regreso al
     * cel (toTv=false), usando la API REST de Spotify Connect.
     *
     * @param toTv true para transferir a la TV, false para regresar al teléfono
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

    /**
     * Busca videos de YouTube usando YouTubeRepository y publica el
     * resultado en _youtubeVideos para que YouTubeScreen los muestre.
     *
     * @param query texto de búsqueda
     */
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
    /**
     * Firebase es la fuente de verdad de playOnTv. Sin esto, si cierras
     * la app con el modo TV activado, al reabrirla el cel arrancaría
     * creyendo que está en false mientras Firebase sigue en true, y
     * terminaría sonando en los dos lados a la vez.
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

    /**
     * Escucha playback/tvCommand, que la TV escribe cuando el usuario
     * presiona play/pausa desde el control remoto de la TV
     * (FirebaseTvSync.sendPlayPause). Ejecuta la acción real en el
     * teléfono (según la fuente activa) y borra el comando
     * (`setValue(null)`) para que no se vuelva a procesar dos veces.
     */
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

    /**
     * Detecta cuándo el ExoPlayer termina una pista (STATE_ENDED) para
     * avanzar automáticamente a la siguiente canción.
     */
    private fun setupExoPlayerListener() {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    nextSong()
                }
            }
        })
    }

    /**
     * Se suscribe a todos los StateFlow que expone SpotifyPlayerManager
     * (conexión, progreso, duración, pausa, canción actual y fin de
     * pista) y traduce esos cambios al estado general de la app
     * (_playbackState) y a Firebase, para que el resto de la app y los
     * otros dispositivos vean lo mismo que está pasando en Spotify.
     */
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

    /**
     * Bucle infinito (cada 500ms) que lee la posición actual del
     * ExoPlayer, calcula el progreso (0f-1f) y lo publica tanto
     * localmente (_progress) como en Firebase, para las fuentes que usan
     * ExoPlayer (Jamendo y Radio).
     */
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

    /**
     * Escucha el nodo de descargas en Firebase y, además de actualizar
     * la lista local, recalcula el espacio usado (_storageUsedMb)
     * sumando el tamaño de las canciones ya descargadas.
     */
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

    /**
     * Escucha playback/skipSong, que el reloj escribe cuando el usuario
     * presiona siguiente/anterior desde Wear OS. Según la fuente activa,
     * decide si debe avanzar canción o estación de radio, y borra el
     * comando después de procesarlo.
     */
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

    /**
     * Detiene cualquier audio local (Spotify o ExoPlayer) y actualiza el
     * estado a la fuente "youtube" — el video en sí no se reproduce
     * dentro de la app del teléfono (se abre en el navegador desde
     * YouTubeScreen), pero Firebase sí se actualiza para que la TV
     * muestre el video embebido en su propio WebView.
     *
     * @param video video de YouTube a reproducir
     */
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
    /** Carga las pistas populares de Jamendo y las publica en _songs. */
    fun loadPopularTracks() {
        viewModelScope.launch {
            _isLoading.value = true
            _songs.value = jamendoRepo.getPopularTracks()
            _isLoading.value = false
        }
    }

    /**
     * Busca pistas en Jamendo por texto y las publica en _songs.
     * @param query texto de búsqueda
     */
    fun searchTracks(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _songs.value = jamendoRepo.searchTracks(query)
            _isLoading.value = false
        }
    }

    /** Carga las estaciones de radio más votadas y las publica en _radioStations. */
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

    /**
     * Busca estaciones de radio por texto y las publica en _radioStations.
     * @param query texto de búsqueda (ciudad, país o nombre de estación)
     */
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

    /**
     * Guarda el token de Spotify recién obtenido (en SharedPreferences,
     * para no tener que volver a iniciar sesión cada vez), conecta el
     * SpotifyPlayerManager y carga las canciones destacadas.
     *
     * @param token access token obtenido del login OAuth de Spotify
     */
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

    /** Cierra sesión de Spotify: limpia el token guardado, la lista de canciones y desconecta el SpotifyPlayerManager. */
    fun logoutSpotify() {
        _spotifyToken.value = null
        _spotifySongs.value = emptyList()
        _currentSource.value = "jamendo"
        prefs.edit().remove("66f7b9f9a86343ca966251fde4b8bbca").apply()
        spotifyPlayer.disconnect()
    }

    /**
     * Busca canciones en Spotify por texto, validando primero que haya
     * un token de sesión activo.
     * @param query texto de búsqueda
     */
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

    /**
     * Reproduce una canción de Spotify. Si el SpotifyPlayerManager ya
     * está conectado a la app de Spotify, reproduce directo; si no,
     * intenta conectar y además abre la app de Spotify vía Intent como
     * respaldo. Solo controla el reproductor si isLocal() es true —
     * si el modo TV está activo, únicamente actualiza el estado.
     *
     * @param song canción de Spotify a reproducir
     * @param context contexto de Android necesario para lanzar el Intent
     */
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

    /** Fuerza la conexión al SpotifyPlayerManager (usado por la UI cuando el usuario reintenta conectar). */
    fun connectSpotifyPlayer() { spotifyPlayer.connect() }
    fun disconnectSpotifyPlayer() { spotifyPlayer.disconnect() }

    /** Detiene y limpia el ExoPlayer, y pausa Spotify si estaba activo — se usa antes de cambiar a una fuente distinta. */
    private fun stopAll() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        if (spotifyPlayer.isConnected.value) {
            spotifyPlayer.pause()
        }
    }

    /**
     * Reproduce una canción de Jamendo. Pausa Spotify si estaba sonando,
     * y solo toca el ExoPlayer local si isLocal() es true (si el modo TV
     * está activo, la TV es quien realmente reproduce el audio).
     *
     * @param song canción de Jamendo a reproducir
     */
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

    /**
     * Reproduce una estación de radio en vivo. Detiene cualquier otra
     * fuente primero (stopAll) y solo carga el stream en el ExoPlayer
     * si isLocal() es true.
     *
     * @param id identificador de la estación
     * @param name nombre de la estación
     * @param city ciudad/país de la estación (se usa como "artista")
     * @param streamUrl URL del stream de audio en vivo
     */
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

    /**
     * Agrega una canción a la cola local (evitando duplicados) y
     * sincroniza los primeros 3 elementos hacia Firebase.
     * @param song canción a encolar
     */
    fun addToQueue(song: Song) {
        if (_queue.value.none { it.id == song.id }) {
            _queue.value = _queue.value + song
            firebaseRepo.updateQueue(_queue.value)
        }
    }

    /**
     * Quita una canción de la cola por su id y sincroniza el cambio a Firebase.
     * @param songId id de la canción a remover
     */
    fun removeFromQueue(songId: String) {
        _queue.value = _queue.value.filter { it.id != songId }
        firebaseRepo.updateQueue(_queue.value)
    }

    /** Vacía la cola completa y sincroniza el cambio a Firebase. */
    fun clearQueue() {
        _queue.value = emptyList()
        firebaseRepo.updateQueue(emptyList())
    }


    /**
     * Reproduce una canción específica de la cola, delegando en la
     * lógica correcta según su fuente (Spotify, Radio, o Jamendo/genérico),
     * y la quita de la cola al terminar.
     *
     * @param song canción de la cola a reproducir ahora
     */
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

    /**
     * Agrega o quita una canción de favoritos. No modifica el StateFlow
     * local directamente: escribe en Firebase y deja que
     * listenForFavorites() actualice el estado cuando llegue el cambio
     * confirmado del servidor.
     *
     * @param song canción a marcar/desmarcar como favorita
     */
    fun toggleFavorite(song: Song) {
        // No mutamos _favorites directo: escribimos a Firebase y dejamos que
        // listenForFavorites() actualice el StateFlow cuando llegue el cambio.
        if (_favorites.value.any { it.id == song.id }) {
            firebaseRepo.removeFavorite(song.id)
        } else {
            firebaseRepo.saveFavorite(song)
        }
    }

    /** Escucha el nodo de favoritos en Firebase y actualiza _favorites con la lista completa cada vez que cambia. */
    private fun listenForFavorites() {
        viewModelScope.launch {
            firebaseRepo.observeFavorites().collect { list ->
                _favorites.value = list
            }
        }
    }

    /**
     * Alterna entre reproducir y pausar. Si isLocal() es true, controla
     * directamente el reproductor que corresponda según la fuente
     * (Spotify, Radio con ExoPlayer, o Jamendo/YouTube con ExoPlayer).
     * Si el modo TV está activo, solo actualiza el estado — la TV
     * reacciona sola al cambio en Firebase.
     */
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

    /**
     * Avanza a la siguiente canción. Primero revisa si hay algo en la
     * cola (tiene prioridad sobre cualquier fuente); si la cola está
     * vacía, usa el control nativo de Spotify o avanza en la lista local
     * de _songs según corresponda.
     */
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

    /**
     * Retrocede a la canción anterior, con la misma lógica que
     * nextSong() pero en sentido contrario (sin revisar la cola, ya que
     * la cola es solo de próximas canciones).
     */
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

    /**
     * Simula una descarga progresiva (10% cada 300ms) guardando el
     * avance en Firebase en cada paso, hasta marcarla como completada
     * (descargada=true) al llegar a 100%.
     *
     * @param song canción de Jamendo a descargar
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

    /** Avanza a la siguiente estación dentro de la lista de estaciones cargadas (_radioStations), en modo circular. */
    fun nextRadioStation() {
        val stationList = _radioStations.value
        if (stationList.isEmpty()) return
        val currentIndex = stationList.indexOfFirst { it.id == _playbackState.value.currentSong.id }
        val nextIndex = (currentIndex + 1) % stationList.size
        val next = stationList[nextIndex]
        playRadioStation(next.id, next.name, next.city, next.streamUrl)
    }

    /** Retrocede a la estación anterior dentro de la lista de estaciones cargadas (_radioStations), en modo circular. */
    fun previousRadioStation() {
        val stationList = _radioStations.value
        if (stationList.isEmpty()) return
        val currentIndex = stationList.indexOfFirst { it.id == _playbackState.value.currentSong.id }
        val prevIndex = if (currentIndex <= 0) stationList.size - 1 else currentIndex - 1
        val prev = stationList[prevIndex]
        playRadioStation(prev.id, prev.name, prev.city, prev.streamUrl)
    }
    /** Cancela una descarga en progreso, eliminándola de Firebase. */
    fun cancelarDescarga(songId: String) { firebaseRepo.removeDownload(songId) }
    /** Elimina una descarga ya completada, quitándola de Firebase. */
    fun eliminarDescarga(songId: String) { firebaseRepo.removeDownload(songId) }
    /** Cambia la fuente activa mostrada en la UI (no reproduce nada por sí sola, solo actualiza el StateFlow de la fuente seleccionada). */
    fun setSource(source: String) { _currentSource.value = source }
    /** Calcula el porcentaje de almacenamiento usado por las descargas, sobre el límite total (storageTotalMb). */
    fun getPorcentajeUso(): Float = (_storageUsedMb.value / storageTotalMb) * 100f

    /**
     * Libera el ExoPlayer y desconecta Spotify cuando el ViewModel se
     * destruye, para no dejar recursos de audio activos en segundo plano.
     */
    override fun onCleared() {
        super.onCleared()
        exoPlayer.release()
        spotifyPlayer.disconnect()
    }
}
```

---

## 🔄 Resumen del flujo de datos del módulo app

1. El usuario elige una fuente en `HomeScreen` (Spotify, Jamendo, Radio o YouTube) y navega a su pantalla correspondiente.
2. Cada pantalla llama a funciones del `PlayerViewModel` (`playSong`, `playSongSpotify`, `playRadioStation`, `playYouTubeVideo`) para iniciar la reproducción.
3. El ViewModel decide, según `isLocal()` (es decir, según la bandera `playOnTv`), si debe controlar el reproductor local (ExoPlayer o Spotify App Remote) o dejar que sea la TV quien reproduzca.
4. En cualquier caso, el ViewModel actualiza `FirebaseRepository`, que escribe en los nodos `playback`, `descargas` y `favoritos` de Firebase.
5. Los módulos `tv` y `wear` escuchan esos mismos nodos y reaccionan: la TV reproduce el audio si le corresponde, y ambos actualizan su UI para reflejar lo que está pasando.
6. Los comandos que llegan desde TV o Wear (`tvCommand`, `skipSong`, `playOnTv`) se escuchan de vuelta en el propio `PlayerViewModel`, cerrando el ciclo de comunicación entre los tres dispositivos.
