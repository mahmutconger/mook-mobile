package com.mcclabs.mook.util

/**
 * Compares two semantic version strings (e.g. "1.0.5" vs "1.1.0").
 * 
 * @return 0 if versions are equal.
 * @return < 0 if [v1] is older than [v2].
 * @return > 0 if [v1] is newer than [v2].
 */
fun compareSemVer(v1: String, v2: String): Int {
    if (v1 == v2) return 0
    
    val parts1 = v1.split(".").mapNotNull { it.toIntOrNull() }
    val parts2 = v2.split(".").mapNotNull { it.toIntOrNull() }
    
    val length = maxOf(parts1.size, parts2.size)
    for (i in 0 until length) {
        val p1 = parts1.getOrElse(i) { 0 }
        val p2 = parts2.getOrElse(i) { 0 }
        if (p1 != p2) {
            return p1.compareTo(p2)
        }
    }
    return 0
}
