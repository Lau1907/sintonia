package mx.utng.sintonia.ui.screens

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import mx.utng.sintonia.data.model.Song
import mx.utng.sintonia.ui.theme.SintoniaCard
import mx.utng.sintonia.ui.theme.SintoniaDark
import mx.utng.sintonia.ui.theme.SintoniaGreen
import mx.utng.sintonia.ui.theme.SintoniaSubtext
import mx.utng.sintonia.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JamendoScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val songs by viewModel.songs.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        containerColor = SintoniaDark,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Atrás",
                        tint = Color.White, modifier = Modifier.padding(start = 8.dp))
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Jamendo", fontWeight = FontWeight.Bold,
                            color = Color.White, fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = SintoniaGreen.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Creative Commons", color = SintoniaGreen, fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
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
            Text("RESULTADOS", color = SintoniaSubtext,
                fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SintoniaGreen)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(songs) { song ->
                        JamendoSongCard(
                            song = song,
                            isPlaying = playbackState.currentSong.id == song.id && playbackState.isPlaying,
                            downloadStatus = downloads.find { it.id == song.id },
                            onClick = { viewModel.playSong(song) },
                            onDownloadClick = { viewModel.downloadSong(song) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                color = SintoniaGreen.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "ⓘ Descarga legal bajo licencia Creative Commons",
                                    color = SintoniaGreen, fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun JamendoSongCard(
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
        Column {
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
                    Text(
                        if (isPlaying) "En reproducción · ${song.duration / 60}:${String.format("%02d", song.duration % 60)}"
                        else "${song.artist} · ${song.duration / 60}:${String.format("%02d", song.duration % 60)}",
                        color = if (isPlaying) SintoniaGreen else SintoniaSubtext,
                        fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
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
                                color = SintoniaGreen, strokeWidth = 2.dp,
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
            if (isPlaying) {
                LinearProgressIndicator(
                    progress = { 0.45f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = SintoniaGreen,
                    trackColor = SintoniaDark
                )
            }
        }
    }
}