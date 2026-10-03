package com.mcclabs.mook.domain.billing

import kotlin.math.ceil

/**
 * Aktif ücretsiz deneme (trial) süresiyle ilgili saf hesaplamalar (Gereksinim 1.10).
 *
 * Bu nesnedeki her iki fonksiyon da SAF'tır — hiçbir repository'ye bağımlı değildir, yalnızca
 * [EntitlementState]'in zaten taşıdığı `inTrial`/`expiresAtMillis`/`tier` alanlarından karar
 * üretir. Bu bilinçli bir tasarım: hem Paywall hem Ayarlar ekranı aynı sonucu üretmeli, hem de
 * mantık MockK gerektirmeden (sahtelenecek hiçbir bağımlılık olmadığından) doğrudan test
 * edilebilmelidir.
 */
object TrialPeriod {
    private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

    /**
     * Aktif bir denemede kalan gün sayısını döner; deneme aktif değilse ya da bitiş tarihi
     * bilinmiyorsa `null` döner.
     *
     * Kalan süre yukarı yuvarlanır (`ceil`): örn. 30 dakika kaldıysa "0 gün kaldı" yerine
     * "1 gün kaldı" gösterilir — kullanıcıya süresinden ERKEN bitmiş gibi bir izlenim
     * vermemek için her zaman daha CÖMERT taraf tercih edilir. Süre çoktan geçmişse
     * (sunucu/istemci senkronizasyon gecikmesi) 0 döner, negatif değer ASLA dönmez.
     */
    fun remainingDays(state: EntitlementState, nowMillis: Long): Int? {
        if (!state.inTrial) return null
        val expiresAt = state.expiresAtMillis ?: return null
        val remainingMillis = expiresAt - nowMillis
        if (remainingMillis <= 0) return 0
        return ceil(remainingMillis / MILLIS_PER_DAY.toDouble()).toInt()
    }

    /**
     * Aktif bir deneme sırasında GENEL "Yükselt" teşviklerinin gösterilip gösterilmeyeceğine
     * karar verir.
     *
     * Deneme dışındayken her zaman `true`'dur (kural devre dışı). Aktif bir denemedeyken ise
     * yalnızca kademe DEĞİŞMEYEN (cross-grade — ör. aynı kademenin aylık/yıllık paketi
     * arasında geçiş) teklifler gösterilir; [targetTier] mevcut denenen kademeden FARKLIYSA
     * (daha üst bir kademeye "yükselt" teşviki) bastırılır — kullanıcı zaten bir kademeyi
     * deniyorken art arda "Premium'a geç!" göstermek gürültüdür ve mevcut kararını
     * sorgulatır.
     */
    fun shouldShowUpgradePrompt(current: EntitlementState, targetTier: Tier): Boolean {
        if (!current.inTrial) return true
        val isCrossGrade = targetTier == current.tier
        return isCrossGrade
    }
}
