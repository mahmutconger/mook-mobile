package com.mcclabs.mook.feature.match

data class MatchUiState(
    val isLoading: Boolean = true,
    val matchedUserName: String? = null,
    val matchedUserPhotoUrl: String? = null,
    val currentUserPhotoUrl: String? = null
)
