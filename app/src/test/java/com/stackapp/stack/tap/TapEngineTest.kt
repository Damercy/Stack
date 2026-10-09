package com.stackapp.stack.tap

import org.junit.Assert.assertEquals
import org.junit.Test

class TapEngineTest {
    @Test
    fun recordTapIncrementsLifetimeAndTodayCounts() {
        val engine = TapEngine()

        val state = engine.recordTap()

        assertEquals(1, state.lifetimeCount)
        assertEquals(1, state.todayCount)
    }

    @Test
    fun recordTapPreservesExistingCounts() {
        val engine = TapEngine(TapState(lifetimeCount = 41, todayCount = 9))

        val state = engine.recordTap()

        assertEquals(42, state.lifetimeCount)
        assertEquals(10, state.todayCount)
    }

    @Test
    fun rapidTapsDoNotDropIncrements() {
        val engine = TapEngine()

        repeat(500) {
            engine.recordTap()
        }

        assertEquals(500, engine.state.lifetimeCount)
        assertEquals(500, engine.state.todayCount)
    }

    @Test
    fun recordTapResetsTodayCountWhenDayChanges() {
        val engine = TapEngine(
            TapState(
                lifetimeCount = 41,
                todayCount = 9,
                todayDayKey = "2026-07-08",
            ),
        )

        val state = engine.recordTap(dayKey = "2026-07-09")

        assertEquals(42, state.lifetimeCount)
        assertEquals(1, state.todayCount)
        assertEquals("2026-07-09", state.todayDayKey)
    }
}
