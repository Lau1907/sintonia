package mx.utng.sintonia.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import mx.utng.sintonia.ui.theme.SintoniaGreen

@Composable
fun QueueScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.QueueMusic, contentDescription = null, tint = SintoniaGreen, modifier = Modifier.size(48.dp))
            Text("Cola de reproducción", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text("Próximamente", color = Color(0xFF666666), style = MaterialTheme.typography.bodySmall)
        }
    }
}