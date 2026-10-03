package com.mcclabs.mook.billing

import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.domain.billing.WinBackEligibility
import com.mcclabs.mook.domain.billing.WinBackHiddenReason
import com.mcclabs.mook.domain.billing.WinBackOfferDecision
import com.mcclabs.mook.domain.billing.WinBackOfferUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Gereksinim 2.7: Win-Back (geri kazanım) indirimi uygunluk kuralları.
 *
 * Asıl 90 günlük soğuma matematiği sunucudadır (`functions/src/monetization.ts`) — bu
 * test [SubscriptionRepository]'yi MockK ile sahteleyerek yalnızca [WinBackOfferUseCase]'in
 * o sonucu DOĞRU bir sunum kararına indirgediğini ve BELİRSİZLİKTE HER ZAMAN güvenli
 * tarafta (gizli) kaldığını doğrular — tıpkı [PriceChangeConfirmationUseCaseTest]'te
 * olduğu gibi.
 */
@DisplayName("WinBackOfferUseCase")
class WinBackOfferUseCaseTest {

    private val repository = mockk<SubscriptionRepository>()
    private val useCase = WinBackOfferUseCase(repository)

    @Test
    fun `soguma suresi doldu ise indirim gosterilir`() = runTest {
        coEvery { repository.winBackEligibility() } returns Result.success(
            WinBackEligibility(isEligible = true, cooldownEndsAtMillis = null),
        )

        assertEquals(WinBackOfferDecision.Show, useCase())
    }

    @Test
    fun `90 gunluk soguma suresi henuz dolmadiysa indirim gizlenir`() = runTest {
        coEvery { repository.winBackEligibility() } returns Result.success(
            WinBackEligibility(isEligible = false, cooldownEndsAtMillis = 9_999_999_999L),
        )

        val decision = assertIs<WinBackOfferDecision.Hidden>(useCase())
        assertEquals(WinBackHiddenReason.COOLDOWN_ACTIVE, decision.reason)
    }

    @Test
    fun `sunucu hatasi durumunda belirsizlikte indirim gizlenir`() = runTest {
        // Gereksinim 2.7'nin amacı kötüye kullanımı ÖNLEMEKTİR — bir ağ hatasında
        // "uygun say" yönünde YANLIŞ bir varsayılana ASLA düşülmemeli.
        coEvery { repository.winBackEligibility() } returns Result.failure(IllegalStateException("network"))

        val decision = assertIs<WinBackOfferDecision.Hidden>(useCase())
        assertEquals(WinBackHiddenReason.UNKNOWN, decision.reason)
    }

    @Test
    fun `revenuecat yapilandirilmamissa (FreeSubscriptionRepository benzeri hata) indirim gizlenir`() = runTest {
        coEvery { repository.winBackEligibility() } returns
            Result.failure(IllegalStateException("Billing is not configured"))

        val decision = assertIs<WinBackOfferDecision.Hidden>(useCase())
        assertEquals(WinBackHiddenReason.UNKNOWN, decision.reason)
    }
}
