package com.mcclabs.mook.util

/**
 * Minimal multiplatform logger.
 *
 * Failures in this app are mostly swallowed by `catch` blocks that fall back to a
 * default, which makes a permission error look identical to "no data". Every such
 * fallback should log through here so the real cause stays visible.
 *
 * On Android this lands in logcat; on iOS in the Xcode console. Filter with the
 * shared [TAG].
 */
object Log {
    const val TAG = "WalkMatch"

    fun d(message: String) = writeLog("D", TAG, message, null)

    fun e(message: String, throwable: Throwable? = null) = writeLog("E", TAG, message, throwable)
}

internal expect fun writeLog(level: String, tag: String, message: String, throwable: Throwable?)
