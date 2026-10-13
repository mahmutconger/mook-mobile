package com.mcclabs.mook.ui.optimistic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Ekranın ana iş parçacığında kullanılır; kapsamın sahibi ViewModel'dir.
 * Aynı anahtardaki ikinci işlem tamamlanana kadar reddedilir, farklı anahtarlar bağımsızdır.
 * Geri alma, güncel durum üzerinde yalnızca işleme ait alanları değiştirmelidir;
 * önceki durumun tamamını kopyalamak eşzamanlı güncellemeleri kaybettirir.
 * İptalde yerel değişiklik geri alınır, kullanıcıya hata gösterilmez.
 */
class OptimisticStateController<State, Key>(
    private val state: MutableStateFlow<State>,
    private val scope: CoroutineScope,
) {
    private val pendingKeys = mutableSetOf<Key>()

    fun isPending(key: Key): Boolean = key in pendingKeys

    fun <Result> execute(
        key: Key,
        optimistic: (State) -> State,
        rollback: (current: State, previous: State) -> State,
        settle: (State) -> State = { it },
        sync: suspend () -> Result,
        onSuccess: suspend (Result) -> Unit = {},
        onFailure: (Exception) -> Unit,
    ): Boolean {
        if (!scope.isActive || !pendingKeys.add(key)) return false
        val previous = state.value
        state.update(optimistic)
        // Askıya alınana kadar başlatmak, kapsam hemen iptal edilse bile temizliği garanti eder.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val result = try {
                sync()
            } catch (cancelled: CancellationException) {
                state.update { rollback(it, previous) }
                throw cancelled
            } catch (error: Exception) {
                state.update { rollback(it, previous) }
                onFailure(error)
                return@launch
            } finally {
                pendingKeys.remove(key)
                state.update(settle)
            }
            // Sunucu onayından sonraki gezinme/reklam hatası onaylanmış yazımı geri almaz.
            onSuccess(result)
        }
        return true
    }
}
