package com.mcclabs.mook.feature.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.model.ChatRoom
import com.mcclabs.mook.domain.repository.ChatRepository
import com.mcclabs.mook.domain.repository.DiscoverRepository
import com.mcclabs.mook.ui.loading.ImageLoadStatus
import com.mcclabs.mook.ui.loading.progressiveLoadingEnabled
import com.mcclabs.mook.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Profil okunamadığında ad ve fotoğraf boş bırakılır. */
private typealias PeerProfile = Pair<String?, String?>

/**
 * Sohbet metinleri Android'de hemen gösterilir; eksik kişi bilgileri bağımsız tamamlanır.
 * Görsel baytlarını arayüz yükler ve sonuçlarını tek yönlü olaylarla geri bildirir.
 */
class ChatListViewModel(
    private val chatRepository: ChatRepository,
    private val discoverRepository: DiscoverRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatListUiState())
    val state: StateFlow<ChatListUiState> = _state.asStateFlow()

    /** Yeni liste bildirimlerinde aynı profil tekrar sorgulanmaz. */
    private val profileCache = mutableMapOf<String, PeerProfile>()

    fun onImageLoadChanged(url: String, status: ImageLoadStatus) {
        _state.update { it.copy(imageLoading = it.imageLoading.onResult(url, status)) }
    }

    init {
        viewModelScope.launch {
            val rooms = chatRepository.observeChats().catch { error ->
                Log.e("Sohbet listesi akışı hatası", error)
                _state.update { it.copy(error = error.message ?: "Sohbetler yüklenemedi", isLoading = false) }
            }
            // Android'de yeni bildirim, önceki listeye ait eksik profil sorgularını iptal eder.
            if (progressiveLoadingEnabled) rooms.collectLatest { loadRooms(it) }
            else rooms.collect { loadRooms(it) }
        }
    }

    private suspend fun loadRooms(rawRooms: List<ChatRoom>) {
        if (progressiveLoadingEnabled) publishRooms(rawRooms)
        val missing = rawRooms.map { it.peerUid }.distinct().filter { it !in profileCache }
        coroutineScope {
            missing.forEach { peerUid ->
                launch {
                    profileCache[peerUid] = fetchPeerProfile(peerUid)
                    if (progressiveLoadingEnabled) publishRooms(rawRooms)
                }
            }
        }
        publishRooms(rawRooms)
    }

    private fun publishRooms(rawRooms: List<ChatRoom>) {
        val rooms = rawRooms.map { room ->
            val cached = profileCache[room.peerUid]
            room.copy(
                peerName = if (progressiveLoadingEnabled) cached?.first ?: room.peerName else cached?.first,
                peerPhotoUrl = if (progressiveLoadingEnabled) cached?.second ?: room.peerPhotoUrl else cached?.second,
            )
        }
        _state.update {
            it.copy(rooms = rooms, isLoading = false, error = null,
                imageLoading = it.imageLoading.reconcile(rooms.map { room -> room.peerPhotoUrl }))
        }
    }

    /** Tek bir profil hatası, diğer sohbetleri ve mesaj önizlemelerini engellemez. */
    private suspend fun fetchPeerProfile(peerUid: String): PeerProfile = try {
        val profile = discoverRepository.getProfileDetails(peerUid)
        profile?.name to profile?.photoUrls?.firstOrNull()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.e("Sohbet profili alınamadı (uid=$peerUid)", error)
        null to null
    }
}
