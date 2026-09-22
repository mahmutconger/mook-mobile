package com.mcclabs.mook.feature.settings

import com.mcclabs.mook.domain.billing.EntitlementState

data class SettingsUiState(
    val email: String = "",
    val discoverVisible: Boolean = true,
    val isDarkMode: Boolean = false,
    val ageRangeStart: Int = 18,
    val ageRangeEnd: Int = 35,
    val isLoading: Boolean = true,
    /** Onay iletişim kutusunun görünürlüğü ("Emin misin?" adımı). */
    val showDeleteConfirmDialog: Boolean = false,
    /** Hesap silme akışının anlık durumu (Idle/Loading/Success/Error). */
    val deleteAccount: DeleteAccountUiState = DeleteAccountUiState.Idle,
    val appLanguage: String = "en",
    val showLanguageDialog: Boolean = false,
    /** Live RevenueCat state; this is presentation only, never an authority for quotas. */
    val subscription: EntitlementState = EntitlementState(),
    val isRestoringPurchases: Boolean = false,
    val restorePurchasesMessage: String? = null,
)
