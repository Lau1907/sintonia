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