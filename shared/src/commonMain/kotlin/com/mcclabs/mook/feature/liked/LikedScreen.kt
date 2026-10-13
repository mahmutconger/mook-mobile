package com.mcclabs.mook.feature.liked

import com.mcclabs.mook.ui.layout.scaffoldPadding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle

import com.mcclabs.mook.ui.loading.LoadingSkeleton
import com.mcclabs.mook.ui.loading.SkeletonLayout
import com.mcclabs.mook.ui.loading.ProgressiveAsyncImage
import com.mcclabs.mook.ui.loading.ImageLoadStatus

import mook.shared.generated.resources.liked_me_title
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mcclabs.mook.domain.model.LikedProfile
import com.mcclabs.mook.ui.components.BottomNavBar
import com.mcclabs.mook.ui.components.NeonChip
import com.mcclabs.mook.ui.components.NeonPrimaryButton
import com.mcclabs.mook.ui.components.WalkTalkDemoPromoCard
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.ic_user
import mook.shared.generated.resources.nav_liked
import mook.shared.generated.resources.liked_load_error_title
import mook.shared.generated.resources.liked_empty
import mook.shared.generated.resources.liked_match_chip
import mook.shared.generated.resources.discover_retry
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikedScreen(
    /** "Beni Beğenenler" ekranına geçiş. */
    onNavigateToLikedMe: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToDiscover: () -> Unit,
    onNavigateToChats: () -> Unit,
    onNavigateToWalkTalkDemo: () -> Unit,
    onNavigateToPaywall: () -> Unit,
    viewModel: LikedViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is LikedEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }
    val currentUserId = Firebase.auth.currentUser?.uid.orEmpty()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.nav_liked),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    TextButton(onClick = onNavigateToLikedMe) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(Res.string.liked_me_title), color = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            BottomNavBar(
                currentRoute = "liked",
                enabledRoutes = setOf("discover", "liked", "chats", "paywall", "profile"),
                onNavigate = { route ->
                    when (route) {
                        "discover" -> onNavigateToDiscover()
                        "chats" -> onNavigateToChats()
                        "paywall" -> onNavigateToPaywall()
                        "profile" -> if (currentUserId.isNotEmpty()) onNavigateToProfile(currentUserId)
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().scaffoldPadding(padding),
            contentAlignment = Alignment.Center
        ) {
            when {
                state.isLoading -> LoadingSkeleton(SkeletonLayout.Likes)

                state.error != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.liked_load_error_title),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.error.orEmpty(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    NeonPrimaryButton(
                        text = stringResource(Res.string.discover_retry),
                        onClick = { viewModel.load() },
                        modifier = Modifier.height(48.dp)
                    )
                }

                state.visibleLiked.isEmpty() -> Text(
                    text = stringResource(Res.string.liked_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // The Chats tab is where messaging is on the user's mind, so the
                    // WalkTalk translation demo is offered here rather than buried in Settings.
                    item(key = "walktalk_demo_promo") {
                        WalkTalkDemoPromoCard(onClick = onNavigateToWalkTalkDemo)
                    }
                    items(state.visibleLiked, key = { it.profile.id }) { liked ->
                        LikedRow(onUnlike = { viewModel.unlikeProfile(liked.profile.id) }, liked = liked, onClick = { onNavigateToProfile(liked.profile.id) }, onImageLoadChanged = viewModel::onImageLoadChanged)
                    }
                }
            }
        }
    }
}

@Composable
private fun LikedRow(onUnlike: () -> Unit, liked: LikedProfile, onClick: () -> Unit, onImageLoadChanged: (String, ImageLoadStatus) -> Unit) {
    val profile = liked.profile
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        val photo = profile.photoUrls.firstOrNull()
        if (photo.isNullOrBlank()) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_user),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        } else {
            ProgressiveAsyncImage(
                model = photo,
                onImageLoadChanged = onImageLoadChanged,
                contentDescription = profile.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLow)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                // Age is omitted rather than guessed when no birth date was given.
                text = profile.age?.let { "${profile.name}, $it" } ?: profile.name,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            val subtitle = listOfNotNull(
                profile.country?.name,
                profile.language?.let { "${it.flagEmoji} ${it.name}" }
            ).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        IconButton(onClick = onUnlike) {
            Icon(Icons.Filled.Favorite, contentDescription = "Beğeniyi geri çek", tint = MaterialTheme.colorScheme.primary)
        }

        if (liked.isMatch) {
            Spacer(Modifier.width(8.dp))
            NeonChip(text = stringResource(Res.string.liked_match_chip), isSelected = true)
        }
    }
}
