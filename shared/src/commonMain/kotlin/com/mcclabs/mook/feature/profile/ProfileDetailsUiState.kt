package com.mcclabs.mook.feature.profile

import com.mcclabs.mook.domain.model.DiscoverProfile

data class ProfileDetailsUiState(
    val profile: DiscoverProfile? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    /** True when the viewed profile is the signed-in user's own — drives Settings vs Report UI. */
    val isOwnProfile: Boolean = false,
    /** Whether the report-profile dialog is visible. */
    val showReportDialog: Boolean = false,
    /** The report reason the user has selected, before submitting. */
    val selectedReportReason: String? = null,
    /** Whether the block-user confirmation dialog is visible. */
    val showBlockConfirmDialog: Boolean = false,
    /** True if there is a mutual match with this profile. Used to show/hide the message button. */
    val isMatched: Boolean = false
)
