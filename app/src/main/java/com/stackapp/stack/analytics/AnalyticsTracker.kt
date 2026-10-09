package com.stackapp.stack.analytics

interface AnalyticsTracker {
    fun track(event: AnalyticsEvent, params: Map<String, Any> = emptyMap())
}

enum class AnalyticsEvent(val eventName: String) {
    AppOpened("app_opened"),
    TapRecorded("tap_recorded"),
    TapResonanceTriggered("tap_resonance_triggered"),
    TapMilestoneReached("tap_milestone_reached"),
    LeaderboardOpened("leaderboard_opened"),
    LeaderboardRankSeen("leaderboard_rank_seen"),
    DisplayNameCreated("display_name_created"),
    SubscriptionOfferShown("subscription_offer_shown"),
    SubscriptionReminderShown("subscription_reminder_shown"),
    SubscriptionBillingOpened("subscription_billing_opened"),
    SubscriptionStarted("subscription_started"),
    SubscriptionCancelOpened("subscription_cancel_opened"),
    SubscriptionExpired("subscription_expired"),
    AutoMinerStarted("auto_miner_started"),
    AutoMinerEarningsRevealed("auto_miner_earnings_revealed"),
}

class LocalAnalyticsTracker : AnalyticsTracker {
    override fun track(event: AnalyticsEvent, params: Map<String, Any>) = Unit
}

fun shouldTrackTap(count: Long): Boolean =
    count <= 10 || count % 100L == 0L

fun tapMilestone(count: Long): Long? =
    when (count) {
        50L, 100L, 250L, 500L, 1_000L, 2_500L, 5_000L, 10_000L,
        25_000L, 50_000L, 100_000L -> count
        else -> null
    }

fun shouldTrackSubscriptionExpired(previousActive: Boolean, refreshedActive: Boolean): Boolean =
    previousActive && !refreshedActive
