package com.mcclabs.mook.data.billing

import android.annotation.SuppressLint
import android.provider.Settings
import com.google.firebase.FirebaseApp
import java.security.MessageDigest

/**
 * Gereksinim 1 (Faz 4): `Settings.Secure.ANDROID_ID`'yi ÖZETLEYEREK (SHA-256) döner — ham
 * ANDROID_ID hiçbir zaman saklanmaz/iletilmez, yalnızca geri döndürülemez özeti sunucuya
 * gider.
 *
 * ÖNEMLİ (dürüstçe belirtilmesi gereken sınır): ANDROID_ID, Android 8+'ta
 * uygulama-imzalama-anahtarı + kullanıcı profiline göre KAPSAMLANIR ve bir fabrika
 * sıfırlamasında YA DA imzalama anahtarı değiştiğinde DEĞİŞİR — bu yüzden "sahtelenemez" bir
 * donanım kimliği DEĞİLDİR. Bu, savunma-derinliği (defense-in-depth) amaçlı, pratik ve
 * yaygın kullanılan bir sinyaldir; asıl (mağaza düzeyindeki) uygunluk kontrolü hâlâ
 * RevenueCat'in `checkTrialOrIntroductoryPriceEligibility`'sidir (bkz. [SubscriptionRepository]
 * KDoc'u) — bu yalnızca ONUN kaçırdığı "aynı cihaz, farklı hesap" boşluğunu kapatan EK bir
 * katmandır.
 */
@SuppressLint("HardwareIds")
actual fun platformDeviceIdentifier(): String {
    // Paylaşılan katmanın Koin grafiğinde Context yok; Firebase ContentProvider ile uygulama
    // açılışında zaten başlatılmış durumda, bu yüzden uygulama Context'i oradan alınır (bkz.
    // PlatformPendingActionQueue.android.kt'deki aynı desen).
    val androidId = runCatching {
        Settings.Secure.getString(FirebaseApp.getInstance().applicationContext.contentResolver, Settings.Secure.ANDROID_ID)
    }.getOrNull()
    if (androidId.isNullOrBlank() || androidId == "9774d56d682e549c") {
        // "9774d56d682e549c" bilinen bir eski-cihaz/emülatör yer tutucusudur (bkz. Android
        // dokümantasyonu) — anlamsız bir kimliği özetleyip göndermek yerine boş döneriz.
        return ""
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(androidId.toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}
