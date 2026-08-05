package com.mcclabs.mook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mcclabs.mook.domain.model.Language
import com.mcclabs.mook.ui.theme.NeonColors

/**
 * A dropdown selector for choosing a [Language].
 *
 * Displays the currently selected language's flag emoji and name,
 * or a placeholder when nothing is selected. Tapping opens a
 * [DropdownMenu] listing all available [languages].
 *
 * @param label              Descriptor shown above the selector (e.g. "Native Language").
 * @param selectedLanguage   The currently selected [Language], or `null`.
 * @param onLanguageSelected Callback when a language is chosen from the menu.
 * @param languages          Full list of available languages.
 * @param modifier           Optional [Modifier].
 */
@Composable
fun LanguageDropdown(
    label: String,
    selectedLanguage: Language?,
    onLanguageSelected: (Language) -> Unit,
    languages: List<Language>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Label
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = NeonColors.TextSecondary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Selector row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NeonColors.InputBackground)
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectedLanguage != null) {
                Text(
                    text = selectedLanguage.flagEmoji,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = selectedLanguage.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = NeonColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    text = "Select…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonColors.TextTertiary,
                    modifier = Modifier.weight(1f),
                )
            }

            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Expand",
                tint = NeonColors.TextSecondary,
            )
        }

        // Dropdown menu
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(NeonColors.InputBackground),
        ) {
            languages.forEach { language ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = language.flagEmoji,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = language.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = NeonColors.TextPrimary,
                            )
                        }
                    },
                    onClick = {
                        onLanguageSelected(language)
                        expanded = false
                    },
                )
            }
        }
    }
}
