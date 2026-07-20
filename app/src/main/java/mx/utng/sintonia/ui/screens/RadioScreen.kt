package mx.utng.sintonia.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
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
import mx.utng.sintonia.ui.theme.SintoniaGreen
import mx.utng.sintonia.ui.theme.SintoniaPink
import mx.utng.sintonia.ui.theme.SintoniaSubtext
import mx.utng.sintonia.viewmodel.PlayerViewModel

data class RadioStation(
    val id: String,
    val name: String,
    val city: String,
    val country: String,
    val genre: String,
    val streamUrl: String
)

val popularStations = listOf(
    RadioStation("radio-unam", "Radio UNAM", "Ciudad de México", "México", "Cultura", "https://radiounam.unam.mx/stream"),
    RadioStation("bbc-radio6", "BBC Radio 6 Music", "Londres", "Reino Unido", "Internacional", "http://stream.live.vc.bbcmedia.co.uk/bbc_6music"),
    RadioStation("radio-nacional", "Radio Nacional", "Buenos Aires", "Argentina", "Noticias", "https://icecast.radionacional.com.ar/nacional.mp3"),
    RadioStation("nhk-world", "NHK World Radio", "Tokio", "Japón", "Internacional", "https://nhkworld.jp/stream"),
    RadioStation("rtve-radio1", "RNE Radio 1", "Madrid", "España", "Noticias", "https://rne.stream.rtve.es/rne_r1/live.mp3"),
    RadioStation("france-inter", "France Inter", "París", "Francia", "Cultura", "https://icecast.radiofrance.fr/franceinter-midfi.mp3"),
    RadioStation("deutschlandfunk", "Deutschlandfunk", "Colonia", "Alemania", "Noticias", "https://st01.sslstream.dlf.de/dlf/01/128/mp3/stream.mp3"),
    RadioStation("wnyc", "WNYC", "Nueva York", "EE.UU.", "Cultura", "https://fm939.wnyc.org/wnycfm.aac"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(viewModel: PlayerViewModel, modifier: Modifier = Modifier) {
    var searchQuery by remember { mutableStateOf("") }
    val playbackState by viewModel.playbackState.collectAsState()

    val filteredStations = remember(searchQuery) {
        if (searchQuery.isEmpty()) popularStations
        else popularStations.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.city.contains(searchQuery, ignoreCase = true) ||
                    it.genre.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = SintoniaDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Radio Garden", fontWeight = FontWeight.Bold,
                            color = Color.White, fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = SintoniaPink.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("En vivo", color = SintoniaPink, fontSize = 11.sp,
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
                placeholder = { Text("Ciudad, país o estación...", color = SintoniaSubtext) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SintoniaPink) },
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
            Text("ESTACIONES POPULARES", color = SintoniaSubtext, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredStations) { station ->
                    RadioStationCard(
                        station = station,
                        isPlaying = playbackState.currentSong.id == station.id && playbackState.isPlaying,
                        onClick = { viewModel.playRadioStation(station.id, station.name, station.city, station.streamUrl) }
                    )
                }
            }
        }
    }
}

@Composable
fun RadioStationCard(station: RadioStation, isPlaying: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
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
                    Icon(Icons.Default.Radio, contentDescription = null,
                        tint = if (isPlaying) SintoniaPink else SintoniaSubtext,
                        modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(station.name, color = Color.White, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${station.city} · ${station.genre}", color = SintoniaSubtext, fontSize = 12.sp)
            }
            if (isPlaying) {
                Surface(
                    color = SintoniaPink,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("● EN VIVO", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                }
            }
        }
    }
}