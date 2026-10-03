package com.mcclabs.mook.domain.billing

import com.mcclabs.mook.domain.repository.BoostSummary
import com.mcclabs.mook.domain.repository.InteractionRepository

/** Gereksinim 2.12 (Faz 4): Discover ekranının Boost aktif durumu için UI fazı. */
sealed interface BoostPhase {
    /** Aktif bir Boost yok. */
    data object Idle : BoostPhase

    /** Boost aktif — [remainingMillis] 30 dakikalık pencerenin kalan süresidir (>= 0). */
    data class Active(val remainingMillis: Long) : BoostPhase
}

/**
 * Gereksinim 2.12 (Faz 4): Boost'un (30 dakikalık, faturalandırma-döngüsü bazlı ekstra
 * görünürlük artırımı) başlatılması, geri sayım durumunun hesaplanması ve tamamlanma
 * özetinin alınmasını tek bir yerde toplayan domain kullanım durumu.
 *
 * ## Faturalandırma döngüsü, takvim ayı DEĞİL
 * Boost kotasının NE ZAMAN sıfırlanacağının asıl (yetkili) kaynağı sunucudur —
 * `functions/src/monetization.ts`teki `usage/{uid}.boosts` alanı artık takvim ayı
 * değiştiğinde DEĞİL, yalnızca RevenueCat'in "RENEWAL"/"INITIAL_PURCHASE" webhook olayı —
 * yani kullanıcının KENDİ abonelik yıldönümü — geldiğinde sıfırlanır (bkz.
 * `revenuecatWebhook.ts`). Bu sınıf, [nextResetHintMillis]'i de AYNI ilkeyle —
 * [SubscriptionRepository]'nin RevenueCat'ten okuduğu [EntitlementState.expiresAtMillis]
 * yetkisinin bitiş tarihinden — türetir, ASLA takvim ayının sonundan değil. Kullanılmayan
 * Boost'lar bir SONRAKİ döngüye devretmez: sunucu her yeni döngüde sayacı doğrudan SIFIRA
 * çeker (bir önceki bakiyeyi TAŞIMAZ), bu yüzden istemci tarafında ayrıca bir "devretme"
 * mantığı kurulmasına hiç gerek yoktur.
 *
 * Gerçek günlük/aylık kota kontrolü (Boost hakkının TÜKENMESİ) her zaman olduğu gibi
 * sunucudaki `activateBoost` callable'ında yapılır — bu sınıf yalnızca (a) o çağrıyı
 * sarmalar, (b) sunucunun döndürdüğü bitiş zaman damgasından SAF bir UI fazı hesaplar,
 * ve (c) Boost penceresi kapandığında özet verisini çeker. Asıl zorlama her zaman
 * sunucudadır — [FeatureGate] ve `CharacterQuotaManager` ile aynı desen.
 */
class BoostManagerUseCase(
    private val subscriptionRepository: SubscriptionRepository,
    private val interactionRepository: InteractionRepository,
) {

    /** Sunucudaki `activateBoost` callable'ını çağırır ve yeni bitiş zaman damgasını döner. */
    suspend fun activate(): Result<Long> = interactionRepository.activateBoost()

    /**
     * Boost penceresi kapandığında ("Boost bitti! Profilin X kişiye fazladan gösterildi"
     * özet diyaloğu için) sunucudan özeti çeker.
     */
    suspend fun fetchCompletionSummary(): Result<BoostSummary> = interactionRepository.getBoostSummary()

    /**
     * [nowMillis] ve sunucunun döndürdüğü [boostUntilMillis]'ten SAF (yan etkisiz) bir UI
     * fazı hesaplar — ağ çağrısı yapmaz, yalnızca iki zaman damgasını karşılaştırır.
     */
    fun phaseFor(nowMillis: Long, boostUntilMillis: Long?): BoostPhase {
        if (boostUntilMillis == null || boostUntilMillis <= nowMillis) return BoostPhase.Idle
        return BoostPhase.Active(remainingMillis = boostUntilMillis - nowMillis)
    }

    /**
     * Boost kotasının bir SONRAKİ ne zaman sıfırlanacağına dair UI ipucu — KASITLI olarak
     * [SubscriptionRepository]'nin RevenueCat'ten okuduğu [EntitlementState.expiresAtMillis]
     * yetkisinden okunur, herhangi bir takvim ayı hesaplamasından DEĞİL (bkz. sınıf KDoc'u).
     * Yetki henüz çözülmediyse ya da bir abonelik yoksa (ör. Free kullanıcı) `null` döner.
     */
    fun nextResetHintMillis(): Long? = subscriptionRepository.state.value.expiresAtMillis
}
