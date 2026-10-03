package com.mcclabs.mook.billing

import com.mcclabs.mook.domain.billing.PlanCatalog
import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.Period
import com.mcclabs.mook.domain.billing.PlanPackage
import com.mcclabs.mook.domain.billing.PaywallOffer
import com.mcclabs.mook.domain.billing.PriceChangeCheckResult
import com.mcclabs.mook.domain.billing.PriceChangeConfirmationUseCase
import com.mcclabs.mook.domain.billing.PurchasePriceSnapshot
import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.domain.billing.Tier
import com.mcclabs.mook.domain.billing.UnknownReason
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Gereksinim 2.3: [PriceChangeConfirmationUseCase]'in gerçek bir bağımlılığı
 * ([SubscriptionRepository]) olduğundan (aksine [CharacterQuotaManagerTest]/
 * [FeatureGateTest]'teki saf domain sınıfları) MockK burada anlamlıdır — tıpkı
 * [TierDowngradeUseCaseTest]'te olduğu gibi.
 */
@DisplayName("PriceChangeConfirmationUseCase")
class PriceChangeConfirmationUseCaseTest {

    private val repository = mockk<SubscriptionRepository>()
    private val useCase = PriceChangeConfirmationUseCase(repository)

    private fun plan(
        productIdentifier: String,
        amountMicros: Long,
        currencyCode: String = "TRY",
        localizedPrice: String = "₺149,99",
    ) = PlanPackage(
        identifier = "\$rc_monthly",
        tier = Tier.PREMIUM,
        period = Period.MONTHLY,
        localizedPrice = localizedPrice,
        productIdentifier = productIdentifier,
        priceAmountMicros = amountMicros,
        priceCurrencyCode = currencyCode,
    )

    private fun entitlement(
        tier: Tier = Tier.PREMIUM,
        isResolved: Boolean = true,
        activeProductIdentifier: String? = "mook_premium:monthly-v1",
        lastKnownPurchasePrice: PurchasePriceSnapshot? = PurchasePriceSnapshot(
            productIdentifier = "mook_premium:monthly-v1",
            amountMicros = 100_000_000L,
            currencyCode = "TRY",
            formattedPrice = "₺100,00",
        ),
    ) = EntitlementState(
        tier = tier,
        isResolved = isResolved,
        limits = PlanCatalog.BUNDLED.limitsFor(tier),
        activeProductIdentifier = activeProductIdentifier,
        lastKnownPurchasePrice = lastKnownPurchasePrice,
    )

    @Nested
    @DisplayName("Karşılaştırma yapılamayan durumlar")
    inner class CannotCompare {

        @Test
        fun `free kademede karsilastirilacak abonelik yoktur`() = runTest {
            every { repository.state } returns MutableStateFlow(entitlement(tier = Tier.FREE))

            assertEquals(PriceChangeCheckResult.NoActiveSubscription, useCase())
        }

        @Test
        fun `henuz cozulmemis (isResolved=false) durumda karsilastirma yapilmaz`() = runTest {
            every { repository.state } returns MutableStateFlow(entitlement(isResolved = false))

            assertEquals(PriceChangeCheckResult.NoActiveSubscription, useCase())
        }

        @Test
        fun `canli urun kimligi henuz bilinmiyorsa ACTIVE_PRODUCT_NOT_RESOLVED doner`() = runTest {
            every { repository.state } returns MutableStateFlow(entitlement(activeProductIdentifier = null))

            val result = assertIs<PriceChangeCheckResult.Unknown>(useCase())
            assertEquals(UnknownReason.ACTIVE_PRODUCT_NOT_RESOLVED, result.reason)
        }

        @Test
        fun `yerel satin alma gecmisi yoksa NO_LOCAL_PURCHASE_HISTORY doner`() = runTest {
            every { repository.state } returns MutableStateFlow(entitlement(lastKnownPurchasePrice = null))

            val result = assertIs<PriceChangeCheckResult.Unknown>(useCase())
            assertEquals(UnknownReason.NO_LOCAL_PURCHASE_HISTORY, result.reason)
        }

        @Test
        fun `kayitli fiyat farkli bir urune aitse NO_LOCAL_PURCHASE_HISTORY doner`() = runTest {
            // Kullanıcı en son satın almadan sonra FARKLI bir plana geçmiş olabilir —
            // eski fiyat bu ürün için anlamlı bir referans DEĞİLDİR.
            every { repository.state } returns MutableStateFlow(
                entitlement(
                    activeProductIdentifier = "mook_premium:yearly-v1",
                    lastKnownPurchasePrice = PurchasePriceSnapshot(
                        "mook_premium:monthly-v1", 100_000_000L, "TRY", "₺100,00",
                    ),
                ),
            )

            val result = assertIs<PriceChangeCheckResult.Unknown>(useCase())
            assertEquals(UnknownReason.NO_LOCAL_PURCHASE_HISTORY, result.reason)
        }

        @Test
        fun `teklifler okunamiyorsa OFFERINGS_UNAVAILABLE doner`() = runTest {
            every { repository.state } returns MutableStateFlow(entitlement())
            coEvery { repository.offerings() } returns Result.failure(IllegalStateException("network"))

            val result = assertIs<PriceChangeCheckResult.Unknown>(useCase())
            assertEquals(UnknownReason.OFFERINGS_UNAVAILABLE, result.reason)
        }

        @Test
        fun `urun guncel tekliflerde yoksa OFFER_NOT_FOUND doner`() = runTest {
            every { repository.state } returns MutableStateFlow(entitlement())
            coEvery { repository.offerings() } returns Result.success(
                PaywallOffer(listOf(plan("mook_premium:yearly-v1", 900_000_000L))),
            )

            val result = assertIs<PriceChangeCheckResult.Unknown>(useCase())
            assertEquals(UnknownReason.OFFER_NOT_FOUND, result.reason)
        }

        @Test
        fun `para birimi degistiyse CURRENCY_CHANGED doner`() = runTest {
            every { repository.state } returns MutableStateFlow(entitlement())
            coEvery { repository.offerings() } returns Result.success(
                PaywallOffer(listOf(plan("mook_premium:monthly-v1", 500_000_000L, currencyCode = "USD"))),
            )

            val result = assertIs<PriceChangeCheckResult.Unknown>(useCase())
            assertEquals(UnknownReason.CURRENCY_CHANGED, result.reason)
        }
    }

    @Nested
    @DisplayName("Gecerli karsilastirma")
    inner class ValidComparison {

        @Test
        fun `fiyat arttiysa Increased doner ve yerellestirilmis metinleri tasir`() = runTest {
            every { repository.state } returns MutableStateFlow(entitlement())
            coEvery { repository.offerings() } returns Result.success(
                PaywallOffer(
                    listOf(plan("mook_premium:monthly-v1", 150_000_000L, localizedPrice = "₺150,00")),
                ),
            )

            val result = assertIs<PriceChangeCheckResult.Increased>(useCase())
            assertEquals("mook_premium:monthly-v1", result.productIdentifier)
            assertEquals("₺100,00", result.previousLocalizedPrice)
            assertEquals("₺150,00", result.currentLocalizedPrice)
        }

        @Test
        fun `fiyat ayniysa NoPriceChange doner`() = runTest {
            every { repository.state } returns MutableStateFlow(entitlement())
            coEvery { repository.offerings() } returns Result.success(
                PaywallOffer(listOf(plan("mook_premium:monthly-v1", 100_000_000L))),
            )

            assertEquals(PriceChangeCheckResult.NoPriceChange, useCase())
        }

        @Test
        fun `fiyat dustuyse de Increased degil NoPriceChange doner`() = runTest {
            // Bir fiyat DÜŞÜŞÜ, Google'ın onay gerektiren senaryosu DEĞİLDİR — kullanıcıya
            // yanlışlıkla bir "artış" uyarısı gösterilmemelidir.
            every { repository.state } returns MutableStateFlow(entitlement())
            coEvery { repository.offerings() } returns Result.success(
                PaywallOffer(listOf(plan("mook_premium:monthly-v1", 80_000_000L))),
            )

            assertEquals(PriceChangeCheckResult.NoPriceChange, useCase())
        }
    }
}
