package mx.utng.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import mx.utng.wear.ui.WearApp

/**
 * Actividad principal (única) de la app de Wear OS.
 * Es el punto de entrada del proceso.
 */
class MainActivity : ComponentActivity() {
    /**
     * Monta el Composable raíz WearApp(), que se encarga de toda la
     * lógica de estado, navegación y suscripción a Firebase. A
     * diferencia del módulo TV, aquí no hay nada que inicializar antes
     * de mostrar la UI (no hay un reproductor propio, el reloj solo
     * controla al teléfono).
     *
     * @param savedInstanceState estado previo de la actividad (estándar de Android)
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp()
        }
    }
}