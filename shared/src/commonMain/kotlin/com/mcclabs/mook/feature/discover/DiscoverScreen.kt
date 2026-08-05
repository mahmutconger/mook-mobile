package com.mcclabs.mook.feature.discover

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import coil3.compose.AsyncImage
import com.mcclabs.mook.feature.filters.FiltersScreen
import com.mcclabs.mook.ui.components.BottomNavBar
import com.mcclabs.mook.ui.components.ReportBottomSheet
import com.mcclabs.mook.ui.components.NeonPrimaryButton
import com.mcclabs.mook.ui.components.SwipeableProfileCard
import com.mcclabs.mook.ui.theme.NeonColors
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.ic_info_circle
import mook.shared.generated.resources.ic_warning
import mook.shared.generated.resources.menu_svgrepo_com
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onNavigateToProfile: (String) -> Unit,
    onNavigateToMatch: (String) -> Unit = {},
    onNavigateToLiked: () -> Unit = {},
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
                        AsyncImage(
                            model = state.currentUserAvatarUrl,
                            contentDescription = "Your profile",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(NeonColors.Card)
                                .clickable(enabled = currentUserId.isNotEmpty()) {
                                    onNavigateToProfile(currentUserId)
                                }
                        )
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
                            text = "Couldn't load profiles",
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
                            text = "Retry",
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
                            text = "No more profiles",
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
                        state.profiles.firstOrNull()?.let { top ->
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
                title = { Text("Block User", color = NeonColors.Error) },
                text = {
                    Text("Are you sure you want to block this user? They will no longer see you, and you will no longer see them in your feed.", color = NeonColors.TextSecondary)
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmBlock() }) {
                        Text("Block", color = NeonColors.Error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.onBlockDismiss() }) {
                        Text("Cancel", color = NeonColors.TextSecondary)
                    }
                },
                containerColor = NeonColors.Card
            )
        }
    }
}
