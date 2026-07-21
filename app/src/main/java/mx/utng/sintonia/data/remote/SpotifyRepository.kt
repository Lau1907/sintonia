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

    suspend fun searchTracks(query: String, token: String): List<Song> {
        val cleanQuery = query.trim()

        if (cleanQuery.isEmpty()) return emptyList()

        return try {
            val authHeader = if (token.startsWith("Bearer ")) token else "Bearer $token"

            Log.d("SpotifyRepository", "Buscando '$cleanQuery' con limit=20")

            val response = api.searchTracks(
                authHeader = authHeader,
                query = cleanQuery,
                type = "track",
                limit = 20
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
}