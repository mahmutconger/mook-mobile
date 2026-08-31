package com.mcclabs.mook.util

/**
 * This device's FCM registration token, or `null` when there is not one yet.
 *
 * The two platforms obtain it differently, which is why this is an `expect`:
 *
 * - **Android** asks the Firebase Messaging SDK directly, which is linked into the
 *   shared module.
 * - **iOS** cannot: FirebaseMessaging is linked into the Xcode app, not into the
 *   shared framework. There the token arrives in `MessagingDelegate` and is handed
 *   *in* through [com.mcclabs.mook.onPushTokenReceived], so this returns whatever
 *   Swift last supplied — `null` until APNs registration completes.
 */
expect suspend fun fetchPushToken(): String?
