package mx.utng.sintonia.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import mx.utng.sintonia.ui.screens.DownloadsScreen
import mx.utng.sintonia.ui.screens.HomeScreen
import mx.utng.sintonia.ui.screens.QueueScreen
import mx.utng.sintonia.ui.screens.RadioScreen
import mx.utng.sintonia.ui.screens.YouTubeScreen
import mx.utng.sintonia.ui.theme.SintoniaDark
import mx.utng.sintonia.ui.theme.SintoniaGreen
import mx.utng.sintonia.ui.theme.SintoniaSubtext
import mx.utng.sintonia.viewmodel.PlayerViewModel

val screens = listOf(
    Screen.Home,
    Screen.Radio,
    Screen.Queue,
    Screen.Downloads,
    Screen.YouTube
)

@Composable
fun AppNavigation(viewModel: PlayerViewModel) {
    val navController = rememberNavController()

    Scaffold(
        containerColor = SintoniaDark,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF1A1A1A)) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = {
                            Text(
                                screen.label,
                                fontSize = androidx.compose.ui.unit.TextUnit(
                                    10f,
                                    androidx.compose.ui.unit.TextUnitType.Sp
                                )
                            )
                        },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SintoniaGreen,
                            selectedTextColor = SintoniaGreen,
                            unselectedIconColor = SintoniaSubtext,
                            unselectedTextColor = SintoniaSubtext,
                            indicatorColor = SintoniaGreen.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Home.route) { HomeScreen(viewModel = viewModel) }
            composable(Screen.Radio.route) { RadioScreen(viewModel = viewModel) }
            composable(Screen.Queue.route) { QueueScreen(viewModel = viewModel) }
            composable(Screen.Downloads.route) { DownloadsScreen(viewModel = viewModel) }
            composable(Screen.YouTube.route) { YouTubeScreen() }
        }
    }
}