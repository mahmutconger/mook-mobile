package com.mcclabs.mook.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mcclabs.mook.ui.theme.NeonColors

/**
 * A glassmorphism-styled card with a translucent dark background,
 * subtle border, and generous internal padding.
 *
 * Use this card as a container for grouped UI elements that need
 * visual separation from the main background.
 *
 * @param modifier Optional [Modifier] applied to the card.
 * @param content  Column-scoped composable content rendered inside the card.
 */
@Composable
fun GlassmorphismCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = NeonColors.GlassBackground,
        ),
        border = BorderStroke(1.dp, NeonColors.CardBorder),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            content = content,
        )
    }
}
