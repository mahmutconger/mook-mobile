package com.mcclabs.mook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.premium_badge
import org.jetbrains.compose.resources.stringResource

/** Altın tonlu Premium rozeti renkleri (tema renginden bağımsız, her iki modda okunaklı). */
private val PremiumGold = Color(0xFFF5B301)
private val PremiumAmber = Color(0xFFFF8A00)

/**
 * Premium abonelerin profillerinde (Keşfet kartı, profil ayrıntısı) gösterilen rozet.
 * Kademe bilgisi sunucudan gelir (`public_profiles.subscriptionTier`); istemci tarafından
 * değiştirilemez.
 */
@Composable
fun PremiumBadge(modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(PremiumGold, PremiumAmber)))
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 2.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(if (compact) 12.dp else 14.dp))
        if (!compact) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(Res.string.premium_badge),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
