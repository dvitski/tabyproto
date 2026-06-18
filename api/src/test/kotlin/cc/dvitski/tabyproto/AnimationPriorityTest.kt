package cc.dvitski.tabyproto

import kotlin.test.Test
import kotlin.test.assertTrue

class AnimationPriorityTest {
    @Test
    fun `GAME priority sits between MUSIC and VOICE`() {
        assertTrue(AnimationPriority.GAME > AnimationPriority.MUSIC)
        assertTrue(AnimationPriority.GAME < AnimationPriority.VOICE)
    }
}
