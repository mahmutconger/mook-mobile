package com.mcclabs.mook.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mcclabs.mook.domain.model.DiscoverProfile
import com.mcclabs.mook.ui.theme.NeonColors
import kotlinx.coroutines.launch
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.ic_check_circle
import mook.shared.generated.resources.ic_user
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

// Card palette, aligned to the MOOK light tokens.
private val CardSurface = Color.White
private val CardName = NeonColors.TextPrimary
private val CardSubtitle = NeonColors.TextSecondary
private val CardBio = NeonColors.TextSecondary
private val ChipBackground = NeonColors.Surface
private val ChipText = NeonColors.TextSecondary
private val VerifiedBadge = NeonColors.Primary

@Composable
fun SwipeableProfileCard(
    profile: DiscoverProfile,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    onClick: () -> Unit,
    onReportClick: () -> Unit = {},
    onBlockClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    interactive: Boolean = true
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    val dragModifier = if (interactive) {
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragEnd = {
                    coroutineScope.launch {
                        val threshold = size.width / 3f
                        if (offsetX.value > threshold) {
                            offsetX.animateTo(size.width.toFloat() * 1.5f)
                            onSwipeRight()
                        } else if (offsetX.value < -threshold) {
                            offsetX.animateTo(-size.width.toFloat() * 1.5f)
                            onSwipeLeft()
                        } else {
                            offsetX.animateTo(0f)
                            offsetY.animateTo(0f)
                        }
                    }
                }
            ) { change, dragAmount ->
                change.consume()
                coroutineScope.launch {
                    offsetX.snapTo(offsetX.value + dragAmount.x)
                    offsetY.snapTo(offsetY.value + dragAmount.y)
                }
            }
        }
    } else Modifier

    Card(
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
            .graphicsLayer { rotationZ = offsetX.value / 20f }
            .then(dragModifier),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            PhotoCarousel(
                photoUrls = profile.photoUrls,
                interactive = interactive,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )

            Spacer(Modifier.height(12.dp))

            // Avatar + name + verified + language·country. Tapping opens the profile.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (interactive) Modifier.clickable(onClick = onClick) else Modifier)
            ) {
                Avatar(photoUrl = profile.photoUrls.firstOrNull())
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile.age?.let { "${profile.name}, $it" } ?: profile.name,
                            color = CardName,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (profile.verified) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                painter = painterResource(Res.drawable.ic_check_circle),
                                contentDescription = "Verified",
                                tint = VerifiedBadge,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    val subtitle = listOfNotNull(
                        profile.language?.name,
                        profile.country?.name
                    ).joinToString(" - ")
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            color = CardSubtitle,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                var expanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = NeonColors.TextSecondary
                        )
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(NeonColors.Card)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Report Profile", color = NeonColors.TextPrimary) },
                            onClick = {
                                expanded = false
                                onReportClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Block User", color = NeonColors.Error) },
                            onClick = {
                                expanded = false
                                onBlockClick()
                            }
                        )
                    }
                }
            }

            // Bio — directly under the name block.
            if (profile.bio.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = profile.bio,
                    color = CardBio,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3
                )
            }

            // Interests — a horizontal row under the bio.
            if (profile.interests.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    profile.interests.forEach { InterestChip(text = it) }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Pass / Like buttons.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleActionButton(
                    enabled = interactive,
                    onClick = {
                        coroutineScope.launch {
                            offsetX.animateTo(-2000f)
                            onSwipeLeft()
                        }
                    }
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Pass", tint = CardName, modifier = Modifier.size(28.dp))
                }
                CircleActionButton(
                    enabled = interactive,
                    onClick = {
                        coroutineScope.launch {
                            offsetX.animateTo(2000f)
                            onSwipeRight()
                        }
                    }
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = "Like", tint = NeonColors.Accent, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

private const val PHOTO_DURATION_MS = 5000

/**
 * Photo with story-style auto-advancing segmented progress bars.
 *
 * The active segment fills left-to-right over [PHOTO_DURATION_MS]; when full, the photo
 * advances (looping after the last). Tapping the left/right half jumps to the previous/next
 * photo and restarts the timer. Navigation is by tap, not swipe, so it does not fight the
 * card's drag-to-like/pass gesture. Auto-advance only runs on the interactive front card.
 */
@Composable
private fun PhotoCarousel(
    photoUrls: List<String>,
    interactive: Boolean,
    modifier: Modifier = Modifier
) {
    var index by remember(photoUrls) { mutableStateOf(0) }
    val count = photoUrls.size
    val progress = remember { Animatable(0f) }

    // Fill the current segment, then move to the next photo. Re-keys on [index] so a
    // manual tap restarts the fill from zero; disabled off the interactive card.
    LaunchedEffect(index, count, interactive) {
        if (!interactive || count <= 1) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(PHOTO_DURATION_MS, easing = LinearEasing))
        index = (index + 1) % count
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(NeonColors.Card)
    ) {
        if (count == 0) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_user),
                    contentDescription = null,
                    tint = NeonColors.TextTertiary,
                    modifier = Modifier.size(64.dp)
                )
            }
        } else {
            AsyncImage(
                model = photoUrls[index.coerceIn(0, count - 1)],
                contentDescription = "Photo ${index + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (interactive && count > 1) Modifier.pointerInput(count) {
                            detectTapGestures { offset ->
                                if (offset.x < size.width / 2f) {
                                    if (index > 0) index--
                                } else {
                                    index = (index + 1) % count
                                }
                            }
                        } else Modifier
                    )
            )

            // Story-style segmented progress bars along the bottom of the photo.
            if (count > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (i in 0 until count) {
                        val fill = when {
                            i < index -> 1f
                            i == index -> if (interactive) progress.value else 1f
                            else -> 0f
                        }
                        SegmentBar(fill = fill, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** One progress segment: a translucent track with a fill from 0f..1f of its width. */
@Composable
private fun SegmentBar(fill: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(3.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.4f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fill.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(50))
                .background(NeonColors.Primary)
        )
    }
}

@Composable
private fun Avatar(photoUrl: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(44.dp).clip(CircleShape).background(ChipBackground),
        contentAlignment = Alignment.Center
    ) {
        if (photoUrl.isNullOrBlank()) {
            Icon(
                painter = painterResource(Res.drawable.ic_user),
                contentDescription = null,
                tint = CardSubtitle,
                modifier = Modifier.size(24.dp)
            )
        } else {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun InterestChip(text: String) {
    Text(
        text = text,
        color = ChipText,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(ChipBackground)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

@Composable
private fun CircleActionButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(ChipBackground)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
