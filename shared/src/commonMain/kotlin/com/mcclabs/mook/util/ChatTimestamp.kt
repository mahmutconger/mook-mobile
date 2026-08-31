package com.mcclabs.mook.util

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * How a chat-list row labels the time of its last message.
 *
 * Split into a type rather than a plain string because "yesterday" has to come from
 * the string catalogue to follow the app's language, while the other two are numeric
 * and locale-neutral. Keeping the decision here makes it testable without Compose.
 */
sealed interface ChatTimestampLabel {

    /** Sent today — show the wall clock, e.g. "14:32". */
    data class Today(val text: String) : ChatTimestampLabel

    /** Sent yesterday — the screen renders the localized word. */
    data object Yesterday : ChatTimestampLabel

    /** Anything older — a numeric date, e.g. "31.08.2026". */
    data class Older(val text: String) : ChatTimestampLabel

    /** No timestamp to show at all. */
    data object None : ChatTimestampLabel
}

/**
 * Chooses the label for [epochMillis] relative to [nowMillis].
 *
 * Deliberately *not* "3 days ago" style: relative phrasing needs plural rules per
 * language, and a chat list is scanned, not read — a date is faster to recognise than
 * a duration to decode. Day names are avoided for the same reason they would cost
 * seven more strings per language for one row of text.
 *
 * The comparison is on calendar days in [timeZone], not on elapsed milliseconds: a
 * message sent at 23:50 must read "yesterday" at 00:10, not "today".
 */
fun chatTimestampLabel(
    epochMillis: Long?,
    nowMillis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): ChatTimestampLabel {
    if (epochMillis == null || epochMillis <= 0L) return ChatTimestampLabel.None

    val sent = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone)
    val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone).date

    // Widened explicitly: toEpochDays() returns Int on some targets and Long on
    // others, so comparing the raw difference compiles on Android and fails on iOS.
    val daysApart = today.toEpochDays().toLong() - sent.date.toEpochDays().toLong()
    return when {
        daysApart == 0L -> ChatTimestampLabel.Today(formatClockTime(epochMillis, timeZone))
        daysApart == 1L -> ChatTimestampLabel.Yesterday
        // A future timestamp means a clock skew somewhere; showing the date is
        // honest, whereas "today" would be a guess.
        else -> ChatTimestampLabel.Older(
            "${sent.date.dayOfMonth.padded()}.${sent.date.monthNumber.padded()}.${sent.date.year}"
        )
    }
}

private fun Int.padded(): String = if (this < 10) "0$this" else toString()
