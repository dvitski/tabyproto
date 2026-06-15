package cc.dvitski.tabyproto.desktop

import java.util.prefs.Preferences

class GeneralStore {
    private val prefs = Preferences.userNodeForPackage(GeneralStore::class.java)

    fun loadMinimizeToTray(): Boolean = prefs.getBoolean(KEY_MINIMIZE_TO_TRAY, true)

    fun saveMinimizeToTray(enabled: Boolean) {
        prefs.putBoolean(KEY_MINIMIZE_TO_TRAY, enabled)
    }

    private companion object {
        const val KEY_MINIMIZE_TO_TRAY = "minimizeToTray"
    }
}
