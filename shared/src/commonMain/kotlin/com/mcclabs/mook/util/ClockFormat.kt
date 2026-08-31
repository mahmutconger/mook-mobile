package com.mcclabs.mook.util

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Formats an epoch-millis timestamp as a zero-padded 24-hour wall clock ("09:05")
 * in the device's own time zone.
 *
 * A chat bubble with no time on it gives the reader no way to tell a reply from
 * two minutes ago apart from one from last week. 24-hour is used unconditionally:
 * kotlinx-datetime carries no locale-aware time formatter, and inventing an
 * AM/PM rule per language here would be worse than one consistent format.
 *
 * Returns an empty string for a non-positive [epochMillis] — a message document
 * written before its `timestamp` field landed reads as 0, and "00:00" would be a
 * lie the user cannot distinguish from a real midnight message.
 */
fun formatClockTime(epochMillis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    if (epochMillis <= 0L) return ""
    val time = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone).time
    return "${time.hour.padded()}:${time.minute.padded()}"
}

private fun Int.padded(): String = if (this < 10) "0$this" else toString()
