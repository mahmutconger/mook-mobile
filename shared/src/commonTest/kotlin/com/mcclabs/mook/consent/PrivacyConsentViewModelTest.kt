package com.mcclabs.mook.consent

import com.mcclabs.mook.domain.consent.ConsentPolicy
import com.mcclabs.mook.domain.consent.ConsentRepository
import com.mcclabs.mook.domain.consent.ConsentState
import com.mcclabs.mook.domain.consent.requiresDecision
import com.mcclabs.mook.feature.consent.ConsentChoices
import com.mcclabs.mook.feature.consent.PrivacyConsentViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** KVKK onay akışı: tek seferlik gösterim, politika sürümü, kayıt ve Ayarlar'dan düzenleme. */
@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyConsentViewModelTest {

    /** Kalıcılığı taklit eden sahte depo; son kaydedilen kararı da tutar. */
    private class FakeConsentRepository(initial: ConsentState) : ConsentRepository {
        private val mutable = MutableStateFlow(initial)
        override val state: StateFlow<ConsentState> = mutable
        var recorded: Pair<Boolean, Boolean>? = null
        override fun refreshConsent() = Unit
        override fun setCrossBorderTransferAccepted(accepted: Boolean) = mutable.update { it.copy(crossBorderTransferAccepted = accepted) }
        override fun setPersonalizedAdsAllowed(accepted: Boolean) = mutable.update { it.copy(personalizedAdsAllowed = accepted) }
        override fun showPrivacyOptionsForm() = Unit
        override fun recordDecision(crossBorderTransferAccepted: Boolean, personalizedAdsAllowed: Boolean) {
            recorded = crossBorderTransferAccepted to personalizedAdsAllowed
            mutable.update {
                it.copy(
                    crossBorderTransferAccepted = crossBorderTransferAccepted,
                    personalizedAdsAllowed = personalizedAdsAllowed,
                    decisionPolicyVersion = ConsentPolicy.CURRENT_VERSION,
                )
            }
        }
    }

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val freshInstall = ConsentState(isResolved = true)

    @Test
    fun karariOlmayanKullaniciyaEkranGosterilirVeIzinlerOncedenIsaretliDegildir() {
        val viewModel = PrivacyConsentViewModel(FakeConsentRepository(freshInstall))
        assertTrue(viewModel.state.value.requiresDecision)
        assertEquals(ConsentChoices(crossBorderTransfer = false, personalizedAds = false), viewModel.state.value.choices)
    }

    @Test
    fun onayAltyapisiCozulmedenEkranGosterilmez() {
        val viewModel = PrivacyConsentViewModel(FakeConsentRepository(ConsentState(isResolved = false)))
        assertFalse(viewModel.state.value.requiresDecision)
    }

    @Test
    fun tumunuKabulEtHerIkiIzniKaydederVeEkranBirDahaGosterilmez() {
        val repository = FakeConsentRepository(freshInstall)
        val viewModel = PrivacyConsentViewModel(repository)
        viewModel.acceptAll()
        assertEquals(true to true, repository.recorded)
        assertFalse(viewModel.state.value.requiresDecision)
    }

    @Test
    fun tumunuReddetHerIkiIzniKapaliKaydederVeEkraniKapatir() {
        val repository = FakeConsentRepository(freshInstall)
        val viewModel = PrivacyConsentViewModel(repository)
        viewModel.rejectAll()
        assertEquals(false to false, repository.recorded)
        assertFalse(viewModel.state.value.requiresDecision)
    }

    @Test
    fun ayriAyriSecimlerOldugeGibiKaydedilir() {
        val repository = FakeConsentRepository(freshInstall)
        val viewModel = PrivacyConsentViewModel(repository)
        viewModel.onCrossBorderTransferToggled(true)
        viewModel.saveChoices()
        assertEquals(true to false, repository.recorded)
    }

    @Test
    fun politikaSurumuArtinciEkranYenidenGosterilir() {
        val oldDecision = freshInstall.copy(decisionPolicyVersion = ConsentPolicy.CURRENT_VERSION - 1)
        assertTrue(oldDecision.requiresDecision)
        val current = freshInstall.copy(decisionPolicyVersion = ConsentPolicy.CURRENT_VERSION)
        assertFalse(current.requiresDecision)
    }

    @Test
    fun ayarlardanAcilinciKayitliKararlaDoldurulurVeKapatilabilir() {
        val saved = freshInstall.copy(
            crossBorderTransferAccepted = true,
            personalizedAdsAllowed = false,
            decisionPolicyVersion = ConsentPolicy.CURRENT_VERSION,
        )
        val viewModel = PrivacyConsentViewModel(FakeConsentRepository(saved))
        viewModel.openEditor()
        assertTrue(viewModel.state.value.isEditorOpen)
        assertEquals(ConsentChoices(crossBorderTransfer = true, personalizedAds = false), viewModel.state.value.choices)
        viewModel.dismissEditor()
        assertFalse(viewModel.state.value.isEditorOpen)
    }
}
