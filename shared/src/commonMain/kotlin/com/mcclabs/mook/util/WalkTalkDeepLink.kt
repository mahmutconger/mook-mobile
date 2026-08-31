package com.mcclabs.mook.util

/** Host of WalkTalk's App Link / Universal Link domain. */
private const val WALKTALK_BASE_URL = "https://walktalkk.com"

/** Base URL of the WalkTalk chat deep link (App Link / Universal Link). */
private const val WALKTALK_CHAT_URL = "$WALKTALK_BASE_URL/chat"

/**
 * Path the "Try WalkTalk" CTA opens.
 *
 * Deliberately the same `/chat` path the match flow already uses, because that path is
 * known to be registered in WalkTalk's App Links / `apple-app-site-association` — an
 * unregistered path would open Safari instead of the app on iOS and quietly break the
 * CTA. Change this one constant (to e.g. `/sso`) once WalkTalk publishes a dedicated
 * landing path and adds it to its AASA file.
 */
private const val WALKTALK_SSO_ENTRY_URL = WALKTALK_CHAT_URL

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

/**
 * Builds the URL the live-translation demo's CTA opens to hand the user over to WalkTalk.
 *
 * This URL carries **no identity and no secret** — only a provenance hint. That is
 * deliberate: the SSO token is minted by Mook's existing `generateSsoToken` callable
 * during the `mook://authorize` handshake that *WalkTalk* initiates once it is open.
 * Putting a token in this URL would leak it into browser history and referrer headers
 * on the not-installed path, and would duplicate a flow that already exists.
 *
 * The handshake this button starts:
 * 1. Mook opens `https://walktalkk.com/chat?source=mook&flow=sso`.
 * 2. WalkTalk opens `mook://authorize?client=WalkTalk&callback=walktalk%3A%2F%2Fsso-callback`.
 * 3. Mook's [com.mcclabs.mook.feature.sso.SsoAuthorizeScreen] mints the custom token and
 *    returns `walktalk://sso-callback?token=…`.
 *
 * Opening it through [rememberWalkTalkChatOpener] gives the store fallback for free:
 * Google Play on Android when the package is absent, and the web page (which redirects
 * to the App Store) on iOS.
 *
 * @param source Provenance tag recorded by WalkTalk's analytics. Not user data.
 */
fun buildWalkTalkSsoEntryUrl(source: String = WALKTALK_SOURCE_DEMO): String = buildString {
    append(WALKTALK_SSO_ENTRY_URL)
    append("?source=").append(encodeUrlComponent(source))
    append("&flow=sso")
}

/** Provenance tag for the live-translation demo CTA. */
const val WALKTALK_SOURCE_DEMO: String = "mook_translate_demo"
