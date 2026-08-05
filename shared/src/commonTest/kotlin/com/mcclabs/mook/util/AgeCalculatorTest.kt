package com.mcclabs.mook.util

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgeCalculatorTest {

    private fun millisOf(iso: String): Long = Instant.parse(iso).toEpochMilliseconds()

    private fun ageOn(birth: String, today: String): Int? = calculateAge(
        birthDateMillis = millisOf(birth),
        nowMillis = millisOf(today),
        timeZone = TimeZone.UTC,
    )

    @Test
    fun returnsNullWhenBirthDateIsUnknown() {
        assertNull(calculateAge(birthDateMillis = null))
    }

    @Test
    fun countsFullYearsAfterBirthdayHasPassed() {
        assertEquals(30, ageOn(birth = "1995-03-10T00:00:00Z", today = "2025-08-01T12:00:00Z"))
    }

    @Test
    fun doesNotCountBirthdayThatHasNotOccurredYet() {
        assertEquals(29, ageOn(birth = "1995-12-10T00:00:00Z", today = "2025-08-01T12:00:00Z"))
    }

    @Test
    fun countsBirthdayOnTheDayItself() {
        assertEquals(30, ageOn(birth = "1995-08-01T00:00:00Z", today = "2025-08-01T12:00:00Z"))
    }

    @Test
    fun handlesLeapDayBirthInNonLeapYear() {
        assertEquals(4, ageOn(birth = "2020-02-29T00:00:00Z", today = "2025-02-28T12:00:00Z"))
    }

    @Test
    fun returnsNullForFutureBirthDate() {
        assertNull(ageOn(birth = "2030-01-01T00:00:00Z", today = "2025-08-01T12:00:00Z"))
    }
}
