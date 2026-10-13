package com.mcclabs.mook.feature.discover

import com.mcclabs.mook.ui.loading.ImageLoadStatus
import com.mcclabs.mook.ui.loading.progressiveLoadingEnabled

import com.mcclabs.mook.domain.billing.LimitReason
import com.mcclabs.mook.domain.billing.PaywallRequest
import com.mcclabs.mook.domain.analytics.trackGateDecision
import com.mcclabs.mook.ads.LikeInterstitialAttempt
import com.mcclabs.mook.domain.billing.AdDisplayRules
import com.mcclabs.mook.domain.interaction.SetLikeUseCase
import com.mcclabs.mook.ui.optimistic.OptimisticStateController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.Flow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.repository.DiscoverRepository
import com.mcclabs.mook.domain.repository.InteractionRepository
import com.mcclabs.mook.domain.repository.SettingsRepository
import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.domain.billing.Feature
import com.mcclabs.mook.domain.billing.FeatureGate
import com.mcclabs.mook.domain.billing.GateDecision
import com.mcclabs.mook.domain.billing.UsageSnapshot
import com.mcclabs.mook.domain.billing.RecoverPendingSwipeActionsUseCase
import com.mcclabs.mook.util.getCurrentTimeMillis
import com.mcclabs.mook.domain.billing.LikeInterstitialGateway
import com.mcclabs.mook.domain.analytics.AnalyticsRepository
import com.mcclabs.mook.domain.billing.BoostManagerUseCase
import com.mcclabs.mook.domain.repository.BoostSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.mcclabs.mook.domain.model.DiscoverProfile
import kotlin.collections.ArrayDeque
import com.mcclabs.mook.domain.model.Languages
import com.mcclabs.mook.domain.model.MatchResult
import com.mcclabs.mook.domain.model.MatchSettings
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import com.mcclabs.mook.data.appFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.error_generic
import mook.shared.generated.resources.error_swipe_failed
import mook.shared.generated.resources.info_swipe_queued_offline
import mook.shared.generated.resources.report_submitted
import mook.shared.generated.resources.report_failed
import mook.shared.generated.resources.user_blocked
import mook.shared.generated.resources.block_failed
import mook.shared.generated.resources.discover_boost_started
import mook.shared.generated.resources.discover_rewind_success

sealed class DiscoverEvent {
    data class NavigateToProfile(val profileId: String) : DiscoverEvent()
    /** Emitted on a mutual match; carries the id of the user who was matched with. */
    data class NavigateToMatch(val matchedUserId: String) : DiscoverEvent()
    data class ShowSnackbar(val message: String) : DiscoverEvent()
    /** Free user hit the daily like limit and chose to upgrade. */
    /** Paywall'a yönlendir; [request] başlığı ve deneme ön seçimini belirler. */
    data class NavigateToPaywall(val request: PaywallRequest = PaywallRequest()) : DiscoverEvent()
}

/**
 * Backs the Discovery grid.
 *
 * The screen is a paged browse surface rather than a card deck: profiles accumulate as the
 * user scrolls, and acting on someone (liking here, or liking/passing from their profile)
 * takes them out of the feed. Filters live in [SettingsRepository], so a change there
 * restarts paging from the top without the user leaving the screen.
 */
class DiscoverViewModel(
    private val repository: DiscoverRepository,
    private val interactionRepository: InteractionRepository,
    private val settingsRepository: SettingsRepository,
    private val subscriptions: SubscriptionRepository,
    private val recoverPendingSwipeActionsUseCase: RecoverPendingSwipeActionsUseCase,
    private val likeInterstitialGateway: LikeInterstitialGateway,
    /** Gereksinim 2.12 (Faz 4): bkz. [BoostManagerUseCase] KDoc'u. */
    private val boostManagerUseCase: BoostManagerUseCase,
    /** Gereksinim 2.14 (Faz 4): karşılıklı eşleşme oluştuğunda "match_created" olayını
     *  Free/Premium havuz sağlığı kırılımıyla kaydetmek için — bkz. [AnalyticsRepository]. */
    private val analyticsRepository: AnalyticsRepository,
    private val setLikeUseCase: SetLikeUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(DiscoverUiState(isLoading = progressiveLoadingEnabled))
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()

    private val _events = Channel<DiscoverEvent>(Channel.UNLIMITED)
    val events: Flow<DiscoverEvent> = _events.receiveAsFlow()
    private val optimisticState = OptimisticStateController<DiscoverUiState, String>(_state, viewModelScope)
    private var likeRevision = 0L

    /** Reklam istenmeden önce yerel kota kontrolü yapan saf mantık (bkz. Gereksinim 1.1). */
    private val featureGate = FeatureGate()

    /** The filters the visible page was loaded with; used by refresh and paging. */
    private var currentSettings: MatchSettings = MatchSettings()
    private var contentGeneration: Long = 0

    /** Görsel sonucu yalnızca mevcut veri kümesine aitse duruma uygulanır. */
    fun onImageLoadChanged(url: String, status: ImageLoadStatus) {
        _state.update { it.copy(imageLoading = it.imageLoading.onResult(url, status)) }
    }

    init {
        observeEntitlement()
        observeSettingsAndReload()
        // Gereksinim 1.3: bir önceki oturumda reklam kapanmadan süreç öldüyse, kalıcı
        // kuyrukta kalmış beğeni eylemlerini şimdi sessizce sunucuya gönder.
        viewModelScope.launch { recoverPendingSwipeActionsUseCase() }
    }

    /** Keeps every tier's client-side gate in sync with RevenueCat, not just Premium. */
    private fun observeEntitlement() {
        viewModelScope.launch {
            subscriptions.state.collect { entitlement ->
                _state.update { it.copy(entitlement = entitlement) }
            }
        }
        viewModelScope.launch { subscriptions.refresh() }
    }

    /**
     * Reloads the grid whenever the filters change, so applying a filter takes effect
     * without the user having to leave and re-enter Discovery.
     */
    private fun observeSettingsAndReload() {
        viewModelScope.launch {
            // Seed the cache from Firestore before the first emission is consumed.
            runCatching { settingsRepository.getSettings() }

            settingsRepository.observeSettings().collectLatest { settings ->
                currentSettings = settings
                applyFilterChips(settings)
                loadFirstPage(settings)
            }
        }
    }

    /** Mirrors the stored filters into the chip row above the grid. */
    private fun applyFilterChips(settings: MatchSettings) {
        val code = settings.roomLanguageCode
        _state.update {
            it.copy(
                roomLanguage = Languages.fromCode(code),
                isLanguageIndependentRoom =
                    code != null && code.equals(Languages.LANGUAGE_INDEPENDENT_ROOM_CODE, ignoreCase = true),
                ageRangeStart = settings.ageRangeStart,
                ageRangeEnd = settings.ageRangeEnd,
            )
        }
    }

    /**
     * Loads page one. Paging state lives in the repository, so it is reset explicitly here
     * rather than relying on the filters having changed — the settings flow re-emits on every
     * save, including saves that leave the values untouched.
     */
    private suspend fun loadFirstPage(settings: MatchSettings) {
        val generation = ++contentGeneration
        val refreshing = _state.value.isRefreshing
        _state.update { it.copy(isLoading = !refreshing, error = null) }
        try {
            repository.resetDiscoverPaging()
            val profiles = repository.getDiscoverProfiles(settings)
            if (progressiveLoadingEnabled && generation != contentGeneration) return
            if (progressiveLoadingEnabled) {
                // Profil metni, kota ve öğretici sorgularını beklemeden gösterilir.
                _state.update {
                    it.copy(
                        profiles = profiles,
                        imageLoading = it.imageLoading.reconcile(profiles.map { profile -> profile.photoUrls.firstOrNull() }),
                        likedProfileIds = it.likedProfileIds,
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        endReached = !repository.hasMoreProfiles(),
                    )
                }
            }
            val hasSeenTutorial = settingsRepository.getHasSeenLikedMeTutorial()
            val revision = likeRevision
            val likeUsage = repository.getLikeUsageToday()
            if (progressiveLoadingEnabled && generation != contentGeneration) return
            _state.update {
                it.copy(
                    profiles = if (progressiveLoadingEnabled) it.profiles else profiles,
                    likedProfileIds = it.likedProfileIds,
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    endReached = !repository.hasMoreProfiles(),
                    hasSeenLikedMeTutorial = hasSeenTutorial,
                    likedMeTutorialProfileId = profiles.firstOrNull { p -> p.hasLikedMe }?.id,
                    swipesUsedToday = if (revision == likeRevision && !it.isSwipeInFlight) likeUsage.likes else it.swipesUsedToday,
                    rewardedLikesToday = likeUsage.rewardedLikes,
                    likesEver = if (revision == likeRevision && !it.isSwipeInFlight) likeUsage.likesEver else it.likesEver,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (progressiveLoadingEnabled && generation != contentGeneration) return
            _state.update {
                it.copy(
                    error = e.message ?: getString(Res.string.error_generic),
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                )
            }
        }
    }

    /**
     * Appends the next page. Called when the grid scrolls near its end; a no-op while another
     * load is in flight or once the room is exhausted, so scrolling cannot stack requests.
     */
    fun loadMore() {
        val s = _state.value
        if (s.isLoading || s.isLoadingMore || s.isRefreshing || s.endReached) return

        val generation = contentGeneration
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            try {
                val next = repository.getDiscoverProfiles(currentSettings)
                if (progressiveLoadingEnabled && generation != contentGeneration) return@launch
                _state.update { current ->
                    // Guard against duplicates: a profile can surface twice if documents shift
                    // between pages (someone's lastActiveTimestamp updates mid-scroll).
                    val existing = current.profiles.mapTo(mutableSetOf()) { p -> p.id }
                    current.copy(
                        profiles = current.profiles + next.filter { p -> existing.add(p.id) },
                        imageLoading = current.imageLoading.reconcile((current.profiles + next).map { profile -> profile.photoUrls.firstOrNull() }),
                        isLoadingMore = false,
                        endReached = next.isEmpty() || !repository.hasMoreProfiles(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (progressiveLoadingEnabled && generation != contentGeneration) return@launch
                // A failed page must not clear what is already on screen; the user can scroll
                // again to retry.
                _state.update { it.copy(isLoadingMore = false) }
                _events.send(DiscoverEvent.ShowSnackbar(e.message ?: getString(Res.string.error_generic)))
            }
        }
    }

    /** Pull-to-refresh: rebuilds the feed from the top with the current filters. */
    fun refresh() {
        if (_state.value.isRefreshing) return
        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch { loadFirstPage(currentSettings) }
    }

    /** Re-runs the query after a failure; the settings flow does not re-emit on its own. */
    fun retry() {
        viewModelScope.launch {
            loadFirstPage(settingsRepository.getSettings())
        }
    }

    /**
     * Drops cards for people acted on elsewhere — typically a like or pass made on the profile
     * screen the user has just come back from. Called when Discovery resumes, so the feed is
     * consistent without re-querying or losing the scroll position.
     */
    fun pruneActedOnProfiles() {
        val acted = repository.actedOnProfileIds()
        if (acted.isEmpty()) return
        _state.update { current ->
            val remaining = current.profiles.filterNot { it.id in acted }
            if (remaining.size == current.profiles.size) current
            else current.copy(
                profiles = remaining,
                imageLoading = current.imageLoading.reconcile(remaining.map { profile -> profile.photoUrls.firstOrNull() }),
                likedProfileIds = current.likedProfileIds - acted,
                likedMeTutorialProfileId = remaining.firstOrNull { it.hasLikedMe }?.id,
            )
        }
        // Acting elsewhere also spends the daily allowance; re-read it so the limit sheet
        // fires at the right moment.
        viewModelScope.launch {
            val revision = likeRevision
            val usage = repository.getLikeUsageToday()
            _state.update { it.copy(
                swipesUsedToday = if (revision == likeRevision && !it.isSwipeInFlight) usage.likes else it.swipesUsedToday,
                rewardedLikesToday = usage.rewardedLikes) }
        }
    }

    fun dismissLikedMeTutorial() {
        viewModelScope.launch {
            settingsRepository.setHasSeenLikedMeTutorial(true)
            _state.update { it.copy(hasSeenLikedMeTutorial = true) }
        }
    }

    // ── Liking ──────────────────────────────────────────────────────────────

    /** Kalbi anında günceller; aynı anda yalnızca bir kaydırma işlemi kabul edilir. */
    fun likeProfile(profileId: String) {
        val before = _state.value
        if (before.isSwipeInFlight || before.isLoading || before.isRefreshing) return
        if (before.profiles.none { it.id == profileId }) return
        val isLiked = profileId !in before.likedProfileIds
        if (isLiked && !consumeSwipeOrBlock()) return
        likeRevision++
        val isFree = before.entitlement.isResolved && before.entitlement.limits.showsAds
        optimisticState.execute(
            key = profileId,
            optimistic = {
                it.copy(isSwipeInFlight = true, likedProfileIds =
                    if (isLiked) it.likedProfileIds + profileId else it.likedProfileIds - profileId)
            },
            rollback = { current, _ ->
                current.copy(
                    likedProfileIds = if (isLiked) current.likedProfileIds - profileId else current.likedProfileIds + profileId,
                    swipesUsedToday = if (isLiked) (current.swipesUsedToday - 1).coerceAtLeast(0) else current.swipesUsedToday,
                )
            },
            settle = { it.copy(isSwipeInFlight = false) },
            sync = { setLikeUseCase(profileId, isLiked) },
            onFailure = { _events.trySend(DiscoverEvent.ShowSnackbar("Beğeni güncellenemedi. Lütfen tekrar deneyin.")) },
            onSuccess = { result ->
                if (!isLiked) {
                    repository.unmarkActedOn(profileId)
                } else {
                    repository.markActedOn(profileId)
                    _state.update { it.copy(likesEver = it.likesEver + 1) }
                    if (result is MatchResult.MutualMatch) {
                        analyticsRepository.logMatchCreated(viewerIsPremium = !isFree)
                        _events.send(DiscoverEvent.NavigateToMatch(profileId))
                    }
                    if (AdDisplayRules.allowsLikeInterstitial(result)) {
                        val attempt = likeInterstitialGateway.attemptShowAfterTransition(isFree, _state.value.likesEver)
                        likeInterstitialGateway.recordAction(isFree, attempt, true)
                    } else if (AdDisplayRules.countsTowardAdCadence(result)) {
                        likeInterstitialGateway.recordAction(isFree, LikeInterstitialAttempt(countSuccessfulLike = true), true)
                    }
                }
            },
        )
    }

    /** Passes a tile without leaving Discovery; its immediate undo is the Rewind control. */
    fun passProfile(profileId: String) {
        if (_state.value.isSwipeInFlight) return
        val profile = _state.value.profiles.find { it.id == profileId } ?: return
        val index = _state.value.profiles.indexOfFirst { it.id == profileId }
        _state.update { it.copy(isSwipeInFlight = true) }
        removeProfile(profileId)
        repository.markActedOn(profileId)
        viewModelScope.launch {
            when (interactionRepository.swipeUser(profileId, isLike = false)) {
                is MatchResult.Error -> {
                    insertProfile(profile, index)
                    repository.unmarkActedOn(profileId)
                    _state.update { it.copy(isSwipeInFlight = false) }
                    _events.send(DiscoverEvent.ShowSnackbar(getString(Res.string.error_swipe_failed)))
                }
                is MatchResult.QueuedOffline -> {
                    // Gereksinim 1 (Faz 6): kart zaten arayüzden kaldırıldı, GERİ ALINMAZ. Sunucu
                    // henüz bu geçmeyi onaylamadığından (kuyrukta bekliyor) [passedProfileStack]'e
                    // BİLEREK EKLENMEZ -- aksi halde "geri al", sunucudaki farklı (daha eski, zaten
                    // senkronize) bir geçmeyi geri alabilirdi.
                    _state.update { it.copy(isSwipeInFlight = false) }
                    _events.send(DiscoverEvent.ShowSnackbar(getString(Res.string.info_swipe_queued_offline)))
                }
                else -> {
                    // Gereksinim 1.14: TEK bir alan yerine bir yığına (stack) eklenir — art
                    // arda birden fazla geçme, sunucunun günlük kotası izin verdiği sürece
                    // her biri ayrı ayrı geri alınabilsin diye. Eski davranışta bu alan her
                    // yeni geçmede ÜZERİNE YAZILIYORDU; bu, ilk geçmenin kalıcı olarak geri
                    // alınamaz hale gelmesine neden oluyordu.
                    passedProfileStack.addLast(PassedProfile(profile, index))
                    _state.update { it.copy(hasRewindablePass = true, isSwipeInFlight = false) }
                }
            }
        }
    }

    /**
     * En son geçilen profili geri getirir (Gereksinim 1.14).
     *
     * [passedProfileStack] oturum boyunca geçilen TÜM profilleri (en eskiden en yeniye)
     * tutar — yalnızca en üsttekini değil. Sunucunun kendi yığını (`usage.passStack`,
     * `functions/src/monetization.ts`) günlük `rewindsPerDay` kotası izin verdiği sürece
     * her çağrıda bir öncekini geri getirir, bu yüzden kullanıcı art arda birden fazla kez
     * "Geri Al"a basarak geçtiği profilleri sırayla (en yeniden en eskiye) geri getirebilir.
     */
    fun rewindLastPass() {
        val passed = passedProfileStack.lastOrNull() ?: return
        if (_state.value.entitlement.limits.rewindsPerDay == 0) {
            viewModelScope.launch { _events.send(DiscoverEvent.NavigateToPaywall(PaywallRequest(LimitReason.REWINDS))) }
            return
        }
        if (_state.value.isRewinding) return
        _state.update { it.copy(isRewinding = true) }
        viewModelScope.launch {
            interactionRepository.rewindLastPass()
                .onSuccess { profileUid ->
                    if (profileUid == passed.profile.id) {
                        passedProfileStack.removeLast()
                        repository.unmarkActedOn(profileUid)
                        insertProfile(passed.profile, passed.index)
                    } else {
                        // Sunucudaki yığın istemcininkiyle senkron değil (ör. aynı hesap başka
                        // bir cihazdan da geçme yapmış olabilir) — hangi kartların hâlâ geçerli
                        // olduğunu güvenle bilemeyiz, bu yüzden tutarsız kalmaktansa yerel
                        // yığını TAMAMEN temizliyoruz.
                        passedProfileStack.clear()
                    }
                    _state.update { it.copy(hasRewindablePass = passedProfileStack.isNotEmpty(), isRewinding = false) }
                    _events.send(DiscoverEvent.ShowSnackbar(getString(Res.string.discover_rewind_success)))
                }
                .onFailure {
                    _state.update { it.copy(isRewinding = false) }
                    _events.send(DiscoverEvent.ShowSnackbar(it.message ?: getString(Res.string.error_generic)))
                }
        }
    }

    /** Gereksinim 2.12 (Faz 4): bir önceki Boost'un tamamlanma denetimi hala bekliyorsa iptal edilir. */
    private var boostCompletionJob: Job? = null

    /**
     * Activates the current tier's Boost; the server owns allowance (now billing-cycle
     * based, bkz. [BoostManagerUseCase] KDoc'u) and the 30-minute expiry.
     */
    fun activateBoost() {
        if (_state.value.entitlement.limits.boostsPerMonth == 0) {
            viewModelScope.launch { _events.send(DiscoverEvent.NavigateToPaywall(PaywallRequest(LimitReason.BOOSTS))) }
            return
        }
        if (_state.value.isBoosting) return
        _state.update { it.copy(isBoosting = true) }
        viewModelScope.launch {
            boostManagerUseCase.activate()
                .onSuccess { boostUntil ->
                    _state.update { it.copy(isBoosting = false, boostUntilMillis = boostUntil) }
                    _events.send(DiscoverEvent.ShowSnackbar(getString(Res.string.discover_boost_started)))
                    scheduleBoostCompletionCheck(boostUntil)
                }
                .onFailure {
                    _state.update { it.copy(isBoosting = false) }
                    _events.send(DiscoverEvent.ShowSnackbar(it.message ?: getString(Res.string.error_generic)))
                }
        }
    }

    /**
     * Gereksinim 2.12 (Faz 4): [boostUntilMillis] anına kadar bekler, ardından sunucudan
     * Boost özetini çekip tek seferlik ("Boost bitti! Profilin X kişiye fazladan
     * gösterildi") özet diyaloğunu tetikler. Ekran kapanıp ViewModel temizlenirse
     * ([viewModelScope] iptal edilirse) bu bekleme de kendiliğinden durur -- Boost hala
     * SUNUCUDA aktif kalır, yalnızca bu özel istemci bildirimi kaybolur (ağır bir maliyeti
     * yoktur, çünkü özet her zaman `getBoostSummary` ile sonradan da çekilebilir).
     */
    private fun scheduleBoostCompletionCheck(boostUntilMillis: Long) {
        boostCompletionJob?.cancel()
        boostCompletionJob = viewModelScope.launch {
            val remaining = boostUntilMillis - getCurrentTimeMillis()
            if (remaining > 0) delay(remaining)
            boostManagerUseCase.fetchCompletionSummary()
                .onSuccess { summary -> _state.update { it.copy(boostSummary = summary) } }
        }
    }

    /** Gereksinim 2.12 (Faz 4): özet diyaloğu kapatılırken çağrılır -- tek seferlik bayrağı sıfırlar. */
    fun onBoostSummaryDismissed() {
        _state.update { it.copy(boostSummary = null) }
    }

    /**
     * Günlük beğeni kotasını [FeatureGate] üzerinden denetler. Bu, herhangi bir AdMob
     * interstitial'ı istenmeden ÖNCE çağrılır (Gereksinim 1.1): kota tükenmişse kullanıcıya
     * hiçbir şekilde reklam izletilmez, [DiscoverUiState.limitReason] set edilir ve
     * arayan taraf Limit Sheet'i açar. Kota müsaitse iyimser olarak sayaç bir artırılır ve
     * eylem (ve ardından olası reklam) devam eder. Premium'da limitler `null` olduğundan bu
     * kontrol pratikte hiçbir zaman engellemez.
     */
    private fun consumeSwipeOrBlock(): Boolean {
        val current = _state.value
        val usage = UsageSnapshot(likes = current.swipesUsedToday, rewardedLikes = current.rewardedLikesToday)
        val decision = featureGate.decide(Feature.LIKE, current.entitlement, usage, showInterstitial = false)
        analyticsRepository.trackGateDecision(Feature.LIKE, current.entitlement, decision, source = "discover")
        if (decision is GateDecision.LimitReached) {
            _state.update {
                it.copy(limitReason = decision.reason, limitIsFairUseCap = decision.upgradeTo == null)
            }
            return false
        }
        _state.update { it.copy(swipesUsedToday = it.swipesUsedToday + 1) }
        return true
    }

    fun onUpgradeClicked() {
        val reason = _state.value.limitReason
        _state.update { it.copy(limitReason = null) }
        viewModelScope.launch { _events.send(DiscoverEvent.NavigateToPaywall(PaywallRequest(reason))) }
    }

    /** Limit sayfasında "Standart'ı ücretsiz dene" seçildi: Paywall deneme paketi seçili açılır. */
    fun onTrialClicked() {
        val reason = _state.value.limitReason
        _state.update { it.copy(limitReason = null) }
        viewModelScope.launch { _events.send(DiscoverEvent.NavigateToPaywall(PaywallRequest(reason, preselectTrial = true))) }
    }

    fun onLimitSheetDismissed() {
        _state.update { it.copy(limitReason = null) }
    }

    /** Refreshes only from the server; an AdMob client callback never mutates reward balances. */
    fun onRewardedLikeConfirmed() {
        viewModelScope.launch {
            val revision = likeRevision
            val usage = repository.getLikeUsageToday()
            _state.update {
                val updated = it.copy(
                    swipesUsedToday = if (revision == likeRevision && !it.isSwipeInFlight) usage.likes else it.swipesUsedToday,
                    rewardedLikesToday = usage.rewardedLikes,
                    likesEver = if (revision == likeRevision && !it.isSwipeInFlight) usage.likesEver else it.likesEver,
                )
                updated.copy(limitReason = if (updated.canSwipe) null else updated.limitReason)
            }
        }
    }

    private fun removeProfile(profileId: String) {
        _state.update { current ->
            val remaining = current.profiles.filterNot { it.id == profileId }
            current.copy(
                profiles = remaining,
                imageLoading = current.imageLoading.reconcile(remaining.map { profile -> profile.photoUrls.firstOrNull() }),
                likedMeTutorialProfileId = remaining.firstOrNull { it.hasLikedMe }?.id,
            )
        }
    }

    /** Puts a profile back where it was after a failed write. */
    private fun insertProfile(profile: DiscoverProfile, index: Int) {
        _state.update { current ->
            val restored = current.profiles.toMutableList()
            restored.add(index.coerceIn(0, restored.size), profile)
            current.copy(
                profiles = restored,
                imageLoading = current.imageLoading.reconcile(restored.map { profile -> profile.photoUrls.firstOrNull() }),
                likedMeTutorialProfileId = restored.firstOrNull { it.hasLikedMe }?.id,
            )
        }
    }

    private data class PassedProfile(val profile: DiscoverProfile, val index: Int)

    /**
     * Gereksinim 1.14: bu oturumda geçilen (pas geçilen) profillerin yığını (stack) —
     * en eskiden en yeniye sıralı. Sunucunun `usage.passStack`'iyle (bkz. `rewindLastPass`
     * KDoc'u) birebir aynı mantıkla, yalnızca sonundan eklenir/çıkarılır.
     */
    private val passedProfileStack = ArrayDeque<PassedProfile>()

    fun onProfileClicked(profileId: String) {
        viewModelScope.launch {
            _events.send(DiscoverEvent.NavigateToProfile(profileId))
        }
    }

    // ── Filter chips ────────────────────────────────────────────────────────

    fun onAgeChipClicked() {
        _state.update { it.copy(showAgeSheet = true) }
    }

    fun onAgeSheetDismissed() {
        // Reverts the sliders to what is actually stored, so closing without applying
        // does not leave the chip showing a range the feed is not using.
        _state.update {
            it.copy(
                showAgeSheet = false,
                ageRangeStart = currentSettings.ageRangeStart,
                ageRangeEnd = currentSettings.ageRangeEnd,
            )
        }
    }

    /** Live slider movement — chip text follows, nothing is queried until Apply. */
    fun onAgeRangeChanged(start: Int, end: Int) {
        _state.update { it.copy(ageRangeStart = start, ageRangeEnd = end) }
    }

    /** Persists the age range; the settings flow then reloads the grid. */
    fun onAgeRangeApplied() {
        val s = _state.value
        _state.update { it.copy(showAgeSheet = false) }
        viewModelScope.launch {
            runCatching {
                settingsRepository.saveSettings(
                    currentSettings.copy(ageRangeStart = s.ageRangeStart, ageRangeEnd = s.ageRangeEnd)
                )
            }.onFailure {
                _events.send(DiscoverEvent.ShowSnackbar(getString(Res.string.error_generic)))
            }
        }
    }

    // ── UGC Safety: Reporting & Blocking ────────────────────────────────────

    fun onReportClick(profileId: String) {
        _state.update {
            it.copy(showReportDialog = true, selectedProfileToReportOrBlock = profileId)
        }
    }

    fun onReportDismiss() {
        _state.update {
            it.copy(
                showReportDialog = false,
                selectedReportReason = null,
                selectedProfileToReportOrBlock = null
            )
        }
    }

    fun onReportReasonSelected(reason: String) {
        _state.update { it.copy(selectedReportReason = reason) }
    }

    fun submitReport() {
        val reason = _state.value.selectedReportReason ?: return
        val profileId = _state.value.selectedProfileToReportOrBlock ?: return
        val reporterUid = Firebase.auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val report = mapOf(
                    "reporterUid" to reporterUid,
                    "reportedUid" to profileId,
                    "reason" to reason,
                    "timestamp" to com.mcclabs.mook.util.getCurrentTimeMillis(),
                    "status" to "pending"
                )
                appFirestore
                    .collection("reports")
                    .document
                    .set(report)
                _state.update {
                    it.copy(
                        showReportDialog = false,
                        selectedReportReason = null,
                        selectedProfileToReportOrBlock = null
                    )
                }
                _events.send(DiscoverEvent.ShowSnackbar(getString(Res.string.report_submitted)))
                // Take the reported profile out of the feed immediately.
                repository.markActedOn(profileId)
                removeProfile(profileId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(DiscoverEvent.ShowSnackbar(getString(Res.string.report_failed)))
            }
        }
    }

    fun onBlockClick(profileId: String) {
        _state.update {
            it.copy(showBlockConfirmDialog = true, selectedProfileToReportOrBlock = profileId)
        }
    }

    fun onBlockDismiss() {
        _state.update {
            it.copy(showBlockConfirmDialog = false, selectedProfileToReportOrBlock = null)
        }
    }

    fun confirmBlock() {
        val profileId = _state.value.selectedProfileToReportOrBlock ?: return
        val currentUid = Firebase.auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                appFirestore
                    .collection("users")
                    .document(currentUid)
                    .set(
                        mapOf("blockedUsers" to dev.gitlive.firebase.firestore.FieldValue.arrayUnion(profileId)),
                        merge = true
                    )
                _state.update {
                    it.copy(showBlockConfirmDialog = false, selectedProfileToReportOrBlock = null)
                }
                _events.send(DiscoverEvent.ShowSnackbar(getString(Res.string.user_blocked)))
                repository.markActedOn(profileId)
                removeProfile(profileId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(DiscoverEvent.ShowSnackbar(getString(Res.string.block_failed)))
            }
        }
    }
}
