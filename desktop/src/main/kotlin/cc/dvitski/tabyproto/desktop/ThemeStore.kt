package cc.dvitski.tabyproto.desktop

import java.util.prefs.Preferences

class ThemeStore {
    private val prefs = Preferences.userNodeForPackage(ThemeStore::class.java)

    fun load(): Pair<Boolean, ColorPalette> {
        val isDark = prefs.getBoolean(KEY_DARK, true)
        val paletteName = prefs.get(KEY_PALETTE, ColorPalette.OCEAN.name)
        val palette = ColorPalette.entries.firstOrNull { it.name == paletteName } ?: ColorPalette.OCEAN
        return Pair(isDark, palette)
    }

    fun save(isDark: Boolean, palette: ColorPalette) {
        prefs.putBoolean(KEY_DARK, isDark)
        prefs.put(KEY_PALETTE, palette.name)
    }

    private companion object {
        const val KEY_DARK = "isDark"
        const val KEY_PALETTE = "palette"
    }
}
