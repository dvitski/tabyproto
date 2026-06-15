package cc.dvitski.tabyproto.desktop

import cc.dvitski.tabyproto.Animation

enum class IdlePhase { Active, Idle, Relaxed }

data class IdleStatus(
    val phase: IdlePhase,
    val lastActivityAt: Long,
    val currentAnimation: Animation?,
    val dimBrightness: Int?,
    val nextAnimAt: Long,
)

data class IdleSettings(
    val variationIntervalSec: Int = 90,
    val relaxedThresholdSec: Int = 300,
    val dimEnabled: Boolean = true,
    val dimDelayThresholdSec: Int = 300,
    val dimFloorPercent: Int = 20,
)
