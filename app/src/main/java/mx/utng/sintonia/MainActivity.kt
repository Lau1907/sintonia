package mx.utng.sintonia

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationResponse
import mx.utng.sintonia.data.remote.SpotifyAuthManager
import mx.utng.sintonia.ui.screens.HomeScreen
import mx.utng.sintonia.ui.theme.SintoniaTheme
import mx.utng.sintonia.viewmodel.PlayerViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SintoniaTheme {
                HomeScreen(viewModel = viewModel)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SpotifyAuthManager.REQUEST_CODE) {
            val response = AuthorizationClient.getResponse(resultCode, data)
            when (response.type) {
                AuthorizationResponse.Type.TOKEN -> {
                    viewModel.setSpotifyToken(response.accessToken)
                }
                AuthorizationResponse.Type.ERROR -> {
                    android.util.Log.e("SPOTIFY", "Error: ${response.error}")
                }
                else -> {}
            }
        }
    }
}