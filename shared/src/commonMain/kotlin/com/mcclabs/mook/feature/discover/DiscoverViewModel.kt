package com.mcclabs.mook.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.repository.DiscoverRepository
import com.mcclabs.mook.domain.repository.InteractionRepository
import com.mcclabs.mook.domain.repository.SettingsRepository
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
import kotlinx.coroutines.launch
import com.mcclabs.mook.util.Log

sealed class DiscoverEvent {
    data class NavigateToProfile(val profileId: String) : DiscoverEvent()
    /** Emitted on a mutual match; carries the id of the user who was matched with. */
    data class NavigateToMatch(val matchedUserId: String) : DiscoverEvent()
    data class ShowSnackbar(val message: String) : DiscoverEvent()
}

class DiscoverViewModel(
    private val repository: DiscoverRepository,
    private val interactionRepository: InteractionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<DiscoverEvent>()
    val events: SharedFlow<DiscoverEvent> = _events.asSharedFlow()

    init {
        loadCurrentUserAvatar()
        observeSettingsAndReload()
    }

    /**
     * Reloads the stack whenever the filters change, so applying a filter takes effect
     * without the user having to leave and re-enter Discover.
     */
    private fun observeSettingsAndReload() {
        viewModelScope.launch {
            // Seed the cache from Firestore before the first emission is consumed.
            runCatching { settingsRepository.getSettings() }

            settingsRepository.observeSettings().collectLatest { settings ->
                loadProfiles(settings)
            }
        }
    }

    private suspend fun loadProfiles(settings: MatchSettings) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            val profiles = repository.getDiscoverProfiles(settings)
            val hasSeenTutorial = settingsRepository.getHasSeenLikedMeTutorial()
            _state.value = _state.value.copy(
                profiles = profiles, 
                isLoading = false,
                hasSeenLikedMeTutorial = hasSeenTutorial
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                error = e.message ?: "Something went wrong.",
                isLoading = false
            )
        }
    }

    /** Re-runs the query after a failure; the settings flow does not re-emit on its own. */
    fun retry() {
        viewModelScope.launch {
            loadCurrentUserAvatar()
            loadProfiles(settingsRepository.getSettings())
        }
    }

    fun dismissLikedMeTutorial() {
        viewModelScope.launch {
            settingsRepository.setHasSeenLikedMeTutorial(true)
            _state.value = _state.value.copy(hasSeenLikedMeTutorial = true)
        }
    }

    private fun loadCurrentUserAvatar() {
        viewModelScope.launch {
            val uid = Firebase.auth.currentUser?.uid
            if (uid == null) {
                Log.e("Discover: oturum açık değil, avatar yüklenemiyor")
                return@launch
            }
            val profile = try {
                repository.getProfileDetails(uid)
            } catch (e: Exception) {
                Log.e("Discover: kendi profilim okunamadı (uid=$uid)", e)
                null
            }
            _state.value = _state.value.copy(
                currentUserAvatarUrl = profile?.photoUrls?.firstOrNull()
            )
        }
    }

    fun swipeRight(profileId: String) {
        val profile = _state.value.profiles.find { it.id == profileId } ?: return
        removeProfile(profileId)
        viewModelScope.launch {
            val result = interactionRepository.swipeUser(profileId, isLike = true)
            if (result is MatchResult.Error) {
                insertProfile(profile)
                _events.emit(DiscoverEvent.ShowSnackbar("Swipe failed, please check connection."))
            } else if (result is MatchResult.MutualMatch) {
                _events.emit(DiscoverEvent.NavigateToMatch(profileId))
            }
        }
    }

    fun swipeLeft(profileId: String) {
        val profile = _state.value.profiles.find { it.id == profileId } ?: return
        removeProfile(profileId)
        viewModelScope.launch {
            val result = interactionRepository.swipeUser(profileId, isLike = false)
            if (result is MatchResult.Error) {
                insertProfile(profile)
                _events.emit(DiscoverEvent.ShowSnackbar("Swipe failed, please check connection."))
            }
        }
    }

    private fun removeProfile(profileId: String) {
        val currentProfiles = _state.value.profiles.toMutableList()
        currentProfiles.removeAll { it.id == profileId }
        _state.value = _state.value.copy(profiles = currentProfiles)
    }

    private fun insertProfile(profile: com.mcclabs.mook.domain.model.DiscoverProfile) {
        val currentProfiles = _state.value.profiles.toMutableList()
        currentProfiles.add(0, profile)
        _state.value = _state.value.copy(profiles = currentProfiles)
    }

    fun onProfileClicked(profileId: String) {
        viewModelScope.launch {
            _events.emit(DiscoverEvent.NavigateToProfile(profileId))
        }
    }

    // ── UGC Safety: Reporting & Blocking ────────────────────────────────────

    fun onReportClick(profileId: String) {
        _state.value = _state.value.copy(
            showReportDialog = true,
            selectedProfileToReportOrBlock = profileId
        )
    }

    fun onReportDismiss() {
        _state.value = _state.value.copy(
            showReportDialog = false,
            selectedReportReason = null,
            selectedProfileToReportOrBlock = null
        )
    }

    fun onReportReasonSelected(reason: String) {
        _state.value = _state.value.copy(selectedReportReason = reason)
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
                _state.value = _state.value.copy(
                    showReportDialog = false,
                    selectedReportReason = null,
                    selectedProfileToReportOrBlock = null
                )
                _events.emit(DiscoverEvent.ShowSnackbar("Report submitted successfully."))
                // Optionally remove the profile from feed immediately
                removeProfile(profileId)
            } catch (e: Exception) {
                _events.emit(DiscoverEvent.ShowSnackbar("Failed to submit report."))
            }
        }
    }

    fun onBlockClick(profileId: String) {
        _state.value = _state.value.copy(
            showBlockConfirmDialog = true,
            selectedProfileToReportOrBlock = profileId
        )
    }

    fun onBlockDismiss() {
        _state.value = _state.value.copy(
            showBlockConfirmDialog = false,
            selectedProfileToReportOrBlock = null
        )
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
                _state.value = _state.value.copy(
                    showBlockConfirmDialog = false,
                    selectedProfileToReportOrBlock = null
                )
                _events.emit(DiscoverEvent.ShowSnackbar("User blocked."))
                // Remove from feed
                removeProfile(profileId)
            } catch (e: Exception) {
                _events.emit(DiscoverEvent.ShowSnackbar("Failed to block user."))
            }
        }
    }
}
