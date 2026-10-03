package com.mcclabs.mook.domain.billing

import com.mcclabs.mook.domain.model.MatchResult

/**
 * Reklam gösterim kuralları — bir reklamın gösterilip gösterilemeyeceğine dair, platformdan
 * bağımsız ve test edilebilir TEK karar noktası.
 *
 * Kurallar (para kazanma raporu "Bilinçli olarak reklam koymadığımız yerler"):
 * - Banner reklam HİÇBİR ekranda kullanılmaz (düşük gelir, tasarımı bozar); bu yüzden banner
 *   bileşeni kod tabanından tamamen kaldırılmıştır.
 * - Eşleşme kutlama ekranı reklamla ASLA kesilmez: karşılıklı eşleşme sonucunda beğeni geçiş
 *   reklamı denenmez, kullanıcı doğrudan kutlama ekranına gider.
 * - Başarısız ya da çevrimdışı kuyruğa alınmış bir beğeniden sonra reklam gösterilmez.
 * - Sohbet, kayıt/onboarding ve uygulama açılışında reklam yoktur (ilgili ekranlar reklam
 *   bileşeni içermez).
 */
object AdDisplayRules {

    /**
     * Bir beğeni sonucunun ardından beğeni geçiş reklamı (interstitial) DENENEBİLİR mi?
     * Yalnızca eşleşme oluşturmayan, başarılı bir beğeni (`SingleLike`) için `true` döner.
     * Sıklık/bekleme süresi kontrolü bundan sonra [AdFrequencyPolicy] tarafından yapılır.
     */
    fun allowsLikeInterstitial(result: MatchResult): Boolean = result is MatchResult.SingleLike

    /**
     * Reklam gösterilmeyen ama BAŞARILI bir beğeni (ör. karşılıklı eşleşme) yine de reklam
     * sıklığı sayacına dahil edilmeli mi? Eşleşme de kullanıcının bir eylemidir; sayılmazsa
     * reklamlar fiilen seyrekleşir ve sıklık politikası tutarsızlaşır.
     */
    fun countsTowardAdCadence(result: MatchResult): Boolean =
        result is MatchResult.SingleLike || result is MatchResult.MutualMatch
}
