package com.mcclabs.mook.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The CTA URL is the one thing standing between the demo and a conversion, and it is
 * also the easiest place to leak a credential. Both properties are pinned here.
 */
class WalkTalkDeepLinkTest {

    @Test
    fun ctaUrlTargetsWalkTalksUniversalLinkDomain() {
        val url = buildWalkTalkSsoEntryUrl()
        assertTrue(
            url.startsWith("https://walktalkk.com/"),
            "CTA must use the https domain so iOS Universal Links and the store fallback work: $url",
        )
    }

    @Test
    fun ctaUrlCarriesProvenanceAndFlow() {
        assertEquals(
            "https://walktalkk.com/chat?source=mook_translate_demo&flow=sso",
            buildWalkTalkSsoEntryUrl(),
        )
    }

    @Test
    fun ctaUrlNeverCarriesIdentityOrSecrets() {
        val url = buildWalkTalkSsoEntryUrl()
        listOf("token", "uid", "email", "password", "key").forEach { forbidden ->
            assertFalse(
                url.contains(forbidden, ignoreCase = true),
                "CTA URL must not contain '$forbidden': $url",
            )
        }
    }

    @Test
    fun customSourceIsPercentEncoded() {
        val url = buildWalkTalkSsoEntryUrl(source = "liked tab & promo")
        assertEquals(
            "https://walktalkk.com/chat?source=liked%20tab%20%26%20promo&flow=sso",
            url,
        )
    }

    @Test
    fun chatDeepLinkStillBuildsTheMatchFlowUrl() {
        // The CTA additions must not disturb the existing match -> WalkTalk hand-off.
        assertEquals(
            "https://walktalkk.com/chat?peerId=abc&uid=me&email=a%40b.com",
            buildWalkTalkChatUrl(peerId = "abc", currentUid = "me", currentEmail = "a@b.com"),
        )
    }
}
