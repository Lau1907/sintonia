package mx.utng.sintonia.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import mx.utng.sintonia.data.model.Song
import mx.utng.sintonia.ui.theme.SintoniaCard
import mx.utng.sintonia.ui.theme.SintoniaDark
import mx.utng.sintonia.ui.theme.SintoniaGreen
import mx.utng.sintonia.ui.theme.SintoniaPink
import mx.utng.sintonia.ui.theme.SintoniaSubtext
import mx.utng.sintonia.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(viewModel: PlayerViewModel, modifier: Modifier = Modifier) {
    val queue by viewModel.queue.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    Scaffold(
        modifier = modifier,
        containerColor = SintoniaDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Cola de reproducción",
                            fontWeight = FontWeight.Bold, color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (queue.isNotEmpty()) {
                            Surface(
                                color = SintoniaGreen.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "${queue.size} canciones",
                                    color = SintoniaGreen, fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (queue.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearQueue() }) {
                            Text("Limpiar", color = SintoniaSubtext, fontSize = 13.sp)
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
            // Canción actual
            if (playbackState.currentSong.title.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "REPRODUCIENDO AHORA", color = SintoniaSubtext,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                CurrentSongCard(song = playbackState.currentSong, source = playbackState.source)
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                "PRÓXIMAS CANCIONES", color = SintoniaSubtext,
                fontSize = 11.sp, fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (queue.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.QueueMusic, contentDescription = null,
                            tint = SintoniaSubtext, modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No hay canciones en la cola",
                            color = SintoniaSubtext, fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Usa el botón + en Jamendo o Spotify",
                            color = SintoniaSubtext.copy(alpha = 0.6f), fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(queue) { index, song ->
                        QueueSongCard(
                            index = index + 1,
                            song = song,
                            onPlay = { viewModel.playFromQueue(song) },
                            onRemove = { viewModel.removeFromQueue(song.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CurrentSongCard(song: Song, source: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = SintoniaGreen.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = SintoniaGreen.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PlayArrow, contentDescription = null,
                        tint = SintoniaGreen, modifier = Modifier.size(24.dp)
                    )
                }
            }
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
                    else -> Color(0xFF4A9EFF)
                },
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    source.replaceFirstChar { it.uppercase() },
                    color = Color.White, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
fun QueueSongCard(
    index: Int,
    song: Song,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SintoniaCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.DragHandle, contentDescription = null,
                tint = SintoniaSubtext, modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "$index.", color = SintoniaSubtext, fontSize = 13.sp,
                modifier = Modifier.width(24.dp)
            )
            Surface(
                color = SintoniaDark,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.MusicNote, contentDescription = null,
                        tint = when (song.source) {
                            "spotify" -> SintoniaGreen
                            "radio" -> SintoniaPink
                            else -> Color(0xFF4A9EFF)
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title, color = Color.White, fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${song.artist} · ${song.source.replaceFirstChar { it.uppercase() }}",
                    color = SintoniaSubtext, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            // Reproducir
            IconButton(onClick = onPlay) {
                Icon(
                    Icons.Default.PlayArrow, contentDescription = "Reproducir",
                    tint = SintoniaGreen
                )
            }
            // Quitar de la cola
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close, contentDescription = "Quitar",
                    tint = SintoniaSubtext
                )
            }
        }
    }
}