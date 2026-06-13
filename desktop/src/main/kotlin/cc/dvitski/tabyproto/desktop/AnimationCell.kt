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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.dvitski.tabyproto.Animation
import io.github.kdroidfilter.composemediaplayer.VideoPlayerSurface
import io.github.kdroidfilter.composemediaplayer.rememberVideoPlayerState

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AnimationCell(
    animation: Animation,
    thumbnail: ImageBitmap?,
    isSending: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    var hovered by remember { mutableStateOf(false) }
    val playerState = rememberVideoPlayerState()

    DisposableEffect(playerState) { onDispose { playerState.dispose() } }
    LaunchedEffect(isSending) { if (isSending) hovered = false }
    LaunchedEffect(hovered) {
        if (hovered) {
            val path = AnimationResources.videoPath(animation.id) ?: return@LaunchedEffect
            playerState.loop = true
            playerState.openUri(path)
        } else {
            playerState.pause()
        }
    }

    Column(
        modifier = modifier
            .background(theme.surface)
            .border(BorderStroke(if (hovered) 2.dp else 1.dp, if (hovered) theme.accent else theme.border))
            .clickable(enabled = !isSending, onClick = onSend)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            val videoReady = hovered && playerState.isPlaying && !playerState.isLoading
            when {
                videoReady -> VideoPlayerSurface(playerState = playerState, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                thumbnail != null -> Image(bitmap = thumbnail, contentDescription = animation.id, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else -> Text("…", color = theme.textSecondary, fontSize = 16.sp)
            }
            if (isSending) {
                Box(Modifier.fillMaxSize().background(Color(0xAA000000)), contentAlignment = Alignment.Center) {
                    Text("SENDING", color = theme.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
        Text(
            text = animation.id,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = theme.textPrimary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }
}
