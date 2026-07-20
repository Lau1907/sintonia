package mx.utng.sintonia.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.utng.sintonia.ui.theme.SintoniaCard
import mx.utng.sintonia.ui.theme.SintoniaDark
import mx.utng.sintonia.ui.theme.SintoniaPink
import mx.utng.sintonia.ui.theme.SintoniaSubtext
import mx.utng.sintonia.viewmodel.PlayerViewModel

data class RadioStation(
    val id: String,
    val name: String,
    val city: String,
    val genre: String,
    val streamUrl: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(viewModel: PlayerViewModel, modifier: Modifier = Modifier, onBack: () -> Unit = {}) {
    var searchQuery by remember { mutableStateOf("") }
    val playbackState by viewModel.playbackState.collectAsState()
    val stations by viewModel.radioStations.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadTopRadioStations()
    }

    Scaffold(
        modifier = modifier,
        containerColor = SintoniaDark,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {   // ← aquí estaba el problema, era solo Icon sin onClick
                        Icon(
                            Icons.Default.ArrowBack, contentDescription = "Atrás",
                            tint = Color.White
                        )
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Radio Garden", fontWeight = FontWeight.Bold,
                            color = Color.White, fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = SintoniaPink.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "En vivo", color = SintoniaPink, fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
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
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { query ->
                    searchQuery = query
                    if (query.isEmpty()) {
                        viewModel.loadTopRadioStations()
                    } else {
                        viewModel.searchRadioStations(query)
                    }
                },
                placeholder = { Text("Ciudad, país o estación...", color = SintoniaSubtext) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = SintoniaPink)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SintoniaPink,
                    unfocusedBorderColor = SintoniaCard,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = SintoniaPink
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                if (searchQuery.isEmpty()) "ESTACIONES POPULARES" else "RESULTADOS",
                color = SintoniaSubtext,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SintoniaPink)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(stations) { station ->
                        RadioStationCard(
                            station = station,
                            isPlaying = playbackState.currentSong.id == station.id && playbackState.isPlaying,
                            onClick = {
                                viewModel.playRadioStation(
                                    station.id, station.name, station.city, station.streamUrl
                                )
                            }
                        )
                    }

                    if (playbackState.source == "radio" && playbackState.isPlaying) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "ONDA DE AUDIO — ${playbackState.currentSong.title.uppercase()}",
                                color = SintoniaSubtext, fontSize = 11.sp, fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            AudioWaveVisualizer()
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioWaveVisualizer() {
    val barCount = 20
    val animations = List(barCount) { index ->
        val infiniteTransition = rememberInfiniteTransition(label = "wave$index")
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 400 + (index * 50),
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar$index"
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        animations.forEach { anim ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(anim.value)
            ) {
                HorizontalDivider(
                    modifier = Modifier.fillMaxSize(),
                    color = SintoniaPink,
                    thickness = 4.dp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioStationCard(station: RadioStation, isPlaying: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) SintoniaPink.copy(alpha = 0.15f) else SintoniaCard
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = if (isPlaying) SintoniaPink.copy(alpha = 0.3f) else SintoniaCard,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Radio, contentDescription = null,
                        tint = if (isPlaying) SintoniaPink else SintoniaSubtext,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    station.name, color = Color.White, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${station.city} · ${station.genre}",
                    color = SintoniaSubtext, fontSize = 12.sp
                )
            }
            if (isPlaying) {
                Surface(
                    color = SintoniaPink,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "● En vivo", color = Color.White, fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}