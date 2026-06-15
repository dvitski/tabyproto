package cc.dvitski.tabyproto.desktop

import kotlin.time.Duration

sealed class MusicState {
    object Idle : MusicState()
    data class Active(val sessions: List<NowPlaying>) : MusicState()
}

data class NowPlaying(
    val track: String?,
    val artist: String?,
    val albumArtUri: String?,
    val position: Duration,
    val duration: Duration?,
    val source: MusicSource,
    val isPlaying: Boolean,
    val appId: String?,
)

fun MusicState.Active.primary(): NowPlaying =
    sessions.maxByOrNull { (if (it.isPlaying) 10 else 0) + when (it.source) { MusicSource.Spotify -> 2; MusicSource.Tidal -> 1; else -> 0 } }
        ?: sessions.first()

enum class MusicSource { Generic, Spotify, Tidal }

enum class MediaControl { PlayPause, Next, Prev }
