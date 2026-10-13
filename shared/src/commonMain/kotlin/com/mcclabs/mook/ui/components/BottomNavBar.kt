package com.mcclabs.mook.ui.components

import com.mcclabs.mook.ui.theme.LocalAppColors
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
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
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.app_logo_transparent
import mook.shared.generated.resources.ic_crown
import mook.shared.generated.resources.ic_star
import mook.shared.generated.resources.ic_user
import mook.shared.generated.resources.nav_discover
import mook.shared.generated.resources.nav_liked
import mook.shared.generated.resources.nav_chats
import mook.shared.generated.resources.chat_round_line_svgrepo_com
import mook.shared.generated.resources.nav_subscription
import mook.shared.generated.resources.nav_profile
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Alt gezinme, henüz tüketilmemiş yatay ve alt güvenli alanı etkileşimli öğelerden ayırır. */
@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    enabledRoutes: Set<String> = setOf("discover", "liked", "chats", "paywall", "profile"),
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
) {
    Row(
        modifier = modifier
            .testTag("bottomNavigationBar")
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(windowInsets)
            .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
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
            // Logonun kendi renkleri korunur.
            tintIcon = false
        )
        BottomNavItem(
            icon = Res.drawable.ic_star,
            label = stringResource(Res.string.nav_liked),
            route = "liked",
            currentRoute = currentRoute,
            isEnabled = "liked" in enabledRoutes,
            onNavigate = onNavigate
        )
        BottomNavItem(
            icon = Res.drawable.chat_round_line_svgrepo_com,
            label = stringResource(Res.string.nav_chats),
            route = "chats",
            currentRoute = currentRoute,
            isEnabled = "chats" in enabledRoutes,
            onNavigate = onNavigate
        )
        BottomNavItem(
            icon = Res.drawable.ic_crown,
            label = stringResource(Res.string.nav_subscription),
            route = "paywall",
            currentRoute = currentRoute,
            isEnabled = "paywall" in enabledRoutes,
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
private fun RowScope.BottomNavItem(
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
        !isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        isSelected -> LocalAppColors.current.onGradient
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // Seçili sekmede simgeler degrade içerik rengini kullanır.
    val iconTint = when {
        isSelected -> LocalAppColors.current.onGradient
        tintIcon -> contentColor
        else -> Color.Unspecified
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .testTag("bottomNavItem$route")
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .then(if (isSelected) Modifier.background(BrandGradient) else Modifier)
            .selectable(selected = isSelected, enabled = isEnabled, role = Role.Tab) { onNavigate(route) }
            .padding(horizontal = 8.dp, vertical = 8.dp)
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
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
