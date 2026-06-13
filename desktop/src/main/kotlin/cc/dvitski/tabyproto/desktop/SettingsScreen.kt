package cc.dvitski.tabyproto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.dvitski.tabyproto.Animation
import cc.dvitski.tabyproto.TabyDevice

private val SettingsBg = Color(0xFF0D0D1A)
private val CategoryBg = Color(0xFF090914)
private val AccentPurple = Color(0xFF7C3AED)
private val LightPurple = Color(0xFFA78BFA)

private val SettingsCategory.label: String
    get() = when (this) {
        SettingsCategory.PlayAnimations -> "Play animations"
        SettingsCategory.Music -> "Music"
        SettingsCategory.Voice -> "Voice"
        SettingsCategory.Device -> "Device"
    }

@Composable
fun SettingsScreen(
    selectedCategory: SettingsCategory,
    onCategorySelect: (SettingsCategory) -> Unit,
    animations: List<Animation>,
    query: String,
    onQueryChange: (String) -> Unit,
    thumbnailCache: ThumbnailCache,
    sendingAnimation: Animation?,
    onSend: (Animation) -> Unit,
    devices: List<TabyDevice>,
    activeDeviceId: String?,
    onSelectDevice: (String) -> Unit,
    onAddHost: (String) -> Unit,
    onRemoveHost: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxSize().background(SettingsBg)) {
        Column(
            modifier = Modifier
                .width(120.dp)
                .fillMaxHeight()
                .background(CategoryBg)
                .padding(top = 16.dp),
        ) {
            Text(
                text = "SETTINGS",
                color = Color(0xFF444444),
                fontSize = 7.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp),
            )
            SettingsCategory.entries.forEach { category ->
                CategoryItem(
                    label = category.label,
                    selected = category == selectedCategory,
                    onClick = { onCategorySelect(category) },
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
            when (selectedCategory) {
                SettingsCategory.PlayAnimations -> PlayAnimationsDetail(
                    animations = animations,
                    query = query,
                    onQueryChange = onQueryChange,
                    thumbnailCache = thumbnailCache,
                    sendingAnimation = sendingAnimation,
                    onSend = onSend,
                )
                SettingsCategory.Music -> PlaceholderDetail(
                    title = "Music",
                    body = "Music integration coming soon.",
                )
                SettingsCategory.Voice -> PlaceholderDetail(
                    title = "Voice",
                    body = "Wake word and microphone settings coming soon.",
                )
                SettingsCategory.Device -> DeviceDetail(
                    devices = devices,
                    activeDeviceId = activeDeviceId,
                    onSelect = onSelectDevice,
                    onAddHost = onAddHost,
                    onRemoveHost = onRemoveHost,
                )
            }
        }
    }
}

@Composable
private fun CategoryItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .drawBehind {
                if (selected) {
                    drawRect(color = AccentPurple.copy(alpha = 0.1f))
                    drawRect(
                        color = AccentPurple,
                        topLeft = Offset.Zero,
                        size = Size(2.dp.toPx(), size.height),
                    )
                }
            }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            color = if (selected) LightPurple else Color(0xFF888888),
        )
    }
}

@Composable
private fun PlayAnimationsDetail(
    animations: List<Animation>,
    query: String,
    onQueryChange: (String) -> Unit,
    thumbnailCache: ThumbnailCache,
    sendingAnimation: Animation?,
    onSend: (Animation) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Play animations", color = Color(0xFFE2E8F0), fontSize = 13.sp)
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Filter animations") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        AnimationGrid(
            animations = animations,
            thumbnailCache = thumbnailCache,
            sendingAnimation = sendingAnimation,
            onSend = onSend,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

@Composable
private fun PlaceholderDetail(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Color(0xFFE2E8F0), fontSize = 13.sp)
        Text(body, color = Color(0xFF555555), fontSize = 10.sp)
    }
}

@Composable
private fun DeviceDetail(
    devices: List<TabyDevice>,
    activeDeviceId: String?,
    onSelect: (String) -> Unit,
    onAddHost: (String) -> Unit,
    onRemoveHost: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Device", color = Color(0xFFE2E8F0), fontSize = 13.sp)
        DeviceSelector(
            devices = devices,
            activeDeviceId = activeDeviceId,
            onSelect = onSelect,
            onAddHost = onAddHost,
            onRemoveHost = onRemoveHost,
        )
    }
}
