package com.mcclabs.mook.util

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearsUntil

/**
 * Returns the age in whole years for a birth date given as [birthDateMillis], or `null`
 * if the date is unknown or lies in the future.
 *
 * Birth dates are stored as UTC midnight by the registration date picker, so they are
 * read back in UTC; "today" is resolved in the device's own zone. A birthday that has
 * not yet occurred this year is not counted.
 *
 * [nowMillis] and [timeZone] are injectable for testing.
 */
fun calculateAge(
    birthDateMillis: Long?,
    nowMillis: Long = getCurrentTimeMillis(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): Int? {
    if (birthDateMillis == null) return null
    val birthDate = Instant.fromEpochMilliseconds(birthDateMillis).toLocalDateTime(TimeZone.UTC).date
    val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone).date
    return birthDate.yearsUntil(today).takeIf { it >= 0 }
}
