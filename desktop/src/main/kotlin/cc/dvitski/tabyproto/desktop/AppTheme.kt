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
            background = Color(0xFF1E2124), surface = Color(0xFF282C30), surface2 = Color(0xFF32373C),
            border = Color(0xFF484E54),
            textPrimary = Color(0xFFE4E8EC), textSecondary = Color(0xFF78828C),
            sidebarBg = Color(0xFF14171A), sidebarText = Color(0xFFE4E8EC),
            accent = accent, onlineGreen = Color(0xFF80C8A0),
        )
    } else {
        AppTheme(
            isDark = false, palette = palette,
            background = Color(0xFFF4F5F7), surface = Color(0xFFE8EAED), surface2 = Color(0xFFD8DCE0),
            border = Color(0xFFBCC2C8),
            textPrimary = Color(0xFF202428), textSecondary = Color(0xFF868E96),
            sidebarBg = Color(0xFF2C3038), sidebarText = Color(0xFFF4F5F7),
            accent = accent, onlineGreen = Color(0xFF387858),
        )
    }
}

val LocalAppTheme = compositionLocalOf { buildTheme(isDark = true, palette = ColorPalette.OCEAN) }
