package com.mcclabs.mook.data.billing

import com.mcclabs.mook.domain.billing.PendingActionQueue
import com.mcclabs.mook.domain.billing.PendingSwipeAction
import platform.Foundation.NSUserDefaults

actual fun createPendingActionQueue(): PendingActionQueue = UserDefaultsPendingActionQueue()

private class UserDefaultsPendingActionQueue : PendingActionQueue {

    private val defaults get() = NSUserDefaults.standardUserDefaults

    override suspend fun enqueue(action: PendingSwipeAction) {
        val current = readAll().toMutableList()
        if (current.none { it.profileId == action.profileId && it.isLike == action.isLike }) {
            current.add(action)
        }
        writeAll(current.takeLast(MAX_QUEUE_SIZE))
    }

    override suspend fun dequeueAll(): List<PendingSwipeAction> = readAll()

    override suspend fun remove(action: PendingSwipeAction) {
        writeAll(readAll().filterNot { it == action })
    }

    override suspend fun clear() {
        val count = defaults.integerForKey(KEY_COUNT)
        for (i in 0 until count) {
            defaults.removeObjectForKey(keyProfileId(i))
            defaults.removeObjectForKey(keyIsLike(i))
            defaults.removeObjectForKey(keyEnqueuedAt(i))
        }
        defaults.removeObjectForKey(KEY_COUNT)
    }

    private fun readAll(): List<PendingSwipeAction> {
        val count = defaults.integerForKey(KEY_COUNT)
        return (0 until count).mapNotNull { i ->
            val profileId = defaults.stringForKey(keyProfileId(i)) ?: return@mapNotNull null
            defaults.objectForKey(keyEnqueuedAt(i)) ?: return@mapNotNull null
            PendingSwipeAction(
                profileId = profileId,
                isLike = defaults.boolForKey(keyIsLike(i)),
                enqueuedAtMillis = defaults.integerForKey(keyEnqueuedAt(i)),
            )
        }
    }

    private fun writeAll(actions: List<PendingSwipeAction>) {
        val previousCount = defaults.integerForKey(KEY_COUNT)
        for (i in 0 until previousCount) {
            defaults.removeObjectForKey(keyProfileId(i))
            defaults.removeObjectForKey(keyIsLike(i))
            defaults.removeObjectForKey(keyEnqueuedAt(i))
        }
        actions.forEachIndexed { i, action ->
            defaults.setObject(action.profileId, forKey = keyProfileId(i))
            defaults.setBool(action.isLike, forKey = keyIsLike(i))
            // NSInteger 64 bit; epoch milisaniyesi sığar.
            defaults.setInteger(action.enqueuedAtMillis, forKey = keyEnqueuedAt(i))
        }
        defaults.setInteger(actions.size.toLong(), forKey = KEY_COUNT)
    }

    private fun keyProfileId(i: Long) = "mook_pending_swipe_${i}_profile_id"
    private fun keyIsLike(i: Long) = "mook_pending_swipe_${i}_is_like"
    private fun keyEnqueuedAt(i: Long) = "mook_pending_swipe_${i}_enqueued_at"

    private companion object {
        const val KEY_COUNT = "mook_pending_swipe_count"
        const val MAX_QUEUE_SIZE = 20
    }
}
