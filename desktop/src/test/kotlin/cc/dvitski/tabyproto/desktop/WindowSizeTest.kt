package cc.dvitski.tabyproto.desktop

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowSizeTest {
    @Test fun `below 680dp is Compact`()   = assertEquals(WindowSize.Compact,  windowSizeFor(679.dp))
    @Test fun `zero dp is Compact`()       = assertEquals(WindowSize.Compact,  windowSizeFor(0.dp))
    @Test fun `680dp is Medium`()          = assertEquals(WindowSize.Medium,   windowSizeFor(680.dp))
    @Test fun `959dp is Medium`()          = assertEquals(WindowSize.Medium,   windowSizeFor(959.dp))
    @Test fun `960dp is Expanded`()        = assertEquals(WindowSize.Expanded, windowSizeFor(960.dp))
    @Test fun `1100dp is Expanded`()       = assertEquals(WindowSize.Expanded, windowSizeFor(1100.dp))
}
