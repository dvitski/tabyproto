package cc.dvitski.tabyproto.desktop

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WindowSize { Compact, Medium, Expanded }

fun windowSizeFor(width: Dp): WindowSize = when {
    width < 680.dp -> WindowSize.Compact
    width < 960.dp -> WindowSize.Medium
    else           -> WindowSize.Expanded
}

val LocalWindowSize = compositionLocalOf { WindowSize.Expanded }
