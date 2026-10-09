package com.stackapp.stack.ui

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.stackapp.stack.analytics.AnalyticsEvent
import com.stackapp.stack.analytics.AnalyticsTracker
import com.stackapp.stack.analytics.shouldTrackSubscriptionExpired
import com.stackapp.stack.analytics.shouldTrackTap
import com.stackapp.stack.analytics.tapMilestone
import com.stackapp.stack.identity.DeviceId
import com.stackapp.stack.leaderboard.LeaderboardEntry
import com.stackapp.stack.leaderboard.LeaderboardRepository
import com.stackapp.stack.leaderboard.LeaderboardScore
import com.stackapp.stack.leaderboard.LeaderboardState
import com.stackapp.stack.leaderboard.StackProfile
import com.stackapp.stack.leaderboard.UsernameClaimResult
import com.stackapp.stack.leaderboard.cleanDisplayName
import com.stackapp.stack.leaderboard.todayIstKey
import com.stackapp.stack.monetization.AUTO_MINER_REMINDER_TAP_THRESHOLD
import com.stackapp.stack.monetization.BillingRepository
import com.stackapp.stack.monetization.EntitlementState
import com.stackapp.stack.monetization.SubscriptionOffer
import com.stackapp.stack.tap.AudioEngine
import com.stackapp.stack.tap.HapticEngine
import com.stackapp.stack.tap.TapEngine
import com.stackapp.stack.tap.TapState
import com.stackapp.stack.tap.SoundPalette
import com.stackapp.stack.tap.soundPalette
import com.stackapp.stack.tap.soundPalettes
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun StackApp(
    activity: Activity,
    tapEngine: TapEngine,
    deviceId: DeviceId,
    initialDisplayName: String?,
    initialCountryCode: String,
    initialOnboardingComplete: Boolean,
    initialAutoMinerActive: Boolean,
    initialAwayEarnings: Long,
    initialShowSubscriptionOffer: Boolean,
    initialSoundPaletteId: String,
    leaderboardRepository: LeaderboardRepository,
    billingRepository: BillingRepository,
    analyticsTracker: AnalyticsTracker,
    hapticEngine: HapticEngine,
    audioEngine: AudioEngine,
    onTapStateChanged: (TapState) -> Unit,
    onDisplayNameChanged: (String) -> Unit,
    onCountryCodeChanged: (String) -> Unit,
    onOnboardingCompleteChanged: (Boolean) -> Unit,
    onSoundPaletteChanged: (String) -> Unit,
    onAutoMinerActiveChanged: (Boolean) -> Unit,
) {
    var lifetimeCount by remember {
        mutableLongStateOf((tapEngine.state.lifetimeCount - initialAwayEarnings).coerceAtLeast(0))
    }
    var todayCount by remember { mutableLongStateOf(tapEngine.state.todayCount) }
    var displayName by remember { mutableStateOf(initialDisplayName) }
    var countryCode by remember {
        mutableStateOf(initialCountryCode.ifBlank { defaultCountryCode() })
    }
    var onboardingComplete by remember {
        mutableStateOf(initialOnboardingComplete || !initialDisplayName.isNullOrBlank())
    }
    var autoMinerActive by remember { mutableStateOf(initialAutoMinerActive) }
    var awayEarnings by remember { mutableLongStateOf(initialAwayEarnings) }
    var subscriptionOffer by remember { mutableStateOf(SubscriptionOffer()) }
    var subscriptionMessage by remember { mutableStateOf<String?>(null) }
    var selectedSoundPaletteId by remember { mutableStateOf(soundPalette(initialSoundPaletteId).id) }
    var previewSoundPaletteId by remember { mutableStateOf<String?>(null) }
    var previewTapsRemaining by remember { mutableIntStateOf(0) }
    var soundPreviewOffer by remember { mutableStateOf(false) }
    var currentScreen by remember {
        mutableStateOf(if (onboardingComplete) StackScreen.Tap else StackScreen.Restoring)
    }
    var tapPulse by remember { mutableIntStateOf(0) }
    var tapIntensity by remember { mutableStateOf(0.75f) }
    var tapReward by remember { mutableStateOf(TapReward.Standard) }
    var milestoneCelebration by remember { mutableStateOf<Long?>(null) }
    var milestonePulse by remember { mutableIntStateOf(0) }
    var showSwipeHint by remember { mutableStateOf(true) }
    var leaderboardState by remember {
        mutableStateOf(LeaderboardState(displayName, todayCount, emptyList()))
    }
    var leaderboardUiState by remember { mutableStateOf(LeaderboardUiState.Idle) }
    val dailyGuestPaletteId = remember { dailyGuestPaletteId() }
    val effectiveSoundPaletteId = previewSoundPaletteId ?: selectedSoundPaletteId

    fun refreshLeaderboard(name: String?, count: Long) {
        leaderboardUiState = LeaderboardUiState.Loading
        loadLeaderboard(
            repository = leaderboardRepository,
            deviceId = deviceId,
            displayName = name,
            countryCode = countryCode,
            todayCount = count,
            onResult = { state ->
                leaderboardState = state
                leaderboardUiState = LeaderboardUiState.Loaded
                state.currentUserRank?.let { rank ->
                    analyticsTracker.track(
                        AnalyticsEvent.LeaderboardRankSeen,
                        mapOf(
                            "rank" to rank,
                            "today_count" to state.todayCount,
                        ),
                    )
                }
            },
            onError = { leaderboardUiState = LeaderboardUiState.Error },
        )
    }

    LaunchedEffect(effectiveSoundPaletteId) {
        audioEngine.selectPalette(effectiveSoundPaletteId)
    }

    LaunchedEffect(Unit) {
        if (!onboardingComplete) {
            kotlinx.coroutines.delay(550)
            if (currentScreen == StackScreen.Restoring) currentScreen = StackScreen.Onboarding
        }
    }

    LaunchedEffect(Unit) {
        if (!onboardingComplete) {
            leaderboardRepository.loadProfile(
                deviceId = deviceId,
                onResult = { profile ->
                    if (currentScreen != StackScreen.Restoring) return@loadProfile
                    if (profile == null) {
                        currentScreen = StackScreen.Onboarding
                    } else {
                        displayName = cleanDisplayName(profile.displayName)
                        countryCode = profile.countryCode.ifBlank { defaultCountryCode() }
                        onboardingComplete = true
                        onDisplayNameChanged(displayName.orEmpty())
                        onCountryCodeChanged(countryCode)
                        onOnboardingCompleteChanged(true)
                        currentScreen = StackScreen.Tap
                    }
                },
                onError = {
                    if (currentScreen == StackScreen.Restoring) currentScreen = StackScreen.Onboarding
                },
            )
        }
    }

    LaunchedEffect(displayName, countryCode, onboardingComplete) {
        val name = displayName
        if (onboardingComplete && !name.isNullOrBlank()) {
            leaderboardRepository.claimUsername(
                deviceId = deviceId,
                profile = StackProfile(name, countryCode),
                onResult = { result ->
                    if (result == UsernameClaimResult.Taken) {
                        displayName = null
                        onboardingComplete = false
                        currentScreen = StackScreen.Onboarding
                    }
                },
            )
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3_200)
        showSwipeHint = false
    }

    LaunchedEffect(Unit) {
        if (initialAwayEarnings > 0) {
            val target = tapEngine.state.lifetimeCount
            val start = lifetimeCount
            val steps = 18
            repeat(steps) { step ->
                kotlinx.coroutines.delay(45)
                lifetimeCount = start + (((target - start) * (step + 1)) / steps)
            }
        }
        billingRepository.loadOffer(
            onResult = { subscriptionOffer = it },
            onError = { subscriptionMessage = "Auto-Miner setup pending" },
        )
        billingRepository.refreshEntitlement(
            onResult = { entitlement ->
                if (shouldTrackSubscriptionExpired(autoMinerActive, entitlement.autoMinerActive)) {
                    analyticsTracker.track(AnalyticsEvent.SubscriptionExpired)
                }
                autoMinerActive = entitlement.autoMinerActive
                onAutoMinerActiveChanged(entitlement.autoMinerActive)
            },
            onError = { subscriptionMessage = "Auto-Miner unavailable right now" },
        )
    }

    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    val pagerScope = rememberCoroutineScope()

    LaunchedEffect(pagerState, onboardingComplete) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (onboardingComplete && page == 0) {
                analyticsTracker.track(
                    AnalyticsEvent.LeaderboardOpened,
                    mapOf("today_count" to todayCount),
                )
                if (!displayName.isNullOrBlank()) {
                    refreshLeaderboard(displayName, todayCount)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(premiumBackground()),
    ) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                if (initialState == StackScreen.Onboarding && targetState == StackScreen.Tap) {
                    (fadeIn(tween(420)) + scaleIn(tween(420), initialScale = 0.96f))
                        .togetherWith(fadeOut(tween(260)) + scaleOut(tween(320), targetScale = 1.05f))
                } else if (targetState == StackScreen.Leaderboard) {
                    (slideInHorizontally(animationSpec = tween(360, easing = FastOutSlowInEasing)) { it } + fadeIn())
                        .togetherWith(
                            slideOutHorizontally(animationSpec = tween(280, easing = FastOutSlowInEasing)) { -it / 3 } +
                                fadeOut(),
                        )
                } else {
                    (slideInHorizontally(animationSpec = tween(360, easing = FastOutSlowInEasing)) { -it } + fadeIn())
                        .togetherWith(
                            slideOutHorizontally(animationSpec = tween(280, easing = FastOutSlowInEasing)) { it / 3 } +
                                fadeOut(),
                        )
                }.using(SizeTransform(clip = false))
            },
            label = "screen-transition",
        ) { screen ->
            when (screen) {
                StackScreen.Restoring -> RestoringProfileScreen()

                StackScreen.Onboarding -> PremiumOnboardingScreen(
                    initialCountryCode = countryCode,
                    onCheckUsername = { name, callback ->
                        leaderboardRepository.checkUsername(
                            displayName = name,
                            deviceId = deviceId,
                            onResult = callback,
                            onError = { callback(false) },
                        )
                    },
                    onLoadSuggestions = { candidates, callback ->
                        leaderboardRepository.availableUsernameSuggestions(
                            candidates = candidates,
                            onResult = callback,
                            onError = { callback(candidates.take(4)) },
                        )
                    },
                    onDemoTap = {
                        val state = tapEngine.recordTap()
                        lifetimeCount = state.lifetimeCount
                        todayCount = state.todayCount
                        tapPulse += 1
                        onTapStateChanged(state)
                        hapticEngine.clink(0.78f, 1)
                        audioEngine.clink(0.78f, 1, state.lifetimeCount)
                    },
                    onComplete = { name, selectedCountry, callback ->
                        val cleanName = cleanDisplayName(name)
                        leaderboardRepository.claimUsername(
                            deviceId = deviceId,
                            profile = StackProfile(cleanName, selectedCountry),
                            onResult = { result ->
                                if (result == UsernameClaimResult.Success) {
                                    displayName = cleanName
                                    countryCode = selectedCountry
                                    onboardingComplete = true
                                    onDisplayNameChanged(cleanName)
                                    onCountryCodeChanged(selectedCountry)
                                    onOnboardingCompleteChanged(true)
                                    currentScreen = StackScreen.Tap
                                }
                                callback(result)
                            },
                        )
                    },
                )

                StackScreen.Tap -> HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    key = { it },
                ) { page ->
                    when (page) {
                        0 -> LeaderboardScreen(
                            entries = leaderboardState.entries,
                            uiState = leaderboardUiState,
                            displayName = displayName,
                            todayCount = todayCount,
                            countryCode = countryCode,
                            onDisplayNameSaved = { name ->
                                val cleanName = cleanDisplayName(name)
                                if (cleanName.isNotBlank()) {
                                    displayName = cleanName
                                    onDisplayNameChanged(cleanName)
                                    analyticsTracker.track(
                                        AnalyticsEvent.DisplayNameCreated,
                                        mapOf("today_count" to todayCount),
                                    )
                                    refreshLeaderboard(name = cleanName, count = todayCount)
                                }
                            },
                        )

                        1 -> TapScreen(
                    lifetimeCount = lifetimeCount,
                    tapPulse = tapPulse,
                    tapIntensity = tapIntensity,
                    tapReward = tapReward,
                    showSwipeHint = showSwipeHint,
                    soundPaletteId = effectiveSoundPaletteId,
                    dailyGuestPaletteId = dailyGuestPaletteId,
                    autoMinerActive = autoMinerActive,
                    awayEarnings = awayEarnings,
                    onTap = { intensity ->
                        awayEarnings = 0
                        tapIntensity = intensity
                        val reward = chooseTapReward()
                        tapReward = reward
                        tapPulse += 1
                        val state = tapEngine.recordTap()
                        lifetimeCount = state.lifetimeCount
                        todayCount = state.todayCount
                        onTapStateChanged(state)
                        if (shouldTrackTap(state.lifetimeCount)) {
                            analyticsTracker.track(
                                AnalyticsEvent.TapRecorded,
                                mapOf(
                                    "lifetime_count" to state.lifetimeCount,
                                    "today_count" to state.todayCount,
                                ),
                            )
                        }
                        tapMilestone(state.lifetimeCount)?.let { milestone ->
                            milestoneCelebration = milestone
                            milestonePulse += 1
                            analyticsTracker.track(
                                AnalyticsEvent.TapMilestoneReached,
                                mapOf("milestone" to milestone),
                            )
                            hapticEngine.celebrate()
                            audioEngine.celebrate(milestone)
                        }
                        if (reward != TapReward.Standard) {
                            analyticsTracker.track(
                                AnalyticsEvent.TapResonanceTriggered,
                                mapOf("tier" to reward.analyticsName),
                            )
                        }
                        hapticEngine.clink(intensity, reward.accent)
                        audioEngine.clink(intensity, 0, state.lifetimeCount)
                        if (previewTapsRemaining > 0) {
                            previewTapsRemaining -= 1
                            if (previewTapsRemaining == 0) {
                                previewSoundPaletteId = null
                                soundPreviewOffer = true
                                subscriptionMessage = "Unlock every Stack sound"
                                pagerScope.launch { pagerState.animateScrollToPage(2) }
                            }
                        }
                    },
                    onPaletteSelected = { paletteId ->
                        selectedSoundPaletteId = paletteId
                        previewSoundPaletteId = null
                        previewTapsRemaining = 0
                        soundPreviewOffer = false
                        onSoundPaletteChanged(paletteId)
                    },
                    onPalettePreview = { paletteId ->
                        audioEngine.previewPalette(paletteId)
                        previewSoundPaletteId = paletteId
                        previewTapsRemaining = 8
                    },
                            onAwayEarningsShown = { awayEarnings = 0 },
                        )

                        else -> StudioScreen(
                            lifetimeCount = lifetimeCount,
                            todayCount = todayCount,
                            soundPaletteId = effectiveSoundPaletteId,
                            autoMinerActive = autoMinerActive,
                            forceShowSubscriptionOffer = initialShowSubscriptionOffer || soundPreviewOffer,
                            soundPreviewOffer = soundPreviewOffer,
                            subscriptionOffer = subscriptionOffer,
                            subscriptionMessage = subscriptionMessage,
                            analyticsTracker = analyticsTracker,
                            onReturnToStack = {
                                pagerScope.launch { pagerState.animateScrollToPage(1) }
                            },
                            onSubscribe = {
                                analyticsTracker.track(
                                    AnalyticsEvent.SubscriptionBillingOpened,
                                    mapOf("today_count" to todayCount),
                                )
                                billingRepository.launchPurchase(
                                    activity = activity,
                                    onResult = { entitlement ->
                                        autoMinerActive = entitlement.autoMinerActive
                                        onAutoMinerActiveChanged(entitlement.autoMinerActive)
                                        subscriptionMessage = if (entitlement.autoMinerActive) {
                                            analyticsTracker.track(
                                                AnalyticsEvent.SubscriptionStarted,
                                                mapOf("today_count" to todayCount),
                                            )
                                            analyticsTracker.track(AnalyticsEvent.AutoMinerStarted)
                                            "Auto-Miner active"
                                        } else {
                                            "Auto-Miner not active"
                                        }
                                    },
                                    onError = { error ->
                                        subscriptionMessage = error.message ?: "Subscription unavailable"
                                    },
                                )
                            },
                            onManageSubscription = {
                                analyticsTracker.track(AnalyticsEvent.SubscriptionCancelOpened)
                                billingRepository.openSubscriptionManagement(
                                    activity = activity,
                                    onResult = { entitlement ->
                                        autoMinerActive = entitlement.autoMinerActive
                                        onAutoMinerActiveChanged(entitlement.autoMinerActive)
                                        subscriptionMessage = if (entitlement.autoMinerActive) {
                                            "Manage subscription opened"
                                        } else {
                                            "Auto-Miner inactive"
                                        }
                                    },
                                    onError = { error ->
                                        subscriptionMessage = error.message ?: "Subscription management unavailable"
                                    },
                                )
                            },
                        )
                    }
                }

                StackScreen.Leaderboard -> LeaderboardScreen(
                    entries = leaderboardState.entries,
                    uiState = leaderboardUiState,
                    displayName = displayName,
                    todayCount = todayCount,
                    countryCode = countryCode,
                    onDisplayNameSaved = { name ->
                        val cleanName = cleanDisplayName(name)
                        if (cleanName.isNotBlank()) {
                            displayName = cleanName
                            onDisplayNameChanged(cleanName)
                            analyticsTracker.track(
                                AnalyticsEvent.DisplayNameCreated,
                                mapOf("today_count" to todayCount),
                            )
                            refreshLeaderboard(name = cleanName, count = todayCount)
                        }
                    },
                )
            }
        }
        MilestoneCelebration(
            milestone = milestoneCelebration,
            trigger = milestonePulse,
            onFinished = { completedMilestone ->
                if (milestoneCelebration == completedMilestone) {
                    milestoneCelebration = null
                }
            },
        )
    }
}

@Composable
private fun RestoringProfileScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(
                text = "STACK",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                ),
            )
            Spacer(modifier = Modifier.height(12.dp))
            BasicText(
                text = "restoring your stack",
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.38f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

@Composable
private fun PremiumOnboardingScreen(
    initialCountryCode: String,
    onCheckUsername: (String, (Boolean) -> Unit) -> Unit,
    onLoadSuggestions: (List<String>, (List<String>) -> Unit) -> Unit,
    onDemoTap: () -> Unit,
    onComplete: (String, String, (UsernameClaimResult) -> Unit) -> Unit,
) {
    var stage by remember { mutableStateOf(OnboardingStage.Tap) }
    var demoTaps by remember { mutableIntStateOf(0) }
    var displayName by remember { mutableStateOf("") }
    var countryCode by remember { mutableStateOf(initialCountryCode.ifBlank { defaultCountryCode() }) }
    var choosingCountry by remember { mutableStateOf(false) }
    var usernameStatus by remember { mutableStateOf(UsernameStatus.Idle) }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    var claiming by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(demoTaps) {
        if (demoTaps >= 3 && stage == OnboardingStage.Tap) {
            kotlinx.coroutines.delay(260)
            stage = OnboardingStage.Identity
        }
    }

    LaunchedEffect(stage) {
        if (stage == OnboardingStage.Identity) {
            onLoadSuggestions(usernameCandidates()) { suggestions = it }
            kotlinx.coroutines.delay(340)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(displayName) {
        val candidate = cleanDisplayName(displayName)
        if (stage != OnboardingStage.Identity || candidate.length < 3) {
            usernameStatus = UsernameStatus.Idle
            return@LaunchedEffect
        }
        usernameStatus = UsernameStatus.Checking
        kotlinx.coroutines.delay(350)
        onCheckUsername(candidate) { available ->
            usernameStatus = if (available) UsernameStatus.Available else UsernameStatus.Taken
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                (slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(320)))
                    .togetherWith(slideOutVertically(tween(300)) { -it / 5 } + fadeOut(tween(220)))
                    .using(SizeTransform(clip = false))
            },
            label = "onboarding-stage",
        ) { currentStage ->
            when (currentStage) {
                OnboardingStage.Tap -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            if (demoTaps < 3) {
                                demoTaps += 1
                                onDemoTap()
                            }
                        }
                        .padding(horizontal = 28.dp, vertical = 42.dp),
                ) {
                    BasicText(
                        modifier = Modifier.align(Alignment.TopCenter),
                        text = "STACK",
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BasicText(
                            text = if (demoTaps == 0) "Make your first sound" else "Keep building",
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 31.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            ),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        BasicText(
                            text = "Tap anywhere",
                            style = TextStyle(
                                color = Color(0xFFCDEEFF).copy(alpha = 0.70f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                        Spacer(modifier = Modifier.height(36.dp))
                        OnboardingStack(activeLayers = demoTaps)
                        Spacer(modifier = Modifier.height(30.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(3) { index ->
                                Box(
                                    modifier = Modifier
                                        .size(if (index < demoTaps) 7.dp else 5.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(
                                            if (index < demoTaps) Color(0xFFCDEEFF)
                                            else Color.White.copy(alpha = 0.18f),
                                        ),
                                )
                            }
                        }
                    }
                    BasicText(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        text = if (demoTaps == 0) "One gesture. Infinite stacks." else "${3 - demoTaps} to go",
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.34f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }

                OnboardingStage.Identity -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BasicText(
                        text = "Your stack is alive.",
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    BasicText(
                        text = "Name it before you climb.",
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.44f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    Spacer(modifier = Modifier.height(22.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassSurface(RoundedCornerShape(8.dp))
                            .padding(16.dp),
                    ) {
                        BasicText(
                            text = "Display name",
                            style = TextStyle(color = Color.White.copy(alpha = 0.60f), fontSize = 12.sp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        BasicTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        when (usernameStatus) {
                                            UsernameStatus.Available -> Color(0xFF9FE2C1).copy(alpha = 0.65f)
                                            UsernameStatus.Taken -> Color(0xFFFFC7B8).copy(alpha = 0.65f)
                                            else -> Color.White.copy(alpha = 0.12f)
                                        },
                                    ),
                                    RoundedCornerShape(14.dp),
                                )
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            value = displayName,
                            onValueChange = {
                                displayName = cleanDisplayName(it)
                                claiming = false
                            },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                            decorationBox = { inner ->
                                if (displayName.isBlank()) {
                                    BasicText(
                                        text = "taptempo",
                                        style = TextStyle(color = Color.White.copy(alpha = 0.28f), fontSize = 18.sp),
                                    )
                                }
                                inner()
                            },
                        )
                        Spacer(modifier = Modifier.height(7.dp))
                        BasicText(
                            modifier = Modifier.height(18.dp),
                            text = when (usernameStatus) {
                                UsernameStatus.Idle -> "At least 3 characters"
                                UsernameStatus.Checking -> "Checking availability..."
                                UsernameStatus.Available -> "Available"
                                UsernameStatus.Taken -> "Already taken. Try one below."
                            },
                            style = TextStyle(
                                color = when (usernameStatus) {
                                    UsernameStatus.Available -> Color(0xFF9FE2C1)
                                    UsernameStatus.Taken -> Color(0xFFFFC7B8)
                                    else -> Color.White.copy(alpha = 0.35f)
                                },
                                fontSize = 12.sp,
                            ),
                        )
                        if (suggestions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(9.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                suggestions.take(3).forEach { suggestion ->
                                    BasicText(
                                        modifier = Modifier
                                            .weight(1f)
                                            .glassSurface(RoundedCornerShape(8.dp))
                                            .clickable { displayName = suggestion }
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                        text = suggestion,
                                        style = TextStyle(
                                            color = Color.White.copy(alpha = 0.68f),
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center,
                                        ),
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(13.dp))
                        BasicText(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassSurface(RoundedCornerShape(8.dp))
                                .clickable { choosingCountry = true }
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            text = "${countryFlag(countryCode)}  ${countryName(countryCode)}",
                            style = TextStyle(color = Color.White.copy(alpha = 0.82f), fontSize = 15.sp),
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    if (usernameStatus == UsernameStatus.Available && !claiming) {
                        PremiumButton(
                            text = "Enter Stack",
                            onClick = {
                                claiming = true
                                keyboardController?.hide()
                                onComplete(displayName, countryCode) { result ->
                                    claiming = false
                                    when (result) {
                                        UsernameClaimResult.Success -> Unit
                                        UsernameClaimResult.Taken -> usernameStatus = UsernameStatus.Taken
                                        UsernameClaimResult.Error -> usernameStatus = UsernameStatus.Idle
                                    }
                                }
                            },
                        )
                    } else {
                        BasicText(
                            text = if (claiming) "Reserving your name..." else "Choose an available name",
                            style = TextStyle(color = Color.White.copy(alpha = 0.30f), fontSize = 13.sp),
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }

        if (choosingCountry) {
            CountryPicker(
                selectedCode = countryCode,
                onSelected = {
                    countryCode = it
                    choosingCountry = false
                    scope.launch {
                        kotlinx.coroutines.delay(180)
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                },
                onDismiss = { choosingCountry = false },
            )
        }
    }
}

private enum class OnboardingStage { Tap, Identity }
private enum class UsernameStatus { Idle, Checking, Available, Taken }

@Composable
private fun OnboardingScreen(
    initialCountryCode: String,
    onDemoTap: () -> Unit,
    onComplete: (String, String) -> Unit,
) {
    var demoTaps by remember { mutableIntStateOf(0) }
    var displayName by remember { mutableStateOf("") }
    var countryCode by remember { mutableStateOf(initialCountryCode.ifBlank { defaultCountryCode() }) }
    var choosingCountry by remember { mutableStateOf(false) }
    val reveal = remember { Animatable(0f) }

    LaunchedEffect(demoTaps) {
        if (demoTaps >= 3) {
            reveal.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 42.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = "STACK",
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                ),
            )
            Spacer(modifier = Modifier.weight(0.65f))

            if (demoTaps < 3) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            demoTaps += 1
                            onDemoTap()
                        }
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BasicText(
                        text = "Build your first chord",
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    BasicText(
                        text = "Tap ${3 - demoTaps} more ${if (3 - demoTaps == 1) "time" else "times"}",
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.46f),
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    Spacer(modifier = Modifier.height(34.dp))
                    OnboardingStack(activeLayers = demoTaps)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = reveal.value
                            translationY = (1f - reveal.value) * 36.dp.toPx()
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BasicText(
                        text = "Your stack has a sound.",
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 29.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BasicText(
                        text = "Complete phrases. Climb your local board.",
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.48f),
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassSurface(RoundedCornerShape(8.dp))
                            .padding(16.dp),
                    ) {
                        BasicText(
                            text = "Choose your name",
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 13.sp,
                            ),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        BasicTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(
                                    BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                    RoundedCornerShape(16.dp),
                                )
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            value = displayName,
                            onValueChange = { displayName = cleanDisplayName(it) },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                            decorationBox = { inner ->
                                if (displayName.isBlank()) {
                                    BasicText(
                                        text = "tapgod42",
                                        style = TextStyle(
                                            color = Color.White.copy(alpha = 0.28f),
                                            fontSize = 18.sp,
                                        ),
                                    )
                                }
                                inner()
                            },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        BasicText(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassSurface(RoundedCornerShape(8.dp))
                                .clickable { choosingCountry = true }
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            text = "${countryFlag(countryCode)}  ${countryName(countryCode)}",
                            style = TextStyle(color = Color.White.copy(alpha = 0.82f), fontSize = 16.sp),
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    if (displayName.isNotBlank()) {
                        PremiumButton(
                            text = "Enter Stack",
                            onClick = { onComplete(displayName, countryCode) },
                        )
                    } else {
                        BasicText(
                            text = "Name your stack to continue",
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.30f),
                                fontSize = 13.sp,
                            ),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        if (choosingCountry) {
            CountryPicker(
                selectedCode = countryCode,
                onSelected = {
                    countryCode = it
                    choosingCountry = false
                },
                onDismiss = { choosingCountry = false },
            )
        }
    }
}

@Composable
private fun OnboardingStack(activeLayers: Int) {
    Column(
        modifier = Modifier.width(220.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(3) { visualIndex ->
            val active = visualIndex >= 3 - activeLayers
            val widths = listOf(142.dp, 186.dp, 164.dp)
            Box(
                modifier = Modifier
                    .width(widths[visualIndex])
                    .height(22.dp)
                    .shadow(
                        elevation = if (active) 18.dp else 0.dp,
                        shape = RoundedCornerShape(7.dp),
                        ambientColor = Color(0xFFBDE7FF).copy(alpha = 0.24f),
                    )
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        if (active) {
                            Brush.horizontalGradient(
                                listOf(Color(0xFFECF8FF), Color(0xFFAEDFFF)),
                            )
                        } else {
                            Brush.horizontalGradient(
                                listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.03f)),
                            )
                        },
                    ),
            )
        }
    }
}

@Composable
private fun CountryPicker(
    selectedCode: String,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val countries = remember { availableCountries() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080A0F).copy(alpha = 0.98f))
            .padding(horizontal = 22.dp, vertical = 34.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                modifier = Modifier.weight(1f),
                text = "Choose country",
                style = TextStyle(color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold),
            )
            BasicText(
                modifier = Modifier
                    .glassSurface(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDismiss)
                    .padding(12.dp),
                text = "Close",
                style = TextStyle(color = Color.White.copy(alpha = 0.62f), fontSize = 14.sp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(countries, key = { it.code }) { country ->
                val selected = country.code == selectedCode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassSurface(RoundedCornerShape(8.dp))
                        .clickable { onSelected(country.code) }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        modifier = Modifier.width(40.dp),
                        text = countryFlag(country.code),
                        style = TextStyle(color = Color.White, fontSize = 20.sp),
                    )
                    BasicText(
                        text = country.name,
                        style = TextStyle(
                            color = Color.White.copy(alpha = if (selected) 0.96f else 0.74f),
                            fontSize = 16.sp,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun TapScreen(
    lifetimeCount: Long,
    tapPulse: Int,
    tapIntensity: Float,
    tapReward: TapReward,
    showSwipeHint: Boolean,
    soundPaletteId: String,
    dailyGuestPaletteId: String,
    autoMinerActive: Boolean,
    awayEarnings: Long,
    onTap: (Float) -> Unit,
    onPaletteSelected: (String) -> Unit,
    onPalettePreview: (String) -> Unit,
    onAwayEarningsShown: () -> Unit,
) {
    var showSoundPicker by remember { mutableStateOf(false) }
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(tapPulse) {
        if (tapPulse > 0) {
            pulse.snapTo(1f)
            val duration = 380 + (tapReward.accent * 90)
            pulse.animateTo(0f, animationSpec = tween(duration, easing = FastOutSlowInEasing))
        }
    }

    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = showSoundPicker,
            transitionSpec = {
                fadeIn(tween(110)).togetherWith(fadeOut(tween(110))).using(SizeTransform(clip = false))
            },
            label = "sound-library-container-transform",
        ) { pickerOpen ->
            if (pickerOpen) {
                SoundPalettePicker(
                    modifier = Modifier
                        .sharedBounds(
                            sharedContentState = rememberSharedContentState("sound-library-bounds"),
                            animatedVisibilityScope = this@AnimatedContent,
                            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
                        )
                        .fillMaxSize(),
                    selectedPaletteId = soundPaletteId,
                    dailyGuestPaletteId = dailyGuestPaletteId,
                    premiumActive = autoMinerActive,
                    onSelected = {
                        showSoundPicker = false
                        onPaletteSelected(it)
                    },
                    onPreview = {
                        showSoundPicker = false
                        onPalettePreview(it)
                    },
                    onDismiss = { showSoundPicker = false },
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics {
                            onClick(label = "Add one tap") {
                                onTap(0.75f)
                                true
                            }
                        }
                        .pointerInput(onTap) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val start = down.position
                                var maxPressure = down.pressure
                                var maxDistance = 0f
                                var released = false
                                var consumedByControl = false

                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    maxPressure = maxOf(maxPressure, change.pressure)
                                    maxDistance = maxOf(
                                        maxDistance,
                                        hypot(
                                            change.position.x - start.x,
                                            change.position.y - start.y,
                                        ),
                                    )
                                    released = !change.pressed
                                    consumedByControl = consumedByControl || change.isConsumed
                                } while (!released)

                                if (released && !consumedByControl && maxDistance < viewConfiguration.touchSlop * 1.35f) {
                                    onTap(normalizeTapPressure(maxPressure))
                                }
                            }
                        },
                ) {
                    TapCountText(
                        count = lifetimeCount,
                        tapPulse = tapPulse,
                        pulse = pulse.value,
                        intensity = tapIntensity,
                        reward = tapReward,
                        showSwipeHint = showSwipeHint,
                    )
                    BasicText(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .navigationBarsPadding()
                            .padding(end = 20.dp, bottom = 20.dp)
                            .sharedBounds(
                                sharedContentState = rememberSharedContentState("sound-library-bounds"),
                                animatedVisibilityScope = this@AnimatedContent,
                                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
                            )
                            .glassSurface(RoundedCornerShape(22.dp))
                            .clickable { showSoundPicker = true }
                            .padding(horizontal = 18.dp, vertical = 13.dp),
                        text = soundPalette(soundPaletteId).label,
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    AwayEarningsReveal(
                        earnedTaps = awayEarnings,
                        onShown = onAwayEarningsShown,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioScreen(
    lifetimeCount: Long,
    todayCount: Long,
    soundPaletteId: String,
    autoMinerActive: Boolean,
    forceShowSubscriptionOffer: Boolean,
    soundPreviewOffer: Boolean,
    subscriptionOffer: SubscriptionOffer,
    subscriptionMessage: String?,
    analyticsTracker: AnalyticsTracker,
    onReturnToStack: () -> Unit,
    onSubscribe: () -> Unit,
    onManageSubscription: () -> Unit,
) {
    val nextMilestone = nextStackMilestone(lifetimeCount)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 28.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = "Studio",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                BasicText(
                    text = "Shape how your Stack looks, sounds, and grows.",
                    style = TextStyle(color = Color.White.copy(alpha = 0.42f), fontSize = 13.sp),
                )
            }
            BasicText(
                modifier = Modifier
                    .glassSurface(RoundedCornerShape(8.dp))
                    .clickable(onClick = onReturnToStack)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                text = "Stack",
                style = TextStyle(color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp),
            )
        }

        Spacer(modifier = Modifier.height(28.dp))
        BasicText(
            text = "YOUR BUILD",
            style = TextStyle(
                color = Color.White.copy(alpha = 0.34f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassSurface(RoundedCornerShape(8.dp))
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(paletteColor(soundPaletteId).copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(7.dp)
                        .background(Color.White.copy(alpha = 0.72f), RoundedCornerShape(3.dp)),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = soundPalette(soundPaletteId).label,
                    style = TextStyle(color = Color.White.copy(alpha = 0.92f), fontSize = 17.sp),
                )
                BasicText(
                    text = "Current eight-note soundscape",
                    style = TextStyle(color = Color.White.copy(alpha = 0.38f), fontSize = 12.sp),
                )
            }
            BasicText(
                text = "${(lifetimeCount % 8L).toInt()}/8",
                style = TextStyle(color = Color(0xFFCDEEFF).copy(alpha = 0.76f), fontSize = 13.sp),
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            StudioMetric(
                label = "Today",
                value = formatCount(todayCount),
                modifier = Modifier.weight(1f),
            )
            StudioMetric(
                label = "Next milestone",
                value = nextMilestone?.let(::formatCount) ?: "Mastered",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(30.dp))
        BasicText(
            text = "PREMIUM",
            style = TextStyle(
                color = Color.White.copy(alpha = 0.34f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Spacer(modifier = Modifier.height(10.dp))
        StudioBenefit(
            title = "Build while you are away",
            detail = if (autoMinerActive) "Auto-Miner is active on this Stack." else "Auto-Miner keeps your lifetime build moving between sessions.",
        )
        StudioBenefit(
            title = "Unlock every soundscape",
            detail = "Keep all premium palettes instead of relying on the rotating daily sound.",
        )
        StudioBenefit(
            title = "More ways to shape your Stack",
            detail = "Premium visual finishes and future Studio releases are included while active.",
        )

        SubscriptionPrompt(
            lifetimeCount = lifetimeCount,
            todayCount = todayCount,
            autoMinerActive = autoMinerActive,
            forceShowSubscriptionOffer = forceShowSubscriptionOffer || !autoMinerActive,
            soundPreviewOffer = soundPreviewOffer,
            offer = subscriptionOffer,
            message = subscriptionMessage,
            analyticsTracker = analyticsTracker,
            onSubscribe = onSubscribe,
            onManageSubscription = onManageSubscription,
            modifier = Modifier.padding(top = 18.dp, bottom = 18.dp),
        )
    }
}

@Composable
private fun StudioMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 8.dp)) {
        BasicText(
            text = value,
            style = TextStyle(color = Color.White.copy(alpha = 0.90f), fontSize = 22.sp, fontWeight = FontWeight.Medium),
        )
        BasicText(
            text = label,
            style = TextStyle(color = Color.White.copy(alpha = 0.36f), fontSize = 12.sp),
        )
    }
}

@Composable
private fun StudioBenefit(title: String, detail: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(7.dp)
                .background(Color(0xFF9FE2C1), RoundedCornerShape(3.dp)),
        )
        Spacer(modifier = Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = TextStyle(color = Color.White.copy(alpha = 0.86f), fontSize = 15.sp, fontWeight = FontWeight.Medium),
            )
            Spacer(modifier = Modifier.height(3.dp))
            BasicText(
                text = detail,
                style = TextStyle(color = Color.White.copy(alpha = 0.40f), fontSize = 12.sp),
            )
        }
    }
}

@Composable
private fun TapCountText(
    count: Long,
    tapPulse: Int,
    pulse: Float,
    intensity: Float,
    reward: TapReward,
    showSwipeHint: Boolean,
) {
    val idleScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "count-idle-scale",
    )
    val scale = idleScale + (pulse * (0.025f + intensity * 0.035f + reward.accent * 0.006f))

    Box(modifier = Modifier.fillMaxSize()) {
        InfiniteStackScene(
            count = count,
            tapPulse = tapPulse,
            pulse = pulse,
            intensity = intensity,
            accentColor = reward.haloColor,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = 0.96f + (pulse * 0.04f)
                },
                text = formatCount(count),
                style = TextStyle(
                    color = Color.White,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                ),
            )
            Spacer(modifier = Modifier.height(12.dp))
            MelodyProgress(count = count)
            Spacer(modifier = Modifier.height(12.dp))
            AnimatedVisibility(
                visible = showSwipeHint,
                enter = fadeIn(tween(240)),
                exit = fadeOut(tween(900)),
            ) {
                BasicText(
                    text = "board  |  studio",
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.32f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
        if (reward != TapReward.Standard && pulse > 0.02f) {
            BasicText(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 132.dp)
                    .graphicsLayer {
                        val vanish = 1f - pulse
                        translationY = -(vanish * 54.dp.toPx())
                        scaleX = 0.78f + vanish * 0.36f
                        scaleY = 0.78f + vanish * 0.36f
                        alpha = (pulse * 1.55f).coerceIn(0f, 1f)
                    },
                text = reward.label,
                style = TextStyle(
                    color = reward.haloColor.copy(alpha = 0.92f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

@Composable
private fun InfiniteStackScene(
    count: Long,
    tapPulse: Int,
    pulse: Float,
    intensity: Float,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val ambient by rememberInfiniteTransition(label = "stack-ambient").animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "stack-ambient-alpha",
    )

    Canvas(modifier = modifier) {
        val visibleLayers = min(count.coerceAtLeast(0), 20L).toInt()
        val baseY = size.height * 0.90f
        val centerX = size.width / 2f
        val layerRise = size.height * 0.0385f
        val slabDepth = size.height * 0.026f
        val baseWidth = size.width * 0.72f
        val glowRadius = size.width * (0.56f + intensity * 0.13f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = (0.10f + pulse * 0.15f) * ambient),
                    Color(0xFF9EDFFF).copy(alpha = 0.045f * ambient),
                    Color.Transparent,
                ),
                center = androidx.compose.ui.geometry.Offset(centerX, baseY - layerRise * visibleLayers * 0.55f),
                radius = glowRadius,
            ),
            radius = glowRadius,
            center = androidx.compose.ui.geometry.Offset(centerX, baseY - layerRise * visibleLayers * 0.55f),
        )

        if (visibleLayers == 0) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.07f),
                topLeft = androidx.compose.ui.geometry.Offset(centerX - baseWidth / 2f, baseY),
                size = androidx.compose.ui.geometry.Size(baseWidth, slabDepth),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(slabDepth / 2f),
            )
            return@Canvas
        }

        repeat(visibleLayers) { layer ->
            val absoluteIndex = count - visibleLayers + layer + 1
            val newest = layer == visibleLayers - 1
            val deterministic = (((absoluteIndex * 37L) % 11L).toInt() - 5) / 5f
            val widthVariation = (((absoluteIndex * 53L) % 9L).toInt() - 4) * size.width * 0.0022f
            val slabWidth = baseWidth + widthVariation
            val xShift = deterministic * size.width * 0.012f
            val targetY = baseY - layer * layerRise
            val dropDistance = if (newest && tapPulse > 0) pulse * size.height * 0.29f else 0f
            val impactCompression = if (!newest && pulse > 0f) {
                pulse * (visibleLayers - layer).coerceAtMost(5) * size.height * 0.0015f
            } else {
                0f
            }
            val y = targetY - dropDistance + impactCompression
            val left = centerX - slabWidth / 2f + xShift
            val right = centerX + slabWidth / 2f + xShift
            val perspective = slabDepth * 0.9f
            val layerAlpha = 0.28f + (layer / visibleLayers.toFloat()) * 0.44f
            val tint = if (newest) accentColor else Color(0xFFBEEBFF)

            val front = Path().apply {
                moveTo(left, y)
                lineTo(right, y)
                lineTo(right - perspective, y + slabDepth)
                lineTo(left + perspective, y + slabDepth)
                close()
            }
            drawPath(
                path = front,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        tint.copy(alpha = layerAlpha * 0.68f),
                        Color(0xFF407493).copy(alpha = layerAlpha * 0.24f),
                    ),
                    startY = y,
                    endY = y + slabDepth,
                ),
            )

            val top = Path().apply {
                moveTo(left, y)
                lineTo(left + perspective, y - slabDepth * 0.72f)
                lineTo(right + perspective, y - slabDepth * 0.72f)
                lineTo(right, y)
                close()
            }
            drawPath(
                path = top,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = layerAlpha * 0.62f),
                        tint.copy(alpha = layerAlpha * 0.48f),
                        Color.White.copy(alpha = layerAlpha * 0.22f),
                    ),
                    startX = left,
                    endX = right,
                ),
            )
            drawLine(
                color = Color.White.copy(alpha = layerAlpha * 0.55f),
                start = androidx.compose.ui.geometry.Offset(left + perspective, y - slabDepth * 0.72f),
                end = androidx.compose.ui.geometry.Offset(right + perspective, y - slabDepth * 0.72f),
                strokeWidth = 1.2f,
            )
        }
    }
}

@Composable
private fun MelodyProgress(count: Long) {
    val completedSteps = when {
        count <= 0 -> 0
        count % 8L == 0L -> 8
        else -> (count % 8L).toInt()
    }

    Row(
        modifier = Modifier.width(126.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(8) { index ->
            val active = index < completedSteps
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(if (active) 4.dp else 2.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (active) Color(0xFFCDEEFF).copy(alpha = 0.88f)
                        else Color.White.copy(alpha = 0.16f),
                    ),
            )
        }
    }
}

@Composable
private fun MilestoneCelebration(
    milestone: Long?,
    trigger: Int,
    onFinished: (Long) -> Unit,
) {
    if (milestone == null || trigger <= 0) return
    val progress = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(950, easing = FastOutSlowInEasing))
        onFinished(milestone)
    }
    val value = progress.value
    if (value >= 1f) return
    val colors = listOf(
        Color(0xFFCDEEFF),
        Color(0xFFFFE2A8),
        Color(0xFFD8C7FF),
        Color(0xFF9FE2C1),
    )
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val originX = size.width / 2f
            val originY = size.height / 2f
            repeat(42) { index ->
                val seed = index * 47 + (milestone % 97).toInt()
                val angle = ((seed % 360) / 180f * PI).toFloat()
                val velocity = size.minDimension * (0.18f + (seed % 19) / 70f)
                val spread = value * velocity
                val x = originX + cos(angle) * spread
                val y = originY + sin(angle) * spread + size.minDimension * 0.16f * value * value
                val alpha = (1f - value).coerceIn(0f, 1f)
                drawCircle(
                    color = colors[index % colors.size].copy(alpha = alpha),
                    radius = (3f + seed % 6) * (1f - value * 0.35f),
                    center = androidx.compose.ui.geometry.Offset(x, y),
                )
            }
        }
        BasicText(
            modifier = Modifier.graphicsLayer {
                alpha = ((1f - value) * 0.14f).coerceIn(0f, 0.14f)
                scaleX = 0.82f + value * 0.34f
                scaleY = 0.82f + value * 0.34f
            },
            text = formatCount(milestone),
            style = TextStyle(
                color = Color.White,
                fontSize = 92.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun SoundPalettePicker(
    modifier: Modifier = Modifier,
    selectedPaletteId: String,
    dailyGuestPaletteId: String,
    premiumActive: Boolean,
    onSelected: (String) -> Unit,
    onPreview: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = modifier
            .background(premiumBackground())
            .background(Color(0xFF0A0D14).copy(alpha = 0.82f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 32.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = "Sound library",
                    style = TextStyle(color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
                )
                BasicText(
                    text = "Every palette builds a different phrase.",
                    style = TextStyle(color = Color.White.copy(alpha = 0.38f), fontSize = 12.sp),
                )
            }
            BasicText(
                modifier = Modifier
                    .glassSurface(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDismiss)
                    .padding(12.dp),
                text = "Close",
                style = TextStyle(color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp),
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(soundPalettes, key = { it.id }) { palette ->
                val dailyGuest = palette.id == dailyGuestPaletteId
                val unlocked = premiumActive || !palette.premium || dailyGuest
                val selected = palette.id == selectedPaletteId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassSurface(RoundedCornerShape(8.dp))
                        .clickable {
                            if (unlocked) onSelected(palette.id) else onPreview(palette.id)
                        }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(paletteColor(palette.id).copy(alpha = 0.24f)),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    BasicText(
                        modifier = Modifier.weight(1f),
                        text = palette.label,
                        style = TextStyle(
                            color = Color.White.copy(alpha = if (unlocked) 0.88f else 0.62f),
                            fontSize = 16.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                    )
                    BasicText(
                        text = when {
                            selected -> "Active"
                            dailyGuest -> "Daily"
                            unlocked -> "Select"
                            else -> "Preview"
                        },
                        style = TextStyle(
                            color = if (unlocked) Color(0xFFCDEEFF).copy(alpha = 0.72f)
                            else Color(0xFFFFE2A8).copy(alpha = 0.72f),
                            fontSize = 11.sp,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun AwayEarningsReveal(
    earnedTaps: Long,
    onShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (earnedTaps <= 0) return

    LaunchedEffect(earnedTaps) {
        kotlinx.coroutines.delay(2_800)
        onShown()
    }

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BasicText(
            modifier = Modifier
                .glassSurface(RoundedCornerShape(999.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp),
            text = "You earned ${formatCount(earnedTaps)} taps while away",
            style = TextStyle(
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun SubscriptionPrompt(
    lifetimeCount: Long,
    todayCount: Long,
    autoMinerActive: Boolean,
    forceShowSubscriptionOffer: Boolean,
    soundPreviewOffer: Boolean,
    offer: SubscriptionOffer,
    message: String?,
    analyticsTracker: AnalyticsTracker,
    onSubscribe: () -> Unit,
    onManageSubscription: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showPrompt = shouldShowSubscriptionPrompt(
        lifetimeCount = lifetimeCount,
        autoMinerActive = autoMinerActive,
        forceShowSubscriptionOffer = forceShowSubscriptionOffer,
    )
    if (!showPrompt) return

    var offerShownTracked by remember { mutableStateOf(false) }
    LaunchedEffect(showPrompt) {
        if (!offerShownTracked) {
            analyticsTracker.track(
                AnalyticsEvent.SubscriptionOfferShown,
                mapOf(
                    "today_count" to todayCount,
                    "lifetime_count" to lifetimeCount,
                    "auto_miner_active" to autoMinerActive,
                    "forced" to forceShowSubscriptionOffer,
                ),
            )
            offerShownTracked = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(8.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            text = if (autoMinerActive) {
                "Auto-Miner active"
            } else if (soundPreviewOffer) {
                "Keep every sound in your Stack"
            } else {
                "Stack taps while you sleep"
            },
            style = TextStyle(
                color = Color.White.copy(alpha = 0.86f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(modifier = Modifier.height(12.dp))
        PremiumButton(
            text = if (autoMinerActive) "Manage Auto-Miner" else "Subscribe - ${offer.priceText}",
            onClick = if (autoMinerActive) onManageSubscription else onSubscribe,
        )
        if (!message.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            BasicText(
                text = message,
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

@Composable
private fun LeaderboardScreen(
    entries: List<LeaderboardEntry>,
    uiState: LeaderboardUiState,
    displayName: String?,
    todayCount: Long,
    countryCode: String,
    onDisplayNameSaved: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 32.dp),
    ) {
        BasicText(
            text = "Today's Stack",
            style = TextStyle(
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Spacer(modifier = Modifier.height(6.dp))
        BasicText(
            text = "Higher layers belong to today's strongest rhythm.",
            style = TextStyle(color = Color.White.copy(alpha = 0.38f), fontSize = 13.sp),
        )
        Spacer(modifier = Modifier.height(20.dp))

        if (displayName.isNullOrBlank()) {
            DisplayNamePrompt(onDisplayNameSaved)
            Spacer(modifier = Modifier.height(20.dp))
        }

        when (uiState) {
            LeaderboardUiState.Loading, LeaderboardUiState.Idle -> LeaderboardShimmer(
                modifier = Modifier.weight(1f),
            )

            LeaderboardUiState.Error -> Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = "The board could not refresh.",
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.42f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    ),
                )
            }

            LeaderboardUiState.Loaded -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                items(entries, key = { "${it.rank}-${it.displayName}" }) { entry ->
                    LeaderboardRow(entry)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassSurface(RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = "${countryFlag(countryCode)}  ${displayName ?: "unnamed"}  |  " +
                    "${formatCount(todayCount)} today",
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

@Composable
private fun LeaderboardShimmer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "leaderboard-shimmer")
    val phase by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(760, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmer-alpha",
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(8) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = (index.coerceAtMost(3) * 6).dp)
                    .height(if (index < 3) 52.dp else 44.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(alpha = phase * 0.18f),
                                Color.White.copy(alpha = phase * 0.07f),
                                Color.White.copy(alpha = phase * 0.14f),
                            ),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun DisplayNamePrompt(onDisplayNameSaved: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(8.dp))
            .padding(16.dp),
    ) {
        BasicText(
            text = "Pick a display name",
            style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(modifier = Modifier.height(10.dp))
        BasicTextField(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.09f))
                .border(
                    BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                    RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            value = draft,
            onValueChange = { draft = cleanDisplayName(it) },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
            decorationBox = { innerTextField ->
                if (draft.isBlank()) {
                    BasicText(
                        text = "tapgod42",
                        style = TextStyle(color = Color.White.copy(alpha = 0.35f), fontSize = 18.sp),
                    )
                }
                innerTextField()
            },
        )
        Spacer(modifier = Modifier.height(12.dp))
        PremiumButton(
            text = "Save",
            onClick = { onDisplayNameSaved(draft) },
        )
    }
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntry) {
    val color = if (entry.isCurrentUser || entry.rank <= 3) Color.White else Color.White.copy(alpha = 0.72f)
    val weight = if (entry.isCurrentUser) FontWeight.Medium else FontWeight.Normal
    val layerColor = when (entry.rank) {
        1 -> Color(0xFFFFE2A8)
        2 -> Color(0xFFCDEEFF)
        3 -> Color(0xFFD8C7FF)
        else -> Color.White
    }
    val sideInset = when (entry.rank) {
        1 -> 0.dp
        2 -> 7.dp
        3 -> 14.dp
        else -> 18.dp
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sideInset)
            .shadow(
                elevation = if (entry.rank <= 3) (13 - entry.rank * 2).dp else 3.dp,
                shape = RoundedCornerShape(7.dp),
                ambientColor = layerColor.copy(alpha = 0.16f),
            )
            .clip(RoundedCornerShape(7.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        layerColor.copy(alpha = if (entry.isCurrentUser) 0.22f else 0.13f),
                        Color.White.copy(alpha = if (entry.isCurrentUser) 0.10f else 0.045f),
                    ),
                ),
            )
            .border(
                BorderStroke(1.dp, layerColor.copy(alpha = if (entry.rank <= 3) 0.30f else 0.10f)),
                RoundedCornerShape(7.dp),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (entry.rank <= 3) 13.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                modifier = Modifier.width(42.dp),
                text = "#${entry.rank}",
                style = TextStyle(color = layerColor.copy(alpha = 0.92f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            )
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = entry.displayName,
                    style = TextStyle(color = color, fontSize = 16.sp, fontWeight = weight),
                )
                if (entry.countryCode.isNotBlank()) {
                    BasicText(
                        text = "${countryFlag(entry.countryCode)}  ${entry.countryCode}",
                        style = TextStyle(color = color.copy(alpha = 0.48f), fontSize = 11.sp),
                    )
                }
            }
            BasicText(
                text = formatCount(entry.todayCount),
                style = TextStyle(color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

@Composable
private fun PremiumButton(
    text: String,
    onClick: () -> Unit,
) {
    BasicText(
        modifier = Modifier
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(8.dp),
                ambientColor = Color.Black.copy(alpha = 0.24f),
                spotColor = Color(0xFFBDE7FF).copy(alpha = 0.16f),
            )
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.22f),
                        Color(0xFFBDE7FF).copy(alpha = 0.10f),
                        Color.White.copy(alpha = 0.07f),
                    ),
                ),
            )
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.26f)),
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        text = text,
        style = TextStyle(
            color = Color.White.copy(alpha = 0.94f),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        ),
    )
}

private fun Modifier.glassSurface(shape: RoundedCornerShape): Modifier =
    this
        .shadow(
            elevation = 12.dp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.30f),
            spotColor = Color(0xFFBDE7FF).copy(alpha = 0.08f),
        )
        .clip(shape)
        .drawWithCache {
            val glass = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.19f),
                    Color(0xFFBDE7FF).copy(alpha = 0.075f),
                    Color(0xFF080B12).copy(alpha = 0.26f),
                ),
            )
            onDrawBehind {
                drawRect(color = Color(0xFF0B1019).copy(alpha = 0.48f))
                drawRect(brush = glass)
            }
        }
        .border(
            BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
            shape,
        )

private fun premiumBackground(): Brush =
    Brush.verticalGradient(
        colors = listOf(
            Color(0xFF07080D),
            Color(0xFF10141C),
            Color(0xFF030407),
        ),
    )

private enum class StackScreen {
    Restoring,
    Onboarding,
    Tap,
    Leaderboard,
}

private enum class LeaderboardUiState {
    Idle,
    Loading,
    Loaded,
    Error,
}

private enum class TapReward(
    val label: String,
    val analyticsName: String,
    val accent: Int,
    val haloColor: Color,
    val haloStrength: Float,
) {
    Standard("", "standard", 0, Color.White, 0f),
    Clean("Clean", "clean", 1, Color(0xFFBDE7FF), 0.10f),
    Perfect("Perfect", "perfect", 2, Color(0xFFD8C7FF), 0.20f),
    Rare("Rare resonance", "rare", 3, Color(0xFFFFE2A8), 0.34f),
}

private fun chooseTapReward(roll: Float = Random.nextFloat()): TapReward =
    when {
        roll < 0.004f -> TapReward.Rare
        roll < 0.024f -> TapReward.Perfect
        roll < 0.114f -> TapReward.Clean
        else -> TapReward.Standard
    }

private fun nextStackMilestone(count: Long): Long? =
    listOf(50L, 100L, 250L, 500L, 1_000L, 2_500L, 5_000L, 10_000L, 25_000L, 50_000L, 100_000L)
        .firstOrNull { it > count }

internal fun normalizeTapPressure(pressure: Float): Float {
    if (!pressure.isFinite() || pressure <= 0.1f) return 0.75f
    val normalized = (pressure.coerceIn(0.2f, 1f) - 0.2f) / 0.8f
    return 0.58f + (normalized * 0.42f)
}

internal fun shouldShowSubscriptionPrompt(
    lifetimeCount: Long,
    autoMinerActive: Boolean,
    forceShowSubscriptionOffer: Boolean,
): Boolean =
    autoMinerActive ||
        lifetimeCount >= AUTO_MINER_REMINDER_TAP_THRESHOLD ||
        forceShowSubscriptionOffer

private fun loadLeaderboard(
    repository: LeaderboardRepository,
    deviceId: DeviceId,
    displayName: String?,
    countryCode: String,
    todayCount: Long,
    onResult: (LeaderboardState) -> Unit,
    onError: (Throwable) -> Unit,
) {
    val cleanName = displayName?.takeIf { it.isNotBlank() } ?: "You"
    repository.upsertAndLoad(
        LeaderboardScore(
            deviceId = deviceId,
            displayName = cleanName,
            todayCount = todayCount,
            dayKey = todayIstKey(),
            countryCode = countryCode,
        ),
        onResult = onResult,
        onError = onError,
    )
}

private fun localLeaderboardStateForDisplay(
    displayName: String?,
    todayCount: Long,
): LeaderboardState =
    com.stackapp.stack.leaderboard.localLeaderboardState(displayName, todayCount)

private fun formatCount(count: Long): String =
    NumberFormat.getIntegerInstance(Locale.US).format(count)

private data class CountryChoice(val code: String, val name: String)

private fun defaultCountryCode(): String =
    Locale.getDefault().country
        .uppercase(Locale.US)
        .takeIf { it.length == 2 }
        ?: "IN"

private fun availableCountries(): List<CountryChoice> =
    Locale.getISOCountries()
        .map { code -> CountryChoice(code, countryName(code)) }
        .filter { it.name.isNotBlank() }
        .sortedBy { it.name }

private fun countryName(code: String): String {
    if (code.length != 2) return "Choose country"
    return Locale.Builder()
        .setRegion(code.uppercase(Locale.US))
        .build()
        .getDisplayCountry(Locale.getDefault())
        .ifBlank { code.uppercase(Locale.US) }
}

private fun countryFlag(code: String): String {
    if (code.length != 2 || code.any { !it.isLetter() }) return ""
    return code.uppercase(Locale.US)
        .map { character -> String(Character.toChars(0x1F1E6 + character.code - 'A'.code)) }
        .joinToString("")
}

private fun dailyGuestPaletteId(): String {
    val premium = soundPalettes.filter { it.premium }
    return premium[kotlin.math.abs(todayIstKey().hashCode()).mod(premium.size)].id
}

private fun paletteColor(id: String): Color =
    when (id) {
        "crystal" -> Color(0xFFCDEEFF)
        "felt" -> Color(0xFFD9D5CF)
        "celeste" -> Color(0xFFD8C7FF)
        "marimba" -> Color(0xFFFFD4A8)
        "kalimba" -> Color(0xFFAEE4D1)
        "pizzicato" -> Color(0xFFFFC7B8)
        "temple" -> Color(0xFFFFE2A8)
        "bloom" -> Color(0xFFB8D2FF)
        "fifth" -> Color(0xFFE2C0C0)
        "joy" -> Color(0xFFFFEDB8)
        else -> Color.White
    }

private fun usernameCandidates(): List<String> {
    val prefixes = listOf("glass", "stack", "tap", "nova", "echo", "tempo", "pulse", "chord")
    val suffixes = listOf("wave", "bloom", "spark", "sonic", "drift", "tone", "rise", "loop")
    val seed = System.nanoTime().toInt()
    return List(8) { index ->
        val prefix = prefixes[kotlin.math.abs(seed + index * 7).mod(prefixes.size)]
        val suffix = suffixes[kotlin.math.abs(seed / 3 + index * 5).mod(suffixes.size)]
        val number = kotlin.math.abs(seed + index * 31).mod(90) + 10
        if (index % 2 == 0) "$prefix$suffix" else "$prefix$number"
    }.distinct()
}
