package com.mcclabs.mook.util

private const val HEX = "0123456789ABCDEF"
private const val UNRESERVED =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"

/**
 * Percent-encodes [value] for safe use as a URL query-component value.
 *
 * Encodes every byte outside the RFC 3986 *unreserved* set (`A-Z a-z 0-9 - _ . ~`)
 * over the UTF-8 representation, so characters like `@`, `+` and non-ASCII are made
 * URL-safe. Multiplatform-friendly: no `java.net.URLEncoder` dependency.
 */
fun encodeUrlComponent(value: String): String {
    val bytes = value.encodeToByteArray()
    val sb = StringBuilder(bytes.size)
    for (b in bytes) {
        val c = b.toInt() and 0xFF
        if (c < 128 && c.toChar() in UNRESERVED) {
            sb.append(c.toChar())
        } else {
            sb.append('%').append(HEX[c shr 4]).append(HEX[c and 0x0F])
        }
    }
    return sb.toString()
}
