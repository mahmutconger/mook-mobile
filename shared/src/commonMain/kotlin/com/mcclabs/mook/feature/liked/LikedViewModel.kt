package com.mcclabs.mook.feature.liked

import com.mcclabs.mook.ui.loading.ImageLoadStatus
import com.mcclabs.mook.ui.loading.progressiveLoadingEnabled

import com.mcclabs.mook.domain.interaction.SetLikeUseCase
import com.mcclabs.mook.ui.optimistic.OptimisticStateController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Job
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.repository.DiscoverRepository
import com.mcclabs.mook.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

class LikedViewModel(
    private val repository: DiscoverRepository,
    private val setLikeUseCase: SetLikeUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(LikedUiState())
    val state: StateFlow<LikedUiState> = _state.asStateFlow()

    private val optimisticState = OptimisticStateController<LikedUiState, String>(_state, viewModelScope)
    private val _events = Channel<LikedEvent>(Channel.UNLIMITED)
    val events: Flow<LikedEvent> = _events.receiveAsFlow()
    private var loadJob: Job? = null
    private var likeRevision = 0L

    /** Satır hemen gizlenir; hata durumunda aynı sırada yeniden görünür. */
    fun unlikeProfile(profileId: String) {
        if (_state.value.liked.none { it.profile.id == profileId } || optimisticState.isPending(profileId)) return
        likeRevision++
        optimisticState.execute(
            key = profileId,
            optimistic = { it.copy(unlikedProfileIds = it.unlikedProfileIds + profileId) },
            rollback = { current, _ -> current.copy(unlikedProfileIds = current.unlikedProfileIds - profileId) },
            sync = { setLikeUseCase(profileId, false) },
            onFailure = {
                likeRevision++
                _events.trySend(LikedEvent.ShowSnackbar("Beğeni geri çekilemedi. Lütfen tekrar deneyin."))
            },
            onSuccess = {
                likeRevision++
                repository.unmarkActedOn(profileId)
                _state.update { current ->
                    val remaining = current.liked.filterNot { it.profile.id == profileId }
                    current.copy(liked = remaining, unlikedProfileIds = current.unlikedProfileIds - profileId,
                        imageLoading = current.imageLoading.reconcile(remaining.map { it.profile.photoUrls.firstOrNull() }))
                }
            },
        )
    }

    /** Görsel sonucu yalnızca mevcut veri kümesine aitse duruma uygulanır. */
    fun onImageLoadChanged(url: String, status: ImageLoadStatus) {
        _state.update { it.copy(imageLoading = it.imageLoading.onResult(url, status)) }
    }

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val revision = likeRevision
            _state.update { it.copy(isLoading = !progressiveLoadingEnabled || it.liked.isEmpty(), error = null) }
            try {
                val liked = repository.getLikedProfiles()
                ensureActive()
                _state.update { current ->
                    val profiles = if (revision == likeRevision && current.unlikedProfileIds.isEmpty()) liked else current.liked
                    current.copy(liked = profiles, isLoading = false,
                        imageLoading = current.imageLoading.reconcile(profiles.map { item -> item.profile.photoUrls.firstOrNull() }))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Beğendiklerim yüklenemedi", e)
                _state.update {
                    it.copy(error = "Beğendikleriniz yüklenemedi. Lütfen tekrar deneyin.", isLoading = false)
                }
            }
        }
    }
}

sealed interface LikedEvent {
    data class ShowSnackbar(val message: String) : LikedEvent
}
