package com.mcclabs.mook.feature.likedme

import com.mcclabs.mook.ui.layout.scaffoldPadding
import com.mcclabs.mook.ui.theme.LocalAppColors
import com.mcclabs.mook.ui.loading.LoadingSkeleton
import com.mcclabs.mook.ui.loading.SkeletonLayout
import com.mcclabs.mook.ui.loading.ProgressiveAsyncImage
import com.mcclabs.mook.ui.loading.ImageLoadStatus

import com.mcclabs.mook.domain.billing.PaywallRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.mcclabs.mook.ads.NativeAdFeedCard
import com.mcclabs.mook.domain.billing.Tier
import com.mcclabs.mook.domain.model.LikedMeEntry
import com.mcclabs.mook.ui.components.LimitSheet
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.liked_me_all_revealed
import mook.shared.generated.resources.liked_me_counter
import mook.shared.generated.resources.liked_me_empty_body
import mook.shared.generated.resources.liked_me_empty_title
import mook.shared.generated.resources.liked_me_go_premium
import mook.shared.generated.resources.liked_me_hidden_name
import mook.shared.generated.resources.liked_me_liked_you
import mook.shared.generated.resources.liked_me_retry
import mook.shared.generated.resources.liked_me_title
import mook.shared.generated.resources.liked_me_unlock
import mook.shared.generated.resources.liked_me_unlocks_none
import mook.shared.generated.resources.liked_me_unlocks_remaining
import mook.shared.generated.resources.liked_me_view_profile
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Kilitli fotoğraflar bu boyutta (piksel) indirilip çözülür; ağdan ve bellekten tasarruf sağlar. */
private const val LOCKED_PHOTO_DECODE_SIZE = 24

/**
 * "Beni Beğenenler" ekranı: üstte toplam sayaç, altında beğenenler listesi.
 *
 * - Kilitli profiller bulanık gösterilir. Bulanıklık iki katmanlıdır ve ana iş parçacığını
 *   yormaz: (1) Coil fotoğrafı arka planda yalnızca 24×24 piksel olarak çözer — asıl fotoğraf
 *   cihazda hiç tam çözünürlükte oluşmaz; (2) Android 12+ üzerinde `Modifier.blur` GPU'da
 *   (RenderEffect) çalışarak görüntüyü yumuşatır. Eski sürümlerde (1) tek başına yeterli
 *   gizlilik sağlar.
 * - Ücretsiz planda her 8. satırda (7, 15, 23 …) yerel reklam yer alır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikedMeScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToPaywall: (PaywallRequest) -> Unit,
    viewModel: LikedMeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LikedMeEvent.NavigateToProfile -> onNavigateToProfile(event.profileUid)
                is LikedMeEvent.NavigateToPaywall -> onNavigateToPaywall(event.request)
            }
        }
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onMessageShown()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(Res.string.liked_me_title), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().scaffoldPadding(padding)) {
            when {
                state.isLoading -> LoadingSkeleton(SkeletonLayout.LikedMe)
                state.errorMessage != null -> ErrorState(state.errorMessage!!, onRetry = viewModel::load)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "header") {
                        CounterHeader(state = state, onGoPremium = viewModel::onUpgradeClicked)
                    }
                    if (state.items.isEmpty()) {
                        item(key = "empty") { EmptyState() }
                    }
                    items(state.items, key = { it.key }) { item ->
                        when (item) {
                            is LikedMeListItem.Profile -> LikerRow(
                                entry = item.entry,
                                isUnlocking = state.unlockingToken == item.entry.entryToken,
                                onClick = { viewModel.onEntryClicked(item.entry) },
                                onImageLoadChanged = viewModel::onImageLoadChanged,
                            )
                            is LikedMeListItem.NativeAd -> NativeAdFeedCard(isFree = true, placement = "liked_me_native")
                        }
                    }
                }
            }
        }
    }

    state.limitReason?.let { reason ->
        LimitSheet(
            reason = reason,
            entitlement = state.entitlement,
            rewardedLikesToday = 0,
            isFairUseCap = false,
            showRewardedAd = viewModel.canEarnRewardedUnlock(),
            onRewardConfirmed = viewModel::onRewardedUnlockConfirmed,
            onUpgrade = viewModel::onUpgradeClicked,
            onStartTrial = viewModel::onTrialClicked,
            onDismiss = viewModel::onLimitSheetDismissed,
        )
    }
}

@Composable
private fun CounterHeader(state: LikedMeUiState, onGoPremium: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(Res.string.liked_me_counter, state.counterLabel),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(4.dp))
        val remaining = state.unlocksRemainingToday
        Text(
            text = when {
                state.revealAll -> stringResource(Res.string.liked_me_all_revealed)
                remaining != null && remaining > 0 -> stringResource(Res.string.liked_me_unlocks_remaining, remaining)
                else -> stringResource(Res.string.liked_me_unlocks_none)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!state.revealAll && state.entitlement.tier != Tier.PREMIUM && state.totalCount > 0) {
            Spacer(Modifier.size(12.dp))
            Button(
                onClick = onGoPremium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Text(stringResource(Res.string.liked_me_go_premium)) }
        }
    }
}

@Composable
private fun LikerRow(entry: LikedMeEntry, isUnlocking: Boolean, onClick: () -> Unit, onImageLoadChanged: (String, ImageLoadStatus) -> Unit) {
    val profile = entry.profile
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = !isUnlocking, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LikerPhoto(photoUrl = entry.photoUrl, locked = !entry.isUnlocked, onImageLoadChanged = onImageLoadChanged)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (entry.isUnlocked && profile != null) {
                    listOfNotNull(profile.name.ifBlank { null }, profile.age?.toString()).joinToString(", ")
                } else {
                    stringResource(Res.string.liked_me_hidden_name)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(Res.string.liked_me_liked_you),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        when {
            isUnlocking -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
            entry.isUnlocked -> OutlinedButton(onClick = onClick) { Text(stringResource(Res.string.liked_me_view_profile)) }
            else -> Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.liked_me_unlock))
            }
        }
    }
}

/**
 * Profil fotoğrafı. Kilitliyse fotoğraf yalnızca [LOCKED_PHOTO_DECODE_SIZE] piksel olarak
 * çözülür ve ayrıca GPU bulanıklığı uygulanır (bkz. ekran KDoc'u).
 */
@Composable
private fun LikerPhoto(photoUrl: String?, locked: Boolean, onImageLoadChanged: (String, ImageLoadStatus) -> Unit) {
    val context = LocalPlatformContext.current
    val request = remember(photoUrl, locked) {
        ImageRequest.Builder(context)
            .data(photoUrl)
            .apply { if (locked) size(LOCKED_PHOTO_DECODE_SIZE, LOCKED_PHOTO_DECODE_SIZE) }
            .build()
    }
    Box(
        modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        ProgressiveAsyncImage(
            model = request,
            imageUrl = photoUrl,
            onImageLoadChanged = onImageLoadChanged,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(if (locked) Modifier.blur(16.dp, BlurredEdgeTreatment(CircleShape)) else Modifier),
        )
        if (locked) {
            Icon(
                Icons.Filled.Lock, contentDescription = null, tint = LocalAppColors.current.onPhoto,
                modifier = Modifier.clip(CircleShape).background(LocalAppColors.current.photoScrim.copy(alpha = 0.55f))
                    .padding(6.dp).size(20.dp),
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(Res.string.liked_me_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            stringResource(Res.string.liked_me_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.size(12.dp))
        TextButton(onClick = onRetry) { Text(stringResource(Res.string.liked_me_retry), color = MaterialTheme.colorScheme.primary) }
    }
}
