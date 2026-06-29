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