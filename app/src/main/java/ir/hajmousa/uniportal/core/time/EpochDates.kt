package ir.hajmousa.uniportal.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun tehranDate(epochSeconds: Long): LocalDate =
    Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.of("Asia/Tehran")).toLocalDate()
