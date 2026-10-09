package com.stackapp.stack.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsTrackerTest {
    @Test
    fun shouldTrackTapTracksEarlyTapsAndHundreds() {
        assertTrue(shouldTrackTap(1))
        assertTrue(shouldTrackTap(10))
        assertTrue(shouldTrackTap(100))
        assertTrue(shouldTrackTap(500))
    }

    @Test
    fun shouldTrackTapSkipsMostHighVolumeTaps() {
        assertFalse(shouldTrackTap(11))
        assertFalse(shouldTrackTap(99))
        assertFalse(shouldTrackTap(101))
    }

    @Test
    fun tapMilestoneReturnsOnlyConfiguredMilestones() {
        assertEquals(50L, tapMilestone(50))
        assertEquals(25_000L, tapMilestone(25_000))
        assertEquals(null, tapMilestone(25_001))
    }

    @Test
    fun subscriptionExpiredTracksOnlyActiveToInactiveTransition() {
        assertTrue(shouldTrackSubscriptionExpired(previousActive = true, refreshedActive = false))
        assertFalse(shouldTrackSubscriptionExpired(previousActive = false, refreshedActive = false))
        assertFalse(shouldTrackSubscriptionExpired(previousActive = false, refreshedActive = true))
        assertFalse(shouldTrackSubscriptionExpired(previousActive = true, refreshedActive = true))
    }
}
