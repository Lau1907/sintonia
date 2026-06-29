package mx.utng.sintonia.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.utng.sintonia.ui.theme.SintoniaDark
import mx.utng.sintonia.ui.theme.SintoniaGreen
import mx.utng.sintonia.viewmodel.PlayerViewModel

data class NavItem(val label: String, val icon: ImageVector, val route: String)

@Composable
fun MainScreen(viewModel: PlayerViewModel = viewModel()) {
    val navItems = listOf(
        NavItem("Inicio", Icons.Default.Home, "home"),
        NavItem("Buscar", Icons.Default.Search, "search"),
        NavItem("Cola", Icons.Default.QueueMusic, "queue"),
        NavItem("Descarg", Icons.Default.Download, "downloads"),
        NavItem("Config", Icons.Default.Settings, "settings")
    )
    var selectedRoute by remember { mutableStateOf("home") }
    Scaffold(
        containerColor = SintoniaDark,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF1A1A1A)) {
                navItems.forEach { item ->
                    NavigationBarItem(
                        selected = selectedRoute == item.route,
                        onClick = { selectedRoute = item.route },
                        icon = {
                            Icon(item.icon, contentDescription = item.label)
                        },
                        label = { Text(item.label, fontSize = androidx.compose.ui.unit.TextUnit.Unspecified) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SintoniaGreen,
                            selectedTextColor = SintoniaGreen,
                            unselectedIconColor = Color(0xFF666666),
                            unselectedTextColor = Color(0xFF666666),
                            indicatorColor = Color(0xFF1A1A1A)
                        )
                    )
                }
            }
        }
    ) { padding ->
        when (selectedRoute) {
            "home" -> HomeScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(padding)
            )
            "search" -> SpotifyScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(padding)
            )
            "queue" -> QueueScreen(modifier = Modifier.padding(padding))
            "downloads" -> DownloadsScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(padding)
            )
            "settings" -> SettingsScreen(modifier = Modifier.padding(padding))
        }
    }
}