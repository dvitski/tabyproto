package cc.dvitski.tabyproto.desktop

data class IdleSettings(
    val variationIntervalSec: Int = 90,
    val relaxedThresholdSec: Int = 300,
    val dimEnabled: Boolean = true,
    val dimDelayThresholdSec: Int = 300,
    val dimFloorPercent: Int = 20,
)
