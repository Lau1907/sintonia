package mx.utng.sintonia.data.model

import com.google.firebase.database.PropertyName

data class PlaybackState(
    @get:PropertyName("isPlaying")
    @set:PropertyName("isPlaying")
    var isPlaying: Boolean = false,
    var currentSong: Song = Song(),
    var volume: Int = 70,
    var source: String = "jamendo"
)