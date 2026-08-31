package com.mcclabs.mook.feature.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mcclabs.mook.domain.model.Language
import com.mcclabs.mook.ui.theme.NeonColors
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.discover_age_chip_value
import mook.shared.generated.resources.discover_chip_age
import mook.shared.generated.resources.room_language_independent
import mook.shared.generated.resources.room_switch_title
import org.jetbrains.compose.resources.stringResource

/**
 * The quick-filter row above the grid: which room the user is browsing, and the age range.
 *
 * Room replaces what would be a country filter in a dating app — here the room *is* the
 * audience, so tapping it goes to the room switcher rather than opening a picker inline.
 * Everything finer-grained (country, and the same age range) stays in the filters drawer.
 */
@Composable
fun DiscoveryFilterBar(
    roomLanguage: Language?,
    isLanguageIndependentRoom: Boolean,
    ageRangeStart: Int,
    ageRangeEnd: Int,
    onRoomClick: () -> Unit,
    onAgeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val roomLabel = when {
        roomLanguage != null -> "${roomLanguage.flagEmoji} ${roomLanguage.name}"
        isLanguageIndependentRoom -> "🌐 " + stringResource(Res.string.room_language_independent)
        // No room stored yet (the gate normally prevents this) — offer the action instead.
        else -> stringResource(Res.string.room_switch_title)
    }

    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            FilterChip(label = roomLabel, onClick = onRoomClick)
        }
        item {
            FilterChip(
                label = stringResource(Res.string.discover_chip_age) + " " +
                    stringResource(Res.string.discover_age_chip_value, ageRangeStart, ageRangeEnd),
                onClick = onAgeClick
            )
        }
    }
}

/** A tappable pill with a trailing chevron, styled off the card tokens so it works in both themes. */
@Composable
private fun FilterChip(
    label: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(NeonColors.Card)
            .border(1.dp, NeonColors.CardBorder, shape)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = NeonColors.TextPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(2.dp))
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = NeonColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}
