package cc.dvitski.tabyproto.desktop

import java.util.prefs.Preferences

class IdleStore {
    private val prefs = Preferences.userNodeForPackage(IdleStore::class.java)

    fun load() = IdleSettings(
        variationIntervalSec  = prefs.getInt(KEY_VARIATION, 90),
        relaxedThresholdSec   = prefs.getInt(KEY_RELAXED,   300),
        dimEnabled            = prefs.getBoolean(KEY_DIM_ENABLED, true),
        dimDelayThresholdSec  = prefs.getInt(KEY_DIM_DELAY,  300),
        dimFloorPercent       = prefs.getInt(KEY_DIM_FLOOR,  20),
    )

    fun save(s: IdleSettings) {
        prefs.putInt(KEY_VARIATION,       s.variationIntervalSec)
        prefs.putInt(KEY_RELAXED,         s.relaxedThresholdSec)
        prefs.putBoolean(KEY_DIM_ENABLED, s.dimEnabled)
        prefs.putInt(KEY_DIM_DELAY,       s.dimDelayThresholdSec)
        prefs.putInt(KEY_DIM_FLOOR,       s.dimFloorPercent)
    }

    private companion object {
        const val KEY_VARIATION   = "idleVariationIntervalSec"
        const val KEY_RELAXED     = "idleRelaxedThresholdSec"
        const val KEY_DIM_ENABLED = "idleDimEnabled"
        const val KEY_DIM_DELAY   = "idleDimDelayThresholdSec"
        const val KEY_DIM_FLOOR   = "idleDimFloorPercent"
    }
}
