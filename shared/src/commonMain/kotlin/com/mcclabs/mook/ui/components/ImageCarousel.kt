package com.mcclabs.mook.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mcclabs.mook.ui.theme.NeonColors
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.ic_user
import mook.shared.generated.resources.carousel_no_photos
import mook.shared.generated.resources.carousel_photo_cd
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ImageCarousel(imageUrls: List<String>, modifier: Modifier = Modifier) {
    if (imageUrls.isEmpty()) {
        EmptyPhotoPlaceholder(modifier)
        return
    }

    val pagerState = rememberPagerState(pageCount = { imageUrls.size })

    Box(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            AsyncImage(
                model = imageUrls[page],
                contentDescription = stringResource(Res.string.carousel_photo_cd, page + 1),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .background(NeonColors.Surface)
            )
        }

        // A single photo needs no page indicator. The dots sit at the TOP so an
        // overlapping details sheet (as in ProfileDetails) can't hide them, and the
        // active dot stretches into a pill — a tab-like cue that more photos exist.
        if (imageUrls.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 12.dp)
                    // Translucent gray-black pill so the indicator stays visible over
                    // light photos.
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                for (i in imageUrls.indices) {
                    val isSelected = pagerState.currentPage == i
                    val dotWidth by animateDpAsState(if (isSelected) 20.dp else 8.dp)
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(8.dp)
                            .width(dotWidth)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isSelected) Color.White else Color.White.copy(alpha = 0.4f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPhotoPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(NeonColors.Card),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(Res.drawable.ic_user),
                contentDescription = null,
                tint = NeonColors.TextTertiary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.carousel_no_photos),
                style = MaterialTheme.typography.bodyMedium,
                color = NeonColors.TextTertiary
            )
        }
    }
}
