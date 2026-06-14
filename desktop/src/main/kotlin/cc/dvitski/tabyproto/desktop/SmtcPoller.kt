package cc.dvitski.tabyproto.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

class SmtcPoller {

    private val cacheDir = File(System.getProperty("java.io.tmpdir"), "taby-smtc").also { it.mkdirs() }
    private val exeFile  = File(cacheDir, "smtc_query_v2.exe")

    private val exePath: String? by lazy { buildExe() }

    private fun buildExe(): String? {
        if (exeFile.exists()) return exeFile.absolutePath
        val csc        = findCsc()             ?: return null
        val winMd      = findWindowsWinMd()    ?: return null
        val sysRtFacade = findSysRtFacade()   ?: return null
        val winRtDll   = """C:\Windows\Microsoft.NET\Framework64\v4.0.30319\System.Runtime.WindowsRuntime.dll"""
        if (!File(winRtDll).exists()) return null

        val src = SmtcPoller::class.java.getResourceAsStream("/smtc_query.cs") ?: return null
        val srcFile = File(cacheDir, "smtc_query.cs").also { f -> src.use { s -> f.writeBytes(s.readBytes()) } }

        ProcessBuilder(
            csc, "/nologo", "/target:exe", "/platform:x64",
            "/out:${exeFile.absolutePath}",
            "/r:$winMd", "/r:$winRtDll", "/r:$sysRtFacade",
            srcFile.absolutePath,
        ).redirectErrorStream(true).start().also { it.inputStream.bufferedReader().readText() }.waitFor()

        return exeFile.takeIf { it.exists() }?.absolutePath
    }

    // Requires .NET Framework 4.x (csc.exe ships with Windows via .NET Framework).
    // Requires Windows SDK (Windows.winmd is in the UnionMetadata folder).
    // If compilation fails on a new machine, check these three paths.
    private fun findCsc(): String? =
        """C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe""".let { if (File(it).exists()) it else null }

    private fun findWindowsWinMd(): String? =
        File("""C:\Program Files (x86)\Windows Kits\10\UnionMetadata""").takeIf { it.exists() }
            ?.listFiles()
            ?.filter { it.isDirectory && it.name.matches(Regex("""\d+\.\d+\.\d+\.\d+""")) }
            ?.sortedDescending()
            ?.mapNotNull { File(it, "Windows.winmd").takeIf { f -> f.exists() } }
            ?.firstOrNull()
            ?.absolutePath

    private fun findSysRtFacade(): String? =
        listOf("v4.8.1", "v4.8", "v4.7.2", "v4.7.1").mapNotNull { ver ->
            File("""C:\Program Files (x86)\Reference Assemblies\Microsoft\Framework\.NETFramework\$ver\Facades\System.Runtime.dll""")
                .takeIf { it.exists() }
        }.firstOrNull()?.absolutePath

    suspend fun poll(): SmtcResult = withContext(Dispatchers.IO) {
        val exe = exePath ?: return@withContext SmtcResult.Idle
        try {
            val proc = ProcessBuilder(exe).redirectErrorStream(true).start()
            val output = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            parse(output)
        } catch (_: Exception) {
            SmtcResult.Idle
        }
    }

    private fun parse(raw: String): SmtcResult = try {
        val json = raw.lines().lastOrNull { it.trim().startsWith("{") }?.trim() ?: return SmtcResult.Idle
        val obj = Json.parseToJsonElement(json) as? JsonObject ?: return SmtcResult.Idle
        if (obj.str("state") != "Playing") return SmtcResult.Idle
        SmtcResult.Playing(
            title        = obj.str("title"),
            artist       = obj.str("artist"),
            albumArtPath = obj.str("albumArtPath"),
            positionMs   = obj.long("positionMs") ?: 0L,
            durationMs   = obj.long("durationMs"),
            appId        = obj.str("appId"),
            isPlaying    = obj.bool("isPlaying") ?: true,
        )
    } catch (_: Exception) {
        SmtcResult.Idle
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.content?.toLongOrNull()

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

    sealed class SmtcResult {
        object Idle : SmtcResult()
        data class Playing(
            val title: String?,
            val artist: String?,
            val albumArtPath: String?,
            val positionMs: Long,
            val durationMs: Long?,
            val appId: String?,
            val isPlaying: Boolean,
        ) : SmtcResult()
    }
}
