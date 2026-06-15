package cc.dvitski.tabyproto.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.imageio.ImageIO

fun main() = application {
    val appState = remember { AppState() }
    val minimizeToTray by appState.minimizeToTray.collectAsState()
    var isWindowVisible by remember { mutableStateOf(true) }
    var awtWindow by remember { mutableStateOf<java.awt.Window?>(null) }

    fun showWindow() {
        isWindowVisible = true
        awtWindow?.toFront()
        awtWindow?.requestFocus()
    }

    if (SystemTray.isSupported()) {
        val trayIcon = remember {
            val image = ImageIO.read(
                object {}.javaClass.classLoader.getResource("icons/tray-icon.png")
            )
            val popup = PopupMenu().apply {
                add(MenuItem("Open").apply { addActionListener { showWindow() } })
                addSeparator()
                add(MenuItem("Quit").apply { addActionListener { appState.close(); exitApplication() } })
            }
            TrayIcon(image, "TabyProto", popup).apply {
                isImageAutoSize = true
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        if (e.button == MouseEvent.BUTTON1) showWindow()
                    }
                })
            }
        }

        DisposableEffect(Unit) {
            SystemTray.getSystemTray().add(trayIcon)
            onDispose { SystemTray.getSystemTray().remove(trayIcon) }
        }
    }

    Window(
        onCloseRequest = {
            if (minimizeToTray) isWindowVisible = false
            else { appState.close(); exitApplication() }
        },
        title = "TabyProto",
        state = rememberWindowState(width = 1100.dp, height = 860.dp),
        visible = isWindowVisible,
    ) {
        val density = LocalDensity.current
        SideEffect {
            awtWindow = window
            window.minimumSize = with(density) {
                Dimension(520.dp.roundToPx(), 460.dp.roundToPx())
            }
        }
        App(appState)
    }
}
