package cc.dvitski.tabyproto.desktop

sealed class GameState {
    object Idle : GameState()
    data class Active(val processName: String) : GameState()
}
