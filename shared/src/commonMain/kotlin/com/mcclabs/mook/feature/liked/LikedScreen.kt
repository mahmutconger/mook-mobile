package com.mcclabs.mook.feature.liked

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
import coil3.compose.AsyncImage
import com.mcclabs.mook.domain.model.LikedProfile
import com.mcclabs.mook.ui.components.BottomNavBar
import com.mcclabs.mook.ui.components.NeonChip
import com.mcclabs.mook.ui.components.NeonPrimaryButton
import com.mcclabs.mook.ui.theme.NeonColors
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
    onNavigateToProfile: (String) -> Unit,
    onNavigateToDiscover: () -> Unit,
    viewModel: LikedViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val currentUserId = Firebase.auth.currentUser?.uid.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.nav_liked),
                        color = NeonColors.Primary,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NeonColors.Background)
            )
        },
        bottomBar = {
            BottomNavBar(
                currentRoute = "chats",
                enabledRoutes = setOf("discover", "chats", "profile"),
                onNavigate = { route ->
                    when (route) {
                        "discover" -> onNavigateToDiscover()
                        "profile" -> if (currentUserId.isNotEmpty()) onNavigateToProfile(currentUserId)
                    }
                }
            )
        },
        containerColor = NeonColors.Background
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(color = NeonColors.Primary)

                state.error != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.liked_load_error_title),
                        color = NeonColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.error.orEmpty(),
                        color = NeonColors.TextSecondary,
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

                state.liked.isEmpty() -> Text(
                    text = stringResource(Res.string.liked_empty),
                    color = NeonColors.TextSecondary,
                    style = MaterialTheme.typography.titleMedium
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.liked, key = { it.profile.id }) { liked ->
                        LikedRow(liked = liked, onClick = { onNavigateToProfile(liked.profile.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun LikedRow(liked: LikedProfile, onClick: () -> Unit) {
    val profile = liked.profile
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(NeonColors.Card)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        val photo = profile.photoUrls.firstOrNull()
        if (photo.isNullOrBlank()) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(NeonColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_user),
                    contentDescription = null,
                    tint = NeonColors.TextTertiary,
                    modifier = Modifier.size(28.dp)
                )
            }
        } else {
            AsyncImage(
                model = photo,
                contentDescription = profile.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(CircleShape).background(NeonColors.Surface)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                // Age is omitted rather than guessed when no birth date was given.
                text = profile.age?.let { "${profile.name}, $it" } ?: profile.name,
                color = NeonColors.TextPrimary,
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
                    color = NeonColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (liked.isMatch) {
            Spacer(Modifier.width(8.dp))
            NeonChip(text = stringResource(Res.string.liked_match_chip), isSelected = true)
        }
    }
}
