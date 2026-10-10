package com.stackapp.stack.offbalance

import com.stackapp.stack.BuildConfig

/** Controlled physics faults are available only in debug journey tests. */
object BalancePhysicsFactory {
    @Volatile var testCreate: ((Difficulty, Boolean, Int, Int) -> BalancePhysics)? = null
    fun create(mode:Difficulty,tutorial:Boolean,trial:Int,chance:Int):BalancePhysics =
        (if(BuildConfig.DEBUG)testCreate else null)?.invoke(mode,tutorial,trial,chance)
            ?: BalancePhysics(mode,tutorial,trial,chance)
}
