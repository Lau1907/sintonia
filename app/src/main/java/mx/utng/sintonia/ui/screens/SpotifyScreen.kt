package mx.utng.sintonia.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationResponse
import mx.utng.sintonia.data.model.Song
import mx.utng.sintonia.data.remote.SpotifyAuthManager
import mx.utng.sintonia.ui.theme.SintoniaCard
import mx.utng.sintonia.ui.theme.SintoniaDark
import mx.utng.sintonia.ui.theme.SintoniaGreen
import mx.utng.sintonia.ui.theme.SintoniaSubtext
import mx.utng.sintonia.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    navController: NavController? = null
) {
    val context = LocalContext.current
    val songs by viewModel.spotifySongs.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val spotifyToken by viewModel.spotifyToken.collectAsState()
    val spotifyProgress by viewModel.spotifyProgress.collectAsState()
    val spotifyDuration by viewModel.spotifyDuration.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val spotifyAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val response = AuthorizationClient.getResponse(result.resultCode, result.data)
        when (response.type) {
            AuthorizationResponse.Type.TOKEN -> viewModel.setSpotifyToken(response.accessToken)
            AuthorizationResponse.Type.ERROR -> android.util.Log.e("SpotifyAuth", "Error: ${response.error}")
            else -> {}
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = SintoniaDark,
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
                            "Spotify", fontWeight = FontWeight.Bold,
                            color = SintoniaGreen, fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (spotifyToken != null) {
                            Surface(
                                color = SintoniaGreen.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "Conectado", color = SintoniaGreen, fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (spotifyToken != null) {
                        IconButton(onClick = { viewModel.logoutSpotify() }) {
                            Icon(
                                Icons.Default.ExitToApp, contentDescription = "Cerrar sesión",
                                tint = SintoniaSubtext
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SintoniaDark)
            )
        },
        bottomBar = {
            if (playbackState.currentSong.title.isNotEmpty() && playbackState.source == "spotify") {
                SpotifyPlayerBar(
                    song = playbackState.currentSong,
                    isPlaying = playbackState.isPlaying,
                    progress = spotifyProgress,
                    duration = spotifyDuration,
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
            if (spotifyToken == null) {
                // ── Pantalla de login ─────────────────────────────────────────
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.MusicNote, contentDescription = null,
                            tint = SintoniaGreen, modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Conecta tu cuenta de Spotify",
                            color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Necesitas cuenta Premium para reproducir",
                            color = SintoniaSubtext, fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                val request = SpotifyAuthManager.getAuthRequest()
                                val intent = AuthorizationClient.createLoginActivityIntent(
                                    context as Activity, request
                                )
                                spotifyAuthLauncher.launch(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SintoniaGreen),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.MusicNote, contentDescription = null,
                                tint = Color.Black
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Iniciar sesión con Spotify", color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                // ── Pantalla principal con token ──────────────────────────────
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar en Spotify...", color = SintoniaSubtext) },
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchQuery.isNotBlank()) {
                                viewModel.setSource("spotify")
                                viewModel.searchSpotifyTracks(searchQuery)
                            }
                        }
                    ),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            TextButton(onClick = {
                                viewModel.setSource("spotify")
                                viewModel.searchSpotifyTracks(searchQuery)
                            }) {
                                Text("Buscar", color = SintoniaGreen)
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (searchQuery.isEmpty()) "CANCIONES POPULARES" else "RESULTADOS",
                    color = SintoniaSubtext, fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SintoniaGreen)
                    }
                } else if (songs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Search, contentDescription = null,
                                tint = SintoniaSubtext, modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Busca una canción en Spotify", color = SintoniaSubtext)
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(songs) { song ->
                            SpotifySongCard(
                                song = song,
                                isPlaying = playbackState.currentSong.id == song.id && playbackState.isPlaying,
                                onClick = { viewModel.playSongSpotify(song, context) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpotifyPlayerBar(
    song: Song,
    isPlaying: Boolean,
    progress: Float,
    duration: Long,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = SintoniaCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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
                        fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.artist, color = SintoniaSubtext, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    color = SintoniaGreen,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "Spotify", color = Color.Black, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = SintoniaGreen,
                trackColor = SintoniaDark
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatTime((progress * (duration / 1000f)).toInt()),
                    color = SintoniaSubtext, fontSize = 10.sp
                )
                Text(
                    formatTime((duration / 1000).toInt()),
                    color = SintoniaSubtext, fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

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
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(26.dp)
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

fun formatTime(seconds: Int): String {
    val min = seconds / 60
    val sec = seconds % 60
    return "%d:%02d".format(min, sec)
}

@Composable
fun SpotifySongCard(song: Song, isPlaying: Boolean, onClick: () -> Unit) {
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
            if (isPlaying) {
                Icon(Icons.Default.Pause, contentDescription = null, tint = SintoniaGreen)
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = SintoniaSubtext)
            }
        }
    }
}