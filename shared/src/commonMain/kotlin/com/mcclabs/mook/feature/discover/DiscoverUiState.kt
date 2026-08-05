package com.mcclabs.mook.feature.discover

import com.mcclabs.mook.domain.model.DiscoverProfile

data class DiscoverUiState(
    val profiles: List<DiscoverProfile> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    
    // Safety requirement for Google Play (Reporting & Blocking)
    val showReportDialog: Boolean = false,
    val selectedReportReason: String? = null,
    val showBlockConfirmDialog: Boolean = false,
    val selectedProfileToReportOrBlock: String? = null,

    /** The signed-in user's own avatar, shown in the top bar. */
    val currentUserAvatarUrl: String? = null
)
