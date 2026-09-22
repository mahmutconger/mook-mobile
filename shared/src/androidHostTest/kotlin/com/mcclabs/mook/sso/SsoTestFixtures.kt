package com.mcclabs.mook.sso

import com.mcclabs.mook.domain.sso.SsoClientPolicy

/** Testlerde ortak kullanılan sabitler. Değerler üretim beyaz listesiyle bilinçli olarak aynıdır. */
internal object SsoFixtures {
    const val CLIENT_ID = "walktalk"
    const val REDIRECT_URI = "https://walktalkk.com/sso-callback"

    /** 32 baytın base64url kodlaması uzunluğunda (43 karakter), geçerli biçimde bir state. */
    const val VALID_STATE = "Q2FsbGJhY2tTdGF0ZVZhbHVlXzAxMjM0NTY3ODlhYmM"
    const val OTHER_VALID_STATE = "T3RoZXJTdGF0ZVZhbHVlXzAxMjM0NTY3ODlhYmNkZWY"

    const val NOW = 1_800_000_000_000L
    const val TTL = 10 * 60 * 1000L

    val WALKTALK = SsoClientPolicy(
        clientId = CLIENT_ID,
        displayName = "WalkTalk",
        allowedRedirectUris = setOf(REDIRECT_URI),
        androidPackageName = "com.istaps.walktalk2",
    )

    val OTHER_CLIENT = SsoClientPolicy(
        clientId = "partner",
        displayName = "Partner",
        allowedRedirectUris = setOf("https://partner.example.com/sso-callback"),
        androidPackageName = "com.example.partner",
    )
}
