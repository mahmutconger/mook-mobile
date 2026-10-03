package com.mcclabs.mook.billing

import com.mcclabs.mook.domain.billing.SsvConfirmationPolicy
import com.mcclabs.mook.domain.billing.SsvConfirmationResult
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Gereksinim 1.4: AdMob SSV zaman aşımı mantığının testleri.
 *
 * [SsvConfirmationPolicy] platformdan (AdMob SDK'sı) bağımsız, saf bir bekleme
 * politikası olduğu için bu testler `kotlinx-coroutines-test`'in sanal zamanını
 * kullanır — gerçek saniyeler beklemeden 10 saniyelik bir zaman aşımını anında
 * doğrular.
 */
class SsvConfirmationPolicyTest {

    private val policy = SsvConfirmationPolicy(timeoutMillis = 5_000L, pollIntervalMillis = 1_000L)

    @Test
    fun `odul ilk kontrolde dogrulanirsa hemen Confirmed doner`() = runTest {
        var calls = 0
        val result = policy.await {
            calls++
            true
        }

        assertEquals(SsvConfirmationResult.Confirmed, result)
        assertEquals(1, calls)
    }

    @Test
    fun `odul birkac yoklamadan sonra dogrulanirsa Confirmed doner`() = runTest {
        var calls = 0
        val result = policy.await {
            calls++
            calls >= 3
        }

        assertEquals(SsvConfirmationResult.Confirmed, result)
        assertEquals(3, calls)
    }

    @Test
    fun `odul hic dogrulanmazsa zaman asimi sonunda TimedOut doner`() = runTest {
        var calls = 0
        val result = policy.await {
            calls++
            false
        }

        assertEquals(SsvConfirmationResult.TimedOut, result)
        // 5 saniyelik zaman aşımı, 1 saniyelik yoklama aralığıyla en fazla ~5-6 çağrı
        // üretebilir — sonsuz bir döngü veya çağrının hiç durmadığı bir kaçak olmadığını
        // doğrular.
        assertTrue(calls in 1..6, "beklenmeyen çağrı sayısı: $calls")
    }

    @Test
    fun `zaman asimindan sonra checkRewardGranted bir daha cagrilmaz`() = runTest {
        var calls = 0
        policy.await {
            calls++
            false
        }
        val callsAtTimeout = calls

        // `withTimeoutOrNull` iç coroutine'i zaman aşımında iptal eder. Sanal saati
        // daha da ileri sararak (bu testteki 5 saniyelik zaman aşımının iki katı kadar)
        // hâlâ arkada çalışan, iptal edilmemiş bir görev kalıp kalmadığını
        // doğruluyoruz — kalsaydı `calls` burada artardı.
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(callsAtTimeout, calls)
    }

    @Test
    fun `varsayilan yapilandirma tam 10 saniye sonra zaman asimina ugrar`() = runTest {
        // Gereksinim 1.4: "10 saniyelik bir zaman aşımı" — `TestScope.currentTime`,
        // gerçek saniyeler beklemeden sanal saati okur, bu yüzden varsayılan kurucu
        // parametresi `BillingConfig.REWARDED_SSV_TIMEOUT_MILLIS`'ten (10.000ms) sapsa
        // bu test onu yakalar.
        val defaultPolicy = SsvConfirmationPolicy()

        val result = defaultPolicy.await { false }

        assertIs<SsvConfirmationResult.TimedOut>(result)
        assertEquals(10_000L, currentTime)
    }
}
