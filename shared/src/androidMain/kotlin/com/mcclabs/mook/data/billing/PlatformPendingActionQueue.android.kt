package com.mcclabs.mook.data.billing

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.FirebaseApp
import com.mcclabs.mook.domain.billing.PendingActionQueue
import com.mcclabs.mook.domain.billing.PendingSwipeAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Paylaşılan katmanın Koin grafiğinde Context yok; Firebase ContentProvider ile uygulama
// açılışında zaten başlatılmış durumda, bu yüzden uygulama Context'i oradan alınır (bkz.
// data/sso/PlatformSsoSecurity.android.kt'deki aynı desen).
actual fun createPendingActionQueue(): PendingActionQueue =
    SharedPreferencesPendingActionQueue { FirebaseApp.getInstance().applicationContext }

private class SharedPreferencesPendingActionQueue(
    contextProvider: () -> Context,
) : PendingActionQueue {

    private val prefs: SharedPreferences by lazy {
        contextProvider().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun enqueue(action: PendingSwipeAction) = withContext(Dispatchers.IO) {
        val current = readAll().toMutableList()
        // Aynı profile+yön için zaten bekleyen bir kayıt varsa yinelemeyiz (ör. kullanıcı
        // hızlıca iki kez dokunduysa) — sunucu zaten idempotent olsa da kuyruğu şişirmemek için.
        if (current.none { it.profileId == action.profileId && it.isLike == action.isLike }) {
            current.add(action)
        }
        writeAll(current.takeLast(MAX_QUEUE_SIZE))
    }

    override suspend fun dequeueAll(): List<PendingSwipeAction> = withContext(Dispatchers.IO) {
        readAll()
    }

    override suspend fun remove(action: PendingSwipeAction) = withContext(Dispatchers.IO) {
        val current = readAll().filterNot { it == action }
        writeAll(current)
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        // apply() yerine commit(): süreç bu çağrıdan hemen sonra ölse dahi temizliğin diske
        // yazılmış olduğundan emin olunur. Dönüş değeri BİLEREK yok sayılır — arayüz `Unit`
        // bekler; `commit()`in kendi `Boolean`i burada anlamlı bir sinyal taşımaz.
        prefs.edit().clear().commit()
        Unit
    }

    private fun readAll(): List<PendingSwipeAction> {
        val count = prefs.getInt(KEY_COUNT, 0)
        return (0 until count).mapNotNull { i ->
            val profileId = prefs.getString(keyProfileId(i), null) ?: return@mapNotNull null
            if (!prefs.contains(keyEnqueuedAt(i))) return@mapNotNull null
            PendingSwipeAction(
                profileId = profileId,
                isLike = prefs.getBoolean(keyIsLike(i), true),
                enqueuedAtMillis = prefs.getLong(keyEnqueuedAt(i), 0L),
            )
        }
    }

    private fun writeAll(actions: List<PendingSwipeAction>) {
        val editor = prefs.edit()
        // Önce eski girişleri tamamen temizle (önceki yazımdan daha az öğe kalmış olabilir),
        // ardından güncel listeyi baştan yaz.
        val previousCount = prefs.getInt(KEY_COUNT, 0)
        for (i in 0 until previousCount) {
            editor.remove(keyProfileId(i)).remove(keyIsLike(i)).remove(keyEnqueuedAt(i))
        }
        actions.forEachIndexed { i, action ->
            editor.putString(keyProfileId(i), action.profileId)
            editor.putBoolean(keyIsLike(i), action.isLike)
            editor.putLong(keyEnqueuedAt(i), action.enqueuedAtMillis)
        }
        editor.putInt(KEY_COUNT, actions.size)
        // commit(): bu kayıt tam olarak process ölümüne karşı korumak için var olduğundan
        // asenkron apply() ile yarış durumuna girmemelidir.
        editor.commit()
    }

    private fun keyProfileId(i: Int) = "p${i}_profile_id"
    private fun keyIsLike(i: Int) = "p${i}_is_like"
    private fun keyEnqueuedAt(i: Int) = "p${i}_enqueued_at"

    private companion object {
        const val PREFS_NAME = "mook_pending_swipe_actions"
        const val KEY_COUNT = "count"
        const val MAX_QUEUE_SIZE = 20
    }
}
