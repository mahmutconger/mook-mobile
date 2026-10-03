package com.mcclabs.mook.feature.room

import com.mcclabs.mook.domain.billing.PaywallRequest
import com.mcclabs.mook.domain.billing.LimitReason
import com.mcclabs.mook.domain.billing.Tier
import com.mcclabs.mook.domain.analytics.AnalyticsEvent
import com.mcclabs.mook.domain.analytics.AnalyticsRepository
import com.mcclabs.mook.domain.billing.BillingConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.domain.room.DailyRoomChangeLimitReachedException
import com.mcclabs.mook.domain.room.RoomSwitchErrorCodes
import com.mcclabs.mook.domain.billing.TierDowngradeResult
import com.mcclabs.mook.domain.billing.TierDowngradeUseCase
import com.mcclabs.mook.domain.connectivity.ConnectivityObserver
import com.mcclabs.mook.domain.model.Language
import com.mcclabs.mook.domain.model.Languages
import com.mcclabs.mook.domain.repository.DiscoverRepository
import com.mcclabs.mook.domain.repository.RoomSlotRepository
import com.mcclabs.mook.domain.repository.SettingsRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoomSwitchUiState(
    val isLoading: Boolean = true,
    val languages: List<Language> = emptyList(),
    val selectedCode: String? = null,
    /** Shown once (first visit) to explain what rooms are and how to use this screen. */
    val showInfo: Boolean = false,
    /** A quota rejection is an expected product state, never an app crash. */
    val error: RoomSelectionError? = null,
    /**
     * Gereksinim 1.7: bir kademe düşüşü sonrası açık oda sayısı yeni sınırı aşıyorsa
     * dolu, aksi halde `null`. Doluyken arayüz, kullanıcının hangi fazla odaları
     * kapatacağını seçmesi için bir istem gösterir.
     */
    val downgradePrompt: RoomDowngradePrompt? = null,
    /**
     * Günlük oda değiştirme hakkı dolduğunda `true`: ekran genel bir hata yerine limit
     * sayfasını (yükseltme / yarın tekrar dene) gösterir.
     */
    val showDailyLimitSheet: Boolean = false,
    /** Limit sayfasının plan limitlerini doğru göstermesi için anlık abonelik durumu. */
    val entitlement: EntitlementState = EntitlementState(),
) {
    /**
     * Limit sayfasında "Reklam izle, odanı değiştir" düğmesi gösterilsin mi? Yalnızca ödüllü
     * reklam hakkı olan kademeler (Ücretsiz/Ekonomik); bugünkü hakkın kullanılıp
     * kullanılmadığını sunucu (`canEarnReward`) söyler.
     */
    val canEarnRoomSwitchReward: Boolean
        get() = entitlement.tier in BillingConfig.REWARDED_ELIGIBLE_TIERS
}

/**
 * [RoomSwitchViewModel.state]'in [TierDowngradeResult.RoomSelectionRequired]'dan
 * türetilen, ekranın doğrudan gösterebileceği hâli — oda kodlarını [Language]'a
 * çözer ve kullanıcının o anki seçimini taşır.
 */
data class RoomDowngradePrompt(
    val openRooms: List<Language>,
    val allowedSlots: Int,
    val selectedToClose: Set<String> = emptySet(),
) {
    /** En az bu kadar oda seçilmeden onaylanamaz — geri kalan, yeni sınıra sığar. */
    val minimumToClose: Int get() = (openRooms.size - allowedSlots).coerceAtLeast(0)
    val canConfirm: Boolean get() = selectedToClose.size >= minimumToClose
}

enum class RoomSelectionError {
    SLOT_LIMIT,
    UNAVAILABLE,
    OFFLINE,

    /** Bugünkü oda değiştirme hakkı doldu (bkz. [DailyRoomChangeLimitReachedException]). */
    DAILY_LIMIT,
}

sealed class RoomSwitchEvent {
    /** The room was changed; return to the previous screen. */
    data object Done : RoomSwitchEvent()

    /** Kullanıcı limit sayfasından planını yükseltmeyi seçti. */
    data class NavigateToPaywall(val request: PaywallRequest = PaywallRequest()) : RoomSwitchEvent()
}

/**
 * Lets the user change their current language room. Unlike [RoomGateViewModel], this
 * always shows the picker (no skip-if-set shortcut) and highlights the active room.
 */
class RoomSwitchViewModel(
    private val settingsRepository: SettingsRepository,
    private val discoverRepository: DiscoverRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val roomSlotRepository: RoomSlotRepository,
    private val tierDowngradeUseCase: TierDowngradeUseCase,
    private val subscriptionRepository: SubscriptionRepository,
    /** `limit_reached` analitik olayı için. */
    private val analyticsRepository: AnalyticsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RoomSwitchUiState())

    /** Günlük limit nedeniyle geçilemeyen, ödül sonrası tekrar denenecek oda. */
    private var pendingRoom: Language? = null
    val state: StateFlow<RoomSwitchUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<RoomSwitchEvent>()
    val events: SharedFlow<RoomSwitchEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val uid = Firebase.auth.currentUser?.uid
            val ownLanguageCode = uid?.let { discoverRepository.getProfileDetails(it)?.language?.code }
            val languages = listOf(languageIndependentRoom()) +
                Languages.ALL.filterNot { it.code.equals(ownLanguageCode, ignoreCase = true) }
            val currentRoom = settingsRepository.getRoomLanguageCode()
            val showInfo = !settingsRepository.getHasSeenRoomSwitchInfo()
            _state.update {
                it.copy(
                    isLoading = false,
                    languages = languages,
                    selectedCode = currentRoom,
                    showInfo = showInfo,
                )
            }
        }
        checkForTierDowngrade()
    }

    // ── Tier downgrade (Gereksinim 1.7) ───────────────────────────────────

    /**
     * `RoomSwitchScreen`, kullanıcının odalarını yönettiği tek yer olduğu için kademe
     * düşüşü kontrolünün doğal yuvasıdır — ekran her açıldığında (uygulama açılışı da
     * dahil, bu ekran onboarding'in bir parçasıysa) çalışır.
     */
    private fun checkForTierDowngrade() {
        viewModelScope.launch {
            val allowedSlots = subscriptionRepository.state.value.limits.roomSlots
            when (val result = tierDowngradeUseCase(allowedSlots = allowedSlots)) {
                is TierDowngradeResult.RoomSelectionRequired -> {
                    val independent = languageIndependentRoom()
                    val languages = result.openRooms.map { usage ->
                        when {
                            usage.code.equals(independent.code, ignoreCase = true) -> independent
                            else -> Languages.ALL.find { it.code.equals(usage.code, ignoreCase = true) }
                                ?: Language(code = usage.code, name = usage.code, flagEmoji = "🌐")
                        }
                    }
                    _state.update {
                        it.copy(downgradePrompt = RoomDowngradePrompt(languages, result.allowedSlots))
                    }
                }
                TierDowngradeResult.NoActionNeeded, is TierDowngradeResult.RoomsAutoClosed -> Unit
            }
        }
    }

    /** Toggles one room in the current close-selection. */
    fun onToggleRoomToClose(code: String) {
        _state.update { state ->
            val prompt = state.downgradePrompt ?: return@update state
            val selected = prompt.selectedToClose
            val next = if (code in selected) selected - code else selected + code
            state.copy(downgradePrompt = prompt.copy(selectedToClose = next))
        }
    }

    /** The user explicitly chose which rooms to close. */
    fun onConfirmCloseRooms() {
        val prompt = _state.value.downgradePrompt ?: return
        if (!prompt.canConfirm) return
        viewModelScope.launch {
            roomSlotRepository.closeRooms(prompt.selectedToClose.toList())
            _state.update { it.copy(downgradePrompt = null, selectedCode = settingsRepository.getRoomLanguageCode()) }
        }
    }

    /**
     * The user dismissed the prompt without choosing (Gereksinim 1.7: "If they ignore
     * it, automatically close the oldest used rooms"). Re-runs the same check with
     * auto-close enabled rather than guessing at the selection here, so the decision
     * of *which* rooms to close stays in one place: [TierDowngradeUseCase].
     */
    fun onDowngradePromptDismissed() {
        val allowedSlots = _state.value.downgradePrompt?.allowedSlots
        _state.update { it.copy(downgradePrompt = null) }
        if (allowedSlots == null) return
        viewModelScope.launch {
            tierDowngradeUseCase(allowedSlots = allowedSlots, autoCloseIfOverLimit = true)
            _state.update { it.copy(selectedCode = settingsRepository.getRoomLanguageCode()) }
        }
    }

    fun onRoomSelected(language: Language) {
        // Gereksinim 1.2: oda değişimi sunucuya yazan bir eylemdir; bağlantı yokken denemek
        // yalnızca belirsiz bir hataya yol açar — bunun yerine anında yerelleştirilmiş bir
        // çevrimdışı hatası gösterilir ve ağ çağrısı hiç yapılmaz.
        if (!connectivityObserver.isOnline.value) {
            _state.update { it.copy(error = RoomSelectionError.OFFLINE) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            try {
                settingsRepository.setRoomLanguageCode(language.code)
                _events.emit(RoomSwitchEvent.Done)
            } catch (limitReached: DailyRoomChangeLimitReachedException) {
                // Beklenen ürün durumu: hata metni değil, limit sayfası gösterilir. Seçilen oda
                // hatırlanır; kullanıcı ödüllü reklam izlerse geçiş otomatik tekrarlanır.
                pendingRoom = language
                val entitlement = subscriptionRepository.state.value
                analyticsRepository.track(
                    AnalyticsEvent.LimitReached(
                        reason = LimitReason.ROOM_SWITCHES,
                        tier = entitlement.tier,
                        source = "room_switch",
                        upgradeAvailable = entitlement.tier != Tier.PREMIUM,
                    ),
                )
                _state.update {
                    it.copy(
                        error = null,
                        showDailyLimitSheet = true,
                        entitlement = subscriptionRepository.state.value,
                    )
                }
            } catch (error: Exception) {
                _state.update { it.copy(error = error.toRoomSelectionError()) }
            }
        }
    }

    /** Limit sayfası kapatıldı; kullanıcı mevcut odasında kalır. */
    fun onDailyLimitSheetDismissed() {
        pendingRoom = null
        _state.update { it.copy(showDailyLimitSheet = false) }
    }

    /**
     * Ödüllü reklam doğrulandı (sunucu `rewardedRoomSwitches` sayacını artırdı): limit sayfası
     * kapanır ve kullanıcının seçtiği odaya geçiş, yeni hakla OTOMATİK olarak tekrarlanır.
     */
    fun onRoomSwitchRewardConfirmed() {
        val room = pendingRoom
        pendingRoom = null
        _state.update { it.copy(showDailyLimitSheet = false) }
        if (room != null) onRoomSelected(room)
    }

    /** Limit sayfasından yükseltme seçildi: sayfayı kapatıp Paywall'a yönlendirir. */
    fun onUpgradeFromLimitSheet() {
        _state.update { it.copy(showDailyLimitSheet = false) }
        viewModelScope.launch { _events.emit(RoomSwitchEvent.NavigateToPaywall(PaywallRequest(LimitReason.ROOM_SWITCHES))) }
    }

    /** Limit sayfasında "Standart'ı ücretsiz dene" seçildi. */
    fun onTrialFromLimitSheet() {
        _state.update { it.copy(showDailyLimitSheet = false) }
        viewModelScope.launch {
            _events.emit(RoomSwitchEvent.NavigateToPaywall(PaywallRequest(LimitReason.ROOM_SWITCHES, preselectTrial = true)))
        }
    }

    /** Dismisses the first-visit info dialog and remembers it so it never shows again. */
    fun onInfoDismissed() {
        _state.update { it.copy(showInfo = false) }
        viewModelScope.launch { settingsRepository.setHasSeenRoomSwitchInfo(true) }
    }
}

internal fun Throwable.toRoomSelectionError(): RoomSelectionError = when {
    this is DailyRoomChangeLimitReachedException -> RoomSelectionError.DAILY_LIMIT
    message?.contains(RoomSwitchErrorCodes.SLOT_LIMIT, ignoreCase = true) == true -> RoomSelectionError.SLOT_LIMIT
    else -> RoomSelectionError.UNAVAILABLE
}
