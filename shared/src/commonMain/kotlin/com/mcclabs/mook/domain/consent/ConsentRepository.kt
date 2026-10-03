package com.mcclabs.mook.domain.consent

import kotlinx.coroutines.flow.StateFlow

/**
 * Gereksinim 3 (Faz 6, KVKK/GDPR): UMP (User Messaging Platform) tabanlı onay durumunun TEK
 * doğruluk kaynağı -- Analytics ve AdMob SDK'larının başlatılması bu durumun ÇÖZÜLMESİNE
 * (bkz. [ConsentState.isResolved]) kadar ERTELENİR (bkz. `AdMobConsentManager.android.kt`).
 *
 * Türkiye'deki (KVKK) kullanıcılar için UMP'nin kendi IAB TCF akışı (öncelikle AB/İngiltere
 * bölgesel yasalarını hedefler) tek başına yeterli değildir; bu yüzden
 * [ConsentState.crossBorderTransferAccepted] ve [ConsentState.personalizedAdsAllowed], KVKK'ye
 * özgü AYRI bir açık onay ekranıyla toplanır ve KALICI olarak saklanır.
 *
 * Diğer tüm üçüncü taraf SDK sarmalayıcıları gibi (bkz. [com.mcclabs.mook.domain.billing.SubscriptionRepository])
 * ViewModel'ler DOĞRUDAN platforma özgü UMP/AdMob/Firebase çağrılarına değil, bu arayüze
 * bağımlıdır -- Repository deseni ve SOLID'in Bağımlılığın Tersine Çevrilmesi ilkesiyle tutarlı.
 */
interface ConsentRepository {
    val state: StateFlow<ConsentState>

    /** UMP'nin onay bilgisini günceller ve gerekiyorsa (EEA/İngiltere) onay formunu gösterir. */
    fun refreshConsent()

    /**
     * Gereksinim 3 (KVKK): sınır ötesi veri aktarımı (Firebase, RevenueCat, DeepL) onayını
     * KALICI olarak kaydeder. `false` iken Analytics toplama KAPALI kalır; uygulamanın TEMEL
     * işlevi (kimlik doğrulama, eşleşme) bundan ETKİLENMEZ.
     */
    fun setCrossBorderTransferAccepted(accepted: Boolean)

    /** Gereksinim 3/4: kişiselleştirilmiş reklam onayını KALICI olarak kaydeder. */
    fun setPersonalizedAdsAllowed(accepted: Boolean)

    /**
     * KVKK onay ekranında verilen kararı TEK seferde ve atomik olarak kalıcılaştırır: her iki
     * izin ve kararın verildiği [ConsentPolicy.CURRENT_VERSION] birlikte yazılır. Bu çağrıdan
     * sonra [ConsentState.requiresDecision] `false` olur ve Analytics/AdMob durumu ANINDA
     * (uygulamayı yeniden başlatmadan) yeni karara göre güncellenir.
     */
    fun recordDecision(crossBorderTransferAccepted: Boolean, personalizedAdsAllowed: Boolean)

    /** UMP gizlilik seçenekleri formunu gösterir -- kullanıcı onayını sonradan değiştirebilmelidir. */
    fun showPrivacyOptionsForm()
}

data class ConsentState(
    /** UMP'den (AB/İngiltere) ya da yerel KVKK akışından bir karar alınana kadar `false`. */
    val isResolved: Boolean = false,
    /** Firebase Analytics'in toplama yapıp yapamayacağı -- bkz. [crossBorderTransferAccepted]. */
    val analyticsAllowed: Boolean = false,
    /** AdMob'un başlatılıp başlatılamayacağı (herhangi bir reklam -- kişiselleştirilmemiş dahil). */
    val adsAllowed: Boolean = false,
    /**
     * Gereksinim 3 (KVKK): Firebase, RevenueCat ve DeepL'e (üçü de Türkiye dışı) kullanıcı
     * verisi aktarımının AÇIKÇA onaylandığı. `false` iken bu üç SDK'nın veri işleme kapsamı
     * asgariye indirilir (bkz. [ConsentRepository] KDoc'u).
     */
    val crossBorderTransferAccepted: Boolean = false,
    /** `false` iken reklam istekleri Gereksinim 4 (TFUA/NPA) gereği kişiselleştirilmemiş işaretlenir. */
    val personalizedAdsAllowed: Boolean = false,
    val privacyOptionsRequired: Boolean = false,
    /**
     * Kullanıcının KVKK kararını hangi aydınlatma/onay metni sürümü için verdiği; hiç karar
     * vermediyse `null`. [ConsentPolicy.CURRENT_VERSION]'dan küçükse onay ekranı yeniden gösterilir.
     */
    val decisionPolicyVersion: Int? = null,
)

/**
 * KVKK onay metninin sürümü. Aydınlatma metni, veri aktarılan taraflar ya da işleme amaçları
 * değiştiğinde ARTIRILMALIDIR — böylece daha önce karar vermiş kullanıcılara onay ekranı
 * bir kez daha gösterilir. Aksi halde ekran kullanıcı başına yalnızca bir kez görünür.
 */
object ConsentPolicy {
    const val CURRENT_VERSION: Int = 1
}

/**
 * Onay ekranı gösterilmeli mi? Platformun onay altyapısı (Android'de UMP) çözülene kadar
 * `false` kalır — böylece UMP'nin kendi formu ile KVKK ekranı üst üste binmez.
 */
val ConsentState.requiresDecision: Boolean
    get() = isResolved && (decisionPolicyVersion ?: 0) < ConsentPolicy.CURRENT_VERSION
