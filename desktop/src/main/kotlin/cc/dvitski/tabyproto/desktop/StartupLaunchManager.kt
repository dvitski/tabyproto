package cc.dvitski.tabyproto.desktop

import java.io.File
import java.nio.file.Paths
import java.util.logging.Logger

object StartupLaunchManager {
    private val log = Logger.getLogger("taby-startup-launch")

    private const val RUN_KEY_PATH = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val VALUE_NAME = "TabyProto-Desktop"
    private const val EXE_NAME = "TabyProto-Desktop.exe"

    /** Resolves the installed app's native launcher exe from java.home (<install>/runtime),
     *  matching the jpackage Windows app-image layout. Returns null when not running from
     *  an installed app (e.g. ./gradlew :desktop:run), where the feature is unavailable. */
    fun installedExePath(): File? {
        val installDir = Paths.get(System.getProperty("java.home")).parent ?: return null
        val exe = installDir.resolve(EXE_NAME).toFile()
        return if (exe.isFile) exe else null
    }

    internal fun buildRegCommand(exePath: String, enabled: Boolean, minimized: Boolean): List<String> =
        if (enabled) {
            val command = if (minimized) "\"$exePath\" --minimized" else "\"$exePath\""
            listOf("reg", "add", RUN_KEY_PATH, "/v", VALUE_NAME, "/t", "REG_SZ", "/d", command, "/f")
        } else {
            listOf("reg", "delete", RUN_KEY_PATH, "/v", VALUE_NAME, "/f")
        }

    /** Best-effort: failures are logged, never surfaced to the UI. */
    fun sync(enabled: Boolean, minimized: Boolean, exePath: File) {
        val command = buildRegCommand(exePath.absolutePath, enabled, minimized)
        runCatching {
            ProcessBuilder(command).redirectErrorStream(true).start().waitFor()
        }.onFailure { log.warning("Startup registration sync failed: ${it.message}") }
    }
}
