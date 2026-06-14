package cc.dvitski.tabyproto.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    val appState = remember { AppState() }
    Window(
        onCloseRequest = {
            appState.close()
            exitApplication()
        },
        title = "TabyProto",
        state = rememberWindowState(width = 1100.dp, height = 860.dp),
    ) {
        App(appState)
    }
}
