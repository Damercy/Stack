package com.stackapp.stack.offbalance

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class BalancePhysicsTest {
    private fun settle(game:BalancePhysics,seconds:Double=2.0):BalanceFrame {
        var frame=game.snapshot();repeat((seconds*60).toInt()){frame=game.advance(BalancePhysics.STEP)};return frame
    }
    @Test fun centeredSlabLandsOnceAndRemainsSupported() {
        val game=BalancePhysics(Difficulty.STEADY,true)
        assertTrue(game.drop());assertFalse(game.drop())
        val landed=settle(game)
        assertEquals(1,landed.score);assertFalse(landed.down);assertTrue(landed.canDrop)
        val resting=settle(game,4.0)
        assertEquals(1,resting.score);assertFalse(resting.down)
        assertTrue(resting.pieces.all{it.y>0})
    }
    @Test fun renderCadenceDoesNotChangePhysics() {
        val a=BalancePhysics(Difficulty.WOBBLY);val b=BalancePhysics(Difficulty.WOBBLY)
        a.drop();b.drop();repeat(120){a.advance(1.0/60)};repeat(60){b.advance(1.0/30)}
        assertEquals(a.snapshot().score,b.snapshot().score)
        a.snapshot().pieces.zip(b.snapshot().pieces).forEach {(x,y)->assertEquals(x.x,y.x,.002f);assertEquals(x.y,y.y,.002f)}
    }
    @Test fun zeroElapsedPreservesFrozenTower() {
        val game=BalancePhysics(Difficulty.WOBBLY);game.drop();settle(game)
        val frame=game.snapshot();repeat(120){game.advance(0.0)}
        assertEquals(frame,game.snapshot())
    }
    @Test fun excessiveTiltHasPhysicalConsequences() {
        val game=BalancePhysics(Difficulty.CHAOS);game.drop();settle(game);game.setInput(1f)
        val frame=settle(game,10.0)
        assertTrue(frame.down || abs(frame.lean)>.4f)
        assertNotEquals(0f,frame.pieces.first().x)
    }
    @Test fun hitchCatchUpIsBounded() {
        val a=BalancePhysics(Difficulty.STEADY);val b=BalancePhysics(Difficulty.STEADY)
        a.drop();b.drop();a.advance(600.0);b.advance(.1)
        assertEquals(a.snapshot(),b.snapshot())
    }
    @Test fun discTrialRequiresLandingBeforeHold() {
        val game=BalancePhysics(Difficulty.STEADY,trial=1)
        assertEquals(PieceKind.DISC,game.snapshot().incoming?.kind)
        assertEquals(0f,settle(game).holdSeconds,0f)
        game.drop();val frame=settle(game,5.0)
        assertEquals(1,frame.score);assertTrue(frame.holdSeconds>=3);assertFalse(frame.down)
    }
    @Test fun fiveCenteredLandingsKeepTheBottomPieces() {
        val game=BalancePhysics(Difficulty.STEADY,trial=0)
        repeat(5) { i ->
            var attempts=0
            while(abs(game.snapshot().incoming!!.x)>.08f && attempts++<300)game.advance(BalancePhysics.STEP)
            assertTrue(game.drop());val frame=settle(game,2.0)
            assertEquals(i+1,frame.score);assertEquals(i+2,frame.pieces.size);assertFalse("Layer ${i+1}: $frame",frame.down)
            assertTrue(frame.pieces.first().y>.1f)
        }
    }
    @Test fun oppositeInputsProduceOppositeLeans() {
        val a=BalancePhysics(Difficulty.STEADY);val b=BalancePhysics(Difficulty.STEADY)
        a.drop();b.drop();settle(a);settle(b);a.setInput(.35f);b.setInput(-.35f)
        val x=settle(a,.5);val y=settle(b,.5)
        assertTrue(x.lean>0);assertTrue(y.lean<0);assertEquals(x.lean,-y.lean,.02f)
    }
    @Test fun collapseCannotAwardAnotherLayer() {
        val game=BalancePhysics(Difficulty.CHAOS);game.drop();settle(game);game.setInput(1f)
        val down=settle(game,10.0);assertTrue(down.down)
        val finished=settle(game,3.0)
        assertEquals(down.score,finished.score);assertFalse(game.drop());assertFalse(finished.canDrop)
    }
    @Test fun rapidTapsCannotSpawnMultiplePendingPieces() {
        val game=BalancePhysics(Difficulty.STEADY)
        assertTrue(game.drop());repeat(1000){assertFalse(game.drop())}
        assertEquals(2,game.snapshot().pieces.size);assertEquals(0,game.snapshot().score)
    }
    @Test fun previewsMatchDroppedGeometryInEveryDifficulty() {
        Difficulty.entries.forEach{mode->val game=BalancePhysics(mode);val preview=game.snapshot().incoming!!
            game.drop();val dropped=game.snapshot().pieces.last()
            assertEquals(preview.width,dropped.width,0f);assertEquals(preview.height,dropped.height,0f);assertEquals(preview.kind,dropped.kind)}
    }
    @Test fun detachedPiecesEndRunsEvenWithAnUprightBeam() {
        val game=BalancePhysics(Difficulty.STEADY);game.drop();settle(game)
        // Reproduce a fallen piece while leaving the beam horizontal.
        val field=BalancePhysics::class.java.getDeclaredField("bodies").apply{isAccessible=true}
        @Suppress("UNCHECKED_CAST") val pieces=field.get(game) as List<Pair<org.jbox2d.dynamics.Body,PiecePose>>
        pieces.last().first.setTransform(org.jbox2d.common.Vec2(2.5f,-.1f),0f)
        val frame=game.advance(BalancePhysics.STEP)
        assertTrue(frame.down);assertFalse(frame.canDrop);val score=frame.score
        repeat(100){assertFalse(game.drop());game.advance(.05)}
        assertEquals(score,game.snapshot().score)
    }
    @Test fun difficultyRampsOnlyAfterLandingAndWithoutTeleporting() {
        val game=BalancePhysics(Difficulty.STEADY);assertEquals(1f,game.speedMultiplier(),0f)
        game.drop();game.advance(.1);assertEquals(1f,game.speedMultiplier(),0f)
        settle(game);assertEquals(1.02f,game.speedMultiplier(),.001f)
        val before=game.snapshot().incoming!!.x;game.advance(BalancePhysics.STEP)
        assertTrue(abs(before-game.snapshot().incoming!!.x)<.05f)
    }
    @Test fun failuresAreCadenceIndependentAcrossSlowAndHighRefreshScreens() {
        val a=BalancePhysics(Difficulty.CHAOS);val b=BalancePhysics(Difficulty.CHAOS)
        a.drop();b.drop();settle(a);settle(b);a.setInput(1f);b.setInput(1f)
        repeat(720){a.advance(1.0/60)};repeat(1440){b.advance(1.0/120)}
        assertTrue(a.snapshot().down);assertEquals(a.snapshot().down,b.snapshot().down);assertEquals(a.snapshot().score,b.snapshot().score)
    }
    @Test fun idleTimeNeverAdvancesTrialHoldOrAwardsScores() {
        val game=BalancePhysics(Difficulty.STEADY,trial=1);repeat(10000){game.advance(.1)}
        assertEquals(0f,game.snapshot().holdSeconds,0f);assertEquals(0,game.snapshot().score)
    }
    @Test fun piecesSupportedOnATiltedBeamDoNotCountAsFallen(){
        val game=BalancePhysics(Difficulty.STEADY);game.drop()
        val bodyField=BalancePhysics::class.java.getDeclaredField("bodies").apply{isAccessible=true}
        @Suppress("UNCHECKED_CAST") val bodies=bodyField.get(game) as List<Pair<org.jbox2d.dynamics.Body,PiecePose>>
        val beamField=BalancePhysics::class.java.getDeclaredField("beam").apply{isAccessible=true}
        val beam=beamField.get(game) as org.jbox2d.dynamics.Body
        val angle=.45f;beam.setTransform(org.jbox2d.common.Vec2(0f,0f),angle)
        bodies.forEach{(body,_)->val y=body.position.y;body.setTransform(org.jbox2d.common.Vec2(-kotlin.math.sin(angle)*y,kotlin.math.cos(angle)*y),angle)}
        assertFalse(game.advance(BalancePhysics.STEP).down)
    }
    @Test fun invalidSensorInputAndClockValuesCannotCorruptPhysics(){
        val game=BalancePhysics(Difficulty.STEADY);game.drop();game.setInput(Float.NaN)
        game.advance(Double.NaN);game.advance(-100.0);val frame=settle(game)
        assertEquals(1,frame.score);assertTrue(frame.pieces.all{it.x.isFinite() && it.y.isFinite()});assertFalse(frame.down)
    }
    @Test fun easyModeBuildsTwelveSlabsBeforeIntroducingABarrel(){
        val game=BalancePhysics(Difficulty.STEADY)
        repeat(12){layer->
            assertEquals(PieceKind.SLAB,game.snapshot().incoming!!.kind)
            var ticks=0
            while(game.snapshot().incoming!=null && abs(game.snapshot().incoming!!.x)>.025f && ticks++<600)game.advance(BalancePhysics.STEP)
            assertFalse("Layer ${layer+1} fell while waiting",game.snapshot().down)
            assertTrue(game.drop());val frame=settle(game,1.5)
            assertFalse("Layer ${layer+1} fell: $frame",frame.down);assertEquals(layer+1,frame.score)
        }
        assertEquals(PieceKind.DISC,game.snapshot().incoming!!.kind)
    }
    @Test fun mixedTrialShapesCanStackOnTheirActualContactSurfaces(){
        val game=BalancePhysics(Difficulty.STEADY,trial=3)
        repeat(4){layer->
            var ticks=0
            while(abs(game.snapshot().incoming!!.x)>.015f && ticks++<600)game.advance(BalancePhysics.STEP)
            assertTrue(game.drop());val frame=settle(game,2.0)
            assertFalse("Mixed layer ${layer+1} fell",frame.down);assertEquals(layer+1,frame.score)
        }
        assertTrue(game.snapshot().pieces.any{it.kind==PieceKind.DISC})
        assertTrue(game.snapshot().pieces.any{it.kind==PieceKind.WEDGE})
    }
    @Test fun collapseRunsUntilSettledOrTheBoundedAnimationLimit(){
        val game=BalancePhysics(Difficulty.CHAOS);game.drop();settle(game);game.setInput(1f)
        var ticks=0
        while(!game.snapshot().down && ticks++<900)game.advance(BalancePhysics.STEP)
        assertTrue(game.snapshot().down);assertFalse(game.collapseFinished)
        val failed=game.snapshot();game.advance(.1)
        assertFalse(game.collapseFinished);assertNotEquals(failed.pieces,game.snapshot().pieces)
        var collapseTicks=0
        while(!game.collapseFinished && collapseTicks++<500)game.advance(BalancePhysics.STEP)
        assertTrue(game.collapseFinished);assertEquals(failed.score,game.snapshot().score);assertFalse(game.drop())
        val fresh=BalancePhysics(Difficulty.CHAOS)
        assertEquals(0,fresh.snapshot().score);assertEquals(1,fresh.snapshot().pieces.size);assertFalse(fresh.collapseFinished)
    }
}
