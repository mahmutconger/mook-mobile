package com.mcclabs.mook.feature.discover

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.mcclabs.mook.feature.filters.FiltersScreen
import com.mcclabs.mook.ui.components.BottomNavBar
import com.mcclabs.mook.ui.components.ReportBottomSheet
import com.mcclabs.mook.ui.components.NeonPrimaryButton
import com.mcclabs.mook.ui.components.SwipeableProfileCard
import com.mcclabs.mook.ui.theme.NeonColors
import mook.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onNavigateToProfile: (String) -> Unit,
    onNavigateToMatch: (String) -> Unit = {},
    onNavigateToLiked: () -> Unit = {},
    onNavigateToRoomSwitch: () -> Unit = {},
    viewModel: DiscoverViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentUserId = Firebase.auth.currentUser?.uid.orEmpty()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DiscoverEvent.NavigateToProfile -> onNavigateToProfile(event.profileId)
                is DiscoverEvent.NavigateToMatch -> onNavigateToMatch(event.matchedUserId)
                is DiscoverEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
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
                            text = "Mook",
                            color = NeonColors.Primary,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            coroutineScope.launch { drawerState.open() }
                        }) {
                            Icon(
                                painter = painterResource(Res.drawable.menu_svgrepo_com),
                                contentDescription = "Menu",
                                tint = NeonColors.TextPrimary
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToRoomSwitch) {
                            Icon(
                                painter = painterResource(Res.drawable.flag_svgrepo_com),
                                contentDescription = stringResource(Res.string.discover_switch_room_cd),
                                tint = NeonColors.TextPrimary
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
                    enabledRoutes = setOf("discover", "chats", "profile"),
                    onNavigate = { route ->
                        when (route) {
                            "chats" -> onNavigateToLiked()
                            "profile" -> if (currentUserId.isNotEmpty()) onNavigateToProfile(currentUserId)
                        }
                    }
                )
            },
            containerColor = NeonColors.Background
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(color = NeonColors.Primary)
                } else if (state.error != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
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
                            text = state.error.orEmpty(),
                            color = NeonColors.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        NeonPrimaryButton(
                            text = stringResource(Res.string.discover_retry),
                            onClick = { viewModel.retry() },
                            modifier = Modifier.height(48.dp)
                        )
                    }
                } else if (state.profiles.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_info_circle),
                            contentDescription = null,
                            tint = NeonColors.TextTertiary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(Res.string.discover_no_more_profiles),
                            color = NeonColors.TextSecondary,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        state.profiles.getOrNull(1)?.let { behind ->
                            key(behind.id) {
                                SwipeableProfileCard(
                                    profile = behind,
                                    onSwipeLeft = {},
                                    onSwipeRight = {},
                                    onClick = {},
                                    interactive = false,
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = 0.95f
                                        scaleY = 0.95f
                                    }
                                )
                            }
                        }
                        state.profiles.firstOrNull()?.let { top ->
                            key(top.id) {
                                SwipeableProfileCard(
                                    profile = top,
                                    onSwipeLeft = { viewModel.swipeLeft(top.id) },
                                    onSwipeRight = { viewModel.swipeRight(top.id) },
                                    onClick = { viewModel.onProfileClicked(top.id) },
                                    onReportClick = { viewModel.onReportClick(top.id) },
                                    onBlockClick = { viewModel.onBlockClick(top.id) }
                                )
                            }
                        }
                    }

                    // Liked me tutorial overlay
                    val topProfile = state.profiles.firstOrNull()
                    if (topProfile != null && topProfile.hasLikedMe && !state.hasSeenLikedMeTutorial) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .pointerInput(Unit) { detectTapGestures { } }, // intercept taps
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Card(
                                modifier = Modifier.padding(32.dp),
                                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = com.mcclabs.mook.ui.theme.NeonColors.Card),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = stringResource(Res.string.discover_liked_me_title),
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = stringResource(Res.string.discover_liked_me_body),
                                        color = com.mcclabs.mook.ui.theme.NeonColors.TextSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    com.mcclabs.mook.ui.components.NeonPrimaryButton(
                                        text = stringResource(Res.string.discover_liked_me_dismiss),
                                        onClick = { viewModel.dismissLikedMeTutorial() },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Report bottom sheet ---
        if (state.showReportDialog) {
            ReportBottomSheet(
                selectedReason = state.selectedReportReason,
                onReasonSelected = { viewModel.onReportReasonSelected(it) },
                onSubmit = { viewModel.submitReport() },
                onDismiss = { viewModel.onReportDismiss() },
            )
        }

        // --- Block Confirmation Dialog ---
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
