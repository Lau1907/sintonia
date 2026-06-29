package mx.utng.sintonia.data.model

data class Song(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val albumCover: String = "",
    val audioUrl: String = "",
    val duration: Int = 0,
    val source: String = "jamendo",
    val tamanoMb: Float = 0f,
    val progresoDescarga: Int = 0,
    val descargada: Boolean = false
)