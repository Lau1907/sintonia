package mx.utng.tv

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class TvPlayerState(
    val source: String = "jamendo",
    val isPlaying: Boolean = false,
    val currentTitle: String = "",
    val currentArtist: String = "",
    val currentCoverUrl: String = "",
    val volume: Int = 70,
    val queue: List<Pair<String, String>> = emptyList() // title to artist
)

object FirebaseTvSync {

    private val playerRef = FirebaseDatabase.getInstance()
        .reference.child("playback")

    fun observePlayerState(): Flow<TvPlayerState> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val queueSnapshot = snapshot.child("queue")
                    val queue = mutableListOf<Pair<String, String>>()
                    queueSnapshot.children.forEach { item ->
                        val title = item.child("title").getValue(String::class.java) ?: ""
                        val artist = item.child("artist").getValue(String::class.java) ?: ""
                        if (title.isNotEmpty()) queue.add(title to artist)
                    }

                    val state = TvPlayerState(
                        source = snapshot.child("source").getValue(String::class.java) ?: "jamendo",
                        isPlaying = snapshot.child("isPlaying").getValue(Boolean::class.java) ?: false,
                        currentTitle = snapshot.child("currentSong").child("title").getValue(String::class.java) ?: "",
                        currentArtist = snapshot.child("currentSong").child("artist").getValue(String::class.java) ?: "",
                        currentCoverUrl = snapshot.child("currentSong").child("albumCover").getValue(String::class.java) ?: "",
                        volume = snapshot.child("volume").getValue(Int::class.java) ?: 70,
                        queue = queue
                    )
                    trySend(state)
                } catch (e: Exception) {
                    android.util.Log.e("FirebaseTvSync", "Error: ${e.message}")
                }
            }
            override fun onCancelled(error: DatabaseError) {  // ← esto faltaba
                close(error.toException())
            }
        }
        playerRef.addValueEventListener(listener)
        awaitClose { playerRef.removeEventListener(listener) }
    }

    // Comandos que el smartphone escucha
    fun sendPlayPause(isCurrentlyPlaying: Boolean) {
        FirebaseDatabase.getInstance().reference
            .child("playback").child("tvCommand").setValue(
                if (isCurrentlyPlaying) "pause" else "play"
            )
    }

    fun sendSkipNext() {
        playerRef.child("skipSong").setValue("next")
    }

    fun sendSkipPrevious() {
        playerRef.child("skipSong").setValue("previous")
    }
}