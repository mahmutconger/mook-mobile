package com.mcclabs.mook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import com.mcclabs.mook.domain.model.Country
import com.mcclabs.mook.ui.theme.NeonColors
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.dropdown_select
import mook.shared.generated.resources.dropdown_expand_cd
import mook.shared.generated.resources.filters_search_country
import org.jetbrains.compose.resources.stringResource

/**
 * A searchable dropdown selector for choosing a [Country].
 *
 * Displays the currently selected country's localized name, or a placeholder
 * when nothing is selected. Tapping opens a [DropdownMenu] with a search field
 * that filters the (typically ~250 item) list by localized name.
 *
 * @param label             Descriptor shown above the selector (e.g. "Country").
 * @param selectedCountry   The currently selected [Country], or `null`.
 * @param onCountrySelected Callback when a country is chosen from the menu.
 * @param countries         Full list of available countries (localized, sorted).
 * @param modifier          Optional [Modifier].
 */
@Composable
fun CountryDropdown(
    label: String,
    selectedCountry: Country?,
    onCountrySelected: (Country) -> Unit,
    countries: List<Country>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val filtered = remember(query, countries) {
        if (query.isBlank()) countries
        else countries.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = NeonColors.TextSecondary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NeonColors.InputBackground)
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedCountry?.name ?: stringResource(Res.string.dropdown_select),
                style = MaterialTheme.typography.bodyLarge,
                color = if (selectedCountry != null) NeonColors.TextPrimary else NeonColors.TextTertiary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(Res.string.dropdown_expand_cd),
                tint = NeonColors.TextSecondary,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                query = ""
            },
            modifier = Modifier
                .background(NeonColors.InputBackground)
                .heightIn(max = 360.dp),
        ) {
            // Search box at the top of the menu.
            CustomAuthTextField(
                value = query,
                onValueChange = { query = it },
                label = "",
                placeholder = stringResource(Res.string.filters_search_country),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )

            filtered.forEach { country ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = country.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeonColors.TextPrimary,
                        )
                    },
                    onClick = {
                        onCountrySelected(country)
                        expanded = false
                        query = ""
                    },
                )
            }
        }
    }
}
