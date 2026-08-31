package com.mcclabs.mook.feature.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.repository.ChatRepository
import com.mcclabs.mook.domain.repository.DiscoverRepository
import com.mcclabs.mook.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A peer's display name and avatar, both null when their profile could not be read. */
private typealias PeerProfile = Pair<String?, String?>

/**
 * ViewModel for the Chat List (Inbox) screen.
 *
 * Observes all active chat rooms for the current user and enriches them
 * with the peer's profile details (name, photo) fetched from DiscoverRepository.
 */
class ChatListViewModel(
    private val chatRepository: ChatRepository,
    private val discoverRepository: DiscoverRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatListUiState())
    val state: StateFlow<ChatListUiState> = _state.asStateFlow()

    /** Cache of fetched profiles, so a new emission only pays for peers it hasn't seen. */
    private val profileCache = mutableMapOf<String, PeerProfile>()

    init {
        observeChats()
    }

    private fun observeChats() {
        viewModelScope.launch {
            chatRepository.observeChats()
                .catch { e ->
                    Log.e("Sohbet listesi akışı hatası", e)
                    _state.update { it.copy(error = e.message, isLoading = false) }
                }
                .collect { rawRooms ->
                    // Fetch the profiles this emission is missing in parallel. One at a
                    // time inside collect stalls the whole stream behind N sequential
                    // round trips on the first load — exactly when the inbox is blank
                    // and the user is waiting on it.
                    val missing = rawRooms.map { it.peerUid }
                        .distinct()
                        .filter { it !in profileCache }

                    coroutineScope {
                        missing.map { peerUid ->
                            async { peerUid to fetchPeerProfile(peerUid) }
                        }.awaitAll()
                    }.forEach { (peerUid, profile) -> profileCache[peerUid] = profile }

                    val enrichedRooms = rawRooms.map { room ->
                        val cached = profileCache[room.peerUid]
                        room.copy(
                            peerName = cached?.first,
                            peerPhotoUrl = cached?.second,
                        )
                    }

                    _state.update {
                        it.copy(
                            rooms = enrichedRooms,
                            isLoading = false,
                            error = null,
                        )
                    }
                }
        }
    }

    /**
     * Reads one peer's name and avatar. A failure caches a blank entry rather than
     * propagating: one unreadable profile must not blank out the whole inbox, and the
     * screen renders a localized placeholder for a null name.
     */
    private suspend fun fetchPeerProfile(peerUid: String): PeerProfile = try {
        val profile = discoverRepository.getProfileDetails(peerUid)
        profile?.name to profile?.photoUrls?.firstOrNull()
    } catch (e: Exception) {
        Log.e("Peer profil alınamadı (uid=$peerUid)", e)
        null to null
    }
}
