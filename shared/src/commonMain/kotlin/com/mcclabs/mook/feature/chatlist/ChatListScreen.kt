package com.mcclabs.mook.feature.chatlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mcclabs.mook.domain.model.ChatRoom
import com.mcclabs.mook.ui.components.BottomNavBar
import com.mcclabs.mook.ui.theme.BrandGradient
import com.mcclabs.mook.ui.theme.NeonColors
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import com.mcclabs.mook.util.ChatTimestampLabel
import com.mcclabs.mook.util.chatTimestampLabel
import com.mcclabs.mook.util.getCurrentTimeMillis
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.chat_list_empty
import mook.shared.generated.resources.chat_list_own_message_prefix
import mook.shared.generated.resources.chat_list_start
import mook.shared.generated.resources.chat_list_title
import mook.shared.generated.resources.chat_list_yesterday
import mook.shared.generated.resources.chat_message_deleted
import mook.shared.generated.resources.chat_unknown_peer
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onNavigateToChat: (chatId: String, peerUid: String) -> Unit,
    onNavigateToDiscover: () -> Unit,
    onNavigateToLiked: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    viewModel: ChatListViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val currentUid = Firebase.auth.currentUser?.uid.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.chat_list_title),
                        color = NeonColors.Primary,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NeonColors.Background)
            )
        },
        bottomBar = {
            BottomNavBar(
                currentRoute = "chats", // Custom route for highlighting
                enabledRoutes = setOf("discover", "liked", "chats", "profile"),
                onNavigate = { route ->
                    when (route) {
                        "discover" -> onNavigateToDiscover()
                        "liked" -> onNavigateToLiked()
                        "profile" -> if (currentUid.isNotEmpty()) onNavigateToProfile(currentUid)
                    }
                }
            )
        },
        containerColor = NeonColors.Background
    ) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonColors.Primary)
                }
            }
            state.rooms.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(Res.string.chat_list_empty),
                        color = NeonColors.TextSecondary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.rooms, key = { it.chatId }) { room ->
                        ChatRoomRow(
                            room = room,
                            currentUid = currentUid,
                            onClick = { onNavigateToChat(room.chatId, room.peerUid) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatRoomRow(room: ChatRoom, currentUid: String, onClick: () -> Unit) {
    val isMeLastSender = room.lastSenderUid == currentUid
    val prefix = if (isMeLastSender) stringResource(Res.string.chat_list_own_message_prefix) else ""
    val messageText = when {
        // Quoting the text of a message the sender retracted would leak exactly what
        // deleting it was meant to take back.
        room.lastMessageDeleted -> stringResource(Res.string.chat_message_deleted)
        else -> room.lastMessage ?: stringResource(Res.string.chat_list_start)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(NeonColors.Card)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = room.peerPhotoUrl,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(NeonColors.Background)
        )
        
        Spacer(Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = room.peerName ?: stringResource(Res.string.chat_unknown_peer),
                style = MaterialTheme.typography.titleMedium,
                color = NeonColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = prefix + messageText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (room.unreadCount > 0) NeonColors.TextPrimary else NeonColors.TextSecondary,
                fontWeight = if (room.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(Modifier.width(8.dp))

        // Date above, badge below: the timestamp is what tells two otherwise
        // identical rows apart when scanning the list.
        Column(horizontalAlignment = Alignment.End) {
            val label = chatTimestampLabel(
                epochMillis = room.lastMessageTimestamp,
                nowMillis = getCurrentTimeMillis(),
            )
            val labelText = when (label) {
                is ChatTimestampLabel.Today -> label.text
                is ChatTimestampLabel.Older -> label.text
                ChatTimestampLabel.Yesterday -> stringResource(Res.string.chat_list_yesterday)
                ChatTimestampLabel.None -> null
            }
            if (labelText != null) {
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (room.unreadCount > 0) NeonColors.Primary else NeonColors.TextTertiary,
                    fontWeight = if (room.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }

            if (room.unreadCount > 0) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(BrandGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = room.unreadCount.toString(),
                        color = NeonColors.Background,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
