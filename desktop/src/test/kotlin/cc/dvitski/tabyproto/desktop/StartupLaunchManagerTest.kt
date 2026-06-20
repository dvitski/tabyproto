package cc.dvitski.tabyproto.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class StartupLaunchManagerTest {

    private val runKeyPath = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private val exePath = "C:\\Users\\test\\AppData\\Local\\TabyProto-Desktop\\TabyProto-Desktop.exe"

    @Test
    fun `enabled without minimized builds add command with bare exe path`() {
        val result = StartupLaunchManager.buildRegCommand(exePath, enabled = true, minimized = false)
        assertEquals(
            listOf("reg", "add", runKeyPath, "/v", "TabyProto-Desktop", "/t", "REG_SZ", "/d", "\"$exePath\"", "/f"),
            result,
        )
    }

    @Test
    fun `enabled with minimized builds add command with minimized flag appended`() {
        val result = StartupLaunchManager.buildRegCommand(exePath, enabled = true, minimized = true)
        assertEquals(
            listOf("reg", "add", runKeyPath, "/v", "TabyProto-Desktop", "/t", "REG_SZ", "/d", "\"$exePath\" --minimized", "/f"),
            result,
        )
    }

    @Test
    fun `disabled builds delete command regardless of minimized flag`() {
        val result = StartupLaunchManager.buildRegCommand(exePath, enabled = false, minimized = true)
        assertEquals(
            listOf("reg", "delete", runKeyPath, "/v", "TabyProto-Desktop", "/f"),
            result,
        )
    }
}
