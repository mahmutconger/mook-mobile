package com.mcclabs.mook.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mcclabs.mook.ui.theme.NeonColors

/**
 * Horizontal row of dots indicating multi-step progress.
 *
 * The active dot is larger (10 dp) and uses [NeonColors.Primary],
 * while inactive dots are smaller (8 dp) and use [NeonColors.TextTertiary].
 * Size and color transitions are smoothly animated.
 *
 * @param totalSteps  Total number of steps.
 * @param currentStep Zero-indexed active step.
 * @param modifier    Optional [Modifier].
 */
@Composable
fun StepIndicator(
    totalSteps: Int,
    currentStep: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(totalSteps) { index ->
            val isActive = index == currentStep

            val dotSize by animateDpAsState(
                targetValue = if (isActive) 10.dp else 8.dp,
                animationSpec = tween(durationMillis = 300),
                label = "stepDotSize",
            )

            val dotColor by animateColorAsState(
                targetValue = if (isActive) NeonColors.Primary else NeonColors.TextTertiary,
                animationSpec = tween(durationMillis = 300),
                label = "stepDotColor",
            )

            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
    }
}
