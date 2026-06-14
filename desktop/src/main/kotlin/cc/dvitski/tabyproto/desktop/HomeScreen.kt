package cc.dvitski.tabyproto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhoneAndroid
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.dvitski.tabyproto.Animation
import cc.dvitski.tabyproto.TabyDevice
import cc.dvitski.tabyproto.TabyTransport

@Composable
fun HomeScreen(
    devices: List<TabyDevice>,
    activeDeviceId: String?,
    lastSent: Animation?,
    brightness: Int?,
    onBrightnessChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    Column(
        modifier = modifier.fillMaxSize().background(theme.background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DevicePanel(devices, activeDeviceId, lastSent, brightness, onBrightnessChange, Modifier.fillMaxWidth().weight(1.4f))
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MusicPanel(Modifier.weight(1f).fillMaxHeight())
            MobilePanel(Modifier.weight(1f).fillMaxHeight())
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
private fun MusicPanel(modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    Column(modifier = modifier.clip(AppCardShape).background(theme.surface).border(1.dp, theme.border, AppCardShape)) {
        PanelHeader("MUSIC") {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = theme.accent, modifier = Modifier.size(18.dp))
        }
        Text("Not configured", color = theme.textSecondary, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun MobilePanel(modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    Column(modifier = modifier.clip(AppCardShape).background(theme.surface).border(1.dp, theme.border, AppCardShape)) {
        PanelHeader("MOBILE") {
            Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, tint = theme.accent, modifier = Modifier.size(18.dp))
        }
        Text("No device paired", color = theme.textSecondary, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 16.dp))
    }
}
