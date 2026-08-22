package mx.utng.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Actividad principal (única) de la app de Android TV.
 * Es el punto de entrada del proceso.
 */
class MainActivity : ComponentActivity() {
    /**
     * Inicializa TvPlayer (crea el ExoPlayer) y define el contenido de la
     * pantalla con Compose: un Box de fondo oscuro que contiene
     * TvDashboardScreen, el único "screen" de este módulo (Android TV no
     * necesita navegación entre pantallas para este caso de uso).
     *
     * @param savedInstanceState estado previo de la actividad (estándar de Android)
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TvPlayer.initialize(this)
        setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0A0A0A))
                ) {
                    TvDashboardScreen()
                }
            }
        }
    }

    /**
     * Libera los recursos del reproductor (ExoPlayer) cuando la actividad
     * se destruye, para no dejar el reproductor de audio corriendo en
     * segundo plano ni fugar memoria.
     *
     * Por qué existe: ExoPlayer mantiene recursos nativos (decodificadores,
     * buffers); si no se llama a release(), pueden quedarse reservados aunque
     * la Activity ya no exista.
     */
    override fun onDestroy() {
        super.onDestroy()
        TvPlayer.release()
    }
}