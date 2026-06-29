package mx.utng.sintonia.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse

object SpotifyAuthManager {
    const val CLIENT_ID = "63ea034767694ac388fb5837cc2f8369"
    const val REDIRECT_URI = "mx.utng.sintonia://callback"
    const val REQUEST_CODE = 1337

    fun getAuthRequest(): AuthorizationRequest {
        return AuthorizationRequest.Builder(
            CLIENT_ID,
            AuthorizationResponse.Type.TOKEN,
            REDIRECT_URI
        )
            .setScopes(arrayOf("streaming", "user-read-playback-state", "user-modify-playback-state"))
            .build()
    }
}