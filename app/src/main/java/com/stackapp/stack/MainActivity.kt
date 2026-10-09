package com.stackapp.stack

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.stackapp.stack.analytics.AnalyticsEvent
import com.stackapp.stack.analytics.createAnalyticsTracker
import com.stackapp.stack.autominer.AutoMinerWorker
import com.stackapp.stack.leaderboard.createLeaderboardRepository
import com.stackapp.stack.leaderboard.LeaderboardCacheStore
import com.stackapp.stack.monetization.EXTRA_SHOW_AUTO_MINER_OFFER
import com.stackapp.stack.monetization.SubscriptionReminder
import com.stackapp.stack.monetization.createBillingRepository
import com.stackapp.stack.security.installFirebaseAppCheckIfAvailable
import com.stackapp.stack.tap.AndroidAudioEngine
import com.stackapp.stack.tap.AndroidHapticEngine
import com.stackapp.stack.tap.RoomTapStore
import com.stackapp.stack.tap.TapEngine
import com.stackapp.stack.ui.StackAtelierApp
import com.stackapp.stack.ui.StackAdaptiveFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var audioEngine: AndroidAudioEngine? = null
    private var subscriptionReminder: SubscriptionReminder? = null
    private var pendingReminderLifetimeCount: Long = 0
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                subscriptionReminder?.showAutoMinerOffer(pendingReminderLifetimeCount)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            show(WindowInsetsCompat.Type.systemBars())
        }
        if (BuildConfig.USE_FIREBASE) installFirebaseAppCheckIfAvailable(this)

        val tapStore = RoomTapStore(this)
        val storedState = tapStore.load()
        val nowMillis = System.currentTimeMillis()
        val subscriptionReminder = SubscriptionReminder(this).also {
            this.subscriptionReminder = it
            it.recordFirstOpenIfNeeded(nowMillis)
        }
        val analyticsTracker = createAnalyticsTracker(this)
        analyticsTracker.track(
            AnalyticsEvent.AppOpened,
            mapOf(
                "lifetime_count" to storedState.tapState.lifetimeCount,
                "today_count" to storedState.tapState.todayCount,
                "auto_miner_active" to storedState.autoMinerActive,
            ),
        )
        val launchAutoMinerResult = tapStore.applyAutoMiner(nowMillis)
        if (launchAutoMinerResult.earnedTaps > 0) {
            analyticsTracker.track(
                AnalyticsEvent.AutoMinerEarningsRevealed,
                mapOf("earned_taps" to launchAutoMinerResult.earnedTaps),
            )
        }
        maybeShowSubscriptionReminder(
            subscriptionReminder = subscriptionReminder,
            analyticsTracker = analyticsTracker,
            lifetimeCount = launchAutoMinerResult.state.lifetimeCount,
            autoMinerActive = storedState.autoMinerActive,
            nowMillis = nowMillis,
        )
        val billingRepository = createBillingRepository(
            context = this,
            isAutoMinerActive = { tapStore.load().autoMinerActive },
            setAutoMinerActive = { active ->
                val current = tapStore.load()
                tapStore.saveAutoMinerActive(active)
                if (active && (!current.autoMinerActive || current.lastAutoMinerSyncAt <= 0)) {
                    tapStore.saveLastAutoMinerSyncAt(System.currentTimeMillis())
                }
                AutoMinerWorker.sync(this, active)
            },
        )
        AutoMinerWorker.sync(this, storedState.autoMinerActive)
        val audioEngine = AndroidAudioEngine(this).also { this.audioEngine = it }
        val soundPreferences = getSharedPreferences("stack_sound", MODE_PRIVATE)
        val initialSoundPaletteId = soundPreferences.getString("selected_palette", "crystal") ?: "crystal"
        soundPreferences.edit().remove("selected_impact").apply()
        val initialMaterialId = soundPreferences.getString("selected_material", "paper") ?: "paper"
        val initialThemeId = soundPreferences.getString("selected_theme", "light") ?: "light"

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                StackAdaptiveFrame {
                StackAtelierApp(
                activity = this,
                tapEngine = TapEngine(launchAutoMinerResult.state),
                deviceId = storedState.deviceId,
                initialDisplayName = storedState.displayName,
                initialCountryCode = storedState.countryCode,
                initialOnboardingComplete = storedState.onboardingComplete,
                initialAutoMinerActive = storedState.autoMinerActive,
                initialAwayEarnings = launchAutoMinerResult.earnedTaps,
                initialShowSubscriptionOffer = intent.getBooleanExtra(EXTRA_SHOW_AUTO_MINER_OFFER, false),
                initialSoundPaletteId = initialSoundPaletteId,
                initialMaterialId = initialMaterialId,
                initialThemeId = initialThemeId,
                leaderboardRepository = createLeaderboardRepository(this),
                leaderboardCacheStore = LeaderboardCacheStore(this),
                billingRepository = billingRepository,
                analyticsTracker = analyticsTracker,
                hapticEngine = AndroidHapticEngine(this),
                audioEngine = audioEngine,
                onCommitTap = { withContext(Dispatchers.IO) { tapStore.recordTap() } },
                onDisplayNameChanged = tapStore::saveDisplayName,
                onCountryCodeChanged = tapStore::saveCountryCode,
                onOnboardingCompleteChanged = tapStore::saveOnboardingComplete,
                onSoundPaletteChanged = { paletteId ->
                    soundPreferences.edit().putString("selected_palette", paletteId).apply()
                },
                onMaterialChanged = { materialId ->
                    soundPreferences.edit().putString("selected_material", materialId).apply()
                },
                onThemeChanged = { themeId ->
                    soundPreferences.edit().putString("selected_theme", themeId).apply()
                },
                onAutoMinerActiveChanged = { active ->
                    val current = tapStore.load()
                    tapStore.saveAutoMinerActive(active)
                    if (active && (!current.autoMinerActive || current.lastAutoMinerSyncAt <= 0)) {
                        tapStore.saveLastAutoMinerSyncAt(System.currentTimeMillis())
                    }
                    AutoMinerWorker.sync(this, active)
                },
                )
                }
            }
        }
    }

    override fun onDestroy() {
        audioEngine?.release()
        audioEngine = null
        super.onDestroy()
    }

    private fun maybeShowSubscriptionReminder(
        subscriptionReminder: SubscriptionReminder,
        analyticsTracker: com.stackapp.stack.analytics.AnalyticsTracker,
        lifetimeCount: Long,
        autoMinerActive: Boolean,
        nowMillis: Long,
    ) {
        if (!BuildConfig.PAYMENTS_ENABLED) return
        val reason = subscriptionReminder.eligibleReason(
            lifetimeCount = lifetimeCount,
            autoMinerActive = autoMinerActive,
            nowMillis = nowMillis,
        ) ?: return

        pendingReminderLifetimeCount = lifetimeCount
        if (subscriptionReminder.hasNotificationPermission()) {
            subscriptionReminder.showAutoMinerOffer(lifetimeCount)
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        analyticsTracker.track(
            AnalyticsEvent.SubscriptionReminderShown,
            mapOf(
                "lifetime_count" to lifetimeCount,
                "reason" to reason,
            ),
        )
    }

}
