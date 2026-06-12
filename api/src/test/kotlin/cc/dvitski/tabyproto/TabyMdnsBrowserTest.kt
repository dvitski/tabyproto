package cc.dvitski.tabyproto

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TabyMdnsBrowserTest {

    @Test
    fun `matches taby in service name case-insensitively`() {
        assertTrue(isTabyCandidate("Taby-AB12", null))
        assertTrue(isTabyCandidate("my-taby-device", null))
    }

    @Test
    fun `matches taby in server hostname`() {
        assertTrue(isTabyCandidate("some-service", "taby.local."))
    }

    @Test
    fun `rejects unrelated services`() {
        assertFalse(isTabyCandidate("printer", "printer.local."))
        assertFalse(isTabyCandidate(null, null))
    }
}
