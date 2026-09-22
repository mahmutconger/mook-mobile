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
 * Canlı çeviri demosunun CTA'sının, kullanıcıyı WalkTalk'a devretmek için açtığı adres.
 *
 * Adres **kimlik ya da token taşımaz**. Token, WalkTalk açıldıktan sonra başlattığı
 * yetkilendirme isteğinde, kullanıcı onay verince Mook tarafından üretilir.
 *
 * Başlattığı el sıkışma:
 * 1. Mook `M` değerini üretip saklar ve `https://walktalkk.com/chat?source=…&flow=sso&mook_state=M` adresini açar.
 * 2. WalkTalk **kendi** `W` değerini üretip saklar ve
 *    `https://mook-sso.web.app/sso/authorize?client_id=walktalk&redirect_uri=https%3A%2F%2Fwalktalkk.com%2Fsso-callback&state=W&mook_state=M`
 *    adresini açar. `M`'yi yalnızca iletir, kendi CSRF değeri olarak kullanmaz.
 * 3. Mook istemciyi, redirect_uri'yi ve `M`'yi doğrular, onay alır ve
 *    `https://walktalkk.com/sso-callback#token=…&state=W` adresini yalnızca WalkTalk paketine teslim eder.
 * 4. WalkTalk dönen `W`'yi kendi kaydıyla karşılaştırır.
 *
 * @param source WalkTalk analitiği için kaynak etiketi; kullanıcı verisi değildir.
 * @param mookState [com.mcclabs.mook.domain.sso.GenerateAuthStateUseCase] ile üretilmiş değer.
 *   Üretilemediyse `null` verilir; akış bu durumda WalkTalk'un kendi başlattığı akış gibi sürer.
 */
fun buildWalkTalkSsoEntryUrl(
    source: String = WALKTALK_SOURCE_DEMO,
    mookState: String? = null,
): String = buildString {
    append(WALKTALK_SSO_ENTRY_URL)
    append("?source=").append(encodeUrlComponent(source))
    append("&flow=sso")
    if (mookState != null) {
        append("&mook_state=").append(encodeUrlComponent(mookState))
    }
}

/** Provenance tag for the live-translation demo CTA. */
const val WALKTALK_SOURCE_DEMO: String = "mook_translate_demo"
