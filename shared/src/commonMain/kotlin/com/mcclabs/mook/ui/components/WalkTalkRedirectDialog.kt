package com.mcclabs.mook.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mcclabs.mook.ui.theme.BrandGradient
import com.mcclabs.mook.ui.theme.NeonColors
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.chat_round_line_svgrepo_com
import mook.shared.generated.resources.walktalk_dialog_back
import mook.shared.generated.resources.walktalk_dialog_body
import mook.shared.generated.resources.walktalk_dialog_confirm
import mook.shared.generated.resources.walktalk_dialog_feature_quality
import mook.shared.generated.resources.walktalk_dialog_feature_translation
import mook.shared.generated.resources.walktalk_dialog_feature_voice
import mook.shared.generated.resources.walktalk_dialog_title
import mook.shared.generated.resources.walktalk_dialog_title_named
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * A branded confirmation pop-up shown before sending the user from Mook to the
 * companion WalkTalk app. It explains *why* they're being taken there (smoother
 * chat, voice, instant translation) so the redirect never feels abrupt.
 *
 * Fully theme-aware via [NeonColors]; the primary CTA carries the brand gradient.
 *
 * @param userName  Matched person's name, used to personalize the copy. Optional.
 * @param onConfirm Invoked when the user chooses to continue to WalkTalk.
 * @param onDismiss Invoked on "Geri" or when the dialog is dismissed.
 */
@Composable
fun WalkTalkRedirectDialog(
    userName: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(NeonColors.Background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Gradient badge
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(BrandGradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.chat_round_line_svgrepo_com),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = if (userName.isNullOrBlank()) {
                    stringResource(Res.string.walktalk_dialog_title)
                } else {
                    stringResource(Res.string.walktalk_dialog_title_named, userName)
                },
                style = MaterialTheme.typography.titleLarge,
                color = NeonColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = stringResource(Res.string.walktalk_dialog_body),
                style = MaterialTheme.typography.bodyMedium,
                color = NeonColors.TextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))

            // Feature highlights
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(NeonColors.InputBackground)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FeatureRow("🌍", stringResource(Res.string.walktalk_dialog_feature_translation))
                FeatureRow("🎙️", stringResource(Res.string.walktalk_dialog_feature_voice))
                FeatureRow("✨", stringResource(Res.string.walktalk_dialog_feature_quality))
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, NeonColors.CardBorder),
                ) {
                    Text(
                        text = stringResource(Res.string.walktalk_dialog_back),
                        color = NeonColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                NeonPrimaryButton(
                    text = stringResource(Res.string.walktalk_dialog_confirm),
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(emoji: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = emoji, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = NeonColors.TextPrimary,
        )
    }
}
