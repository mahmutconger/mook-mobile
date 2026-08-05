package com.mcclabs.mook.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mcclabs.mook.ui.theme.NeonColors

/** The predefined report reasons, in display order. "Underage" is kept for CSAE reports. */
val ReportReasons: List<String> = listOf(
    "Spam",
    "Harassment",
    "Inappropriate Photo",
    "Underage",
    "Other",
)

/**
 * A modal bottom sheet for reporting a user, with the predefined [ReportReasons].
 *
 * Shared by the Discover card and the profile screen so the reporting flow is
 * identical everywhere. Submit is disabled until a reason is chosen.
 *
 * @param selectedReason   The currently chosen reason, or `null`.
 * @param onReasonSelected Called when a reason row is tapped.
 * @param onSubmit         Called when the user confirms the report.
 * @param onDismiss        Called when the sheet is dismissed without submitting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportBottomSheet(
    selectedReason: String?,
    onReasonSelected: (String) -> Unit,
    onSubmit: () -> Unit,
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
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "Report User",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = NeonColors.TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Why are you reporting this user?",
                style = MaterialTheme.typography.bodyMedium,
                color = NeonColors.TextSecondary,
            )
            Spacer(Modifier.height(12.dp))

            ReportReasons.forEach { reason ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onReasonSelected(reason) }
                        .padding(vertical = 6.dp)
                ) {
                    RadioButton(
                        selected = selectedReason == reason,
                        onClick = { onReasonSelected(reason) },
                        colors = RadioButtonDefaults.colors(selectedColor = NeonColors.Primary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(reason, color = NeonColors.TextPrimary)
                }
            }

            Spacer(Modifier.height(16.dp))
            NeonPrimaryButton(
                text = "Submit Report",
                onClick = onSubmit,
                enabled = selectedReason != null,
            )
        }
    }
}
