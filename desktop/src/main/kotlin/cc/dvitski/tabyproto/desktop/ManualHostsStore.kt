package cc.dvitski.tabyproto.desktop

import java.util.prefs.Preferences

/** Persists the user's manually added WiFi hosts across app restarts. */
class ManualHostsStore {
    private val prefs = Preferences.userNodeForPackage(ManualHostsStore::class.java)

    fun load(): List<String> =
        prefs.get(KEY, "").split(SEPARATOR).filter { it.isNotBlank() }

    fun save(hosts: List<String>) {
        prefs.put(KEY, hosts.joinToString(SEPARATOR))
    }

    private companion object {
        const val KEY = "manualHosts"
        const val SEPARATOR = ","
    }
}
