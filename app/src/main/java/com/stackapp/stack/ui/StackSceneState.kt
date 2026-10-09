package com.stackapp.stack.ui

internal enum class ScenePhase {
    Landing,
    ContactPending,
    Contacted,
    Settled,
    Exiting,
    Missed,
    AwaitingDisposal,
}

internal data class SceneObject(
    val id: Long,
    val generation: Long,
    val appearanceSeed: Long,
    val renderSlot: Int,
    val phase: ScenePhase,
    val intensity: Float = .82f,
    val settledBeforeCommit: Boolean = false,
)

internal data class StackSceneState(
    val material: LiveMaterial,
    val generation: Long,
    val objects: List<SceneObject>,
) {
    val settled: List<SceneObject> get() = objects.filter { it.phase == ScenePhase.Settled }
    val active: List<SceneObject> get() = objects.filter {
        it.phase == ScenePhase.Landing ||
            it.phase == ScenePhase.ContactPending ||
            it.phase == ScenePhase.Contacted
    }
    val exiting: List<SceneObject> get() = objects.filter { it.phase == ScenePhase.Exiting }
    val misses: List<SceneObject> get() = objects.filter { it.phase == ScenePhase.Missed }
    val isAnimating: Boolean get() = objects.any {
        it.phase != ScenePhase.Settled && it.phase != ScenePhase.AwaitingDisposal
    }
}

internal sealed interface AdmissionResult {
    data class Accepted(val objectId: Long) : AdmissionResult
    data class Missed(val objectId: Long) : AdmissionResult
    data object Ignored : AdmissionResult
}

internal object StackSceneReducer {
    private const val MISS_SLOT_COUNT = 6

    fun restore(
        count: Long,
        material: LiveMaterial,
        generation: Long = 0L,
    ): StackSceneState {
        val visible = count.coerceAtMost(material.visibleLayers.toLong()).toInt()
        val first = (count - visible + 1).coerceAtLeast(1)
        return StackSceneState(
            material = material,
            generation = generation,
            objects = List(visible) { offset ->
                val seed = first + offset
                SceneObject(
                    id = restoredLayerId(seed),
                    generation = generation,
                    appearanceSeed = seed,
                    renderSlot = offset,
                    phase = ScenePhase.Settled,
                )
            },
        )
    }

    fun admit(
        state: StackSceneState,
        eventId: Long,
        intensity: Float,
        forceMiss: Boolean = false,
    ): Pair<StackSceneState, AdmissionResult> {
        val scoringSlots = 0 until state.material.scoringPoolSize
        val usedScoring = state.objects
            .filter { it.renderSlot in scoringSlots }
            .mapTo(mutableSetOf()) { it.renderSlot }
        val freeScoring = scoringSlots.firstOrNull { it !in usedScoring }

        if (!forceMiss && state.active.size < state.material.maxConcurrentLandings && freeScoring != null) {
            val updated = state.objects + SceneObject(
                id = eventId,
                generation = state.generation,
                appearanceSeed = eventId,
                renderSlot = freeScoring,
                phase = ScenePhase.Landing,
                intensity = intensity,
            )
            return state.copy(objects = updated) to AdmissionResult.Accepted(eventId)
        }

        val missSlots = state.material.scoringPoolSize until
            (state.material.scoringPoolSize + MISS_SLOT_COUNT)
        val usedMisses = state.objects
            .filter { it.renderSlot in missSlots }
            .mapTo(mutableSetOf()) { it.renderSlot }
        val freeMiss = missSlots.firstOrNull { it !in usedMisses }
            ?: return state to AdmissionResult.Ignored
        val missed = SceneObject(
            id = eventId,
            generation = state.generation,
            appearanceSeed = eventId,
            renderSlot = freeMiss,
            phase = ScenePhase.Missed,
            intensity = intensity,
        )
        return state.copy(objects = state.objects + missed) to AdmissionResult.Missed(eventId)
    }

    fun beginContact(
        state: StackSceneState,
        objectId: Long,
        generation: Long,
    ): Pair<StackSceneState, Boolean> {
        val target = state.objects.firstOrNull { it.id == objectId }
        if (state.generation != generation || target?.phase != ScenePhase.Landing) return state to false
        return state.replace(objectId) { it.copy(phase = ScenePhase.ContactPending) } to true
    }

    fun contactCommitted(state: StackSceneState, objectId: Long, generation: Long): StackSceneState {
        if (state.generation != generation) return state
        val target = state.objects.firstOrNull { it.id == objectId }
        if (target?.phase != ScenePhase.ContactPending) return state
        val committed = state.replace(objectId) { it.copy(phase = ScenePhase.Contacted) }
        return if (target.settledBeforeCommit) settle(committed, objectId, generation) else committed
    }

    fun settle(state: StackSceneState, objectId: Long, generation: Long): StackSceneState {
        if (state.generation != generation) return state
        if (state.objects.firstOrNull { it.id == objectId }?.phase == ScenePhase.ContactPending) {
            return state.replace(objectId) { it.copy(settledBeforeCommit = true) }
        }
        val settled = state.replaceIf(objectId, ScenePhase.Contacted) { it.copy(phase = ScenePhase.Settled) }
        val overflow = (settled.settled.size - settled.material.visibleLayers).coerceAtLeast(0)
        if (overflow == 0) return settled
        val retiring = settled.settled.take(overflow).mapTo(mutableSetOf()) { it.id }
        return settled.copy(objects = settled.objects.map {
            if (it.id in retiring) it.copy(phase = ScenePhase.Exiting) else it
        })
    }

    fun animationFinished(state: StackSceneState, objectId: Long, generation: Long): StackSceneState {
        if (state.generation != generation) return state
        val target = state.objects.firstOrNull { it.id == objectId } ?: return state
        if (target.phase != ScenePhase.Exiting && target.phase != ScenePhase.Missed) return state
        return state.replace(objectId) { it.copy(phase = ScenePhase.AwaitingDisposal) }
    }

    fun releaseDisposed(state: StackSceneState, objectId: Long, generation: Long): StackSceneState {
        if (state.generation != generation) return state
        return state.copy(
            objects = state.objects.filterNot {
                it.id == objectId && it.phase == ScenePhase.AwaitingDisposal
            },
        )
    }

    fun cancelAndRestore(
        state: StackSceneState,
        count: Long,
        material: LiveMaterial = state.material,
    ): StackSceneState = restore(count, material, state.generation + 1)

    private inline fun StackSceneState.replace(
        objectId: Long,
        transform: (SceneObject) -> SceneObject,
    ) = copy(objects = objects.map { if (it.id == objectId) transform(it) else it })

    private inline fun StackSceneState.replaceIf(
        objectId: Long,
        expected: ScenePhase,
        transform: (SceneObject) -> SceneObject,
    ) = copy(objects = objects.map {
        if (it.id == objectId && it.phase == expected) transform(it) else it
    })

    private fun restoredLayerId(seed: Long) = Long.MIN_VALUE + seed
}
