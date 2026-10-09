package com.stackapp.stack.offbalance

import android.content.Context
import java.time.LocalDate

class BalancePreferences(context: Context) {
    private val prefs=context.getSharedPreferences("off_balance",Context.MODE_PRIVATE)
    var introduced: Boolean
        get()=prefs.getBoolean("introduced",false)
        set(v){prefs.edit().putBoolean("introduced",v).apply()}
    var difficulty: Difficulty
        get()=runCatching { Difficulty.valueOf(prefs.getString("difficulty","WOBBLY")!!) }.getOrDefault(Difficulty.WOBBLY)
        set(v){prefs.edit().putString("difficulty",v.name).apply()}
    var music: Float
        get()=prefs.getFloat("music",.7f)
        set(v){prefs.edit().putFloat("music",v.coerceIn(0f,1f)).apply()}
    var effects: Float
        get()=prefs.getFloat("effects",.85f)
        set(v){prefs.edit().putFloat("effects",v.coerceIn(0f,1f)).apply()}
    var sensitivity: Float
        get()=prefs.getFloat("sensitivity",.35f)
        set(v){prefs.edit().putFloat("sensitivity",v.coerceIn(0f,1f)).apply()}
    var touch: Boolean
        get()=prefs.getBoolean("touch",false)
        set(v){prefs.edit().putBoolean("touch",v).apply()}
    var haptics: Boolean
        get()=prefs.getBoolean("haptics",true)
        set(v){prefs.edit().putBoolean("haptics",v).apply()}
    var track: Int
        get()=prefs.getInt("track",0)
        set(v){prefs.edit().putInt("track",v.coerceIn(0,5)).apply()}
    var skin:BalanceSkin
        get()=runCatching{BalanceSkin.valueOf(prefs.getString("skin","GOLD")!!)}.getOrDefault(BalanceSkin.GOLD)
        set(v){prefs.edit().putString("skin",v.name).apply()}
    var completedRuns:Int
        get()=prefs.getInt("completed_runs",0)
        set(v){prefs.edit().putInt("completed_runs",v.coerceAtLeast(0)).apply()}
    var offerShownAt:Long
        get()=prefs.getLong("offer_shown",0)
        set(v){prefs.edit().putLong("offer_shown",v).apply()}
    var autoMusic: Boolean
        get()=prefs.getBoolean("auto_music",true)
        set(v){prefs.edit().putBoolean("auto_music",v).apply()}
    var trialsComplete: Int
        get()=prefs.getInt("trials",0)
        set(v){prefs.edit().putInt("trials",v).apply()}
    fun best(mode: Difficulty)=prefs.getInt("best_${mode.name}",0)
    fun today(mode: Difficulty): Int = if(prefs.getString("day","")==competitionDay()) prefs.getInt("today_${mode.name}",0) else 0
    fun record(mode: Difficulty,score:Int) {
        val edit=prefs.edit().putInt("best_${mode.name}",maxOf(best(mode),score))
        if(prefs.getString("day","")!=competitionDay()) Difficulty.entries.forEach { edit.remove("today_${it.name}") }
        edit.putString("day",competitionDay()).putInt("today_${mode.name}",maxOf(today(mode),score)).apply()
    }
}
