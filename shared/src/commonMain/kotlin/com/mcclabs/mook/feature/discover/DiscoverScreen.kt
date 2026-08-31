package com.mcclabs.mook.feature.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.mcclabs.mook.domain.billing.BillingConfig
import com.mcclabs.mook.feature.filters.FiltersScreen
import com.mcclabs.mook.ui.components.BottomNavBar
import com.mcclabs.mook.ui.components.NeonPrimaryButton
import com.mcclabs.mook.ui.components.ReportBottomSheet
import com.mcclabs.mook.ui.theme.NeonColors
import com.mcclabs.mook.util.getCurrentTimeMillis
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mook.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** How often the online dots are re-evaluated. Presence is a 5-minute window, so this is plenty. */
private const val PRESENCE_TICK_MILLIS = 30_000L

/** Start fetching the next page once the user is this many tiles from the end. */
private const val PREFETCH_DISTANCE = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onNavigateToProfile: (String) -> Unit,
    onNavigateToMatch: (String) -> Unit = {},
    onNavigateToLiked: () -> Unit = {},
    onNavigateToChats: () -> Unit = {},
    onNavigateToRoomSwitch: () -> Unit = {},
    onNavigateToPaywall: () -> Unit = {},
    viewModel: DiscoverViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentUserId = Firebase.auth.currentUser?.uid.orEmpty()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    // Presence is derived from a timestamp, so "online" has to be recomputed on a clock, not
    // only when the profile list changes.
    var nowMillis by remember { mutableStateOf(getCurrentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(PRESENCE_TICK_MILLIS)
            nowMillis = getCurrentTimeMillis()
        }
    }

    // Coming back from a profile the user liked or passed on: drop that card rather than
    // re-querying the whole feed and losing their place.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.pruneActedOnProfiles()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DiscoverEvent.NavigateToProfile -> onNavigateToProfile(event.profileId)
                is DiscoverEvent.NavigateToMatch -> onNavigateToMatch(event.matchedUserId)
                is DiscoverEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                is DiscoverEvent.NavigateToPaywall -> onNavigateToPaywall()
            }
        }
    }

    // Infinite scroll: ask for the next page while the last tiles are still off-screen, so
    // new rows are already there by the time the user reaches them.
    val shouldPage by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = gridState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 1 - PREFETCH_DISTANCE
        }
    }
    LaunchedEffect(shouldPage, state.profiles.size, state.endReached) {
        if (shouldPage) viewModel.loadMore()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = NeonColors.Background,
                drawerContentColor = NeonColors.TextPrimary
            ) {
                FiltersScreen(
                    onNavigateBack = {
                        coroutineScope.launch { drawerState.close() }
                    }
                )
            }
        },
        scrimColor = Color.Transparent
    ) {
        Scaffold(
            modifier = Modifier.graphicsLayer {
                val drawerWidthPx = 360.dp.toPx()
                val offset = drawerState.currentOffset
                if (!offset.isNaN()) {
                    this.translationX = (drawerWidthPx + offset).coerceAtLeast(0f)
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(Res.string.discover_title),
                            color = NeonColors.TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(onClick = {
                            coroutineScope.launch { drawerState.open() }
                        }) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_settings_minimalistic),
                                contentDescription = stringResource(Res.string.discover_filters_cd),
                                tint = NeonColors.TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = NeonColors.Background
                    )
                )
            },
            bottomBar = {
                BottomNavBar(
                    currentRoute = "discover",
                    // Voices has no screen yet, so that tab stays inert.
                    enabledRoutes = setOf("discover", "liked", "chats", "profile"),
                    onNavigate = { route ->
                        when (route) {
                            "liked" -> onNavigateToLiked()
                            "chats" -> onNavigateToChats()
                            "profile" -> if (currentUserId.isNotEmpty()) onNavigateToProfile(currentUserId)
                        }
                    }
                )
            },
            containerColor = NeonColors.Background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                DiscoveryFilterBar(
                    roomLanguage = state.roomLanguage,
                    isLanguageIndependentRoom = state.isLanguageIndependentRoom,
                    ageRangeStart = state.ageRangeStart,
                    ageRangeEnd = state.ageRangeEnd,
                    onRoomClick = onNavigateToRoomSwitch,
                    onAgeClick = { viewModel.onAgeChipClicked() },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        state.isLoading -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = NeonColors.Primary)
                        }

                        state.error != null -> ErrorState(
                            message = state.error.orEmpty(),
                            onRetry = { viewModel.retry() }
                        )

                        state.isEmpty -> EmptyRoomState(onSwitchRoom = onNavigateToRoomSwitch)

                        else -> PullToRefreshBox(
                            isRefreshing = state.isRefreshing,
                            onRefresh = { viewModel.refresh() },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                state = gridState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    bottom = 16.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(state.profiles, key = { it.id }) { profile ->
                                    ProfileGridCard(
                                        profile = profile,
                                        nowMillis = nowMillis,
                                        onClick = { viewModel.onProfileClicked(profile.id) },
                                        onLike = { viewModel.likeProfile(profile.id) },
                                        onReport = { viewModel.onReportClick(profile.id) },
                                        onBlock = { viewModel.onBlockClick(profile.id) }
                                    )
                                }

                                if (state.isLoadingMore || state.endReached) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        GridFooter(isLoading = state.isLoadingMore)
                                    }
                                }
                            }
                        }
                    }

                    // "This person liked you" coach mark, shown once, over the grid.
                    if (state.likedMeTutorialProfileId != null && !state.hasSeenLikedMeTutorial) {
                        LikedMeOverlay(onDismiss = { viewModel.dismissLikedMeTutorial() })
                    }
                }
            }
        }

        // ── Age range sheet ────────────────────────────────────────────────
        if (state.showAgeSheet) {
            AgeRangeSheet(
                start = state.ageRangeStart,
                end = state.ageRangeEnd,
                onRangeChange = { s, e -> viewModel.onAgeRangeChanged(s, e) },
                onApply = { viewModel.onAgeRangeApplied() },
                onDismiss = { viewModel.onAgeSheetDismissed() }
            )
        }

        // ── Daily like limit ───────────────────────────────────────────────
        if (state.showLimitSheet) {
            LikeLimitDialog(
                onUpgrade = { viewModel.onUpgradeClicked() },
                onDismiss = { viewModel.onLimitSheetDismissed() }
            )
        }

        // ── Report bottom sheet ────────────────────────────────────────────
        if (state.showReportDialog) {
            ReportBottomSheet(
                selectedReason = state.selectedReportReason,
                onReasonSelected = { viewModel.onReportReasonSelected(it) },
                onSubmit = { viewModel.submitReport() },
                onDismiss = { viewModel.onReportDismiss() },
            )
        }

        // ── Block confirmation ─────────────────────────────────────────────
        if (state.showBlockConfirmDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.onBlockDismiss() },
                title = { Text(stringResource(Res.string.discover_block_user), color = NeonColors.Error) },
                text = {
                    Text(stringResource(Res.string.discover_block_user_message), color = NeonColors.TextSecondary)
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmBlock() }) {
                        Text(stringResource(Res.string.discover_block_confirm), color = NeonColors.Error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.onBlockDismiss() }) {
                        Text(stringResource(Res.string.discover_block_cancel), color = NeonColors.TextSecondary)
                    }
                },
                containerColor = NeonColors.Card
            )
        }
    }
}

/** Footer row under the grid: a spinner while paging, or the end-of-feed note. */
@Composable
private fun GridFooter(isLoading: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = NeonColors.Primary,
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp
            )
        } else {
            Text(
                text = stringResource(Res.string.discover_no_more_profiles),
                color = NeonColors.TextTertiary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_warning),
            contentDescription = null,
            tint = NeonColors.Error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.discover_error_couldnt_load_profiles),
            color = NeonColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = NeonColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        NeonPrimaryButton(
            text = stringResource(Res.string.discover_retry),
            onClick = onRetry,
            modifier = Modifier.height(48.dp)
        )
    }
}

@Composable
private fun EmptyRoomState(onSwitchRoom: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_info_circle),
            contentDescription = null,
            tint = NeonColors.TextTertiary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.discover_empty_room_message),
            color = NeonColors.TextSecondary,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        NeonPrimaryButton(
            text = stringResource(Res.string.room_switch_title),
            onClick = onSwitchRoom,
            modifier = Modifier.height(48.dp)
        )
    }
}

/** One-time coach mark explaining that someone in the grid has already liked the user. */
@Composable
private fun LikedMeOverlay(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) { detectTapGestures { } }, // intercept taps
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp),
            colors = CardDefaults.cardColors(containerColor = NeonColors.Card),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(Res.string.discover_liked_me_title),
                    color = NeonColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.discover_liked_me_body),
                    color = NeonColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                NeonPrimaryButton(
                    text = stringResource(Res.string.discover_liked_me_dismiss),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** Free-tier gate: shown when the daily like allowance runs out. */
@Composable
private fun LikeLimitDialog(onUpgrade: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NeonColors.Card,
        title = {
            Text(
                text = stringResource(Res.string.discover_swipe_limit_title),
                color = NeonColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = stringResource(
                    Res.string.discover_swipe_limit_body,
                    BillingConfig.FREE_DAILY_SWIPE_LIMIT
                ),
                color = NeonColors.TextSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onUpgrade) {
                Text(stringResource(Res.string.discover_upgrade_cta), color = NeonColors.Primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.discover_block_cancel), color = NeonColors.TextSecondary)
            }
        }
    )
}
