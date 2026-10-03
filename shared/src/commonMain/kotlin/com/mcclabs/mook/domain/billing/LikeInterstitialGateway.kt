package com.mcclabs.mook.domain.billing

import com.mcclabs.mook.ads.LikeInterstitialAttempt

/**
 * Gereksinim 2.15 (KRİTİK): bir beğeni eyleminin SUNUCU YANITI alındıktan ve arayüz geçişi
 * (kartın kaldırılması / eşleşme diyaloğunun açılması) TAMAMLANDIKTAN SONRA, uygun olduğunda
 * bir interstitial reklam göstermeyi dener.
 *
 * AdMob'un "beklenmedik interstitial" (unexpected interstitial) politikası, bir reklamın
 * kullanıcının kendi başlattığı bir eylemi (ör. "Beğen" dokunuşu) SENKRON olarak KESEREK
 * gösterilmesini yasaklar; reklam yalnızca kullanıcının zaten beklediği doğal bir GEÇİŞ
 * noktasında (ör. bir profil kartından bir SONRAKİNE geçerken) gösterilmelidir. Bu arayüzün
 * TEK yöntemi bilinçli olarak `AfterTransition` adını taşır ve YALNIZCA ilgili ViewModel,
 * sunucunun beğeni isteğine kesin bir cevap verdiğini VE arayüzü buna göre güncellediğini
 * (StateFlow güncellendi, olay yayıldı — "kaydırma geçişi tamamlandı") doğruladıktan SONRA
 * çağrılmalıdır; ASLA eylemin kendisinden ÖNCE.
 *
 * Önceki tasarımda (`showLikeInterstitialBeforeAction`) reklam, sunucuya istek gönderilmeden
 * ÖNCE, kullanıcının "Beğen" dokunuşunu senkron olarak bloklayarak gösteriliyordu — bu tam
 * olarak AdMob'un yasakladığı "beklenmedik interstitial" örneğiydi. Bu arayüz, o platforma
 * özgü (AdMob SDK'sına bağımlı) mantığı [AdFrequencyPolicy] gibi saf/test edilebilir
 * sınıflardan ayrı tutarken, ViewModel'lerin çağrı SIRASINI (Dependency Inversion ile enjekte
 * edilen bir arayüz üzerinden) MockK ile doğrulanabilir kılar — bkz. `DiscoverViewModelTest`.
 */
interface LikeInterstitialGateway {
    /**
     * Uygun olduğunda (bkz. [AdFrequencyPolicy.canShow]) bir interstitial göstermeyi dener ve
     * kapanmasını bekler; uygun değilse (kadans/kota/onboarding penceresi) ya da önceden
     * yüklenmiş bir reklam yoksa ANINDA varsayılan [LikeInterstitialAttempt] ile döner — bu
     * durum kullanıcı deneyimini ASLA geciktirmez.
     */
    suspend fun attemptShowAfterTransition(isFree: Boolean, likesEver: Int): LikeInterstitialAttempt

    /** Reklam sonrası kadans sayaçlarını günceller (bkz. [AdFrequencyPolicy.canShow] `actionsSinceLastAd`). */
    fun recordAction(isFree: Boolean, attempt: LikeInterstitialAttempt, succeeded: Boolean)

    /**
     * Profil ziyareti geçiş reklamını (her 3. ziyaret — bkz. [AdFrequencyRules]) gösterir ve
     * kullanıcı kapatana kadar bekler. Genel sıklık sınırları (oturum/gün/en kısa aralık, ilk 24
     * saat), Remote Config kill-switch'i ve KVKK onayı yine geçerlidir.
     *
     * @return Reklam gerçekten gösterildiyse `true`; hazır değilse veya bir sınıra takıldıysa `false`.
     */
    suspend fun showProfileVisitInterstitial(isFree: Boolean): Boolean
}
