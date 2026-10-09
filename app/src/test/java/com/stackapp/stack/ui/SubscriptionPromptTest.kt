package com.stackapp.stack.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionPromptTest {
    @Test
    fun promptShowsForActiveSubscribers() {
        assertTrue(
            shouldShowSubscriptionPrompt(
                lifetimeCount = 0,
                autoMinerActive = true,
                forceShowSubscriptionOffer = false,
            ),
        )
    }

    @Test
    fun promptShowsAfterLifetimeHabitThreshold() {
        assertTrue(
            shouldShowSubscriptionPrompt(
                lifetimeCount = 25_000,
                autoMinerActive = false,
                forceShowSubscriptionOffer = false,
            ),
        )
    }

    @Test
    fun promptShowsWhenOpenedFromReminderNotification() {
        assertTrue(
            shouldShowSubscriptionPrompt(
                lifetimeCount = 100,
                autoMinerActive = false,
                forceShowSubscriptionOffer = true,
            ),
        )
    }

    @Test
    fun promptStaysHiddenBeforeHabitTrigger() {
        assertFalse(
            shouldShowSubscriptionPrompt(
                lifetimeCount = 24_999,
                autoMinerActive = false,
                forceShowSubscriptionOffer = false,
            ),
        )
    }
}
