package cc.dvitski.tabyproto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VoiceOverlay(
    listeningState: ListeningState,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF12122A))
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF5B21B6)),
            )

            Text(
                text = when (listeningState) {
                    ListeningState.Idle -> "READY"
                    ListeningState.WakeWordDetected -> "WAKE WORD"
                    ListeningState.Listening -> "LISTENING"
                    ListeningState.Responding -> "RESPONDING"
                },
                color = Color(0xFFA78BFA),
                fontSize = 11.sp,
                letterSpacing = 3.sp,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(6.dp, 14.dp, 20.dp, 11.dp, 22.dp, 8.dp, 18.dp, 6.dp).forEach { h ->
                    Box(
                        Modifier
                            .width(2.dp)
                            .height(h)
                            .background(Color(0xFFA78BFA)),
                    )
                }
            }

            Text(
                text = "Click outside to dismiss",
                color = Color(0xFF3D3060),
                fontSize = 9.sp,
            )
        }
    }
}
