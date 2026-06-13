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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.dvitski.tabyproto.Animation
import cc.dvitski.tabyproto.TabyDevice
import cc.dvitski.tabyproto.TabyTransport

private val SidebarBg = Color(0xFF090914)
private val AccentPurple = Color(0xFF7C3AED)
private val LightPurple = Color(0xFFA78BFA)
private val SidebarOnlineGreen = Color(0xFF22C55E)
private val OfflineGrey = Color(0xFF6C7086)

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
    Column(
        modifier = modifier
            .width(72.dp)
            .fillMaxHeight()
            .background(SidebarBg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(10.dp))
        Text("TABY", color = LightPurple, fontSize = 10.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))

        NavItem(
            icon = "🏠",
            label = "Home",
            selected = selectedScreen == Screen.Home,
            onClick = { onNavigate(Screen.Home) },
        )
        NavItem(
            icon = "⚙",
            label = "Settings",
            selected = selectedScreen is Screen.Settings,
            onClick = { onNavigate(Screen.Settings()) },
        )

        Spacer(Modifier.weight(1f))

        DevicePill(
            devices = devices,
            activeDeviceId = activeDeviceId,
            lastSent = lastSent,
            onClick = { onNavigate(Screen.Settings(SettingsCategory.Device)) },
        )
        Spacer(Modifier.height(4.dp))
        VoiceOrb(listeningState = listeningState, onClick = onVoiceClick)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun NavItem(
    icon: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .drawBehind {
                if (selected) {
                    drawRect(color = AccentPurple.copy(alpha = 0.13f))
                    drawRect(
                        color = AccentPurple,
                        topLeft = Offset.Zero,
                        size = Size(2.dp.toPx(), size.height),
                    )
                }
            }
            .padding(vertical = 5.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(icon, fontSize = 14.sp)
            Text(
                text = label,
                fontSize = 7.sp,
                color = if (selected) LightPurple else Color(0xFF888888),
            )
        }
    }
}

@Composable
private fun DevicePill(
    devices: List<TabyDevice>,
    activeDeviceId: String?,
    lastSent: Animation?,
    onClick: () -> Unit,
) {
    val active = devices.firstOrNull { it.id == activeDeviceId }
    val online = active?.online == true

    Column(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (online) Color(0xFF0E1A10) else Color(0xFF12121A))
            .border(1.dp, if (online) SidebarOnlineGreen.copy(alpha = 0.27f) else OfflineGrey.copy(alpha = 0.27f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (online) SidebarOnlineGreen else OfflineGrey),
            )
            Text(
                text = when (active?.transport) {
                    TabyTransport.USB -> "USB"
                    TabyTransport.WIFI -> "WiFi"
                    TabyTransport.BLUETOOTH -> "BT"
                    null -> "—"
                },
                fontSize = 7.sp,
                color = if (online) Color(0xFF4ADE80) else OfflineGrey,
            )
        }
        Text(
            text = lastSent?.id ?: if (active != null) "connected" else "No device",
            fontSize = 6.sp,
            color = if (online) Color(0xFF3A6040) else OfflineGrey,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun VoiceOrb(
    listeningState: ListeningState,
    onClick: () -> Unit,
) {
    val active = listeningState != ListeningState.Idle

    Column(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF12122A))
            .border(
                width = 1.dp,
                color = AccentPurple.copy(alpha = if (active) 0.8f else 0.33f),
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (active) Color(0xFF6D28D9) else Color(0xFF4C1D95)),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(3.dp, 6.dp, 4.dp, 5.dp, 3.dp).forEach { h ->
                Box(
                    Modifier
                        .width(1.dp)
                        .height(h)
                        .background(LightPurple.copy(alpha = if (active) 1f else 0.5f)),
                )
            }
        }
        Text(
            text = if (active) "ACTIVE" else "VOICE ↑",
            fontSize = 6.sp,
            color = if (active) LightPurple else AccentPurple,
            letterSpacing = 0.5.sp,
        )
    }
}
