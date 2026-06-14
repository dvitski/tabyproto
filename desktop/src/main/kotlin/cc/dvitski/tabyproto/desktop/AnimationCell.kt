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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
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
    LaunchedEffect(hovered, isSending) {
        if (hovered && !isSending) {
            val path = AnimationResources.videoPath(animation.id) ?: return@LaunchedEffect
            playerState.loop = true
            playerState.openUri(path)
        } else {
            playerState.pause()
        }
    }

    Column(
        modifier = modifier
            .clip(AppCardShape)
            .background(theme.surface)
            .border(BorderStroke(if (hovered) 2.dp else 1.dp, if (hovered) theme.accent else theme.border), AppCardShape)
            .clickable(enabled = !isSending, onClick = onSend)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val ratio = if (thumbnail != null) thumbnail.width.toFloat() / thumbnail.height.toFloat() else 1f
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(ratio).clip(AppCardShape),
            contentAlignment = Alignment.Center,
        ) {
            val videoReady = hovered && playerState.isPlaying && !playerState.isLoading
            when {
                videoReady -> VideoPlayerSurface(playerState = playerState, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                thumbnail != null -> Image(bitmap = thumbnail, contentDescription = animation.id, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                else -> Text("…", color = theme.textSecondary, fontSize = 18.sp)
            }

            // Hover play overlay
            if (hovered && !isSending) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(theme.accent.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
            }

            if (isSending) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.50f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = theme.accent, modifier = Modifier.size(34.dp), strokeWidth = 3.dp)
                }
            }

            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 3.dp, vertical = 2.dp),
            ) {
                Icon(
                    imageVector = when {
                        animation is Animation.Looping && animation.intro != null -> Icons.Rounded.AllInclusive
                        animation is Animation.Looping                            -> Icons.Rounded.Repeat
                        else                                                      -> Icons.Rounded.PlayArrow
                    },
                    contentDescription = when {
                        animation is Animation.Looping && animation.intro != null -> "Intro + loop"
                        animation is Animation.Looping                            -> "Looping"
                        else                                                      -> "Play once"
                    },
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(12.dp),
                )
            }
        }

        Text(
            text = animation.displayName,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = theme.textPrimary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
}
