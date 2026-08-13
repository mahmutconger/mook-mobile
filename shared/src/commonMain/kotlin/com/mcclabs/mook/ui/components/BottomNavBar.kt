package com.mcclabs.mook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mcclabs.mook.ui.theme.BrandGradient
import com.mcclabs.mook.ui.theme.NeonColors
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.app_logo_transparent
import mook.shared.generated.resources.ic_crown
import mook.shared.generated.resources.ic_star
import mook.shared.generated.resources.ic_user
import mook.shared.generated.resources.nav_discover
import mook.shared.generated.resources.nav_liked
import mook.shared.generated.resources.nav_subscription
import mook.shared.generated.resources.nav_profile
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The main bottom navigation bar.
 *
 * @param enabledRoutes Routes that have a destination. Tabs outside this set are dimmed
 *   and cannot be tapped, so the bar never offers a button that leads nowhere.
 */
@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    enabledRoutes: Set<String> = setOf("discover", "chats", "voices", "profile")
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonColors.Background)
            .border(width = 1.dp, color = NeonColors.CardBorder)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomNavItem(
            icon = Res.drawable.app_logo_transparent,
            label = stringResource(Res.string.nav_discover),
            route = "discover",
            currentRoute = currentRoute,
            isEnabled = "discover" in enabledRoutes,
            onNavigate = onNavigate,
            // The app logo carries its own colours, so it must not be tinted like a glyph.
            tintIcon = false
        )
        BottomNavItem(
            icon = Res.drawable.ic_star,
            label = stringResource(Res.string.nav_liked),
            route = "chats",
            currentRoute = currentRoute,
            isEnabled = "chats" in enabledRoutes,
            onNavigate = onNavigate
        )
        BottomNavItem(
            icon = Res.drawable.ic_crown,
            label = stringResource(Res.string.nav_subscription),
            route = "voices",
            currentRoute = currentRoute,
            isEnabled = "voices" in enabledRoutes,
            onNavigate = onNavigate
        )
        BottomNavItem(
            icon = Res.drawable.ic_user,
            label = stringResource(Res.string.nav_profile),
            route = "profile",
            currentRoute = currentRoute,
            isEnabled = "profile" in enabledRoutes,
            onNavigate = onNavigate
        )
    }
}

@Composable
private fun BottomNavItem(
    icon: DrawableResource,
    label: String,
    route: String,
    currentRoute: String,
    isEnabled: Boolean,
    onNavigate: (String) -> Unit,
    tintIcon: Boolean = true
) {
    val isSelected = currentRoute == route
    val contentColor = when {
        !isEnabled -> NeonColors.TextTertiary.copy(alpha = 0.4f)
        isSelected -> Color.White
        else -> NeonColors.TextTertiary
    }
    // On the active gradient pill everything (even the multi-colour logo) turns white
    // for contrast; off the pill the logo keeps its own colours.
    val iconTint = when {
        isSelected -> Color.White
        tintIcon -> contentColor
        else -> Color.Unspecified
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(if (isSelected) Modifier.background(BrandGradient) else Modifier)
            .clickable(enabled = isEnabled) { onNavigate(route) }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
