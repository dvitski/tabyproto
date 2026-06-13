package cc.dvitski.tabyproto.desktop

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ColorPalette(
    val displayName: String,
    val lightAccent: Color,
    val darkAccent: Color,
) {
    EMBER("Ember",       Color(0xFFB04040), Color(0xFFE89090)),
    TANGERINE("Tangerine", Color(0xFFB06830), Color(0xFFE8B080)),
    SUNFLOWER("Sunflower", Color(0xFF907820), Color(0xFFD8C870)),
    FERN("Fern",         Color(0xFF387858), Color(0xFF80C8A0)),
    TEAL("Teal",         Color(0xFF286878), Color(0xFF70C8D8)),
    OCEAN("Ocean",       Color(0xFF3858A0), Color(0xFF90B8E8)),
    BERRY("Berry",       Color(0xFF884888), Color(0xFFD090D0)),
}

val AppCardShape   = RoundedCornerShape(12.dp)
val AppItemShape   = RoundedCornerShape(8.dp)
val AppButtonShape = RoundedCornerShape(8.dp)

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
            background = Color(0xFF28231E), surface = Color(0xFF332C28), surface2 = Color(0xFF3E3730),
            border = Color(0xFF5A5248),
            textPrimary = Color(0xFFEDE8E0), textSecondary = Color(0xFF7A7268),
            sidebarBg = Color(0xFF1E1A16), sidebarText = Color(0xFFEDE8E0),
            accent = accent, onlineGreen = Color(0xFF80C8A0),
        )
    } else {
        AppTheme(
            isDark = false, palette = palette,
            background = Color(0xFFF8F4EF), surface = Color(0xFFEDE8E0), surface2 = Color(0xFFE2DCD4),
            border = Color(0xFFC8BEB4),
            textPrimary = Color(0xFF2C2420), textSecondary = Color(0xFF8C8078),
            sidebarBg = Color(0xFF3A3028), sidebarText = Color(0xFFF8F4EF),
            accent = accent, onlineGreen = Color(0xFF387858),
        )
    }
}

val LocalAppTheme = compositionLocalOf { buildTheme(isDark = true, palette = ColorPalette.OCEAN) }
