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