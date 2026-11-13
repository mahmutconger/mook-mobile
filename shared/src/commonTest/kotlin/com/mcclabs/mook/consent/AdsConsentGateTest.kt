package com.mcclabs.mook.consent

import com.mcclabs.mook.domain.consent.ConsentPolicy
import com.mcclabs.mook.domain.consent.adsMayStart
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Reklam SDK'sının KVKK kararından ÖNCE başlamaması gerekir: aksi halde reklam kimliği ve
 * IP adresi, kullanıcı onay ekranını görmeden Google'a gider. Bkz. [adsMayStart].
 */
class AdsConsentGateTest {

    @Test
    fun adsDoNotStartBeforeTheKvkkDecisionEvenIfUmpAllowsThem() {
        // Türkiye'de UMP formu çıkmaz ve canRequestAds() hemen true döner; bu tek başına
        // reklamları başlatmaya YETMEMELİ.
        assertFalse(adsMayStart(platformAllowsAds = true, decisionPolicyVersion = null))
    }

    @Test
    fun adsStartOnceTheCurrentPolicyIsDecided() {
        assertTrue(adsMayStart(platformAllowsAds = true, decisionPolicyVersion = ConsentPolicy.CURRENT_VERSION))
    }

    @Test
    fun aDecisionForAnOlderPolicyIsNotEnough() {
        // Politika sürümü artırıldığında kullanıcı yeniden karar verene kadar reklam başlamaz.
        assertFalse(adsMayStart(platformAllowsAds = true, decisionPolicyVersion = ConsentPolicy.CURRENT_VERSION - 1))
    }

    @Test
    fun umpRefusalAlwaysWins() {
        // AEA/BK'da UMP onayı yoksa KVKK kararı reklamları açamaz.
        assertFalse(adsMayStart(platformAllowsAds = false, decisionPolicyVersion = ConsentPolicy.CURRENT_VERSION))
        assertFalse(adsMayStart(platformAllowsAds = false, decisionPolicyVersion = null))
    }
}
