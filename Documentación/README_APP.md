# Módulo `:app` — Smartphone (Hub Central)
### Sinfonía — Control Multimedia Inteligente para el Ecosistema Digital
**Materia:** Desarrollo para dispositivos inteligentes  
**Grupo:** GIDS6093  
**Alumnas:** Medrano Hernández Vanesa Monserrat · Tapia Cid Laura Berenice  
**Docente:** Rodríguez García Anastacio  
 
---
 
## Descripción General
 
El módulo `:app` es el **núcleo (hub central)** del sistema Sinfonía. Concentra toda la lógica de negocio, consume las APIs externas de música y video, gestiona la reproducción de audio con ExoPlayer y Spotify App Remote SDK, y sincroniza el estado de reproducción en tiempo real con Firebase Realtime Database hacia el smartwatch y el Android TV.
 
### Responsabilidades principales
- Reproducir música de Jamendo, Spotify, Radio Browser y YouTube
- Publicar el estado del reproductor en Firebase para que TV y Wear lo lean
- Escuchar y ejecutar comandos enviados desde el smartwatch y la TV
- Gestionar la cola de reproducción, descargas y favoritos
---
 
## Estructura de Carpetas
 
```
app/src/main/java/mx/utng/sintonia/
│
├── data/
│   ├── firebase/
│   │   └── FirebaseRepository.kt
│   ├── model/
│   │   ├── PlaybackState.kt
│   │   └── Song.kt
│   └── remote/
│       ├── JamendoApi.kt
│       ├── JamendoRepository.kt
│       ├── RadioBrowserService.kt
│       ├── RadioRepository.kt
│       ├── SpotifyAuthManager.kt
│       ├── SpotifyPlayerManager.kt
│       ├── SpotifyRepository.kt
│       └── YouTubeRepository.kt
│
├── ui/
│   ├── navigation/
│   │   ├── AppNavigation.kt
│   │   └── Screen.kt
│   ├── screens/
│   │   ├── HomeScreen.kt
│   │   ├── JamendoScreen.kt
│   │   ├── RadioScreen.kt
│   │   ├── SpotifyScreen.kt
│   │   ├── YouTubeScreen.kt
│   │   ├── QueueScreen.kt
│   │   ├── DownloadsScreen.kt
│   │   └── SettingsScreen.kt
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
│
├── viewmodel/
│   └── PlayerViewModel.kt
│
└── MainActivity.kt
```
 
---
 
## Capa de Datos (`data/`)
 
### `data/model/Song.kt`
 
**Descripción:** Modelo de datos que representa una canción o estación de radio en cualquier fuente del sistema.
 
```kotlin
data class Song(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val albumCover: String = "",
    val audioUrl: String = "",
    val duration: Int = 0,
    val source: String = "",
    val descargada: Boolean = false,
    val progresoDescarga: Int = 0,
    val tamanoMb: Float = 0f
)
```
 
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | `String` | Identificador único de la canción (ID de Jamendo, UUID de Radio Browser, etc.) |
| `title` | `String` | Título de la canción o nombre de la estación de radio |
| `artist` | `String` | Nombre del artista o ciudad de la estación |
| `albumCover` | `String` | URL de la imagen de portada del álbum o thumbnail |
| `audioUrl` | `String` | URL del stream de audio o URI de Spotify (`spotify:track:...`) |
| `duration` | `Int` | Duración en segundos. Para radio siempre es 0 |
| `source` | `String` | Fuente de origen: `"jamendo"`, `"spotify"`, `"radio"`, `"youtube"` |
| `descargada` | `Boolean` | Indica si la canción ya fue descargada localmente (solo Jamendo) |
| `progresoDescarga` | `Int` | Progreso de descarga de 0 a 100 |
| `tamanoMb` | `Float` | Tamaño estimado del archivo en megabytes |
 
---
 
### `data/model/PlaybackState.kt`
 
**Descripción:** Modelo del estado global del reproductor. Se serializa y publica en Firebase Realtime Database para que Android TV y Wear OS lo lean en tiempo real.
 
```kotlin
data class PlaybackState(
    var isPlaying: Boolean = false,
    var currentSong: Song = Song(),
    var volume: Int = 70,
    var source: String = "jamendo"
)
```
 
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `isPlaying` | `Boolean` | `true` si hay reproducción activa, `false` si está pausado |
| `currentSong` | `Song` | Objeto completo de la canción que se está reproduciendo |
| `volume` | `Int` | Volumen actual del sistema (0-100) |
| `source` | `String` | Fuente activa de reproducción |
 
> **Nota técnica:** La anotación `@get:PropertyName("isPlaying")` y `@set:PropertyName("isPlaying")` es necesaria para que Firebase Realtime Database serialice correctamente el campo booleano con Kotlin, ya que el compilador genera `isIsPlaying` por convención en lugar de `isPlaying`.
 
---
 
### `data/firebase/FirebaseRepository.kt`
 
**Descripción:** Repositorio central de comunicación con Firebase Realtime Database. Gestiona toda la escritura y lectura del estado de reproducción y las descargas.
 
**Estructura en Firebase:**
```json
{
  "playback": {
    "isPlaying": true,
    "source": "jamendo",
    "progress": 0.45,
    "currentSong": {
      "id": "123",
      "title": "Morning Groove",
      "artist": "Artista",
      "albumCover": "https://...",
      "audioUrl": "https://...",
      "duration": 240,
      "source": "jamendo"
    },
    "queue": [
      { "title": "...", "artist": "..." }
    ],
    "skipSong": null,
    "tvCommand": null
  },
  "descargas": {
    "songId123": { ... }
  }
}
```
 
#### Propiedades
 
| Propiedad | Tipo | Descripción |
|-----------|------|-------------|
| `db` | `DatabaseReference` | Referencia al nodo `playback` en Firebase |
| `dbDescargas` | `DatabaseReference` | Referencia al nodo `descargas` en Firebase |
 
#### Métodos
 
---
 
##### `observePlaybackState(): Flow<PlaybackState>`
**Descripción:** Observa en tiempo real los cambios en el estado del reproductor en Firebase.  
**Retorna:** `Flow<PlaybackState>` — emite un nuevo valor cada vez que Firebase detecta un cambio en el nodo `playback`.  
**Uso:** Utilizado por el TV y el Wear para mantenerse sincronizados con el smartphone.
 
```kotlin
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
```
 
---
 
##### `updatePlaybackState(state: PlaybackState)`
**Descripción:** Escribe el estado completo del reproductor en Firebase. Se llama cada vez que cambia la canción, la fuente o el estado de reproducción.  
**Parámetros:**
 
| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `state` | `PlaybackState` | Estado completo a publicar en Firebase |
 
---
 
##### `updateIsPlaying(isPlaying: Boolean)`
**Descripción:** Actualiza únicamente el campo `isPlaying` en Firebase sin reescribir todo el objeto, lo que reduce el consumo de ancho de banda.  
**Parámetros:**
 
| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `isPlaying` | `Boolean` | `true` para reproduciendo, `false` para pausado |
 
---
 
##### `updateCurrentSong(song: Song)`
**Descripción:** Actualiza únicamente el nodo `currentSong` en Firebase. Se usa cuando Spotify cambia de canción automáticamente y se necesita actualizar solo la información de la canción.  
**Parámetros:**
 
| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `song` | `Song` | Objeto Song con los datos actualizados de la canción |
 
---
 
##### `updateProgress(progress: Float)`
**Descripción:** Publica el progreso actual de reproducción en Firebase para que la TV lo muestre en su barra de progreso.  
**Parámetros:**
 
| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `progress` | `Float` | Valor entre 0.0 y 1.0 representando el porcentaje de avance |
 
---
 
##### `observeDownloads(): Flow<List<Song>>`
**Descripción:** Observa en tiempo real la lista de canciones descargadas almacenadas en Firebase bajo el nodo `descargas`.  
**Retorna:** `Flow<List<Song>>` — emite la lista completa cada vez que hay un cambio.
 
---
 
##### `saveDownload(song: Song)`
**Descripción:** Guarda o actualiza una canción en el nodo `descargas` de Firebase usando su `id` como clave.  
**Parámetros:**
 
| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `song` | `Song` | Canción a guardar, incluyendo `progresoDescarga` y `descargada` |
 
---
 
##### `removeDownload(songId: String)`
**Descripción:** Elimina una canción del nodo `descargas` en Firebase.  
**Parámetros:**
 
| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `songId` | `String` | ID de la canción a eliminar |
 
---
 
### `data/remote/JamendoApi.kt`
 
**Descripción:** Interfaz Retrofit que define los endpoints de la API REST de Jamendo para obtener música gratuita bajo licencia Creative Commons.
 
**Base URL:** `https://api.jamendo.com/v3.0/`
 
#### Endpoints
 
##### `searchTracks(clientId, format, limit, search, audioFormat, imageSize): JamendoApiResponse`
**Descripción:** Busca canciones por nombre, artista o género.  
**Parámetros query:**
 
| Parámetro | Tipo | Valor por defecto | Descripción |
|-----------|------|-------------------|-------------|
| `client_id` | `String` | - | Clave de API de Jamendo |
| `format` | `String` | `"json"` | Formato de respuesta |
| `limit` | `Int` | `20` | Número máximo de resultados |
| `search` | `String` | - | Término de búsqueda |
| `audioformat` | `String` | `"mp32"` | Formato de audio del stream |
| `imagesize` | `Int` | `500` | Tamaño en px de las imágenes de portada |
 
##### `getFeaturedTracks(clientId, format, limit, order, audioFormat, imageSize): JamendoApiResponse`
**Descripción:** Obtiene canciones populares ordenadas por popularidad total.  
**Parámetros query:**
 
| Parámetro | Tipo | Valor por defecto | Descripción |
|-----------|------|-------------------|-------------|
| `order` | `String` | `"popularity_total"` | Criterio de ordenamiento |
 
---
 
### `data/remote/JamendoRepository.kt`
 
**Descripción:** Repositorio que consume `JamendoApi` y transforma las respuestas en objetos `Song` para el ViewModel. Crea internamente su propia instancia de Retrofit sin inyección de dependencias.
 
#### Métodos
 
##### `getPopularTracks(): List<Song>`
**Descripción:** Obtiene las 20 canciones más populares de Jamendo al abrir la pantalla de Jamendo.  
**Retorna:** Lista de objetos `Song` con url de stream, portada y duración.
 
##### `searchTracks(query: String): List<Song>`
**Descripción:** Busca canciones por término de texto en la API de Jamendo.  
**Parámetros:**
 
| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `query` | `String` | Texto de búsqueda (nombre de canción, artista, álbum) |
 
**Retorna:** Lista de `Song` que coinciden con el término de búsqueda.
 
---
 
### `data/remote/RadioBrowserService.kt`
 
**Descripción:** Interfaz Retrofit para la Radio Browser API, una base de datos open source con más de 30,000 estaciones de radio de todo el mundo. No requiere API key.
 
**Base URL:** `https://de1.api.radio-browser.info/`
 
#### Endpoints
 
##### `getTopStations(): List<RadioBrowserStation>`
**Descripción:** Obtiene las 20 estaciones de radio más populares por votos de la comunidad.  
**Query params fijos:** `hidebroken=true`, `order=votes`, `limit=20`
 
##### `searchStations(name, limit, hideBroken, order): List<RadioBrowserStation>`
**Descripción:** Busca estaciones de radio por nombre, ciudad o país.  
**Parámetros:**
 
| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `name` | `String` | Nombre de la estación, ciudad o país |
| `limit` | `Int` | Máximo de resultados (por defecto 20) |
| `hideBroken` | `Boolean` | Si `true`, oculta estaciones con streams caídos |
 
---
 
### `data/remote/RadioRepository.kt`
 
**Descripción:** Repositorio que consume `RadioBrowserService` y convierte los resultados en objetos `RadioStation` usables por el ViewModel. La propiedad `url_resolved` de Radio Browser ya contiene la URL directa del stream, por lo que no se requieren redirecciones adicionales.
 
#### Métodos
 
##### `getTopStations(): List<RadioStation>`
**Descripción:** Obtiene estaciones populares y filtra las que tienen URL vacía.
 
##### `searchStations(query: String): List<RadioStation>`
**Descripción:** Busca estaciones por nombre y filtra resultados sin URL.
 
---
 
### `data/remote/SpotifyAuthManager.kt`
 
**Descripción:** Objeto singleton que configura y gestiona la autenticación OAuth 2.0 con Spotify. Genera la solicitud de autorización que abre el flujo de login de Spotify.
 
#### Constantes
 
| Constante | Descripción |
|-----------|-------------|
| `CLIENT_ID` | ID de cliente registrado en Spotify Developer Dashboard |
| `REDIRECT_URI` | URI de redirección configurado en el Dashboard (`mx.utng.sintonia://callback`) |
| `REQUEST_CODE` | Código de solicitud para identificar el resultado en `onActivityResult` |
 
#### Métodos
 
##### `getAuthRequest(): AuthorizationRequest`
**Descripción:** Construye y retorna la solicitud de autorización de Spotify con los scopes necesarios.  
**Scopes solicitados:** `streaming`, `user-read-playback-state`, `user-modify-playback-state`  
**Retorna:** `AuthorizationRequest` listo para lanzar con `AuthorizationClient.createLoginActivityIntent()`
 
---
 
### `data/remote/SpotifyPlayerManager.kt`
 
**Descripción:** Clase que gestiona la conexión con Spotify App Remote SDK para reproducción de música dentro de la app sin salir a la aplicación oficial de Spotify. Requiere cuenta Spotify Premium.
 
Implementa un **timer local de interpolación de progreso** para que la barra de progreso avance suavemente sin depender del SDK, que solo dispara eventos cuando cambia el estado.
 
#### Propiedades (StateFlow)
 
| Propiedad | Tipo | Descripción |
|-----------|------|-------------|
| `isConnected` | `StateFlow<Boolean>` | `true` cuando la conexión con Spotify App Remote está activa |
| `currentTrackName` | `StateFlow<String>` | Nombre de la canción actualmente en reproducción |
| `currentArtist` | `StateFlow<String>` | Nombre del artista de la canción actual |
| `currentTrackUri` | `StateFlow<String>` | URI de Spotify de la canción actual (`spotify:track:...`) |
| `currentAlbumCover` | `StateFlow<String>` | URI de la imagen de portada del álbum |
| `progress` | `StateFlow<Float>` | Progreso de reproducción interpolado localmente (0.0-1.0) |
| `duration` | `StateFlow<Long>` | Duración de la canción en milisegundos |
| `isPaused` | `StateFlow<Boolean>` | `true` si la reproducción está pausada |
 
#### Variables internas de interpolación
 
| Variable | Tipo | Descripción |
|----------|------|-------------|
| `lastPlaybackPosition` | `Long` | Posición en ms recibida del último evento del SDK |
| `lastEventTime` | `Long` | Timestamp en ms de cuándo llegó el último evento |
| `progressJob` | `Job?` | Coroutine del timer de interpolación, cancelable |
 
#### Métodos
 
##### `connect()`
**Descripción:** Establece la conexión con Spotify App Remote usando las credenciales de `SpotifyAuthManager`. Al conectar exitosamente, suscribe al `PlayerState` de Spotify para recibir eventos de cambio de canción, progreso y pausa/reproducción.  
**Comportamiento al conectar:**
1. Actualiza `isConnected` a `true`
2. Inicia la suscripción a `playerApi.subscribeToPlayerState()`
3. Por cada evento recibido: actualiza artista, URI, duración, estado de pausa y portada
4. Cancela el timer anterior e inicia uno nuevo con `startProgressTimer()`
##### `startProgressTimer(duration: Long)` *(privado)*
**Descripción:** Lanza una coroutine que cada 500ms calcula el progreso estimado basándose en el tiempo transcurrido desde el último evento del SDK, sin depender de nuevas respuestas de Spotify.
 
**Fórmula:**
```kotlin
val elapsed = System.currentTimeMillis() - lastEventTime
val estimatedPosition = lastPlaybackPosition + elapsed
_progress.value = (estimatedPosition / duration).coerceIn(0f, 1f)
```
 
**Parámetros:**
 
| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `duration` | `Long` | Duración total de la canción en milisegundos |
 
##### `playSong(spotifyUri: String)`
**Descripción:** Envía el comando de reproducción al reproductor nativo de Spotify.  
**Parámetros:**
 
| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `spotifyUri` | `String` | URI de Spotify con formato `spotify:track:TRACK_ID` |
 
##### `pause()`
**Descripción:** Pausa la reproducción de Spotify y cancela el timer de interpolación de progreso.
 
##### `resume()`
**Descripción:** Reanuda la reproducción y reinicia el timer de interpolación desde la posición actual.
 
##### `skipNext()`
**Descripción:** Salta a la siguiente canción en la cola de Spotify.
 
##### `skipPrevious()`
**Descripción:** Regresa a la canción anterior o reinicia la canción actual.
 
##### `addToQueue(spotifyUri: String)`
**Descripción:** Agrega una canción a la cola de reproducción de Spotify.
 
##### `disconnect()`
**Descripción:** Cancela el timer, cancela el scope de coroutines y desconecta Spotify App Remote liberando recursos.
 
---
 
### `data/remote/SpotifyRepository.kt`
 
**Descripción:** Repositorio que consume la Spotify Web API para búsqueda de canciones usando el token OAuth 2.0 obtenido por `SpotifyAuthManager`.
 
**Base URL:** `https://api.spotify.com/v1/`
 
#### Métodos
 
##### `searchTracks(query: String, token: String): List<Song>`
**Descripción:** Busca canciones en el catálogo de Spotify usando el endpoint `/search`.  
**Parámetros:**
 
| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `query` | `String` | Término de búsqueda |
| `token` | `String` | Token de acceso OAuth 2.0. Si no empieza con `"Bearer "`, se agrega automáticamente |
 
**Retorna:** Lista de `Song` con `audioUrl` en formato `spotify:track:TRACK_ID` para reproducción con App Remote SDK.
 
##### `getFeaturedTracks(token: String): List<Song>`
**Descripción:** Busca canciones populares con la query `"top hits 2024"` para mostrar contenido al abrir la pantalla de Spotify por primera vez.  
**Parámetros:**
 
| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `token` | `String` | Token de acceso OAuth 2.0 |
 
---
 
## ViewModel (`viewmodel/`)
 
### `viewmodel/PlayerViewModel.kt`
 
**Descripción:** El ViewModel central de toda la aplicación. Hereda de `AndroidViewModel` para tener acceso al `Application context` necesario para inicializar ExoPlayer y SpotifyPlayerManager. Centraliza toda la lógica de reproducción, coordinación entre fuentes y sincronización con Firebase.
 
#### Dependencias internas
 
| Propiedad | Tipo | Descripción |
|-----------|------|-------------|
| `jamendoRepo` | `JamendoRepository` | Repositorio de Jamendo |
| `spotifyRepository` | `SpotifyRepository` | Repositorio de búsqueda de Spotify |
| `firebaseRepo` | `FirebaseRepository` | Repositorio de Firebase |
| `radioRepo` | `RadioRepository` | Repositorio de Radio Browser |
| `exoPlayer` | `ExoPlayer` | Reproductor de audio para Jamendo y Radio |
| `spotifyPlayer` | `SpotifyPlayerManager` | Gestor de Spotify App Remote |
| `appContext` | `Context` | Contexto de la aplicación para Intent sin Activity |
| `prefs` | `SharedPreferences` | Almacena el token de Spotify entre sesiones |
 
#### StateFlows expuestos a la UI
 
| StateFlow | Tipo | Descripción |
|-----------|------|-------------|
| `songs` | `List<Song>` | Canciones de Jamendo (búsqueda o populares) |
| `spotifySongs` | `List<Song>` | Canciones encontradas en Spotify |
| `playbackState` | `PlaybackState` | Estado actual del reproductor (canción, fuente, isPlaying) |
| `isLoading` | `Boolean` | `true` mientras se cargan datos de una API |
| `downloads` | `List<Song>` | Canciones guardadas en Firebase bajo el nodo `descargas` |
| `storageUsedMb` | `Float` | MB totales usados por canciones descargadas |
| `spotifyToken` | `String?` | Token OAuth 2.0 activo de Spotify, `null` si no está conectado |
| `currentSource` | `String` | Fuente activa: `"jamendo"`, `"spotify"`, `"radio"`, `"youtube"` |
| `radioStations` | `List<RadioStation>` | Estaciones de Radio Browser API |
| `progress` | `Float` | Progreso de ExoPlayer (Jamendo/Radio) de 0.0 a 1.0 |
| `spotifyProgress` | `Float` | Progreso interpolado de Spotify de 0.0 a 1.0 |
| `spotifyDuration` | `Long` | Duración en ms de la canción de Spotify |
| `spotifyConnected` | `Boolean` | Estado de conexión con Spotify App Remote |
| `queue` | `List<Song>` | Cola de reproducción local |
| `favorites` | `List<Song>` | Canciones marcadas como favoritas (Spotify) |
| `youtubeVideos` | `List<YouTubeVideo>` | Videos encontrados en YouTube |
 
#### Bloque `init`
 
Al instanciarse el ViewModel ejecuta:
1. Recupera el token de Spotify guardado en `SharedPreferences` y reconecta si existe
2. `loadPopularTracks()` — carga las canciones populares de Jamendo
3. `listenForWearCommands()` — escucha comandos del smartwatch en Firebase
4. `listenForDownloads()` — observa descargas en Firebase
5. `trackProgress()` — inicia el timer de progreso de ExoPlayer
6. `observeSpotifyState()` — suscribe a los StateFlows de SpotifyPlayerManager
7. `setupExoPlayerListener()` — configura el listener de fin de canción
---
 
#### Métodos privados
 
##### `setupExoPlayerListener()`
**Descripción:** Registra un `Player.Listener` en ExoPlayer que detecta cuando una canción termina (`STATE_ENDED`) y llama automáticamente a `nextSong()` para continuar con la cola o la siguiente canción.
 
##### `observeSpotifyState()`
**Descripción:** Lanza 5 coroutines que colectan los StateFlows de `SpotifyPlayerManager`:
- `isConnected` → actualiza `_spotifyConnected`
- `progress` → actualiza `_spotifyProgress` y publica en Firebase con `updateProgress()`
- `duration` → actualiza `_spotifyDuration`
- `isPaused` → actualiza `isPlaying` en `_playbackState` y Firebase
- `currentTrackName` → actualiza canción actual en `_playbackState` y Firebase cuando Spotify cambia de pista automáticamente
##### `trackProgress()`
**Descripción:** Coroutine infinita que cada 500ms lee la posición actual de ExoPlayer y calcula el progreso cuando hay reproducción activa de Jamendo o Radio. También publica el progreso en Firebase.
 
```kotlin
while (true) {
    if (exoPlayer.isPlaying && exoPlayer.duration > 0) {
        val p = exoPlayer.currentPosition.toFloat() / exoPlayer.duration.toFloat()
        _progress.value = p
        firebaseRepo.updateProgress(p)
    }
    delay(500)
}
```
 
##### `listenForDownloads()`
**Descripción:** Colecta el Flow de `firebaseRepo.observeDownloads()` y actualiza `_downloads` y `_storageUsedMb` sumando el tamaño de las canciones marcadas como descargadas.
 
##### `listenForWearCommands()`
**Descripción:** Registra un `ValueEventListener` en Firebase en el nodo `playback/skipSong`. Cuando el smartwatch escribe `"next"` o `"previous"`, el ViewModel ejecuta `nextSong()` o `previousSong()` respectivamente y luego borra el valor con `setValue(null)` para evitar ejecuciones repetidas.
 
---
 
#### Métodos públicos — Carga de datos
 
##### `loadPopularTracks()`
**Descripción:** Carga las canciones más populares de Jamendo de forma asíncrona y actualiza `_songs`.
 
##### `searchTracks(query: String)`
**Descripción:** Busca canciones en Jamendo y actualiza `_songs`.  
**Parámetros:** `query` — término de búsqueda.
 
##### `loadTopRadioStations()`
**Descripción:** Obtiene las estaciones de radio más populares desde Radio Browser API y actualiza `_radioStations`. Si falla, registra el error sin crashear la app.
 
##### `searchRadioStations(query: String)`
**Descripción:** Busca estaciones de radio por nombre, ciudad o país.  
**Parámetros:** `query` — término de búsqueda.
 
##### `searchSpotifyTracks(query: String)`
**Descripción:** Busca canciones en Spotify usando el token actual. Si no hay token o la query está vacía, registra un error y no hace la búsqueda.
 
##### `searchYouTubeVideos(query: String)`
**Descripción:** Busca videos en YouTube Data API v3 y actualiza `_youtubeVideos`.
 
---
 
#### Métodos públicos — Reproducción
 
##### `playSong(song: Song)`
**Descripción:** Reproduce una canción de Jamendo usando ExoPlayer. Si había reproducción de Spotify activa, la pausa primero.
 
**Flujo:**
1. Pausa Spotify si estaba activo
2. Detiene y limpia ExoPlayer
3. Carga el `audioUrl` como `MediaItem`
4. Prepara y reproduce con `playWhenReady = true`
5. Reinicia `_progress` a 0
6. Actualiza `_playbackState` y publica en Firebase
**Parámetros:**
 
| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `song` | `Song` | Canción de Jamendo a reproducir |
 
---
 
##### `playSongSpotify(song: Song, context: Context)`
**Descripción:** Reproduce una canción de Spotify. Si el App Remote está conectado, usa `spotifyPlayer.playSong()`. Si no está conectado, intenta reconectar y abre la app nativa de Spotify con un Intent como fallback.
 
**Parámetros:**
 
| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `song` | `Song` | Canción de Spotify con `audioUrl` en formato `spotify:track:...` |
| `context` | `Context` | Contexto de la Activity para lanzar el Intent de Spotify |
 
---
 
##### `playRadioStation(id: String, name: String, city: String, streamUrl: String)`
**Descripción:** Reproduce un stream de radio en vivo usando ExoPlayer. Soporta streams MP3 directos y HLS (`.m3u8`) gracias a la dependencia `media3-exoplayer-hls`.
 
**Parámetros:**
 
| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `id` | `String` | UUID de la estación en Radio Browser |
| `name` | `String` | Nombre de la estación de radio |
| `city` | `String` | Ciudad o país de la estación |
| `streamUrl` | `String` | URL directa del stream de audio |
 
---
 
##### `playYouTubeVideo(video: YouTubeVideo)`
**Descripción:** No reproduce el video en la app (YouTube lo prohíbe), pero actualiza el estado en Firebase para que la TV muestre la información del video. También detiene ExoPlayer y pausa Spotify.
 
**Parámetros:**
 
| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `video` | `YouTubeVideo` | Video de YouTube con título, canal y thumbnail |
 
---
 
##### `togglePlayPause()`
**Descripción:** Alterna entre reproducción y pausa según la fuente activa.
- **Spotify:** Llama a `spotifyPlayer.pause()` o `spotifyPlayer.resume()`
- **Otras fuentes:** Llama a `exoPlayer.pause()` o `exoPlayer.play()`
En ambos casos actualiza `_playbackState` y Firebase.
 
---
 
##### `nextSong()`
**Descripción:** Avanza a la siguiente canción respetando este orden de prioridad:
1. Si la fuente es Spotify → `spotifyPlayer.skipNext()`
2. Si hay canciones en la cola local → `playFromQueue(_queue.value.first())`
3. Si no hay cola → siguiente canción en `_songs` de Jamendo de forma cíclica
---
 
##### `previousSong()`
**Descripción:** Retrocede a la canción anterior.
- **Spotify:** `spotifyPlayer.skipPrevious()`
- **Otras fuentes:** Canción anterior en `_songs`, con wrap-around al final si está en la primera
---
 
#### Métodos públicos — Cola de reproducción
 
##### `addToQueue(song: Song)`
**Descripción:** Agrega una canción a la cola local `_queue`. Verifica que no esté duplicada por `id` antes de agregar.
 
##### `removeFromQueue(songId: String)`
**Descripción:** Elimina una canción de la cola por su `id`.
 
##### `clearQueue()`
**Descripción:** Vacía completamente la cola de reproducción.
 
##### `playFromQueue(song: Song)`
**Descripción:** Reproduce una canción de la cola y la elimina de ella después de iniciar la reproducción. Maneja los tres casos de fuente:
- `"spotify"` → usa App Remote o Intent fallback
- `"radio"` → usa `playRadioStation()`
- Cualquier otra → usa ExoPlayer directamente
---
 
#### Métodos públicos — Favoritos
 
##### `toggleFavorite(song: Song)`
**Descripción:** Agrega o quita una canción de `_favorites`. Si ya está en favoritos la elimina, si no, la agrega.
 
---
 
#### Métodos públicos — Descargas
 
##### `downloadSong(song: Song)`
**Descripción:** Simula el proceso de descarga de una canción de Jamendo en Firebase. Verifica que no esté ya en `_downloads`, guarda el estado inicial con `progresoDescarga = 0` y luego actualiza el progreso en incrementos de 10% cada 300ms hasta llegar al 100%.
 
> **Nota:** La descarga es una simulación del flujo. En producción se integraría con el `audiodownload` URL de Jamendo bajo licencia Creative Commons.
 
##### `cancelarDescarga(songId: String)` / `eliminarDescarga(songId: String)`
**Descripción:** Elimina una canción del nodo `descargas` en Firebase.
 
---
 
#### Métodos públicos — Spotify
 
##### `setSpotifyToken(token: String)`
**Descripción:** Almacena el token OAuth en `SharedPreferences`, conecta Spotify App Remote y carga canciones populares automáticamente al conectar.
 
##### `logoutSpotify()`
**Descripción:** Cierra la sesión de Spotify limpiando el token de memoria y `SharedPreferences`, vaciando `_spotifySongs` y desconectando el App Remote.
 
##### `setSpotifyToken(token: String)` *(setters varios)*
Ver tabla de métodos de Spotify arriba.
 
---
 
##### `onCleared()`
**Descripción:** Se llama cuando el ViewModel es destruido. Libera ExoPlayer y desconecta Spotify App Remote para evitar memory leaks.
 
---
 
## Pantallas (`ui/screens/`)
 
### `HomeScreen.kt`
 
**Descripción:** Pantalla principal de la app. Muestra el selector de fuentes en grid 2x2 y la tarjeta de "Reproduciendo Ahora" con progreso en tiempo real. Implementa un **timer local** que avanza el progreso cada 500ms sin depender de Firebase para evitar saltos visuales.
 
#### Composables
 
##### `HomeScreen(viewModel, navController, modifier)`
**Descripción:** Composable principal de la pantalla. Colecta todos los StateFlows necesarios y gestiona el `localProgress` con dos `LaunchedEffect`:
1. Sincroniza `localProgress` con Firebase o Spotify cuando llegan nuevos valores
2. Timer local que avanza el progreso cada 500ms según la fuente activa
**Parámetros:**
 
| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `viewModel` | `PlayerViewModel` | ViewModel principal inyectado |
| `navController` | `NavController?` | Controlador de navegación para navegar a cada fuente |
| `modifier` | `Modifier` | Modificador de Compose |
 
---
 
##### `SourceButton(label, sublabel, icon, color, selected, modifier, onClick)`
**Descripción:** Botón de selección de fuente de reproducción. Muestra el ícono, nombre y subtítulo de cada fuente. Cuando está seleccionado, aplica un borde y fondo con el color de la fuente.
 
**Parámetros:**
 
| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `label` | `String` | Nombre de la fuente ("Spotify", "Jamendo", etc.) |
| `sublabel` | `String` | Subtítulo descriptivo ("Conectado", "Gratuito", etc.) |
| `icon` | `ImageVector` | Ícono de Material Icons |
| `color` | `Color` | Color de acento de la fuente |
| `selected` | `Boolean` | Si la fuente está activa actualmente |
| `onClick` | `() -> Unit` | Acción al tocar el botón |
 
---
 
##### `NowPlayingCard(song, isPlaying, source, progress, duration, onTogglePlay, onNext, onPrevious)`
**Descripción:** Tarjeta que muestra la canción en reproducción con portada, título, artista, barra de progreso animada y controles de reproducción. Para radio usa una barra de progreso infinita animada en rosa. Para Spotify y Jamendo muestra el tiempo transcurrido y total.
 
**Parámetros:**
 
| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `song` | `Song` | Canción en reproducción |
| `isPlaying` | `Boolean` | Estado de reproducción |
| `source` | `String` | Fuente activa para aplicar colores correctos |
| `progress` | `Float` | Progreso local interpolado (0.0-1.0) |
| `duration` | `Int` | Duración en segundos para calcular el tiempo |
| `onTogglePlay` | `() -> Unit` | Callback de play/pause |
| `onNext` | `() -> Unit` | Callback de siguiente canción |
| `onPrevious` | `() -> Unit` | Callback de canción anterior |
 
---
 
##### `PlayerBar(song, isPlaying, progress, onTogglePlay, onNext, onPrevious)`
**Descripción:** Barra de reproducción minimalista que se muestra en la parte inferior de otras pantallas (Jamendo, Spotify). Incluye portada pequeña, título, artista, controles y barra de progreso con tiempo.
 
---
 
##### `SongCard(song, isPlaying, downloadStatus, onClick, onDownloadClick)`
**Descripción:** Tarjeta de canción reutilizable que muestra portada, título, artista y el estado del botón de descarga (descargable, en progreso, o descargada).
 
---
 
##### `formatTime(seconds: Int): String`
**Descripción:** Función utilitaria que convierte segundos a formato `"m:ss"`.  
**Parámetros:** `seconds` — tiempo en segundos.  
**Retorna:** String formateado, ej: `"3:45"`. Retorna `"0:00"` si el valor es negativo o cero.
 
---
 
### `JamendoScreen.kt`
 
**Descripción:** Pantalla de búsqueda y reproducción de música gratuita de Jamendo. Muestra un campo de búsqueda, lista de canciones y un banner informativo de licencia Creative Commons.
 
#### Composables
 
##### `JamendoScreen(viewModel, modifier)`
**Descripción:** Pantalla principal de Jamendo. Carga las canciones populares al abrir con `LaunchedEffect` si la lista está vacía. Muestra un `SnackbarHost` para confirmar cuando una canción se agrega a la cola.
 
##### `JamendoSongCard(song, isPlaying, downloadStatus, isInQueue, onClick, onDownloadClick, onAddToQueue)`
**Descripción:** Tarjeta de canción de Jamendo con todos los controles. El botón de cola cambia de color a verde cuando la canción ya está en la cola. Si está reproduciéndose, muestra una barra de progreso verde en la parte inferior de la tarjeta.
 
**Parámetros:**
 
| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `isInQueue` | `Boolean` | Si la canción ya está en la cola de reproducción |
| `onAddToQueue` | `() -> Unit` | Callback para agregar a la cola |
| `onDownloadClick` | `() -> Unit` | Callback para iniciar descarga |
 
---
 
### `RadioScreen.kt`
 
**Descripción:** Pantalla de radio en vivo. Carga estaciones desde Radio Browser API automáticamente al abrir. La búsqueda llama a la API en tiempo real conforme el usuario escribe.
 
#### Modelos de datos locales
 
```kotlin
data class RadioStation(
    val id: String,      // UUID de Radio Browser
    val name: String,    // Nombre de la estación
    val city: String,    // País o ciudad
    val genre: String,   // Género o tags de la estación
    val streamUrl: String // URL directa del stream de audio
)
```
 
#### Composables
 
##### `RadioScreen(viewModel, modifier, onBack)`
**Descripción:** Pantalla principal de radio. Implementa búsqueda en tiempo real — al escribir llama a `viewModel.searchRadioStations()`, al borrar todo regresa a las estaciones populares con `viewModel.loadTopRadioStations()`. Muestra un `CircularProgressIndicator` mientras carga.
 
##### `AudioWaveVisualizer()`
**Descripción:** Visualizador de onda de audio animado con 20 barras verticales que se animan de forma independiente usando `rememberInfiniteTransition`. Se muestra cuando hay una estación de radio en reproducción.
 
##### `RadioStationCard(station, isPlaying, onClick)`
**Descripción:** Tarjeta de estación de radio que muestra nombre, ciudad y género. Cuando está en reproducción, aplica fondo rosa translúcido y muestra un badge `"● En vivo"`.
 
---
 
### `SpotifyScreen.kt`
 
**Descripción:** Pantalla de Spotify con dos estados: sin conectar (muestra botón de login) y conectado (muestra buscador y canciones). Al conectar por primera vez, carga canciones populares automáticamente.
 
#### Composables
 
##### `SpotifyScreen(viewModel, modifier, navController)`
**Descripción:** Pantalla principal de Spotify. Usa `rememberLauncherForActivityResult` para manejar el resultado del flujo OAuth 2.0 de Spotify. Muestra un `SnackbarHost` para confirmar cuando se agrega a cola o favoritos.
 
##### `SpotifyPlayerBar(song, isPlaying, progress, duration, onTogglePlay, onNext, onPrevious)`
**Descripción:** Barra de reproducción específica de Spotify con barra de progreso y tiempo en formato `m:ss`. Diferente a `PlayerBar` porque muestra el tiempo real de Spotify.
 
##### `SpotifySongCard(song, isPlaying, isInQueue, isFavorite, onClick, onAddToQueue, onFavorite)`
**Descripción:** Tarjeta de canción de Spotify con tres botones de acción: favorito (corazón), agregar a cola y play/pause. El corazón se vuelve verde cuando la canción está en favoritos.
 
**Parámetros:**
 
| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `isFavorite` | `Boolean` | Si la canción está en la lista de favoritos |
| `onFavorite` | `() -> Unit` | Callback para agregar/quitar de favoritos |
 
##### `formatTime(seconds: Int): String`
**Descripción:** Igual que en `HomeScreen`, convierte segundos a `"m:ss"`.
 
---
 
### `YouTubeScreen.kt`
 
**Descripción:** Pantalla de búsqueda de videos de YouTube. Si no hay resultados de la API, muestra 6 videos de muestra hardcodeados. Al reproducir un video, abre YouTube en Chrome Custom Tabs integrado en la app y actualiza el estado en Firebase para que la TV lo muestre.
 
#### Modelos de datos locales
 
```kotlin
data class YouTubeVideo(
    val id: String,         // ID del video en YouTube
    val title: String,      // Título del video
    val channel: String,    // Nombre del canal
    val views: String,      // Vistas formateadas ("1.2B vistas")
    val thumbnail: String,  // URL del thumbnail
    val youtubeUrl: String  // URL completa del video
)
```
 
#### Composables
 
##### `YouTubeScreen(modifier, navController, viewModel)`
**Descripción:** Pantalla principal de YouTube. Al tocar un video llama primero a `viewModel.playYouTubeVideo(video)` para actualizar Firebase y luego abre el video en Chrome Custom Tabs con colores de la app.
 
##### `YouTubeVideoCard(video, onClick)`
**Descripción:** Tarjeta de video con thumbnail a pantalla completa, título del video, canal, número de vistas y badges de reproducción.
 
---
 
### `QueueScreen.kt`
 
**Descripción:** Pantalla que muestra la cola de reproducción local. La canción actual aparece destacada en una tarjeta verde. Las canciones en cola muestran el ícono de su fuente con el color correspondiente.
 
#### Composables
 
##### `QueueScreen(viewModel, modifier)`
**Descripción:** Lee `viewModel.queue` y `viewModel.playbackState`. Muestra un botón "Limpiar" en el AppBar cuando hay canciones en la cola. Si la cola está vacía, muestra un estado vacío con instrucciones.
 
##### `CurrentSongCard(song, source)`
**Descripción:** Tarjeta verde que muestra la canción en reproducción actualmente con su badge de fuente coloreado según la fuente activa.
 
##### `QueueSongCard(index, song, onPlay, onRemove)`
**Descripción:** Tarjeta de canción en la cola con número de posición, ícono de fuente coloreado, título, artista, botón de reproducción inmediata y botón de eliminar de la cola.
 
**Parámetros:**
 
| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `onPlay` | `() -> Unit` | Reproduce la canción inmediatamente desde la cola |
| `onRemove` | `() -> Unit` | Elimina la canción de la cola sin reproducirla |
 
---
 
### `DownloadsScreen.kt`
 
**Descripción:** Pantalla que muestra las canciones descargadas de Jamendo con indicador de almacenamiento usado.
 
#### Composables
 
##### `DownloadsScreen(viewModel, modifier)`
**Descripción:** Muestra una barra de progreso de almacenamiento (MB usados / 1 GB) y la lista de descargas desde Firebase.
 
##### `DownloadCard(song, onCancel)`
**Descripción:** Tarjeta de descarga que muestra el título, tamaño y estado. Si está en progreso, muestra el porcentaje y un botón de cancelar. Si está completa, muestra un check verde.
 
---
 
## Navegación (`ui/navigation/`)
 
### `AppNavigation.kt`
 
**Descripción:** Composable raíz que configura el `NavHost` con todas las rutas de la app y el `NavigationBar` inferior.
 
**Rutas configuradas:**
 
| Ruta | Destino |
|------|---------|
| `home` | `HomeScreen` |
| `search` (Jamendo) | `JamendoScreen` |
| `queue` | `QueueScreen` |
| `downloads` | `DownloadsScreen` |
| `settings` | `SettingsScreen` |
| `spotify` | `SpotifyScreen` |
| `radio` | `RadioScreen` |
| `youtube` | `YouTubeScreen` |
 
El `NavigationBar` inferior se compone de 5 ítems: Inicio, Buscar, Cola, Descarg. y Config. Los colores de selección usan verde de Sinfonía (`SintoniaGreen`).
 
---
 
## Tema (`ui/theme/`)
 
### `Color.kt` — Paleta de colores
 
| Constante | Hex | Uso |
|-----------|-----|-----|
| `SintoniaDark` | `#0D0D0D` | Fondo principal oscuro |
| `SintoniaCard` | `#1A1A1A` | Fondo de tarjetas |
| `SintoniaGreen` | `#1DB954` | Color primario (Spotify green) |
| `SintoniaPink` | `#E91E8C` | Color de acento para radio |
| `SintoniaSubtext` | `#888888` | Texto secundario y subtítulos |
 
---
 
## APIs y Credenciales
 
| API | Endpoint base | Autenticación | Uso |
|-----|---------------|---------------|-----|
| Jamendo | `api.jamendo.com/v3.0` | API Key (gratuita) | Música Creative Commons |
| Spotify Web API | `api.spotify.com/v1` | OAuth 2.0 Bearer Token | Búsqueda de canciones |
| Spotify App Remote | SDK nativo Android | App Remote connection | Reproducción en-app |
| Radio Browser | `de1.api.radio-browser.info` | Sin autenticación | Estaciones de radio |
| YouTube Data API v3 | `googleapis.com/youtube/v3` | API Key | Búsqueda de videos |
| Firebase Realtime DB | Proyecto Firebase | `google-services.json` | Sincronización en tiempo real |
 
---
 
## Dependencias Principales
 
```kotlin
// UI — Jetpack Compose
implementation(platform("androidx.compose:compose-bom:2024.12.01"))
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.material:material-icons-extended")
implementation("androidx.navigation:navigation-compose:2.8.5")
implementation("androidx.activity:activity-compose:1.9.3")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
 
// Firebase
implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
implementation("com.google.firebase:firebase-database-ktx")  // Realtime Database
implementation("com.google.firebase:firebase-auth-ktx")
 
// Networking
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.retrofit2:converter-gson:2.11.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
 
// Reproductor de audio
implementation("androidx.media3:media3-exoplayer:1.5.0")
implementation("androidx.media3:media3-exoplayer-hls:1.5.0")     // Streams HLS (.m3u8)
implementation("androidx.media3:media3-datasource-okhttp:1.5.0") // Streams HTTP
 
// Spotify
implementation("com.spotify.android:auth:2.1.0")                           // OAuth 2.0
implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))        // App Remote SDK
 
// Imágenes asíncronas
implementation("io.coil-kt:coil-compose:2.7.0")
 
// YouTube — navegador integrado
implementation("androidx.browser:browser:1.8.0")
 
// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
```
 
---
 
## Cómo ejecutar el módulo `:app`
 
1. Clona el repositorio
2. Coloca `google-services.json` en `app/`
3. Configura las credenciales en los archivos correspondientes:
   - `JamendoApi.kt` → `JAMENDO_CLIENT_ID`
   - `SpotifyAuthManager.kt` → `CLIENT_ID` y `REDIRECT_URI`
   - `local.properties` → `YOUTUBE_API_KEY`
4. En Firebase Console, configura las reglas de Realtime Database:
```json
   { "rules": { ".read": true, ".write": true } }
```
5. Selecciona el módulo `:app` en Android Studio
6. Ejecuta en un dispositivo o emulador con Android 8.0+ (API 26+)
---
