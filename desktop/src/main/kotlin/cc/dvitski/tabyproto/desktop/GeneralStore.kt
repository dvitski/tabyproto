package cc.dvitski.tabyproto.desktop

import java.util.prefs.Preferences

class GeneralStore {
    private val prefs = Preferences.userNodeForPackage(GeneralStore::class.java)

    fun loadMinimizeToTray(): Boolean = prefs.getBoolean(KEY_MINIMIZE_TO_TRAY, true)

    fun saveMinimizeToTray(enabled: Boolean) {
        prefs.putBoolean(KEY_MINIMIZE_TO_TRAY, enabled)
    }

    fun loadLaunchAtStartup(): Boolean = prefs.getBoolean(KEY_LAUNCH_AT_STARTUP, false)

    fun saveLaunchAtStartup(enabled: Boolean) {
        prefs.putBoolean(KEY_LAUNCH_AT_STARTUP, enabled)
    }

    fun loadStartMinimizedOnStartup(): Boolean = prefs.getBoolean(KEY_START_MINIMIZED, false)

    fun saveStartMinimizedOnStartup(enabled: Boolean) {
        prefs.putBoolean(KEY_START_MINIMIZED, enabled)
    }

    private companion object {
        const val KEY_MINIMIZE_TO_TRAY = "minimizeToTray"
        const val KEY_LAUNCH_AT_STARTUP = "launchAtStartupEnabled"
        const val KEY_START_MINIMIZED = "startMinimizedOnStartup"
    }
}
