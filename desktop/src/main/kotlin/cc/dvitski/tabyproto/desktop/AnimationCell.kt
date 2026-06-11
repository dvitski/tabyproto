package cc.dvitski.tabyproto.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.dvitski.tabyproto.Animation
import io.github.kdroidfilter.composemediaplayer.VideoPlayerSurface
import io.github.kdroidfilter.composemediaplayer.rememberVideoPlayerState

private val CardBackground = Color(0xFF313244)
private val BorderRest = BorderStroke(1.dp, Color(0xFF45475A))
private val BorderHover = BorderStroke(2.dp, Color(0xFFCBA6F7))
private val CardShape = RoundedCornerShape(8.dp)
private val SendingScrim = Color(0xAA000000)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AnimationCell(
    animation: Animation,
    thumbnail: ImageBitmap?,
    isSending: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var hovered by remember { mutableStateOf(false) }
    val playerState = rememberVideoPlayerState()

    DisposableEffect(playerState) {
        onDispose { playerState.dispose() }
    }

    LaunchedEffect(isSending) {
        if (isSending) hovered = false
    }

    LaunchedEffect(hovered) {
        if (hovered) {
            val path = AnimationResources.videoPath(animation.id) ?: return@LaunchedEffect
            playerState.loop = true
            playerState.openUri(path)
        } else {
            playerState.pause()
        }
    }

    val border = if (hovered) BorderHover else BorderRest

    Column(
        modifier = modifier
            .clip(CardShape)
            .background(CardBackground)
            .border(border, CardShape)
            .clickable(enabled = !isSending, onClick = onSend)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Square image/video area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            // Only show the video surface once WMF has frames — avoids black flash.
            val videoReady = hovered && playerState.isPlaying && !playerState.isLoading
            if (videoReady) {
                VideoPlayerSurface(
                    playerState = playerState,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = animation.id,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CircularProgressIndicator()
            }

            // Sending overlay — layered on top of whatever is showing
            if (isSending) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SendingScrim),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }

        // Animation ID label
        Text(
            text = animation.id,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }
}
