package com.mcclabs.mook.domain.billing

/**
 * Immediate Authorization Fallback (Altyapı Gereksinimi): RevenueCat webhook gecikmesi yarış
 * koşuluna karşı SON ÇARE doğrulama mantığı — bkz. [SubscriptionRepository.verifyEntitlementNow]
 * KDoc'undaki tam gerekçe.
 *
 * Bu sınıf SAF karar mantığıdır: üç bağımlılık da (özel talep okuma, belge okuma, RevenueCat
 * REST doğrulaması) birer `suspend` fonksiyon olarak ENJEKTE edilir — sınıfın kendisi ne
 * Firebase'e ne Android'e ne de ağa bağımlıdır. Bu yüzden MockK ile üç sahte/mock fonksiyon
 * geçirilerek tam olarak test edilebilir (bkz. `EntitlementFallbackVerifierTest`). Gerçek
 * Android implementasyonu `AndroidRevenueCatSubscriptionRepository.verifyEntitlementNow()`de
 * bu sınıfı gerçek okumalarla besler.
 */
class EntitlementFallbackVerifier(
    /** Mevcut (ZORLA YENİLENMEMİŞ, önbellekteki) Firebase kimlik jetonunun özel talebinden
     *  çözümlenen kademe. Talep yoksa/okunamıyorsa [Tier.FREE] döner — sunucudaki
     *  `tierFromEntitlements()` ile BİREBİR aynı "boş -> FREE" ilkesi. */
    private val readClaimedTier: suspend () -> Tier,
    /** `customers/{uid}` Firestore belgesinden çözümlenen kademe — aynı "boş -> FREE" ilkesi. */
    private val readDocumentTier: suspend () -> Tier,
    /** RevenueCat'in KENDİ REST API'sine sunucu tarafında (gizli API anahtarıyla) DOĞRUDAN
     *  sorup dönen, yetkili kademe — istek başarısız olursa `null`. */
    private val verifyViaRevenueCatRest: suspend () -> Tier?,
) {
    /**
     * @param locallyKnownTier RevenueCat SDK'sının CİHAZDA zaten bildiği (bir satın alma
     *   tamamlandığında AĞ GECİKMESİ OLMADAN güncellenen) kademe — hem "bayat mı?"
     *   karşılaştırmasının referans noktasıdır hem de doğrulamaya gerek yoksa dönülecek
     *   üstü kapalı "değişiklik yok" sinyalidir.
     * @return Yalnızca hem özel talep HEM DE belge [locallyKnownTier]dan FARKLI (bayat)
     *   görünüyorsa RevenueCat REST doğrulamasının sonucunu döner; aksi halde (biri zaten
     *   günceli yansıtıyorsa, ya da her ikisi de zaten [locallyKnownTier] ile aynıysa) `null`
     *   döner. `null`, "REST'e gitmeye gerek yoktu / gitti ama doğrulayamadı" anlamına gelir
     *   — çağıran taraf bunu normal ret akışına devam etme sinyali olarak okumalıdır. Bu
     *   fonksiyon ASLA bir reddi kendiliğinden bir onaya ÇEVİRMEZ; yalnızca GERÇEKTEN bayat
     *   bir reddi düzeltir.
     */
    suspend fun verify(locallyKnownTier: Tier): Tier? {
        val claimStale = readClaimedTier() != locallyKnownTier
        val documentStale = readDocumentTier() != locallyKnownTier
        return if (claimStale && documentStale) verifyViaRevenueCatRest() else null
    }
}
