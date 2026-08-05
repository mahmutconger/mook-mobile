package com.mcclabs.mook.feature.settings

data class SettingsUiState(
    val email: String = "",
    val discoverVisible: Boolean = true,
    val isDarkMode: Boolean = false,
    val ageRangeStart: Int = 18,
    val ageRangeEnd: Int = 35,
    val isLoading: Boolean = true,
    /** Whether to show the "Are you sure?" delete-account confirmation dialog. */
    val showDeleteConfirmDialog: Boolean = false,
    /** Non-null when account deletion failed; shown as an error message in the UI. */
    val deleteError: String? = null,
)
