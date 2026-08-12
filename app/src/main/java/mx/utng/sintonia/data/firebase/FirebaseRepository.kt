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
    private val dbFavoritos = FirebaseDatabase.getInstance().reference.child("favoritos")

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

    /**
     * ANTES: db.setValue(state) reemplazaba TODO el nodo "playback",
     * borrando playOnTv, queue, y cualquier otro campo que no forme
     * parte de PlaybackState. Ahora solo tocamos los campos que
     * realmente cambian, usando updateChildren en vez de setValue.
     */
    fun updatePlaybackState(state: PlaybackState) {
        val updates = mapOf<String, Any?>(
            "isPlaying" to state.isPlaying,
            "currentSong" to state.currentSong,
            "source" to state.source
        )
        db.updateChildren(updates)
    }

    fun updateQueue(songs: List<Song>) {
        val queueData = songs.take(3).mapIndexed { index, song ->
            mapOf(
                "title" to song.title,
                "artist" to song.artist
            )
        }
        FirebaseDatabase.getInstance().reference
            .child("playback").child("queue").setValue(queueData)
    }
    fun updateIsPlaying(isPlaying: Boolean) {
        db.child("isPlaying").setValue(isPlaying)
    }

    fun updatePlayOnTv(playOnTv: Boolean) {
        db.child("playOnTv").setValue(playOnTv)
    }
    fun updateProgress(progress: Float) {
        db.child("progress").setValue(progress)
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

    // --- Favoritos ---

    fun observeFavorites(): Flow<List<Song>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val songs = snapshot.children.mapNotNull { it.getValue(Song::class.java) }
                trySend(songs)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        dbFavoritos.addValueEventListener(listener)
        awaitClose { dbFavoritos.removeEventListener(listener) }
    }

    fun saveFavorite(song: Song) {
        dbFavoritos.child(song.id).setValue(song)
    }

    fun removeFavorite(songId: String) {
        dbFavoritos.child(songId).removeValue()
    }
}