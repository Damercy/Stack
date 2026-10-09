package com.stackapp.stack.ui

import android.app.Activity
import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.waterfall
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import com.stackapp.stack.BuildConfig
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.stackapp.stack.R
import com.stackapp.stack.analytics.AnalyticsEvent
import com.stackapp.stack.analytics.AnalyticsTracker
import com.stackapp.stack.analytics.shouldTrackTap
import com.stackapp.stack.analytics.tapMilestone
import com.stackapp.stack.identity.DeviceId
import com.stackapp.stack.leaderboard.LeaderboardEntry
import com.stackapp.stack.leaderboard.LeaderboardCache
import com.stackapp.stack.leaderboard.LeaderboardCacheStore
import com.stackapp.stack.leaderboard.LeaderboardRepository
import com.stackapp.stack.leaderboard.LeaderboardScore
import com.stackapp.stack.leaderboard.LeaderboardState
import com.stackapp.stack.leaderboard.StackProfile
import com.stackapp.stack.leaderboard.UsernameClaimResult
import com.stackapp.stack.leaderboard.cleanDisplayName
import com.stackapp.stack.leaderboard.todayIstKey
import com.stackapp.stack.monetization.BillingRepository
import com.stackapp.stack.monetization.SubscriptionOffer
import com.stackapp.stack.tap.AudioEngine
import com.stackapp.stack.tap.HapticEngine
import com.stackapp.stack.tap.SoundPalette
import com.stackapp.stack.tap.TapEngine
import com.stackapp.stack.tap.TapState
import com.stackapp.stack.tap.soundPalette
import com.stackapp.stack.tap.soundPalettes
import java.text.NumberFormat
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import kotlin.math.abs
import kotlin.math.max

private val Ink: Color @Composable get() = LocalStackPalette.current.background
private val Bone: Color @Composable get() = LocalStackPalette.current.foreground
private val Mist: Color @Composable get() = LocalStackPalette.current.secondary
private val Celadon: Color @Composable get() = LocalStackPalette.current.accent
private val Brass: Color @Composable get() = LocalStackPalette.current.brass

private val uiFont = FontFamily.SansSerif
private val numberFont = FontFamily.SansSerif

private data class StackMaterial(
    val id: String,
    val name: String,
    val description: String,
    @DrawableRes val image: Int,
    val accent: Color,
    val premium: Boolean,
    val landingScale: Float,
    val liveMaterial: LiveMaterial,
)

private val stackMaterials = listOf(
    StackMaterial("paper", "Cotton Paper", "Soft, fibrous, precise", R.drawable.stack_cotton_paper, Color(0xFFD7D1C2), false, 0.985f, LiveMaterial.Paper),
    StackMaterial("stone", "River Stone", "Grounded, mineral, weighty", R.drawable.stack_river_stone, Color(0xFFAAB2A7), true, 0.965f, LiveMaterial.Stone),
    StackMaterial("library", "The Library", "Cloth, leather, quiet gravity", R.drawable.stack_library, Color(0xFFB7A181), true, 0.975f, LiveMaterial.Library),
)

private enum class StackLayoutMode { Compact, CompactLandscape, Medium, Expanded }

private data class StackLayoutSpec(
    val mode: StackLayoutMode,
    val contentMaxWidth: androidx.compose.ui.unit.Dp,
    val sceneWidthFraction: Float,
    val framingScale: Float,
) {
    val split: Boolean get() = mode == StackLayoutMode.CompactLandscape || mode == StackLayoutMode.Expanded
}

private class TapRhythmGate {
    private var lastTapAt = 0L
    private var expectedInterval = 0f

    fun shouldStack(now: Long): Boolean {
        if (lastTapAt == 0L) {
            lastTapAt = now
            return true
        }
        val interval = (now - lastTapAt).coerceAtLeast(0L).toFloat()
        lastTapAt = now
        if (interval >= 650f) {
            expectedInterval = 0f
            return true
        }
        if (interval < 75f) return false
        if (expectedInterval == 0f) {
            expectedInterval = interval
            return true
        }
        val inRhythm = abs(interval - expectedInterval) <= max(80f, expectedInterval * .42f)
        expectedInterval = if (inRhythm) expectedInterval * .72f + interval * .28f else interval
        return inRhythm
    }

    fun reset() {
        lastTapAt = 0L
        expectedInterval = 0f
    }
}

@Composable
private fun rememberStackLayoutSpec(): StackLayoutSpec {
    val availableSize = LocalStackWindowSize.current
    val mediumWidth = availableSize.width >= 600.dp
    val expandedWidth = availableSize.width >= 840.dp
    val compactHeight = availableSize.height < 480.dp && availableSize.width >= 600.dp
    return when {
        compactHeight -> StackLayoutSpec(StackLayoutMode.CompactLandscape, 720.dp, .62f, 1.22f)
        expandedWidth -> StackLayoutSpec(StackLayoutMode.Expanded, 1_120.dp, .64f, 1.08f)
        mediumWidth -> StackLayoutSpec(StackLayoutMode.Medium, 720.dp, 1f, 1.05f)
        else -> StackLayoutSpec(StackLayoutMode.Compact, 600.dp, 1f, 1f)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StackAtelierApp(
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
    initialMaterialId: String,
    initialThemeId: String,
    leaderboardRepository: LeaderboardRepository,
    leaderboardCacheStore: LeaderboardCacheStore,
    billingRepository: BillingRepository,
    analyticsTracker: AnalyticsTracker,
    hapticEngine: HapticEngine,
    audioEngine: AudioEngine,
    onCommitTap: suspend () -> TapState,
    onDisplayNameChanged: (String) -> Unit,
    onCountryCodeChanged: (String) -> Unit,
    onOnboardingCompleteChanged: (Boolean) -> Unit,
    onSoundPaletteChanged: (String) -> Unit,
    onMaterialChanged: (String) -> Unit,
    onThemeChanged: (String) -> Unit,
    onAutoMinerActiveChanged: (Boolean) -> Unit,
) {
    var count by remember { mutableLongStateOf((tapEngine.state.lifetimeCount - initialAwayEarnings).coerceAtLeast(0)) }
    var today by remember { mutableLongStateOf(tapEngine.state.todayCount) }
    var name by remember { mutableStateOf(initialDisplayName) }
    var country by remember { mutableStateOf(initialCountryCode.ifBlank(::defaultCountry)) }
    var onboarded by remember { mutableStateOf(initialOnboardingComplete || !initialDisplayName.isNullOrBlank()) }
    var restoring by remember { mutableStateOf(!onboarded) }
    var premium by remember { mutableStateOf(initialAutoMinerActive) }
    var offer by remember { mutableStateOf(SubscriptionOffer()) }
    var soundId by remember { mutableStateOf(soundPalette(initialSoundPaletteId).id) }
    var materialId by remember { mutableStateOf(stackMaterials.firstOrNull { it.id == initialMaterialId }?.id ?: "paper") }
    var theme by remember { mutableStateOf(stackTheme(initialThemeId)) }
    var showSounds by remember { mutableStateOf(false) }
    var nextLandingId by remember { mutableLongStateOf(0L) }
    var sceneState by remember {
        mutableStateOf(StackSceneReducer.restore(count, stackMaterials.first { it.id == materialId }.liveMaterial))
    }
    var reward by remember { mutableStateOf("") }
    val initialCache = remember { leaderboardCacheStore.load()?.takeIf { it.dayKey == todayIstKey() } }
    var leaderboard by remember { mutableStateOf(LeaderboardState(name, today, initialCache?.entries.orEmpty())) }
    var boardLoading by remember { mutableStateOf(initialCache == null) }
    var boardRefreshing by remember { mutableStateOf(false) }
    var boardNeedsRefresh by remember {
        mutableStateOf(!LeaderboardCacheStore.isFresh(initialCache, todayIstKey(), System.currentTimeMillis()))
    }
    var selectedPage by rememberSaveable { mutableIntStateOf(1) }
    val collectionUnlocked = premium || !BuildConfig.PAYMENTS_ENABLED
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var backgroundedAt by remember { mutableLongStateOf(0L) }
    val layoutSpec = rememberStackLayoutSpec()
    val rhythmGate = remember { TapRhythmGate() }

    fun refreshBoard() {
        if (boardRefreshing) return
        boardRefreshing = true
        boardLoading = leaderboard.entries.isEmpty()
        leaderboardRepository.upsertAndLoad(
            LeaderboardScore(deviceId, name ?: "You", today, todayIstKey(), country),
            onResult = {
                leaderboard = it
                boardLoading = false
                boardRefreshing = false
                boardNeedsRefresh = false
                leaderboardCacheStore.save(
                    LeaderboardCache(it.entries, todayIstKey(), System.currentTimeMillis()),
                )
            },
            onError = {
                boardLoading = false
                boardRefreshing = false
                scope.launch { snackbar.showSnackbar("Could not refresh leaderboard") }
            },
        )
    }

    val tapCommitMutex = remember { Mutex() }
    suspend fun commitSuccessfulContact(objectState: SceneObject, showReward: Boolean): TapState = tapCommitMutex.withLock {
        val tapState = onCommitTap()
        tapEngine.replaceState(tapState)
        count = tapState.lifetimeCount
        today = tapState.todayCount
        boardNeedsRefresh = true
        if (showReward) {
            reward = when (kotlin.random.Random.nextFloat()) {
                in 0f..0.012f -> "Perfect"
                in 0.012f..0.10f -> "Clean"
                else -> ""
            }
        }
        audioEngine.playTone(objectState.intensity, 0, tapState.lifetimeCount)
        hapticEngine.clink(objectState.intensity, if (showReward && reward == "Perfect") 2 else 0)
        tapMilestone(tapState.lifetimeCount)?.let {
            hapticEngine.celebrate()
            audioEngine.celebrate(it)
        }
        if (shouldTrackTap(tapState.lifetimeCount)) {
            analyticsTracker.track(
                AnalyticsEvent.TapRecorded,
                mapOf("lifetime_count" to tapState.lifetimeCount),
            )
        }
        tapState
    }

    LaunchedEffect(soundId) { audioEngine.selectTone(soundId) }
    LaunchedEffect(reward) {
        if (reward.isNotBlank()) {
            delay(900)
            reward = ""
        }
    }
    LaunchedEffect(Unit) {
        billingRepository.loadOffer(onResult = { offer = it }, onError = {})
        billingRepository.refreshEntitlement(
            onResult = { premium = it.autoMinerActive; onAutoMinerActiveChanged(premium) },
            onError = {},
        )
        if (!onboarded) {
            var resolved = false
            leaderboardRepository.loadProfile(
                deviceId,
                onResult = { profile ->
                    resolved = true
                    if (profile != null) {
                        name = profile.displayName
                        country = profile.countryCode.ifBlank(::defaultCountry)
                        onboarded = true
                        onDisplayNameChanged(profile.displayName)
                        onCountryCodeChanged(country)
                        onOnboardingCompleteChanged(true)
                    }
                    restoring = false
                },
                onError = { resolved = true; restoring = false },
            )
            delay(650)
            if (!resolved) restoring = false
        }
        if (initialAwayEarnings > 0) {
            val target = tapEngine.state.lifetimeCount
            repeat(16) { step -> delay(45); count += (target - count) / (16 - step) }
            sceneState = StackSceneReducer.cancelAndRestore(sceneState, count)
        }
    }
    LaunchedEffect(onboarded) {
        if (onboarded && initialCache == null) refreshBoard()
    }
    LaunchedEffect(Unit) {
        snapshotFlow { selectedPage }.collect { page ->
            if (page != 1) {
                rhythmGate.reset()
                sceneState = StackSceneReducer.cancelAndRestore(sceneState, count)
            }
            if (page == 0 && onboarded) {
                analyticsTracker.track(AnalyticsEvent.LeaderboardOpened)
                if (leaderboardCacheStore.load()?.dayKey != todayIstKey()) boardNeedsRefresh = true
                if (boardNeedsRefresh) refreshBoard()
            }
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> backgroundedAt = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> {
                    if (LeaderboardCacheStore.requiresResumeRefresh(backgroundedAt, System.currentTimeMillis())) {
                        boardNeedsRefresh = true
                        if (selectedPage == 0 && onboarded) refreshBoard()
                    }
                    backgroundedAt = 0L
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler {
        when {
            showSounds -> showSounds = false
            onboarded && selectedPage != 1 -> selectedPage = 1
            else -> activity.finish()
        }
    }

    SideEffect {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = theme == StackTheme.Light
            isAppearanceLightNavigationBars = theme == StackTheme.Light
        }
    }
    val palette = theme.palette()
    CompositionLocalProvider(
        LocalStackPalette provides palette,
    ) {
        MaterialTheme(colorScheme = palette.colorScheme) {
            Box(Modifier.fillMaxSize().background(Ink).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))) {
                AnimatedContent(
                    modifier = Modifier.fillMaxSize().then(
                        if (onboarded && !restoring) {
                            if (layoutSpec.split) Modifier.padding(start = 100.dp)
                            else Modifier.padding(bottom = 90.dp)
                        } else Modifier
                    ),
                    targetState = when { restoring -> 0; !onboarded -> 1; else -> 2 },
                    transitionSpec = { (fadeIn(tween(430)) + scaleIn(tween(430), initialScale = .97f)).togetherWith(fadeOut(tween(260)) + scaleOut(targetScale = 1.03f)) },
                    label = "root",
                ) { state ->
                    when (state) {
                        0 -> RestoringScreen()
                        1 -> AtelierOnboarding(
                            layoutSpec = layoutSpec,
                            initialCountry = country,
                            repository = leaderboardRepository,
                            deviceId = deviceId,
                            nextObjectId = { ++nextLandingId },
                            onLandingContact = { objectState -> commitSuccessfulContact(objectState, false) },
                            onComplete = { chosenName, chosenCountry ->
                                name = chosenName; country = chosenCountry; onboarded = true
                                sceneState = StackSceneReducer.cancelAndRestore(
                                    sceneState,
                                    count,
                                    stackMaterials.first { it.id == materialId }.liveMaterial,
                                )
                                onDisplayNameChanged(chosenName); onCountryCodeChanged(chosenCountry); onOnboardingCompleteChanged(true)
                            },
                        )
                        else -> NavDisplay(
                            backStack = if (selectedPage == 1) listOf(1) else listOf(1, selectedPage),
                            onBack = { selectedPage = 1 },
                            transitionSpec = {
                                (fadeIn(tween(220)) + slideInHorizontally(tween(300)) { it / 14 })
                                    .togetherWith(fadeOut(tween(140)))
                            },
                            popTransitionSpec = {
                                fadeIn(tween(220)).togetherWith(fadeOut(tween(140)))
                            },
                            entryProvider = { page -> NavEntry(page) {
                            when (page) {
                                0 -> AtelierLeaderboard(
                                    layoutSpec = layoutSpec,
                                    entries = leaderboard.entries,
                                    loading = boardLoading,
                                    refreshing = boardRefreshing,
                                    premium = premium,
                                    name = name,
                                    today = today,
                                    country = country,
                                    onRefresh = ::refreshBoard,
                                )
                                1 -> AtelierTapScreen(
                                    layoutSpec = layoutSpec,
                                    count = count,
                                    today = today,
                                    sceneState = sceneState,
                                    sceneEnabled = selectedPage == 1 && !showSounds,
                                    reward = reward,
                                    material = stackMaterials.first { it.id == materialId },
                                    sound = soundPalette(soundId),
                                    onSound = { showSounds = true },
                                    onTap = { intensity ->
                                        nextLandingId++
                                        sceneState = StackSceneReducer.admit(
                                            sceneState,
                                            nextLandingId,
                                            intensity,
                                            forceMiss = !rhythmGate.shouldStack(SystemClock.elapsedRealtime()),
                                        ).first
                                    },
                                    onContact = { objectState ->
                                        val (contactedState, accepted) = StackSceneReducer.beginContact(
                                            sceneState,
                                            objectState.id,
                                            objectState.generation,
                                        )
                                        sceneState = contactedState
                                        if (accepted) {
                                            scope.launch {
                                                try {
                                                    commitSuccessfulContact(objectState, true)
                                                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                                    throw cancelled
                                                } catch (_: Exception) {
                                                    sceneState = StackSceneReducer.cancelAndRestore(sceneState, count)
                                                    snackbar.showSnackbar("Could not save this layer. Try again.")
                                                    return@launch
                                                }
                                                sceneState = if (sceneState.generation == objectState.generation) {
                                                    StackSceneReducer.contactCommitted(sceneState, objectState.id, objectState.generation)
                                                } else {
                                                    StackSceneReducer.cancelAndRestore(sceneState, count)
                                                }
                                            }
                                        }
                                    },
                                    onSettled = {
                                        sceneState = StackSceneReducer.settle(sceneState, it.id, it.generation)
                                    },
                                    onAnimationFinished = { objectState ->
                                        sceneState = StackSceneReducer.animationFinished(
                                            sceneState,
                                            objectState.id,
                                            objectState.generation,
                                        )
                                        scope.launch {
                                            withFrameNanos { }
                                            sceneState = StackSceneReducer.releaseDisposed(
                                                sceneState,
                                                objectState.id,
                                                objectState.generation,
                                            )
                                        }
                                    },
                                )
                                else -> AtelierScreen(
                                    layoutSpec = layoutSpec,
                                    selected = materialId,
                                    premium = collectionUnlocked,
                                    offer = offer,
                                    theme = theme,
                                    onTheme = { selectedTheme ->
                                        theme = selectedTheme
                                        onThemeChanged(selectedTheme.id)
                                    },
                                    onMaterial = { selected ->
                                        if (!selected.premium || collectionUnlocked) {
                                            materialId = selected.id
                                            sceneState = StackSceneReducer.cancelAndRestore(sceneState, count, selected.liveMaterial)
                                            onMaterialChanged(selected.id)
                                            selectedPage = 1
                                        }
                                    },
                                    onSubscribe = {
                                        analyticsTracker.track(AnalyticsEvent.SubscriptionBillingOpened)
                                        billingRepository.launchPurchase(activity, onResult = { premium = it.autoMinerActive; onAutoMinerActiveChanged(premium) }, onError = {})
                                    },
                                    onManage = { billingRepository.openSubscriptionManagement(activity, onResult = { premium = it.autoMinerActive; onAutoMinerActiveChanged(premium) }, onError = {}) },
                                )
                            }
                            } },
                        )
                    }
                }
                if (onboarded && !restoring) {
                    StackNavigation(selectedPage, layoutSpec.split, { selectedPage = it },
                        Modifier.align(if (layoutSpec.split) Alignment.CenterStart else Alignment.BottomCenter))
                }
                if (showSounds) {
                    SoundLibrarySheet(layoutSpec, soundId, collectionUnlocked, onToneSelect = {
                        soundId = it
                        onSoundPaletteChanged(it)
                        audioEngine.previewTone(it)
                    }, onPremiumLocked = {
                        showSounds = false
                        scope.launch {
                            selectedPage = 2
                            analyticsTracker.track(AnalyticsEvent.SubscriptionBillingOpened)
                            billingRepository.launchPurchase(
                                activity,
                                onResult = { premium = it.autoMinerActive; onAutoMinerActiveChanged(premium) },
                                onError = {},
                            )
                        }
                    }, onDismiss = { showSounds = false })
                }
                SnackbarHost(
                    hostState = snackbar,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 72.dp),
                )
            }
        }
    }
}

@Composable
private fun RestoringScreen() {
    Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("STACK", color = Bone, fontFamily = uiFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Spacer(Modifier.height(18.dp))
            Text("Restoring your stack", color = Mist.copy(alpha = .62f), fontFamily = uiFont, fontSize = 14.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AtelierOnboarding(
    layoutSpec: StackLayoutSpec,
    initialCountry: String,
    repository: LeaderboardRepository,
    deviceId: DeviceId,
    nextObjectId: () -> Long,
    onLandingContact: suspend (SceneObject) -> TapState,
    onComplete: (String, String) -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    var taps by remember { mutableIntStateOf(0) }
    var landed by remember { mutableIntStateOf(0) }
    val completedLandingIds = remember { mutableSetOf<Long>() }
    var name by remember { mutableStateOf("") }
    var country by remember { mutableStateOf(initialCountry) }
    var available by remember { mutableStateOf<Boolean?>(null) }
    var checking by remember { mutableStateOf(false) }
    var showCountries by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf(listOf("quietstack", "paperpulse", "softlanding")) }
    var sceneState by remember { mutableStateOf(StackSceneReducer.restore(0, LiveMaterial.Paper)) }
    val keyboard = LocalSoftwareKeyboardController.current
    val nameFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(step) {
        if (step == 1) {
            delay(380)
            nameFocus.requestFocus()
            keyboard?.show()
        }
    }

    LaunchedEffect(taps, landed, sceneState.active.size, sceneState.settled.size) {
        if (taps == 3 && landed == 3 && sceneState.active.isEmpty() && sceneState.settled.size == 3 && step == 0) {
            delay(260)
            step = 1
        }
    }

    LaunchedEffect(name) {
        available = null
        if (name.length >= 3) {
            checking = true; delay(350)
            repository.checkUsername(name, deviceId, onResult = { available = it; checking = false }, onError = { checking = false })
        }
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        val sceneModifier = if (layoutSpec.split) {
            Modifier.align(Alignment.CenterEnd).fillMaxWidth(layoutSpec.sceneWidthFraction).fillMaxHeight()
        } else {
            Modifier.fillMaxSize()
        }
        if (step == 0) LiveStackScene(
            state = sceneState,
            modifier = sceneModifier,
            framingScale = layoutSpec.framingScale,
            onContact = { objectState ->
                val (contacted, accepted) = StackSceneReducer.beginContact(
                    sceneState,
                    objectState.id,
                    objectState.generation,
                )
                sceneState = contacted
                if (accepted) {
                    scope.launch {
                        onLandingContact(objectState)
                        sceneState = StackSceneReducer.contactCommitted(sceneState, objectState.id, objectState.generation)
                        if (sceneState.objects.any { it.id == objectState.id && it.phase == ScenePhase.Settled } && completedLandingIds.add(objectState.id)) landed++
                    }
                }
            },
            onSettled = {
                sceneState = StackSceneReducer.settle(sceneState, it.id, it.generation)
                if (it.generation == sceneState.generation && sceneState.objects.any { layer -> layer.id == it.id && layer.phase == ScenePhase.Settled } && completedLandingIds.add(it.id)) landed++
            },
            onAnimationFinished = { objectState ->
                sceneState = StackSceneReducer.animationFinished(
                    sceneState,
                    objectState.id,
                    objectState.generation,
                )
                scope.launch {
                    withFrameNanos { }
                    sceneState = StackSceneReducer.releaseDisposed(
                        sceneState,
                        objectState.id,
                        objectState.generation,
                    )
                }
            },
        )
        AtelierHeader("Stack", Modifier.align(Alignment.TopCenter).padding(horizontal = 24.dp))
        AnimatedContent(step, transitionSpec = { (slideInHorizontally { it / 2 } + fadeIn()).togetherWith(slideOutHorizontally { -it / 2 } + fadeOut()) }, label = "onboarding") { page ->
            if (page == 0) {
                Box(Modifier.fillMaxSize().clickable {
                    if (taps < 3) {
                        val (nextState, admission) = StackSceneReducer.admit(
                            sceneState,
                            nextObjectId(),
                            .82f,
                        )
                        sceneState = nextState
                        if (admission is AdmissionResult.Accepted) taps++
                    }
                }) {
                    Column(
                        Modifier
                            .align(if (layoutSpec.split) Alignment.CenterStart else Alignment.TopCenter)
                            .then(if (layoutSpec.split) Modifier.fillMaxWidth(1f - layoutSpec.sceneWidthFraction) else Modifier)
                            .windowInsetsPadding(immersiveTopInsets())
                            .padding(horizontal = 24.dp, vertical = if (layoutSpec.split) 24.dp else 88.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Build your first stack", color = Bone, fontFamily = uiFont, fontSize = 28.sp, fontWeight = FontWeight.Medium)
                        Text("Tap anywhere. Feel each layer land.", color = Mist, fontFamily = uiFont, fontSize = 14.sp)
                    }
                    Row(Modifier.align(if (layoutSpec.split) Alignment.BottomStart else Alignment.BottomCenter).navigationBarsPadding().padding(22.dp).then(if (layoutSpec.split) Modifier.fillMaxWidth(1f - layoutSpec.sceneWidthFraction) else Modifier.fillMaxWidth()).glass(RoundedCornerShape(8.dp)).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Tap anywhere", color = Bone, fontFamily = uiFont, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text("$landed of 3", color = Mist, fontFamily = numberFont, fontSize = 12.sp)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 22.dp), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.widthIn(max = if (layoutSpec.split) 560.dp else layoutSpec.contentMaxWidth).fillMaxWidth().navigationBarsPadding().padding(bottom = 18.dp).glass(RoundedCornerShape(20.dp)).padding(20.dp)) {
                        Text("Make it yours", color = Bone, fontFamily = uiFont, fontSize = 27.sp, fontWeight = FontWeight.Medium)
                        Text("Your name and home appear on today's Stack.", color = Mist, fontFamily = uiFont, fontSize = 13.sp)
                        Spacer(Modifier.height(22.dp))
                        GlassField(name, "Username", Modifier.focusRequester(nameFocus), onValue = { name = cleanDisplayName(it) })
                        Spacer(Modifier.height(8.dp))
                        Text(when { checking -> "Checking..."; available == true -> "Available"; available == false -> "Already taken"; else -> "3-20 letters, numbers or symbols" }, color = if (available == true) Celadon else if (available == false) Color(0xFFE3A39E) else Mist, fontFamily = uiFont, fontSize = 12.sp)
                        if (available == false) {
                            Spacer(Modifier.height(10.dp)); LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(suggestions) { s -> Text(s, Modifier.clip(CircleShape).background(Color.White.copy(alpha = .08f)).clickable { name = s }.padding(horizontal = 12.dp, vertical = 8.dp), color = Bone, fontFamily = uiFont, fontSize = 12.sp) }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(LocalStackPalette.current.glassBorder).clickable { showCountries = true }.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${flag(country)}  ${countryName(country)}", Modifier.weight(1f), color = Bone, fontFamily = uiFont)
                            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Mist)
                        }
                        Spacer(Modifier.height(18.dp))
                        AtelierButton("Enter Stack", enabled = available == true) {
                            keyboard?.hide(); repository.claimUsername(deviceId, StackProfile(cleanDisplayName(name), country)) { result ->
                                if (result == UsernameClaimResult.Success) onComplete(cleanDisplayName(name), country) else available = false
                            }
                        }
                    }
                }
            }
        }
        if (showCountries) CountrySheet(country, onSelect = { country = it; showCountries = false }, onDismiss = { showCountries = false })
    }
}

@Composable
private fun AtelierTapScreen(
    layoutSpec: StackLayoutSpec,
    count: Long,
    today: Long,
    sceneState: StackSceneState,
    sceneEnabled: Boolean,
    reward: String,
    material: StackMaterial,
    sound: SoundPalette,
    onSound: () -> Unit,
    onTap: (Float) -> Unit,
    onContact: (SceneObject) -> Unit,
    onSettled: (SceneObject) -> Unit,
    onAnimationFinished: (SceneObject) -> Unit,
) {
    var soundControlExpanded by remember { mutableStateOf(true) }
    LaunchedEffect(sound.id) {
        soundControlExpanded = true
        delay(2_500)
        soundControlExpanded = false
    }
    val soundControlWidth by animateDpAsState(if (soundControlExpanded) 132.dp else 48.dp, tween(320), label = "sound-width")
    val currentOnTap by rememberUpdatedState(onTap)
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val tapInput = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            data class PointerTrack(val start: Offset, val downAt: Long, var cancelled: Boolean = false)
            val pointers = mutableMapOf<PointerId, PointerTrack>()
            do {
                val event = awaitPointerEvent(PointerEventPass.Final)
                event.changes.forEach { change ->
                    if (change.changedToDownIgnoreConsumed()) {
                        pointers[change.id] = PointerTrack(change.position, change.uptimeMillis)
                    }
                    pointers[change.id]?.let { track ->
                        val delta = change.position - track.start
                        if (delta.getDistance() > touchSlop || (change.isConsumed && delta.getDistance() > touchSlop / 2f)) {
                            track.cancelled = true
                        }
                        if (change.changedToUpIgnoreConsumed()) {
                            if (!track.cancelled && change.uptimeMillis - track.downAt <= 320L) currentOnTap(.82f)
                            pointers.remove(change.id)
                        }
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }
    Box(Modifier.fillMaxSize().background(Ink)) {
        val sceneModifier = if (layoutSpec.split) {
            Modifier.align(Alignment.CenterEnd).fillMaxWidth(layoutSpec.sceneWidthFraction).fillMaxHeight()
        } else {
            Modifier.padding(top = 230.dp, bottom = 70.dp).fillMaxSize()
        }
        LiveStackScene(
            state = sceneState,
            modifier = sceneModifier,
            framingScale = layoutSpec.framingScale,
            enabled = sceneEnabled,
            onContact = onContact,
            onSettled = onSettled,
            onAnimationFinished = onAnimationFinished,
        )
        Box(
            sceneModifier
                .align(if (layoutSpec.split) Alignment.BottomEnd else Alignment.BottomCenter)
                .height(if (layoutSpec.split) 76.dp else 112.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = .96f)))),
        )
        Box(sceneModifier.then(tapInput).semantics {
            onClick("Add a layer") { currentOnTap(.82f); true }
        })
        Column(
            Modifier
                .align(if (layoutSpec.split) Alignment.CenterStart else Alignment.TopCenter)
                .then(if (layoutSpec.split) Modifier.fillMaxWidth(1f - layoutSpec.sceneWidthFraction) else Modifier)
                .windowInsetsPadding(immersiveTopInsets())
                .padding(horizontal = 20.dp, vertical = if (layoutSpec.split) 20.dp else 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Your stack", color = Mist, fontFamily = uiFont, fontSize = 15.sp)
            Text(formatAtelier(count), color = Bone, fontFamily = numberFont, fontSize = if (count >= 1_000_000) 44.sp else 68.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp)
            Text("${formatAtelier(today)} layers today", color = Mist, fontFamily = uiFont, fontSize = 15.sp)
            Spacer(Modifier.height(11.dp))
        }
        AnimatedVisibility(reward.isNotBlank(), Modifier.align(if (layoutSpec.split) Alignment.CenterEnd else Alignment.Center), enter = fadeIn() + scaleIn(initialScale = .8f), exit = fadeOut(tween(700))) {
            Text(reward, color = material.accent, fontFamily = uiFont, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
        Row(
            Modifier
                .align(if (layoutSpec.split) Alignment.BottomStart else Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = if (layoutSpec.split) 22.dp else 0.dp, bottom = 14.dp)
                .width(soundControlWidth)
                .height(48.dp)
                .glass(RoundedCornerShape(24.dp))
                .clickable(onClick = onSound)
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Rounded.GraphicEq, "Sound library", Modifier.size(21.dp), tint = Celadon)
            AnimatedVisibility(soundControlExpanded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(9.dp))
                    Text(sound.label, color = Bone.copy(alpha = .92f), fontFamily = uiFont, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoundLibrarySheet(
    layoutSpec: StackLayoutSpec,
    selectedTone: String,
    premium: Boolean,
    onToneSelect: (String) -> Unit,
    onPremiumLocked: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val palette = LocalStackPalette.current
    val rippleColor = if (palette.background.red + palette.background.green + palette.background.blue > 1.5f) {
        palette.accent.copy(alpha = .16f)
    } else {
        Color.White.copy(alpha = .14f)
    }
    val maxWidth = if (layoutSpec.mode == StackLayoutMode.Expanded) 720.dp else 640.dp
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Ink.copy(alpha = .94f), contentColor = Bone, dragHandle = null, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.widthIn(max = maxWidth).fillMaxHeight(.82f).fillMaxWidth().align(Alignment.CenterHorizontally)) {
            Box(Modifier.padding(top = 10.dp).width(36.dp).height(4.dp).background(Color.White.copy(alpha = .22f), CircleShape).align(Alignment.CenterHorizontally))
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Sound Library", color = Bone, fontFamily = uiFont, fontSize = 29.sp, fontWeight = FontWeight.Normal); Text("Tap a tone to hear it.", color = Celadon, fontFamily = uiFont, fontSize = 13.sp) }
                IconButton(onClick = onDismiss, Modifier.size(48.dp).border(1.dp, Color.White.copy(alpha = .25f), CircleShape)) { Icon(Icons.Rounded.Close, "Close", tint = Bone, modifier = Modifier.size(23.dp)) }
            }
            LazyColumn(Modifier.weight(1f).padding(horizontal = 26.dp)) {
                items(soundPalettes, key = { it.id }) { tone ->
                    val isSelected = tone.id == selectedTone
                    val locked = tone.premium && !premium
                    val shape = RoundedCornerShape(14.dp)
                    val interaction = remember { MutableInteractionSource() }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .clickable(interactionSource = interaction, indication = ripple(bounded = true, color = rippleColor)) {
                                if (locked) onPremiumLocked() else onToneSelect(tone.id)
                            }
                            .padding(horizontal = 10.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.GraphicEq, null, Modifier.size(27.dp), tint = Celadon)
                        Spacer(Modifier.width(24.dp))
                        Text(tone.label, Modifier.weight(1f), color = Bone, fontFamily = uiFont, fontSize = 18.sp)
                        if (isSelected) Text("Selected", color = Celadon, fontFamily = uiFont, fontSize = 12.sp)
                        else if (locked) Icon(Icons.Rounded.Lock, "Locked", Modifier.size(18.dp), tint = Mist)
                        Spacer(Modifier.width(26.dp))
                        Box(Modifier.size(29.dp).border(1.dp, if (isSelected) Celadon else Mist.copy(alpha = .35f), CircleShape), contentAlignment = Alignment.Center) { if (isSelected) Box(Modifier.size(19.dp).background(Celadon, CircleShape)) }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalStackPalette.current.glassBorder))
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(18.dp))
        }
    }
}

@Composable
private fun AtelierLeaderboard(
    layoutSpec: StackLayoutSpec,
    entries: List<LeaderboardEntry>,
    loading: Boolean,
    refreshing: Boolean,
    premium: Boolean,
    name: String?,
    today: Long,
    country: String,
    onRefresh: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Bone.copy(alpha = .06f), Ink, Ink),
                ),
            ),
    ) {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AtelierHeader("Today")
                Text(resetCountdown(), Modifier.fillMaxWidth().padding(bottom = 12.dp), color = Mist, fontFamily = numberFont, fontSize = 11.sp, textAlign = TextAlign.Center)
                if (layoutSpec.mode == StackLayoutMode.Expanded && !loading) {
                    Row(Modifier.weight(1f).fillMaxWidth().widthIn(max = layoutSpec.contentMaxWidth), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        Column(Modifier.weight(.43f).fillMaxHeight()) {
                            entries.take(3).forEach { PodiumRow(it) }
                            Spacer(Modifier.weight(1f))
                            CurrentUserRow(name, today, country, premium)
                        }
                        LazyColumn(Modifier.weight(.57f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            items(entries.drop(3), key = { "${it.rank}-${it.displayName}" }) { LeaderRow(it) }
                        }
                    }
                } else {
                    val content = Modifier.weight(1f).fillMaxWidth().widthIn(max = layoutSpec.contentMaxWidth)
                    if (loading) LeaderboardLoading(content) else if (layoutSpec.mode == StackLayoutMode.CompactLandscape) {
                        LazyColumn(content, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            items(entries, key = { "${it.rank}-${it.displayName}" }) { entry -> if (entry.rank <= 3) PodiumRow(entry) else LeaderRow(entry) }
                            item { CurrentUserRow(name, today, country, premium) }
                        }
                    } else {
                        LazyColumn(content, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            items(entries, key = { "${it.rank}-${it.displayName}" }) { entry -> if (entry.rank <= 3) PodiumRow(entry) else LeaderRow(entry) }
                        }
                    }
                    if (layoutSpec.mode != StackLayoutMode.CompactLandscape) {
                        Box(Modifier.fillMaxWidth().widthIn(max = layoutSpec.contentMaxWidth)) { CurrentUserRow(name, today, country, premium) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentUserRow(name: String?, today: Long, country: String, premium: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp).height(72.dp).glass(RoundedCornerShape(24.dp)).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("You", color = Celadon, fontFamily = uiFont, fontSize = 12.sp)
        Spacer(Modifier.width(14.dp)); MiniStack(3)
        Spacer(Modifier.width(14.dp)); Text(name ?: "You", Modifier.weight(1f), color = Bone, fontFamily = uiFont, fontSize = 14.sp)
        if (premium) {
            Row(Modifier.height(26.dp).glass(RoundedCornerShape(13.dp)).padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Layers, null, Modifier.size(13.dp), tint = Celadon)
                Spacer(Modifier.width(4.dp))
                Text("PRO", color = Celadon, fontFamily = numberFont, fontSize = 9.sp)
            }
            Spacer(Modifier.width(9.dp))
        }
        Text(formatAtelier(today), color = Celadon, fontFamily = numberFont, fontSize = 14.sp)
        Spacer(Modifier.width(12.dp)); Text(flag(country), fontSize = 16.sp)
    }
}

@Composable
private fun PodiumRow(entry: LeaderboardEntry) {
    Row(Modifier.fillMaxWidth().height(132.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${entry.rank}", Modifier.width(48.dp), color = when (entry.rank) { 1 -> Brass; 2 -> Bone.copy(alpha = .76f); else -> Color(0xFFB58F7D) }, fontFamily = numberFont, fontSize = 31.sp)
        Column(Modifier.width(108.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(5 - entry.rank) { layer -> Box(Modifier.width((82 - layer * 5).dp).height(10.dp).graphicsLayer { rotationZ = (layer - 2) * 1.5f }.background(Bone.copy(alpha = .82f - layer * .08f), RoundedCornerShape(2.dp))) }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.displayName, color = Bone, fontFamily = uiFont, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatAtelier(entry.todayCount), color = Celadon, fontFamily = numberFont, fontSize = 25.sp)
            Text("${flag(entry.countryCode)}  ${countryName(entry.countryCode)}", color = Mist, fontFamily = uiFont, fontSize = 12.sp)
        }
    }
}

@Composable
private fun LeaderRow(entry: LeaderboardEntry) {
    val highlight = entry.rank <= 3 || entry.isCurrentUser
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (highlight) LocalStackPalette.current.colorScheme.surface else Color.Transparent).padding(horizontal = 12.dp, vertical = if (entry.rank <= 3) 15.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${entry.rank}", Modifier.width(34.dp), color = if (entry.rank <= 3) Brass else Mist, fontFamily = numberFont, fontSize = 13.sp)
        if (entry.rank <= 3) MiniStack(entry.rank)
        Column(Modifier.weight(1f).padding(start = if (entry.rank <= 3) 10.dp else 0.dp)) { Text(entry.displayName, color = Bone, fontFamily = uiFont, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${flag(entry.countryCode)}  ${entry.countryCode}", color = Mist.copy(alpha = .65f), fontFamily = uiFont, fontSize = 10.sp) }
        Text(formatAtelier(entry.todayCount), color = Bone, fontFamily = numberFont, fontSize = 13.sp)
    }
}

@Composable
private fun MiniStack(rank: Int) {
    Column(Modifier.width(34.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) { repeat(4 - rank.coerceAtMost(3)) { Box(Modifier.width((28 - it * 3).dp).height(4.dp).background(Brass.copy(alpha = .72f), RoundedCornerShape(1.dp))) } }
}

@Composable
private fun LeaderboardLoading(modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(.55f, tween(700), label = "shimmer")
    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) { repeat(8) { Box(Modifier.fillMaxWidth().height(54.dp).background(Mist.copy(alpha = .08f * alpha), RoundedCornerShape(14.dp))) } }
}

@Composable
private fun AtelierScreen(
    layoutSpec: StackLayoutSpec,
    selected: String,
    premium: Boolean,
    offer: SubscriptionOffer,
    theme: StackTheme,
    onTheme: (StackTheme) -> Unit,
    onMaterial: (StackMaterial) -> Unit,
    onSubscribe: () -> Unit,
    onManage: () -> Unit,
) {
    var focused by rememberSaveable(selected) { mutableStateOf(selected) }
    val preview = stackMaterials.first { it.id == focused }
    Box(Modifier.fillMaxSize().background(Ink)) {
        Column(
            Modifier.widthIn(max = 760.dp).fillMaxSize().align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            AtelierHeader("Collection")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(LocalStackPalette.current.colorScheme.surface)) {
                AnimatedContent(preview.image, transitionSpec = { fadeIn(tween(240)).togetherWith(fadeOut(tween(180))) }, label = "material-preview") { image ->
                    Image(painterResource(image), preview.name, Modifier.fillMaxWidth().height(if (layoutSpec.split) 240.dp else 200.dp), contentScale = ContentScale.Crop)
                }
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(preview.name, color = Bone, fontFamily = uiFont, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Text(preview.description, color = Mist, fontFamily = uiFont, fontSize = 14.sp)
                    }
                    if (preview.id == selected) Icon(Icons.Rounded.Check, "Current material", tint = Celadon)
                }
            }
            Text("MATERIALS", color = Mist, fontFamily = uiFont, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                stackMaterials.forEach { material ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(18.dp))
                            .border(if (focused == material.id) 2.dp else .5.dp, if (focused == material.id) Celadon else LocalStackPalette.current.glassBorder, RoundedCornerShape(18.dp))
                            .selectable(focused == material.id, role = Role.RadioButton) { focused = material.id }) {
                            Image(painterResource(material.image), material.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        Text(material.name, Modifier.padding(top = 8.dp), color = if (focused == material.id) Celadon else Mist, fontFamily = uiFont, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
            AtelierButton(if (preview.id == selected) "Selected" else if (preview.premium && !premium) "Unlock Stack · ${offer.priceText}" else "Use ${preview.name}", enabled = preview.id != selected) {
                if (preview.premium && !premium) onSubscribe() else onMaterial(preview)
            }
            Text("PREFERENCES", color = Mist, fontFamily = uiFont, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(horizontal = 18.dp)) {
                ThemeToggle(theme, onTheme)
                FeatureRow("Sound collection", "${soundPalettes.size} tones to find your rhythm", Icons.Rounded.GraphicEq)
                FeatureRow("Your materials", "${stackMaterials.size} tactile ways to build", Icons.Rounded.Layers)
            }
            if (BuildConfig.PAYMENTS_ENABLED && premium) {
                Text("Manage membership", Modifier.fillMaxWidth().clickable(onClick = onManage).padding(12.dp), color = Celadon, fontFamily = uiFont, fontSize = 15.sp, textAlign = TextAlign.Center)
            }
            Text("Small steps. A little higher each day.", Modifier.fillMaxWidth().padding(bottom = 24.dp), color = Mist, fontFamily = uiFont, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ThemeToggle(theme: StackTheme, onTheme: (StackTheme) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Appearance", Modifier.weight(1f), color = Bone, fontFamily = uiFont, fontSize = 14.sp)
        Row(Modifier.width(104.dp).height(44.dp).glass(RoundedCornerShape(22.dp))) {
            listOf(StackTheme.Light to Icons.Rounded.LightMode, StackTheme.Dark to Icons.Rounded.DarkMode).forEach { (option, icon) ->
                val selected = option == theme
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(22.dp))
                        .background(if (selected) Celadon.copy(alpha = .18f) else Color.Transparent)
                        .clickable { onTheme(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, option.name, Modifier.size(20.dp), tint = if (selected) Celadon else Mist)
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalStackPalette.current.glassBorder))
}

@Composable
private fun FeatureRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(24.dp), tint = Celadon)
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f)) { Text(label, color = Bone, fontFamily = uiFont, fontSize = 14.sp); Text(value, color = Mist, fontFamily = uiFont, fontSize = 12.sp) }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalStackPalette.current.glassBorder))
}

@Composable
private fun AtelierHeader(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().windowInsetsPadding(immersiveTopInsets()).padding(top = 20.dp, bottom = 8.dp).height(52.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(title, color = Bone, fontFamily = uiFont, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-1).sp)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun immersiveTopInsets(): WindowInsets =
    WindowInsets.displayCutout
        .union(WindowInsets.waterfall)
        .union(WindowInsets.statusBarsIgnoringVisibility)
        .only(WindowInsetsSides.Top)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountrySheet(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var search by remember { mutableStateOf("") }
    val countries = remember(search) { Locale.getISOCountries().map { it to countryName(it) }.filter { it.second.contains(search, true) || it.first.contains(search, true) }.sortedBy { it.second } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Ink.copy(alpha = .94f), dragHandle = null, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxHeight(.82f).navigationBarsPadding().padding(horizontal = 20.dp)) {
            Box(Modifier.padding(top = 10.dp).width(36.dp).height(4.dp).background(Color.White.copy(alpha = .22f), CircleShape).align(Alignment.CenterHorizontally))
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text("Choose country", Modifier.weight(1f), color = Bone, fontFamily = uiFont, fontSize = 25.sp); IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close", tint = Bone) } }
            GlassField(search, "Search", onValue = { search = it })
            Spacer(Modifier.height(10.dp))
            LazyColumn { items(countries, key = { it.first }) { (code, label) -> Row(Modifier.fillMaxWidth().clickable { onSelect(code) }.padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) { Text(flag(code), fontSize = 20.sp); Spacer(Modifier.width(14.dp)); Text(label, Modifier.weight(1f), color = Bone, fontFamily = uiFont, fontSize = 15.sp); if (code == selected) Icon(Icons.Rounded.Check, null, tint = Celadon) }; Box(Modifier.fillMaxWidth().height(1.dp).background(LocalStackPalette.current.glassBorder)) } }
        }
    }
}

@Composable
private fun GlassField(value: String, placeholder: String, modifier: Modifier = Modifier, onValue: (String) -> Unit) {
    BasicTextField(value, onValue, modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Mist.copy(alpha = .08f)).border(BorderStroke(1.dp, Color.White.copy(alpha = .12f)), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 14.dp), textStyle = TextStyle(color = Bone, fontFamily = uiFont, fontSize = 16.sp), singleLine = true, decorationBox = { inner -> if (value.isEmpty()) Text(placeholder, color = Mist.copy(alpha = .6f), fontFamily = uiFont); inner() })
}

@Composable
private fun AtelierButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().height(50.dp), enabled = enabled, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Celadon, contentColor = Color.White, disabledContainerColor = Mist.copy(alpha = .12f), disabledContentColor = Mist), elevation = ButtonDefaults.buttonElevation(0.dp)) { Text(text, fontFamily = uiFont, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp) }
}

@Composable
private fun MelodyDots(count: Long) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(8) { i -> Box(Modifier.size(if (i < (count % 8).toInt()) 4.dp else 3.dp).background(if (i < (count % 8).toInt()) Bone else Mist.copy(alpha = .35f), CircleShape)) } }
}

@Composable
private fun Modifier.glass(shape: RoundedCornerShape): Modifier {
    val palette = LocalStackPalette.current
    return clip(shape)
        .background(palette.colorScheme.surface)
        .border(.5.dp, palette.glassBorder, shape)
}
private fun formatAtelier(value: Long) = NumberFormat.getIntegerInstance(Locale.US).format(value)
@Composable
private fun resetCountdown(): String {
    var text by remember { mutableStateOf("Resets in 00:00:00") }
    LaunchedEffect(Unit) {
        val zone = ZoneId.of("Asia/Kolkata")
        while (true) {
            val now = ZonedDateTime.now(zone)
            val remaining = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(zone)).seconds.coerceAtLeast(0)
            text = "Resets in %02d:%02d:%02d".format(remaining / 3600, (remaining % 3600) / 60, remaining % 60)
            delay(1_000)
        }
    }
    return text
}
private fun defaultCountry() = Locale.getDefault().country.takeIf { it.length == 2 } ?: "IN"
private fun countryName(code: String) = runCatching { Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.getDefault()) }.getOrDefault(code)
private fun flag(code: String): String = if (code.length == 2) code.uppercase(Locale.US).map { Character.toChars(0x1F1E6 + it.code - 'A'.code).concatToString() }.joinToString("") else ""
