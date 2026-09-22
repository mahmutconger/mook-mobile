package com.mcclabs.mook.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.repository.DiscoverRepository
import com.mcclabs.mook.domain.repository.InteractionRepository
import com.mcclabs.mook.domain.repository.SettingsRepository
import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.domain.model.DiscoverProfile
import com.mcclabs.mook.domain.model.Languages
import com.mcclabs.mook.domain.model.MatchResult
import com.mcclabs.mook.domain.model.MatchSettings
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import com.mcclabs.mook.data.appFirestore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.error_generic
import mook.shared.generated.resources.error_swipe_failed
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
    data object NavigateToPaywall : DiscoverEvent()
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
) : ViewModel() {

    private val _state = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<DiscoverEvent>()
    val events: SharedFlow<DiscoverEvent> = _events.asSharedFlow()

    /** The filters the visible page was loaded with; used by refresh and paging. */
    private var currentSettings: MatchSettings = MatchSettings()

    init {
        observeEntitlement()
        observeSettingsAndReload()
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
        val refreshing = _state.value.isRefreshing
        _state.update { it.copy(isLoading = !refreshing, error = null) }
        try {
            repository.resetDiscoverPaging()
            val profiles = repository.getDiscoverProfiles(settings)
            val hasSeenTutorial = settingsRepository.getHasSeenLikedMeTutorial()
            val swipesUsedToday = repository.getSwipesUsedToday()
            _state.update {
                it.copy(
                    profiles = profiles,
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    endReached = !repository.hasMoreProfiles(),
                    hasSeenLikedMeTutorial = hasSeenTutorial,
                    likedMeTutorialProfileId = profiles.firstOrNull { p -> p.hasLikedMe }?.id,
                    swipesUsedToday = swipesUsedToday,
                )
            }
        } catch (e: Exception) {
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

        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            try {
                val next = repository.getDiscoverProfiles(currentSettings)
                _state.update { current ->
                    // Guard against duplicates: a profile can surface twice if documents shift
                    // between pages (someone's lastActiveTimestamp updates mid-scroll).
                    val existing = current.profiles.mapTo(mutableSetOf()) { p -> p.id }
                    current.copy(
                        profiles = current.profiles + next.filter { p -> existing.add(p.id) },
                        isLoadingMore = false,
                        endReached = next.isEmpty() || !repository.hasMoreProfiles(),
                    )
                }
            } catch (e: Exception) {
                // A failed page must not clear what is already on screen; the user can scroll
                // again to retry.
                _state.update { it.copy(isLoadingMore = false) }
                _events.emit(DiscoverEvent.ShowSnackbar(e.message ?: getString(Res.string.error_generic)))
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
                likedMeTutorialProfileId = remaining.firstOrNull { it.hasLikedMe }?.id,
            )
        }
        // Acting elsewhere also spends the daily allowance; re-read it so the limit sheet
        // fires at the right moment.
        viewModelScope.launch {
            val used = repository.getSwipesUsedToday()
            _state.update { it.copy(swipesUsedToday = used) }
        }
    }

    fun dismissLikedMeTutorial() {
        viewModelScope.launch {
            settingsRepository.setHasSeenLikedMeTutorial(true)
            _state.update { it.copy(hasSeenLikedMeTutorial = true) }
        }
    }

    // ── Liking ──────────────────────────────────────────────────────────────

    /**
     * Likes someone straight from the grid. The card leaves the feed immediately and is put
     * back if the write fails, so a dropped connection never silently loses a like.
     */
    fun likeProfile(profileId: String) {
        if (_state.value.isSwipeInFlight) return
        val profile = _state.value.profiles.find { it.id == profileId } ?: return
        if (!consumeSwipeOrBlock()) return
        val index = _state.value.profiles.indexOfFirst { it.id == profileId }
        _state.update { it.copy(isSwipeInFlight = true) }
        removeProfile(profileId)
        repository.markActedOn(profileId)
        viewModelScope.launch {
            when (interactionRepository.swipeUser(profileId, isLike = true)) {
                is MatchResult.Error -> {
                    insertProfile(profile, index)
                    revertSwipeCount()
                    _state.update { it.copy(isSwipeInFlight = false) }
                    _events.emit(DiscoverEvent.ShowSnackbar(getString(Res.string.error_swipe_failed)))
                }
                is MatchResult.MutualMatch -> {
                    _state.update { it.copy(isSwipeInFlight = false) }
                    _events.emit(DiscoverEvent.NavigateToMatch(profileId))
                }
                else -> _state.update { it.copy(isSwipeInFlight = false) }
            }
        }
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
                    _events.emit(DiscoverEvent.ShowSnackbar(getString(Res.string.error_swipe_failed)))
                }
                else -> {
                    lastPassedProfile = PassedProfile(profile, index)
                    _state.update { it.copy(hasRewindablePass = true, isSwipeInFlight = false) }
                }
            }
        }
    }

    /** Restores only the latest pass, exactly matching the server's rewind contract. */
    fun rewindLastPass() {
        val passed = lastPassedProfile ?: return
        if (_state.value.entitlement.limits.rewindsPerDay == 0) {
            viewModelScope.launch { _events.emit(DiscoverEvent.NavigateToPaywall) }
            return
        }
        if (_state.value.isRewinding) return
        _state.update { it.copy(isRewinding = true) }
        viewModelScope.launch {
            interactionRepository.rewindLastPass()
                .onSuccess { profileUid ->
                    if (profileUid == passed.profile.id) {
                        repository.unmarkActedOn(profileUid)
                        insertProfile(passed.profile, passed.index)
                    }
                    lastPassedProfile = null
                    _state.update { it.copy(hasRewindablePass = false, isRewinding = false) }
                    _events.emit(DiscoverEvent.ShowSnackbar(getString(Res.string.discover_rewind_success)))
                }
                .onFailure {
                    _state.update { it.copy(isRewinding = false) }
                    _events.emit(DiscoverEvent.ShowSnackbar(it.message ?: getString(Res.string.error_generic)))
                }
        }
    }

    /** Activates the current tier's Boost; the server owns monthly allowance and expiry. */
    fun activateBoost() {
        if (_state.value.entitlement.limits.boostsPerMonth == 0) {
            viewModelScope.launch { _events.emit(DiscoverEvent.NavigateToPaywall) }
            return
        }
        if (_state.value.isBoosting) return
        _state.update { it.copy(isBoosting = true) }
        viewModelScope.launch {
            interactionRepository.activateBoost()
                .onSuccess { boostUntil ->
                    _state.update { it.copy(isBoosting = false, boostUntilMillis = boostUntil) }
                    _events.emit(DiscoverEvent.ShowSnackbar(getString(Res.string.discover_boost_started)))
                }
                .onFailure {
                    _state.update { it.copy(isBoosting = false) }
                    _events.emit(DiscoverEvent.ShowSnackbar(it.message ?: getString(Res.string.error_generic)))
                }
        }
    }

    /**
     * Enforces the free daily limit. Returns true (and optimistically counts the like) when
     * allowed; otherwise raises the upgrade sheet and returns false. Premium bypasses it.
     */
    private fun consumeSwipeOrBlock(): Boolean {
        if (!_state.value.canSwipe) {
            _state.update { it.copy(showLimitSheet = true) }
            return false
        }
        _state.update { it.copy(swipesUsedToday = it.swipesUsedToday + 1) }
        return true
    }

    /** Rolls the optimistic count back when the write fails. */
    private fun revertSwipeCount() {
        _state.update { it.copy(swipesUsedToday = (it.swipesUsedToday - 1).coerceAtLeast(0)) }
    }

    fun onUpgradeClicked() {
        _state.update { it.copy(showLimitSheet = false) }
        viewModelScope.launch { _events.emit(DiscoverEvent.NavigateToPaywall) }
    }

    fun onLimitSheetDismissed() {
        _state.update { it.copy(showLimitSheet = false) }
    }

    private fun removeProfile(profileId: String) {
        _state.update { current ->
            val remaining = current.profiles.filterNot { it.id == profileId }
            current.copy(
                profiles = remaining,
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
                likedMeTutorialProfileId = restored.firstOrNull { it.hasLikedMe }?.id,
            )
        }
    }

    private data class PassedProfile(val profile: DiscoverProfile, val index: Int)
    private var lastPassedProfile: PassedProfile? = null

    fun onProfileClicked(profileId: String) {
        viewModelScope.launch {
            _events.emit(DiscoverEvent.NavigateToProfile(profileId))
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
                _events.emit(DiscoverEvent.ShowSnackbar(getString(Res.string.error_generic)))
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
                _events.emit(DiscoverEvent.ShowSnackbar(getString(Res.string.report_submitted)))
                // Take the reported profile out of the feed immediately.
                repository.markActedOn(profileId)
                removeProfile(profileId)
            } catch (e: Exception) {
                _events.emit(DiscoverEvent.ShowSnackbar(getString(Res.string.report_failed)))
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
                _events.emit(DiscoverEvent.ShowSnackbar(getString(Res.string.user_blocked)))
                repository.markActedOn(profileId)
                removeProfile(profileId)
            } catch (e: Exception) {
                _events.emit(DiscoverEvent.ShowSnackbar(getString(Res.string.block_failed)))
            }
        }
    }
}
