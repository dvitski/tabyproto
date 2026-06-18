package cc.dvitski.tabyproto.desktop

import java.util.prefs.Preferences

data class DetectionSettings(val musicEnabled: Boolean = true, val gameEnabled: Boolean = true)

class DetectionSettingsStore {
    private val prefs = Preferences.userNodeForPackage(DetectionSettingsStore::class.java)

    fun load(): DetectionSettings = DetectionSettings(
        musicEnabled = prefs.getBoolean(KEY_MUSIC, true),
        gameEnabled  = prefs.getBoolean(KEY_GAME, true),
    )

    fun save(settings: DetectionSettings) {
        prefs.putBoolean(KEY_MUSIC, settings.musicEnabled)
        prefs.putBoolean(KEY_GAME, settings.gameEnabled)
    }

    private companion object {
        const val KEY_MUSIC = "musicDetectionEnabled"
        const val KEY_GAME = "gameDetectionEnabled"
    }
}
