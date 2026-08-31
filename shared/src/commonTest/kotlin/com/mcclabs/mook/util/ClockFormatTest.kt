package com.mcclabs.mook.util

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Bubble timestamps are pinned to UTC here so the assertions do not depend on the
 * machine running them.
 */
class ClockFormatTest {

    private val utc = TimeZone.UTC

    @Test
    fun formatsAsZeroPaddedTwentyFourHourTime() {
        // 1970-01-01T09:05:00Z
        assertEquals("09:05", formatClockTime(9 * 3_600_000L + 5 * 60_000L, utc))
        // 1970-01-01T23:59:00Z — no AM/PM wrap-around.
        assertEquals("23:59", formatClockTime(23 * 3_600_000L + 59 * 60_000L, utc))
    }

    @Test
    fun midnightIsNotConfusedWithAMissingTimestamp() {
        // One millisecond past the epoch is a real (if absurd) time and must render.
        assertEquals("00:00", formatClockTime(1L, utc))
    }

    @Test
    fun aMissingTimestampRendersNothingRatherThanALie() {
        // A message document written before its `timestamp` field landed reads as 0.
        // "00:00" there would be indistinguishable from a real midnight message.
        assertEquals("", formatClockTime(0L, utc))
        assertEquals("", formatClockTime(-1L, utc))
    }
}
