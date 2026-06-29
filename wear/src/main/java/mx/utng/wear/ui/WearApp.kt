package mx.utng.wear.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import mx.utng.wear.ui.screens.NotificationScreen
import mx.utng.wear.ui.screens.PlayerScreen
import mx.utng.wear.ui.screens.VolumeScreen

data class WearState(
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val volume: Int = 70,
    val source: String = "jamendo",
    val nivelBateria: Int = 100
)

@Composable
fun WearApp() {
    val context = LocalContext.current
    val db = FirebaseDatabase.getInstance().reference.child("playback")
    var state by remember { mutableStateOf(WearState()) }
    var previousTitle by remember { mutableStateOf("") }
    var showNotification by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isPlaying = snapshot.child("isPlaying").getValue(Boolean::class.java) ?: false
                val title = snapshot.child("currentSong").child("title").getValue(String::class.java) ?: ""
                val artist = snapshot.child("currentSong").child("artist").getValue(String::class.java) ?: ""
                val volume = snapshot.child("volume").getValue(Int::class.java) ?: 70
                val source = snapshot.child("source").getValue(String::class.java) ?: "jamendo"
                if (title != previousTitle && title.isNotEmpty()) {
                    showNotification = true
                    previousTitle = title
                }
                // OJO: cambié esto a state.copy(...) en vez de WearState(...) nuevo,
                // para que cada actualización de Firebase NO te borre el nivelBateria
                // que viene del receiver de abajo.
                state = state.copy(
                    isPlaying = isPlaying,
                    title = title,
                    artist = artist,
                    volume = volume,
                    source = source
                )
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.addValueEventListener(listener)
        onDispose { db.removeEventListener(listener) }
    }

    // Nivel de batería del reloj (VistaReloj.nivelBateria del modelo de dominio).
    // Se lee directo del sistema del watch, no depende de Firebase ni del teléfono.
    DisposableEffect(Unit) {
        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level >= 0 && scale > 0) {
                    val porcentaje = (level * 100) / scale
                    state = state.copy(nivelBateria = porcentaje)
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(batteryReceiver) }
    }

    val navController = rememberSwipeDismissableNavController()
    SwipeDismissableNavHost(navController = navController, startDestination = "player") {
        composable("player") {
            PlayerScreen(
                state = state,
                onTogglePlay = { db.child("isPlaying").setValue(!state.isPlaying) },
                onNext = {
                    db.child("skipSong").setValue("next")
                },
                onPrevious = {
                    db.child("skipSong").setValue("previous")
                },
                onVolumeClick = { navController.navigate("volume") }
            )
        }
        composable("volume") {
            VolumeScreen(
                volume = state.volume,
                nivelBateria = state.nivelBateria,
                onVolumeUp = { db.child("volume").setValue((state.volume + 10).coerceAtMost(100)) },
                onVolumeDown = { db.child("volume").setValue((state.volume - 10).coerceAtLeast(0)) }
            )
        }
        composable("notification") {
            NotificationScreen(
                title = state.title,
                artist = state.artist,
                source = state.source,
                nivelBateria = state.nivelBateria,
                onOk = {
                    db.child("isPlaying").setValue(true)
                    navController.navigate("player") {
                        popUpTo("player") { inclusive = true }
                    }
                },
                onSkip = {
                    db.child("skipSong").setValue("next")
                    navController.navigate("player") {
                        popUpTo("player") { inclusive = true }
                    }
                }
            )
        }
    }
    if (showNotification) {
        LaunchedEffect(state.title) {
            navController.navigate("notification")
            showNotification = false
        }
    }
}