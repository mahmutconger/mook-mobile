package com.mcclabs.mook.ui.loading

import com.mcclabs.mook.ui.theme.LocalAppColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val LocalShimmerProgress = staticCompositionLocalOf<State<Float>?> { null }

@Composable
private fun rememberShimmerProgress(): State<Float> {
    val transition = rememberInfiniteTransition(label = "shimmer")
    return transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerProgress",
    )
}

/** Animasyon değeri çizim sırasında okunur; her karede yeniden kompozisyon yapılmaz. */
@Composable
fun Modifier.shimmer(): Modifier {
    val progress = LocalShimmerProgress.current ?: rememberShimmerProgress()
    val base = MaterialTheme.colorScheme.surfaceContainerLow
    val highlight = LocalAppColors.current.shimmerHighlight
    return drawWithCache {
        onDrawBehind {
            val width = size.width.coerceAtLeast(1f)
            val start = width * progress.value
            drawRect(base)
            drawRect(Brush.linearGradient(
                colors = listOf(Color.Transparent, highlight, Color.Transparent),
                start = Offset(start, 0f),
                end = Offset(start + width, size.height),
            ))
        }
    }
}

@Composable
fun ShimmerPlaceholder(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    Box(modifier.clip(shape).shimmer().testTag("shimmerPlaceholder"))
}

enum class SkeletonLayout { Discover, Likes, LikedMe, Chats, Profile }

/** Ekran başlığı ve gezinme kullanılabilir kalırken içerik yerleşimi önceden gösterilir. */
@Composable
fun LoadingSkeleton(layout: SkeletonLayout, modifier: Modifier = Modifier) {
    if (!progressiveLoadingEnabled) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }
    val progress = rememberShimmerProgress()
    CompositionLocalProvider(LocalShimmerProgress provides progress) {
        Box(modifier.fillMaxSize().testTag("skeleton${layout.name}").semantics {
            contentDescription = "İçerik yükleniyor"
        }) {
            when (layout) {
                SkeletonLayout.Discover -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(6) {
                        Box(Modifier.fillMaxWidth().aspectRatio(0.74f).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
                            ShimmerPlaceholder(Modifier.fillMaxSize())
                            Column(Modifier.align(Alignment.BottomStart).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ShimmerPlaceholder(Modifier.fillMaxWidth(0.75f).height(18.dp))
                                ShimmerPlaceholder(Modifier.fillMaxWidth(0.5f).height(12.dp))
                            }
                        }
                    }
                }
                SkeletonLayout.Profile -> Column {
                    ShimmerPlaceholder(Modifier.fillMaxWidth().weight(1f), RoundedCornerShape(0.dp))
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        SkeletonRow()
                        ShimmerPlaceholder(Modifier.width(110.dp).height(20.dp))
                        repeat(3) { ShimmerPlaceholder(Modifier.fillMaxWidth(if (it == 2) 0.65f else 1f).height(14.dp)) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(3) { ShimmerPlaceholder(Modifier.width(84.dp).height(30.dp), RoundedCornerShape(50)) }
                        }
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (layout == SkeletonLayout.LikedMe) item {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ShimmerPlaceholder(Modifier.size(32.dp), CircleShape)
                            ShimmerPlaceholder(Modifier.width(180.dp).height(24.dp))
                            ShimmerPlaceholder(Modifier.width(140.dp).height(14.dp))
                        }
                    }
                    items(7) { SkeletonRow(showTimestamp = layout == SkeletonLayout.Chats) }
                }
            }
        }
    }
}

@Composable
private fun SkeletonRow(showTimestamp: Boolean = false) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        ShimmerPlaceholder(Modifier.size(56.dp), CircleShape)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ShimmerPlaceholder(Modifier.fillMaxWidth(0.65f).height(18.dp))
            ShimmerPlaceholder(Modifier.fillMaxWidth(0.9f).height(13.dp))
        }
        if (showTimestamp) {
            Spacer(Modifier.width(12.dp))
            ShimmerPlaceholder(Modifier.width(32.dp).height(12.dp))
        }
    }
}
