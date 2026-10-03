package com.mcclabs.mook.domain.analytics

/**
 * Gereksinim 2.10 (Faz 4): bir reklamın gösterildiği yüzeyin BİÇİMİ — ARPDAU
 * (kullanıcı başına günlük ortalama gelir) analizinde reklam gelirini biçime göre
 * segmentlemek için kullanılır.
 */
enum class AdFormat {
    BANNER,
    INTERSTITIAL,
    NATIVE,
    REWARDED,
}

/**
 * Gereksinim 2.10 & 2.14 (Faz 4): Firebase Analytics'i (ve gelecekte eklenebilecek
 * başka bir analiz SDK'sını) soyutlayan tek giriş noktası.
 *
 * Diğer tüm üçüncü taraf SDK sarmalayıcıları gibi (bkz. [com.mcclabs.mook.domain.billing.SubscriptionRepository],
 * [com.mcclabs.mook.domain.repository.UpdateRepository]) ViewModel'ler ve platforma
 * özgü reklam kodu (ör. `AdFormats.android.kt`) DOĞRUDAN `Firebase.analytics`e değil,
 * bu arayüze bağımlıdır — Repository deseni ve SOLID'in Bağımlılığın Tersine Çevrilmesi
 * ilkesiyle tutarlı.
 *
 * Bir analitik çağrısının başarısız olması KULLANICI AKIŞINI ASLA etkilememelidir;
 * implementasyon (bkz. `AnalyticsRepositoryImpl`) her çağrıyı kendi içinde yutar ve
 * yalnızca loglar.
 */
interface AnalyticsRepository {

    /**
     * Gereksinim 2.10: bir reklam gösteriminin AdMob'un `OnPaidEventListener`
     * geri çağrısından gelen GERÇEK geliri ("ödenen değer") ile birlikte kaydını tutar.
     * ARPDAU hesaplamaları [placement] (nerede gösterildi), [format] ve [valueMicros]/
     * [currencyCode] kırılımlarına ihtiyaç duyar.
     *
     * @param placement Reklamın gösterildiği ekran/nokta (ör. "discover_banner",
     *   "discover_like_interstitial", "discover_rewarded_like").
     * @param valueMicros AdMob'un mikro birim (1/1.000.000) cinsinden bildirdiği ödenen değer.
     * @param precisionType AdMob'un `AdValue.getPrecisionType()` ile döndürdüğü ham
     *   hassasiyet kodu (ör. tahmini/kesin) — ham değer olarak saklanır, yorumlanmaz.
     */
    fun logAdRevenuePaid(
        placement: String,
        format: AdFormat,
        valueMicros: Long,
        currencyCode: String,
        precisionType: Int,
    )

    /**
     * Gereksinim 2.14: karşılıklı bir eşleşme oluştuğunda ateşlenir. [viewerIsPremium]
     * Free ve Premium havuzlarının eşleşme oranlarını (havuz sağlığı) ayrı ayrı
     * izleyebilmek için taşınır.
     */
    fun logMatchCreated(viewerIsPremium: Boolean)

    /**
     * Gereksinim 2.14: bir sohbette karşı tarafın AÇILIŞ mesajına BU CİHAZDAKİ
     * kullanıcının verdiği İLK yanıt gönderildiğinde ateşlenir — bir eşleşmenin
     * gerçek bir sohbete dönüşüp dönüşmediğini (havuz sağlığının bir diğer göstergesi)
     * Free/Premium kırılımıyla izlemek içindir.
     *
     * Bu olay KASITLI olarak yalnızca "ben yanıt verdiğimde" ateşlenir, "karşı taraf
     * yanıt verdiğinde" DEĞİL: bir istemç yalnızca KENDİ kullanıcısının abonelik
     * kademesini güvenilir biçimde bilebilir (karşı tarafınkini asla bilemez).
     * Eşleşen her iki tarafın istemcisi de kendi yanıtını raporladığından, sunucu
     * tarafındaki toplam analiz zaten HER İKİ yönü de doğal olarak kapsar.
     * [replierIsPremium] bu yüzden HER ZAMAN olayı ateşleyen cihazın kendi
     * kullanıcısının kademesidir.
     */
    fun logFirstMessageReplied(replierIsPremium: Boolean)

    /**
     * Gereksinim 5 (Faz 6, KVKK Madde 11 -- Analytics veri silme talebi): Firebase
     * Analytics'in kendi Kullanıcı-Kimliği (User-ID) mekanizmasını [uid] ile eşler.
     *
     * Bu ÇOK ÖNEMLİDİR: `deleteAccount` Cloud Function'ı (bkz. `deleteAccount.ts`deki
     * `requestAnalyticsDataDeletion()`), GA4'ün Kullanıcı Silme (User Deletion) API'sini
     * TAM OLARAK bu User-ID alanıyla eşleşen bir `userId` tanımlayıcısıyla çağırır --
     * bu çağrı OLMADAN sunucu tarafındaki silme isteğinin GA4'te eşleştirebileceği HİÇBİR
     * kimlik olmaz. `App.kt`deki `signedInUid` efektinden, HER oturum açma/soğuk
     * başlangıçta çağrılmalıdır.
     */
    fun identifyUser(uid: String)

    /**
     * Kapsamlı Analitik Olay Sözlüğü'ndeki ([AnalyticsEvent]) bir olayı gönderir: kapı
     * kararları, reklam yaşam döngüsü, paywall/satın alma adımları ve abonelik değişimleri.
     * Diğer tüm çağrılar gibi asla hata fırlatmaz ve kullanıcı akışını kesmez.
     */
    fun track(event: AnalyticsEvent)
}
