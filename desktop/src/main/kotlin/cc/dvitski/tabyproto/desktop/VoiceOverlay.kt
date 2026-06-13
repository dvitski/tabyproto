package cc.dvitski.tabyproto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VoiceOverlay(listeningState: ListeningState, onDismiss: () -> Unit) {
    val theme = LocalAppTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.60f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .clip(AppCardShape)
                .background(theme.surface)
                .border(1.dp, theme.border, AppCardShape)
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(theme.accent.copy(alpha = 0.15f))
                    .border(2.dp, theme.accent, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = null, tint = theme.accent, modifier = Modifier.size(30.dp))
            }

            Text(
                text = when (listeningState) {
                    ListeningState.Idle -> "Ready"
                    ListeningState.WakeWordDetected -> "Wake word detected"
                    ListeningState.Listening -> "Listening…"
                    ListeningState.Responding -> "Responding"
                },
                color = theme.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(6.dp, 14.dp, 20.dp, 11.dp, 22.dp, 8.dp, 18.dp, 6.dp).forEach { h ->
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(h)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                            .background(theme.accent.copy(alpha = 0.7f))
                    )
                }
            }

            Text("Click outside to dismiss", color = theme.textSecondary, fontSize = 11.sp)
        }
    }
}
