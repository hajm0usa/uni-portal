package ir.hajmousa.uniportal.core.time

import android.icu.text.SimpleDateFormat
import android.icu.util.TimeZone
import android.icu.util.ULocale
import java.time.ZoneId
import java.util.Date

fun formatPersianDateTime(epochSeconds: Long): String = format(epochSeconds, "yyyy/MM/dd HH:mm")

fun formatPersianDate(epochSeconds: Long): String = format(epochSeconds, "yyyy/MM/dd")

fun formatPersianDay(epochSeconds: Long): String = format(epochSeconds, "EEEE، d MMMM yyyy")

fun formatPersianTime(epochSeconds: Long): String = format(epochSeconds, "HH:mm")

private fun format(epochSeconds: Long, pattern: String): String =
    SimpleDateFormat(pattern, ULocale("fa_IR@calendar=persian")).apply {
        timeZone = TimeZone.getTimeZone(ZoneId.of("Asia/Tehran").id)
    }.format(Date(Math.multiplyExact(epochSeconds, 1000L)))
