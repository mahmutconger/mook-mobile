package com.mcclabs.mook.billing

import com.mcclabs.mook.domain.billing.AdDisplayRules
import com.mcclabs.mook.domain.model.MatchResult
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Reklam gösterim kuralları: eşleşme kutlaması ve başarısız beğeniler reklamla kesilmez. */
class AdDisplayRulesTest {

    @Test
    fun karsilikliEslesmedeGecisReklamiDenenmez() {
        assertFalse(AdDisplayRules.allowsLikeInterstitial(MatchResult.MutualMatch))
    }

    @Test
    fun eslesmeYineDeReklamSikligiSayacinaEklenir() {
        assertTrue(AdDisplayRules.countsTowardAdCadence(MatchResult.MutualMatch))
    }

    @Test
    fun eslesmeOlusturmayanBasariliBegenidenSonraReklamDenenebilir() {
        assertTrue(AdDisplayRules.allowsLikeInterstitial(MatchResult.SingleLike))
    }

    @Test
    fun basarisizYaDaCevrimdisiBegenidenSonraReklamYokVeSayilmaz() {
        for (result in listOf(MatchResult.Error("ağ hatası"), MatchResult.QueuedOffline, MatchResult.Pass)) {
            assertFalse(AdDisplayRules.allowsLikeInterstitial(result))
            assertFalse(AdDisplayRules.countsTowardAdCadence(result))
        }
    }
}
