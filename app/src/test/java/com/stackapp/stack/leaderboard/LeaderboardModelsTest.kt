package com.stackapp.stack.leaderboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaderboardModelsTest {
    @Test
    fun localLeaderboardIncludesCurrentUser() {
        val state = localLeaderboardState(displayName = "stacker", todayCount = 100)

        val currentUser = state.entries.single { it.isCurrentUser }

        assertEquals("stacker", currentUser.displayName)
        assertEquals(100, currentUser.todayCount)
    }

    @Test
    fun localLeaderboardRanksByTodayCountDescending() {
        val state = localLeaderboardState(displayName = "stacker", todayCount = 7000)

        val currentUser = state.entries.single { it.isCurrentUser }

        assertEquals(2, currentUser.rank)
        assertEquals(2, state.currentUserRank)
        assertTrue(state.entries.zipWithNext().all { (left, right) ->
            left.todayCount >= right.todayCount
        })
    }

    @Test
    fun localLeaderboardUsesFallbackNameBeforeDisplayNameIsSet() {
        val state = localLeaderboardState(displayName = null, todayCount = 50)

        val currentUser = state.entries.single { it.isCurrentUser }

        assertEquals("You", currentUser.displayName)
        assertTrue(state.needsDisplayName)
    }

    @Test
    fun cleanDisplayNameKeepsSafeLeaderboardCharacters() {
        assertEquals("tap-god_42.ok", cleanDisplayName("  tap-god_42.ok!!!  "))
    }

    @Test
    fun cleanDisplayNameLimitsLengthAndRemovesEmoji() {
        assertEquals("abcdefghijklmnopqrst", cleanDisplayName("abcdefghijklmnopqrstuv🔥"))
    }
}
