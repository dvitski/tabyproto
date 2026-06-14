package cc.dvitski.tabyproto.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.dvitski.tabyproto.Animation

@Composable
fun AnimationGrid(
    animations: List<Animation>,
    thumbnailCache: ThumbnailCache,
    sendingAnimation: Animation?,
    onSend: (Animation) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(animations, key = { it.id }) { animation ->
            AnimationCell(
                animation = animation,
                thumbnail = thumbnailCache.thumbnailFor(animation),
                isSending = sendingAnimation == animation,
                onSend = { onSend(animation) },
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}
