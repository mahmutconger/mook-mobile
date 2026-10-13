package com.mcclabs.mook.feature.liked

import com.mcclabs.mook.ui.loading.ImageLoadingState
import com.mcclabs.mook.ui.loading.ContentLoadingPhase

import com.mcclabs.mook.domain.model.LikedProfile

data class LikedUiState(
    val liked: List<LikedProfile> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val imageLoading: ImageLoadingState = ImageLoadingState(),
) {
    val imagesLoading: Boolean get() = imageLoading.imagesLoading
    val loadingPhase: ContentLoadingPhase get() = imageLoading.phase(isLoading, error != null)
}
