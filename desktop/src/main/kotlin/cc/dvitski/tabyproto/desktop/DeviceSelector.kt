package cc.dvitski.tabyproto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import cc.dvitski.tabyproto.DeviceSource
import cc.dvitski.tabyproto.TabyDevice
import cc.dvitski.tabyproto.TabyTransport

@Composable
fun DeviceSelector(
    devices: List<TabyDevice>,
    activeDeviceId: String?,
    onSelect: (String) -> Unit,
    onAddHost: (String) -> Unit,
    onRemoveHost: (String) -> Unit,
) {
    val theme = LocalAppTheme.current
    var expanded by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    val active = devices.firstOrNull { it.id == activeDeviceId }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .border(2.dp, theme.border)
                .background(theme.surface)
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            StatusDot(when {
                active == null -> theme.textSecondary
                active.online -> theme.onlineGreen
                else -> theme.textSecondary
            })
            Spacer(Modifier.width(6.dp))
            Text(active?.label ?: "No device", color = theme.textPrimary, fontSize = 13.sp)
            if (active != null) {
                Spacer(Modifier.width(6.dp))
                TransportTag(active.transport)
            }
            Spacer(Modifier.width(4.dp))
            Text("▾", color = theme.textPrimary, fontSize = 11.sp)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (devices.isEmpty()) {
                DropdownMenuItem(onClick = {}, enabled = false) { Text("No devices found", fontSize = 13.sp) }
            }
            devices.forEach { device ->
                DropdownMenuItem(onClick = { onSelect(device.id); expanded = false }) {
                    StatusDot(if (device.online) theme.onlineGreen else theme.textSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text(device.label, fontSize = 13.sp, modifier = Modifier.weight(1f).widthIn(min = 120.dp))
                    Spacer(Modifier.width(8.dp))
                    TransportTag(device.transport)
                    val src = device.source
                    if (src is DeviceSource.Wifi && src.manual) {
                        Spacer(Modifier.width(8.dp))
                        Text("✕", fontSize = 12.sp, color = theme.textSecondary,
                            modifier = Modifier.clickable { onRemoveHost(src.host) })
                    }
                }
            }
            DropdownMenuItem(onClick = { expanded = false; showAddDialog = true }) {
                Text("Add device…", fontSize = 13.sp)
            }
        }
    }

    if (showAddDialog) {
        AddDeviceDialog(onAdd = { host -> onAddHost(host); showAddDialog = false }, onDismiss = { showAddDialog = false })
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}

@Composable
private fun TransportTag(transport: TabyTransport) {
    val theme = LocalAppTheme.current
    Text(
        text = when (transport) { TabyTransport.USB -> "USB"; TabyTransport.WIFI -> "WiFi"; TabyTransport.BLUETOOTH -> "BT" },
        color = theme.textSecondary,
        fontSize = 10.sp,
    )
}

@Composable
private fun AddDeviceDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    val theme = LocalAppTheme.current
    var host by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.background(theme.surface).border(2.dp, theme.border).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add Taby by hostname or IP", color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier.fillMaxWidth().border(2.dp, theme.border).background(theme.surface2)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                BasicTextField(
                    value = host, onValueChange = { host = it }, singleLine = true,
                    textStyle = TextStyle(color = theme.textPrimary, fontSize = 11.sp),
                    cursorBrush = SolidColor(theme.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (host.isEmpty()) Text("e.g. taby.local or 192.168.1.50", color = theme.textSecondary, fontSize = 11.sp)
            }
            Row(modifier = Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.border(2.dp, theme.border).background(theme.surface)
                        .clickable(onClick = onDismiss).padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text("Cancel", color = theme.textSecondary, fontSize = 11.sp) }
                Box(
                    modifier = Modifier
                        .border(2.dp, if (host.isNotBlank()) theme.border else theme.textSecondary)
                        .background(if (host.isNotBlank()) theme.accent else theme.surface)
                        .clickable(enabled = host.isNotBlank()) { onAdd(host) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("Add", color = if (host.isNotBlank()) theme.background else theme.textSecondary,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
