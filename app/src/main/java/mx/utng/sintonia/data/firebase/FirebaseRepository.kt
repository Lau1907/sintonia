package mx.utng.sintonia.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import mx.utng.sintonia.data.model.PlaybackState
import mx.utng.sintonia.data.model.Song
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirebaseRepository {
    private val db = FirebaseDatabase.getInstance().reference.child("playback")
    private val dbDescargas = FirebaseDatabase.getInstance().reference.child("descargas")

    fun observePlaybackState(): Flow<PlaybackState> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val state = snapshot.getValue(PlaybackState::class.java)
                state?.let { trySend(it) }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.addValueEventListener(listener)
        awaitClose { db.removeEventListener(listener) }
    }

    fun updatePlaybackState(state: PlaybackState) {
        db.setValue(state)
    }

    fun updateIsPlaying(isPlaying: Boolean) {
        db.child("isPlaying").setValue(isPlaying)
    }

    fun updateCurrentSong(song: Song) {
        db.child("currentSong").setValue(song)
    }

    // --- Descargas (persistencia de GestorDescargas) ---

    fun observeDownloads(): Flow<List<Song>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val songs = snapshot.children.mapNotNull { it.getValue(Song::class.java) }
                trySend(songs)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        dbDescargas.addValueEventListener(listener)
        awaitClose { dbDescargas.removeEventListener(listener) }
    }

    fun saveDownload(song: Song) {
        dbDescargas.child(song.id).setValue(song)
    }

    fun removeDownload(songId: String) {
        dbDescargas.child(songId).removeValue()
    }
}