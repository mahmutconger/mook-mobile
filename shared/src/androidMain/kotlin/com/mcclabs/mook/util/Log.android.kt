package com.mcclabs.mook.util

import android.util.Log as AndroidLog

internal actual fun writeLog(level: String, tag: String, message: String, throwable: Throwable?) {
    when (level) {
        "E" -> AndroidLog.e(tag, message, throwable)
        else -> AndroidLog.d(tag, message)
    }
}
