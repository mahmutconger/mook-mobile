package com.mcclabs.mook.feature.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.repository.ChatRepository
import com.mcclabs.mook.domain.repository.DiscoverRepository
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
import com.mcclabs.mook.util.Log
import com.mcclabs.mook.util.buildWalkTalkChatUrl

sealed class MatchEvent {
    data class OpenDeepLink(val url: String) : MatchEvent()
    data class NavigateToChat(val chatId: String, val peerUid: String) : MatchEvent()
}

class MatchViewModel(
    private val discoverRepository: DiscoverRepository,
    private val chatRepository: ChatRepository,
    private val matchedUserId: String
) : ViewModel() {

    private val _state = MutableStateFlow(MatchUiState())
    val state: StateFlow<MatchUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<MatchEvent>()
    val events: SharedFlow<MatchEvent> = _events.asSharedFlow()

    init {
        loadMatch()
    }

    private fun loadMatch() {
        viewModelScope.launch {
            Log.d("Eşleşme ekranı açılıyor (eşleşilen uid=$matchedUserId)")
            val matched = try {
                discoverRepository.getProfileDetails(matchedUserId)
            } catch (e: Exception) {
                Log.e("Eşleşilen kullanıcı okunamadı (uid=$matchedUserId)", e)
                null
            }
            val currentUserId = Firebase.auth.currentUser?.uid
            val currentUser = currentUserId?.let {
                try {
                    discoverRepository.getProfileDetails(it)
                } catch (e: Exception) {
                    Log.e("Kendi profilim okunamadı (uid=$it)", e)
                    null
                }
            }

            _state.update {
                it.copy(
                    isLoading = false,
                    matchedUserName = matched?.name,
                    matchedUserPhotoUrl = matched?.photoUrls?.firstOrNull(),
                    currentUserPhotoUrl = currentUser?.photoUrls?.firstOrNull()
                )
            }
        }
    }

    fun onChatClicked() {
        viewModelScope.launch {
            val currentUid = Firebase.auth.currentUser?.uid ?: return@launch
            val chatId = chatRepository.buildChatId(currentUid, matchedUserId)
            _events.emit(MatchEvent.NavigateToChat(chatId, matchedUserId))
        }
    }
}
