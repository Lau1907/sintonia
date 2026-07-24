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