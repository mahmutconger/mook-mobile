package com.mcclabs.mook.consent

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.mcclabs.mook.ads.AdMobActivityHolder
import com.mcclabs.mook.ads.AdMobConsentManager
import com.mcclabs.mook.ads.onDiscoveryAdsVisible
import com.mcclabs.mook.domain.consent.ConsentPolicy
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * AdMob'un KVKK kararından önce başlatılmadığı ve reklam isteği göndermediği, gerçek
 * [AdMobConsentManager] ve gerçek geçiş reklamı yükleme yolu üzerinden doğrulanır.
 * Mobile Ads SDK'sı, UMP ve Firebase statik çağrıları taklit edilir; ağ isteği yapılmaz.
 *
 * [AdMobConsentManager] süreç boyunca yaşayan bir `object` olduğu için adımlar tek bir
 * testte, gerçek kullanıcı akışının sırasıyla çalıştırılır.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class AdMobConsentGateTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val consentInformation = mockk<ConsentInformation>(relaxed = true)
    private val requests = mutableListOf<AdRequest>()
    private val loadCallbacks = mutableListOf<InterstitialAdLoadCallback>()
    private lateinit var activity: Activity

    @Before
    fun setUp() {
        // Test reklam birimi yalnızca hata ayıklanabilir derlemede seçilir.
        context.applicationInfo.flags = context.applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        AdMobActivityHolder.activity = activity

        mockkStatic(FirebaseApp::class)
        val firebaseApp = mockk<FirebaseApp>()
        every { FirebaseApp.getInstance() } returns firebaseApp
        every { firebaseApp.applicationContext } returns context
        mockkStatic(FirebaseAnalytics::class)
        every { FirebaseAnalytics.getInstance(any()) } returns mockk(relaxed = true)

        mockkStatic(MobileAds::class)
        every { MobileAds.setRequestConfiguration(any()) } just Runs
        every { MobileAds.initialize(any(), any()) } just Runs
        mockkStatic(InterstitialAd::class)
        every { InterstitialAd.load(any(), any(), capture(requests), capture(loadCallbacks)) } just Runs

        // UMP: onay bilgisi hemen çözülür ve form gerekmez (Türkiye'deki tipik durum).
        mockkStatic(UserMessagingPlatform::class)
        every { UserMessagingPlatform.getConsentInformation(any()) } returns consentInformation
        every { consentInformation.requestConsentInfoUpdate(any(), any(), any(), any()) } answers {
            thirdArg<ConsentInformation.OnConsentInfoUpdateSuccessListener>().onConsentInfoUpdateSuccess()
        }
        every { UserMessagingPlatform.loadAndShowConsentFormIfRequired(any(), any()) } answers {
            secondArg<ConsentForm.OnConsentFormDismissedListener>().onConsentFormDismissed(null)
        }
    }

    @After
    fun tearDown() {
        AdMobActivityHolder.activity = null
        unmockkAll()
    }

    private fun AdRequest.npaFlag(): String? = getNetworkExtrasBundle(AdMobAdapter::class.java)?.getString("npa")

    /** Bekleyen yüklemeyi başarısız sonuçlandırır; denetleyici yeni bir isteğe hazır olur. */
    private fun finishPendingLoad() = loadCallbacks.last().onAdFailedToLoad(mockk(relaxed = true))

    @Test
    fun adsStartOnlyAfterTheKvkkDecisionAndFollowItsChoices() {
        // 1) Yeni kurulum: UMP izin veriyor ama KVKK kararı yok.
        every { consentInformation.canRequestAds() } returns true
        AdMobConsentManager.requestConsent(activity)

        assertTrue(AdMobConsentManager.state.value.isResolved)
        assertNull(AdMobConsentManager.state.value.decisionPolicyVersion)
        assertFalse(AdMobConsentManager.canRequestAds)
        assertFalse(AdMobConsentManager.state.value.adsAllowed)
        onDiscoveryAdsVisible(isFree = true)
        verify(exactly = 0) { MobileAds.initialize(any(), any()) }
        verify(exactly = 0) { MobileAds.setRequestConfiguration(any()) }
        assertTrue("Karar verilmeden reklam isteği gönderilmemeli", requests.isEmpty())

        // 2) "Tümünü reddet": reklam başlar ama istekler kişiselleştirilmemiş (NPA) gider.
        AdMobConsentManager.recordDecision(crossBorderTransferAccepted = false, personalizedAdsAllowed = false)

        assertEquals(ConsentPolicy.CURRENT_VERSION, AdMobConsentManager.state.value.decisionPolicyVersion)
        assertTrue(AdMobConsentManager.canRequestAds)
        assertTrue(AdMobConsentManager.state.value.adsAllowed)
        verify(exactly = 1) { MobileAds.initialize(any(), any()) }
        assertEquals("1", AdMobConsentManager.npaRequestExtras().getString("npa"))

        // Reklamsız (ücretli) planda kapı açık olsa bile reklam istenmez.
        onDiscoveryAdsVisible(isFree = false)
        assertTrue("Ücretli planda reklam isteği gönderilmemeli", requests.isEmpty())

        onDiscoveryAdsVisible(isFree = true)
        assertEquals(1, requests.size)
        assertEquals("1", requests.last().npaFlag())
        finishPendingLoad()

        // 3) "Tümünü kabul et": SDK yeniden başlatılmaz, istekler kişiselleştirilmiş gider.
        AdMobConsentManager.recordDecision(crossBorderTransferAccepted = true, personalizedAdsAllowed = true)

        assertTrue(AdMobConsentManager.canRequestAds)
        verify(exactly = 1) { MobileAds.initialize(any(), any()) }
        assertNull(AdMobConsentManager.npaRequestExtras().getString("npa"))
        onDiscoveryAdsVisible(isFree = true)
        assertEquals(2, requests.size)
        assertNull(requests.last().npaFlag())
        finishPendingLoad()

        // 4) UMP izni geri çekilirse KVKK kararı tek başına reklamı açık tutamaz.
        every { consentInformation.canRequestAds() } returns false
        AdMobConsentManager.requestConsent(activity)

        assertFalse(AdMobConsentManager.canRequestAds)
        assertFalse(AdMobConsentManager.state.value.adsAllowed)
        onDiscoveryAdsVisible(isFree = true)
        assertEquals(2, requests.size)
    }
}
