package com.mcclabs.mook.util

import platform.Foundation.NSLog

internal actual fun writeLog(level: String, tag: String, message: String, throwable: Throwable?) {
    val suffix = throwable?.let { " | ${it::class.simpleName}: ${it.message}" }.orEmpty()
    NSLog("%s/%s: %s%s", level, tag, message, suffix)
}
