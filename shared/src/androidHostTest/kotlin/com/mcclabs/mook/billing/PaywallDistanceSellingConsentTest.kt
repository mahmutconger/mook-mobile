package com.mcclabs.mook.billing

import com.mcclabs.mook.domain.analytics.AnalyticsRepository
import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.Period
import com.mcclabs.mook.domain.billing.PlanPackage
import com.mcclabs.mook.domain.billing.PurchaseOutcome
import com.mcclabs.mook.domain.billing.RestoreSubscriptionUseCase
import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.domain.billing.Tier
import com.mcclabs.mook.domain.connectivity.ConnectivityObserver
import com.mcclabs.mook.feature.paywall.PaywallViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Gereksinim 6 (Faz 6, Tüketici Hukuku Ön Bilgilendirme Uyumluluğu): [PaywallViewModel]'deki
 * Mesafeli Sözleşmeler Yönetmeliği Cayma Hakkı İstisnası onay kutusu durum mantığını doğrular --
 * onay kutusu varsayılan olarak İŞARETSİZDİR ve [PaywallViewModel.purchase] onaylanmadan HİÇBİR
 * satın alma isteğini SDK'ya iletmez (arayüzdeki düğmenin devre dışı bırakılmasından BAĞIMSIZ
 * bir ikinci zorlama katmanı).
 */
class PaywallDistanceSellingConsentTest {

    private val subscriptions = mockk<SubscriptionRepository>(relaxed = true)
    private val connectivityObserver = mockk<ConnectivityObserver>()
    private val restoreSubscriptionUseCase = mockk<RestoreSubscriptionUseCase>(relaxed = true)

    private val plan = PlanPackage(
        identifier = "\$rc_monthly",
        tier = Tier.PREMIUM,
        period = Period.MONTHLY,
        localizedPrice = "₺99,99",
    )

    @BeforeEach
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher())
        every { subscriptions.state } returns MutableStateFlow(EntitlementState())
        every { connectivityObserver.isOnline } returns MutableStateFlow(true)
        // PaywallViewModel.init çağırdığı load() burada test dışı bir kaygı -- teklif listesini
        // sonuçsuz (failure) bırakmak, relaxed mock'un PaywallOffer için anlamsız bir vekil (proxy)
        // üretip ClassCastException fırlatmasını önler.
        coEvery { subscriptions.offerings() } returns Result.failure(Exception("test: offerings not stubbed"))
        coEvery { subscriptions.refresh() } returns Unit
    }

    @AfterEach
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    private fun createViewModel() =
        PaywallViewModel(subscriptions, connectivityObserver, restoreSubscriptionUseCase, mockk<AnalyticsRepository>(relaxed = true))

    @Test
    @DisplayName("Onay kutusu varsayılan olarak işaretsizdir")
    fun startsUnaccepted() = runTest {
        val viewModel = createViewModel()

        assertFalse(viewModel.state.value.distanceSellingContractAccepted)
    }

    @Test
    @DisplayName("setDistanceSellingContractAccepted durumu günceller")
    fun togglesAcceptedState() = runTest {
        val viewModel = createViewModel()

        viewModel.setDistanceSellingContractAccepted(true)
        assertTrue(viewModel.state.value.distanceSellingContractAccepted)

        viewModel.setDistanceSellingContractAccepted(false)
        assertFalse(viewModel.state.value.distanceSellingContractAccepted)
    }

    @Test
    @DisplayName("onaylanmadan purchase() satın alma isteğini SDK'ya HİÇ iletmez")
    fun purchaseBlockedWithoutConsent() = runTest {
        val viewModel = createViewModel()

        viewModel.purchase(plan)

        coVerify(exactly = 0) { subscriptions.purchase(any()) }
    }

    @Test
    @DisplayName("onaylandıktan sonra purchase() satın alma isteğini SDK'ya iletir")
    fun purchaseProceedsAfterConsent() = runTest {
        // Gereksinim 6: burada yalnızca onay kapısının satın alma isteğini ENGELLEMEDİĞİNİ
        // doğrularız. Çağrıyı BİLEREK hiç tamamlanmayan (`awaitCancellation`) bırakıyoruz --
        // sonuç dallarının (başarı/iptal/bekliyor) her biri bir sonraki adımda gerçek Android
        // `Resources`e ihtiyaç duyan Compose Resources `getString`'i çağırır; bu da
        // Robolectric OLMAYAN saf bir JVM birim testinde mevcut değildir ve testin ASIL
        // iddiasıyla (SDK çağrısının GERÇEKTEN yapıldığı) ilgisizdir.
        coEvery { subscriptions.purchase(plan) } coAnswers { awaitCancellation() }
        val viewModel = createViewModel()

        viewModel.setDistanceSellingContractAccepted(true)
        viewModel.purchase(plan)

        coVerify(exactly = 1) { subscriptions.purchase(plan) }
    }
}
