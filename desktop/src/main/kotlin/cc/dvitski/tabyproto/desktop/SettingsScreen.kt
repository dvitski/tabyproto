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
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        SettingsCategory.Music -> "Music"
        SettingsCategory.Voice -> "Voice"
        SettingsCategory.Device -> "Device"
        SettingsCategory.Appearance -> "Appearance"
        SettingsCategory.Idle -> "Idle"
    }

@Composable
fun SettingsScreen(
    selectedCategory: SettingsCategory,
    onCategorySelect: (SettingsCategory) -> Unit,
    animations: List<Animation>,
    query: String,
    onQueryChange: (String) -> Unit,
    typeFilter: AnimationTypeFilter,
    onTypeFilterChange: (AnimationTypeFilter) -> Unit,
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
    brightness: Int?,
    onBrightnessChange: (Int) -> Unit,
    musicState: MusicState,
    onMusicControl: (MediaControl, String?) -> Unit,
    idleSettings: IdleSettings,
    onIdleSettingsChange: (IdleSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    Row(modifier = modifier.fillMaxSize().background(theme.background)) {
        Column(
            modifier = Modifier.width(200.dp).fillMaxHeight().background(theme.surface).padding(vertical = 14.dp),
        ) {
            Text(
                text = "SETTINGS",
                color = theme.textSecondary,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            SettingsCategory.entries.forEach { category ->
                CategoryItem(category.label, category == selectedCategory) { onCategorySelect(category) }
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(20.dp)) {
            when (selectedCategory) {
                SettingsCategory.Music -> MusicDetail(musicState, onMusicControl)
                SettingsCategory.Voice -> PlaceholderDetail("Voice", "Wake word and microphone settings coming soon.")
                SettingsCategory.Device -> DeviceDetail(devices, activeDeviceId, onSelectDevice, onAddHost, onRemoveHost, brightness, onBrightnessChange, animations, query, onQueryChange, typeFilter, onTypeFilterChange, thumbnailCache, sendingAnimation, onSend)
                SettingsCategory.Appearance -> AppearanceDetail(isDark, currentPalette, onSetTheme)
                SettingsCategory.Idle -> IdleDetail(idleSettings, onIdleSettingsChange)
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
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) theme.accent else theme.textSecondary,
        )
    }
}


@Composable
private fun TypeFilterChip(
    label: String,
    icon: ImageVector?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .clip(AppButtonShape)
            .border(1.dp, if (selected) theme.accent else theme.border, AppButtonShape)
            .background(if (selected) theme.accent.copy(alpha = 0.12f) else theme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (selected) theme.accent else theme.textSecondary, modifier = Modifier.size(14.dp))
        }
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) theme.accent else theme.textSecondary,
        )
    }
}

@Composable
private fun MusicDetail(state: MusicState, onControl: (MediaControl, String?) -> Unit) {
    val theme = LocalAppTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Music", color = theme.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        when (state) {
            MusicState.Idle -> Text("No music playing", color = theme.textSecondary, fontSize = 15.sp)
            is MusicState.Active -> {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    state.sessions.forEach { session ->
                        val appId = session.appId
                        MusicSessionDetail(session = session, onControl = { c -> onControl(c, appId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicSessionDetail(session: NowPlaying, onControl: (MediaControl) -> Unit) {
    val theme = LocalAppTheme.current
    val accent = theme.accent
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val sourceName = when (session.source) { MusicSource.Spotify -> "Spotify"; MusicSource.Tidal -> "Tidal"; else -> "Music" }
        Text(sourceName, color = if (session.isPlaying) accent else theme.textSecondary,
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MusicDetailAlbumArt(uri = session.albumArtUri, accent = accent)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(session.track ?: "Unknown", color = theme.textPrimary, fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(session.artist ?: "", color = theme.textSecondary, fontSize = 15.sp,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material.IconButton(onClick = { onControl(MediaControl.Prev) }) {
                Icon(Icons.Rounded.SkipPrevious, "Prev", tint = theme.textSecondary, modifier = Modifier.size(28.dp))
            }
            androidx.compose.material.IconButton(onClick = { onControl(MediaControl.PlayPause) }) {
                Icon(if (session.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "PlayPause",
                    tint = accent, modifier = Modifier.size(32.dp))
            }
            androidx.compose.material.IconButton(onClick = { onControl(MediaControl.Next) }) {
                Icon(Icons.Rounded.SkipNext, "Next", tint = theme.textSecondary, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun MusicDetailAlbumArt(uri: String?, accent: androidx.compose.ui.graphics.Color) {
    val theme = LocalAppTheme.current
    Box(
        modifier = Modifier.size(96.dp).clip(AppItemShape).background(theme.textSecondary.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        var bitmap by remember(uri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
        LaunchedEffect(uri) {
            bitmap = null
            if (uri != null) bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { java.io.File(uri.removePrefix("file://")).inputStream().buffered().use { androidx.compose.ui.res.loadImageBitmap(it) } }.getOrNull()
            }
        }
        val bmp = bitmap
        if (bmp != null) {
            androidx.compose.foundation.Image(bmp, null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = accent.copy(alpha = 0.5f), modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
private fun PlaceholderDetail(title: String, body: String) {
    val theme = LocalAppTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = theme.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(body, color = theme.textSecondary, fontSize = 15.sp)
    }
}

@Composable
private fun DeviceDetail(
    devices: List<TabyDevice>,
    activeDeviceId: String?,
    onSelect: (String) -> Unit,
    onAddHost: (String) -> Unit,
    onRemoveHost: (String) -> Unit,
    brightness: Int?,
    onBrightnessChange: (Int) -> Unit,
    animations: List<Animation>,
    query: String,
    onQueryChange: (String) -> Unit,
    typeFilter: AnimationTypeFilter,
    onTypeFilterChange: (AnimationTypeFilter) -> Unit,
    thumbnailCache: ThumbnailCache,
    sendingAnimation: Animation?,
    onSend: (Animation) -> Unit,
) {
    val theme = LocalAppTheme.current
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Device", color = theme.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        DeviceSelector(devices, activeDeviceId, onSelect, onAddHost, onRemoveHost)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("BRIGHTNESS", color = theme.textSecondary, fontSize = 12.sp, letterSpacing = 1.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.Brightness6, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(20.dp))
                Slider(
                    value = (brightness ?: 100).toFloat() / 100f,
                    onValueChange = { onBrightnessChange((it * 100).roundToInt().coerceIn(0, 100)) },
                    enabled = brightness != null,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = theme.accent, activeTrackColor = theme.accent),
                )
                Text(
                    text = if (brightness != null) "$brightness%" else "—",
                    color = theme.textSecondary,
                    fontSize = 13.sp,
                )
            }
        }
        Text("PLAY ANIMATIONS", color = theme.textSecondary, fontSize = 12.sp, letterSpacing = 1.sp)
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
            Icon(Icons.Rounded.Search, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(22.dp))
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = theme.textPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(theme.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (query.isEmpty()) Text("Filter animations…", color = theme.textSecondary, fontSize = 15.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TypeFilterChip(label = "All",        icon = null,                       selected = typeFilter == AnimationTypeFilter.All)        { onTypeFilterChange(AnimationTypeFilter.All) }
            TypeFilterChip(label = "Once",       icon = Icons.Rounded.PlayArrow,    selected = typeFilter == AnimationTypeFilter.Once)       { onTypeFilterChange(AnimationTypeFilter.Once) }
            TypeFilterChip(label = "Loop",       icon = Icons.Rounded.Repeat,       selected = typeFilter == AnimationTypeFilter.Loop)       { onTypeFilterChange(AnimationTypeFilter.Loop) }
            TypeFilterChip(label = "Intro Loop", icon = Icons.Rounded.AllInclusive, selected = typeFilter == AnimationTypeFilter.IntroLoop)  { onTypeFilterChange(AnimationTypeFilter.IntroLoop) }
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
private fun AppearanceDetail(isDark: Boolean, currentPalette: ColorPalette, onSetTheme: (Boolean, ColorPalette) -> Unit) {
    val theme = LocalAppTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Appearance", color = theme.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("MODE", color = theme.textSecondary, fontSize = 12.sp, letterSpacing = 1.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeButton("Light", !isDark, Modifier.weight(1f)) { onSetTheme(false, currentPalette) }
                ModeButton("Dark", isDark, Modifier.weight(1f)) { onSetTheme(true, currentPalette) }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("PALETTE", color = theme.textSecondary, fontSize = 12.sp, letterSpacing = 1.sp)
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
            fontSize = 15.sp,
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
            .size(52.dp)
            .clip(AppItemShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) theme.textPrimary else color.copy(alpha = 0.4f), AppItemShape)
            .background(color)
            .clickable(onClick = onClick),
    )
}

private fun formatSeconds(secs: Int): String = when {
    secs < 60      -> "${secs}s"
    secs % 60 == 0 -> "${secs / 60}m"
    else           -> "${secs / 60}m ${secs % 60}s"
}

@Composable
private fun IdleDetail(settings: IdleSettings, onChange: (IdleSettings) -> Unit) {
    val theme = LocalAppTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Idle", color = theme.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        IdleSlider(
            label    = "ANIMATION INTERVAL",
            value    = settings.variationIntervalSec,
            range    = 30..300,
            step     = 30,
            format   = { formatSeconds(it) },
            onChange = { onChange(settings.copy(variationIntervalSec = it)) },
        )

        IdleSlider(
            label    = "RELAXED AFTER",
            value    = settings.relaxedThresholdSec,
            range    = 60..900,
            step     = 60,
            format   = { formatSeconds(it) },
            onChange = { onChange(settings.copy(relaxedThresholdSec = it)) },
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("BRIGHTNESS DIM", color = theme.textSecondary, fontSize = 12.sp, letterSpacing = 1.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeButton("On",  settings.dimEnabled,  Modifier.weight(1f)) { onChange(settings.copy(dimEnabled = true)) }
                ModeButton("Off", !settings.dimEnabled, Modifier.weight(1f)) { onChange(settings.copy(dimEnabled = false)) }
            }
        }

        if (settings.dimEnabled) {
            IdleSlider(
                label    = "DIM AFTER",
                value    = settings.dimDelayThresholdSec,
                range    = 60..900,
                step     = 60,
                format   = { formatSeconds(it) },
                onChange = { onChange(settings.copy(dimDelayThresholdSec = it)) },
            )

            IdleSlider(
                label    = "DIM FLOOR",
                value    = settings.dimFloorPercent,
                range    = 10..80,
                step     = 5,
                format   = { "$it%" },
                onChange = { onChange(settings.copy(dimFloorPercent = it)) },
            )
        }
    }
}

@Composable
private fun IdleSlider(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    format: (Int) -> String,
    onChange: (Int) -> Unit,
) {
    val theme = LocalAppTheme.current
    val steps = (range.last - range.first) / step - 1
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = theme.textSecondary, fontSize = 12.sp, letterSpacing = 1.sp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Slider(
                value    = (value - range.first).toFloat() / (range.last - range.first).toFloat(),
                onValueChange = { raw ->
                    val snapped = (range.first + ((raw * (range.last - range.first)).toInt() + step / 2) / step * step)
                        .coerceIn(range.first, range.last)
                    onChange(snapped)
                },
                steps    = steps,
                modifier = Modifier.weight(1f),
                colors   = SliderDefaults.colors(thumbColor = theme.accent, activeTrackColor = theme.accent),
            )
            Text(
                text     = format(value),
                color    = theme.textSecondary,
                fontSize = 13.sp,
            )
        }
    }
}
