package mx.utng.sintonia.data.remote

import mx.utng.sintonia.data.model.Song
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SpotifyRepository {
    private val api: SpotifyApi = Retrofit.Builder()
        .baseUrl("https://api.spotify.com/v1/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SpotifyApi::class.java)

    suspend fun searchTracks(query: String, token: String): List<Song> {
        return try {
            api.searchTracks(
                token = "Bearer $token",
                query = query
            ).tracks?.items?.map { track ->
                Song(
                    id = track.id,
                    title = track.name,
                    artist = track.artists.firstOrNull()?.name ?: "",
                    albumCover = track.album.images.firstOrNull()?.url ?: "",
                    audioUrl = "spotify:track:${track.id}",
                    duration = track.duration_ms / 1000,
                    source = "spotify"
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}