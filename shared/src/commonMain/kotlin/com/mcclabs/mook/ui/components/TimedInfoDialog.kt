package com.mcclabs.mook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mcclabs.mook.ui.theme.BrandGradient
import com.mcclabs.mook.ui.theme.NeonColors
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * A branded, one-time informational dialog the user must read before dismissing.
 *
 * The dialog cannot be dismissed (no back-press, no tap-outside) until a
 * [countdownSeconds]-second timer elapses; while it runs the action button is
 * disabled and shows the remaining seconds, then becomes tappable with
 * [confirmText]. Fully theme-aware via [NeonColors].
 *
 * @param icon            Drawable shown inside the gradient badge.
 * @param title           Dialog heading.
 * @param body            Explanatory text.
 * @param confirmText     Label shown on the button once the countdown finishes.
 * @param onConfirm       Invoked when the user taps the (now-enabled) button.
 * @param countdownSeconds Seconds the user must wait before dismissing (default 5).
 */
@Composable
fun TimedInfoDialog(
    icon: DrawableResource,
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    countdownSeconds: Int = 5,
) {
    var remaining by remember { mutableStateOf(countdownSeconds) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining -= 1
        }
    }
    val enabled = remaining == 0

    Dialog(
        onDismissRequest = { /* Blocked until the countdown finishes. */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(NeonColors.Background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(BrandGradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = NeonColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = NeonColors.TextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))

            NeonPrimaryButton(
                text = if (enabled) confirmText else remaining.toString(),
                onClick = onConfirm,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
