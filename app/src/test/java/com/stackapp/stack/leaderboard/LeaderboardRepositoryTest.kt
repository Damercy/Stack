package com.stackapp.stack.leaderboard

import com.stackapp.stack.identity.DeviceId
import org.junit.Assert.assertEquals
import org.junit.Test

class LeaderboardRepositoryTest {
    @Test
    fun leaderboardScoreSerializesToFirestoreDocumentShape() {
        val score = LeaderboardScore(
            deviceId = DeviceId("device-123"),
            displayName = "tapgod42",
            todayCount = 3847,
            dayKey = "2026-07-08",
        )

        val document = score.toFirestoreDocument()

        assertEquals("device-123", document["device_id"])
        assertEquals("tapgod42", document["display_name"])
        assertEquals(3847L, document["today_count"])
        assertEquals("2026-07-08", document["day_key"])
    }

    @Test
    fun localRepositoryReturnsRankedStateForSubmittedScore() {
        val repository = LocalLeaderboardRepository()
        var state: LeaderboardState? = null
        var error: Throwable? = null

        repository.upsertAndLoad(
            LeaderboardScore(
                deviceId = DeviceId("device-123"),
                displayName = "stacker",
                todayCount = 7000,
                dayKey = "2026-07-08",
            ),
            onResult = { state = it },
            onError = { error = it },
        )

        assertEquals(null, error)
        assertEquals(2, state?.entries?.single { it.isCurrentUser }?.rank)
    }
}
