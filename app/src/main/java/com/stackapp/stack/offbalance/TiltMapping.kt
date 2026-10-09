package com.stackapp.stack.offbalance

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.sign

/** Screen coordinates: positive input means the right edge moves down. */
object TiltMapping {
    fun gravityAcrossScreen(x:Float,y:Float,rotation:Int)=when(rotation){
        1 -> -y
        2 -> -x
        3 -> y
        else -> x
    }
    fun angle(x:Float,y:Float,rotation:Int)=asin((gravityAcrossScreen(x,y,rotation)/9.81f).coerceIn(-1f,1f))
    fun input(angle:Float,neutral:Float):Float {
        val delta=angle-neutral
        if(!delta.isFinite() || abs(delta)<.025f)return 0f
        // Gravity points toward the lowered edge; sensor X is positive to the left.
        return (-(delta-sign(delta)*.025f)/.24f).coerceIn(-1f,1f)
    }
}
