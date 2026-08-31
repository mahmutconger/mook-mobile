package com.mcclabs.mook.util

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The chat-list label is calendar-based, not duration-based. These cases pin the
 * boundary that a naive "less than 24 hours ago" implementation gets wrong.
 */
class ChatTimestampTest {

    private val utc = TimeZone.UTC
    private val day = 24 * 60 * 60 * 1000L

    /** 2026-08-31T12:00:00Z, chosen only so the arithmetic below is readable. */
    private val now = 1788177600000L

    @Test
    fun aMessageFromEarlierTodayShowsTheClock() {
        val label = chatTimestampLabel(now - 3 * 60 * 60 * 1000L, now, utc)
        assertIs<ChatTimestampLabel.Today>(label)
        assertEquals("09:00", label.text)
    }

    @Test
    fun tenMinutesBeforeMidnightIsYesterdayNotToday() {
        // The trap: 23:50 read at 00:10 is 20 minutes ago, but it is not "today".
        val justAfterMidnight = now - 12 * 60 * 60 * 1000L + 10 * 60 * 1000L
        val beforeMidnight = justAfterMidnight - 20 * 60 * 1000L
        assertEquals(
            ChatTimestampLabel.Yesterday,
            chatTimestampLabel(beforeMidnight, justAfterMidnight, utc),
        )
    }

    @Test
    fun twentyThreeHoursAgoCanStillBeYesterday() {
        // Nearly a full day back, but the calendar day differs — still "yesterday".
        assertEquals(
            ChatTimestampLabel.Yesterday,
            chatTimestampLabel(now - 23 * 60 * 60 * 1000L, now, utc),
        )
    }

    @Test
    fun olderMessagesShowANumericDate() {
        val label = chatTimestampLabel(now - 5 * day, now, utc)
        assertIs<ChatTimestampLabel.Older>(label)
        assertEquals("26.08.2026", label.text)
    }

    @Test
    fun singleDigitDayAndMonthAreZeroPadded() {
        // 2026-01-05T00:00:00Z read from 2026-03-01.
        val jan5 = 1767571200000L
        val label = chatTimestampLabel(jan5, jan5 + 55 * day, utc)
        assertIs<ChatTimestampLabel.Older>(label)
        assertEquals("05.01.2026", label.text)
    }

    @Test
    fun missingTimestampsProduceNoLabel() {
        // A room whose only message predates the timestamp field reads as 0, and a
        // room with no messages at all has null.
        assertEquals(ChatTimestampLabel.None, chatTimestampLabel(null, now, utc))
        assertEquals(ChatTimestampLabel.None, chatTimestampLabel(0L, now, utc))
    }

    @Test
    fun aFutureTimestampFallsBackToADateRatherThanClaimingToday() {
        // Clock skew between devices is real; a date is honest where "today" guesses.
        val label = chatTimestampLabel(now + 3 * day, now, utc)
        assertIs<ChatTimestampLabel.Older>(label)
    }
}
