package mx.utng.sintonia.data.remote

import mx.utng.sintonia.data.model.RadioBrowserStation
import retrofit2.http.GET
import retrofit2.http.Query

interface RadioBrowserService {

    // Estaciones populares
    @GET("json/stations/topvote/20")
    suspend fun getTopStations(): List<RadioBrowserStation>

    // Buscar por nombre o país
    @GET("json/stations/search")
    suspend fun searchStations(
        @Query("name") name: String = "",
        @Query("country") country: String = "",
        @Query("limit") limit: Int = 20,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("order") order: String = "votes"
    ): List<RadioBrowserStation>

    // Estaciones de México específicamente
    @GET("json/stations/bycountry/Mexico")
    suspend fun getMexicanStations(
        @Query("limit") limit: Int = 20,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("order") order: String = "votes"
    ): List<RadioBrowserStation>
}