package com.mcclabs.mook.util

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Minimum age required to create a Mook account.
 *
 * Mook has matchmaking features, so Google Play requires blocking users under 18
 * via a neutral age gate during registration.
 */
const val MINIMUM_AGE: Int = 18

/**
 * Calculates the whole-year age for a birth date given as epoch milliseconds,
 * relative to the current date.
 *
 * The birth date is interpreted in [TimeZone.UTC] because the calendar
 * DatePicker reports the selected day as UTC midnight, while "today" uses the
 * device's local zone.
 */
fun calculateAge(birthDateMillis: Long): Int {
    val today = Instant.fromEpochMilliseconds(getCurrentTimeMillis())
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    val birth = Instant.fromEpochMilliseconds(birthDateMillis)
        .toLocalDateTime(TimeZone.UTC).date

    var age = today.year - birth.year
    if (today.monthNumber < birth.monthNumber ||
        (today.monthNumber == birth.monthNumber && today.dayOfMonth < birth.dayOfMonth)
    ) {
        age--
    }
    return age
}

/** Whether [birthDateMillis] meets Mook's [MINIMUM_AGE] requirement. */
fun isOfMinimumAge(birthDateMillis: Long): Boolean =
    calculateAge(birthDateMillis) >= MINIMUM_AGE
