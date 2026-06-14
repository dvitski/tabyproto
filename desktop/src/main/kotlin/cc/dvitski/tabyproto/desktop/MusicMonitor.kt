package cc.dvitski.tabyproto.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

class MusicMonitor(private val scope: CoroutineScope) {

    private val poller = SmtcPoller()
    private val _state = MutableStateFlow<MusicState>(MusicState.Idle)
    val state: StateFlow<MusicState> = _state.asStateFlow()

    fun start() {
        scope.launch {
            while (true) {
                _state.value = poller.poll().toMusicState()
                delay(2_000L)
            }
        }
    }

    fun sendControl(control: MediaControl) {
        val vk = when (control) {
            MediaControl.PlayPause -> 0xB3
            MediaControl.Next      -> 0xB0
            MediaControl.Prev      -> 0xB1
        }
        scope.launch(Dispatchers.IO) {
            val exe = mediaKeyExe ?: return@launch
            runCatching { ProcessBuilder(exe, "$vk").start().waitFor() }
        }
    }

    private val mediaKeyExe: String? by lazy { buildMediaKeyExe() }

    private fun buildMediaKeyExe(): String? {
        val cacheDir = File(System.getProperty("java.io.tmpdir"), "taby-smtc").also { it.mkdirs() }
        val exe = File(cacheDir, "media_key.exe")
        if (exe.exists()) return exe.absolutePath
        val csc = """C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"""
        if (!File(csc).exists()) return null
        val src = MusicMonitor::class.java.getResourceAsStream("/media_key.cs") ?: return null
        val srcFile = File(cacheDir, "media_key.cs").also { f -> src.use { s -> f.writeBytes(s.readBytes()) } }
        ProcessBuilder(csc, "/nologo", "/target:exe", "/platform:x64",
            "/out:${exe.absolutePath}", srcFile.absolutePath)
            .redirectErrorStream(true).start()
            .also { it.inputStream.bufferedReader().readText() }.waitFor()
        return exe.takeIf { it.exists() }?.absolutePath
    }

    private fun SmtcPoller.SmtcResult.toMusicState(): MusicState = when (this) {
        SmtcPoller.SmtcResult.Idle -> MusicState.Idle
        is SmtcPoller.SmtcResult.Playing -> MusicState.Playing(
            track        = title,
            artist       = artist,
            albumArtUri  = albumArtPath?.let { "file://$it" },
            position     = positionMs.milliseconds,
            duration     = durationMs?.takeIf { it > 0 }?.milliseconds,
            source       = when {
                appId?.contains("Spotify", ignoreCase = true) == true -> MusicSource.Spotify
                appId?.contains("Tidal",   ignoreCase = true) == true -> MusicSource.Tidal
                else -> MusicSource.Generic
            },
            isPlaying    = isPlaying,
        )
    }
}
