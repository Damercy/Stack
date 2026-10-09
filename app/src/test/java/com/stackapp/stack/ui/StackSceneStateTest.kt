package com.stackapp.stack.ui

import org.junit.Assert.*
import org.junit.Test

class StackSceneStateTest {
    @Test fun slowStorageCanCommitAfterReboundWithoutKeepingRendererAwake() {
        var state = StackSceneReducer.admit(StackSceneReducer.restore(0, LiveMaterial.Stone), 1, .8f).first
        state = StackSceneReducer.beginContact(state, 1, state.generation).first
        state = StackSceneReducer.settle(state, 1, state.generation)
        assertTrue(state.objects.single().settledBeforeCommit)
        state = StackSceneReducer.contactCommitted(state, 1, state.generation)
        assertEquals(ScenePhase.Settled, state.objects.single().phase)
        assertFalse(state.isAnimating)
    }
    @Test fun restoringLargeCountsCreatesOnlyTheVisibleWindow() {
        val state = StackSceneReducer.restore(10_000, LiveMaterial.Paper)
        assertEquals(LiveMaterial.Paper.visibleLayers, state.settled.size)
        assertEquals(10_000L, state.settled.last().appearanceSeed)
        assertTrue(state.active.isEmpty())
        assertFalse(state.isAnimating)
    }
    @Test fun contactCommitsOnceAndReboundRemainsActiveUntilSettled() {
        var state = StackSceneReducer.admit(StackSceneReducer.restore(0, LiveMaterial.Paper), 1, .8f).first
        val first = StackSceneReducer.beginContact(state, 1, state.generation)
        assertTrue(first.second)
        state = StackSceneReducer.contactCommitted(first.first, 1, state.generation)
        assertEquals(ScenePhase.Contacted, state.objects.single().phase)
        assertTrue(state.isAnimating)
        assertFalse(StackSceneReducer.beginContact(state, 1, state.generation).second)
        state = StackSceneReducer.settle(state, 1, state.generation)
        assertFalse(state.isAnimating)
    }
    @Test fun capacityRetiresOldestOnlyAfterNewLayerSettles() {
        var state = StackSceneReducer.restore(8, LiveMaterial.Stone)
        val originalIds = state.settled.map { it.id }
        state = StackSceneReducer.admit(state, 9, .8f).first
        state = StackSceneReducer.beginContact(state, 9, state.generation).first
        state = StackSceneReducer.contactCommitted(state, 9, state.generation)
        assertEquals(originalIds, state.settled.map { it.id })
        assertTrue(state.exiting.isEmpty())
        state = StackSceneReducer.settle(state, 9, state.generation)
        assertEquals(8, state.settled.size)
        assertEquals(originalIds.first(), state.exiting.single().id)
    }
    @Test fun repeatedOverflowNeverAliasesSlotsAndAlwaysConverges() {
        LiveMaterial.entries.forEach { material ->
            var state = StackSceneReducer.restore(100, material)
            repeat(250) { index ->
                val id = index + 1L
                state = StackSceneReducer.admit(state, id, .8f).first
                state = StackSceneReducer.beginContact(state, id, state.generation).first
                state = StackSceneReducer.contactCommitted(state, id, state.generation)
                state = StackSceneReducer.settle(state, id, state.generation)
                assertEquals(state.objects.size, state.objects.map { it.renderSlot }.distinct().size)
                assertTrue(state.objects.all { it.renderSlot in 0 until material.totalPoolSize })
                state.exiting.forEach { retired ->
                    state = StackSceneReducer.animationFinished(state, retired.id, retired.generation)
                    state = StackSceneReducer.releaseDisposed(state, retired.id, retired.generation)
                }
                assertEquals(material.visibleLayers, state.settled.size)
                assertFalse(state.isAnimating)
            }
        }
    }
    @Test fun burstInputIsBoundedAndDoesNotEvictSettledLayersBeforeContact() {
        var state = StackSceneReducer.restore(100, LiveMaterial.Library)
        val baseline = state.settled
        repeat(100) { state = StackSceneReducer.admit(state, it + 1L, .8f).first }
        assertEquals(baseline, state.settled)
        assertEquals(LiveMaterial.Library.maxConcurrentLandings, state.active.size)
        assertEquals(6, state.misses.size)
        assertTrue(state.objects.size <= state.material.totalPoolSize)
    }
    @Test fun oldGenerationCallbacksCannotModifyRestoredScene() {
        val old = StackSceneReducer.admit(StackSceneReducer.restore(0, LiveMaterial.Stone), 1, .8f).first
        val reset = StackSceneReducer.cancelAndRestore(old, 7)
        assertFalse(StackSceneReducer.beginContact(reset, 1, old.generation).second)
        assertEquals(reset, StackSceneReducer.contactCommitted(reset, 1, old.generation))
        assertEquals(reset, StackSceneReducer.settle(reset, 1, old.generation))
        assertEquals(reset, StackSceneReducer.animationFinished(reset, 1, old.generation))
        assertEquals(reset, StackSceneReducer.releaseDisposed(reset, 1, old.generation))
    }
    @Test fun disposedSlotIsUnavailableUntilExplicitRelease() {
        var state = StackSceneReducer.admit(StackSceneReducer.restore(0, LiveMaterial.Stone), 1, .8f, forceMiss = true).first
        val slot = state.misses.single().renderSlot
        state = StackSceneReducer.animationFinished(state, 1, state.generation)
        assertTrue(StackSceneReducer.admit(state, 2, .8f, forceMiss = true).first.misses.single().renderSlot != slot)
        state = StackSceneReducer.releaseDisposed(state, 1, state.generation)
        assertEquals(slot, StackSceneReducer.admit(state, 3, .8f, forceMiss = true).first.misses.single().renderSlot)
    }
    @Test fun outOfOrderSettlementRetainsChronologicalLayerOrder() {
        var state = StackSceneReducer.restore(0, LiveMaterial.Library)
        (1L..4L).forEach { state = StackSceneReducer.admit(state, it, .8f).first }
        (4L downTo 1L).forEach {
            state = StackSceneReducer.beginContact(state, it, state.generation).first
            state = StackSceneReducer.contactCommitted(state, it, state.generation)
            state = StackSceneReducer.settle(state, it, state.generation)
        }
        assertEquals(listOf(1L, 2L, 3L, 4L), state.settled.map { it.id })
        assertFalse(state.isAnimating)
    }
}
