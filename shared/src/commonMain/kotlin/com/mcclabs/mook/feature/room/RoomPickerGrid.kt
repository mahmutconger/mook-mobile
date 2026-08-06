package com.mcclabs.mook.feature.room

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mcclabs.mook.domain.model.Language
import com.mcclabs.mook.ui.theme.BrandGradient
import com.mcclabs.mook.ui.theme.NeonColors

/**
 * A grid of language rooms, each shown as a flag + name card. The currently selected
 * room (if any) is highlighted with the brand gradient. Shared by [RoomGateScreen] and
 * [RoomSwitchScreen] so both pickers stay visually identical.
 */
@Composable
fun RoomPickerGrid(
    languages: List<Language>,
    selectedCode: String?,
    onSelect: (Language) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(languages, key = { it.code }) { language ->
            val isSelected = language.code.equals(selectedCode, ignoreCase = true)
            RoomCard(language = language, isSelected = isSelected, onClick = { onSelect(language) })
        }
    }
}

@Composable
private fun RoomCard(
    language: Language,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (isSelected) {
                    Modifier.background(BrandGradient)
                } else {
                    Modifier
                        .background(NeonColors.Card)
                        .border(1.dp, NeonColors.CardBorder, shape)
                }
            )
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp, horizontal = 8.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = language.flagEmoji, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = language.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) androidx.compose.ui.graphics.Color.White else NeonColors.TextPrimary,
            textAlign = TextAlign.Center
        )
    }
}
