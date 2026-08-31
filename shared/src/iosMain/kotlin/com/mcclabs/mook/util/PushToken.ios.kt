package com.mcclabs.mook.util

/**
 * The last token Swift handed in.
 *
 * A plain top-level `var`: under Kotlin/Native's current memory model mutable global
 * state is shared across threads, so the `MessagingDelegate` callback and the
 * registration coroutine can be on different queues. A torn read here would at worst
 * miss one token and pick it up on the next launch, so an atomic buys nothing.
 */
internal var latestPushToken: String? = null

actual suspend fun fetchPushToken(): String? = latestPushToken
