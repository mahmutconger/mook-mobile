package com.mcclabs.mook.feature.walktalkdemo

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mcclabs.mook.domain.translation.TranslationError
import com.mcclabs.mook.ui.theme.NeonColors
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.wtdemo_bubble_cd
import mook.shared.generated.resources.wtdemo_retry
import mook.shared.generated.resources.wtdemo_translating
import mook.shared.generated.resources.wtdemo_typing
import org.jetbrains.compose.resources.stringResource

/**
 * One transcript bubble.
 *
 * The **translation is the hero**: it takes the large, high-contrast line, and the
 * original sits underneath in a muted caption — that ordering is the whole point of
 * the demo. While the request is in flight the hero slot keeps its typography and
 * renders a pulsing "translating…" placeholder, so resolving the translation swaps
 * text without changing the bubble's shape.
 *
 * @param message   The bubble to render.
 * @param onRetry   Invoked when the user taps retry on a failed bubble.
 * @param errorText Maps a [TranslationError] onto display copy. Passed in rather than
 *   resolved here so that mapping lives in exactly one place on the screen.
 */
@Composable
fun ChatBubble(
    message: DemoMessage,
    onRetry: () -> Unit,
    errorText: @Composable (TranslationError) -> String,
    modifier: Modifier = Modifier,
) {
    val isRight = message.side == ChatSide.RIGHT
    val bubbleShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (isRight) 20.dp else 4.dp,
        bottomEnd = if (isRight) 4.dp else 20.dp,
    )
    val fill = if (isRight) NeonColors.Primary.copy(alpha = 0.10f) else NeonColors.Card
    val translatingLabel = stringResource(Res.string.wtdemo_translating)

    // The translation and its original are one utterance to a screen reader, not two
    // stray fragments.
    val spoken = stringResource(
        Res.string.wtdemo_bubble_cd,
        when (val translation = message.translation) {
            is BubbleTranslation.Done -> translation.text
            is BubbleTranslation.InFlight -> translatingLabel
            is BubbleTranslation.Failed -> errorText(translation.error)
        },
        message.originalText,
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isRight) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(bubbleShape)
                .background(fill)
                .border(1.dp, NeonColors.CardBorder, bubbleShape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            // Only the text is collapsed into one description; the retry button below
            // stays outside it so it keeps its own accessible action.
            Column(
                modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
            ) {
                when (val translation = message.translation) {
                    is BubbleTranslation.InFlight -> PulsingText(text = translatingLabel)

                    is BubbleTranslation.Done -> Text(
                        text = translation.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = NeonColors.TextPrimary,
                        fontWeight = FontWeight.Medium,
                    )

                    is BubbleTranslation.Failed -> Text(
                        text = errorText(translation.error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonColors.Error,
                    )
                }

                Spacer(Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = message.sourceCode,
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonColors.TextTertiary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = message.originalText,
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonColors.TextTertiary,
                    )
                }
            }

            if (message.translation is BubbleTranslation.Failed) {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = NeonColors.Primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(Res.string.wtdemo_retry),
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonColors.Primary,
                    )
                }
            }
        }
    }
}

/**
 * The "someone is typing" strip shown under the transcript while any bubble is still
 * resolving. Decorative only — the bubbles themselves carry the accessible state, so
 * this would otherwise just repeat itself at the reader.
 */
@Composable
fun TypingIndicator(
    languageName: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(NeonColors.InputBackground)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulsingText(text = stringResource(Res.string.wtdemo_typing, languageName))
    }
}

/** A label that breathes between full and half opacity — the "live" cue, at no layout cost. */
@Composable
private fun PulsingText(text: String, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = NeonColors.TextSecondary,
        modifier = modifier.alpha(pulseAlpha),
    )
}
