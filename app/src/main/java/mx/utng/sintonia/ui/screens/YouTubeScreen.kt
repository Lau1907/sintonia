package mx.utng.sintonia.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import mx.utng.sintonia.ui.theme.SintoniaCard
import mx.utng.sintonia.ui.theme.SintoniaDark
import mx.utng.sintonia.ui.theme.SintoniaSubtext
import mx.utng.sintonia.viewmodel.PlayerViewModel

data class YouTubeVideo(
    val id: String,
    val title: String,
    val channel: String,
    val views: String,
    val thumbnail: String,
    val youtubeUrl: String
)

val sampleVideos = listOf(
    YouTubeVideo("1", "Blinding Lights — Official Music Video",
        "The Weeknd", "1.2B vistas",
        "https://img.youtube.com/vi/4NRXx6U8ABQ/hqdefault.jpg",
        "https://www.youtube.com/watch?v=4NRXx6U8ABQ"),
    YouTubeVideo("2", "Starboy — Official Music Video",
        "The Weeknd", "890M vistas",
        "https://img.youtube.com/vi/34Na4j8AVgA/hqdefault.jpg",
        "https://www.youtube.com/watch?v=34Na4j8AVgA"),
    YouTubeVideo("3", "Save Your Tears — Official Music Video",
        "The Weeknd", "720M vistas",
        "https://img.youtube.com/vi/XXYlFuWEuKI/hqdefault.jpg",
        "https://www.youtube.com/watch?v=XXYlFuWEuKI"),
    YouTubeVideo("4", "As It Was — Official Video",
        "Harry Styles", "900M vistas",
        "https://img.youtube.com/vi/H5v3kku4y6Q/hqdefault.jpg",
        "https://www.youtube.com/watch?v=H5v3kku4y6Q"),
    YouTubeVideo("5", "Flowers — Official Video",
        "Miley Cyrus", "650M vistas",
        "https://img.youtube.com/vi/G7KNmW9a75Y/hqdefault.jpg",
        "https://www.youtube.com/watch?v=G7KNmW9a75Y"),
    YouTubeVideo("6", "Anti-Hero — Official Music Video",
        "Taylor Swift", "580M vistas",
        "https://img.youtube.com/vi/b1kbLwvqugk/hqdefault.jpg",
        "https://www.youtube.com/watch?v=b1kbLwvqugk"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeScreen(modifier: Modifier = Modifier) {
    var searchQuery by remember { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current

    val filteredVideos = remember(searchQuery) {
        if (searchQuery.isEmpty()) sampleVideos
        else sampleVideos.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.channel.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = SintoniaDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("YouTube", fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF0000), fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = Color(0xFFFF0000).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Google Sign-In", color = Color(0xFFFF0000), fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
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
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar videos o música...", color = SintoniaSubtext) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null,
                        tint = Color(0xFFFF0000))
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF0000),
                    unfocusedBorderColor = SintoniaCard,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFFFF0000)
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("VIDEOS POPULARES", color = SintoniaSubtext, fontSize = 11.sp,
                fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filteredVideos) { video ->
                    YouTubeVideoCard(
                        video = video,
                        onClick = { uriHandler.openUri(video.youtubeUrl) }
                    )
                }
            }
        }
    }
}

@Composable
fun YouTubeVideoCard(video: YouTubeVideo, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = SintoniaCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Box {
                AsyncImage(
                    model = video.thumbnail,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    contentScale = ContentScale.Crop
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null,
                            tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
                Surface(
                    color = Color(0xFFFF0000),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Text("Ver en TV", color = Color.White, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(video.title, color = Color.White, fontWeight = FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                Text("${video.channel} · ${video.views}",
                    color = SintoniaSubtext, fontSize = 12.sp)
            }
        }
    }
}