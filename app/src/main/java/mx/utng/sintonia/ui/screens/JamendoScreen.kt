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
import androidx.navigation.NavController
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
    modifier: Modifier = Modifier,
    navController: NavController? = null
) {
    val songs by viewModel.songs.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val queue by viewModel.queue.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = SintoniaDark,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController?.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack, contentDescription = "Atrás",
                            tint = Color.White
                        )
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Jamendo", fontWeight = FontWeight.Bold,
                            color = Color.White, fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = SintoniaGreen.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Creative Commons", color = SintoniaGreen, fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SintoniaDark)
            )
        },
        bottomBar = {
            if (playbackState.currentSong.title.isNotEmpty()) {
                val progress by viewModel.progress.collectAsState()
                val playOnTv by viewModel.playOnTv.collectAsState()
                PlayerBar(
                    song = playbackState.currentSong,
                    isPlaying = playbackState.isPlaying,
                    progress = progress,
                    playOnTv = playOnTv,
                    onTogglePlay = { viewModel.togglePlayPause() },
                    onNext = { viewModel.nextSong() },
                    onPrevious = { viewModel.previousSong() },
                    onToggleTv = { viewModel.togglePlayOnTv() }
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
            Text(
                "RESULTADOS", color = SintoniaSubtext,
                fontSize = 11.sp, fontWeight = FontWeight.Bold
            )
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
                            isInQueue = queue.any { it.id == song.id },
                            onClick = { viewModel.playSong(song) },
                            onDownloadClick = { viewModel.downloadSong(song) },
                            onAddToQueue = {
                                viewModel.addToQueue(song)
                                snackbarMessage = "\"${song.title}\" agregada a la cola"
                            }
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
    isInQueue: Boolean,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onAddToQueue: () -> Unit
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
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song.title, color = Color.White, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (isPlaying) "En reproducción · ${song.duration / 60}:${String.format("%02d", song.duration % 60)}"
                        else "${song.artist} · ${song.duration / 60}:${String.format("%02d", song.duration % 60)}",
                        color = if (isPlaying) SintoniaGreen else SintoniaSubtext,
                        fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                // Botón cola
                IconButton(
                    onClick = onAddToQueue,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (isInQueue) Icons.Default.QueueMusic else Icons.Default.AddToQueue,
                        contentDescription = if (isInQueue) "En cola" else "Agregar a cola",
                        tint = if (isInQueue) SintoniaGreen else SintoniaSubtext,
                        modifier = Modifier.size(20.dp)
                    )
                }
                // Play/Pause
                if (isPlaying) {
                    Icon(Icons.Default.Pause, contentDescription = null,
                        tint = SintoniaGreen, modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null,
                        tint = SintoniaSubtext, modifier = Modifier.size(20.dp))
                }
            }

            // Segunda fila para descarga
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    downloadStatus == null -> {
                        TextButton(
                            onClick = onDownloadClick,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Download, contentDescription = null,
                                tint = SintoniaSubtext, modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Descargar", color = SintoniaSubtext, fontSize = 11.sp)
                        }
                    }
                    !downloadStatus.descargada -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                progress = { downloadStatus.progresoDescarga / 100f },
                                color = SintoniaGreen, strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "${downloadStatus.progresoDescarga}%",
                                color = SintoniaSubtext, fontSize = 11.sp
                            )
                        }
                    }
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle, contentDescription = null,
                                tint = SintoniaGreen, modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Descargada", color = SintoniaGreen, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Barra de progreso si está reproduciendo
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