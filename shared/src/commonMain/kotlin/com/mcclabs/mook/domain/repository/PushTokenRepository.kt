package com.mcclabs.mook.domain.repository

/**
 * Keeps the signed-in user's FCM registration tokens on their `users` document.
 *
 * The `sendMessage` callable pushes a notification to every token in that array, so
 * a token that is never written means the recipient is simply never notified — the
 * push path stays silently dead no matter how correct the server is.
 *
 * A user has one token per device, hence an array rather than a single field.
 */
interface PushTokenRepository {

    /**
     * Adds this device's token to the signed-in user's `fcmTokens` array.
     *
     * Safe to call on every launch: the write is an `arrayUnion`, so a token already
     * present is not duplicated. A no-op when nobody is signed in or the platform has
     * no token yet (iOS before APNs registration completes).
     */
    suspend fun registerCurrentDevice()

    /**
     * Removes this device's token, so a signed-out phone stops receiving the previous
     * user's messages. Call before clearing the Firebase session, while the uid is
     * still available.
     */
    suspend fun unregisterCurrentDevice()
}
