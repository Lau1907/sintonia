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