package cc.dvitski.tabyproto.desktop

import cc.dvitski.tabyproto.Animation
import cc.dvitski.tabyproto.AnimationPriority
import cc.dvitski.tabyproto.Animations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class IdleScheduler(
    private val scope: CoroutineScope,
    private val settings: StateFlow<IdleSettings>,
    private val onRequestAnimation: suspend (Animation, Int) -> Unit,
    private val onStopAnimation: suspend (Int) -> Unit,
    private val onOverrideBrightness: suspend (Int) -> Unit,
    private val onRestoreBrightness: suspend () -> Unit,
    private val getSavedBrightness: () -> Int?,
) {
    internal companion object {
        val IDLE_POOL = listOf(
            Animations.IDLE_01_LOOP,
            Animations.IDLE_02_LOOP,
            Animations.IDLE_VARIATION_LOOP,
        )
        val RELAXED_POOL = listOf(
            Animations.SLEEPING_LOOP,
            Animations.RELAXING_01_LOOP,
            Animations.RELAXING_COUCH_LOOP,
        )
    }

    private var loopJob: Job? = null

    init { startLoop() }

    fun notifyActivity() {
        loopJob?.cancel()
        scope.launch {
            onRestoreBrightness()
            onStopAnimation(AnimationPriority.IDLE)
        }
        startLoop()
    }

    private fun startLoop() {
        loopJob = scope.launch { runLoop() }
    }

    private suspend fun runLoop() {
        var elapsedSec = 0L
        var lastAnim: Animation? = null
        while (true) {
            val s = settings.value
            delay(s.variationIntervalSec * 1000L)
            elapsedSec += s.variationIntervalSec
            val pool = if (elapsedSec >= s.relaxedThresholdSec) RELAXED_POOL else IDLE_POOL
            val candidates = pool.filter { it != lastAnim }
            val next = candidates.randomOrNull() ?: pool.random()
            lastAnim = next
            onRequestAnimation(next, AnimationPriority.IDLE)
            if (s.dimEnabled && elapsedSec >= s.dimDelayThresholdSec) {
                val saved = getSavedBrightness() ?: 100
                val floor = s.dimFloorPercent.coerceAtMost(saved)
                val progress = ((elapsedSec - s.dimDelayThresholdSec).toFloat() /
                    s.dimDelayThresholdSec.toFloat()).coerceIn(0f, 1f)
                val dimmed = (saved - (saved - floor) * progress)
                    .toInt().coerceAtLeast(floor)
                onOverrideBrightness(dimmed)
            }
        }
    }
}
