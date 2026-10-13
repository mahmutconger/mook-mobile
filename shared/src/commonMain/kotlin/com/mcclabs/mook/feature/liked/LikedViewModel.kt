package com.mcclabs.mook.feature.liked

import com.mcclabs.mook.ui.loading.ImageLoadStatus
import com.mcclabs.mook.ui.loading.progressiveLoadingEnabled

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
    private val repository: DiscoverRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LikedUiState())
    val state: StateFlow<LikedUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

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
            _state.update { it.copy(isLoading = !progressiveLoadingEnabled || it.liked.isEmpty(), error = null) }
            try {
                val liked = repository.getLikedProfiles()
                ensureActive()
                _state.update { current ->
                    val profiles = liked
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
