package cc.dvitski.tabyproto.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    fun sendControl(control: MediaControl, appId: String?) {
        if (control == MediaControl.PlayPause) {
            val current = _state.value
            if (current is MusicState.Active) {
                val sessions = current.sessions.toMutableList()
                val idx = if (appId != null) sessions.indexOfFirst { it.appId == appId }
                          else sessions.indexOfFirst { it.isPlaying }.takeIf { it >= 0 } ?: 0
                if (idx in sessions.indices) {
                    sessions[idx] = sessions[idx].copy(isPlaying = !sessions[idx].isPlaying)
                    _state.value = current.copy(sessions = sessions)
                }
            }
        }
        if (appId == null) return
        val command = when (control) {
            MediaControl.PlayPause -> "toggle"
            MediaControl.Next      -> "next"
            MediaControl.Prev      -> "prev"
        }
        scope.launch { poller.sendCommand(command, appId) }
    }

    private fun List<SmtcPoller.SmtcSession>.toMusicState(): MusicState {
        if (isEmpty()) return MusicState.Idle
        return MusicState.Active(map { s ->
            NowPlaying(
                track        = s.title,
                artist       = s.artist,
                albumArtUri  = s.albumArtPath?.let { "file://$it" },
                position     = s.positionMs.milliseconds,
                duration     = s.durationMs?.takeIf { it > 0 }?.milliseconds,
                source       = when {
                    s.appId?.contains("Spotify", ignoreCase = true) == true -> MusicSource.Spotify
                    s.appId?.contains("Tidal",   ignoreCase = true) == true -> MusicSource.Tidal
                    else -> MusicSource.Generic
                },
                isPlaying    = s.isPlaying,
                appId        = s.appId,
            )
        })
    }
}
