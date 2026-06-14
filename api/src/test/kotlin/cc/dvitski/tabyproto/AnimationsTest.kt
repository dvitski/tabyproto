package cc.dvitski.tabyproto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnimationsTest {

    @Test
    fun `byId returns null for unknown id`() {
        assertNull(Animations.byId("does_not_exist"))
    }

    @Test
    fun `busy_loop is Looping without intro`() {
        val anim = Animations.byId("busy_loop")
        assertNotNull(anim)
        assertIs<Animation.Looping>(anim)
        assertNull(anim.intro)
        assertEquals(RawAnimation.BUSY_LOOP, anim.body)
    }

    @Test
    fun `calendar_loop is Looping with calendar_in as intro`() {
        val anim = Animations.byId("calendar_loop")
        assertNotNull(anim)
        assertIs<Animation.Looping>(anim)
        assertEquals(RawAnimation.CALENDAR_IN, anim.intro)
        assertEquals(RawAnimation.CALENDAR_LOOP, anim.body)
    }

    @Test
    fun `task_completed is Once`() {
        val anim = Animations.byId("task_completed")
        assertNotNull(anim)
        assertIs<Animation.Once>(anim)
        assertEquals(RawAnimation.TASK_COMPLETED, anim.raw)
    }

    @Test
    fun `all entries have unique ids`() {
        val ids = Animations.all.map { it.id }
        assertEquals(ids.distinct(), ids)
    }

    @Test
    fun `no entry in all uses an _in id as its canonical id`() {
        assertTrue(Animations.all.none { it.id.endsWith("_in") })
    }

    @Test
    fun `intro transition wire format is correct`() {
        val looping = Animations.byId("calendar_loop") as Animation.Looping
        val cmd = AnimationCommand(looping.intro!!, looping.body)
        assertEquals("calendar_in>calendar_loop", cmd.toWireString())
    }
}
