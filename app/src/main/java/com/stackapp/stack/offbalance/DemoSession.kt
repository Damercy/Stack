package com.stackapp.stack.offbalance

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.flow.MutableStateFlow

/** A local sandbox: no authentication, billing, public scores or production preferences. */
class DemoSession(context:Context) {
    val storage=object:ContextWrapper(context.applicationContext) {
        override fun getApplicationContext():Context=this
        override fun getSharedPreferences(name:String,mode:Int)=
            baseContext.getSharedPreferences("stack_demo_$name",mode)
    }
    private val saved=storage.getSharedPreferences("identity",Context.MODE_PRIVATE)
    init { BalancePreferences(storage).trialsComplete=4 }
    val config=MutableStateFlow(BalanceConfig(styleDiscovery=true,googleSignIn=true,reminders=false,rivalReminders=false,reviews=false))
    val account=object:BalanceAccount {
        override val state=MutableStateFlow(AccountState(configured=true,signedIn=true,name="Demo Player",email="player@example.com",demo=true))
        override fun configure(enabled:Boolean)=Unit
        override suspend fun signIn(activity:Activity){state.value=state.value.copy(signedIn=true,revision=state.value.revision+1)}
        override suspend fun useSavedProfile()=Unit
        override fun cancelSwitch()=Unit
        override suspend fun signOut(){state.value=state.value.copy(signedIn=false,revision=state.value.revision+1)}
        override fun close()=Unit
    }
    val pack=object:StylePackStore {
        override val state=MutableStateFlow(StylePackState(owned=true,message="Demo access · no purchase made"))
        override suspend fun sync(config:BalanceConfig)=Unit
        override fun buy(activity:Activity,config:BalanceConfig)=Unit
        override fun restore(){state.value=state.value.copy(message="Demo access · no purchase made")}
        override fun close()=Unit
    }
    val competition=object:CompetitionRepository {
        override val available=true
        private fun scores(value:Int)=Difficulty.entries.associate{it.name to value}
        private val samples=listOf("orbit_ada" to 42,"neon_sam" to 35,"soft_landing" to 28).mapIndexed{i,(name,score)->
            Competitor("demo_$i",name,scores(score+12),competitionDay(),scores(score))
        }
        private var player:Competitor?=Competitor("demo_self",saved.getString("username","demo_player")!!,scores(12),competitionDay(),scores(12))
        override suspend fun own()=player
        override suspend fun claim(name:String):Competitor {
            require(validUsername(name))
            if(samples.any{usernameKey(it.username)==usernameKey(name)})throw NameUnavailable()
            player=(player ?: Competitor("demo_self",name,scores(0),competitionDay(),scores(0))).copy(username=name)
            saved.edit().putString("username",name).apply()
            return player!!
        }
        override suspend fun publish(mode:Difficulty,best:Int,today:Int):Competitor? {
            player=player?.let{it.copy(best=it.best+(mode.name to maxOf(it.score(mode),best)),day=competitionDay(),daily=it.daily+(mode.name to maxOf(it.score(mode,true),today)))}
            return player
        }
        override suspend fun search(prefix:String)=if(prefix.trim().length<2)emptyList() else samples.filter{usernameKey(it.username).startsWith(usernameKey(prefix))}
        override suspend fun load(ids:Set<String>)=samples.filter{it.id in ids}
        override suspend fun leaders(mode:Difficulty)=(samples+listOfNotNull(player)).sortedWith(compareByDescending<Competitor>{it.score(mode,true)}.thenBy{usernameKey(it.username)}).take(3)
        override suspend fun remove(){player=null;saved.edit().remove("username").apply()}
    }
    companion object {
        /** Called off the main thread only when starting a new sandbox. */
        fun reset(context:Context){listOf("off_balance","rivals","balance_flags","balance_reminders","identity").forEach{
            context.getSharedPreferences("stack_demo_$it",Context.MODE_PRIVATE).edit().clear().commit()
        }}
    }
}
