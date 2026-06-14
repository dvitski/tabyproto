package cc.dvitski.tabyproto

sealed class Animation {
    abstract val id: String
    abstract val displayName: String

    /** Plays once and stops. */
    data class Once(
        override val id: String,
        override val displayName: String,
        val raw: RawAnimation,
    ) : Animation()

    /** Plays an optional intro, then loops the body indefinitely until stopped. */
    data class Looping(
        override val id: String,
        override val displayName: String,
        val intro: RawAnimation?,
        val body: RawAnimation,
    ) : Animation()
}
