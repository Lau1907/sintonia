package mx.utng.sintonia.data.remote

import mx.utng.sintonia.data.model.Song
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class JamendoRepository {
    private val api: JamendoApi = Retrofit.Builder()
        .baseUrl("https://api.jamendo.com/v3.0/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(JamendoApi::class.java)

    /**
     * Jamendo no regresa el tamaño del archivo en MB directamente,
     * así que lo estimamos a partir de la duración asumiendo el
     * bitrate típico de streaming de Jamendo (128 kbps).
     */
    private fun calcularTamanoMb(durationSeconds: Int, bitrateKbps: Int = 128): Float {
        return (durationSeconds * bitrateKbps) / (8f * 1024f)
    }

    suspend fun getPopularTracks(): List<Song> {
        return try {
            api.getPopularTracks().results.map { track ->
                Song(
                    id = track.id,
                    title = track.name,
                    artist = track.artist_name,
                    albumCover = track.image,
                    audioUrl = track.audio,
                    duration = track.duration,
                    source = "jamendo",
                    tamanoMb = calcularTamanoMb(track.duration)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchTracks(query: String): List<Song> {
        return try {
            api.searchTracks(search = query).results.map { track ->
                Song(
                    id = track.id,
                    title = track.name,
                    artist = track.artist_name,
                    albumCover = track.image,
                    audioUrl = track.audio,
                    duration = track.duration,
                    source = "jamendo",
                    tamanoMb = calcularTamanoMb(track.duration)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}