package com.mcclabs.mook.data.repository

import com.mcclabs.mook.data.appFirestore
import com.mcclabs.mook.domain.repository.PushTokenRepository
import com.mcclabs.mook.util.Log
import com.mcclabs.mook.util.fetchPushToken
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FieldValue

/**
 * Stores the device's FCM token in `users/{uid}.fcmTokens`, the array the
 * `sendMessage` callable reads when it pushes a notification.
 *
 * The callable also prunes tokens the FCM API reports as invalid, so this side only
 * has to add on launch and remove on sign-out.
 */
class PushTokenRepositoryImpl : PushTokenRepository {

    override suspend fun registerCurrentDevice() {
        updateTokens(rethrow = false) { token -> FieldValue.arrayUnion(token) }
    }

    /**
     * Çıkış sırasında çağrılır (bkz. `LogoutUseCase`). Kayıttan farklı olarak hata yutulmaz:
     * çağıran taraf başarısızlığı bilmeli, loglamalı ve çıkışa yine de devam etmelidir.
     */
    override suspend fun unregisterCurrentDevice() {
        updateTokens(rethrow = true) { token -> FieldValue.arrayRemove(token) }
    }

    /**
     * Applies [change] to the `fcmTokens` array of the signed-in user's document.
     *
     * Kayıtta hatalar loglanıp yutulur (bildirim bir iyileştirmedir, açılışı engellememeli).
     * [rethrow] `true` ise (çıkış) hata ayrıca yukarı iletilir; çıkışın devam etmesine
     * `LogoutUseCase` karar verir.
     */
    private suspend fun updateTokens(rethrow: Boolean, change: (String) -> FieldValue) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        val token = fetchPushToken()
        if (token.isNullOrBlank()) return

        try {
            appFirestore.collection("users")
                .document(uid)
                .update("fcmTokens" to change(token))
        } catch (e: Exception) {
            Log.e("FCM token kaydı güncellenemedi (uid=$uid)", e)
            if (rethrow) throw e
        }
    }
}
