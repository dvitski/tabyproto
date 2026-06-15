package cc.dvitski.tabyproto.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class LockEvent {
    object Locked : LockEvent()
    object Unlocked : LockEvent()
}

/**
 * Watches Windows session lock/unlock by compiling and running [session_monitor.cs]
 * with csc.exe (same toolchain as [SmtcPoller]) and reading LOCK / UNLOCK lines.
 * Degrades to a no-op if csc.exe is unavailable.
 */
class LockMonitor(private val scope: CoroutineScope) {

    private val cacheDir = File(System.getProperty("java.io.tmpdir"), "taby-lock").also { it.mkdirs() }
    private val exeFile = File(cacheDir, "session_monitor.exe")

    private val _events = MutableSharedFlow<LockEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<LockEvent> = _events.asSharedFlow()

    @Volatile private var process: Process? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        scope.launch(Dispatchers.IO) { runLoop() }
    }

    fun close() {
        running = false
        process?.destroy()
        process = null
    }

    private suspend fun runLoop() {
        val exe = buildExe() ?: return
        while (running) {
            try {
                val proc = ProcessBuilder(exe).start()
                process = proc
                proc.inputStream.bufferedReader().use { reader ->
                    while (running) {
                        val line = reader.readLine() ?: break
                        when (line.trim()) {
                            "LOCK"   -> _events.emit(LockEvent.Locked)
                            "UNLOCK" -> _events.emit(LockEvent.Unlocked)
                        }
                    }
                }
                proc.waitFor()
            } catch (_: Exception) {
                // fall through and restart below
            }
            if (running) delay(2_000L) // brief backoff before relaunching the helper
        }
    }

    private fun buildExe(): String? {
        if (exeFile.exists()) return exeFile.absolutePath
        val csc = findCsc() ?: return null
        val src = LockMonitor::class.java.getResourceAsStream("/session_monitor.cs") ?: return null
        val srcFile = File(cacheDir, "session_monitor.cs").also { f -> src.use { s -> f.writeBytes(s.readBytes()) } }
        ProcessBuilder(
            csc, "/nologo", "/target:exe", "/platform:x64",
            "/out:${exeFile.absolutePath}",
            "/r:System.Windows.Forms.dll",
            srcFile.absolutePath,
        ).redirectErrorStream(true).start().also { it.inputStream.bufferedReader().readText() }.waitFor()
        return exeFile.takeIf { it.exists() }?.absolutePath
    }

    // csc.exe ships with .NET Framework 4.x on Windows (see SmtcPoller for the same probe).
    private fun findCsc(): String? =
        """C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe""".let { if (File(it).exists()) it else null }
}
