package mx.utng.tv

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

val TvBackground = Color(0xFF0A0A0A)
val TvSurface = Color(0xFF1A1A1A)
val TvGreen = Color(0xFF1DB954)
val TvPink = Color(0xFFE91E8C)
val TvBlue = Color(0xFF4A9EFF)
val TvRed = Color(0xFFFF0000)
val TvSubtext = Color(0xFF888888)

val youtubeQueue = listOf(
    Triple("Starboy", "The Weeknd", "4:05"),
    Triple("Save Your Tears", "The Weeknd", "3:35"),
    Triple("Heartless", "The Weeknd", "3:29"),
)

@Composable
fun TvDashboardScreen() {
    var state by remember { mutableStateOf(TvPlayerState()) }
    var localProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        FirebaseTvSync.observePlayerState().collect { newState ->
            if (newState.currentTitle != state.currentTitle) {
                localProgress = 0f
            }
            state = newState
            localProgress = newState.progress
        }
    }

    // Timer local que avanza el progreso sin depender de Firebase
    LaunchedEffect(state.isPlaying, state.currentTitle) {
        while (state.isPlaying && state.duration > 0) {
            delay(500)
            val increment = 0.5f / state.duration.toFloat()
            localProgress = (localProgress + increment).coerceIn(0f, 1f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
    ) {
        if (state.currentCoverUrl.isNotEmpty()) {
            AsyncImage(
                model = state.currentCoverUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.06f
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            TvBackground.copy(alpha = 0.7f),
                            TvBackground
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "SINFONÍA — ANDROID TV DASHBOARD",
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvStatusDot(
                        label = state.source.replaceFirstChar { it.uppercase() },
                        color = when (state.source) {
                            "spotify" -> TvGreen
                            "radio" -> TvPink
                            "youtube" -> TvRed
                            else -> TvBlue
                        }
                    )
                    TvStatusDot(label = "Phone", color = Color.Cyan)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (state.currentTitle.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎵", fontSize = 72.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Esperando reproducción...",
                            color = TvSubtext, fontSize = 28.sp, fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Abre Sinfonía en tu teléfono para comenzar",
                            color = TvSubtext.copy(alpha = 0.5f), fontSize = 18.sp
                        )
                    }
                }
            } else if (state.source == "youtube") {
                TvYouTubeLayout(state = state)
            } else {
                TvMusicLayout(state = state, progress = localProgress)
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "Controlado desde: Smartphone · Firebase",
                    color = TvSubtext.copy(alpha = 0.3f), fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun TvMusicLayout(state: TvPlayerState, progress: Float) {
    val accentColor = when (state.source) {
        "spotify" -> TvGreen
        "radio" -> TvPink
        else -> TvBlue
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Columna izquierda ─────────────────────────────────────────────────
        Column(
            modifier = Modifier.weight(1.5f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Portada
                if (state.source == "radio") {
                    TvRadioVisualizer(isPlaying = state.isPlaying)
                } else {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(TvSurface)
                    ) {
                        AsyncImage(
                            model = state.currentCoverUrl.ifEmpty { null },
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                // Info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = accentColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            when (state.source) {
                                "spotify" -> "● Spotify"
                                "radio" -> "● Radio en vivo"
                                else -> "● Jamendo"
                            },
                            color = accentColor, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Text(
                        state.currentTitle,
                        color = Color.White, fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 44.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        state.currentArtist,
                        color = TvSubtext, fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )

                    // Barra de progreso + tiempo
                    if (state.source == "radio") {
                        TvRadioProgressBar()
                    } else {
                        Column {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = accentColor,
                                trackColor = TvSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    formatTvTime((progress * state.duration * 1000L).toLong()),
                                    color = TvSubtext, fontSize = 12.sp
                                )
                                Text(
                                    formatTvTime(state.duration * 1000L),
                                    color = TvSubtext, fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Controles
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            color = TvSurface, shape = CircleShape,
                            modifier = Modifier
                                .size(44.dp)
                                .clickable { FirebaseTvSync.sendSkipPrevious() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("⏮", fontSize = 18.sp, color = TvSubtext)
                            }
                        }

                        Surface(
                            color = accentColor, shape = CircleShape,
                            modifier = Modifier
                                .size(52.dp)
                                .clickable { FirebaseTvSync.sendPlayPause(state.isPlaying) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    if (state.isPlaying) "⏸" else "▶",
                                    fontSize = 20.sp, color = Color.Black
                                )
                            }
                        }

                        Surface(
                            color = TvSurface, shape = CircleShape,
                            modifier = Modifier
                                .size(44.dp)
                                .clickable { FirebaseTvSync.sendSkipNext() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("⏭", fontSize = 18.sp, color = TvSubtext)
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (state.isPlaying) accentColor else TvSubtext)
                            )
                            Text(
                                if (state.isPlaying) "Reproduciendo" else "Pausado",
                                color = if (state.isPlaying) accentColor else TvSubtext,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // ── Columna derecha — Cola ────────────────────────────────────────────
        Column(
            modifier = Modifier.weight(0.8f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "COLA DE REPRODUCCIÓN",
                color = TvSubtext, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Canción actual
            Surface(
                color = accentColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            state.currentTitle,
                            color = accentColor, fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            state.currentArtist,
                            color = TvSubtext, fontSize = 12.sp, maxLines = 1
                        )
                    }
                    Text("▶", color = accentColor, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (state.queue.isEmpty()) {
                Text(
                    "No hay canciones en cola",
                    color = TvSubtext.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            } else {
                state.queue.forEach { (title, artist) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                title,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(artist, color = TvSubtext, fontSize = 11.sp)
                        }
                    }
                    HorizontalDivider(color = TvSurface, thickness = 0.5.dp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Controlado desde\nSmartphone · Firebase",
                color = TvSubtext.copy(alpha = 0.4f),
                fontSize = 11.sp, lineHeight = 16.sp
            )
        }
    }
}

fun formatTvTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val min = totalSeconds / 60
    val sec = totalSeconds % 60
    return "%d:%02d".format(min, sec)
}

@Composable
fun TvYouTubeLayout(state: TvPlayerState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Column(
            modifier = Modifier.weight(1.5f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val videoId = state.audioUrl
                .removePrefix("https://www.youtube.com/watch?v=")
                .removePrefix("youtube:")
                .take(11)

            if (videoId.isNotEmpty()) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            webViewClient = WebViewClient()
                            webChromeClient = WebChromeClient()
                            settings.apply {
                                javaScriptEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                domStorageEnabled = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                userAgentString = "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36"
                            }
                            loadUrl("https://www.youtube.com/embed/$videoId?autoplay=1&controls=1")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TvSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Text("▶", color = TvRed, fontSize = 48.sp)
                }
            }

            Text(
                state.currentTitle, color = Color.White, fontSize = 22.sp,
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(state.currentArtist, color = TvSubtext, fontSize = 14.sp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = TvRed, shape = CircleShape,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { FirebaseTvSync.sendPlayPause(state.isPlaying) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            if (state.isPlaying) "⏸" else "▶",
                            fontSize = 20.sp, color = Color.White
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "A CONTINUACIÓN", color = TvSubtext, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            youtubeQueue.forEachIndexed { index, (title, channel, duration) ->
                Surface(
                    color = if (index == 0) TvRed.copy(alpha = 0.2f) else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                title,
                                color = if (index == 0) TvRed else Color.White,
                                fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text("$channel · $duration", color = TvSubtext, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Canal", color = TvSubtext, fontSize = 12.sp)
            Text(
                state.currentArtist, color = Color.White,
                fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
            Text("23.4M suscriptores", color = TvSubtext, fontSize = 12.sp)
        }
    }
}

@Composable
fun TvStatusDot(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(label, color = Color.LightGray, fontSize = 14.sp)
    }
}

@Composable
fun TvRadioVisualizer(isPlaying: Boolean) {
    Box(
        modifier = Modifier
            .size(240.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(TvPink.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("📻", fontSize = 72.sp)
            Spacer(modifier = Modifier.height(16.dp))
            if (isPlaying) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(36.dp)
                ) {
                    repeat(10) { index ->
                        val infiniteTransition =
                            rememberInfiniteTransition(label = "bar$index")
                        val height by infiniteTransition.animateFloat(
                            initialValue = 0.2f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(
                                    durationMillis = 300 + (index * 70),
                                    easing = FastOutSlowInEasing
                                ),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "h$index"
                        )
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .fillMaxHeight(height)
                                .clip(RoundedCornerShape(3.dp))
                                .background(TvPink)
                        )
                    }
                }
            } else {
                Text("En pausa", color = TvPink, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun TvRadioProgressBar() {
    val infiniteTransition = rememberInfiniteTransition(label = "radioProgress")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bar"
    )
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp)),
        color = TvPink,
        trackColor = TvSurface
    )
}