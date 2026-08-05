package com.mcclabs.mook.feature.profile

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.mcclabs.mook.util.rememberWalkTalkChatOpener
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mcclabs.mook.domain.model.DiscoverProfile
import com.mcclabs.mook.ui.components.ImageCarousel
import com.mcclabs.mook.ui.components.NeonChip
import com.mcclabs.mook.ui.components.ReportBottomSheet
import com.mcclabs.mook.ui.components.WalkTalkRedirectDialog
import com.mcclabs.mook.ui.theme.BrandGradient
import com.mcclabs.mook.ui.theme.NeonColors
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
private val IconScrim = Color.Black.copy(alpha = 0.35f)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProfileDetailsScreen(
    profileId: String,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToEditProfile: () -> Unit = {},
    viewModel: ProfileDetailsViewModel = koinViewModel<ProfileDetailsViewModel>(
        parameters = { parametersOf(profileId) }
    )
) {
    val state by viewModel.state.collectAsState()
    val openWalkTalkChat = rememberWalkTalkChatOpener()
    val scope = rememberCoroutineScope()
    var pendingChatUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                // Don't redirect immediately — show a branded explainer first.
                is ProfileDetailsEvent.OpenDeepLink -> pendingChatUrl = event.url
                is ProfileDetailsEvent.ReportSubmitted -> onNavigateBack()
                is ProfileDetailsEvent.BlockConfirmed -> onNavigateBack()
            }
        }
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
            containerColor = NeonColors.Card,
            title = {
                Text(
                    text = stringResource(Res.string.profile_block_user_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(Res.string.profile_block_user_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonColors.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmBlock,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonColors.Error)
                ) {
                    Text(stringResource(Res.string.profile_block_confirm), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onBlockDismiss) {
                    Text(stringResource(Res.string.profile_block_cancel), color = NeonColors.TextSecondary)
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
        state.isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize().background(NeonColors.Background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = NeonColors.Primary)
            }
        }

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
                sheetPeekHeight = 148.dp,
                sheetContainerColor = NeonColors.Background,
                sheetContentColor = NeonColors.TextPrimary,
                sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                containerColor = NeonColors.Background,
                sheetContent = {
                    ProfileSheetContent(
                        profile = profile,
                        isOwnProfile = state.isOwnProfile,
                        isMatched = state.isMatched,
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
                            contentDescription = "Back",
                            tint = Color.White,
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
                                contentDescription = "Edit Profile",
                                tint = Color.White,
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
                            contentDescription = if (state.isOwnProfile) "Settings" else "Report",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        else -> {
            Box(
                modifier = Modifier.fillMaxSize().background(NeonColors.Background),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.error ?: stringResource(Res.string.profile_error_loading),
                    color = NeonColors.Error
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
    profile: DiscoverProfile,
    isOwnProfile: Boolean,
    isMatched: Boolean,
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
            AsyncImage(
                model = profile.photoUrls.firstOrNull(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(NeonColors.Card)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    // Age is omitted rather than guessed when no birth date was given.
                    text = profile.age?.let { "${profile.name}, $it" } ?: profile.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = NeonColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                profile.country?.let {
                    Text(
                        text = it.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonColors.TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(Res.string.profile_details_title),
            style = MaterialTheme.typography.titleMedium,
            color = NeonColors.TextPrimary,
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
            color = NeonColors.TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = profile.bio.ifBlank { stringResource(Res.string.profile_no_description) },
            style = MaterialTheme.typography.bodyMedium,
            color = NeonColors.TextSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(Res.string.profile_interests),
            style = MaterialTheme.typography.titleMedium,
            color = NeonColors.TextPrimary,
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
                NeonChip(text = interest)
            }
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
                        color = Color.White,
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
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonColors.Error)
            ) {
                Text(
                    text = stringResource(Res.string.profile_block_user),
                    color = NeonColors.Error,
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
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonColors.Error)
            ) {
                Text(
                    text = stringResource(Res.string.profile_block_user),
                    color = NeonColors.Error,
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
        colors = CardDefaults.cardColors(containerColor = NeonColors.Card)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = NeonColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = NeonColors.Primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
