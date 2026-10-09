package com.stackapp.stack.leaderboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaderboardCacheStoreTest {
    private val cache = LeaderboardCache(emptyList(), "2026-07-15", 1_000L)

    @Test
    fun cacheExpiresAtThirtyMinutes() {
        assertTrue(LeaderboardCacheStore.isFresh(cache, cache.dayKey, 1_000L + LeaderboardCacheStore.MAX_AGE_MILLIS - 1))
        assertFalse(LeaderboardCacheStore.isFresh(cache, cache.dayKey, 1_000L + LeaderboardCacheStore.MAX_AGE_MILLIS))
    }

    @Test
    fun dayChangeInvalidatesCache() {
        assertFalse(LeaderboardCacheStore.isFresh(cache, "2026-07-16", 1_001L))
    }

    @Test
    fun backgroundRefreshUsesSameBoundary() {
        assertFalse(LeaderboardCacheStore.requiresResumeRefresh(1_000L, 1_000L + LeaderboardCacheStore.MAX_AGE_MILLIS - 1))
        assertTrue(LeaderboardCacheStore.requiresResumeRefresh(1_000L, 1_000L + LeaderboardCacheStore.MAX_AGE_MILLIS))
    }
}
