package com.mcclabs.mook.domain.support

/**
 * RevenueCat'te tanımlı hak (entitlement) tanımlayıcıları -- `functions/src/monetization.ts`teki
 * `tierFromEntitlements`in kabul ettiği küçük harfli isimlerle BİREBİR aynı tutulur ki sunucu
 * tarafındaki beyaz liste (whitelist) ile istemci ASLA uyuşmazlığa düşmesin.
 */
enum class PromotionalEntitlement(val wireValue: String) {
    ECONOMY("economy"),
    STANDARD("standard"),
    PREMIUM("premium"),
}

/**
 * RevenueCat Promosyonel Hak REST API'sinin (`POST
 * /v1/subscribers/{app_user_id}/entitlements/{entitlement_identifier}/promotional`) kabul
 * ettiği `duration` değerleri -- bkz. `functions/src/customerSupport.ts` KDoc'u.
 */
enum class PromotionalGrantDuration(val wireValue: String) {
    DAILY("daily"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    THREE_MONTH("three_month"),
    SIX_MONTH("six_month"),
    YEARLY("yearly"),
    LIFETIME("lifetime"),
}

/**
 * Gereksinim 7 (Faz 6, Gözlemlenebilirlik & Destek Araçları): sunucudaki
 * `grantPromotionalEntitlement` Cloud Function'ını TETİKLEYEN tek nokta.
 *
 * YETKİLENDİRME KASITLI olarak BURADA DEĞİL, YALNIZCA sunucuda uygulanır (bkz. Cloud
 * Function'ın KDoc'u): bu depo/arayüz çağıranın GERÇEKTEN bir yönetici/destek yetkilisi
 * OLDUĞUNU asla varsaymaz -- istemci taraflı hiçbir rol bayrağına GÜVENİLMEZ (`moderateUser`/
 * `bootstrapMonetization` ile AYNI ilke). Çağıran yetkili DEĞİLSE sunucu `permission-denied`
 * döner ve bu, [requestPromotionalGrant]'in `Result.failure`i olarak yüzeye çıkar.
 */
interface CustomerSupportRepository {
    /**
     * Bir HATA (bug) YAŞAYAN kullanıcıya geçici bir promosyonel hak tanınması isteğini
     * sunucuya iletir.
     *
     * @param targetUid Hakkın tanınacağı kullanıcının Firebase UID'si.
     * @param entitlement Tanınacak hak (ör. [PromotionalEntitlement.PREMIUM]).
     * @param duration Hakkın süresi (ör. [PromotionalGrantDuration.WEEKLY]).
     * @param reason Destek bileti/hata açıklaması -- sunucu tarafındaki `support_grants`
     *   denetim izine (audit trail) KAYDEDİLİR.
     * @return Başarıda `Result.success(Unit)`; aksi halde açıklayıcı bir [Throwable] taşıyan
     *   `Result.failure` (ör. çağıran yetkili değilse, ya da RevenueCat isteği başarısız olduysa).
     */
    suspend fun requestPromotionalGrant(
        targetUid: String,
        entitlement: PromotionalEntitlement,
        duration: PromotionalGrantDuration,
        reason: String,
    ): Result<Unit>
}
