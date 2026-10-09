package com.stackapp.stack.leaderboard

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DailyKeyTest {
    @Test
    fun todayIstKeyUsesKolkataCalendarDay() {
        val clock = Clock.fixed(
            Instant.parse("2026-07-08T20:00:00Z"),
            ZoneOffset.UTC,
        )

        assertEquals("2026-07-09", todayIstKey(clock))
    }
}

