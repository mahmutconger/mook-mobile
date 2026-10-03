package com.mcclabs.mook.auth

import com.mcclabs.mook.domain.auth.LogoutUseCase
import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.domain.repository.AuthRepository
import com.mcclabs.mook.domain.repository.PushTokenRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import com.mcclabs.mook.util.Log
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * "Katı Çıkış Sırası" testleri: FCM (PushTokenRepository), RevenueCat (SubscriptionRepository)
 * ve kimlik sağlayıcısı (AuthRepository) taklit edilerek çağrı SIRASI ve hata toleransı
 * doğrulanır.
 */
@DisplayName("LogoutUseCase")
class LogoutUseCaseTest {

    private val pushTokens = mockk<PushTokenRepository>(relaxUnitFun = true)
    private val subscriptions = mockk<SubscriptionRepository>(relaxUnitFun = true)
    private val auth = mockk<AuthRepository>(relaxUnitFun = true)
    private val useCase = LogoutUseCase(pushTokens, subscriptions, auth, stepTimeoutMillis = 5_000)

    // `android.util.Log` JVM host testlerinde gerçek bir uygulamaya sahip değildir; hata
    // yollarındaki loglama testi kırmasın diye ortak `Log` nesnesi taklit edilir.
    @BeforeEach
    fun muteLogs() {
        mockkObject(Log)
        every { Log.e(any(), any()) } just runs
    }

    @AfterEach
    fun restoreLogs() = unmockkObject(Log)

    @Test
    fun `adimlar kesin sirayla calisir - FCM jetonu, RevenueCat, signOut`() = runTest {
        val result = useCase()

        coVerifyOrder {
            pushTokens.unregisterCurrentDevice()
            subscriptions.logOut()
            auth.logout()
        }
        assertTrue(result.pushTokenRemoved)
        assertTrue(result.billingIdentityDetached)
    }

    @Test
    fun `FCM jetonu silinemezse cikis yine de tamamlanir`() = runTest {
        coEvery { pushTokens.unregisterCurrentDevice() } throws IllegalStateException("Firestore erişilemiyor")

        val result = useCase()

        coVerifyOrder {
            pushTokens.unregisterCurrentDevice()
            subscriptions.logOut()
            auth.logout()
        }
        assertFalse(result.pushTokenRemoved)
        assertTrue(result.billingIdentityDetached)
    }

    @Test
    fun `cevrimdisi FCM silme sonsuza dek beklerse zaman asimindan sonra devam edilir`() = runTest {
        // Çevrimdışı Firestore yazması sunucu onayını hiç almayabilir.
        coEvery { pushTokens.unregisterCurrentDevice() } coAnswers { awaitCancellation() }

        val result = useCase()

        assertFalse(result.pushTokenRemoved)
        coVerify(exactly = 1) { subscriptions.logOut() }
        coVerify(exactly = 1) { auth.logout() }
    }

    @Test
    fun `RevenueCat cikisi basarisiz olsa da signOut her zaman calisir`() = runTest {
        coEvery { subscriptions.logOut() } throws IllegalStateException("RevenueCat çıkışı başarısız")

        val result = useCase()

        assertTrue(result.pushTokenRemoved)
        assertFalse(result.billingIdentityDetached)
        coVerify(exactly = 1) { auth.logout() }
    }

    @Test
    fun `signOut FCM jetonu silinmeden once asla cagrilmaz`() = runTest {
        var tokenRemovedBeforeSignOut = false
        var tokenRemoved = false
        coEvery { pushTokens.unregisterCurrentDevice() } coAnswers { tokenRemoved = true }
        coEvery { auth.logout() } coAnswers { tokenRemovedBeforeSignOut = tokenRemoved }

        useCase()

        assertTrue(tokenRemovedBeforeSignOut)
    }
}
