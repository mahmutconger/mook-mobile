package com.mcclabs.mook.feature.chatlist

import com.mcclabs.mook.domain.model.ChatRoom

data class ChatListUiState(
    val rooms: List<ChatRoom> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)
