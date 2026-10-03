package com.mcclabs.mook.domain.billing

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * "Yarın tekrar gel" geri sayımının saf hesaplaması: günlük hakların yenilendiği YEREL gece
 * yarısına kalan süre. Yaz saati geçişlerinde de doğrudur (gece yarısı saat dilimi kurallarıyla
 * hesaplanır, sabit 24 saat varsayılmaz). Sunucu da günlük kotaları kullanıcının kayıtlı saat
 * dilimine göre sıfırlar (cihaz dilimi açılışta eşitlenir).
 */
object MidnightCountdown {

    /** [nowMillis] anından bir sonraki yerel gece yarısına kalan milisaniye (her zaman > 0). */
    fun millisUntilNextMidnight(nowMillis: Long, timeZone: TimeZone): Long {
        val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone).date
        val nextMidnight = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
        return (nextMidnight.toEpochMilliseconds() - nowMillis).coerceAtLeast(1L)
    }

    /** Kalan süreyi "SS:DD:ss" biçiminde yazar (ör. "05:07:09"). */
    fun format(remainingMillis: Long): String {
        val totalSeconds = (remainingMillis.coerceAtLeast(0L) + 999) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return "${hours.pad()}:${minutes.pad()}:${seconds.pad()}"
    }

    private fun Long.pad(): String = toString().padStart(2, '0')
}
