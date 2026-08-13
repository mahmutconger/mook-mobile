package com.mcclabs.mook.feature.filters

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import com.mcclabs.mook.domain.model.Country
import com.mcclabs.mook.ui.components.CustomRangeSlider
import com.mcclabs.mook.ui.components.NeonPrimaryButton
import com.mcclabs.mook.ui.theme.BrandGradient
import com.mcclabs.mook.ui.theme.NeonColors
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.calendar_svgrepo_com
import mook.shared.generated.resources.flag_svgrepo_com
import mook.shared.generated.resources.ic_settings_minimalistic
import mook.shared.generated.resources.search_normal_1_svgrepo_com
import mook.shared.generated.resources.filters_title
import mook.shared.generated.resources.filters_subtitle
import mook.shared.generated.resources.filters_age_range
import mook.shared.generated.resources.filters_reset
import mook.shared.generated.resources.filters_apply
import mook.shared.generated.resources.filters_target_country
import mook.shared.generated.resources.filters_any_country
import mook.shared.generated.resources.filters_selected_count
import mook.shared.generated.resources.filters_search_country
import mook.shared.generated.resources.filters_no_countries
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

/** How many search matches to show at once (keeps the drawer compact). */
private const val MAX_COUNTRY_RESULTS = 8

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FiltersScreen(
    onNavigateBack: () -> Unit,
    viewModel: FiltersViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonColors.Background)
            .padding(horizontal = 20.dp)
            .padding(top = 40.dp, bottom = 16.dp)
    ) {
        // ---- Header -------------------------------------------------------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(BrandGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_settings_minimalistic),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = stringResource(Res.string.filters_title),
                    color = NeonColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(Res.string.filters_subtitle),
                    color = NeonColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (state.isLoading) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = NeonColors.Primary)
            }
        } else {
            // ---- Scrollable content --------------------------------------
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CountrySection(
                    selected = state.settings.targetCountries,
                    available = state.availableCountries,
                    onAdd = { viewModel.addCountry(it) },
                    onRemove = { viewModel.removeCountry(it) }
                )

                FilterSection(
                    icon = Res.drawable.calendar_svgrepo_com,
                    title = stringResource(Res.string.filters_age_range),
                    trailing = {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(NeonColors.Primary.copy(alpha = 0.12f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${state.settings.ageRangeStart} – ${state.settings.ageRangeEnd}",
                                color = NeonColors.Primary,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                ) {
                    CustomRangeSlider(
                        value = state.settings.ageRangeStart.toFloat()..state.settings.ageRangeEnd.toFloat(),
                        onValueChange = { range ->
                            viewModel.updateAgeRange(range.start.roundToInt(), range.endInclusive.roundToInt())
                        },
                        valueRange = 18f..100f
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("18", color = NeonColors.TextTertiary, style = MaterialTheme.typography.bodySmall)
                        Text("100", color = NeonColors.TextTertiary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // ---- Sticky action bar ---------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.onReset() },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, NeonColors.CardBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonColors.TextPrimary)
                ) {
                    Text(stringResource(Res.string.filters_reset), fontWeight = FontWeight.SemiBold)
                }

                NeonPrimaryButton(
                    text = stringResource(Res.string.filters_apply),
                    onClick = { viewModel.onApply(onNavigateBack) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Country filter: searchable over the full localized ISO list. Selected countries
 * appear as removable gradient chips; typing filters the list into tappable rows.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CountrySection(
    selected: List<String>,
    available: List<Country>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }

    val results = remember(query, available, selected) {
        val q = query.trim()
        if (q.isBlank()) emptyList()
        else available.asSequence()
            .filter { it.name.contains(q, ignoreCase = true) && it.name !in selected }
            .take(MAX_COUNTRY_RESULTS)
            .toList()
    }

    FilterSection(
        icon = Res.drawable.flag_svgrepo_com,
        title = stringResource(Res.string.filters_target_country),
        subtitle = if (selected.isEmpty()) stringResource(Res.string.filters_any_country)
        else stringResource(Res.string.filters_selected_count, selected.size)
    ) {
        if (selected.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selected.forEach { country ->
                    RemovableChip(text = country, onRemove = { onRemove(country) })
                }
            }
        }

        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(Res.string.filters_search_country)
        )

        if (query.isNotBlank()) {
            if (results.isEmpty()) {
                Text(
                    text = stringResource(Res.string.filters_no_countries),
                    color = NeonColors.TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    results.forEach { country ->
                        ResultRow(
                            text = country.name,
                            onClick = {
                                onAdd(country.name)
                                query = ""
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * A titled card grouping one filter control, styled to match the Mook design
 * language (rounded surface, hairline border, icon-led header). Theme-aware via
 * [NeonColors] tokens.
 */
@Composable
private fun FilterSection(
    icon: DrawableResource,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NeonColors.Card)
            .border(1.dp, NeonColors.CardBorder, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = NeonColors.Primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = NeonColors.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = NeonColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            trailing?.invoke()
        }
        content()
    }
}

/** Compact search field styled with the input-fill token (theme-aware). */
@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NeonColors.InputBackground)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(Res.drawable.search_normal_1_svgrepo_com),
            contentDescription = null,
            tint = NeonColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = NeonColors.TextTertiary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = NeonColors.TextPrimary),
                cursorBrush = SolidColor(NeonColors.Primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** A tappable search-result row that adds the item on click. */
@Composable
private fun ResultRow(
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = NeonColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "+",
            color = NeonColors.Primary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/** A selected pill filled with the brand gradient, with a tap-to-remove "×". */
@Composable
private fun RemovableChip(
    text: String,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(BrandGradient)
            .clickable(onClick = onRemove)
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "×",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
