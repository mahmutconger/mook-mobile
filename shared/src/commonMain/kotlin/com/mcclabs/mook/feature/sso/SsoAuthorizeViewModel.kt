package com.mcclabs.mook.feature.sso

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.sso.SsoAuthRepository
import com.mcclabs.mook.domain.sso.SsoAuthorizationRequest
import com.mcclabs.mook.domain.sso.SsoClientPolicy
import com.mcclabs.mook.domain.sso.SsoDeliveryResult
import com.mcclabs.mook.domain.sso.SsoRejectionReason
import com.mcclabs.mook.domain.sso.SsoTokenFailure
import com.mcclabs.mook.domain.sso.SsoTokenResult
import com.mcclabs.mook.domain.sso.SsoVerificationResult
import com.mcclabs.mook.domain.sso.VerifiedSsoRequest
import com.mcclabs.mook.domain.sso.VerifyAuthCallbackUseCase
import com.mcclabs.mook.domain.sso.buildSsoRedirectUrl
import com.mcclabs.mook.navigation.NavRoutes
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * SSO onay ekranının durumu.
 *
 * Token hiçbir durumda UI state'inde tutulmaz: state kaydedilebilir, günlüğe yazılabilir
 * ya da ekran görüntüsü araçlarına sızabilir.
 */
sealed interface SsoAuthorizeUiState {
    data object Verifying : SsoAuthorizeUiState

    data class AwaitingConsent(
        val clientName: String,
        val accountEmail: String?,
        val isAuthorizing: Boolean = false,
        val error: SsoConsentError? = null,
    ) : SsoAuthorizeUiState

    /** Kullanıcıya neden ayrıntısı verilmez; yalnızca süre aşımı meşru kullanıcı için ayrı gösterilir. */
    data class Blocked(val reason: SsoBlockReason) : SsoAuthorizeUiState
}

/** Meşru kullanıcının karşılaşabileceği, tekrar denenebilir hatalar. */
enum class SsoConsentError { NETWORK, SERVER, CLIENT_APP_UNAVAILABLE }

enum class SsoBlockReason { UNVERIFIED_REQUEST, EXPIRED_REQUEST }

sealed interface SsoAuthorizeEvent {
    data class DeliverToClient(val redirectUrl: String, val client: SsoClientPolicy) : SsoAuthorizeEvent
    data object NavigateToLogin : SsoAuthorizeEvent
    data object Close : SsoAuthorizeEvent
}

class SsoAuthorizeViewModel(
    savedStateHandle: SavedStateHandle,
    private val verifyAuthCallback: VerifyAuthCallbackUseCase,
    private val ssoAuthRepository: SsoAuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<SsoAuthorizeUiState>(SsoAuthorizeUiState.Verifying)
    val state: StateFlow<SsoAuthorizeUiState> = _state.asStateFlow()

    // Tek seferlik olaylar için tamponlu Channel: init içinde, ekran henüz dinlemeye
    // başlamadan gönderilen bir olay (ör. giriş ekranına yönlendirme) kaybolmaz.
    private val _events = Channel<SsoAuthorizeEvent>(Channel.BUFFERED)
    val events: Flow<SsoAuthorizeEvent> = _events.receiveAsFlow()

    /** Yalnızca doğrulama başarılıysa dolar; token üretimi başka hiçbir kaynağa dayanmaz. */
    private var verifiedRequest: VerifiedSsoRequest? = null

    init {
        val request = SsoAuthorizationRequest(
            clientId = savedStateHandle.get<String>(NavRoutes.SsoAuthorize.ARG_CLIENT_ID),
            redirectUri = savedStateHandle.get<String>(NavRoutes.SsoAuthorize.ARG_REDIRECT_URI),
            state = savedStateHandle.get<String>(NavRoutes.SsoAuthorize.ARG_STATE),
            mookState = savedStateHandle.get<String>(NavRoutes.SsoAuthorize.ARG_MOOK_STATE),
        )
        viewModelScope.launch { verify(request) }
    }

    private suspend fun verify(request: SsoAuthorizationRequest) {
        when (val result = verifyAuthCallback(request)) {
            is SsoVerificationResult.Rejected -> block(result.reason)
            is SsoVerificationResult.Verified -> {
                val account = ssoAuthRepository.currentAccount()
                if (account == null) {
                    _events.send(SsoAuthorizeEvent.NavigateToLogin)
                    return
                }
                verifiedRequest = result.request
                _state.value = SsoAuthorizeUiState.AwaitingConsent(
                    clientName = result.request.client.displayName,
                    accountEmail = account.email,
                )
            }
        }
    }

    fun onAllowClicked() {
        val request = verifiedRequest ?: return
        val current = _state.value as? SsoAuthorizeUiState.AwaitingConsent ?: return
        // Çift dokunuş aynı istek için iki token üretmemeli.
        if (current.isAuthorizing) return
        _state.value = current.copy(isAuthorizing = true, error = null)

        viewModelScope.launch {
            when (val result = ssoAuthRepository.mintSsoToken()) {
                is SsoTokenResult.Success -> _events.send(
                    SsoAuthorizeEvent.DeliverToClient(
                        redirectUrl = buildSsoRedirectUrl(request, result.token),
                        client = request.client,
                    ),
                )
                is SsoTokenResult.Failure -> when (result.kind) {
                    SsoTokenFailure.SESSION_EXPIRED -> _events.send(SsoAuthorizeEvent.NavigateToLogin)
                    SsoTokenFailure.NETWORK -> showRetryableError(SsoConsentError.NETWORK)
                    SsoTokenFailure.SERVER -> showRetryableError(SsoConsentError.SERVER)
                }
            }
        }
    }

    /** Ekran, [SsoAuthorizeEvent.DeliverToClient] olayını işledikten sonra sonucu buraya bildirir. */
    fun onDeliveryResult(result: SsoDeliveryResult) {
        when (result) {
            SsoDeliveryResult.DELIVERED -> {
                // İstek tüketildi; aynı ekrandan ikinci bir token üretilemez.
                verifiedRequest = null
                viewModelScope.launch { _events.send(SsoAuthorizeEvent.Close) }
            }
            SsoDeliveryResult.CLIENT_APP_UNAVAILABLE -> showRetryableError(SsoConsentError.CLIENT_APP_UNAVAILABLE)
            // Sahte imzalı uygulama: sessizce ama kesin olarak durdurulur, tekrar deneme sunulmaz.
            SsoDeliveryResult.CLIENT_APP_UNTRUSTED -> block(SsoRejectionReason.REDIRECT_NOT_ALLOWED)
        }
    }

    fun onDenyClicked() = close()

    fun onCloseClicked() = close()

    private fun close() {
        verifiedRequest = null
        viewModelScope.launch { _events.send(SsoAuthorizeEvent.Close) }
    }

    private fun showRetryableError(error: SsoConsentError) {
        _state.update { current ->
            if (current is SsoAuthorizeUiState.AwaitingConsent) {
                current.copy(isAuthorizing = false, error = error)
            } else {
                current
            }
        }
    }

    private fun block(reason: SsoRejectionReason) {
        verifiedRequest = null
        _state.value = SsoAuthorizeUiState.Blocked(
            if (reason == SsoRejectionReason.STATE_EXPIRED) {
                SsoBlockReason.EXPIRED_REQUEST
            } else {
                SsoBlockReason.UNVERIFIED_REQUEST
            },
        )
    }
}
