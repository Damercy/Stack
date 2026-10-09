package com.stackapp.stack.autominer

import com.stackapp.stack.tap.TapState
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoMinerTest {
    @Test
    fun catchUpEarnsFifteenTapsPerFifteenMinuteInterval() {
        val result = applyAutoMinerCatchUp(
            state = TapState(lifetimeCount = 100, todayCount = 25, todayDayKey = "test-day"),
            lastSyncAtMillis = 1_000,
            nowMillis = 1_000 + (3 * AUTO_MINER_INTERVAL_MILLIS),
            currentDayKey = "test-day",
            currentDayStartMillis = 0,
        )

        assertEquals(45, result.earnedTaps)
        assertEquals(145, result.state.lifetimeCount)
        assertEquals(70, result.state.todayCount)
    }

    @Test
    fun catchUpDoesNotAdvanceWhenLessThanOneIntervalElapsed() {
        val result = applyAutoMinerCatchUp(
            state = TapState(lifetimeCount = 100, todayCount = 25, todayDayKey = "test-day"),
            lastSyncAtMillis = 1_000,
            nowMillis = 1_000 + AUTO_MINER_INTERVAL_MILLIS - 1,
            currentDayKey = "test-day",
            currentDayStartMillis = 0,
        )

        assertEquals(0, result.earnedTaps)
        assertEquals(100, result.state.lifetimeCount)
        assertEquals(25, result.state.todayCount)
        assertEquals(1_000, result.syncedAtMillis)
    }

    @Test
    fun catchUpInitializesSyncTimeWithoutGrantingTaps() {
        val result = applyAutoMinerCatchUp(
            state = TapState(lifetimeCount = 100, todayCount = 25, todayDayKey = "test-day"),
            lastSyncAtMillis = 0,
            nowMillis = 5_000,
            currentDayKey = "test-day",
            currentDayStartMillis = 0,
        )

        assertEquals(0, result.earnedTaps)
        assertEquals(5_000, result.syncedAtMillis)
    }

    @Test
    fun inactiveAutoMinerDoesNotEarnOrAdvanceSyncTime() {
        val result = applyAutoMinerCatchUpIfActive(
            state = TapState(lifetimeCount = 100, todayCount = 25, todayDayKey = "test-day"),
            autoMinerActive = false,
            lastSyncAtMillis = 1_000,
            nowMillis = 1_000 + (3 * AUTO_MINER_INTERVAL_MILLIS),
            currentDayKey = "test-day",
            currentDayStartMillis = 0,
        )

        assertEquals(0, result.earnedTaps)
        assertEquals(100, result.state.lifetimeCount)
        assertEquals(25, result.state.todayCount)
        assertEquals(1_000, result.syncedAtMillis)
    }

    @Test
    fun catchUpAcrossMidnightAddsOnlyTodayIntervalsToTodayCount() {
        val result = applyAutoMinerCatchUp(
            state = TapState(
                lifetimeCount = 100,
                todayCount = 40,
                todayDayKey = "2026-07-08",
            ),
            lastSyncAtMillis = 1,
            nowMillis = 45 * 60 * 1000,
            currentDayKey = "2026-07-09",
            currentDayStartMillis = 30 * 60 * 1000,
        )

        assertEquals(30, result.earnedTaps)
        assertEquals(130, result.state.lifetimeCount)
        assertEquals(15, result.state.todayCount)
        assertEquals("2026-07-09", result.state.todayDayKey)
    }
}
