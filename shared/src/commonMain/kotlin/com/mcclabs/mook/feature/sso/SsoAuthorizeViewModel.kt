package com.mcclabs.mook.feature.sso

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.functions.functions
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SsoAuthorizeState(
    val clientName: String = "",
    val callbackUrl: String = "",
    val userEmail: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface SsoAuthorizeEvent {
    data class RedirectToCallback(val url: String) : SsoAuthorizeEvent
    object NavigateToLogin : SsoAuthorizeEvent
    object NavigateBack : SsoAuthorizeEvent
}

class SsoAuthorizeViewModel(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(SsoAuthorizeState())
    val state: StateFlow<SsoAuthorizeState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SsoAuthorizeEvent>()
    val events = _events.asSharedFlow()

    init {
        val client = savedStateHandle.get<String>("client") ?: "Companion App"
        val callback = savedStateHandle.get<String>("callback") ?: ""
        val currentUser = Firebase.auth.currentUser

        if (currentUser == null) {
            // User is not logged in to Mook, force them to login first.
            // A more complex flow would redirect to login with a return-to parameter,
            // but for simplicity, we just send them to Login.
            viewModelScope.launch {
                _events.emit(SsoAuthorizeEvent.NavigateToLogin)
            }
        } else {
            _state.update {
                it.copy(
                    clientName = client,
                    callbackUrl = callback,
                    userEmail = currentUser.email ?: "Unknown Email"
                )
            }
        }
    }

    fun onAllowClicked() {
        val currentCallback = _state.value.callbackUrl
        if (currentCallback.isEmpty()) {
            _state.update { it.copy(error = "Invalid callback URL.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                // Call the Cloud Function
                val result = Firebase.functions.httpsCallable("generateSsoToken").invoke()
                
                // Parse the response to get the custom token
                // Depending on the serialization, it might be a Map or JSON.
                // Firebase Functions SDK usually returns a Map<String, Any> for JS objects.
                val data = result.data<Map<String, String>>()
                val customToken = data["token"]

                if (customToken.isNullOrEmpty()) {
                    _state.update { it.copy(isLoading = false, error = "Invalid token received from server.") }
                } else {
                    // Redirect back to the companion app with the token
                    val finalUrl = "$currentCallback?token=$customToken"
                    _events.emit(SsoAuthorizeEvent.RedirectToCallback(finalUrl))
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Failed to generate SSO token.") }
            }
        }
    }

    fun onDenyClicked() {
        viewModelScope.launch {
            _events.emit(SsoAuthorizeEvent.NavigateBack)
        }
    }
}
