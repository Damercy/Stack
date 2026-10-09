package com.stackapp.stack.monetization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionReminderTest {
    @Test
    fun reminderIsEligibleAfterTapThreshold() {
        val result = autoMinerReminderEligibility(
            lifetimeCount = AUTO_MINER_REMINDER_TAP_THRESHOLD,
            firstOpenAtMillis = 1_000,
            nowMillis = 2_000,
            autoMinerActive = false,
            notificationAlreadyShown = false,
        )

        assertTrue(result.eligible)
        assertEquals("tap_threshold", result.reason)
    }

    @Test
    fun reminderIsEligibleAfterFourteenDays() {
        val result = autoMinerReminderEligibility(
            lifetimeCount = 10,
            firstOpenAtMillis = 1_000,
            nowMillis = 1_000 + AUTO_MINER_REMINDER_MILLIS,
            autoMinerActive = false,
            notificationAlreadyShown = false,
        )

        assertTrue(result.eligible)
        assertEquals("day_14", result.reason)
    }

    @Test
    fun reminderDoesNotShowForActiveSubscribers() {
        val result = autoMinerReminderEligibility(
            lifetimeCount = AUTO_MINER_REMINDER_TAP_THRESHOLD,
            firstOpenAtMillis = 1_000,
            nowMillis = 2_000,
            autoMinerActive = true,
            notificationAlreadyShown = false,
        )

        assertFalse(result.eligible)
    }

    @Test
    fun reminderDoesNotRepeatAfterShown() {
        val result = autoMinerReminderEligibility(
            lifetimeCount = AUTO_MINER_REMINDER_TAP_THRESHOLD,
            firstOpenAtMillis = 1_000,
            nowMillis = 2_000,
            autoMinerActive = false,
            notificationAlreadyShown = true,
        )

        assertFalse(result.eligible)
    }
}
