package com.mcclabs.mook.domain.appcheck

/**
 * Gereksinim 2 (Faz 6, Kapsamlı App Check Uygulaması): hassas bir sunucu işlemi (ör.
 * [com.mcclabs.mook.domain.billing.SubscriptionRepository.verifyEntitlementNow]) için TEK
 * KULLANIMLIK (limited-use, tekrar oynatmaya/replay karşı korumalı) bir App Check jetonuyla
 * korunan bir Cloud Functions çağrısının SONUCU.
 *
 * Bu tip [com.mcclabs.mook.domain.billing.BillingError] ile AYNI ilkeyi izler: ham
 * SDK/Play Integrity ayrıntısı bu sınıfın DIŞINDA (yalnızca `androidMain` eşlemesinde)
 * tutulur, böylece `commonMain`'de kalır ve iOS için de (henüz no-op olsa da) anlamlıdır.
 */
sealed interface AppCheckCallResult<out T> {
    data class Success<T>(val value: T) : AppCheckCallResult<T>

    /**
     * Play Integrity API kotası/hız sınırı aşıldı (Gereksinim 2: "Include error handling on
     * the Android client if Play Integrity API limits are exceeded"). Çağıran bu turu
     * SESSİZCE atlayıp SON ÇARE olmayan bir yola (mevcut iki katmanlı okuma) geri dönmelidir
     * -- kullanıcı ENGELLENMEMELİDİR (non-blocking).
     */
    data object QuotaExceeded : AppCheckCallResult<Nothing>

    /** Cihaz/uygulama bütünlüğü kanıtlanamadı (ör. rootlu cihaz, resmi olmayan APK) YA DA
     *  sunucu jetonu reddetti (`UNAUTHENTICATED`). */
    data object AttestationFailed : AppCheckCallResult<Nothing>

    /** Ağ sorunu ya da sınıflandırılamayan diğer bir hata. */
    data object Unavailable : AppCheckCallResult<Nothing>
}

/**
 * Gereksinim 2: sunucu tarafında yalnızca TEK KULLANIMLIK bir App Check jetonuyla (ve
 * sunucudaki `consumeAppCheckToken: true` seçeneğiyle -- bkz. `subscriptionVerification.ts`)
 * korunan "highly sensitive" Cloud Functions çağrıları için platforma özgü ağ geçidi.
 *
 * Android GERÇEK implementasyonu, gitlive'ın KMP Functions sarmalayıcısını ([com.mcclabs.mook.data.appHttpsCallable])
 * KASITLI olarak BAYPAS EDER: o sarmalayıcı tek kullanımlık jeton seçeneğini
 * (`HttpsCallableOptions.setLimitedUseAppCheckTokens`) DIŞARI VERMEZ -- bu, `AdMobConsentManager`'ın
 * UMP/AdMob için native SDK'yı DOĞRUDAN kullanmasıyla AYNI, daha önce kurulmuş gerekçedir
 * (bkz. o sınıfın KDoc'u). `appHttpsCallable`'ın TEK global geçiş noktası (yasaklı kullanıcı
 * engeli) olma özelliği BOZULMAZ: bu ayrı yol yalnızca App Check jeton STRATEJİSİ için
 * baypas eder, yasaklama kontrolü İÇİN değil -- `verifyEntitlementNow`'ı çağıran
 * `AndroidRevenueCatSubscriptionRepository` zaten yasaklı bir kullanıcı için hiçbir
 * anlamlı işlem YAPMAZ (bkz. o sınıfın diğer akışları).
 *
 * `create...()` fabrika fonksiyonu KASITLI olarak burada (domain katmanında) DEĞİL,
 * `data.appcheck.PlatformLimitedUseAppCheckCallableInvoker.kt`dedir -- `expect`/`actual`
 * bildirimleri AYNI paket adını PAYLAŞMALIDIR (Kotlin dil kuralı); bu depodaki AYNI
 * desen için bkz. `data.billing.PlatformSubscriptionRepository.kt` / `data.consent.PlatformConsentRepository.kt`.
 *
 * iOS: Gereksinim kapsamı DIŞINDADIR ("Focus EXCLUSIVELY on Android") -- no-op bir
 * implementasyon her zaman [AppCheckCallResult.Unavailable] döner.
 */
interface LimitedUseAppCheckCallableInvoker {
    suspend fun invoke(functionName: String, payload: Map<String, Any?> = emptyMap()): AppCheckCallResult<Map<String, Any?>>
}
