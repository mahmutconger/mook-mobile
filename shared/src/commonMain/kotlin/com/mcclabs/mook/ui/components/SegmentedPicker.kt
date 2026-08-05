package com.mcclabs.mook.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mcclabs.mook.ui.theme.NeonColors

/**
 * A neon-styled segmented control that lets the user pick one option from a
 * small fixed set (e.g. gender: Male / Female / Other).
 *
 * @param options       Ordered list of (value, label) pairs to display as segments.
 * @param selectedValue The currently selected value, or `null` when nothing is chosen.
 * @param onSelected    Callback invoked with the chosen value.
 * @param modifier      Optional [Modifier].
 */
@Composable
fun <T> SegmentedPicker(
    options: List<Pair<T, String>>,
    selectedValue: T?,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NeonColors.InputBackground)
            .padding(4.dp),
    ) {
        options.forEach { (value, label) ->
            val selected = value == selectedValue
            val bg by animateColorAsState(
                if (selected) NeonColors.Primary else Color.Transparent
            )
            val textColor by animateColorAsState(
                if (selected) NeonColors.Background else NeonColors.TextSecondary
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .clickable { onSelected(value) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = textColor,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
