package com.mcclabs.mook.billing

import com.mcclabs.mook.domain.analytics.AnalyticsRepository
import com.mcclabs.mook.domain.billing.PlanCatalog
import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.PaywallOffer
import com.mcclabs.mook.domain.billing.Period
import com.mcclabs.mook.domain.billing.PlanPackage
import com.mcclabs.mook.domain.billing.RestoreSubscriptionUseCase
import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.domain.billing.Tier
import com.mcclabs.mook.domain.billing.TrialOfferUseCase
import com.mcclabs.mook.domain.connectivity.ConnectivityObserver
import com.mcclabs.mook.feature.paywall.PaywallLoadState
import com.mcclabs.mook.feature.paywall.PaywallViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** Limit sayfasındaki deneme teklifi uygunluğu ve Paywall'ın yükleme/hata/yeniden deneme akışı. */
class TrialOfferAndPaywallLoadTest {

    private val subscriptions = mockk<SubscriptionRepository>(relaxed = true)
    private val state = MutableStateFlow(free())

    private fun free() = EntitlementState(tier = Tier.FREE, isResolved = true, limits = PlanCatalog.BUNDLED.limitsFor(Tier.FREE))

    private fun standardTrial(days: Int? = 3) = PlanPackage(
        identifier = "\$rc_monthly_standard", tier = Tier.STANDARD, period = Period.MONTHLY, localizedPrice = "₺1",
        hasFreeTrialAvailable = true, freeTrialDays = days, productIdentifier = "mook_standard:monthly",
    )

    @BeforeEach
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher())
        every { subscriptions.state } returns state
        coEvery { subscriptions.introEligibility(any()) } returns mapOf("mook_standard:monthly" to true)
    }

    @AfterEach
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    @DisplayName("Ücretsiz ve uygun kullanıcıya Standart deneme teklifi gösterilir (süre mağazadan)")
    fun freeEligibleUserGetsTrial() = runTest {
        coEvery { subscriptions.offerings() } returns Result.success(PaywallOffer(listOf(standardTrial(days = 7))))
        assertEquals(7, TrialOfferUseCase(subscriptions)()?.trialDays)
    }

    @Test
    @DisplayName("Ücretli abone, uygunsuz kullanıcı veya ağ hatasında deneme teklifi gösterilmez")
    fun trialHiddenWhenNotEligible() = runTest {
        coEvery { subscriptions.offerings() } returns Result.success(PaywallOffer(listOf(standardTrial())))
        coEvery { subscriptions.introEligibility(any()) } returns mapOf("mook_standard:monthly" to false)
        assertNull(TrialOfferUseCase(subscriptions)())

        coEvery { subscriptions.introEligibility(any()) } returns mapOf("mook_standard:monthly" to true)
        state.value = EntitlementState(tier = Tier.ECONOMY, isResolved = true)
        assertNull(TrialOfferUseCase(subscriptions)())

        state.value = free()
        coEvery { subscriptions.offerings() } returns Result.failure(IllegalStateException("ağ"))
        assertNull(TrialOfferUseCase(subscriptions)())
    }

    @Test
    @DisplayName("Paketler yüklenemezse Hata durumu; Tekrar Dene başarılı olunca Hazır")
    fun paywallRetryRecoversFromError() {
        val connectivity = mockk<ConnectivityObserver>()
        every { connectivity.isOnline } returns MutableStateFlow(true)
        coEvery { subscriptions.offerings() } returnsMany listOf(
            Result.failure(IllegalStateException("Plans are not available yet.")),
            Result.success(PaywallOffer(listOf(standardTrial()))),
        )
        val viewModel = PaywallViewModel(
            subscriptions, connectivity, mockk<RestoreSubscriptionUseCase>(relaxed = true), mockk<AnalyticsRepository>(relaxed = true),
        )
        assertEquals(PaywallLoadState.Error(offline = false), viewModel.state.value.loadState)
        assertNull(viewModel.state.value.message)

        viewModel.load()
        assertEquals(PaywallLoadState.Ready, viewModel.state.value.loadState)
    }
}
