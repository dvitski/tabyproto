package cc.dvitski.tabyproto.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneAndroid
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
import cc.dvitski.tabyproto.Animation
import cc.dvitski.tabyproto.TabyDevice
import cc.dvitski.tabyproto.TabyTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    devices: List<TabyDevice>,
    activeDeviceId: String?,
    lastSent: Animation?,
    brightness: Int?,
    onBrightnessChange: (Int) -> Unit,
    musicState: MusicState,
    onMusicControl: (MediaControl, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    Column(
        modifier = modifier.fillMaxWidth().background(theme.background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DevicePanel(devices, activeDeviceId, lastSent, brightness, onBrightnessChange, Modifier.fillMaxWidth())
        val windowSize = LocalWindowSize.current
        if (windowSize == WindowSize.Expanded) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MusicPanel(musicState, onMusicControl, Modifier.weight(1f))
                MobilePanel(Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MusicPanel(musicState, onMusicControl, Modifier.fillMaxWidth())
                MobilePanel(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PanelHeader(label: String, icon: @Composable () -> Unit) {
    val theme = LocalAppTheme.current
    Box(Modifier.fillMaxWidth().height(4.dp).background(theme.accent))
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon()
        Text(label, color = theme.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    }
}

@Composable
private fun DevicePanel(
    devices: List<TabyDevice>,
    activeDeviceId: String?,
    lastSent: Animation?,
    brightness: Int?,
    onBrightnessChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val active = devices.firstOrNull { it.id == activeDeviceId }
    val online = active?.online == true

    Column(
        modifier = modifier
            .clip(AppCardShape)
            .background(theme.surface)
            .border(1.dp, theme.border, AppCardShape),
    ) {
        PanelHeader("DEVICE") {
            Icon(Icons.Rounded.Devices, contentDescription = null, tint = theme.accent, modifier = Modifier.size(18.dp))
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = if (online) 4.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(11.dp).clip(CircleShape).background(if (online) theme.onlineGreen else theme.textSecondary))
            Text(
                text = if (active != null && online) "Connected · ${when (active.transport) {
                    TabyTransport.USB -> "USB"; TabyTransport.WIFI -> "WiFi"; TabyTransport.BLUETOOTH -> "BT"
                }}" else "Disconnected",
                color = theme.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (online) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.Brightness6, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(18.dp))
                Slider(
                    value = (brightness ?: 100).toFloat() / 100f,
                    onValueChange = { onBrightnessChange((it * 100).roundToInt().coerceIn(0, 100)) },
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = theme.accent, activeTrackColor = theme.accent),
                )
                Text("${brightness ?: 100}%", color = theme.textSecondary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun MusicPanel(state: MusicState, onControl: (MediaControl, String?) -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    val accent = theme.accent
    Column(modifier = modifier.clip(AppCardShape).background(theme.surface).border(1.dp, theme.border, AppCardShape)) {
        PanelHeader("MUSIC") {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        }
        when (state) {
            MusicState.Idle -> Text(
                "No music playing",
                color = theme.textSecondary, fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).padding(bottom = 8.dp),
            )
            is MusicState.Active -> Column {
                state.sessions.forEachIndexed { i, session ->
                    if (i > 0) Spacer(Modifier.fillMaxWidth().height(1.dp).background(theme.border))
                    val appId = session.appId
                    SessionRowHome(session = session, accent = accent, onControl = { c -> onControl(c, appId) })
                }
            }
        }
    }
}

@Composable
private fun SessionRowHome(session: NowPlaying, accent: Color, onControl: (MediaControl) -> Unit) {
    val theme = LocalAppTheme.current
    Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 8.dp)) {
        val sourceName = when (session.source) { MusicSource.Spotify -> "Spotify"; MusicSource.Tidal -> "Tidal"; else -> "Music" }
        Text(sourceName, color = if (session.isPlaying) accent else theme.textSecondary, fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlbumArtHome(uri = session.albumArtUri, accent = accent)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(session.track ?: "Unknown", color = theme.textPrimary, fontSize = 15.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(session.artist ?: "", color = theme.textSecondary, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            androidx.compose.material.IconButton(onClick = { onControl(MediaControl.Prev) }) {
                Icon(Icons.Rounded.SkipPrevious, "Prev", tint = theme.textSecondary, modifier = Modifier.size(24.dp))
            }
            androidx.compose.material.IconButton(onClick = { onControl(MediaControl.PlayPause) }) {
                Icon(if (session.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "PlayPause",
                    tint = accent, modifier = Modifier.size(28.dp))
            }
            androidx.compose.material.IconButton(onClick = { onControl(MediaControl.Next) }) {
                Icon(Icons.Rounded.SkipNext, "Next", tint = theme.textSecondary, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun AlbumArtHome(uri: String?, accent: Color) {
    val theme = LocalAppTheme.current
    Box(
        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
            .background(theme.textSecondary.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(uri) {
            bitmap = null
            if (uri != null) bitmap = withContext(Dispatchers.IO) {
                runCatching { File(uri.removePrefix("file://")).inputStream().buffered().use { loadImageBitmap(it) } }.getOrNull()
            }
        }
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = accent.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun MobilePanel(modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    Column(modifier = modifier.clip(AppCardShape).background(theme.surface).border(1.dp, theme.border, AppCardShape)) {
        PanelHeader("MOBILE") {
            Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, tint = theme.accent, modifier = Modifier.size(18.dp))
        }
        Text("No device paired", color = theme.textSecondary, fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp))
    }
}
