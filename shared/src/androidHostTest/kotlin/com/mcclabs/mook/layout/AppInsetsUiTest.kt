package com.mcclabs.mook.layout

import android.app.Application
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.mcclabs.mook.domain.theme.ThemePreference
import com.mcclabs.mook.ui.components.BottomNavBar
import com.mcclabs.mook.ui.layout.AppScaffold
import com.mcclabs.mook.ui.layout.scaffoldPadding
import com.mcclabs.mook.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Gerçek yerleşim sınırları, inset tüketimi ve dokunulabilir öğeler birlikte doğrulanır. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
class AppInsetsUiTest {
    @get:Rule val compose = createComposeRule()
    private val routes = listOf("discover", "liked", "chats", "paywall", "profile")
    private val navigatedRoutes = mutableListOf<String>()
    private var actionCount = 0

    @Test fun gestureAndThreeButtonNavigationKeepBarsAndActionsInsideSafeArea() {
        val safeInsets = MutableWindowInsets(WindowInsets(top = 24.dp, bottom = 24.dp))
        compose.setContent { TestScreen(safeInsets) }
        for (bottom in listOf(24.dp, 48.dp)) {
            compose.runOnIdle { safeInsets.insets = WindowInsets(top = 24.dp, bottom = bottom) }
            val root = bounds("appScaffold")
            val safe = bounds("appContent")
            assertEquals("Durum çubuğu boşluğu bir kez ayrılmalı", root.top + pixels(24.dp.value), safe.top, 1f)
            assertEquals("Alt sistem boşluğu bir kez ayrılmalı", root.bottom - pixels(bottom.value), safe.bottom, 1f)
            assertScreenFits(safe)
            clickActionsAndTabs()
        }
        assertEquals(4, actionCount)
        assertEquals(routes + routes, navigatedRoutes)
    }

    @Test fun keyboardTransitionReservesMaximumInsetAndRestoresLayout() {
        val navigationInsets = WindowInsets(top = 24.dp, bottom = 48.dp)
        val safeInsets = MutableWindowInsets(navigationInsets)
        compose.setContent { TestScreen(safeInsets) }
        val original = bounds("appContent")
        compose.runOnIdle { safeInsets.insets = navigationInsets.union(WindowInsets(bottom = 180.dp)) }
        val root = bounds("appScaffold")
        val keyboardSafe = bounds("appContent")
        assertEquals("Klavye ve gezinme boşlukları toplanmamalı", root.bottom - pixels(180f), keyboardSafe.bottom, 1f)
        assertScreenFits(keyboardSafe)
        clickActionsAndTabs()
        compose.runOnIdle { safeInsets.insets = navigationInsets }
        assertEquals("Klavye kapanınca yerleşim eski sınırına dönmeli", original, bounds("appContent"))
        assertScreenFits(original)
    }

    @Test fun horizontalCutoutAndSideNavigationProtectEveryTab() {
        val safeInsets = WindowInsets(left = 32.dp, top = 24.dp, right = 48.dp)
        compose.setContent { TestScreen(safeInsets) }
        val root = bounds("appScaffold")
        val safe = bounds("appContent")
        assertEquals("Sol kesik boşluğu korunmalı", root.left + pixels(32f), safe.left, 1f)
        assertEquals("Sağ gezinme boşluğu korunmalı", root.right - pixels(48f), safe.right, 1f)
        assertScreenFits(safe)
        clickActionsAndTabs()
    }

    @Test fun standaloneBottomNavigationRespectsUnconsumedBottomAndHorizontalInsets() {
        compose.setContent {
            AppTheme(ThemePreference.Light, systemDark = false) {
                Box(Modifier.fillMaxSize().testTag("standaloneRoot")) {
                    BottomNavBar("discover", navigatedRoutes::add,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        windowInsets = WindowInsets(left = 16.dp, right = 16.dp, bottom = 48.dp))
                }
            }
        }
        val root = bounds("standaloneRoot")
        val safe = Rect(root.left + pixels(16f), root.top, root.right - pixels(16f), root.bottom - pixels(48f))
        for (route in routes) {
            assertInside("bottomNavItem$route", safe)
            compose.onNodeWithTag("bottomNavItem$route").assertIsDisplayed().assertHasClickAction().performClick()
        }
        assertEquals(routes, navigatedRoutes)
    }

    @Test fun consumedRootInsetsAreNotAddedAgainByLegacyModifiers() {
        val insets = WindowInsets(left = 16.dp, top = 24.dp, right = 16.dp, bottom = 48.dp)
        compose.setContent {
            AppTheme(ThemePreference.Light, systemDark = false) {
                AppScaffold(contentWindowInsets = insets) {
                    Box(Modifier.windowInsetsPadding(insets).fillMaxSize().testTag("legacyContent"))
                }
            }
        }
        assertEquals("Alt bileşenler kökte tüketilen boşluğu tekrar eklememeli", bounds("appContent"), bounds("legacyContent"))
    }

    @Test fun androidWindowInsetsDriveRootAndKeyboardWithoutSyntheticLayoutParameters() {
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            TestScreen()
        }
        for (keyboardHeight in listOf(0, 180, 0)) {
            compose.runOnIdle {
                val insets = WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, pixels(24f).toInt(), 0, 0))
                    .setVisible(WindowInsetsCompat.Type.statusBars(), true)
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, pixels(48f).toInt()))
                    .setVisible(WindowInsetsCompat.Type.navigationBars(), true)
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, pixels(keyboardHeight.toFloat()).toInt()))
                    .setVisible(WindowInsetsCompat.Type.ime(), keyboardHeight > 0)
                    .build()
                ViewCompat.dispatchApplyWindowInsets(view, insets)
            }
            val root = bounds("appScaffold")
            val safe = bounds("appContent")
            assertEquals("Android durum çubuğu kök yerleşime aktarılmalı", root.top + pixels(24f), safe.top, 1f)
            assertEquals("Android alt boşluğu kök yerleşime aktarılmalı", root.bottom - pixels(maxOf(48, keyboardHeight).toFloat()), safe.bottom, 1f)
            assertScreenFits(safe)
            clickActionsAndTabs()
        }
    }

    @Composable
    private fun TestScreen(insets: WindowInsets = WindowInsets.safeDrawing) {
        AppTheme(ThemePreference.Light, systemDark = false) {
            AppScaffold(contentWindowInsets = insets) {
                Scaffold(
                    contentWindowInsets = insets,
                    topBar = {
                        TopAppBar(
                            modifier = Modifier.testTag("screenTopBar"),
                            title = { Text("Örnek ekran") },
                            actions = {
                                IconButton(onClick = { actionCount++ }, modifier = Modifier.testTag("topAction")) { Text("Ayarlar") }
                            },
                        )
                    },
                    bottomBar = {
                        BottomNavBar("discover", navigatedRoutes::add,
                            windowInsets = insets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    },
                ) { padding ->
                    Box(Modifier.scaffoldPadding(padding).fillMaxSize().testTag("screenBody")) {
                        Button(onClick = { actionCount++ }, modifier = Modifier.align(Alignment.BottomCenter).testTag("bottomAction")) {
                            Text("Devam et")
                        }
                    }
                }
            }
        }
    }

    private fun assertScreenFits(safe: Rect) {
        for (tag in listOf("screenTopBar", "screenBody", "topAction", "bottomAction") + routes.map { "bottomNavItem$it" }) {
            assertInside(tag, safe)
        }
        assertEquals("İçerik araç çubuğunun hemen altında başlamalı", bounds("screenTopBar").bottom, bounds("screenBody").top, 1f)
        assertEquals("İçerik alt gezinmenin üzerinde bitmeli", bounds("bottomNavigationBar").top, bounds("screenBody").bottom, 1f)
    }

    private fun clickActionsAndTabs() {
        compose.onNodeWithTag("topAction").assertIsDisplayed().assertHasClickAction().performClick()
        compose.onNodeWithTag("bottomAction").assertIsDisplayed().assertHasClickAction().performClick()
        for (route in routes) {
            compose.onNodeWithTag("bottomNavItem$route").assertIsDisplayed().assertHasClickAction().performClick()
        }
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    private fun pixels(value: Float): Float = value * compose.density.density
    private fun assertInside(tag: String, safe: Rect) {
        val actual = bounds(tag)
        assertTrue("$tag güvenli alanın dışında: $actual / $safe",
            actual.left >= safe.left - 1f && actual.top >= safe.top - 1f &&
                actual.right <= safe.right + 1f && actual.bottom <= safe.bottom + 1f)
    }
}
