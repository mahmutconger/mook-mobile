package com.mcclabs.mook.feature.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.model.Language
import com.mcclabs.mook.domain.model.Languages
import com.mcclabs.mook.domain.repository.DiscoverRepository
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

data class RoomGateUiState(
    /** True while checking whether a room is already chosen; keeps the picker hidden to avoid a flash. */
    val isChecking: Boolean = true,
    val languages: List<Language> = emptyList(),
    val isSubmitting: Boolean = false,
)

sealed class RoomGateEvent {
    /** A room is set (now or previously); continue into the app. */
    data object Proceed : RoomGateEvent()
}

/**
 * Gates entry to Discover on having a chosen language room. If the signed-in user
 * already has one, it forwards immediately; otherwise it shows the mandatory picker.
 *
 * The user's own native language is excluded from the list — practicing with people
 * who share your native language defeats the point of a room.
 */
class RoomGateViewModel(
    private val settingsRepository: SettingsRepository,
    private val discoverRepository: DiscoverRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RoomGateUiState())
    val state: StateFlow<RoomGateUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<RoomGateEvent>()
    val events: SharedFlow<RoomGateEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            if (settingsRepository.getRoomLanguageCode() != null) {
                _events.emit(RoomGateEvent.Proceed)
                return@launch
            }

            val uid = Firebase.auth.currentUser?.uid
            val ownLanguageCode = uid?.let { discoverRepository.getProfileDetails(it)?.language?.code }
            val languages = Languages.ALL.filterNot { it.code.equals(ownLanguageCode, ignoreCase = true) }
            _state.update { it.copy(isChecking = false, languages = languages) }
        }
    }

    fun onRoomSelected(language: Language) {
        if (_state.value.isSubmitting) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            settingsRepository.setRoomLanguageCode(language.code)
            _events.emit(RoomGateEvent.Proceed)
        }
    }
}
