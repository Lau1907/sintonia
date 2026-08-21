# Módulo `wear` — Sintonía (Wear OS)

App de smartwatch (Wear OS) que muestra qué se está reproduciendo en el smartphone, permite controlarlo (play/pausa, siguiente, anterior, volumen) y notifica al usuario cuando llega una canción nueva. Al igual que el módulo TV, se sincroniza contra el mismo nodo `playback` de **Firebase Realtime Database**; el reloj no reproduce audio por sí mismo, solo controla al teléfono de forma remota.

---

## 📁 Estructura del módulo

```
wear/
├── build.gradle.kts                          #archivo de configuración de dependencias y build de este módulo
├── google-services.json                      #archivo de configuración de Firebase para este módulo
├── proguard-rules.pro                        #archivo de reglas de ofuscación/minificación para el build de release
└── src/main/
    ├── AndroidManifest.xml                   #archivo que declara la Activity principal y el tema de la app
    ├── java/mx/utng/wear/
    │   ├── MainActivity.kt                   #archivo de la actividad principal (punto de entrada de la app)
    │   └── ui/
    │       ├── WearApp.kt                    #archivo raíz de la UI: estado global, sincronización con Firebase, batería y navegación
    │       └── screens/
    │           ├── PlayerScreen.kt           #archivo de la pantalla principal de reproducción (título, artista, controles)
    │           ├── VolumeScreen.kt           #archivo de la pantalla de control de volumen
    │           └── NotificationScreen.kt     #archivo de la pantalla de aviso de "nueva canción"
    └── res/
        ├── drawable/ic_launcher_*.xml        #archivos vectoriales del ícono adaptativo de la app
        ├── mipmap-anydpi/ic_launcher*.xml    #archivos de configuración del ícono adaptativo
        ├── mipmap-*/ic_launcher*.webp        #archivos de ícono de la app en distintas resoluciones
        └── values/strings.xml                #archivo de textos/strings de la app
```

Este README documenta los **5 archivos de código Kotlin** del módulo (los que contienen lógica y funciones): `MainActivity.kt`, `WearApp.kt`, `PlayerScreen.kt`, `VolumeScreen.kt` y `NotificationScreen.kt`. El código de cada uno se muestra completo, tal como está en el proyecto, con la documentación (KDoc `/** */` y `@param`) insertada directamente arriba de cada función — así se lee todo en un solo bloque, sin cortes.

---

## `MainActivity.kt` — #archivo de la actividad principal (punto de entrada de la app)

```kotlin
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
```

---

## `WearApp.kt` — #archivo raíz de la UI: estado global, sincronización con Firebase, batería y navegación

Composable raíz de la app del reloj: define el estado global (`WearState`), la suscripción a Firebase, la lectura de batería del propio reloj, y la navegación entre las tres pantallas (player, volumen, notificación).

```kotlin
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

/**
 * Estado global de la app de Wear OS, reflejo de lo que hay en Firebase
 * más un dato local (nivelBateria) que no viene del teléfono sino del
 * propio reloj.
 *
 * @param isPlaying true si hay reproducción activa
 * @param title título de la canción actual
 * @param artist artista de la canción actual
 * @param volume volumen actual (0-100)
 * @param source fuente activa ("jamendo", "radio", "spotify", etc.)
 * @param nivelBateria porcentaje de batería del propio smartwatch (0-100),
 *   leído directo del sistema Android, no de Firebase
 */
data class WearState(
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val volume: Int = 70,
    val source: String = "jamendo",
    val nivelBateria: Int = 100
)

/**
 * Composable raíz de la app de Wear OS. Reúne tres responsabilidades:
 *
 * 1) Sincronización con Firebase: un listener sobre el nodo "playback"
 *    actualiza `state` cada vez que cambia isPlaying, title, artist,
 *    volume o source. Usa `state.copy(...)` en vez de crear un
 *    WearState nuevo para no perder el nivelBateria (que no viene
 *    de Firebase) en cada actualización.
 *
 * 2) Lectura de batería local: un BroadcastReceiver escucha
 *    ACTION_BATTERY_CHANGED del propio reloj y actualiza
 *    `nivelBateria` calculando el porcentaje a partir de EXTRA_LEVEL
 *    y EXTRA_SCALE. Esto es independiente de Firebase porque es un
 *    dato físico del dispositivo, no del estado de reproducción.
 *
 * 3) Navegación: usa un SwipeDismissableNavHost (patrón típico de
 *    Wear OS) con tres destinos — "player", "volume" y "notification" —
 *    y detecta cambios de título de canción para disparar
 *    automáticamente la navegación a "notification" cuando llega una
 *    canción nueva (showNotification).
 *
 * Los callbacks que se pasan a cada pantalla (onTogglePlay, onNext,
 * onPrevious, onVolumeUp/Down, onOk, onSkip) no reproducen nada
 * localmente: solo escriben comandos en Firebase (isPlaying,
 * skipSong, volume), y es el teléfono quien ejecuta la acción real
 * y refleja el nuevo estado de vuelta.
 */
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
```

---

## `PlayerScreen.kt` — #archivo de la pantalla principal de reproducción

Pantalla principal del reloj: muestra la canción actual y los controles de reproducción.

```kotlin
package mx.utng.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import mx.utng.wear.ui.WearState

/**
 * Pantalla principal de reproducción del smartwatch. Muestra el
 * nombre de la app, el nivel de batería, título/artista de la canción
 * actual y tres botones de control (anterior, play/pausa, siguiente)
 * más un botón compacto para ir a la pantalla de volumen.
 *
 * Por qué existe: es la pantalla de "inicio" de la navegación
 * (startDestination = "player" en WearApp) — el punto de partida
 * desde el que el usuario controla la reproducción sin salir de la
 * muñeca.
 *
 * @param state estado actual del reproductor (título, artista,
 *   isPlaying, nivelBateria)
 * @param onTogglePlay se invoca al presionar play/pausa; en WearApp
 *   esto escribe el nuevo valor de isPlaying en Firebase
 * @param onNext se invoca al presionar "siguiente"; escribe
 *   "next" en playback/skipSong
 * @param onPrevious se invoca al presionar "anterior"; escribe
 *   "previous" en playback/skipSong
 * @param onVolumeClick se invoca al presionar el botón de volumen;
 *   navega a la pantalla "volume"
 */
@Composable
fun PlayerScreen(
    state: WearState,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onVolumeClick: () -> Unit
) {
    Scaffold(
        modifier = Modifier.background(Color(0xFF121212))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SINTONÍA", color = Color(0xFF1DB954),
                    fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("🔋 ${state.nivelBateria}%", color = Color(0xFFB3B3B3), fontSize = 9.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                state.title.ifEmpty { "Sin reproducción" },
                color = Color.White, fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                state.artist,
                color = Color(0xFFB3B3B3), fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPrevious,
                    modifier = Modifier.size(36.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF333333))
                ) { Text("⏮", fontSize = 14.sp) }
                Button(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(44.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1DB954))
                ) { Text(if (state.isPlaying) "⏸" else "▶", fontSize = 18.sp) }
                Button(
                    onClick = onNext,
                    modifier = Modifier.size(36.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF333333))
                ) { Text("⏭", fontSize = 14.sp) }
            }
            Spacer(modifier = Modifier.height(4.dp))
            CompactButton(
                onClick = onVolumeClick,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF222222))
            ) { Text("🔊", fontSize = 12.sp) }
        }
    }
}
```

---

## `VolumeScreen.kt` — #archivo de la pantalla de control de volumen

Pantalla secundaria para subir/bajar el volumen desde el reloj.

```kotlin
package mx.utng.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*

/**
 * Pantalla de control de volumen. Muestra el nivel de batería, el
 * volumen actual como porcentaje y dos botones (−/+) para ajustarlo
 * de 10 en 10.
 *
 * Por qué existe: separar el control de volumen de PlayerScreen
 * mantiene la pantalla principal simple; en Wear OS es común usar
 * una pantalla dedicada para acciones secundarias que no necesitan
 * estar siempre visibles.
 *
 * @param volume volumen actual (0-100) a mostrar
 * @param nivelBateria porcentaje de batería del reloj a mostrar
 * @param onVolumeUp se invoca al presionar "+"; en WearApp escribe
 *   `(volume + 10).coerceAtMost(100)` en Firebase
 * @param onVolumeDown se invoca al presionar "−"; en WearApp escribe
 *   `(volume - 10).coerceAtLeast(0)` en Firebase
 */
@Composable
fun VolumeScreen(
    volume: Int,
    nivelBateria: Int,
    onVolumeUp: () -> Unit,
    onVolumeDown: () -> Unit
) {
    Scaffold(modifier = Modifier.background(Color(0xFF121212))) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🔋 $nivelBateria%", color = Color(0xFFB3B3B3), fontSize = 10.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Volumen", color = Color(0xFFB3B3B3), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("$volume%", color = Color(0xFF1DB954),
                fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onVolumeDown,
                    modifier = Modifier.size(40.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF333333))
                ) { Text("−", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Button(
                    onClick = onVolumeUp,
                    modifier = Modifier.size(40.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1DB954))
                ) { Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
```

---

## `NotificationScreen.kt` — #archivo de la pantalla de aviso de "nueva canción"

Pantalla que aparece automáticamente cuando llega una canción nueva, para avisarle al usuario sin que tenga que estar mirando el reloj activamente.

```kotlin
package mx.utng.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*

/**
 * Pantalla de notificación de "nueva canción". WearApp navega aquí
 * automáticamente (ver showNotification en WearApp.kt) cada vez que
 * detecta un cambio de título distinto al anterior.
 *
 * Muestra un ícono de campana, el nivel de batería, el título/artista/
 * fuente de la nueva canción, y dos botones: "Omitir" (saltar esta
 * canción) y "OK" (aceptar y empezar a reproducir).
 *
 * Por qué existe: en un smartwatch no siempre se está viendo la
 * pantalla del reproductor; esta pantalla funciona como una alerta
 * puntual que el usuario puede resolver rápido (aceptar o saltar)
 * sin tener que entrar manualmente a revisar qué está sonando.
 *
 * @param title título de la nueva canción
 * @param artist artista de la nueva canción
 * @param source fuente de la canción ("jamendo", "radio", etc.)
 * @param nivelBateria porcentaje de batería del reloj a mostrar
 * @param onOk se invoca al presionar "OK"; en WearApp pone
 *   isPlaying = true en Firebase y regresa a la pantalla "player"
 * @param onSkip se invoca al presionar "Omitir"; en WearApp escribe
 *   "next" en playback/skipSong y regresa a la pantalla "player"
 */
@Composable
fun NotificationScreen(
    title: String,
    artist: String,
    source: String,
    nivelBateria: Int,
    onOk: () -> Unit,
    onSkip: () -> Unit
) {
    Scaffold(modifier = Modifier.background(Color(0xFF121212))) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔔", fontSize = 16.sp)
                Text("🔋 $nivelBateria%", color = Color(0xFFB3B3B3), fontSize = 9.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Nueva canción", color = Color(0xFFB3B3B3), fontSize = 10.sp)
            Text(title, color = Color.White, fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center)
            Text("$artist · $source", color = Color(0xFFB3B3B3),
                fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF333333))
                ) { Text("Omitir", fontSize = 10.sp) }
                Button(
                    onClick = onOk,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1DB954))
                ) { Text("OK", fontSize = 10.sp) }
            }
        }
    }
}
```

---

## 🔄 Resumen del flujo de datos del módulo Wear

1. El teléfono escribe el estado de reproducción en `playback` (Firebase).
2. El `ValueEventListener` dentro de `WearApp()` escucha esos cambios y actualiza `WearState`.
3. Si el título de la canción cambió, `WearApp` navega automáticamente a `NotificationScreen`.
4. `PlayerScreen` y `VolumeScreen` muestran el estado actual y permiten interactuar.
5. Ninguna pantalla reproduce audio: todas las interacciones del usuario (play/pausa, siguiente, anterior, volumen, aceptar/omitir notificación) solo escriben comandos en Firebase; es el teléfono quien procesa esos comandos y refleja el nuevo estado real de vuelta al reloj.
6. El nivel de batería (`nivelBateria`) es la única pieza de estado que **no** viene de Firebase: se lee directo del sistema Android del propio reloj mediante un `BroadcastReceiver`.
