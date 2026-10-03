package com.mcclabs.mook.data.sso

import com.mcclabs.mook.data.appHttpsCallable
import com.mcclabs.mook.data.translation.mapCallableErrorCode
import com.mcclabs.mook.domain.sso.SsoAccount
import com.mcclabs.mook.domain.sso.SsoAuthRepository
import com.mcclabs.mook.domain.sso.SsoTokenFailure
import com.mcclabs.mook.domain.sso.SsoTokenResult
import com.mcclabs.mook.domain.translation.TranslationError
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

@Serializable
private data class SsoTokenResponse(val token: String? = null)

/** `generateSsoToken` callable'ı üzerinden token üreten Firebase uygulaması. */
class FirebaseSsoAuthRepository : SsoAuthRepository {

    override fun currentAccount(): SsoAccount? =
        Firebase.auth.currentUser?.let { user -> SsoAccount(email = user.email) }

    override suspend fun mintSsoToken(): SsoTokenResult {
        val response = try {
            appHttpsCallable(CALLABLE_NAME)
                .invoke()
                .data<SsoTokenResponse>()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            // Ham hata mesajı kullanıcıya gösterilmez ve günlüğe yazılmaz: sunucu yanıtı
            // token ya da iç ayrıntı içerebilir. Yalnızca sınıflandırılmış tür döner.
            val code = (error as? FirebaseFunctionsException)?.code?.name
            return SsoTokenResult.Failure(mapCallableErrorCode(code).toSsoFailure())
        }

        val token = response.token
        return if (token.isNullOrBlank()) {
            SsoTokenResult.Failure(SsoTokenFailure.SERVER)
        } else {
            SsoTokenResult.Success(token)
        }
    }

    private companion object {
        const val CALLABLE_NAME = "generateSsoToken"
    }
}

/** Callable hata eşlemesi çeviri özelliğiyle paylaşılır; SSO yalnızca üç sonucu ayırt eder. */
private fun TranslationError.toSsoFailure(): SsoTokenFailure = when (this) {
    TranslationError.NETWORK, TranslationError.TIMEOUT -> SsoTokenFailure.NETWORK
    TranslationError.UNAUTHENTICATED -> SsoTokenFailure.SESSION_EXPIRED
    else -> SsoTokenFailure.SERVER
}
