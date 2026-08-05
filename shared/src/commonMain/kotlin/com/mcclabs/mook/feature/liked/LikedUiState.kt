package com.mcclabs.mook.feature.liked

import com.mcclabs.mook.domain.model.LikedProfile

data class LikedUiState(
    val liked: List<LikedProfile> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)
