package com.stackapp.stack.analytics

import android.content.Context
import com.stackapp.stack.BuildConfig
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics

class FirebaseAnalyticsTracker(
    private val firebaseAnalytics: FirebaseAnalytics,
) : AnalyticsTracker {
    override fun track(event: AnalyticsEvent, params: Map<String, Any>) {
        firebaseAnalytics.logEvent(event.eventName, params.toBundle())
    }
}

fun createAnalyticsTracker(context: Context): AnalyticsTracker =
    if (!BuildConfig.USE_FIREBASE || FirebaseApp.initializeApp(context) == null) {
        LocalAnalyticsTracker()
    } else {
        FirebaseAnalyticsTracker(FirebaseAnalytics.getInstance(context))
    }

private fun Map<String, Any>.toBundle(): Bundle {
    val bundle = Bundle()
    forEach { (key, value) ->
        when (value) {
            is String -> bundle.putString(key, value)
            is Int -> bundle.putInt(key, value)
            is Long -> bundle.putLong(key, value)
            is Double -> bundle.putDouble(key, value)
            is Float -> bundle.putFloat(key, value)
            is Boolean -> bundle.putBoolean(key, value)
        }
    }
    return bundle
}

