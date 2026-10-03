package com.mcclabs.mook.billing

import com.mcclabs.mook.domain.billing.BoostManagerUseCase
import com.mcclabs.mook.domain.billing.BoostPhase
import com.mcclabs.mook.domain.billing.PlanCatalog
import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.domain.billing.Tier
import com.mcclabs.mook.domain.repository.BoostSummary
import com.mcclabs.mook.domain.repository.InteractionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Gereksinim 2.12 (Faz 4): [BoostManagerUseCase] — özellikle Boost kotasının TAKVİM AYI
 * değil, kullanıcının RevenueCat üzerinden okunan FATURALANDIRMA DÖNGÜSÜ ilkesine göre
 * ele alındığını kanıtlayan testler.
 *
 * Asıl faturalandırma-döngüsü sıfırlaması sunucudadır (`revenuecatWebhook.ts`teki
 * "RENEWAL"/"INITIAL_PURCHASE" olayı — bkz. o dosyanın KDoc'u); bu testler [SubscriptionRepository]
 * ve [InteractionRepository]'yi MockK ile sahteleyerek yalnızca [BoostManagerUseCase]'in bu
 * iki bağımlılığı DOĞRU sırayla/biçimde kullandığını ve saf ([BoostManagerUseCase.phaseFor])
 * mantığının doğru olduğunu doğrular.
 */
@DisplayName("BoostManagerUseCase")
class BoostManagerUseCaseTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val interactionRepository = mockk<InteractionRepository>()
    private val useCase = BoostManagerUseCase(subscriptionRepository, interactionRepository)

    @Nested
    @DisplayName("nextResetHintMillis — faturalandırma döngüsü, takvim ayı DEĞİL")
    inner class NextResetHint {

        @Test
        fun `RevenueCat yetkisinin bitis tarihini doner, herhangi bir takvim ayi hesabini degil`() {
            // Gereksinim 2.12: bu, testin kalbidir — sahte bir "ayın son günü" hesabı değil,
            // doğrudan EntitlementState.expiresAtMillis (RevenueCat'ten gelen) okunmalı.
            val billingCycleEndMillis = 1_800_000_000_123L
            every { subscriptionRepository.state } returns MutableStateFlow(
                EntitlementState(
                    tier = Tier.PREMIUM,
                    isResolved = true,
                    expiresAtMillis = billingCycleEndMillis,
                    limits = PlanCatalog.BUNDLED.limitsFor(Tier.PREMIUM),
                ),
            )

            assertEquals(billingCycleEndMillis, useCase.nextResetHintMillis())
        }

        @Test
        fun `abonelik yoksa (Free) null doner`() {
            every { subscriptionRepository.state } returns MutableStateFlow(
                EntitlementState(
                    tier = Tier.FREE,
                    isResolved = true,
                    expiresAtMillis = null,
                    limits = PlanCatalog.BUNDLED.limitsFor(Tier.FREE),
                ),
            )

            assertNull(useCase.nextResetHintMillis())
        }
    }

    @Nested
    @DisplayName("phaseFor — saf UI fazı hesaplaması")
    inner class PhaseFor {

        @Test
        fun `boostUntilMillis null ise Idle doner`() {
            assertEquals(BoostPhase.Idle, useCase.phaseFor(nowMillis = 1_000L, boostUntilMillis = null))
        }

        @Test
        fun `boostUntilMillis gelecekteyse kalan sureyle Active doner`() {
            val phase = useCase.phaseFor(nowMillis = 1_000L, boostUntilMillis = 1_000L + 90_000L)

            // Not: kotlin.test.assertIs<T>(...) Unit doner (deger DONDURMEZ, yalnizca
            // smart-cast icin daraltir); T doner ve degeri geri veren, JUnit 5 Jupiter'in
            // gercekten sahip oldugu API assertInstanceOf'tur -- burada KASITLI olarak o
            // kullanilir (bkz. diger bazı test dosyalarındaki `assertIs<T>(...)` -- ki bu
            // isim JUnit 5 Jupiter Assertions sınıfında gercekten YOKTUR ve derlenmeyebilir;
            // bu dosya o riski almaz).
            val active = assertInstanceOf(BoostPhase.Active::class.java, phase)
            assertEquals(90_000L, active.remainingMillis)
        }

        @Test
        fun `boostUntilMillis simdiye esit ya da gecmisteyse Idle doner`() {
            assertEquals(BoostPhase.Idle, useCase.phaseFor(nowMillis = 1_000L, boostUntilMillis = 1_000L))
            assertEquals(BoostPhase.Idle, useCase.phaseFor(nowMillis = 1_000L, boostUntilMillis = 999L))
        }
    }

    @Nested
    @DisplayName("activate / fetchCompletionSummary — sunucuya delege eder")
    inner class Delegation {

        @Test
        fun `activate InteractionRepository activateBoost cagrisini sarar`() = runTest {
            coEvery { interactionRepository.activateBoost() } returns Result.success(123_456L)

            val result = useCase.activate()

            assertEquals(Result.success(123_456L), result)
            coVerify(exactly = 1) { interactionRepository.activateBoost() }
        }

        @Test
        fun `fetchCompletionSummary InteractionRepository getBoostSummary cagrisini sarar`() = runTest {
            val summary = BoostSummary(viewsGained = 7, boostUntilMillis = null)
            coEvery { interactionRepository.getBoostSummary() } returns Result.success(summary)

            val result = useCase.fetchCompletionSummary()

            assertEquals(Result.success(summary), result)
            coVerify(exactly = 1) { interactionRepository.getBoostSummary() }
        }

        @Test
        fun `activate basarisiz olursa hatayi oldugu gibi yukari tasir`() = runTest {
            val failure = IllegalStateException("upgrade-required")
            coEvery { interactionRepository.activateBoost() } returns Result.failure(failure)

            assertEquals(Result.failure<Long>(failure), useCase.activate())
        }
    }
}
