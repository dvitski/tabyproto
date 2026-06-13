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
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
        Column(
            modifier = Modifier.width(168.dp).fillMaxHeight().background(theme.surface).padding(vertical = 12.dp),
        ) {
            Text(
                text = "SETTINGS",
                color = theme.textSecondary,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
            SettingsCategory.entries.forEach { category ->
                CategoryItem(category.label, category == selectedCategory) { onCategorySelect(category) }
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(20.dp)) {
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
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .fillMaxWidth()
            .clip(AppItemShape)
            .background(if (selected) theme.accent.copy(alpha = 0.15f) else theme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) theme.accent else theme.textSecondary,
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
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Play Animations", color = theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppItemShape)
                .border(1.dp, theme.border, AppItemShape)
                .background(theme.surface2)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(18.dp))
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = theme.textPrimary, fontSize = 13.sp),
                    cursorBrush = SolidColor(theme.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (query.isEmpty()) Text("Filter animations…", color = theme.textSecondary, fontSize = 13.sp)
            }
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
        Text(title, color = theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(body, color = theme.textSecondary, fontSize = 13.sp)
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
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Device", color = theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        DeviceSelector(devices, activeDeviceId, onSelect, onAddHost, onRemoveHost)
    }
}

@Composable
private fun AppearanceDetail(isDark: Boolean, currentPalette: ColorPalette, onSetTheme: (Boolean, ColorPalette) -> Unit) {
    val theme = LocalAppTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Appearance", color = theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("MODE", color = theme.textSecondary, fontSize = 10.sp, letterSpacing = 1.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeButton("Light", !isDark, Modifier.weight(1f)) { onSetTheme(false, currentPalette) }
                ModeButton("Dark", isDark, Modifier.weight(1f)) { onSetTheme(true, currentPalette) }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("PALETTE", color = theme.textSecondary, fontSize = 10.sp, letterSpacing = 1.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
            .clip(AppButtonShape)
            .border(1.dp, if (selected) theme.accent else theme.border, AppButtonShape)
            .background(if (selected) theme.accent.copy(alpha = 0.12f) else theme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) theme.accent else theme.textSecondary,
        )
    }
}

@Composable
private fun PaletteSwatch(palette: ColorPalette, isDark: Boolean, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    val color = if (isDark) palette.darkAccent else palette.lightAccent
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(AppItemShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) theme.textPrimary else color.copy(alpha = 0.4f), AppItemShape)
            .background(color)
            .clickable(onClick = onClick),
    )
}
