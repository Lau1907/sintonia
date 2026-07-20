package mx.utng.sintonia.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import mx.utng.sintonia.data.model.Song
import mx.utng.sintonia.ui.theme.SintoniaCard
import mx.utng.sintonia.ui.theme.SintoniaDark
import mx.utng.sintonia.ui.theme.SintoniaGreen
import mx.utng.sintonia.ui.theme.SintoniaPink
import mx.utng.sintonia.ui.theme.SintoniaSubtext
import mx.utng.sintonia.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: PlayerViewModel = viewModel(),
    navController: NavController? = null,
    modifier: Modifier = Modifier
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val currentSource by viewModel.currentSource.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val progress by viewModel.progress.collectAsState()

    Scaffold(
        modifier = modifier,
        containerColor = SintoniaDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "SINFONÍA", fontWeight = FontWeight.Bold,
                            color = SintoniaGreen, fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Surface(
                            color = SintoniaGreen.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Wifi, contentDescription = null,
                                    tint = SintoniaGreen, modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("En vivo", color = SintoniaGreen, fontSize = 11.sp)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SintoniaDark)
            )
        },
        bottomBar = {
            if (playbackState.currentSong.title.isNotEmpty()) {
                PlayerBar(
                    song = playbackState.currentSong,
                    isPlaying = playbackState.isPlaying,
                    onTogglePlay = { viewModel.togglePlayPause() },
                    onNext = { viewModel.nextSong() },
                    onPrevious = { viewModel.previousSong() }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "FUENTE DE REPRODUCCIÓN", color = SintoniaSubtext,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SourceButton(
                            label = "Spotify",
                            sublabel = "Conectado",
                            icon = Icons.Default.MusicNote,
                            color = SintoniaGreen,
                            selected = currentSource == "spotify",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.setSource("spotify")
                                navController?.navigate("spotify")
                            }
                        )
                        SourceButton(
                            label = "Jamendo",
                            sublabel = "Gratuito",
                            icon = Icons.Default.LibraryMusic,
                            color = Color(0xFF4A9EFF),
                            selected = currentSource == "jamendo",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.setSource("jamendo")
                                viewModel.loadPopularTracks()
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SourceButton(
                            label = "Radio Garden",
                            sublabel = "Radio en vivo",
                            icon = Icons.Default.Radio,
                            color = SintoniaPink,
                            selected = currentSource == "radio",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.setSource("radio")
                                navController?.navigate("radio")
                            }
                        )
                        SourceButton(
                            label = "YouTube",
                            sublabel = "Video",
                            icon = Icons.Default.PlayCircle,
                            color = Color(0xFFFF0000),
                            selected = currentSource == "youtube",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.setSource("youtube")
                                navController?.navigate("youtube")
                            }
                        )
                    }
                }
            }

            if (playbackState.currentSong.title.isNotEmpty()) {
                item {
                    Text(
                        "REPRODUCIENDO AHORA", color = SintoniaSubtext,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    NowPlayingCard(
                        song = playbackState.currentSong,
                        isPlaying = playbackState.isPlaying,
                        source = currentSource,
                        progress = progress,
                        onTogglePlay = { viewModel.togglePlayPause() },
                        onNext = { viewModel.nextSong() },
                        onPrevious = { viewModel.previousSong() }
                    )
                }
            }

            if (currentSource == "jamendo" && songs.isNotEmpty()) {
                item {
                    Text(
                        "CANCIONES POPULARES", color = SintoniaSubtext,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                }
                items(songs.take(5)) { song ->
                    SongCard(
                        song = song,
                        isPlaying = playbackState.currentSong.id == song.id && playbackState.isPlaying,
                        downloadStatus = downloads.find { it.id == song.id },
                        onClick = { viewModel.playSong(song) },
                        onDownloadClick = { viewModel.downloadSong(song) }
                    )
                }
            }
        }
    }
}

@Composable
fun SourceButton(
    label: String,
    sublabel: String,
    icon: ImageVector,
    color: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(90.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) color.copy(alpha = 0.25f) else SintoniaCard
        ),
        shape = RoundedCornerShape(16.dp),
        border = if (selected) BorderStroke(1.5.dp, color) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                icon, contentDescription = null, tint = color,
                modifier = Modifier.size(28.dp)
            )
            Column {
                Text(
                    label, color = Color.White, fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(sublabel, color = color, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun NowPlayingCard(
    song: Song,
    isPlaying: Boolean,
    source: String,
    progress: Float,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SintoniaCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = song.albumCover,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song.title, color = Color.White, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.artist, color = SintoniaSubtext, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    color = when (source) {
                        "spotify" -> SintoniaGreen
                        "radio" -> SintoniaPink
                        "youtube" -> Color(0xFFFF0000)
                        else -> Color(0xFF4A9EFF)
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        source.replaceFirstChar { it.uppercase() },
                        color = Color.White, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Barra de progreso — animada para radio, real para canciones
            if (source == "radio") {
                val infiniteTransition = rememberInfiniteTransition(label = "radio")
                val radioProgress by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(3000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "radioBar"
                )
                LinearProgressIndicator(
                    progress = { radioProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = SintoniaPink,
                    trackColor = SintoniaDark
                )
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = SintoniaGreen,
                    trackColor = SintoniaDark
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(
                        Icons.Default.SkipPrevious, contentDescription = null,
                        tint = SintoniaSubtext, modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                FloatingActionButton(
                    onClick = onTogglePlay,
                    containerColor = SintoniaGreen,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(onClick = onNext) {
                    Icon(
                        Icons.Default.SkipNext, contentDescription = null,
                        tint = SintoniaSubtext, modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SourceChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) color.copy(alpha = 0.2f) else SintoniaCard,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (selected) color else Color.Transparent)
    ) {
        Text(
            label,
            color = if (selected) color else SintoniaSubtext,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun SongCard(
    song: Song,
    isPlaying: Boolean,
    downloadStatus: Song?,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) SintoniaGreen.copy(alpha = 0.2f) else SintoniaCard
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = song.albumCover,
                contentDescription = null,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title, color = Color.White, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artist, color = SintoniaSubtext, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            when {
                downloadStatus == null -> {
                    IconButton(onClick = onDownloadClick) {
                        Icon(
                            Icons.Default.Download, contentDescription = "Descargar",
                            tint = SintoniaSubtext
                        )
                    }
                }
                !downloadStatus.descargada -> {
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { downloadStatus.progresoDescarga / 100f },
                            color = SintoniaGreen,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                else -> {
                    Icon(
                        Icons.Default.CheckCircle, contentDescription = "Descargada",
                        tint = SintoniaGreen
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            if (isPlaying) {
                Icon(Icons.Default.Pause, contentDescription = null, tint = SintoniaGreen)
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = SintoniaSubtext)
            }
        }
    }
}

@Composable
fun PlayerBar(
    song: Song,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = SintoniaCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = song.albumCover,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title, color = Color.White, fontSize = 13.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artist, color = SintoniaSubtext, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onPrevious) {
                Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = SintoniaSubtext)
            }
            IconButton(onClick = onTogglePlay) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = SintoniaGreen,
                    modifier = Modifier.size(32.dp)
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.SkipNext, contentDescription = null, tint = SintoniaSubtext)
            }
        }
    }
}