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
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Search
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
import androidx.lifecycle.viewmodel.compose.viewModel
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
fun SpotifyScreen(viewModel: PlayerViewModel = viewModel(), modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val songs by viewModel.songs.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val spotifyToken by viewModel.spotifyToken.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    // Launcher para capturar el Token de autenticación de Spotify
    val spotifyAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val response = AuthorizationClient.getResponse(result.resultCode, result.data)
        when (response.type) {
            AuthorizationResponse.Type.TOKEN -> {
                val token = response.accessToken
                viewModel.setSpotifyToken(token)
            }
            AuthorizationResponse.Type.ERROR -> {
                android.util.Log.e("SpotifyAuth", "Error al autenticar: ${response.error}")
            }
            else -> {
                android.util.Log.d("SpotifyAuth", "Cancelado por el usuario")
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = SintoniaDark,
        topBar = {
            TopAppBar(
                title = {
                    Text("Spotify", fontWeight = FontWeight.Bold,
                        color = SintoniaGreen, fontSize = 20.sp)
                },
                actions = {
                    // Botón discreto para cerrar sesión si hay token activo
                    if (spotifyToken != null) {
                        IconButton(onClick = { viewModel.logoutSpotify() }) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = "Cerrar sesión",
                                tint = SintoniaSubtext
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
            if (spotifyToken == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Conecta tu cuenta de Spotify",
                            color = Color.White, fontSize = 16.sp,
                            fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val request = SpotifyAuthManager.getAuthRequest()
                                val intent = AuthorizationClient.createLoginActivityIntent(
                                    context as Activity,
                                    request
                                )
                                spotifyAuthLauncher.launch(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SintoniaGreen)
                        ) {
                            Text("Iniciar sesión con Spotify", color = Color.Black)
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))

                // Campo de texto optimizado para búsqueda
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar en Spotify...", color = SintoniaSubtext) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SintoniaGreen) },
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
                    // 💡 Configuración para habilitar la tecla "Buscar" en el teclado
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
                Text("Spotify · Conectado", color = SintoniaSubtext, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SintoniaGreen)
                    }
                } else if (songs.isEmpty() && searchQuery.isNotBlank()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No se encontraron resultados", color = SintoniaSubtext)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(songs) { song ->
                            SpotifySongCard(
                                song = song,
                                isPlaying = playbackState.currentSong?.id == song.id && playbackState.isPlaying,
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
                    text = song.title,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    color = SintoniaSubtext,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text("♫", color = SintoniaGreen, fontSize = 18.sp)
        }
    }
}