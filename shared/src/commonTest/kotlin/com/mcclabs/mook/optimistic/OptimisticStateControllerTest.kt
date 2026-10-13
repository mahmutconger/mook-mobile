package com.mcclabs.mook.optimistic

import com.mcclabs.mook.ui.optimistic.OptimisticStateController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class OptimisticStateControllerTest {
    private data class TestState(val selected: Set<String> = emptySet(), val title: String = "Profil")

    @Test
    fun successUpdatesSynchronouslyAndRetainsTheConfirmedState() = runTest {
        val state = MutableStateFlow(TestState())
        val controller = OptimisticStateController<TestState, String>(state, backgroundScope)
        val response = CompletableDeferred<Unit>()
        var succeeded = false
        controller.execute("like", { it.copy(selected = it.selected + "like") },
            { current, _ -> current.copy(selected = current.selected - "like") },
            sync = { response.await() }, onSuccess = { succeeded = true },
            onFailure = { error("Beklenmeyen hata.") })
        assertEquals(setOf("like"), state.value.selected)
        assertTrue(controller.isPending("like"))
        assertFalse(succeeded)
        response.complete(Unit)
        runCurrent()
        assertEquals(setOf("like"), state.value.selected)
        assertFalse(controller.isPending("like"))
        assertTrue(succeeded)
    }

    @Test
    fun failureRollsBackAndEmitsExactlyOneSideEffect() = runTest {
        val state = MutableStateFlow(TestState())
        val controller = OptimisticStateController<TestState, String>(state, backgroundScope)
        val response = CompletableDeferred<Unit>()
        val failures = mutableListOf<Exception>()
        controller.execute("like", { it.copy(selected = setOf("like")) },
            { current, previous -> current.copy(selected = previous.selected) },
            sync = { response.await() }, onFailure = { failures += it })
        assertEquals(setOf("like"), state.value.selected)
        val error = IllegalStateException("İzin reddedildi.")
        response.completeExceptionally(error)
        runCurrent()
        assertEquals(emptySet<String>(), state.value.selected)
        // Korutin yığın izi geri kazanımı istisnayı kopyalayabilir; tür ve ileti korunmalıdır.
        assertEquals(error::class, failures.single()::class)
        assertEquals(error.message, failures.single().message)
        assertFalse(controller.isPending("like"))
        runCurrent()
        assertEquals(1, failures.size)
    }

    @Test
    fun duplicateKeysAreRejectedWhileIndependentKeysCanProceed() = runTest {
        val state = MutableStateFlow(TestState())
        val controller = OptimisticStateController<TestState, String>(state, backgroundScope)
        val first = CompletableDeferred<Unit>()
        val second = CompletableDeferred<Unit>()
        var calls = 0
        fun submit(key: String, response: CompletableDeferred<Unit>) = controller.execute(key,
            { it.copy(selected = it.selected + key) },
            { current, _ -> current.copy(selected = current.selected - key) },
            sync = { calls++; response.await() }, onFailure = {})
        assertTrue(submit("first", first))
        assertFalse(submit("first", second))
        assertTrue(submit("second", second))
        state.value = state.value.copy(title = "Yeni profil")
        second.complete(Unit)
        first.completeExceptionally(IllegalStateException("Bağlantı kurulamadı."))
        runCurrent()
        assertEquals(2, calls)
        assertEquals(TestState(setOf("second"), "Yeni profil"), state.value)
        assertFalse(controller.isPending("first"))
        assertFalse(controller.isPending("second"))
    }

    @Test
    fun cancellationRollsBackWithoutAnErrorEventAndReleasesTheKey() = runTest {
        val owner = CoroutineScope(coroutineContext + Job())
        val state = MutableStateFlow(TestState())
        val controller = OptimisticStateController<TestState, String>(state, owner)
        var failures = 0
        controller.execute("like", { it.copy(selected = setOf("like")) },
            { _, previous -> previous }, sync = { CompletableDeferred<Unit>().await() },
            onFailure = { failures++ })
        owner.cancel()
        runCurrent()
        assertEquals(TestState(), state.value)
        assertEquals(0, failures)
        assertFalse(controller.isPending("like"))
        assertFalse(controller.execute("like", { it }, { _, previous -> previous },
            sync = {}, onFailure = {}))
    }

    @Test
    fun explicitCancellationIsNotReportedAsBackendFailure() = runTest {
        val state = MutableStateFlow(TestState())
        val controller = OptimisticStateController<TestState, String>(state, backgroundScope)
        var failures = 0
        controller.execute("like", { it.copy(selected = setOf("like")) },
            { _, previous -> previous }, sync = { throw CancellationException("İşlem iptal edildi.") },
            onFailure = { failures++ })
        assertEquals(TestState(), state.value)
        assertEquals(0, failures)
        assertFalse(controller.isPending("like"))
    }

    @Test
    fun failedKeyCanBeRetried() = runTest {
        val state = MutableStateFlow(TestState())
        val controller = OptimisticStateController<TestState, String>(state, backgroundScope)
        assertTrue(controller.execute("like", { it.copy(selected = setOf("like")) },
            { _, previous -> previous }, sync = { error("Bağlantı kurulamadı.") }, onFailure = {}))
        assertEquals(TestState(), state.value)
        assertTrue(controller.execute("like", { it.copy(selected = setOf("like")) },
            { _, previous -> previous }, sync = {}, onFailure = {}))
        assertEquals(setOf("like"), state.value.selected)
    }
}
