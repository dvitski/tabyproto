package cc.dvitski.tabyproto.desktop

import kotlin.time.Duration

sealed class MusicState {
    object Idle : MusicState()
    data class Playing(
        val track: String?,
        val artist: String?,
        val albumArtUri: String?,
        val position: Duration,
        val duration: Duration?,
        val source: MusicSource,
        val isPlaying: Boolean,
    ) : MusicState()
}

enum class MusicSource { Generic, Spotify, Tidal }

enum class MediaControl { PlayPause, Next, Prev }
