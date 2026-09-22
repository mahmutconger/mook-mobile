package com.mcclabs.mook.feature.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mcclabs.mook.domain.model.DiscoverProfile
import com.mcclabs.mook.ui.theme.BrandGradient
import com.mcclabs.mook.ui.theme.NeonColors
import com.mcclabs.mook.util.countryCodeToFlagEmoji
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.discover_block_user
import mook.shared.generated.resources.discover_card_like_cd
import mook.shared.generated.resources.discover_card_more_cd
import mook.shared.generated.resources.discover_card_pass_cd
import mook.shared.generated.resources.discover_online
import mook.shared.generated.resources.discover_report_profile
import org.jetbrains.compose.resources.stringResource

/** Portrait proportion of one grid tile — tall enough to read as a photo, short enough to pair. */
private const val CARD_ASPECT_RATIO = 0.74f

/** Scrim behind the overlay icon buttons, so they stay legible against any photo. */
private val IconScrim = Color.Black.copy(alpha = 0.35f)

/**
 * One person in the Discovery grid: their photo, name and age, whether they are online, and
 * where they are from. Tapping opens the full profile; the heart likes them without leaving
 * the grid, and the overflow carries the report/block affordances.
 */
@Composable
fun ProfileGridCard(
    profile: DiscoverProfile,
    nowMillis: Long,
    onClick: () -> Unit,
    onLike: () -> Unit,
    onPass: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(CARD_ASPECT_RATIO)
            .clip(shape)
            .background(NeonColors.Card)
            .clickable(onClick = onClick)
    ) {
        val photo = profile.photoUrls.firstOrNull()
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // No usable photo: a branded tile with the initial reads far better than a
            // broken-image box, and keeps the grid rhythm intact.
            Box(
                modifier = Modifier.fillMaxSize().background(BrandGradient),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profile.name.take(1).uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Keeps the caption readable over light photos.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
        )

        // ── Overflow: report / block ───────────────────────────────────────
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(IconScrim)
                    .clickable { menuExpanded = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(Res.string.discover_card_more_cd),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(Res.string.discover_report_profile),
                            color = NeonColors.TextPrimary
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onReport()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(Res.string.discover_block_user),
                            color = NeonColors.Error
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onBlock()
                    }
                )
            }
        }

        // ── Caption + Pass / like ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.age?.let { "${profile.name}, $it" } ?: profile.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (profile.isOnline(nowMillis)) {
                        Spacer(modifier = Modifier.width(6.dp))
                        OnlineBadge()
                    }
                }

                val country = profile.country
                if (country != null) {
                    CountryBadge(code = country.code, name = country.name)
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(IconScrim)
                        .clickable(onClick = onPass),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.discover_card_pass_cd),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(BrandGradient)
                        .clickable(onClick = onLike),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(Res.string.discover_card_like_cd),
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

/** Green dot + "Online", shown only while the person counts as present. */
@Composable
private fun OnlineBadge() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(NeonColors.Success)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(Res.string.discover_online),
            color = NeonColors.Success,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/** Flag + country name pill. Falls back to the name alone when the code has no flag. */
@Composable
private fun CountryBadge(code: String, name: String) {
    val flag = countryCodeToFlagEmoji(code)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (flag != null) {
            Text(text = flag, style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = name,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
