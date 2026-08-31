package com.mcclabs.mook.feature.match

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcclabs.mook.util.rememberWalkTalkChatOpener
import com.mcclabs.mook.ui.components.LinkedAvatars
import com.mcclabs.mook.ui.components.WalkTalkRedirectDialog
import com.mcclabs.mook.ui.components.NeonPrimaryButton
import com.mcclabs.mook.ui.components.ParticleBackground
import com.mcclabs.mook.ui.theme.NeonColors
import mook.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun MatchScreen(
    matchedUserId: String,
    onKeepSwiping: () -> Unit,
    onNavigateToChat: (chatId: String, peerUid: String) -> Unit = { _, _ -> },
    viewModel: MatchViewModel = koinViewModel<MatchViewModel>(
        parameters = { parametersOf(matchedUserId) }
    )
) {
    val state by viewModel.state.collectAsState()
    val openWalkTalkChat = rememberWalkTalkChatOpener()
    var pendingChatUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                // Don't redirect immediately — show a branded explainer first.
                is MatchEvent.OpenDeepLink -> pendingChatUrl = event.url
                is MatchEvent.NavigateToChat -> onNavigateToChat(event.chatId, event.peerUid)
            }
        }
    }

    pendingChatUrl?.let { url ->
        WalkTalkRedirectDialog(
            userName = state.matchedUserName,
            onConfirm = {
                openWalkTalkChat(url)
                pendingChatUrl = null
            },
            onDismiss = { pendingChatUrl = null },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonColors.Background)
    ) {
        ParticleBackground()
        
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(Res.string.match_title),
                color = NeonColors.Primary,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            state.matchedUserName?.let { name ->
                Text(
                    text = stringResource(Res.string.match_liked_each_other, name),
                    color = NeonColors.TextSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            }

            LinkedAvatars(
                userPhotoUrl = state.currentUserPhotoUrl,
                matchedPhotoUrl = state.matchedUserPhotoUrl
            )

            Spacer(modifier = Modifier.height(48.dp))

            NeonPrimaryButton(
                text = stringResource(Res.string.match_chat_walktalk),
                onClick = { viewModel.onChatClicked() },
                modifier = Modifier.padding(horizontal = 32.dp).fillMaxWidth().height(56.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onKeepSwiping) {
                Text(
                    text = stringResource(Res.string.match_keep_swiping),
                    color = NeonColors.TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

