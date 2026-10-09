package com.stackapp.stack.leaderboard

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val istZone: ZoneId = ZoneId.of("Asia/Kolkata")
private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

fun todayIstKey(clock: Clock = Clock.system(istZone)): String =
    LocalDate.now(clock.withZone(istZone)).format(dayFormatter)

fun istDayKeyAt(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(istZone).toLocalDate().format(dayFormatter)

fun startOfIstDayMillis(epochMillis: Long): Long =
    Instant.ofEpochMilli(epochMillis)
        .atZone(istZone)
        .toLocalDate()
        .atStartOfDay(istZone)
        .toInstant()
        .toEpochMilli()
