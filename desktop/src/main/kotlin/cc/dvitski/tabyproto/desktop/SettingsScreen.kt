package cc.dvitski.tabyproto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.dvitski.tabyproto.Animation
import cc.dvitski.tabyproto.TabyDevice

private val SettingsCategory.label: String
    get() = when (this) {
        SettingsCategory.PlayAnimations -> "Play Animations"
        SettingsCategory.Music -> "Music"
        SettingsCategory.Voice -> "Voice"
        SettingsCategory.Device -> "Device"
        SettingsCategory.Appearance -> "Appearance"
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
    isDark: Boolean,
    currentPalette: ColorPalette,
    onSetTheme: (Boolean, ColorPalette) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    Row(modifier = modifier.fillMaxSize().background(theme.background)) {
        Column(modifier = Modifier.width(160.dp).fillMaxHeight().background(theme.surface)) {
            Text(
                text = "SETTINGS",
                color = theme.textSecondary,
                fontSize = 7.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            )
            SettingsCategory.entries.forEach { category ->
                CategoryItem(category.label, category == selectedCategory) { onCategorySelect(category) }
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
            when (selectedCategory) {
                SettingsCategory.PlayAnimations -> PlayAnimationsDetail(animations, query, onQueryChange, thumbnailCache, sendingAnimation, onSend)
                SettingsCategory.Music -> PlaceholderDetail("Music", "Music integration coming soon.")
                SettingsCategory.Voice -> PlaceholderDetail("Voice", "Wake word and microphone settings coming soon.")
                SettingsCategory.Device -> DeviceDetail(devices, activeDeviceId, onSelectDevice, onAddHost, onRemoveHost)
                SettingsCategory.Appearance -> AppearanceDetail(isDark, currentPalette, onSetTheme)
            }
        }
    }
}

@Composable
private fun CategoryItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) theme.accent else theme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) theme.background else theme.textSecondary,
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
    val theme = LocalAppTheme.current
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Play Animations", color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier.fillMaxWidth().border(2.dp, theme.border).background(theme.surface2)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = theme.textPrimary, fontSize = 11.sp),
                cursorBrush = SolidColor(theme.accent),
                modifier = Modifier.fillMaxWidth(),
            )
            if (query.isEmpty()) Text("Filter animations…", color = theme.textSecondary, fontSize = 11.sp)
        }
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
    val theme = LocalAppTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(body, color = theme.textSecondary, fontSize = 11.sp)
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
    val theme = LocalAppTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Device", color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        DeviceSelector(devices, activeDeviceId, onSelect, onAddHost, onRemoveHost)
    }
}

@Composable
private fun AppearanceDetail(isDark: Boolean, currentPalette: ColorPalette, onSetTheme: (Boolean, ColorPalette) -> Unit) {
    val theme = LocalAppTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Appearance", color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("MODE", color = theme.textSecondary, fontSize = 8.sp, letterSpacing = 1.sp)
            Row {
                ModeButton("LIGHT", !isDark, Modifier.weight(1f)) { onSetTheme(false, currentPalette) }
                ModeButton("DARK", isDark, Modifier.weight(1f)) { onSetTheme(true, currentPalette) }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("PALETTE", color = theme.textSecondary, fontSize = 8.sp, letterSpacing = 1.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorPalette.entries.forEach { palette ->
                    PaletteSwatch(palette, isDark, palette == currentPalette) { onSetTheme(isDark, palette) }
                }
            }
        }
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    Box(
        modifier = modifier
            .border(2.dp, theme.border)
            .background(if (selected) theme.accent else theme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            color = if (selected) theme.background else theme.textSecondary)
    }
}

@Composable
private fun PaletteSwatch(palette: ColorPalette, isDark: Boolean, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    val color = if (isDark) palette.darkAccent else palette.lightAccent
    Box(
        modifier = Modifier
            .size(40.dp)
            .border(if (selected) 3.dp else 2.dp, if (selected) theme.border else color)
            .background(color)
            .clickable(onClick = onClick),
    )
}
