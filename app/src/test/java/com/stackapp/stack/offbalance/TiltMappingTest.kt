package com.stackapp.stack.offbalance
import org.junit.Assert.*
import org.junit.Test

class TiltMappingTest {
    @Test fun loweringTheLeftEdgeMovesLeftInEveryRotation(){
        listOf(Triple(2f,0f,0),Triple(0f,-2f,1),Triple(-2f,0f,2),Triple(0f,2f,3)).forEach{(x,y,r)->assertTrue(TiltMapping.input(TiltMapping.angle(x,y,r),0f)<0)}
    }
    @Test fun loweringTheRightEdgeMovesRightInEveryRotation(){
        listOf(Triple(-2f,0f,0),Triple(0f,2f,1),Triple(2f,0f,2),Triple(0f,-2f,3)).forEach{(x,y,r)->assertTrue(TiltMapping.input(TiltMapping.angle(x,y,r),0f)>0)}
    }
    @Test fun calibrationDeadZoneAndBadSamplesNeverMoveTheBeam(){
        assertEquals(0f,TiltMapping.input(.4f,.4f),0f);assertEquals(0f,TiltMapping.input(.02f,0f),0f)
        assertEquals(0f,TiltMapping.input(Float.NaN,0f),0f);assertEquals(1f,TiltMapping.input(-10f,0f),0f)
    }
    @Test fun sensorDirectionMatchesTheActualBeam(){
        val game=BalancePhysics(Difficulty.STEADY);game.drop();repeat(120){game.advance(BalancePhysics.STEP)}
        game.setInput(TiltMapping.input(TiltMapping.angle(1.3f,0f,0),0f));repeat(30){game.advance(BalancePhysics.STEP)}
        assertTrue(game.snapshot().lean<0f)
    }
}
