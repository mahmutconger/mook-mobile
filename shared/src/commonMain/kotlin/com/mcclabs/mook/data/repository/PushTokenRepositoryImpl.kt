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
        updateTokens { token -> FieldValue.arrayUnion(token) }
    }

    override suspend fun unregisterCurrentDevice() {
        updateTokens { token -> FieldValue.arrayRemove(token) }
    }

    /**
     * Applies [change] to the `fcmTokens` array of the signed-in user's document.
     *
     * Failures are logged and swallowed: push is an enhancement, and neither a launch
     * nor a sign-out should fail because a token could not be recorded.
     */
    private suspend fun updateTokens(change: (String) -> FieldValue) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        val token = fetchPushToken()
        if (token.isNullOrBlank()) return

        try {
            appFirestore.collection("users")
                .document(uid)
                .update("fcmTokens" to change(token))
        } catch (e: Exception) {
            Log.e("FCM token kaydı güncellenemedi (uid=$uid)", e)
        }
    }
}
