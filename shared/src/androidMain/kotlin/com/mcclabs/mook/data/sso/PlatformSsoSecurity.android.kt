package com.mcclabs.mook.data.sso

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.FirebaseApp
import com.mcclabs.mook.domain.sso.AuthStateStore
import com.mcclabs.mook.domain.sso.PendingAuthState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom

private val secureRandom = SecureRandom()

internal actual fun platformSecureRandomBytes(size: Int): ByteArray =
    ByteArray(size).also(secureRandom::nextBytes)

// Paylaşılan katmanın Koin grafiğinde Context yok; Firebase ise ContentProvider ile
// uygulama açılışında zaten başlatılmış durumda, bu yüzden uygulama Context'i oradan alınır.
internal actual fun createAuthStateStore(): AuthStateStore =
    SharedPreferencesAuthStateStore { FirebaseApp.getInstance().applicationContext }

private class SharedPreferencesAuthStateStore(
    contextProvider: () -> Context,
) : AuthStateStore {

    private val prefs: SharedPreferences by lazy {
        contextProvider().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun save(state: PendingAuthState) {
        withContext(Dispatchers.IO) {
            // apply() yerine commit(): kullanıcı hemen WalkTalk'a geçer ve sistem Mook
            // sürecini öldürebilir; kayıt diske yazılmadan dönmemeliyiz.
            prefs.edit()
                .putString(KEY_VALUE, state.value)
                .putString(KEY_CLIENT_ID, state.clientId)
                .putLong(KEY_ISSUED_AT, state.issuedAtMillis)
                .commit()
        }
    }

    override suspend fun read(): PendingAuthState? = withContext(Dispatchers.IO) {
        runCatching {
            val value = prefs.getString(KEY_VALUE, null) ?: return@runCatching null
            val clientId = prefs.getString(KEY_CLIENT_ID, null) ?: return@runCatching null
            if (!prefs.contains(KEY_ISSUED_AT)) return@runCatching null
            PendingAuthState(value, clientId, prefs.getLong(KEY_ISSUED_AT, 0L))
        }.getOrNull()
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            runCatching { prefs.edit().clear().commit() }
        }
    }

    private companion object {
        const val PREFS_NAME = "mook_sso_state"
        const val KEY_VALUE = "state"
        const val KEY_CLIENT_ID = "client_id"
        const val KEY_ISSUED_AT = "issued_at"
    }
}
