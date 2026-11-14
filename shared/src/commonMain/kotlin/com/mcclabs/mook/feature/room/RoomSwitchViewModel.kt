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
import com.mcclabs.mook.domain.model.RoomSwitchUsage
import com.mcclabs.mook.domain.model.RoomUsage
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlin.time.TimeSource
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
    /**
     * Kullanıcının şu an AÇIK (erişimi olan) odaları, büyük harfli kodlarla. Bu odalar
     * arasında geçiş ücretsizdir; listede olmayan bir oda "kilitli"dir ve açmak 1 hak kullanır.
     */
    val openRoomCodes: Set<String> = emptySet(),
    /** Planın aynı anda açık tutabileceği oda sayısı; `null` = sınırsız. */
    val roomSlots: Int? = null,
    /** Bugün kalan oda değiştirme hakkı; `null` = sınırsız ya da henüz bilinmiyor. */
    val switchesRemaining: Int? = null,
    /** Günlük hak sınırsız mı (Standart/Premium)? */
    val hasUnlimitedSwitches: Boolean = false,
    /** Kilitli bir oda seçildi; hak kullanımını onaylatan diyalog. */
    val confirmPrompt: RoomSwitchConfirm? = null,
    /** Doluyken "Oda hazırlanıyor" ilerleme ekranı gösterilir. */
    val switchingTo: Language? = null,
) {
    fun isOpen(code: String): Boolean = code.uppercase() in openRoomCodes

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

/**
 * Kilitli bir odaya geçmeden önce gösterilen onay: "X oda değiştirme hakkın var, kullanmak
 * istediğinden emin misin?". Slotlar doluysa yer açmak için kapanacak oda da gösterilir.
 */
data class RoomSwitchConfirm(
    val target: Language,
    /** Bugün kalan hak; `null` = bilinmiyor (sunucu yine de kotayı uygular). */
    val switchesRemaining: Int?,
    val roomToClose: Language?,
)

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

    /** Açık odalar ve son kullanım zamanları — kapanacak odayı tahmin etmek için. */
    private var openRooms: List<RoomUsage> = emptyList()
    private var usage: RoomSwitchUsage = RoomSwitchUsage()
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
            refreshAccess()
        }
        // Plan değişirse (ör. yükseltme sonrası geri dönüldüğünde) slot ve hak göstergesi güncellenir.
        viewModelScope.launch {
            subscriptionRepository.state.collect { entitlement ->
                _state.update { it.withAccess(entitlement) }
            }
        }
        checkForTierDowngrade()
    }

    // ── Erişim göstergesi (açık odalar + kalan hak) ───────────────────────

    /** Açık odaları ve bugünkü kullanımı sunucudan yeniden okur. */
    private suspend fun refreshAccess() {
        val rooms = roomSlotRepository.openRooms()
        val current = _state.value.selectedCode
        // Eski hesaplarda `roomLanguageCodes` olmayabilir; aktif oda her zaman açık sayılır.
        openRooms = if (current != null && rooms.none { it.code.equals(current, ignoreCase = true) }) {
            rooms + RoomUsage(code = current, lastActiveAtMillis = Long.MAX_VALUE)
        } else {
            rooms
        }
        usage = roomSlotRepository.roomSwitchUsageToday()
        _state.update { it.withAccess(subscriptionRepository.state.value) }
    }

    private fun RoomSwitchUiState.withAccess(entitlement: EntitlementState): RoomSwitchUiState {
        val dailyLimit = BillingConfig.effectiveDailyRoomSwitchLimit(
            entitlement.limits, entitlement.tier, usage.rewardedRoomSwitches,
        )
        return copy(
            entitlement = entitlement,
            openRoomCodes = openRooms.map { it.code.uppercase() }.toSet(),
            roomSlots = entitlement.limits.roomSlots,
            hasUnlimitedSwitches = dailyLimit == null,
            switchesRemaining = if (dailyLimit != null && usage.isKnown) {
                (dailyLimit - usage.roomSwitches).coerceAtLeast(0)
            } else {
                null
            },
        )
    }

    /**
     * Yeni bir oda açılırsa slot sınırı nedeniyle kapanacak oda (sunucudaki `planRoomSwitch`
     * ile aynı kural: en uzun süredir aktif kullanılmayan oda). Yer varsa `null`.
     */
    private suspend fun predictRoomToClose(): Language? {
        val slots = _state.value.roomSlots ?: return null
        if (openRooms.size < slots.coerceAtLeast(1)) return null
        val victim = openRooms.minByOrNull { it.lastActiveAtMillis } ?: return null
        return resolveRoomLanguage(victim.code)
    }

    private suspend fun resolveRoomLanguage(code: String): Language {
        val independent = languageIndependentRoom()
        return when {
            code.equals(independent.code, ignoreCase = true) -> independent
            else -> Languages.ALL.find { it.code.equals(code, ignoreCase = true) }
                ?: Language(code = code, name = code, flagEmoji = "🌐")
        }
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
                    val languages = result.openRooms.map { usage -> resolveRoomLanguage(usage.code) }
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
            refreshAccess()
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
            refreshAccess()
        }
    }

    /**
     * Kullanıcı bir odaya dokundu.
     * - Aktif oda: bir şey yapılmaz.
     * - Açık oda: hak düşmeden doğrudan geçilir.
     * - Kilitli oda + hak bitti: istek atılmadan limit sayfası (plan yükselt / reklam) açılır.
     * - Kilitli oda + hak var: önce "X hakkın var, emin misin?" onayı istenir.
     */
    fun onRoomSelected(language: Language) {
        val state = _state.value
        if (state.switchingTo != null) return
        if (language.code.equals(state.selectedCode, ignoreCase = true)) return
        // Gereksinim 1.2: oda değişimi sunucuya yazan bir eylemdir; bağlantı yokken denemek
        // yalnızca belirsiz bir hataya yol açar — bunun yerine anında yerelleştirilmiş bir
        // çevrimdışı hatası gösterilir ve ağ çağrısı hiç yapılmaz.
        if (!connectivityObserver.isOnline.value) {
            _state.update { it.copy(error = RoomSelectionError.OFFLINE) }
            return
        }
        if (state.isOpen(language.code)) {
            performSwitch(language)
            return
        }
        if (state.switchesRemaining == 0) {
            showDailyLimit(language)
            return
        }
        if (state.hasUnlimitedSwitches) {
            performSwitch(language)
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    error = null,
                    confirmPrompt = RoomSwitchConfirm(
                        target = language,
                        switchesRemaining = it.switchesRemaining,
                        roomToClose = predictRoomToClose(),
                    ),
                )
            }
        }
    }

    /** Onay diyaloğunda hak kullanımı onaylandı. */
    fun onConfirmSwitch() {
        val prompt = _state.value.confirmPrompt ?: return
        _state.update { it.copy(confirmPrompt = null) }
        performSwitch(prompt.target)
    }

    fun onDismissConfirm() {
        _state.update { it.copy(confirmPrompt = null) }
    }

    /** Hak bitti uyarısından (özet kartındaki düğme) doğrudan Paywall'a gider. */
    fun onUpgradeClicked() {
        viewModelScope.launch {
            _events.emit(RoomSwitchEvent.NavigateToPaywall(PaywallRequest(LimitReason.ROOM_SWITCHES)))
        }
    }

    private fun performSwitch(language: Language) {
        if (_state.value.switchingTo != null) return
        viewModelScope.launch {
            _state.update { it.copy(error = null, switchingTo = language) }
            val startedAt = TimeSource.Monotonic.markNow()
            try {
                settingsRepository.setRoomLanguageCode(language.code)
                // İlerleme ekranı bir anlığına yanıp sönmesin; en az kısa bir süre görünür kalır.
                val remaining = MIN_PREPARING_MILLIS - startedAt.elapsedNow().inWholeMilliseconds
                if (remaining > 0) delay(remaining)
                _events.emit(RoomSwitchEvent.Done)
            } catch (limitReached: DailyRoomChangeLimitReachedException) {
                // Yerel sayaç eskiyse sunucu yine de reddeder: limit sayfası gösterilir.
                _state.update { it.copy(switchingTo = null) }
                showDailyLimit(language)
                refreshAccess()
            } catch (error: Exception) {
                _state.update { it.copy(switchingTo = null, error = error.toRoomSelectionError()) }
            }
        }
    }

    /**
     * Beklenen ürün durumu: hata metni değil, limit sayfası gösterilir. Seçilen oda
     * hatırlanır; kullanıcı ödüllü reklam izlerse geçiş otomatik tekrarlanır.
     */
    private fun showDailyLimit(language: Language) {
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
                confirmPrompt = null,
                showDailyLimitSheet = true,
                entitlement = entitlement,
            )
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
        // Kullanıcı odayı zaten seçmişti; yeni hakla ikinci bir onay sorulmadan geçilir.
        if (room != null) performSwitch(room)
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

/** "Oda hazırlanıyor" ekranının en kısa görünme süresi. */
private const val MIN_PREPARING_MILLIS = 700L

internal fun Throwable.toRoomSelectionError(): RoomSelectionError = when {
    this is DailyRoomChangeLimitReachedException -> RoomSelectionError.DAILY_LIMIT
    message?.contains(RoomSwitchErrorCodes.SLOT_LIMIT, ignoreCase = true) == true -> RoomSelectionError.SLOT_LIMIT
    else -> RoomSelectionError.UNAVAILABLE
}
