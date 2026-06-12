package cc.dvitski.tabyproto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import cc.dvitski.tabyproto.DeviceSource
import cc.dvitski.tabyproto.TabyDevice
import cc.dvitski.tabyproto.TabyTransport

private val OnlineGreen = Color(0xFF00C853)
private val OfflineGrey = Color(0xFF6C7086)
private val UnknownGrey = Color(0xFF9399B2)

/**
 * Top-bar device picker: shows the active device with a status dot; the
 * dropdown lists every known device, lets the user switch targets, remove
 * manual WiFi hosts, and add a new host by name/IP.
 */
@Composable
fun DeviceSelector(
    devices: List<TabyDevice>,
    activeDeviceId: String?,
    onSelect: (String) -> Unit,
    onAddHost: (String) -> Unit,
    onRemoveHost: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    val active = devices.firstOrNull { it.id == activeDeviceId }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            StatusDot(
                color = when {
                    active == null -> UnknownGrey
                    active.online -> OnlineGreen
                    else -> OfflineGrey
                },
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = active?.label ?: "No device",
                color = Color.White,
                fontSize = 13.sp,
            )
            if (active != null) {
                Spacer(Modifier.width(6.dp))
                TransportTag(active.transport)
            }
            Spacer(Modifier.width(4.dp))
            Text("▾", color = Color.White, fontSize = 11.sp)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (devices.isEmpty()) {
                DropdownMenuItem(onClick = {}, enabled = false) {
                    Text("No devices found", fontSize = 13.sp)
                }
            }
            devices.forEach { device ->
                DropdownMenuItem(onClick = {
                    onSelect(device.id)
                    expanded = false
                }) {
                    StatusDot(color = if (device.online) OnlineGreen else OfflineGrey)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = device.label,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f).widthIn(min = 120.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    TransportTag(device.transport, dark = true)
                    val src = device.source
                    if (src is DeviceSource.Wifi && src.manual) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "✕",
                            fontSize = 12.sp,
                            color = OfflineGrey,
                            modifier = Modifier.clickable { onRemoveHost(src.host) },
                        )
                    }
                }
            }
            DropdownMenuItem(onClick = {
                expanded = false
                showAddDialog = true
            }) {
                Text("Add device…", fontSize = 13.sp)
            }
        }
    }

    if (showAddDialog) {
        AddDeviceDialog(
            onAdd = { host ->
                onAddHost(host)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun TransportTag(transport: TabyTransport, dark: Boolean = false) {
    Text(
        text = when (transport) {
            TabyTransport.USB -> "USB"
            TabyTransport.WIFI -> "WiFi"
            TabyTransport.BLUETOOTH -> "BT"
        },
        color = if (dark) OfflineGrey else UnknownGrey,
        fontSize = 10.sp,
    )
}

@Composable
private fun AddDeviceDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var host by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(8.dp)) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Text("Add Taby by hostname or IP", fontSize = 14.sp)
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("e.g. taby.local or 192.168.1.50") },
                    singleLine = true,
                )
                Spacer(Modifier.size(12.dp))
                Row(modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onAdd(host) },
                        enabled = host.isNotBlank(),
                    ) { Text("Add") }
                }
            }
        }
    }
}
