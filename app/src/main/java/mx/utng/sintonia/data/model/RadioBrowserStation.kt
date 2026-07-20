package mx.utng.sintonia.data.model

data class RadioBrowserStation(
    val stationuuid: String = "",
    val name: String = "",
    val country: String = "",
    val tags: String = "",
    val url_resolved: String = "",
    val favicon: String = "",
    val votes: Int = 0
)