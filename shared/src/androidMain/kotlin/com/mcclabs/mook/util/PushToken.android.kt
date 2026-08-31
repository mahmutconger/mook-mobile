package com.mcclabs.mook.util

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Reads the token straight from the Messaging SDK.
 *
 * Returns `null` rather than throwing when the lookup fails — a device without Play
 * Services, or one that has not reached Firebase yet, should lose push notifications,
 * not the sign-in it was called from.
 */
actual suspend fun fetchPushToken(): String? = suspendCancellableCoroutine { continuation ->
    FirebaseMessaging.getInstance().token
        .addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                Log.e("FCM token alınamadı", task.exception)
                continuation.resume(null)
            }
        }
}
