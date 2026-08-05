package com.mcclabs.mook.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mcclabs.mook.ui.theme.NeonColors

/**
 * A selectable neon-styled chip, typically used for interest / tag selection.
 *
 * Visual states:
 * - **Selected**: 15 % alpha cyan background, cyan border, cyan text.
 * - **Unselected**: transparent background, card-border, secondary text.
 *
 * @param text       Chip label.
 * @param isSelected Whether the chip is currently selected.
 * @param onToggle   Called when the chip is tapped to toggle selection. Pass `null` for a
 *                   read-only chip, which is not clickable.
 * @param modifier   Optional [Modifier].
 */
@Composable
fun NeonChip(
    text: String,
    isSelected: Boolean = false,
    onToggle: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (isSelected) {
        NeonColors.Primary.copy(alpha = 0.15f)
    } else {
        NeonColors.Background
    }

    val borderColor = if (isSelected) NeonColors.Primary else NeonColors.CardBorder
    val textColor = if (isSelected) NeonColors.Primary else NeonColors.TextSecondary

    Surface(
        modifier = if (onToggle != null) modifier.clickable(onClick = onToggle) else modifier,
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
