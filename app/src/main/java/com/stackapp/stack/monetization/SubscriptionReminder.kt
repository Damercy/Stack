package com.stackapp.stack.monetization

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.stackapp.stack.MainActivity
import com.stackapp.stack.R
import java.text.NumberFormat
import java.util.Locale

const val AUTO_MINER_REMINDER_DAYS = 14L
const val AUTO_MINER_REMINDER_TAP_THRESHOLD = 25_000L
const val AUTO_MINER_REMINDER_MILLIS = AUTO_MINER_REMINDER_DAYS * 24L * 60L * 60L * 1000L
const val EXTRA_SHOW_AUTO_MINER_OFFER = "com.stackapp.stack.SHOW_AUTO_MINER_OFFER"

data class SubscriptionReminderEligibility(
    val eligible: Boolean,
    val reason: String? = null,
)

fun autoMinerReminderEligibility(
    lifetimeCount: Long,
    firstOpenAtMillis: Long,
    nowMillis: Long,
    autoMinerActive: Boolean,
    notificationAlreadyShown: Boolean,
): SubscriptionReminderEligibility {
    if (autoMinerActive || notificationAlreadyShown || firstOpenAtMillis <= 0) {
        return SubscriptionReminderEligibility(eligible = false)
    }

    if (lifetimeCount >= AUTO_MINER_REMINDER_TAP_THRESHOLD) {
        return SubscriptionReminderEligibility(eligible = true, reason = "tap_threshold")
    }

    if (nowMillis - firstOpenAtMillis >= AUTO_MINER_REMINDER_MILLIS) {
        return SubscriptionReminderEligibility(eligible = true, reason = "day_14")
    }

    return SubscriptionReminderEligibility(eligible = false)
}

class SubscriptionReminder(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun recordFirstOpenIfNeeded(nowMillis: Long) {
        if (preferences.getLong(KEY_FIRST_OPEN_AT, 0) <= 0) {
            preferences.edit()
                .putLong(KEY_FIRST_OPEN_AT, nowMillis)
                .apply()
        }
    }

    fun eligibleReason(
        lifetimeCount: Long,
        autoMinerActive: Boolean,
        nowMillis: Long,
    ): String? {
        val eligibility = autoMinerReminderEligibility(
            lifetimeCount = lifetimeCount,
            firstOpenAtMillis = preferences.getLong(KEY_FIRST_OPEN_AT, 0),
            nowMillis = nowMillis,
            autoMinerActive = autoMinerActive,
            notificationAlreadyShown = preferences.getBoolean(KEY_NOTIFICATION_SHOWN, false),
        )
        return eligibility.reason.takeIf { eligibility.eligible }
    }

    fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun showAutoMinerOffer(lifetimeCount: Long) {
        createChannel()

        val intent = Intent(appContext, MainActivity::class.java).apply {
            putExtra(EXTRA_SHOW_AUTO_MINER_OFFER, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val formattedCount = NumberFormat.getIntegerInstance(Locale.US).format(lifetimeCount)
        val notification = android.app.Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Stack Auto-Miner")
            .setContentText("You've tapped $formattedCount times. Stack can tap while you sleep.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        appContext.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
        preferences.edit()
            .putBoolean(KEY_NOTIFICATION_SHOWN, true)
            .apply()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Auto-Miner offers",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Auto-Miner subscription reminders"
        }
        appContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private companion object {
        const val PREFERENCES_NAME = "subscription_reminder"
        const val KEY_FIRST_OPEN_AT = "first_open_at"
        const val KEY_NOTIFICATION_SHOWN = "notification_shown"
        const val CHANNEL_ID = "auto_miner_offers"
        const val NOTIFICATION_ID = 49
    }
}
