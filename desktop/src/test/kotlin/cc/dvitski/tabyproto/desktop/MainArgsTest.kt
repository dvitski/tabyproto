package cc.dvitski.tabyproto.desktop

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainArgsTest {

    @Test
    fun `no args means not minimized`() {
        assertFalse(shouldStartMinimized(emptyArray()))
    }

    @Test
    fun `minimized flag present means minimized`() {
        assertTrue(shouldStartMinimized(arrayOf("--minimized")))
    }

    @Test
    fun `unrelated args do not trigger minimized`() {
        assertFalse(shouldStartMinimized(arrayOf("--some-other-flag")))
    }
}
