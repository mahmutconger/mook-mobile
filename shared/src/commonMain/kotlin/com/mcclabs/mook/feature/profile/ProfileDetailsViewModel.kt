package com.mcclabs.mook.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.repository.DiscoverRepository
import com.mcclabs.mook.domain.repository.InteractionRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FieldValue
import com.mcclabs.mook.data.appFirestore
import com.mcclabs.mook.util.buildWalkTalkChatUrl
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ProfileDetailsEvent {
    data class OpenDeepLink(val url: String) : ProfileDetailsEvent()
    object ReportSubmitted : ProfileDetailsEvent()
    object BlockConfirmed : ProfileDetailsEvent()
}

class ProfileDetailsViewModel(
    private val repository: DiscoverRepository,
    private val interactionRepository: InteractionRepository,
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
    }

    fun onSendMessageClicked() {
        viewModelScope.launch {
            // Chat lives in the companion WalkTalk app, opened via deep link — same as MatchScreen.
            // The viewed [profileId] is the peer's Firebase uid (shared Firebase project).
            val current = Firebase.auth.currentUser
            val url = buildWalkTalkChatUrl(
                peerId = profileId,
                currentUid = current?.uid.orEmpty(),
                currentEmail = current?.email.orEmpty(),
            )
            _events.emit(ProfileDetailsEvent.OpenDeepLink(url))
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

                if (profile != null) {
                    _state.value = _state.value.copy(
                        profile = profile, 
                        isLoading = false,
                        isMatched = isMatched
                    )
                } else {
                    _state.value = _state.value.copy(
                        profile = null,
                        error = "Profile not found or could not be loaded.",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Failed to load profile: ${e.message}",
                    isLoading = false
                )
            }
        }
    }
}
