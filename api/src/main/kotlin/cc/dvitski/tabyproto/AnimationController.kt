package cc.dvitski.tabyproto

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AnimationController(
    private val session: TabySession,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private data class Active(val priority: Int, val animation: Animation, val startedAt: Long, val durationMs: Long?)
    private data class Pending(val animation: Animation, val durationMs: Long?)

    private val mutex = Mutex()
    private var active: Active? = null
    private var expiryJob: Job? = null
    private val pending = sortedMapOf<Int, Pending>(reverseOrder())

    /** Returns true if the animation started playing immediately, false if queued. */
    suspend fun request(priority: Int, animation: Animation, durationMs: Long? = null, preempt: Boolean = true): Boolean {
        var toPlay: Animation? = null
        mutex.withLock {
            val now = clock()
            val cur = active
            toPlay = when {
                cur == null -> {
                    activate(priority, animation, durationMs, now)
                    animation
                }
                priority > cur.priority && preempt -> {
                    val remaining = cur.durationMs?.let { it - (now - cur.startedAt) } ?: Long.MAX_VALUE
                    if (remaining > 500L) {
                        expiryJob?.cancel()
                        activate(priority, animation, durationMs, now)
                        animation
                    } else {
                        pending[priority] = Pending(animation, durationMs)
                        null
                    }
                }
                else -> {
                    pending[priority] = Pending(animation, durationMs)
                    null
                }
            }
        }
        toPlay?.let { runCatching { session.play(it) } }
        return toPlay != null
    }

    suspend fun stop(priority: Int) {
        var toPlay: Animation? = null
        mutex.withLock {
            if (active?.priority != priority) return
            expiryJob?.cancel()
            active = null
            toPlay = advancePending()
        }
        toPlay?.let { runCatching { session.play(it) } }
    }

    suspend fun cancel(priority: Int) {
        mutex.withLock { pending.remove(priority) }
    }

    // Must be called while holding mutex
    private fun activate(priority: Int, animation: Animation, durationMs: Long?, now: Long) {
        val record = Active(priority, animation, now, durationMs).also { active = it }
        expiryJob = if (durationMs != null) scope.launch {
            delay(durationMs)
            var toPlay: Animation? = null
            mutex.withLock {
                if (active === record) {
                    active = null
                    toPlay = advancePending()
                }
            }
            toPlay?.let { runCatching { session.play(it) } }
        } else null
    }

    // Must be called while holding mutex; activates and returns the next pending animation
    private fun advancePending(): Animation? {
        val entry = pending.entries.firstOrNull() ?: return null
        pending.remove(entry.key)
        val next = entry.value
        activate(entry.key, next.animation, next.durationMs, clock())
        return next.animation
    }
}
