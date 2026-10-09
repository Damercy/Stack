package com.stackapp.stack.tap

import com.stackapp.stack.leaderboard.todayIstKey

data class TapState(
    val lifetimeCount: Long = 0,
    val todayCount: Long = 0,
    val todayDayKey: String = todayIstKey(),
)

class TapEngine(initialState: TapState = TapState()) {
    var state: TapState = initialState
        private set

    fun recordTap(dayKey: String = todayIstKey()): TapState {
        val currentState = state.forDay(dayKey)
        state = currentState.copy(
            lifetimeCount = state.lifetimeCount + 1,
            todayCount = currentState.todayCount + 1,
        )
        return state
    }

    fun replaceState(value: TapState) {
        state = value
    }
}

fun TapState.forDay(dayKey: String = todayIstKey()): TapState =
    if (todayDayKey == dayKey) {
        this
    } else {
        copy(todayCount = 0, todayDayKey = dayKey)
    }
