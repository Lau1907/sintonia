package mx.utng.sintonia.data.remote

import android.util.Log
import mx.utng.sintonia.data.model.Song
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

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

    private fun buildAuthHeader(token: String): String =
        if (token.startsWith("Bearer ")) token else "Bearer $token"

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