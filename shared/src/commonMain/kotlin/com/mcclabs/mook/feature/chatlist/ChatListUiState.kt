package com.mcclabs.mook.feature.chatlist

import com.mcclabs.mook.ui.loading.ImageLoadingState
import com.mcclabs.mook.ui.loading.ContentLoadingPhase

import com.mcclabs.mook.domain.model.ChatRoom

data class ChatListUiState(
    val rooms: List<ChatRoom> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val imageLoading: ImageLoadingState = ImageLoadingState(),
) {
    val imagesLoading: Boolean get() = imageLoading.imagesLoading
    val loadingPhase: ContentLoadingPhase get() = imageLoading.phase(isLoading, error != null)
}
