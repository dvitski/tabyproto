package cc.dvitski.tabyproto.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Composable
fun MusicBlockAnimated(musicState: MusicState, onControl: (MediaControl, String?) -> Unit) {
    val session = (musicState as? MusicState.Active)?.primary()
    AnimatedVisibility(
        visible = session != null,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) {
        if (session != null) {
            val appId = session.appId
            MusicBlock(state = session, onControl = { control -> onControl(control, appId) })
        }
    }
}

@Composable
private fun MusicBlock(state: NowPlaying, onControl: (MediaControl) -> Unit) {
    val theme = LocalAppTheme.current
    val accent = theme.accent

    Column(
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth()
            .clip(AppItemShape)
            .border(1.dp, accent.copy(alpha = 0.5f), AppItemShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Rounded.MusicNote, null, tint = accent, modifier = Modifier.size(15.dp))
            Text(
                text = when (state.source) { MusicSource.Spotify -> "SPOTIFY"; MusicSource.Tidal -> "TIDAL"; else -> "MUSIC" },
                color = theme.sidebarText.copy(alpha = 0.4f), fontSize = 11.sp, letterSpacing = 1.sp,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AlbumArt(uri = state.albumArtUri, accent = accent)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    state.track ?: "Unknown",
                    fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    color = theme.sidebarText, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    state.artist ?: "",
                    fontSize = 11.sp, color = theme.sidebarText.copy(alpha = 0.55f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }

        val duration = state.duration
        if (duration != null && duration.inWholeMilliseconds > 0) {
            ProgressBar(initialPosition = state.position, duration = duration, isPlaying = state.isPlaying, accent = accent)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = { onControl(MediaControl.Prev) }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Rounded.SkipPrevious, "Prev", tint = theme.sidebarText.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { onControl(MediaControl.PlayPause) }, modifier = Modifier.size(30.dp)) {
                Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "PlayPause", tint = accent, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { onControl(MediaControl.Next) }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Rounded.SkipNext, "Next", tint = theme.sidebarText.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun AlbumArt(uri: String?, accent: Color) {
    val theme = LocalAppTheme.current
    Box(
        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
            .background(theme.sidebarText.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(uri) {
            bitmap = null
            if (uri != null) {
                bitmap = withContext(Dispatchers.IO) {
                    runCatching {
                        File(uri.removePrefix("file://")).inputStream().buffered().use { loadImageBitmap(it) }
                    }.getOrNull()
                }
            }
        }
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = accent.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ProgressBar(initialPosition: Duration, duration: Duration, isPlaying: Boolean, accent: Color) {
    val theme = LocalAppTheme.current
    var position by remember(initialPosition) { mutableStateOf(initialPosition) }
    LaunchedEffect(initialPosition, isPlaying) {
        position = initialPosition
        if (!isPlaying) return@LaunchedEffect
        while (position < duration) {
            delay(1_000L)
            position += 1.seconds
        }
    }
    val progress = (position.inWholeMilliseconds.toFloat() / duration.inWholeMilliseconds).coerceIn(0f, 1f)
    LinearProgressIndicator(
        progress = progress,
        color = accent,
        backgroundColor = theme.sidebarText.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)),
    )
}
