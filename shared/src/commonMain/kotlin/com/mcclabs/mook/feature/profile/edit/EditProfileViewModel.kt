package com.mcclabs.mook.feature.profile.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.repository.AuthRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import com.mcclabs.mook.data.appFirestore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import com.mcclabs.mook.domain.model.Country
import com.mcclabs.mook.domain.model.Language
import com.mcclabs.mook.domain.model.Languages
import com.mcclabs.mook.domain.validation.InputValidator
import com.mcclabs.mook.util.getAvailableCountries
import com.mcclabs.mook.util.isOfMinimumAge
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.edit_profile_min_photos_error
import mook.shared.generated.resources.edit_profile_age_error

data class EditProfileState(
    val photos: List<String> = emptyList(),
    val displayName: String = "",
    val bio: String = "",
    val birthDateMillis: Long? = null,
    val selectedLanguage: Language? = null,
    val selectedCountry: Country? = null,
    val availableCountries: List<Country> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val error: String? = null,
    val displayNameError: String? = null
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
        loadProfile()
    }

    private fun loadProfile() {
        if (currentUserId == null) {
            _state.update { it.copy(isLoading = false, error = "User not logged in.") }
            return
        }

        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true, error = null) }

                val countries = getAvailableCountries()

                // Fetch user profile from Firestore
                val snapshot = appFirestore.collection("users").document(currentUserId).get()
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

                    val displayName = runCatching { snapshot.get<String?>("displayName") }.getOrNull().orEmpty()
                    val bio = runCatching { snapshot.get<String?>("bio") }.getOrNull().orEmpty()
                    val birthDateMillis = runCatching { snapshot.get<Long?>("birthDateMillis") }.getOrNull()
                    val languageCode = runCatching { snapshot.get<String?>("languageCode") }.getOrNull()
                    val countryCode = runCatching { snapshot.get<String?>("countryCode") }.getOrNull()

                    _state.update {
                        it.copy(
                            photos = finalPhotos,
                            displayName = displayName,
                            bio = bio,
                            birthDateMillis = birthDateMillis,
                            selectedLanguage = Languages.fromCode(languageCode),
                            selectedCountry = countryCode?.let { code ->
                                countries.firstOrNull { c -> c.code.equals(code, ignoreCase = true) }
                            },
                            availableCountries = countries,
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

    fun onNameChange(name: String) {
        _state.update { it.copy(displayName = name, displayNameError = null, hasUnsavedChanges = true, error = null) }
    }

    fun onBioChange(bio: String) {
        _state.update { it.copy(bio = bio, hasUnsavedChanges = true, error = null) }
    }

    fun onBirthDateChange(millis: Long?) {
        _state.update { it.copy(birthDateMillis = millis, hasUnsavedChanges = true, error = null) }
    }

    fun onLanguageChange(language: Language) {
        _state.update { it.copy(selectedLanguage = language, hasUnsavedChanges = true, error = null) }
    }

    fun onCountryChange(country: Country) {
        _state.update { it.copy(selectedCountry = country, hasUnsavedChanges = true, error = null) }
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
            viewModelScope.launch {
                val msg = getString(Res.string.edit_profile_min_photos_error)
                _state.update { it.copy(error = msg) }
            }
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

        val current = _state.value
        val currentPhotos = current.photos
        if (currentPhotos.size < 2) {
            viewModelScope.launch {
                val msg = getString(Res.string.edit_profile_min_photos_error)
                _state.update { it.copy(error = msg) }
            }
            return
        }

        // Name must be valid.
        val nameValidation = InputValidator.validateDisplayName(current.displayName)
        if (!nameValidation.isValid) {
            _state.update { it.copy(displayNameError = nameValidation.errorMessage) }
            return
        }

        // Age gate (defense-in-depth): a birth date, if set, must be 18+.
        val birthDateMillis = current.birthDateMillis
        if (birthDateMillis != null && !isOfMinimumAge(birthDateMillis)) {
            viewModelScope.launch {
                val msg = getString(Res.string.edit_profile_age_error)
                _state.update { it.copy(error = msg) }
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                // Update only the edited fields (not a full-document overwrite), so the
                // shared users doc keeps every other field intact.
                val userRef = appFirestore.collection("users").document(currentUserId)
                val updates = buildList<Pair<String, Any?>> {
                    add("photoUrls" to currentPhotos)
                    add("avatarUrl" to (currentPhotos.firstOrNull() ?: ""))
                    add("displayName" to current.displayName.trim())
                    add("bio" to current.bio.trim())
                    birthDateMillis?.let { add("birthDateMillis" to it) }
                    current.selectedLanguage?.code?.let { add("languageCode" to it) }
                    current.selectedCountry?.code?.let { add("countryCode" to it) }
                }.toTypedArray()
                userRef.update(*updates)

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
