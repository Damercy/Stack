package com.stackapp.stack.offbalance

import android.content.Context
import com.stackapp.stack.storage.StackDatabase

/** One-time migration from preview progress into the public game's first season.
 * Call on an IO dispatcher before reading any game state. Purchases and auth stay intact. */
object PublicLaunchReset {
    fun prepare(context:Context):Boolean {
        val marker=context.getSharedPreferences("stack_launch",Context.MODE_PRIVATE)
        if(marker.getInt("epoch",0)>=1)return false
        StackDatabase.get(context).clearAllTables()
        listOf("tap_store","off_balance","rivals","balance_reminders").forEach {
            check(context.getSharedPreferences(it,Context.MODE_PRIVATE).edit().clear().commit())
        }
        check(marker.edit().putInt("epoch",1).commit())
        return true
    }
}
