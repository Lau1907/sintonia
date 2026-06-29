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