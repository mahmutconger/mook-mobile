package com.mcclabs.mook.util

/** Base URL of the WalkTalk chat deep link (App Link / Universal Link). */
private const val WALKTALK_CHAT_URL = "https://walktalkk.com/chat"

/**
 * Builds the WalkTalk "open chat" deep link.
 *
 * Mook and WalkTalk share one Firebase project, so [peerId] (the other person's
 * Firebase uid) is the same id WalkTalk stores under `users/{uid}`. The current
 * user's [currentUid] + [currentEmail] travel along so WalkTalk can verify the
 * link opener against its own auth session; empty values are omitted.
 *
 * Result: `https://walktalkk.com/chat?peerId=<peerId>&uid=<currentUid>&email=<currentEmail>`
 */
fun buildWalkTalkChatUrl(
    peerId: String,
    currentUid: String,
    currentEmail: String,
): String = buildString {
    append(WALKTALK_CHAT_URL)
    append("?peerId=").append(encodeUrlComponent(peerId))
    if (currentUid.isNotEmpty()) {
        append("&uid=").append(encodeUrlComponent(currentUid))
    }
    if (currentEmail.isNotEmpty()) {
        append("&email=").append(encodeUrlComponent(currentEmail))
    }
}
