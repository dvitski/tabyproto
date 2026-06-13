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
import androidx.compose.material.Text
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
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    Column(
        modifier = modifier.fillMaxSize().background(theme.background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DevicePanel(devices, activeDeviceId, lastSent, Modifier.fillMaxWidth().weight(1.4f))
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MusicPanel(Modifier.weight(1f).fillMaxHeight())
            MobilePanel(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun PanelHeader(label: String) {
    val theme = LocalAppTheme.current
    Box(Modifier.fillMaxWidth().height(3.dp).background(theme.accent))
    Text(
        text = label,
        color = theme.accent,
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun DevicePanel(
    devices: List<TabyDevice>,
    activeDeviceId: String?,
    lastSent: Animation?,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val active = devices.firstOrNull { it.id == activeDeviceId }
    val online = active?.online == true

    Column(modifier = modifier.background(theme.surface).border(2.dp, theme.border)) {
        PanelHeader("DEVICE")
        Column(
            modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (online) theme.onlineGreen else theme.textSecondary))
                Text(
                    text = if (active != null && online) "Connected · ${when (active.transport) {
                        TabyTransport.USB -> "USB"; TabyTransport.WIFI -> "WiFi"; TabyTransport.BLUETOOTH -> "BT"
                    }}" else "Disconnected",
                    color = theme.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("NOW PLAYING", color = theme.textSecondary, fontSize = 8.sp, letterSpacing = 1.sp)
                Text(lastSent?.id ?: "—", color = theme.textPrimary, fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun MusicPanel(modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    Column(modifier = modifier.background(theme.surface).border(2.dp, theme.border)) {
        PanelHeader("MUSIC")
        Text("Not configured", color = theme.textSecondary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 14.dp))
    }
}

@Composable
private fun MobilePanel(modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    Column(modifier = modifier.background(theme.surface).border(2.dp, theme.border)) {
        PanelHeader("MOBILE")
        Text("No device paired", color = theme.textSecondary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 14.dp))
    }
}
