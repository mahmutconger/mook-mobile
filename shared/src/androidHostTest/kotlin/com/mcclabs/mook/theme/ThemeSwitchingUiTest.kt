package com.mcclabs.mook.theme

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import com.mcclabs.mook.domain.model.Language
import com.mcclabs.mook.domain.model.DiscoverProfile
import com.mcclabs.mook.feature.room.RoomPickerGrid
import com.mcclabs.mook.feature.discover.ProfileGridCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.ViewModelStore
import com.mcclabs.mook.data.local.LocalKeyValueStore
import com.mcclabs.mook.data.repository.ThemeRepositoryImpl
import com.mcclabs.mook.domain.theme.ThemePreference
import com.mcclabs.mook.feature.theme.ThemePicker
import com.mcclabs.mook.feature.theme.ThemeViewModel
import com.mcclabs.mook.ui.components.NeonPrimaryButton
import com.mcclabs.mook.ui.components.PremiumBadge
import com.mcclabs.mook.ui.theme.AppTheme
import com.mcclabs.mook.ui.theme.MookTheme
import com.mcclabs.mook.ui.theme.themePalette
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Gerçek Compose ağacında tema seçimi, metin renkleri ve korunan form durumu doğrulanır. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ThemeSwitchingUiTest {
    @get:Rule val compose = createComposeRule()
    private val viewModelStore = ViewModelStore()
    @After fun tearDown() { viewModelStore.clear() }

    @Test fun pickerChangesRootAndSharedComponentsWithoutLosingDraft() {
        val repository = ThemeRepositoryImpl(object : LocalKeyValueStore {
            private var stored: String? = "light"
            override suspend fun getString(key: String) = stored
            override suspend fun putString(key: String, value: String) { stored = value }
        })
        compose.setContent {
            val root = remember { ThemeViewModel(repository).also { viewModelStore.put("root", it) } }
            val settings = remember { ThemeViewModel(repository).also { viewModelStore.put("settings", it) } }
            MookTheme(viewModel = root) {
                val state by settings.uiState.collectAsState()
                var draft by remember { mutableStateOf("") }
                Column {
                    ThemePicker(state, settings::selectTheme)
                    Text("Örnek metin", modifier = Modifier.testTag("sampleText"))
                    NeonPrimaryButton("Devam et", {})
                    PremiumBadge()
                    TextField(draft, { draft = it }, modifier = Modifier.testTag("draft"))
                }
            }
        }
        compose.onNodeWithTag("draft").performTextInput("Yürüyüş taslağı")
        for (preference in listOf(ThemePreference.Dark, ThemePreference.Ocean, ThemePreference.System, ThemePreference.Light)) {
            compose.onNodeWithTag("themeOption${preference.name}").performClick().assertIsSelected()
            val palette = themePalette(preference, false)
            assertTextColor(compose.onNodeWithTag("sampleText"), palette.scheme.onSurface)
            assertTextColor(compose.onNodeWithText("Devam et"), palette.colors.onGradient)
            compose.onNodeWithTag("draft").assertTextContains("Yürüyüş taslağı")
        }
    }

    @Test fun systemAndCustomThemeFollowRuntimeSystemChanges() {
        val systemDark = mutableStateOf(false)
        val preference = mutableStateOf(ThemePreference.System)
        compose.setContent {
            AppTheme(preference.value, systemDark = systemDark.value) {
                Text("Sistem örneği", modifier = Modifier.testTag("systemText"))
            }
        }
        for (selected in listOf(ThemePreference.System, ThemePreference.Ocean, ThemePreference.Light, ThemePreference.Dark)) {
            for (dark in listOf(false, true)) {
                compose.runOnIdle { preference.value = selected; systemDark.value = dark }
                assertTextColor(compose.onNodeWithTag("systemText"), themePalette(selected, dark).scheme.onSurface)
            }
        }
    }

    @Test fun nestedThemeDoesNotOverwriteSiblingPalette() {
        compose.setContent {
            AppTheme(ThemePreference.Dark, systemDark = false) {
                Column {
                    Text("Önce", modifier = Modifier.testTag("before"))
                    AppTheme(ThemePreference.Light, systemDark = false) {
                        Text("İçeride", modifier = Modifier.testTag("inside"))
                    }
                    Text("Sonra", modifier = Modifier.testTag("after"))
                }
            }
        }
        val dark = themePalette(ThemePreference.Dark, false).scheme.onSurface
        assertTextColor(compose.onNodeWithTag("before"), dark)
        assertTextColor(compose.onNodeWithTag("inside"), themePalette(ThemePreference.Light, false).scheme.onSurface)
        assertTextColor(compose.onNodeWithTag("after"), dark)
    }

    @Test fun gradientRoomAndPhotolessProfileKeepReadableContentInEveryTheme() {
        val preference = mutableStateOf(ThemePreference.Light)
        val systemDark = mutableStateOf(false)
        val language = Language("tr", "Türkçe odası", "🇹🇷")
        val profile = DiscoverProfile("sample", "Deniz", 27, null, null, emptyList(), "Yürüyüş sever", emptyList())
        compose.setContent {
            AppTheme(preference.value, systemDark = systemDark.value) {
                Column {
                    RoomPickerGrid(listOf(language), "tr", {}, Modifier.height(120.dp))
                    ProfileGridCard(profile, 0L, onClick = {}, onLike = {}, onPass = {}, onReport = {}, onBlock = {},
                        modifier = Modifier.width(200.dp))
                }
            }
        }
        for (selected in ThemePreference.entries) {
            for (dark in listOf(false, true)) {
                compose.runOnIdle { preference.value = selected; systemDark.value = dark }
                val expected = themePalette(selected, dark).colors.onGradient
                assertTextColor(compose.onNodeWithText("Türkçe odası", useUnmergedTree = true), expected)
                assertTextColor(compose.onNodeWithText("D", useUnmergedTree = true), expected)
            }
        }
    }

    private fun assertTextColor(node: SemanticsNodeInteraction, expected: Color) {
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertEquals("Metin etkin yüzeyin içerik rengini kullanmalı", expected, results.single().layoutInput.style.color)
    }
}
