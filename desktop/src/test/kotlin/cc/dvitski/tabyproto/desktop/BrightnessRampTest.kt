package cc.dvitski.tabyproto.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrightnessRampTest {

    @Test
    fun `no steps when already at target`() {
        // The disconnect-triggering flood: ramping 100 to 100 emitted dozens of
        // identical BRIGHTNESS 100 commands. It must emit nothing.
        assertEquals(emptyList(), brightnessRampSteps(from = 100, target = 100, durationMs = 800))
    }

    @Test
    fun `emits no consecutive duplicate values`() {
        val steps = brightnessRampSteps(from = 0, target = 100, durationMs = 1500)
        assertTrue(steps.zipWithNext().none { (a, b) -> a == b }, "duplicate adjacent values: $steps")
    }

    @Test
    fun `lands exactly on target`() {
        assertEquals(100, brightnessRampSteps(from = 0, target = 100, durationMs = 1500).last())
        assertEquals(20, brightnessRampSteps(from = 100, target = 20, durationMs = 800).last())
    }

    @Test
    fun `coarser step interval emits fewer commands over the same ramp`() {
        // The step interval is the knob for how hard a ramp hammers the device — brightness
        // commands touch the LVGL path, which correlates with the firmware hang.
        val fine = brightnessRampSteps(from = 0, target = 100, durationMs = 1500, stepMs = 30)
        val coarse = brightnessRampSteps(from = 0, target = 100, durationMs = 1500, stepMs = 60)
        assertTrue(coarse.size < fine.size, "coarse=${coarse.size} fine=${fine.size}")
        assertEquals(100, coarse.last())
    }

    @Test
    fun `ramps monotonically toward target`() {
        val up = brightnessRampSteps(from = 10, target = 90, durationMs = 1500)
        assertEquals(up.sorted(), up)
        val down = brightnessRampSteps(from = 90, target = 10, durationMs = 1500)
        assertEquals(down.sortedDescending(), down)
    }
}
