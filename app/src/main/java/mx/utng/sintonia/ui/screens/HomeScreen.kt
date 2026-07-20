package mx.utng.sintonia.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    val songs by viewModel.songs.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val currentSource by viewModel.currentSource.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        containerColor = SintoniaDark,
        topBar = {
            TopAppBar(
                title = {
                    Text("SINTONÍA", fontWeight = FontWeight.Bold,
                        color = SintoniaGreen, fontSize = 20.sp)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Selector de fuente
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SourceChip(
                    label = "Jamendo",
                    selected = currentSource == "jamendo",
                    color = SintoniaGreen,
                    onClick = {
                        viewModel.setSource("jamendo")
                        viewModel.loadPopularTracks()
                    }
                )
                SourceChip(
                    label = "Spotify",
                    selected = currentSource == "spotify",
                    color = SintoniaGreen,
                    onClick = {
                        viewModel.setSource("spotify")
                        navController?.navigate("spotify")
                    }
                )
                SourceChip(
                    label = "Radio",
                    selected = currentSource == "radio",
                    color = SintoniaPink,
                    onClick = {
                        viewModel.setSource("radio")
                        navController?.navigate("radio")
                    }
                )
                SourceChip(
                    label = "YouTube",
                    selected = currentSource == "youtube",
                    color = Color(0xFFFF0000),
                    onClick = {
                        viewModel.setSource("youtube")
                        navController?.navigate("youtube")
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar música gratuita...", color = SintoniaSubtext) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = SintoniaGreen)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SintoniaGreen,
                    unfocusedBorderColor = SintoniaCard,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = SintoniaGreen
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        TextButton(onClick = { viewModel.searchTracks(searchQuery) }) {
                            Text("Buscar", color = SintoniaGreen)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text("Jamendo · Creative Commons", color = SintoniaSubtext, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SintoniaGreen)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(songs) { song ->
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
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
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
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = SintoniaSubtext, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            when {
                downloadStatus == null -> {
                    IconButton(onClick = onDownloadClick) {
                        Icon(Icons.Default.Download, contentDescription = "Descargar",
                            tint = SintoniaSubtext)
                    }
                }
                !downloadStatus.descargada -> {
                    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { downloadStatus.progresoDescarga / 100f },
                            color = SintoniaGreen,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                else -> {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Descargada",
                        tint = SintoniaGreen)
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
        modifier = Modifier.fillMaxWidth().padding(8.dp),
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
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontSize = 13.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = SintoniaSubtext, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
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