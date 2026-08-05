package com.mcclabs.mook.feature.liked

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.repository.DiscoverRepository
import com.mcclabs.mook.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LikedViewModel(
    private val repository: DiscoverRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LikedUiState())
    val state: StateFlow<LikedUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val liked = repository.getLikedProfiles()
                _state.update { it.copy(liked = liked, isLoading = false) }
            } catch (e: Exception) {
                Log.e("Beğendiklerim yüklenemedi", e)
                _state.update {
                    it.copy(error = e.message ?: "Something went wrong.", isLoading = false)
                }
            }
        }
    }
}
