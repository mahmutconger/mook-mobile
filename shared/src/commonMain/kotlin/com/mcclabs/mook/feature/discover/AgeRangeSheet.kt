package com.mcclabs.mook.feature.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mcclabs.mook.ui.components.CustomRangeSlider
import com.mcclabs.mook.ui.components.NeonPrimaryButton
import com.mcclabs.mook.ui.theme.NeonColors
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.discover_age_chip_value
import mook.shared.generated.resources.filters_age_range
import mook.shared.generated.resources.filters_apply
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Bounds of the age filter, matching the drawer's slider so the two stay in step. */
private const val MIN_AGE = 18f
private const val MAX_AGE = 100f

/**
 * The age chip's editor. Deliberately a sheet rather than a dropdown: a range needs two
 * handles and room to drag, and this keeps the grid visible behind it.
 *
 * Nothing is saved until Apply, so dragging does not fire a query per pixel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgeRangeSheet(
    start: Int,
    end: Int,
    onRangeChange: (Int, Int) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NeonColors.Background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.filters_age_range),
                    color = NeonColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(Res.string.discover_age_chip_value, start, end),
                    color = NeonColors.Primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            CustomRangeSlider(
                value = start.toFloat()..end.toFloat(),
                onValueChange = { range ->
                    // Keep at least a one-year span so the two handles cannot cross into an
                    // empty range that matches nobody.
                    val low = range.start.roundToInt()
                    val high = range.endInclusive.roundToInt()
                    onRangeChange(low, maxOf(high, low))
                },
                valueRange = MIN_AGE..MAX_AGE,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = MIN_AGE.roundToInt().toString(),
                    color = NeonColors.TextTertiary,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = MAX_AGE.roundToInt().toString(),
                    color = NeonColors.TextTertiary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            NeonPrimaryButton(
                text = stringResource(Res.string.filters_apply),
                onClick = onApply,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
