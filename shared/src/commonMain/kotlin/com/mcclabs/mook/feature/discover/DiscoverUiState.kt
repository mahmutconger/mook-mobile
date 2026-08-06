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

    /** Whether the user has seen the "this person liked you" overlay. */
    val hasSeenLikedMeTutorial: Boolean = false
)
