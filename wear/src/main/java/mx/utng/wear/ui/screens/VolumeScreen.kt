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