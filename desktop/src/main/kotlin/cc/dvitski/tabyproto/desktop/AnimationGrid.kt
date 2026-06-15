package cc.dvitski.tabyproto.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.dvitski.tabyproto.Animation
import io.github.kdroidfilter.composemediaplayer.rememberVideoPlayerState

@Composable
fun AnimationGrid(
    animations: List<Animation>,
    thumbnailCache: ThumbnailCache,
    sendingAnimation: Animation?,
    onSend: (Animation) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A single shared video player for the whole grid: only the hovered cell plays.
    // Using one long-lived player (instead of one per cell) avoids the per-cell
    // open/dispose churn that races composemediaplayer's native frame pipeline and
    // crashes the JVM (EXCEPTION_ACCESS_VIOLATION in nReadVideoFrame).
    val player = rememberVideoPlayerState()
    var hoveredId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(player) { onDispose { player.dispose() } }

    LaunchedEffect(hoveredId, sendingAnimation) {
        val hovered = animations.firstOrNull { it.id == hoveredId }
        val path = hovered?.let { AnimationResources.videoPath(it.id) }
        if (hovered != null && path != null && sendingAnimation != hovered) {
            player.loop = true
            player.openUri(path)
        } else {
            player.pause()
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 170.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(animations, key = { it.id }) { animation ->
            val videoActive = hoveredId == animation.id && player.isPlaying && !player.isLoading
            AnimationCell(
                animation = animation,
                thumbnail = thumbnailCache.thumbnailFor(animation),
                isSending = sendingAnimation == animation,
                player = player,
                videoActive = videoActive,
                onHoverChange = { hovered ->
                    if (hovered) hoveredId = animation.id
                    else if (hoveredId == animation.id) hoveredId = null
                },
                onSend = { onSend(animation) },
                modifier = Modifier.padding(6.dp),
            )
        }
    }
}
