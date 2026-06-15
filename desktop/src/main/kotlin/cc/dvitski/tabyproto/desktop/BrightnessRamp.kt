package cc.dvitski.tabyproto.desktop

/**
 * The de-duplicated sequence of brightness values to send when ramping from
 * [from] to [target] over [durationMs] (one value per [stepMs]).
 *
 * Returns an empty list when already at [target] — this prevents the flood of
 * identical `BRIGHTNESS` commands that previously hammered the device every
 * [stepMs] for no effect. Consecutive duplicate values (from integer rounding)
 * are collapsed, and the sequence always lands exactly on [target].
 */
internal fun brightnessRampSteps(from: Int, target: Int, durationMs: Long, stepMs: Long = 30L): List<Int> {
    if (from == target) return emptyList()
    val steps = (durationMs / stepMs).toInt().coerceAtLeast(1)
    val out = ArrayList<Int>(steps)
    var last = from
    for (i in 1..steps) {
        val value = (from + (target - from).toFloat() * i / steps).toInt()
        if (value != last) {
            out.add(value)
            last = value
        }
    }
    if (out.lastOrNull() != target) out.add(target)
    return out
}
