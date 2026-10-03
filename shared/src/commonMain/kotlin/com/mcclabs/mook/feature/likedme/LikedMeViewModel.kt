package com.mcclabs.mook.feature.likedme

import com.mcclabs.mook.domain.billing.PaywallRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.analytics.AnalyticsEvent
import com.mcclabs.mook.domain.analytics.AnalyticsRepository
import com.mcclabs.mook.domain.billing.AdFrequencyRules
import com.mcclabs.mook.domain.billing.BillingConfig
import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.LimitReason
import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.domain.billing.Tier
import com.mcclabs.mook.domain.connectivity.ConnectivityObserver
import com.mcclabs.mook.domain.model.LikedMeEntry
import com.mcclabs.mook.domain.repository.LikedMeRepository
import com.mcclabs.mook.domain.repository.LikedMeUnlockResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.error_offline
import mook.shared.generated.resources.liked_me_error
import mook.shared.generated.resources.liked_me_not_liked_anymore
import mook.shared.generated.resources.liked_me_unlock_failed
import org.jetbrains.compose.resources.getString

/** "Beni Beğenenler" ekranının tek seferlik yönlendirme olayları. */
sealed interface LikedMeEvent {
    data class NavigateToProfile(val profileUid: String) : LikedMeEvent
    data class NavigateToPaywall(val request: PaywallRequest = PaywallRequest()) : LikedMeEvent
}

/**
 * "Beni Beğenenler" ekranının ViewModel'i (MVVM + StateFlow).
 *
 * - Liste [LikedMeRepository] ile sunucudan gelir; kilitli girişlerde kimlik bilgisi yoktur.
 * - Kilit açma: Premium'da tüm liste zaten açıktır; diğer planlarda günlük hak (Standart) veya
 *   ödüllü reklamla kazanılan hak (Ücretsiz/Ekonomik) kullanılır. Hak yoksa limit sayfası açılır;
 *   ödül sunucuda doğrulanınca bekleyen kilit açma OTOMATİK yeniden denenir.
 * - Plan değişince (ör. Premium satın alındı) liste yeniden yüklenir; reklam yuvaları plana
 *   göre yeniden yerleştirilir (her 8. satır — bkz. [AdFrequencyRules]).
 */
class LikedMeViewModel(
    private val likedMeRepository: LikedMeRepository,
    private val subscriptions: SubscriptionRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val analytics: AnalyticsRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(LikedMeUiState())
    val state: StateFlow<LikedMeUiState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<LikedMeEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<LikedMeEvent> = mutableEvents.asSharedFlow()

    /** Ödüllü reklam doğrulanınca yeniden denenecek giriş. */
    private var pendingUnlockToken: String? = null

    init {
        viewModelScope.launch {
            subscriptions.state
                .distinctUntilChangedBy { it.isResolved to it.tier }
                .collect { entitlement ->
                    val tierChanged = mutableState.value.entitlement.isResolved &&
                        mutableState.value.entitlement.tier != entitlement.tier
                    mutableState.update { it.withEntitlement(entitlement) }
                    if (tierChanged) load()
                }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            if (!connectivityObserver.isOnline.value) {
                mutableState.update { it.copy(isLoading = false, errorMessage = getString(Res.string.error_offline)) }
                return@launch
            }
            mutableState.update { it.copy(isLoading = it.entries.isEmpty(), errorMessage = null) }
            likedMeRepository.load().fold(
                onSuccess = { page ->
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            totalCount = page.totalCount,
                            hasMore = page.hasMore,
                            revealAll = page.revealAll,
                            unlocksRemainingToday = page.unlocksRemainingToday,
                        ).withEntries(page.entries)
                    }
                },
                onFailure = {
                    val message = getString(Res.string.liked_me_error)
                    mutableState.update { state ->
                        // Önceden yüklenmiş bir liste varsa ekranı silmek yerine yalnızca bildir.
                        if (state.entries.isEmpty()) state.copy(isLoading = false, errorMessage = message)
                        else state.copy(isLoading = false, message = message)
                    }
                },
            )
        }
    }

    /** Satıra dokunuldu: açık profil → profil sayfası, kilitli → kilit açma. */
    fun onEntryClicked(entry: LikedMeEntry) {
        val profile = entry.profile
        if (entry.isUnlocked && profile != null) {
            mutableEvents.tryEmit(LikedMeEvent.NavigateToProfile(profile.uid))
        } else {
            onUnlockClicked(entry)
        }
    }

    fun onUnlockClicked(entry: LikedMeEntry) {
        if (entry.isUnlocked || mutableState.value.unlockingToken != null) return
        unlock(entry.entryToken)
    }

    private fun unlock(entryToken: String) {
        viewModelScope.launch {
            if (!connectivityObserver.isOnline.value) {
                mutableState.update { it.copy(message = getString(Res.string.error_offline)) }
                return@launch
            }
            mutableState.update { it.copy(unlockingToken = entryToken) }
            val result = likedMeRepository.unlock(entryToken)
            mutableState.update { it.copy(unlockingToken = null) }
            when (result) {
                is LikedMeUnlockResult.Unlocked -> {
                    pendingUnlockToken = null
                    load()
                }
                LikedMeUnlockResult.DailyLimitReached, LikedMeUnlockResult.UpgradeRequired -> {
                    pendingUnlockToken = entryToken
                    val entitlement = mutableState.value.entitlement
                    analytics.track(
                        AnalyticsEvent.LimitReached(
                            reason = LimitReason.LIKED_ME_UNLOCKS,
                            tier = entitlement.tier,
                            source = "liked_me",
                            upgradeAvailable = entitlement.tier != Tier.PREMIUM,
                        ),
                    )
                    mutableState.update { it.copy(limitReason = LimitReason.LIKED_ME_UNLOCKS) }
                }
                LikedMeUnlockResult.NotLikedByProfile -> {
                    mutableState.update { it.copy(message = getString(Res.string.liked_me_not_liked_anymore)) }
                    load()
                }
                is LikedMeUnlockResult.Failed ->
                    mutableState.update { it.copy(message = getString(Res.string.liked_me_unlock_failed)) }
            }
        }
    }

    /** Ödüllü reklam sunucuda (SSV) doğrulandı: limit sayfasını kapat ve kilidi yeniden dene. */
    fun onRewardedUnlockConfirmed() {
        mutableState.update { it.copy(limitReason = null) }
        pendingUnlockToken?.let(::unlock)
    }

    fun onUpgradeClicked() {
        mutableState.update { it.copy(limitReason = null) }
        mutableEvents.tryEmit(LikedMeEvent.NavigateToPaywall(PaywallRequest(LimitReason.LIKED_ME_UNLOCKS)))
    }

    /** Limit sayfasında "Standart'ı ücretsiz dene" seçildi. */
    fun onTrialClicked() {
        pendingUnlockToken = null
        mutableState.update { it.copy(limitReason = null) }
        mutableEvents.tryEmit(LikedMeEvent.NavigateToPaywall(PaywallRequest(LimitReason.LIKED_ME_UNLOCKS, preselectTrial = true)))
    }

    fun onLimitSheetDismissed() {
        pendingUnlockToken = null
        mutableState.update { it.copy(limitReason = null) }
    }

    fun onMessageShown() {
        mutableState.update { it.copy(message = null) }
    }

    /** Bu planda ödüllü reklamla "beni beğenenler" açma hakkı kazanılabilir mi? */
    fun canEarnRewardedUnlock(): Boolean = mutableState.value.entitlement.tier in BillingConfig.REWARDED_ELIGIBLE_TIERS

    private fun LikedMeUiState.withEntitlement(entitlement: EntitlementState): LikedMeUiState =
        copy(entitlement = entitlement).withEntries(entries)

    private fun LikedMeUiState.withEntries(entries: List<LikedMeEntry>): LikedMeUiState = copy(
        entries = entries,
        items = AdFrequencyRules.interleaveLikedMeAds(
            items = entries,
            showAds = showsAds,
            item = { LikedMeListItem.Profile(it) },
            ad = { slot -> LikedMeListItem.NativeAd(slot) },
        ),
    )
}
