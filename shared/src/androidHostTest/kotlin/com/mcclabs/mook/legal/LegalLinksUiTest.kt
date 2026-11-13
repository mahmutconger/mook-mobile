package com.mcclabs.mook.legal

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mcclabs.mook.domain.legal.LegalLinks
import com.mcclabs.mook.domain.legal.LegalLinksResolver
import com.mcclabs.mook.domain.theme.ThemePreference
import com.mcclabs.mook.feature.paywall.PaywallLegalLinks
import com.mcclabs.mook.feature.registration.RegistrationLegalLinks
import com.mcclabs.mook.feature.settings.SettingsLegalLinks
import com.mcclabs.mook.ui.legal.LocalLegalLinks
import com.mcclabs.mook.ui.theme.AppTheme
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.legal_child_safety
import mook.shared.generated.resources.legal_privacy_policy
import mook.shared.generated.resources.legal_terms_of_use
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * Ayarlar, Paywall ve Kayıt ekranlarındaki yasal bağlantıların, sağlanan [LocalLegalLinks]
 * değerine göre doğru adresi açtığını doğrular. URI açıcı sahte bir [UriHandler] ile yakalanır.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class LegalLinksUiTest {
    @get:Rule val compose = createComposeRule()

    private val opened = mutableListOf<String>()
    private val fakeUriHandler = object : UriHandler {
        override fun openUri(uri: String) { opened += uri }
    }

    // Beklenen etiketler ekranın kullandığı kaynaklardan okunur.
    private lateinit var privacyLabel: String
    private lateinit var termsLabel: String
    private lateinit var childSafetyLabel: String

    private fun show(links: LegalLinks, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            privacyLabel = stringResource(Res.string.legal_privacy_policy)
            termsLabel = stringResource(Res.string.legal_terms_of_use)
            childSafetyLabel = stringResource(Res.string.legal_child_safety)
            CompositionLocalProvider(LocalUriHandler provides fakeUriHandler, LocalLegalLinks provides links) {
                // Ekranlarda olduğu gibi dikey bir sütun içinde gösterilir.
                AppTheme(ThemePreference.Light, systemDark = false) { Column { content() } }
            }
        }
        compose.waitForIdle()
    }

    private fun clickAndCapture(label: String): String {
        opened.clear()
        compose.onNodeWithText(label).assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals("'$label' tek bir adres açmalı", 1, opened.size)
        return opened.single()
    }

    private fun assertSettingsOpens(links: LegalLinks) {
        show(links) { SettingsLegalLinks() }
        assertEquals(links.privacyPolicyUrl, clickAndCapture(privacyLabel))
        assertEquals(links.termsOfUseUrl, clickAndCapture(termsLabel))
        assertEquals(links.childSafetyUrl, clickAndCapture(childSafetyLabel))
    }

    @Test fun settingsLegalSectionOpensTurkishPages() {
        assertSettingsOpens(LegalLinksResolver.forLanguage("tr"))
    }

    @Test fun settingsLegalSectionOpensEnglishPages() {
        assertSettingsOpens(LegalLinksResolver.forLanguage("en"))
    }

    @Test fun settingsLegalSectionShowsThreeSeparateRows() {
        show(LegalLinksResolver.forLanguage("en")) { SettingsLegalLinks() }
        listOf(privacyLabel, termsLabel, childSafetyLabel).forEach {
            compose.onAllNodesWithText(it).assertCountEquals(1)
        }
    }

    private fun assertPaywallOpens(links: LegalLinks) {
        show(links) { PaywallLegalLinks() }
        // Tek bir "Koşullar · Gizlilik" düğmesi yerine iki ayrı bağlantı olmalı.
        compose.onAllNodesWithText(termsLabel).assertCountEquals(1)
        compose.onAllNodesWithText(privacyLabel).assertCountEquals(1)
        assertEquals(links.termsOfUseUrl, clickAndCapture(termsLabel))
        assertEquals(links.privacyPolicyUrl, clickAndCapture(privacyLabel))
    }

    @Test fun paywallLegalLinksOpenTurkishPages() = assertPaywallOpens(LegalLinksResolver.forLanguage("tr"))

    @Test fun paywallLegalLinksOpenEnglishPages() = assertPaywallOpens(LegalLinksResolver.forLanguage("en"))

    private fun assertRegistrationOpens(links: LegalLinks) {
        show(links) { RegistrationLegalLinks() }
        compose.onAllNodesWithText(privacyLabel).assertCountEquals(1)
        compose.onAllNodesWithText(termsLabel).assertCountEquals(1)
        assertEquals(links.privacyPolicyUrl, clickAndCapture(privacyLabel))
        assertEquals(links.termsOfUseUrl, clickAndCapture(termsLabel))
    }

    @Test fun registrationLegalLinksOpenTurkishPages() = assertRegistrationOpens(LegalLinksResolver.forLanguage("tr"))

    @Test fun registrationLegalLinksOpenEnglishPages() = assertRegistrationOpens(LegalLinksResolver.forLanguage("en"))
}
