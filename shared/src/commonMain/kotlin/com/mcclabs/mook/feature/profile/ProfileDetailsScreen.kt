package com.mcclabs.mook.feature.profile

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle

import com.mcclabs.mook.ui.theme.LocalAppColors
import com.mcclabs.mook.ui.loading.LoadingSkeleton
import com.mcclabs.mook.ui.loading.SkeletonLayout
import com.mcclabs.mook.ui.loading.ProgressiveAsyncImage
import com.mcclabs.mook.ui.loading.ImageLoadStatus

import com.mcclabs.mook.domain.billing.PaywallRequest
import com.mcclabs.mook.ui.components.PremiumBadge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.mcclabs.mook.util.rememberWalkTalkChatOpener
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mcclabs.mook.domain.model.DiscoverProfile
import com.mcclabs.mook.ui.components.ImageCarousel
import com.mcclabs.mook.ui.components.LimitSheet
import com.mcclabs.mook.ui.components.NeonChip
import com.mcclabs.mook.ui.components.ReportBottomSheet
import com.mcclabs.mook.ui.components.WalkTalkRedirectDialog
import com.mcclabs.mook.ui.theme.BrandGradient
import kotlinx.coroutines.launch
import mook.shared.generated.resources.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Translucent gray-black scrim behind the photo-overlay icon buttons, so they stay legible
 *  against any photo. */
private val IconScrim: Color
    @Composable get() = LocalAppColors.current.photoScrim.copy(alpha = 0.55f)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProfileDetailsScreen(
    profileId: String,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToMatch: (String) -> Unit = {},
    onNavigateToPaywall: (PaywallRequest) -> Unit = {},
    onNavigateToChat: (chatId: String, peerUid: String) -> Unit = { _, _ -> },
    viewModel: ProfileDetailsViewModel = koinViewModel<ProfileDetailsViewModel>(
        parameters = { parametersOf(profileId) }
    )
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openWalkTalkChat = rememberWalkTalkChatOpener()
    val scope = rememberCoroutineScope()
    var pendingChatUrl by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val eventLifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, eventLifecycleOwner) {
        eventLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    // Don't redirect immediately — show a branded explainer first.
                    is ProfileDetailsEvent.OpenDeepLink -> pendingChatUrl = event.url
                    is ProfileDetailsEvent.ReportSubmitted -> onNavigateBack()
                    is ProfileDetailsEvent.BlockConfirmed -> onNavigateBack()
                    is ProfileDetailsEvent.NavigateToMatch -> onNavigateToMatch(event.matchedUserId)
                    is ProfileDetailsEvent.NavigateToChat -> onNavigateToChat(event.chatId, event.peerUid)
                    // The feed is what the user was doing; a like or pass returns them to it.
                    ProfileDetailsEvent.ActionCompleted -> {
                        onNavigateBack()
                    }
                    is ProfileDetailsEvent.NavigateToPaywall -> onNavigateToPaywall(event.request)
                    is ProfileDetailsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    // ── Daily like limit ────────────────────────────────────────────────────
    state.limitReason?.let { reason ->
        LimitSheet(
            reason = reason,
            entitlement = state.entitlement,
            rewardedLikesToday = state.rewardedLikesToday,
            isFairUseCap = state.limitIsFairUseCap,
            showRewardedAd = state.canEarnRewardedLike,
            onRewardConfirmed = viewModel::onRewardedLikeConfirmed,
            onUpgrade = { viewModel.onUpgradeClicked() },
            onStartTrial = viewModel::onTrialClicked,
            onDismiss = { viewModel.onLimitDialogDismissed() },
        )
    }

    // ── Report Profile bottom sheet ───────────────────────────────────────
    if (state.showReportDialog) {
        ReportBottomSheet(
            selectedReason = state.selectedReportReason,
            onReasonSelected = viewModel::onReportReasonSelected,
            onSubmit = viewModel::submitReport,
            onDismiss = viewModel::onReportDismiss,
        )
    }

    // ── Block User Confirmation Dialog ──────────────────────────────────
    if (state.showBlockConfirmDialog) {
        AlertDialog(
            onDismissRequest = viewModel::onBlockDismiss,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = {
                Text(
                    text = stringResource(Res.string.profile_block_user_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(Res.string.profile_block_user_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmBlock,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                ) {
                    Text(stringResource(Res.string.profile_block_confirm), color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onBlockDismiss) {
                    Text(stringResource(Res.string.profile_block_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // ── WalkTalk Redirect Explainer ─────────────────────────────────────────
    pendingChatUrl?.let { url ->
        WalkTalkRedirectDialog(
            userName = state.profile?.name,
            onConfirm = {
                openWalkTalkChat(url)
                pendingChatUrl = null
            },
            onDismiss = { pendingChatUrl = null },
        )
    }

    val profile = state.profile
    when {
        state.isLoading -> LoadingSkeleton(SkeletonLayout.Profile, Modifier.background(MaterialTheme.colorScheme.background))

        profile != null -> {
            // Opens expanded so details are visible at normal size; the user can then tap
            // the photo (or drag the handle down) to collapse to peek and see the full photo.
            val scaffoldState = rememberBottomSheetScaffoldState(
                bottomSheetState = rememberStandardBottomSheetState(
                    initialValue = SheetValue.Expanded,
                    skipHiddenState = true
                )
            )

            BottomSheetScaffold(
                scaffoldState = scaffoldState,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                sheetPeekHeight = 148.dp,
                sheetContainerColor = MaterialTheme.colorScheme.background,
                sheetContentColor = MaterialTheme.colorScheme.onSurface,
                sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                containerColor = MaterialTheme.colorScheme.background,
                sheetContent = {
                    ProfileSheetContent(
                        profile = profile,
                        onImageLoadChanged = viewModel::onImageLoadChanged,
                        isOwnProfile = state.isOwnProfile,
                        isMatched = state.isMatched,
                        canAct = state.canAct,
                        isActionInFlight = state.isActionInFlight,
                        isLiked = state.isLiked,
                        onLike = { viewModel.onLikeClicked() },
                        onPass = { viewModel.onPassClicked() },
                        onSendMessage = { viewModel.onSendMessageClicked() },
                        onBlock = { viewModel.onBlockClick() }
                    )
                }
            ) {
                // Background: the full-height photo carousel. Tapping it collapses the
                // sheet back to peek so the whole photo is visible.
                Box(modifier = Modifier.fillMaxSize()) {
                    val interaction = remember { MutableInteractionSource() }
                    ImageCarousel(
                        imageUrls = profile.photoUrls,
                        onImageLoadChanged = viewModel::onImageLoadChanged,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = interaction,
                                indication = null
                            ) {
                                scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                            }
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    if (dragAmount < -20) {
                                        scope.launch { scaffoldState.bottomSheetState.expand() }
                                    } else if (dragAmount > 20) {
                                        scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                                    }
                                }
                            }
                    )

                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 48.dp, start = 16.dp)
                            .clip(CircleShape)
                            .background(IconScrim)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.back_svgrepo_com),
                            contentDescription = stringResource(Res.string.common_back),
                            tint = LocalAppColors.current.onPhoto,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    if (state.isOwnProfile) {
                        IconButton(
                            onClick = onNavigateToEditProfile,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 48.dp, end = 64.dp)
                                .clip(CircleShape)
                                .background(IconScrim)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(Res.string.profile_edit_cd),
                                tint = LocalAppColors.current.onPhoto,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Own profile → Settings; someone else's → Report.
                    IconButton(
                        onClick = {
                            if (state.isOwnProfile) onNavigateToSettings() else viewModel.onReportClick()
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 48.dp, end = 16.dp)
                            .clip(CircleShape)
                            .background(IconScrim)
                    ) {
                        Icon(
                            painter = painterResource(
                                if (state.isOwnProfile) Res.drawable.ic_settings_minimalistic
                                else Res.drawable.flag_svgrepo_com
                            ),
                            contentDescription = if (state.isOwnProfile) stringResource(Res.string.profile_settings_cd) else stringResource(Res.string.profile_report_cd),
                            tint = LocalAppColors.current.onPhoto,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        else -> {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.error ?: stringResource(Res.string.profile_error_loading),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * The draggable sheet body. Its top row (small avatar + name) sits within the peek
 * height so it stays visible when the sheet is collapsed; everything below is revealed
 * as the user drags the sheet up.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileSheetContent(
    onImageLoadChanged: (String, ImageLoadStatus) -> Unit,
    profile: DiscoverProfile,
    isOwnProfile: Boolean,
    isMatched: Boolean,
    canAct: Boolean,
    isActionInFlight: Boolean,
    isLiked: Boolean,
    onLike: () -> Unit,
    onPass: () -> Unit,
    onSendMessage: () -> Unit,
    onBlock: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        // Peek row: small avatar + name, always visible when collapsed.
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressiveAsyncImage(
                model = profile.photoUrls.firstOrNull(),
                onImageLoadChanged = onImageLoadChanged,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    // Age is omitted rather than guessed when no birth date was given.
                    text = profile.age?.let { "${profile.name}, $it" } ?: profile.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                if (profile.isPremium) {
                    Spacer(modifier = Modifier.height(4.dp))
                    PremiumBadge()
                }
                profile.country?.let {
                    Text(
                        text = it.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(Res.string.profile_details_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            InfoCard(
                title = stringResource(Res.string.profile_country),
                value = profile.country?.name ?: stringResource(Res.string.profile_country_empty),
                modifier = Modifier.weight(1f)
            )
            InfoCard(
                title = stringResource(Res.string.profile_language),
                value = profile.language?.let { "${it.flagEmoji} ${it.name}" } ?: stringResource(Res.string.profile_language_empty),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(Res.string.profile_about_me),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = profile.bio.ifBlank { stringResource(Res.string.profile_no_description) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(Res.string.profile_interests),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Read-only here: these are someone else's interests, not a picker.
            profile.interests.forEach { interest ->
                NeonChip(text = com.mcclabs.mook.ui.components.interestLabel(interest))
            }
        }

        // The considered decision, after reading the whole profile. The Discovery grid's
        // heart is the shortcut; this is the version with a pass alongside it.
        if (canAct) {
            Spacer(modifier = Modifier.height(24.dp))
            LikePassRow(
                enabled = !isActionInFlight,
                isLiked = isLiked,
                onPass = onPass,
                onLike = onLike
            )
        }

        // Only other people's profiles get a message CTA; you don't message yourself.
        // Additionally, we only show it if there is a mutual match.
        if (!isOwnProfile && isMatched) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onSendMessage,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = BrandGradient,
                            shape = RoundedCornerShape(28.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.profile_send_message),
                        color = LocalAppColors.current.onGradient,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Block User button (Google Play dating-app safety requirement) ────────
            OutlinedButton(
                onClick = onBlock,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Text(
                    text = stringResource(Res.string.profile_block_user),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        } else if (!isOwnProfile && !isMatched) {
            // Block button should still be visible even if they are not matched
            Spacer(modifier = Modifier.height(24.dp))
            
            // ── Block User button (Google Play dating-app safety requirement) ────────
            OutlinedButton(
                onClick = onBlock,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Text(
                    text = stringResource(Res.string.profile_block_user),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** Small labelled card used for the country / language facts under the profile header. */
@Composable
fun InfoCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Pass on the left, like on the right — the same left/right sense the swipe deck used, so
 * the muscle memory carries over now that the gesture is gone.
 */
@Composable
private fun LikePassRow(
    enabled: Boolean,
    isLiked: Boolean,
    onPass: () -> Unit,
    onLike: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onPass,
            enabled = enabled,
            modifier = Modifier.weight(1f).height(56.dp),
            shape = RoundedCornerShape(28.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
        ) {
            Text(
                text = stringResource(Res.string.profile_pass),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall
            )
        }

        Button(
            onClick = onLike,
            enabled = enabled,
            modifier = Modifier.weight(1f).height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent
            ),
            contentPadding = PaddingValues()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(brush = BrandGradient, shape = RoundedCornerShape(28.dp))
                    .alpha(if (enabled) 1f else 0.5f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isLiked) "Beğenildi" else "Beğen",
                    color = LocalAppColors.current.onGradient,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}
