package com.mcclabs.mook.theme

import androidx.lifecycle.ViewModelStore
import com.mcclabs.mook.data.local.LocalKeyValueStore
import com.mcclabs.mook.data.repository.ThemeRepositoryImpl
import com.mcclabs.mook.data.repository.LegacyThemePreferenceSource
import com.mcclabs.mook.domain.theme.ThemePreference
import com.mcclabs.mook.feature.theme.ThemeViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {
    private val viewModelStore = ViewModelStore()
    private var viewModelIndex = 0

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() {
        viewModelStore.clear()
        Dispatchers.resetMain()
    }

    private fun createViewModel(repository: ThemeRepositoryImpl): ThemeViewModel =
        ThemeViewModel(repository).also { viewModelStore.put("theme${viewModelIndex++}", it) }

    @Test fun storedPreferenceLoadsBeforeUiBecomesReady() = runTest {
        val store = FakeThemeStore("dark")
        val read = CompletableDeferred<Unit>()
        store.readGate = read
        val viewModel = createViewModel(ThemeRepositoryImpl(store))
        assertTrue(viewModel.uiState.value.isLoading)
        read.complete(Unit)
        runCurrent()
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(ThemePreference.Dark, viewModel.uiState.value.preference)
    }

    @Test fun everySelectionUpdatesBothObserversAndSurvivesRecreation() = runTest {
        val store = FakeThemeStore()
        val repository = ThemeRepositoryImpl(store)
        val root = createViewModel(repository)
        val settings = createViewModel(repository)
        val emitted = mutableListOf<ThemePreference>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            root.uiState.collect { if (!it.isLoading) emitted += it.preference }
        }
        for (preference in listOf(ThemePreference.Light, ThemePreference.Dark, ThemePreference.Ocean, ThemePreference.System)) {
            settings.selectTheme(preference)
            runCurrent()
            assertEquals(preference, root.uiState.value.preference)
            assertEquals(preference, settings.uiState.value.preference)
            assertEquals(preference.storageKey, store.value)
            val recreated = createViewModel(ThemeRepositoryImpl(store))
            assertEquals(preference, recreated.uiState.value.preference)
        }
        assertEquals(listOf(ThemePreference.System, ThemePreference.Light, ThemePreference.Dark, ThemePreference.Ocean, ThemePreference.System), emitted)
    }

    @Test fun rapidSelectionsPublishLatestWhilePreviousDiskWriteIsPending() = runTest {
        val store = FakeThemeStore("light")
        val write = CompletableDeferred<Unit>()
        store.writeGate = write
        val root = createViewModel(ThemeRepositoryImpl(store))
        root.selectTheme(ThemePreference.Dark)
        assertEquals(ThemePreference.Dark, root.uiState.value.preference)
        root.selectTheme(ThemePreference.Ocean)
        assertEquals(ThemePreference.Ocean, root.uiState.value.preference)
        root.selectTheme(ThemePreference.System)
        assertEquals(ThemePreference.System, root.uiState.value.preference)
        write.complete(Unit)
        runCurrent()
        assertEquals("system", store.value)
        assertEquals(ThemePreference.System, root.uiState.value.preference)
    }

    @Test fun failedSaveRestoresPersistedPreferenceAndShowsTurkishError() = runTest {
        val store = FakeThemeStore("light")
        val root = createViewModel(ThemeRepositoryImpl(store))
        store.failWrites = true
        root.selectTheme(ThemePreference.Dark)
        runCurrent()
        assertEquals(ThemePreference.Light, root.uiState.value.preference)
        assertEquals("light", store.value)
        assertEquals("Tema tercihi kaydedilemedi. Lütfen yeniden deneyin.", root.uiState.value.errorMessage)
        store.failWrites = false
        root.selectTheme(ThemePreference.Ocean)
        runCurrent()
        assertNull(root.uiState.value.errorMessage)
        assertEquals(ThemePreference.Ocean, root.uiState.value.preference)
    }

    @Test fun readFailureReleasesRootWithSystemFallback() = runTest {
        val store = FakeThemeStore().apply { failReads = true }
        val root = createViewModel(ThemeRepositoryImpl(store))
        assertFalse(root.uiState.value.isLoading)
        assertEquals(ThemePreference.System, root.uiState.value.preference)
        assertNotNull(root.uiState.value.errorMessage)
    }

    @Test fun unknownStoredValueFallsBackToSystem() = runTest {
        val root = createViewModel(ThemeRepositoryImpl(FakeThemeStore("unknown")))
        assertEquals(ThemePreference.System, root.uiState.value.preference)
    }

    @Test fun legacyMigrationPersistsOnceAndCannotOverwriteUserSelection() = runTest {
        val store = FakeThemeStore()
        var readCount = 0
        val repository = ThemeRepositoryImpl(store, LegacyThemePreferenceSource { readCount++; true })
        repository.migrateLegacyPreference()
        repository.migrateLegacyPreference()
        assertEquals(ThemePreference.Dark, repository.preference.value)
        assertEquals("dark", store.value)
        assertEquals(1, readCount)
        val response = CompletableDeferred<Boolean>()
        val freshStore = FakeThemeStore()
        val freshRepository = ThemeRepositoryImpl(freshStore, LegacyThemePreferenceSource { response.await() })
        val migration = launch { freshRepository.migrateLegacyPreference() }
        runCurrent()
        freshRepository.setPreference(ThemePreference.Ocean)
        response.complete(true)
        migration.join()
        assertEquals(ThemePreference.Ocean, freshRepository.preference.value)
        assertEquals("ocean", freshStore.value)
    }

    @Test fun failedOlderWriteCannotRollbackOrShowErrorForNewerSelection() = runTest {
        val firstWrite = CompletableDeferred<Unit>()
        var stored = "light"
        var writes = 0
        val store = object : LocalKeyValueStore {
            override suspend fun getString(key: String) = stored
            override suspend fun putString(key: String, value: String) {
                if (++writes == 1) {
                    firstWrite.await()
                    error("Tema tercihi kaydedilemedi")
                }
                stored = value
            }
        }
        val root = createViewModel(ThemeRepositoryImpl(store))
        root.selectTheme(ThemePreference.Dark)
        root.selectTheme(ThemePreference.Ocean)
        firstWrite.complete(Unit)
        runCurrent()
        assertEquals(ThemePreference.Ocean, root.uiState.value.preference)
        assertNull(root.uiState.value.errorMessage)
        assertEquals("ocean", stored)
    }

    @Test fun selectionDuringMigrationDiskWritePublishesImmediatelyAndWinsPersistence() = runTest {
        val store = FakeThemeStore()
        val write = CompletableDeferred<Unit>()
        store.writeGate = write
        val repository = ThemeRepositoryImpl(store, LegacyThemePreferenceSource { true })
        val root = createViewModel(repository)
        val migration = launch { repository.migrateLegacyPreference() }
        runCurrent()
        root.selectTheme(ThemePreference.Ocean)
        assertEquals(ThemePreference.Ocean, root.uiState.value.preference)
        write.complete(Unit)
        migration.join()
        runCurrent()
        assertEquals(ThemePreference.Ocean, root.uiState.value.preference)
        assertEquals("ocean", store.value)
    }

    @Test fun explicitThemeIgnoresSystemAndSystemBasedThemesFollowIt() {
        assertFalse(ThemePreference.Light.isDark(true))
        assertTrue(ThemePreference.Dark.isDark(false))
        for (preference in listOf(ThemePreference.System, ThemePreference.Ocean)) {
            assertFalse(preference.isDark(false))
            assertTrue(preference.isDark(true))
        }
    }
}

private class FakeThemeStore(var value: String? = null) : LocalKeyValueStore {
    var readGate: CompletableDeferred<Unit>? = null
    var writeGate: CompletableDeferred<Unit>? = null
    var failReads = false
    var failWrites = false
    override suspend fun getString(key: String): String? {
        readGate?.await()
        check(!failReads) { "Tema tercihi okunamadı" }
        return value
    }
    override suspend fun putString(key: String, value: String) {
        writeGate?.await()
        check(!failWrites) { "Tema tercihi kaydedilemedi" }
        this.value = value
    }
}
