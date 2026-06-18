package cc.dvitski.tabyproto.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GameMonitor(private val scope: CoroutineScope) {

    private val poller = GamePoller()
    private val _enabled = MutableStateFlow(true)
    private val _state = MutableStateFlow<GameState>(GameState.Idle)
    val state: StateFlow<GameState> = _state.asStateFlow()

    fun start() {
        scope.launch {
            while (true) {
                if (_enabled.value) {
                    val result = poller.poll()
                    _state.value = KnownGames.classify(result.processName, result.isFullscreen)
                }
                delay(3_000L)
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        if (!enabled) _state.value = GameState.Idle
    }
}
