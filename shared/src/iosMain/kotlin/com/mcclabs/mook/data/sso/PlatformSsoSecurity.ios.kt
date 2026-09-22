package com.mcclabs.mook.data.sso

import com.mcclabs.mook.domain.sso.AuthStateStore
import com.mcclabs.mook.domain.sso.PendingAuthState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSUserDefaults
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformSecureRandomBytes(size: Int): ByteArray {
    val bytes = ByteArray(size)
    if (size == 0) return bytes
    val status = bytes.usePinned { pinned ->
        SecRandomCopyBytes(kSecRandomDefault, size.convert(), pinned.addressOf(0))
    }
    // Başarısız bir çağrı sıfırlarla dolu bir dizi bırakır; bunu state olarak kullanmak
    // tahmin edilebilir bir değer üretirdi.
    check(status == errSecSuccess) { "SecRandomCopyBytes başarısız: $status" }
    return bytes
}

internal actual fun createAuthStateStore(): AuthStateStore = UserDefaultsAuthStateStore()

private class UserDefaultsAuthStateStore : AuthStateStore {

    private val defaults get() = NSUserDefaults.standardUserDefaults

    override suspend fun save(state: PendingAuthState) {
        defaults.setObject(state.value, forKey = KEY_VALUE)
        defaults.setObject(state.clientId, forKey = KEY_CLIENT_ID)
        // NSInteger 64 bit; epoch milisaniyesi sığar.
        defaults.setInteger(state.issuedAtMillis, forKey = KEY_ISSUED_AT)
    }

    override suspend fun read(): PendingAuthState? {
        val value = defaults.stringForKey(KEY_VALUE) ?: return null
        val clientId = defaults.stringForKey(KEY_CLIENT_ID) ?: return null
        defaults.objectForKey(KEY_ISSUED_AT) ?: return null
        return PendingAuthState(value, clientId, defaults.integerForKey(KEY_ISSUED_AT))
    }

    override suspend fun clear() {
        listOf(KEY_VALUE, KEY_CLIENT_ID, KEY_ISSUED_AT).forEach(defaults::removeObjectForKey)
    }

    private companion object {
        const val KEY_VALUE = "mook_sso_state"
        const val KEY_CLIENT_ID = "mook_sso_state_client_id"
        const val KEY_ISSUED_AT = "mook_sso_state_issued_at"
    }
}
