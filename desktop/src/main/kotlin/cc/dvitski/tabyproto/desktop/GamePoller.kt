package cc.dvitski.tabyproto.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

class GamePoller {

    private val cacheDir = File(System.getProperty("java.io.tmpdir"), "taby-game").also { it.mkdirs() }
    private val exeFile = File(cacheDir, "game_query_v1.exe")
    private val exePath: String? by lazy { buildExe() }

    private fun buildExe(): String? {
        if (exeFile.exists()) return exeFile.absolutePath
        val csc = findCsc() ?: return null
        val src = GamePoller::class.java.getResourceAsStream("/game_query.cs") ?: return null
        val srcFile = File(cacheDir, "game_query.cs").also { f -> src.use { s -> f.writeBytes(s.readBytes()) } }

        ProcessBuilder(
            csc, "/nologo", "/target:exe", "/platform:x64",
            "/out:${exeFile.absolutePath}",
            srcFile.absolutePath,
        ).redirectErrorStream(true).start().also { it.inputStream.bufferedReader().readText() }.waitFor()

        return exeFile.takeIf { it.exists() }?.absolutePath
    }

    // Requires .NET Framework 4.x (csc.exe ships with Windows via .NET Framework).
    private fun findCsc(): String? =
        """C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe""".let { if (File(it).exists()) it else null }

    suspend fun poll(): GameQueryResult = withContext(Dispatchers.IO) {
        val exe = exePath ?: return@withContext GameQueryResult(null, false)
        try {
            val proc = ProcessBuilder(exe).redirectErrorStream(true).start()
            val output = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            parseResult(output)
        } catch (_: Exception) {
            GameQueryResult(null, false)
        }
    }

    private fun parseResult(raw: String): GameQueryResult = try {
        val json = raw.lines().lastOrNull { it.trim().startsWith("{") }?.trim() ?: return GameQueryResult(null, false)
        val obj = Json.parseToJsonElement(json) as? JsonObject ?: return GameQueryResult(null, false)
        GameQueryResult(
            processName  = (obj["processName"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
            isFullscreen = (obj["isFullscreen"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false,
        )
    } catch (_: Exception) {
        GameQueryResult(null, false)
    }

    data class GameQueryResult(val processName: String?, val isFullscreen: Boolean)
}
