package mx.utng.sintonia.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Inicio", Icons.Default.Home)
    object Radio : Screen("radio", "Radio", Icons.Default.Radio)
    object Queue : Screen("queue", "Cola", Icons.Default.List)
    object Downloads : Screen("downloads", "Descargas", Icons.Default.Download)
    object YouTube : Screen("youtube", "YouTube", Icons.Default.VideoLibrary)
}