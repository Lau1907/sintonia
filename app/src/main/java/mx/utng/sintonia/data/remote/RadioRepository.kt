package mx.utng.sintonia.data.remote

import mx.utng.sintonia.ui.screens.RadioStation
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RadioRepository {

    private val service: RadioBrowserService by lazy {
        Retrofit.Builder()
            .baseUrl("https://de1.api.radio-browser.info/")
            .client(
                OkHttpClient.Builder()
                    .addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    })
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RadioBrowserService::class.java)
    }

    suspend fun getTopStations(): List<RadioStation> =
        service.getTopStations()
            .filter { it.url_resolved.isNotEmpty() }
            .map {
                RadioStation(
                    id = it.stationuuid,
                    name = it.name,
                    city = it.country,
                    genre = it.tags.split(",").firstOrNull()?.trim() ?: "Radio",
                    streamUrl = it.url_resolved
                )
            }

    suspend fun searchStations(query: String): List<RadioStation> =
        service.searchStations(name = query)
            .filter { it.url_resolved.isNotEmpty() }
            .map {
                RadioStation(
                    id = it.stationuuid,
                    name = it.name,
                    city = it.country,
                    genre = it.tags.split(",").firstOrNull()?.trim() ?: "Radio",
                    streamUrl = it.url_resolved
                )
            }
}