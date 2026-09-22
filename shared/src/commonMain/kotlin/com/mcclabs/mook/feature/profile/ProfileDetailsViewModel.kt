package com.mcclabs.mook.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.domain.model.MatchResult
import com.mcclabs.mook.domain.repository.DiscoverRepository
import com.mcclabs.mook.domain.repository.InteractionRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.functions.functions
import dev.gitlive.firebase.firestore.FieldValue
import com.mcclabs.mook.data.appFirestore
import com.mcclabs.mook.domain.repository.ChatRepository
import com.mcclabs.mook.util.buildWalkTalkChatUrl
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.getString
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.profile_not_found
import mook.shared.generated.resources.error_generic
import mook.shared.generated.resources.error_swipe_failed

sealed class ProfileDetailsEvent {
    data class OpenDeepLink(val url: String) : ProfileDetailsEvent()
    object ReportSubmitted : ProfileDetailsEvent()
    object BlockConfirmed : ProfileDetailsEvent()

    /** The like was mutual — hand off to the match celebration. */
    data class NavigateToMatch(val matchedUserId: String) : ProfileDetailsEvent()
    /** Navigate to the real-time chat screen with a matched user. */
    data class NavigateToChat(val chatId: String, val peerUid: String) : ProfileDetailsEvent()
    /** The like or pass landed; the screen returns to the feed. */
    object ActionCompleted : ProfileDetailsEvent()
    /** Free user hit the daily limit and chose to upgrade. */
    object NavigateToPaywall : ProfileDetailsEvent()
    data class ShowMessage(val message: String) : ProfileDetailsEvent()
}

class ProfileDetailsViewModel(
    private val repository: DiscoverRepository,
    private val interactionRepository: InteractionRepository,
    private val subscriptions: SubscriptionRepository,
    private val chatRepository: ChatRepository,
    private val profileId: String
) : ViewModel() {

    // Whether this is the signed-in user's own profile decides the entire chrome:
    // own → Settings entry, no message button; someone else → Report + message.
    private val isOwnProfile: Boolean = Firebase.auth.currentUser?.uid == profileId

    private val _state = MutableStateFlow(ProfileDetailsUiState(isOwnProfile = isOwnProfile))
    val state: StateFlow<ProfileDetailsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ProfileDetailsEvent>()
    val events: SharedFlow<ProfileDetailsEvent> = _events.asSharedFlow()

    init {
        loadProfile()
        observeEntitlement()
    }

    /** Keeps all plan limits current immediately after purchase or restore. */
    private fun observeEntitlement() {
        viewModelScope.launch {
            subscriptions.state.collect { entitlement ->
                _state.value = _state.value.copy(entitlement = entitlement)
            }
        }
        viewModelScope.launch { subscriptions.refresh() }
    }

    // ── Like / pass ─────────────────────────────────────────────────────────

    /**
     * Likes this person. A mutual like hands off to the match screen; otherwise the profile
     * closes and the feed drops the card. This is the deliberate path — the Discovery grid's
     * heart is the quick one — so a failure here surfaces rather than passing silently.
     */
    fun onLikeClicked() = act(isLike = true)

    /** Passes on this person: they leave the feed and will not be shown again. */
    fun onPassClicked() = act(isLike = false)

    private fun act(isLike: Boolean) {
        val current = _state.value
        if (current.isActionInFlight || current.isActedOn || current.isOwnProfile) return
        if (isLike && !current.canSwipe) {
            _state.value = current.copy(showLimitDialog = true)
            return
        }

        _state.value = current.copy(
            isActionInFlight = true,
            swipesUsedToday = if (isLike) current.swipesUsedToday + 1 else current.swipesUsedToday
        )
        viewModelScope.launch {
            when (interactionRepository.swipeUser(profileId, isLike = isLike)) {
                is MatchResult.Error -> {
                    _state.value = _state.value.copy(
                        isActionInFlight = false,
                        swipesUsedToday = if (isLike) (_state.value.swipesUsedToday - 1).coerceAtLeast(0) else _state.value.swipesUsedToday
                    )
                    _events.emit(ProfileDetailsEvent.ShowMessage(getString(Res.string.error_swipe_failed)))
                }
                is MatchResult.MutualMatch -> {
                    // Tell Discovery to drop the card before the match screen takes over, so
                    // coming back from it does not show someone already matched with.
                    repository.markActedOn(profileId)
                    _state.value = _state.value.copy(isActionInFlight = false, isActedOn = true)
                    _events.emit(ProfileDetailsEvent.NavigateToMatch(profileId))
                }
                else -> {
                    repository.markActedOn(profileId)
                    _state.value = _state.value.copy(isActionInFlight = false, isActedOn = true)
                    // The card disappearing from the feed is the confirmation; a toast the
                    // user navigates away from before reading is not.
                    _events.emit(ProfileDetailsEvent.ActionCompleted)
                }
            }
        }
    }

    fun onUpgradeClicked() {
        _state.value = _state.value.copy(showLimitDialog = false)
        viewModelScope.launch { _events.emit(ProfileDetailsEvent.NavigateToPaywall) }
    }

    fun onLimitDialogDismissed() {
        _state.value = _state.value.copy(showLimitDialog = false)
    }

    fun onSendMessageClicked() {
        viewModelScope.launch {
            val currentUid = Firebase.auth.currentUser?.uid ?: return@launch
            val chatId = chatRepository.buildChatId(currentUid, profileId)
            _events.emit(ProfileDetailsEvent.NavigateToChat(chatId = chatId, peerUid = profileId))
        }
    }

    /** Opens the report bottom-sheet / dialog. */
    fun onReportClick() {
        _state.value = _state.value.copy(showReportDialog = true)
    }

    /** Closes the report dialog without submitting. */
    fun onReportDismiss() {
        _state.value = _state.value.copy(showReportDialog = false, selectedReportReason = null)
    }

    /** User picked a report reason from the list. */
    fun onReportReasonSelected(reason: String) {
        _state.value = _state.value.copy(selectedReportReason = reason)
    }

    /**
     * Submits the report to Firestore and closes the dialog.
     * Writes to `reports/{reportId}` with the reporter's uid, reported uid, and reason.
     */
    fun submitReport() {
        val reason = _state.value.selectedReportReason ?: return
        val reporterUid = Firebase.auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val report = mapOf(
                    "reporterUid" to reporterUid,
                    "reportedUid" to profileId,
                    "reason" to reason,
                    "timestamp" to com.mcclabs.mook.util.getCurrentTimeMillis()
                )
                appFirestore
                    .collection("reports")
                    .document
                    .set(report)
                _state.value = _state.value.copy(
                    showReportDialog = false,
                    selectedReportReason = null
                )
                _events.emit(ProfileDetailsEvent.ReportSubmitted)
            } catch (e: Exception) {
                // Silently ignore — the user sees the dialog close regardless.
            }
        }
    }

    /** Opens the block confirmation dialog. */
    fun onBlockClick() {
        _state.value = _state.value.copy(showBlockConfirmDialog = true)
    }

    /** Closes the block dialog without confirming. */
    fun onBlockDismiss() {
        _state.value = _state.value.copy(showBlockConfirmDialog = false)
    }

    /**
     * Adds the reported user to the current user's `blockedUsers` list in Firestore.
     * Blocked profiles are filtered out by Firestore security rules / query-side logic.
     */
    fun confirmBlock() {
        val currentUid = Firebase.auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                appFirestore
                    .collection("users")
                    .document(currentUid)
                    .set(
                        mapOf("blockedUsers" to FieldValue.arrayUnion(profileId)),
                        merge = true
                    )
                _state.value = _state.value.copy(showBlockConfirmDialog = false)
                _events.emit(ProfileDetailsEvent.BlockConfirmed)
            } catch (e: Exception) {
                _state.value = _state.value.copy(showBlockConfirmDialog = false)
            }
        }
    }

    private fun loadProfile() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val profile = repository.getProfileDetails(profileId)
                
                // If it's not our own profile, check if there is a mutual match
                val isMatched = if (!isOwnProfile) {
                    interactionRepository.checkMutualMatch(profileId)
                } else false

                // The exclusion set the feed uses is the same source of truth for "already
                // acted on", so reopening a profile never offers a second like.
                val alreadyActedOn = !isOwnProfile && profileId in repository.actedOnProfileIds()
                val swipesUsedToday = if (isOwnProfile) 0 else repository.getSwipesUsedToday()

                if (profile != null) {
                    if (!isOwnProfile) {
                        // Analytics/audience visibility is server-owned so incognito cannot be
                        // bypassed by a modified client. Failure must not prevent reading a profile.
                        runCatching {
                            Firebase.functions.httpsCallable("recordProfileVisit")
                                .invoke(ProfileVisitRequest(profileId))
                        }
                    }
                    _state.value = _state.value.copy(
                        profile = profile,
                        isLoading = false,
                        isMatched = isMatched,
                        isActedOn = alreadyActedOn,
                        swipesUsedToday = swipesUsedToday
                    )
                } else {
                    _state.value = _state.value.copy(
                        profile = null,
                        error = getString(Res.string.profile_not_found),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = e.message ?: getString(Res.string.error_generic),
                    isLoading = false
                )
            }
        }
    }
}

@Serializable
private data class ProfileVisitRequest(val profileUid: String)
