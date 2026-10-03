package com.mcclabs.mook.billing

import com.mcclabs.mook.domain.billing.EntitlementFallbackVerifier
import com.mcclabs.mook.domain.billing.Tier
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Immediate Authorization Fallback (Altyapı Gereksinimi): [EntitlementFallbackVerifier]'ın SAF
 * karar mantığını doğrular — RevenueCat webhook gecikmesi yarış koşulunda, hem Firebase Auth
 * özel talebi HEM DE `customers/{uid}` belgesi bayatken (ve YALNIZCA o zaman) RevenueCat REST
 * doğrulamasının tetiklendiğini garanti eder.
 */
class EntitlementFallbackVerifierTest {

    private val readClaimedTier = mockk<suspend () -> Tier>()
    private val readDocumentTier = mockk<suspend () -> Tier>()
    private val verifyViaRevenueCatRest = mockk<suspend () -> Tier?>()

    private fun createVerifier() = EntitlementFallbackVerifier(
        readClaimedTier = readClaimedTier,
        readDocumentTier = readDocumentTier,
        verifyViaRevenueCatRest = verifyViaRevenueCatRest,
    )

    @Nested
    @DisplayName("Her iki katman da güncelken")
    inner class BothLayersFresh {
        @Test
        @DisplayName("REST doğrulamasına HİÇ gitmez ve null döner")
        fun doesNotCallRestFallback() = runTest {
            coEvery { readClaimedTier() } returns Tier.PREMIUM
            coEvery { readDocumentTier() } returns Tier.PREMIUM

            val result = createVerifier().verify(locallyKnownTier = Tier.PREMIUM)

            assertNull(result)
            coVerify(exactly = 0) { verifyViaRevenueCatRest() }
        }
    }

    @Nested
    @DisplayName("Yalnızca BİR katman bayatken")
    inner class OnlyOneLayerStale {
        @Test
        @DisplayName("özel talep bayat ama belge güncelse yine REST'e gitmez")
        fun claimStaleButDocumentFresh() = runTest {
            coEvery { readClaimedTier() } returns Tier.FREE
            coEvery { readDocumentTier() } returns Tier.PREMIUM

            val result = createVerifier().verify(locallyKnownTier = Tier.PREMIUM)

            assertNull(result)
            coVerify(exactly = 0) { verifyViaRevenueCatRest() }
        }

        @Test
        @DisplayName("belge bayat ama özel talep güncelse yine REST'e gitmez")
        fun documentStaleButClaimFresh() = runTest {
            coEvery { readClaimedTier() } returns Tier.PREMIUM
            coEvery { readDocumentTier() } returns Tier.FREE

            val result = createVerifier().verify(locallyKnownTier = Tier.PREMIUM)

            assertNull(result)
            coVerify(exactly = 0) { verifyViaRevenueCatRest() }
        }
    }

    @Nested
    @DisplayName("Her iki katman da bayatken (RevenueCat webhook gecikmesi yarış koşulu)")
    inner class BothLayersStale {
        @Test
        @DisplayName("REST doğrulamasını tetikler ve doğrulanmış kademeyi döner")
        fun triggersRestFallbackAndReturnsVerifiedTier() = runTest {
            coEvery { readClaimedTier() } returns Tier.FREE
            coEvery { readDocumentTier() } returns Tier.FREE
            coEvery { verifyViaRevenueCatRest() } returns Tier.PREMIUM

            val result = createVerifier().verify(locallyKnownTier = Tier.PREMIUM)

            assertEquals(Tier.PREMIUM, result)
            coVerify(exactly = 1) { verifyViaRevenueCatRest() }
        }

        @Test
        @DisplayName("REST doğrulaması da başarısız olursa null döner (asla bir reddi kendiliğinden onaya çevirmez)")
        fun returnsNullWhenRestFallbackAlsoFails() = runTest {
            coEvery { readClaimedTier() } returns Tier.FREE
            coEvery { readDocumentTier() } returns Tier.FREE
            coEvery { verifyViaRevenueCatRest() } returns null

            val result = createVerifier().verify(locallyKnownTier = Tier.PREMIUM)

            assertNull(result)
            coVerify(exactly = 1) { verifyViaRevenueCatRest() }
        }
    }
}
