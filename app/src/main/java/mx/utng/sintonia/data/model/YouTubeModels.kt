package mx.utng.sintonia

import com.google.gson.annotations.SerializedName

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