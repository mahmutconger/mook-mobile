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

data class RoomSwitchUiState(
    val isLoading: Boolean = true,
    val languages: List<Language> = emptyList(),
    val selectedCode: String? = null,
)

sealed class RoomSwitchEvent {
    /** The room was changed; return to the previous screen. */
    data object Done : RoomSwitchEvent()
}

/**
 * Lets the user change their current language room. Unlike [RoomGateViewModel], this
 * always shows the picker (no skip-if-set shortcut) and highlights the active room.
 */
class RoomSwitchViewModel(
    private val settingsRepository: SettingsRepository,
    private val discoverRepository: DiscoverRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RoomSwitchUiState())
    val state: StateFlow<RoomSwitchUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<RoomSwitchEvent>()
    val events: SharedFlow<RoomSwitchEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val uid = Firebase.auth.currentUser?.uid
            val ownLanguageCode = uid?.let { discoverRepository.getProfileDetails(it)?.language?.code }
            val languages = Languages.ALL.filterNot { it.code.equals(ownLanguageCode, ignoreCase = true) }
            val currentRoom = settingsRepository.getRoomLanguageCode()
            _state.update { it.copy(isLoading = false, languages = languages, selectedCode = currentRoom) }
        }
    }

    fun onRoomSelected(language: Language) {
        viewModelScope.launch {
            settingsRepository.setRoomLanguageCode(language.code)
            _events.emit(RoomSwitchEvent.Done)
        }
    }
}
