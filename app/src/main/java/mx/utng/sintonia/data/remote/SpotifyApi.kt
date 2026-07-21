package mx.utng.sintonia.data.remote

import retrofit2.http.GET
import retrofit2.http.Header
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

interface SpotifyApi {
    @GET("search")
    suspend fun searchTracks(
        @Header("Authorization") authHeader: String,
        @Query(value = "q") query: String,
        @Query(value = "type") type: String = "track",
        @Query(value = "limit") limit: Int = 20
    ): SpotifySearchResponse
}