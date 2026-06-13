package cc.dvitski.tabyproto.desktop

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ColorPalette(
    val displayName: String,
    val lightAccent: Color,
    val darkAccent: Color,
) {
    EMBER("Ember", Color(0xFFC4271A), Color(0xFFFF4D3D)),
    TANGERINE("Tangerine", Color(0xFFC45A0A), Color(0xFFFF8C3A)),
    SUNFLOWER("Sunflower", Color(0xFF9A7A00), Color(0xFFF5C518)),
    FERN("Fern", Color(0xFF1A6B3A), Color(0xFF3DCC7A)),
    TEAL("Teal", Color(0xFF0A6B6B), Color(0xFF00C7C7)),
    OCEAN("Ocean", Color(0xFF1A4A9A), Color(0xFF5B9EFF)),
    BERRY("Berry", Color(0xFF9A1A6B), Color(0xFFFF5BB8)),
}

data class AppTheme(
    val isDark: Boolean,
    val palette: ColorPalette,
    val background: Color,
    val surface: Color,
    val surface2: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val sidebarBg: Color,
    val sidebarText: Color,
    val accent: Color,
    val onlineGreen: Color,
)

fun buildTheme(isDark: Boolean, palette: ColorPalette): AppTheme {
    val accent = if (isDark) palette.darkAccent else palette.lightAccent
    return if (isDark) {
        AppTheme(
            isDark = true, palette = palette,
            background = Color(0xFF1A1714), surface = Color(0xFF242017), surface2 = Color(0xFF2E2B20),
            border = Color(0xFFEDE6DC), textPrimary = Color(0xFFEDE6DC), textSecondary = Color(0xFF6E6A60),
            sidebarBg = Color(0xFF111008), sidebarText = Color(0xFFEDE6DC),
            accent = accent, onlineGreen = Color(0xFF3DCC7A),
        )
    } else {
        AppTheme(
            isDark = false, palette = palette,
            background = Color(0xFFFAF7F2), surface = Color(0xFFF0EBE3), surface2 = Color(0xFFE5DDD4),
            border = Color(0xFF1A1614), textPrimary = Color(0xFF1A1614), textSecondary = Color(0xFF7A6E68),
            sidebarBg = Color(0xFF1A1614), sidebarText = Color(0xFFFAF7F2),
            accent = accent, onlineGreen = Color(0xFF1A6B3A),
        )
    }
}

val LocalAppTheme = compositionLocalOf { buildTheme(isDark = true, palette = ColorPalette.OCEAN) }
