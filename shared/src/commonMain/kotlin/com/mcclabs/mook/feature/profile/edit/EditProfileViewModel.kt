package com.mcclabs.mook.feature.profile.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.repository.AuthRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Collections

data class EditProfileState(
    val photos: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val error: String? = null
)

sealed interface EditProfileEvent {
    object NavigateBack : EditProfileEvent
}

class EditProfileViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(EditProfileState())
    val state: StateFlow<EditProfileState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<EditProfileEvent>()
    val events = _events.asSharedFlow()
    
    private val currentUserId = Firebase.auth.currentUser?.uid
    
    init {
        loadPhotos()
    }

    private fun loadPhotos() {
        if (currentUserId == null) {
            _state.update { it.copy(isLoading = false, error = "User not logged in.") }
            return
        }

        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true, error = null) }
                
                // Fetch user profile from Firestore
                val snapshot = Firebase.firestore.collection("users").document(currentUserId).get()
                if (snapshot.exists) {
                    val photoUrls = try {
                        snapshot.get<List<String>>("photoUrls")
                    } catch (e: Exception) {
                        emptyList()
                    }
                    
                    // Fallback to older avatarUrl structure if photoUrls doesn't exist
                    val finalPhotos = if (photoUrls.isEmpty()) {
                        val avatarUrl = try { snapshot.get<String?>("avatarUrl") } catch (e: Exception) { null }
                        if (avatarUrl != null) listOf(avatarUrl) else emptyList()
                    } else {
                        photoUrls
                    }
                    
                    _state.update { 
                        it.copy(
                            photos = finalPhotos,
                            isLoading = false,
                            hasUnsavedChanges = false
                        )
                    }
                } else {
                    _state.update { it.copy(isLoading = false, error = "Profile not found.") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun movePhoto(fromIndex: Int, toIndex: Int) {
        if (fromIndex < 0 || toIndex < 0 || fromIndex >= _state.value.photos.size || toIndex >= _state.value.photos.size) return
        
        val newList = _state.value.photos.toMutableList()
        // Swap elements
        val temp = newList[fromIndex]
        newList[fromIndex] = newList[toIndex]
        newList[toIndex] = temp
        
        _state.update { 
            it.copy(
                photos = newList,
                hasUnsavedChanges = true,
                error = null
            )
        }
    }

    fun addPhoto(url: String) {
        val currentPhotos = _state.value.photos
        if (currentPhotos.size >= 6) return
        
        val newList = currentPhotos.toMutableList().apply { add(url) }
        
        _state.update { 
            it.copy(
                photos = newList,
                hasUnsavedChanges = true,
                error = null
            )
        }
    }

    fun deletePhoto(index: Int) {
        val currentPhotos = _state.value.photos
        if (index < 0 || index >= currentPhotos.size) return
        
        if (currentPhotos.size <= 2) {
            _state.update { it.copy(error = "En az 2 fotoğrafın olmak zorundadır.") }
            return
        }
        
        val newList = currentPhotos.toMutableList().apply { removeAt(index) }
        
        _state.update { 
            it.copy(
                photos = newList,
                hasUnsavedChanges = true,
                error = null
            )
        }
    }

    fun saveChanges() {
        if (currentUserId == null) return
        
        val currentPhotos = _state.value.photos
        if (currentPhotos.size < 2) {
            _state.update { it.copy(error = "En az 2 fotoğrafın olmak zorundadır.") }
            return
        }
        
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                // Update Firestore
                val userRef = Firebase.firestore.collection("users").document(currentUserId)
                userRef.update(
                    "photoUrls" to currentPhotos,
                    "avatarUrl" to (currentPhotos.firstOrNull() ?: "")
                )
                
                _state.update { 
                    it.copy(
                        isSaving = false,
                        hasUnsavedChanges = false
                    ) 
                }
                
                _events.emit(EditProfileEvent.NavigateBack)
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }
}
