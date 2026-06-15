package cc.dvitski.tabyproto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.DeviceHub
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.dvitski.tabyproto.Animation
import cc.dvitski.tabyproto.TabyDevice
import cc.dvitski.tabyproto.TabyTransport
import io.github.kdroidfilter.composemediaplayer.VideoPlayerSurface
import io.github.kdroidfilter.composemediaplayer.rememberVideoPlayerState

@Composable
fun Sidebar(
    selectedScreen: Screen,
    devices: List<TabyDevice>,
    activeDeviceId: String?,
    lastSent: Animation?,
    listeningState: ListeningState,
    musicState: MusicState,
    brightness: Int?,
    onBrightnessChange: (Int) -> Unit,
    onNavigate: (Screen) -> Unit,
    onVoiceClick: () -> Unit,
    onMusicControl: (MediaControl, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current

    val playerState = rememberVideoPlayerState()
    var videoVisible by remember { mutableStateOf(false) }
    var videoStarted by remember { mutableStateOf(false) }
    var playingIntro by remember { mutableStateOf(false) }

    DisposableEffect(playerState) { onDispose { playerState.dispose() } }

    LaunchedEffect(lastSent) {
        videoStarted = false
        videoVisible = false
        playingIntro = false
        playerState.pause()
        if (lastSent != null) {
            when (lastSent) {
                is Animation.Once -> {
                    val path = AnimationResources.videoPath(lastSent.raw.id) ?: return@LaunchedEffect
                    videoVisible = true
                    playerState.loop = false
                    playerState.openUri(path)
                }
                is Animation.Looping -> {
                    val intro = lastSent.intro
                    if (intro != null) {
                        val introPath = AnimationResources.videoPath(intro.id) ?: return@LaunchedEffect
                        videoVisible = true
                        playingIntro = true
                        playerState.loop = false
                        playerState.openUri(introPath)
                    } else {
                        val bodyPath = AnimationResources.videoPath(lastSent.body.id) ?: return@LaunchedEffect
                        videoVisible = true
                        playerState.loop = true
                        playerState.openUri(bodyPath)
                    }
                }
            }
        }
    }

    LaunchedEffect(playerState.isPlaying, playerState.isLoading) {
        when {
            playerState.isPlaying && !playerState.isLoading -> videoStarted = true
            videoStarted && !playerState.isPlaying && !playerState.isLoading -> {
                val current = lastSent
                if (playingIntro && current is Animation.Looping) {
                    val bodyPath = AnimationResources.videoPath(current.body.id)
                    if (bodyPath != null) {
                        playingIntro = false
                        videoStarted = false
                        playerState.loop = true
                        playerState.openUri(bodyPath)
                        return@LaunchedEffect
                    }
                }
                videoVisible = false
                videoStarted = false
                playingIntro = false
            }
        }
    }

    val windowSize = LocalWindowSize.current
    val sidebarWidth = when (windowSize) {
        WindowSize.Compact  -> 56.dp
        WindowSize.Medium   -> 180.dp
        WindowSize.Expanded -> 200.dp
    }
    val compact = windowSize == WindowSize.Compact

    Column(
        modifier = modifier
            .width(sidebarWidth)
            .fillMaxHeight()
            .background(theme.sidebarBg),
        horizontalAlignment = Alignment.Start,
    ) {
        if (compact) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("T", color = theme.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Text(
                text = "TABYPROTO",
                color = theme.accent,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 18.dp, top = 24.dp, bottom = 16.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(theme.sidebarText.copy(alpha = 0.12f)))
        Spacer(Modifier.height(6.dp))

        NavItem(Icons.Rounded.Home, "Home", selectedScreen == Screen.Home, compact = compact) { onNavigate(Screen.Home) }
        NavItem(Icons.Rounded.Settings, "Settings", selectedScreen is Screen.Settings, compact = compact) { onNavigate(Screen.Settings()) }

        Spacer(Modifier.weight(1f))

        if (!compact) {
            val active = devices.firstOrNull { it.id == activeDeviceId }
            val online = active?.online == true
            val blockBorder = theme.sidebarText.copy(alpha = 0.20f)

            // Device block
            Column(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .fillMaxWidth()
                    .clip(AppItemShape)
                    .border(1.dp, blockBorder, AppItemShape)
                    .clickable { onNavigate(Screen.Settings(SettingsCategory.Device)) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.DeviceHub, contentDescription = null, tint = theme.sidebarText.copy(alpha = 0.4f), modifier = Modifier.size(15.dp))
                    Text("DEVICE", color = theme.sidebarText.copy(alpha = 0.4f), fontSize = 11.sp, letterSpacing = 1.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (online) theme.onlineGreen else theme.sidebarText.copy(alpha = 0.3f)))
                    Text(
                        text = when (active?.transport) {
                            TabyTransport.USB -> "USB"; TabyTransport.WIFI -> "WiFi"; TabyTransport.BLUETOOTH -> "BT"; null -> "—"
                        },
                        fontSize = 13.sp,
                        color = if (online) theme.onlineGreen else theme.sidebarText.copy(alpha = 0.4f),
                    )
                }
                if (videoVisible) {
                    VideoPlayerSurface(
                        playerState = playerState,
                        modifier = Modifier.fillMaxWidth().height(72.dp).clip(AppItemShape),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text(
                        text = lastSent?.displayName ?: if (active != null) "connected" else "no device",
                        fontSize = 12.sp,
                        color = theme.sidebarText.copy(alpha = 0.3f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (online) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Brightness6,
                            contentDescription = null,
                            tint = theme.sidebarText.copy(alpha = 0.4f),
                            modifier = Modifier.size(13.dp),
                        )
                        Slider(
                            value = (brightness ?: 100).toFloat() / 100f,
                            onValueChange = { onBrightnessChange((it * 100).roundToInt().coerceIn(0, 100)) },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = theme.accent,
                                activeTrackColor = theme.accent,
                                inactiveTrackColor = theme.sidebarText.copy(alpha = 0.2f),
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            MusicBlockAnimated(musicState = musicState, onControl = onMusicControl)
            if (musicState is MusicState.Active) Spacer(Modifier.height(6.dp))

            // Voice block
            val voiceActive = listeningState != ListeningState.Idle
            Column(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .fillMaxWidth()
                    .clip(AppItemShape)
                    .border(1.dp, if (voiceActive) theme.accent else blockBorder, AppItemShape)
                    .clickable(onClick = onVoiceClick)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.Mic, contentDescription = null,
                        tint = if (voiceActive) theme.accent else theme.sidebarText.copy(alpha = 0.4f),
                        modifier = Modifier.size(15.dp))
                    Text("VOICE", color = theme.sidebarText.copy(alpha = 0.4f), fontSize = 11.sp, letterSpacing = 1.sp)
                }
                Text(
                    text = when (listeningState) {
                        ListeningState.Idle -> "Ready"
                        ListeningState.WakeWordDetected -> "Wake word"
                        ListeningState.Listening -> "Listening…"
                        ListeningState.Responding -> "Responding"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (voiceActive) theme.accent else theme.sidebarText.copy(alpha = 0.5f),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, compact: Boolean = false, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    val bgColor = if (selected) theme.accent.copy(alpha = 0.18f) else theme.sidebarBg
    val contentColor = if (selected) theme.accent else theme.sidebarText.copy(alpha = 0.65f)

    Row(
        modifier = Modifier
            .padding(horizontal = if (compact) 4.dp else 10.dp)
            .fillMaxWidth()
            .clip(AppItemShape)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 0.dp else 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (compact) Arrangement.Center else Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(24.dp))
        if (!compact) {
            Text(label, fontSize = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = contentColor)
        }
    }
}
