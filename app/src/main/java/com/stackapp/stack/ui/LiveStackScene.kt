package com.stackapp.stack.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.github.sceneview.RenderQuality
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberFillLightNode
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.delay
import kotlin.math.pow
import io.github.sceneview.rememberRenderer
import io.github.sceneview.rememberView
import com.google.android.filament.Renderer
import com.google.android.filament.Engine
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import io.github.sceneview.createEngine

internal enum class LiveMaterial(
    val assetPaths: List<String>,
    val rise: Float,
    val scale: Float,
    val dropHeight: Float,
    val visibleLayers: Int,
    val maxConcurrentLandings: Int,
    val contactMillis: Int,
    val settleMillis: Int,
    val exitDistance: Float,
    val cameraPosition: Position,
    val cameraTarget: Position,
) {
    Paper((1..4).map { "models/cotton_paper_$it.glb" }, 0.036f, 1.45f, 3.15f, 36, 5, 560, 160, .72f, Position(2.72f, .10f, 5.35f), Position(0f, -2.55f, 0f)),
    Stone((1..3).map { "models/river_stone_$it.glb" }, 0.25f, 1.32f, 3.51f, 8, 4, 390, 210, 1.05f, Position(3.08f, .30f, 5.72f), Position(0f, -2.55f, 0f)),
    Library((1..4).map { "models/library_book_$it.glb" }, 0.19f, 1.38f, 3.33f, 12, 4, 470, 190, .88f, Position(2.92f, .22f, 5.55f), Position(0f, -2.55f, 0f));

    val scoringPoolSize: Int get() = visibleLayers + (maxConcurrentLandings * 2)
    val totalPoolSize: Int get() = scoringPoolSize + 6
}

@Composable
internal fun LiveStackScene(
    state: StackSceneState,
    modifier: Modifier = Modifier,
    framingScale: Float = 1f,
    enabled: Boolean = true,
    onContact: (SceneObject) -> Unit = {},
    onSettled: (SceneObject) -> Unit = {},
    onAnimationFinished: (SceneObject) -> Unit = {},
) {
    val material = state.material
    val supportsVulkan = LocalContext.current.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
    val engine = rememberEngine(engineCreator = { egl ->
        if (supportsVulkan) Engine.create(Engine.Backend.VULKAN) else createEngine(egl)
    })
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    // Keep one environment for the scene's entire lifetime. Disposing a temporary
    // fallback skybox while Filament's Scene still references it can crash natively.
    val environment = rememberEnvironment(environmentLoader, isOpaque = false) {
        environmentLoader.createHDREnvironment("environments/atelier.hdr", createSkybox = false)
            ?: io.github.sceneview.createEnvironment(engine, isOpaque = false)
    }
    val hostLifecycle = LocalLifecycleOwner.current.lifecycle
    val renderLifecycle = remember(hostLifecycle) { BurstRenderLifecycle(hostLifecycle) }
    var hostResumed by remember(hostLifecycle) { mutableStateOf(hostLifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var resumeEpoch by remember(hostLifecycle) { androidx.compose.runtime.mutableIntStateOf(0) }
    DisposableEffect(hostLifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            hostResumed = hostLifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (event == Lifecycle.Event.ON_RESUME) resumeEpoch++
        }
        hostLifecycle.addObserver(observer)
        onDispose { hostLifecycle.removeObserver(observer) }
    }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val currentOnContact by rememberUpdatedState(onContact)
    val currentOnSettled by rememberUpdatedState(onSettled)
    val currentOnFinished by rememberUpdatedState(onAnimationFinished)
    val renderer = rememberRenderer(engine)
    // Keep tone mapping, but the Performance preset removes shadows, bloom, and AO.
    val view = rememberView(engine)
    val refreshRate = LocalView.current.display?.refreshRate ?: 60f
    LaunchedEffect(renderer, refreshRate) {
        renderer.frameRateOptions = Renderer.FrameRateOptions().apply { interval = (refreshRate / 30f).coerceAtLeast(1f) }
    }
    val modelInstances = remember(modelLoader, material) {
        // Share a single asset across the pool. Starting several async loads on the
        // same resource loader can leave later variants with default white textures.
        // Position and rotation still vary deterministically for every layer.
        listOf(modelLoader.createInstancedModel(material.assetPaths.first(), material.totalPoolSize))
    }
    DisposableEffect(modelInstances) {
        onDispose {
            modelInstances.forEach { variants -> variants.firstOrNull()?.asset?.let(modelLoader::destroyModel) }
        }
    }
    DisposableEffect(renderLifecycle) {
        renderLifecycle.attach()
        onDispose { renderLifecycle.destroy() }
    }

    val motionKey = remember(state.objects) {
        state.objects.map { Triple(it.id, it.generation, it.phase) }
    }
    // Asset uploads and a newly sized surface need actual frames before idle suspension.
    var surfaceReady by remember(modelInstances, viewportSize, resumeEpoch) { mutableStateOf(false) }
    val warmupFrames = remember(modelInstances, viewportSize, resumeEpoch) { intArrayOf(0) }
    LaunchedEffect(material, motionKey, framingScale, viewportSize, enabled, surfaceReady, hostResumed) {
        if (!enabled || !hostResumed) { renderLifecycle.pause(); return@LaunchedEffect }
        renderLifecycle.resume()
        // Rebound and retirement finish through reducer callbacks. Never cut them off on a timer.
        if (!state.isAnimating && surfaceReady) {
            delay(320L)
            renderLifecycle.pause()
        }
    }

    val cameraNode = rememberCameraNode(engine)
    SideEffect {
        cameraNode.position = Position(
            material.cameraPosition.x * framingScale,
            material.cameraPosition.y,
            material.cameraPosition.z * framingScale,
        )
        cameraNode.lookAt(material.cameraTarget)
    }
    val stackingObjects = state.objects.filter {
        it.phase == ScenePhase.Landing ||
            it.phase == ScenePhase.ContactPending ||
            it.phase == ScenePhase.Contacted ||
            it.phase == ScenePhase.Settled || it.phase == ScenePhase.Exiting
    }
    val levels = stackingObjects.mapIndexed { index, objectState -> objectState.id to index }.toMap()
    val objectsBySlot = state.objects.associateBy { it.renderSlot }

    SceneView(
        modifier = modifier.fillMaxSize().onSizeChanged { viewportSize = it },
        surfaceType = SurfaceType.Surface,
        engine = engine,
        renderer = renderer,
        view = view,
        modelLoader = modelLoader,
        environmentLoader = environmentLoader,
        environment = environment,
        lifecycle = renderLifecycle.lifecycle,
        isOpaque = false,
        renderQuality = RenderQuality.Performance,
        autoCenterContent = false,
        autoFitContent = false,
        cameraNode = cameraNode,
        cameraManipulator = null,
        onFrame = {
            if (com.stackapp.stack.BuildConfig.DEBUG) {
                android.os.Trace.beginSection("Stack.SceneFrame")
                android.os.Trace.endSection()
            }
            if (!surfaceReady) {
                if (modelLoader.progress >= 1f) {
                    if (++warmupFrames[0] >= 24) surfaceReady = true
                } else warmupFrames[0] = 0
            }
        },
        mainLightNode = rememberMainLightNode(engine) {
            intensity = 60_000f
            rotation = Rotation(x = -42f, y = -34f, z = 0f)
        },
        fillLightNode = rememberFillLightNode(engine) {
            intensity = 14_000f
            rotation = Rotation(x = 24f, y = 132f, z = 0f)
        },
    ) {
        // Nodes must remain composed across slot reuse: SceneView destroys entities on disposal.
        // Key only by pool slot, never by event identity or generation.
        repeat(material.totalPoolSize) { slot ->
            key(material, slot) {
                val objectState = objectsBySlot[slot] ?: SceneObject(
                    id = Long.MAX_VALUE - slot, generation = state.generation,
                    appearanceSeed = slot.toLong(), renderSlot = slot, phase = ScenePhase.AwaitingDisposal,
                )
                val targetY = -3.40f + (levels[objectState.id] ?: 0) * material.rise
                val stackY = remember(objectState.id, objectState.generation) { Animatable(targetY) }
                val drop = remember(objectState.id, objectState.generation) {
                    Animatable(if (objectState.phase == ScenePhase.Landing) material.dropHeight else 0f)
                }
                val exitProgress = remember(objectState.id, objectState.generation) { Animatable(0f) }
                val missProgress = remember(objectState.id, objectState.generation) { Animatable(0f) }

                LaunchedEffect(targetY) {
                    if (objectState.phase != ScenePhase.Exiting && objectState.phase != ScenePhase.Missed) {
                        stackY.animateTo(targetY, tween(280, easing = FastOutSlowInEasing))
                    }
                }
                LaunchedEffect(objectState.id, objectState.generation) {
                    if (objectState.phase != ScenePhase.Landing) return@LaunchedEffect
                    drop.animateTo(0f, tween(material.contactMillis, easing = FastOutSlowInEasing))
                    currentOnContact(objectState)
                    val rebound = when (material) {
                        LiveMaterial.Paper -> 0.035f
                        LiveMaterial.Stone -> 0.075f
                        LiveMaterial.Library -> 0.052f
                    }
                    drop.animateTo(rebound, tween(material.settleMillis / 2, easing = LinearOutSlowInEasing))
                    drop.animateTo(0f, tween(material.settleMillis / 2, easing = FastOutSlowInEasing))
                    currentOnSettled(objectState)
                }
                LaunchedEffect(objectState.id, objectState.generation, objectState.phase) {
                    when (objectState.phase) {
                        ScenePhase.Exiting -> {
                            exitProgress.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
                            currentOnFinished(objectState)
                        }
                        ScenePhase.Missed -> {
                            missProgress.animateTo(1f, tween(560, easing = LinearEasing))
                            currentOnFinished(objectState)
                        }
                        else -> Unit
                    }
                }

                val seed = objectState.appearanceSeed
                val baseXRange = if (material == LiveMaterial.Stone) .10f else .035f
                val baseX = deterministicOffset(seed, material, 17) * baseXRange
                val baseZ = deterministicOffset(seed, material, 31) * .055f
                val yaw = deterministicOffset(seed, material, 47) * if (material == LiveMaterial.Stone) 7f else 2.2f
                val tilt = deterministicOffset(seed, material, 71) * if (material == LiveMaterial.Stone) 2f else .3f
                val fallRatio = (drop.value / material.dropHeight).coerceIn(0f, 1f)
                val missCurve = missProgress.value.pow(1.35f)
                val missDirection = if (deterministicOffset(seed, material, 109) >= 0f) 1f else -1f
                val x = baseX + missDirection * missCurve * .88f
                val y = when (objectState.phase) {
                    ScenePhase.Missed -> -3.40f + material.dropHeight -
                        missProgress.value * (material.dropHeight + material.exitDistance + 1.1f)
                    ScenePhase.Exiting -> stackY.value - exitProgress.value * material.rise
                    else -> stackY.value + drop.value
                }
                val z = baseZ + missProgress.value * .34f
                val missScale = 1f - missProgress.value * .12f
                val variant = slot % modelInstances.size
                val instance = modelInstances[variant][slot / modelInstances.size]
                val extent = instance.asset.boundingBox.halfExtent.maxOrNull()!! * 2f
                val unitScale = material.scale * framingScale / extent
                ModelNode(
                    modelInstance = instance,
                    isVisible = objectState.phase != ScenePhase.AwaitingDisposal,
                    autoAnimate = false,
                    // Apply bottom alignment after normalization; constructor centering uses
                    // the unscaled bounds and displaces differently sized materials.
                    position = Position(
                        x - instance.asset.boundingBox.center[0] * unitScale,
                        y - (instance.asset.boundingBox.center[1] - instance.asset.boundingBox.halfExtent[1]) * unitScale,
                        z - instance.asset.boundingBox.center[2] * unitScale,
                    ),
                    scale = Scale(unitScale * missScale),
                    apply = { isShadowCaster = false; isShadowReceiver = false },
                    rotation = Rotation(
                        x = tilt + fallRatio * deterministicOffset(seed, material, 83) * 12f + missProgress.value * 42f,
                        y = yaw + fallRatio * deterministicOffset(seed, material, 97) * 20f + missDirection * missProgress.value * 70f,
                        z = -tilt * .45f + fallRatio * deterministicOffset(seed, material, 101) * 8f + missDirection * missProgress.value * 56f,
                    ),
                )
            }
        }
    }
}

private class BurstRenderLifecycle(private val host: Lifecycle) : LifecycleOwner {
    private val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.CREATED }
    override val lifecycle: Lifecycle = registry
    private var requested = false
    private val observer = LifecycleEventObserver { _, _ -> sync() }

    fun attach() { host.addObserver(observer); sync() }
    private fun sync() {
        if (registry.currentState == Lifecycle.State.DESTROYED) return
        registry.currentState = if (requested && host.currentState.isAtLeast(Lifecycle.State.RESUMED))
            Lifecycle.State.RESUMED else Lifecycle.State.STARTED
    }

    fun resume() {
        requested = true
        sync()
    }

    fun pause() {
        requested = false
        sync()
    }

    fun destroy() {
        host.removeObserver(observer)
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

private fun deterministicOffset(seed: Long, material: LiveMaterial, salt: Int): Float {
    val mixed = (seed * 1_103_515_245L + salt * 12_345L + material.ordinal * 97L) and 0x7fffffff
    return ((mixed % 10_000L) / 5_000f) - 1f
}
