package com.stackapp.stack.autominer

import com.stackapp.stack.leaderboard.istDayKeyAt
import com.stackapp.stack.leaderboard.startOfIstDayMillis
import com.stackapp.stack.tap.TapState
import com.stackapp.stack.tap.forDay

const val AUTO_MINER_TAPS_PER_INTERVAL = 15L
const val AUTO_MINER_INTERVAL_MINUTES = 15L
const val AUTO_MINER_INTERVAL_MILLIS = AUTO_MINER_INTERVAL_MINUTES * 60L * 1000L

data class AutoMinerResult(
    val state: TapState,
    val earnedTaps: Long,
    val syncedAtMillis: Long,
)

fun applyAutoMinerCatchUp(
    state: TapState,
    lastSyncAtMillis: Long,
    nowMillis: Long,
    currentDayKey: String = istDayKeyAt(nowMillis),
    currentDayStartMillis: Long = startOfIstDayMillis(nowMillis),
): AutoMinerResult {
    if (lastSyncAtMillis <= 0 || nowMillis <= lastSyncAtMillis) {
        return AutoMinerResult(
            state = state.forDay(currentDayKey),
            earnedTaps = 0,
            syncedAtMillis = nowMillis,
        )
    }

    val intervals = (nowMillis - lastSyncAtMillis) / AUTO_MINER_INTERVAL_MILLIS
    val earned = intervals * AUTO_MINER_TAPS_PER_INTERVAL
    val currentDayState = state.forDay(currentDayKey)
    if (earned <= 0) {
        return AutoMinerResult(
            state = currentDayState,
            earnedTaps = 0,
            syncedAtMillis = lastSyncAtMillis,
        )
    }
    val todayIntervals = ((nowMillis - maxOf(lastSyncAtMillis, currentDayStartMillis)) /
        AUTO_MINER_INTERVAL_MILLIS).coerceAtLeast(0)
    val todayEarned = todayIntervals * AUTO_MINER_TAPS_PER_INTERVAL

    return AutoMinerResult(
        state = currentDayState.copy(
            lifetimeCount = state.lifetimeCount + earned,
            todayCount = currentDayState.todayCount + todayEarned,
        ),
        earnedTaps = earned,
        syncedAtMillis = lastSyncAtMillis + (intervals * AUTO_MINER_INTERVAL_MILLIS),
    )
}

fun applyAutoMinerCatchUpIfActive(
    state: TapState,
    autoMinerActive: Boolean,
    lastSyncAtMillis: Long,
    nowMillis: Long,
    currentDayKey: String = istDayKeyAt(nowMillis),
    currentDayStartMillis: Long = startOfIstDayMillis(nowMillis),
): AutoMinerResult =
    if (autoMinerActive) {
        applyAutoMinerCatchUp(
            state = state,
            lastSyncAtMillis = lastSyncAtMillis,
            nowMillis = nowMillis,
            currentDayKey = currentDayKey,
            currentDayStartMillis = currentDayStartMillis,
        )
    } else {
        AutoMinerResult(
            state = state.forDay(currentDayKey),
            earnedTaps = 0,
            syncedAtMillis = lastSyncAtMillis,
        )
    }
