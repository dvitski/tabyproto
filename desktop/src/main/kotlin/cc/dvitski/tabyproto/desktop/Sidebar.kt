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
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.dvitski.tabyproto.Animation
import cc.dvitski.tabyproto.TabyDevice
import cc.dvitski.tabyproto.TabyTransport

@Composable
fun Sidebar(
    selectedScreen: Screen,
    devices: List<TabyDevice>,
    activeDeviceId: String?,
    lastSent: Animation?,
    listeningState: ListeningState,
    onNavigate: (Screen) -> Unit,
    onVoiceClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current

    Column(
        modifier = modifier
            .width(160.dp)
            .fillMaxHeight()
            .background(theme.sidebarBg),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "TABYPROTO",
            color = theme.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 14.dp, top = 16.dp, bottom = 12.dp),
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(theme.sidebarText.copy(alpha = 0.12f)))
        Spacer(Modifier.height(4.dp))

        NavItem("⬡", "Home", selectedScreen == Screen.Home) { onNavigate(Screen.Home) }
        NavItem("◈", "Settings", selectedScreen is Screen.Settings) { onNavigate(Screen.Settings()) }

        Spacer(Modifier.weight(1f))

        val active = devices.firstOrNull { it.id == activeDeviceId }
        val online = active?.online == true
        val blockBorder = theme.sidebarText.copy(alpha = 0.25f)

        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .border(2.dp, blockBorder)
                .clickable { onNavigate(Screen.Settings(SettingsCategory.Device)) }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text("DEVICE", color = theme.sidebarText.copy(alpha = 0.4f), fontSize = 7.sp, letterSpacing = 1.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(if (online) theme.onlineGreen else theme.sidebarText.copy(alpha = 0.3f)))
                Text(
                    text = when (active?.transport) {
                        TabyTransport.USB -> "USB"; TabyTransport.WIFI -> "WiFi"; TabyTransport.BLUETOOTH -> "BT"; null -> "—"
                    },
                    fontSize = 9.sp,
                    color = if (online) theme.onlineGreen else theme.sidebarText.copy(alpha = 0.4f),
                )
            }
            Text(
                text = lastSent?.id ?: if (active != null) "connected" else "no device",
                fontSize = 8.sp,
                color = theme.sidebarText.copy(alpha = 0.3f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(4.dp))

        val voiceActive = listeningState != ListeningState.Idle
        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .border(2.dp, if (voiceActive) theme.accent else blockBorder)
                .clickable(onClick = onVoiceClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text("VOICE", color = theme.sidebarText.copy(alpha = 0.4f), fontSize = 7.sp, letterSpacing = 1.sp)
            Text(
                text = when (listeningState) {
                    ListeningState.Idle -> "READY"
                    ListeningState.WakeWordDetected -> "WAKE WORD"
                    ListeningState.Listening -> "LISTENING"
                    ListeningState.Responding -> "RESPONDING"
                },
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (voiceActive) theme.accent else theme.sidebarText.copy(alpha = 0.5f),
                letterSpacing = 0.5.sp,
            )
        }

        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun NavItem(glyph: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) theme.accent else theme.sidebarBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val textColor = if (selected) theme.sidebarBg else theme.sidebarText.copy(alpha = 0.7f)
        Text(glyph, fontSize = 12.sp, color = textColor)
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor)
    }
}
