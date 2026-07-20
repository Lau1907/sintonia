package mx.utng.sintonia.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.utng.sintonia.ui.theme.SintoniaCard
import mx.utng.sintonia.ui.theme.SintoniaDark
import mx.utng.sintonia.ui.theme.SintoniaGreen
import mx.utng.sintonia.ui.theme.SintoniaSubtext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    var notificationsEnabled by remember { mutableStateOf(true) }
    var wifiOnlyDownload by remember { mutableStateOf(true) }
    var autoPlay by remember { mutableStateOf(true) }

    Scaffold(
        modifier = modifier,
        containerColor = SintoniaDark,
        topBar = {
            TopAppBar(
                title = {
                    Text("Configuración", fontWeight = FontWeight.Bold,
                        color = Color.White, fontSize = 20.sp)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SintoniaDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Perfil
            Card(
                colors = CardDefaults.cardColors(containerColor = SintoniaCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null,
                        tint = SintoniaGreen, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Usuario Sintonía", color = Color.White,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Jamendo · Creative Commons", color = SintoniaSubtext,
                            fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("DISPOSITIVOS", color = SintoniaSubtext,
                fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            SettingsItem(
                icon = Icons.Default.Watch,
                title = "Smartwatch",
                subtitle = "Wear OS conectado via Firebase",
                iconTint = SintoniaGreen
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsItem(
                icon = Icons.Default.BrightnessAuto,
                title = "Android TV",
                subtitle = "Dashboard en tiempo real activo",
                iconTint = SintoniaGreen
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("PREFERENCIAS", color = SintoniaSubtext,
                fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            SettingsToggle(
                icon = Icons.Default.Notifications,
                title = "Notificaciones",
                subtitle = "Avisar cuando cambia la canción",
                checked = notificationsEnabled,
                onCheckedChange = { notificationsEnabled = it }
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsToggle(
                icon = Icons.Default.Wifi,
                title = "Descargar solo con WiFi",
                subtitle = "Evitar uso de datos móviles",
                checked = wifiOnlyDownload,
                onCheckedChange = { wifiOnlyDownload = it }
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsToggle(
                icon = Icons.Default.Download,
                title = "Reproducción automática",
                subtitle = "Siguiente canción al terminar",
                checked = autoPlay,
                onCheckedChange = { autoPlay = it }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("ACERCA DE", color = SintoniaSubtext,
                fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            SettingsItem(
                icon = Icons.Default.Info,
                title = "Sintonía v1.0",
                subtitle = "Desarrollo para Dispositivos Inteligentes · UTNG",
                iconTint = SintoniaSubtext
            )
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color = SintoniaGreen
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SintoniaCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = iconTint,
                modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Medium)
                Text(subtitle, color = SintoniaSubtext, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null,
                tint = SintoniaSubtext)
        }
    }
}

@Composable
fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SintoniaCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = SintoniaGreen,
                modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Medium)
                Text(subtitle, color = SintoniaSubtext, fontSize = 12.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SintoniaGreen,
                    uncheckedThumbColor = SintoniaSubtext,
                    uncheckedTrackColor = SintoniaCard
                )
            )
        }
    }
}