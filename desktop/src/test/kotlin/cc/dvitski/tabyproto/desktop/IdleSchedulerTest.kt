@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package cc.dvitski.tabyproto.desktop

import cc.dvitski.tabyproto.Animation
import cc.dvitski.tabyproto.AnimationPriority
import cc.dvitski.tabyproto.Animations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdleSchedulerTest {

    private val IDLE_POOL = listOf(
        Animations.IDLE_01_LOOP, Animations.IDLE_02_LOOP, Animations.IDLE_VARIATION_LOOP,
    )
    private val RELAXED_POOL = listOf(
        Animations.SLEEPING_LOOP, Animations.RELAXING_01_LOOP, Animations.RELAXING_COUCH_LOOP,
    )

    @Test
    fun `requests idle animation after variationIntervalSec`() = runTest {
        val requested = mutableListOf<Animation>()
        val settings = MutableStateFlow(IdleSettings(
            variationIntervalSec = 60,
            relaxedThresholdSec  = 300,
            dimEnabled           = false,
            dimDelayThresholdSec = 300,
            dimFloorPercent      = 20,
        ))
        IdleScheduler(
            scope                = backgroundScope,
            settings             = settings,
            onRequestAnimation   = { anim, _ -> requested += anim },
            onStopAnimation      = {},
            onOverrideBrightness = {},
            onRestoreBrightness  = {},
            getSavedBrightness   = { 100 },
        )

        advanceTimeBy(60_001L)

        assertEquals(1, requested.size)
        assertTrue(requested[0] in IDLE_POOL)
    }

    @Test
    fun `switches to relaxed pool after relaxedThresholdSec`() = runTest {
        val requested = mutableListOf<Animation>()
        val settings = MutableStateFlow(IdleSettings(
            variationIntervalSec = 60,
            relaxedThresholdSec  = 120,
            dimEnabled           = false,
            dimDelayThresholdSec = 600,
            dimFloorPercent      = 20,
        ))
        IdleScheduler(
            scope                = backgroundScope,
            settings             = settings,
            onRequestAnimation   = { anim, _ -> requested += anim },
            onStopAnimation      = {},
            onOverrideBrightness = {},
            onRestoreBrightness  = {},
            getSavedBrightness   = { 100 },
        )

        advanceTimeBy(180_001L) // 3 ticks: 60s, 120s, 180s

        // ticks at 60s → idle pool, 120s → relaxed pool (120 >= 120), 180s → relaxed pool
        assertEquals(3, requested.size)
        assertTrue(requested[0] in IDLE_POOL)
        assertTrue(requested[1] in RELAXED_POOL)
        assertTrue(requested[2] in RELAXED_POOL)
    }

    @Test
    fun `requests use AnimationPriority_IDLE priority`() = runTest {
        val priorities = mutableListOf<Int>()
        val settings = MutableStateFlow(IdleSettings(variationIntervalSec = 60))
        IdleScheduler(
            scope                = backgroundScope,
            settings             = settings,
            onRequestAnimation   = { _, priority -> priorities += priority },
            onStopAnimation      = {},
            onOverrideBrightness = {},
            onRestoreBrightness  = {},
            getSavedBrightness   = { 100 },
        )

        advanceTimeBy(60_001L)

        assertEquals(listOf(AnimationPriority.IDLE), priorities)
    }

    @Test
    fun `no brightness override when dimEnabled is false`() = runTest {
        val brightnesses = mutableListOf<Int>()
        val settings = MutableStateFlow(IdleSettings(
            variationIntervalSec = 60,
            relaxedThresholdSec  = 600,
            dimEnabled           = false,
            dimDelayThresholdSec = 60,
            dimFloorPercent      = 20,
        ))
        IdleScheduler(
            scope                = backgroundScope,
            settings             = settings,
            onRequestAnimation   = { _, _ -> },
            onStopAnimation      = {},
            onOverrideBrightness = { brightnesses += it },
            onRestoreBrightness  = {},
            getSavedBrightness   = { 100 },
        )

        advanceTimeBy(300_001L)

        assertTrue(brightnesses.isEmpty())
    }

    @Test
    fun `brightness ramps linearly from saved to floor`() = runTest {
        val brightnesses = mutableListOf<Int>()
        // variationInterval=60s, dimDelay=120s, floor=20, saved=100
        // tick 1 (60s): elapsed=60, < dimDelay=120 → no dim
        // tick 2 (120s): elapsed=120 == dimDelay → progress=0.0, dimmed=100
        // tick 3 (180s): progress=(180-120)/120=0.5, dimmed=100-(80*0.5)=60
        // tick 4 (240s): progress=1.0, dimmed=20
        val settings = MutableStateFlow(IdleSettings(
            variationIntervalSec = 60,
            relaxedThresholdSec  = 600,
            dimEnabled           = true,
            dimDelayThresholdSec = 120,
            dimFloorPercent      = 20,
        ))
        IdleScheduler(
            scope                = backgroundScope,
            settings             = settings,
            onRequestAnimation   = { _, _ -> },
            onStopAnimation      = {},
            onOverrideBrightness = { brightnesses += it },
            onRestoreBrightness  = {},
            getSavedBrightness   = { 100 },
        )

        advanceTimeBy(241_000L)

        assertEquals(3, brightnesses.size)
        assertEquals(100, brightnesses[0]) // progress 0.0
        assertEquals(60,  brightnesses[1]) // progress 0.5
        assertEquals(20,  brightnesses[2]) // progress 1.0, clamped at floor
    }

    @Test
    fun `brightness is clamped at floor when past 2x dimDelay`() = runTest {
        val brightnesses = mutableListOf<Int>()
        val settings = MutableStateFlow(IdleSettings(
            variationIntervalSec = 60,
            relaxedThresholdSec  = 600,
            dimEnabled           = true,
            dimDelayThresholdSec = 60,
            dimFloorPercent      = 30,
        ))
        IdleScheduler(
            scope                = backgroundScope,
            settings             = settings,
            onRequestAnimation   = { _, _ -> },
            onStopAnimation      = {},
            onOverrideBrightness = { brightnesses += it },
            onRestoreBrightness  = {},
            getSavedBrightness   = { 80 },
        )

        advanceTimeBy(600_001L) // 10 ticks, well past 2×dimDelay

        assertTrue(brightnesses.all { it >= 30 })
        assertEquals(30, brightnesses.last())
    }

    @Test
    fun `notifyActivity stops IDLE and restores brightness`() = runTest {
        val stopped = mutableListOf<Int>()
        val restoredCount = mutableListOf<Unit>()
        val settings = MutableStateFlow(IdleSettings(variationIntervalSec = 60, dimEnabled = false))
        val scheduler = IdleScheduler(
            scope                = backgroundScope,
            settings             = settings,
            onRequestAnimation   = { _, _ -> },
            onStopAnimation      = { priority -> stopped += priority },
            onOverrideBrightness = {},
            onRestoreBrightness  = { restoredCount += Unit },
            getSavedBrightness   = { 100 },
        )

        scheduler.notifyActivity()
        advanceTimeBy(1L)

        assertEquals(listOf(AnimationPriority.IDLE), stopped)
        assertEquals(1, restoredCount.size)
    }

    @Test
    fun `consecutive animations are not the same`() = runTest {
        val requested = mutableListOf<Animation>()
        val settings = MutableStateFlow(IdleSettings(
            variationIntervalSec = 60,
            relaxedThresholdSec  = 600,
            dimEnabled           = false,
        ))
        IdleScheduler(
            scope                = backgroundScope,
            settings             = settings,
            onRequestAnimation   = { anim, _ -> requested += anim },
            onStopAnimation      = {},
            onOverrideBrightness = {},
            onRestoreBrightness  = {},
            getSavedBrightness   = { 100 },
        )

        advanceTimeBy(600_001L) // 10 ticks

        for (i in 1 until requested.size) {
            assertFalse(requested[i] == requested[i - 1], "Repeated animation at index $i: ${requested[i].id}")
        }
    }
}
