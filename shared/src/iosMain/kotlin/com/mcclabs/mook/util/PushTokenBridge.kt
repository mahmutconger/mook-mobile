package com.mcclabs.mook.util

import com.mcclabs.mook.data.repository.PushTokenRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Outlives any screen: the token callback fires from `MessagingDelegate` on Firebase's
 * own queue, with no ViewModel or composition in scope to borrow a scope from.
 */
private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/**
 * Entry point for `AppDelegate.messaging(_:didReceiveRegistrationToken:)`.
 *
 * FirebaseMessaging is linked into the Xcode target, not into this framework, so iOS
 * cannot pull its own token the way Android does — Swift must push it in.
 *
 * Registration is attempted here *as well as* from `App`, because the two events race:
 * `App` registers when a uid appears, which misses a token that arrives afterwards;
 * this registers when a token appears, which misses a user who signs in afterwards.
 * Together they cover both orderings, and `arrayUnion` makes the overlap free.
 *
 * Deliberately in its own file rather than in `PushToken.ios.kt`: the Obj-C facade
 * class is named after the file, and `PushTokenBridgeKt` is a name Swift can be
 * written against without guessing how the `.ios` infix is mangled.
 *
 * Swift: `PushTokenBridgeKt.onPushTokenReceived(token: fcmToken)`
 */
fun onPushTokenReceived(token: String) {
    latestPushToken = token.takeIf { it.isNotBlank() } ?: return
    bridgeScope.launch {
        // Stateless, so constructing one here rather than reaching into Koin — which
        // is started inside the App composable and may not exist yet at this point.
        PushTokenRepositoryImpl().registerCurrentDevice()
    }
}
