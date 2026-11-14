package com.mcclabs.mook.domain.support

/**
 * Kullanıcıya gösterilen destek iletişim bilgisi. Play mağaza kaydındaki destek e-postası,
 * gizlilik politikası ve hesap silme sayfası (`hosting/mook-sso/account-deletion.html`) ile
 * aynı olmalıdır; adres değişirse bu üç yer birlikte güncellenir.
 */
object SupportContact {
    const val EMAIL = "sametyanan123@gmail.com"
    const val MAILTO_URI = "mailto:$EMAIL"
}
