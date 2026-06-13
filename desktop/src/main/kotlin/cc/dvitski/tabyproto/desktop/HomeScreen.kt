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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D1A))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DevicePanel(
            devices = devices,
            activeDeviceId = activeDeviceId,
            lastSent = lastSent,
            modifier = Modifier.fillMaxWidth().weight(1.4f),
        )
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MusicPanel(modifier = Modifier.weight(1f).fillMaxHeight())
            MobilePanel(modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun DevicePanel(
    devices: List<TabyDevice>,
    activeDeviceId: String?,
    lastSent: Animation?,
    modifier: Modifier = Modifier,
) {
    val active = devices.firstOrNull { it.id == activeDeviceId }
    val online = active?.online == true

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0E1A10))
            .border(1.dp, Color(0xFF22C55E).copy(alpha = 0.27f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("DEVICE", color = Color(0xFF22C55E), fontSize = 8.sp, letterSpacing = 1.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (online) Color(0xFF22C55E) else Color(0xFF6C7086)),
                )
                Text(
                    text = if (active != null && active.online) {
                        "Connected · ${when (active.transport) {
                            TabyTransport.USB -> "USB"
                            TabyTransport.WIFI -> "WiFi"
                            TabyTransport.BLUETOOTH -> "BT"
                        }}"
                    } else "Disconnected",
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.sp,
                )
            }
            Text(
                text = "Now playing: ${lastSent?.id ?: "—"}",
                color = Color(0xFF555555),
                fontSize = 9.sp,
            )
        }
        Text("🤖", fontSize = 36.sp)
    }
}

@Composable
private fun MusicPanel(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1A140A))
            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("MUSIC", color = Color(0xFFF59E0B), fontSize = 8.sp, letterSpacing = 1.sp)
        Text("Not configured", color = Color(0xFF555555), fontSize = 9.sp)
    }
}

@Composable
private fun MobilePanel(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF080E1A))
            .border(1.dp, Color(0xFF06B6D4).copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("MOBILE", color = Color(0xFF06B6D4), fontSize = 8.sp, letterSpacing = 1.sp)
            Text("No device paired", color = Color(0xFF555555), fontSize = 9.sp)
        }
        Text("📱", fontSize = 20.sp)
    }
}
