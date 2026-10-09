package com.stackapp.stack.offbalance

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.*
import com.stackapp.stack.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/** Remote controls describe capabilities already included in the installed app. */
data class BalanceConfig(
    val payments:Boolean=false, val verificationReady:Boolean=false,
    val product:String="off_balance_style_pack", val styleDiscovery:Boolean=false,
    val offerResults:Boolean=false, val offerTrials:Boolean=false, val offerSettings:Boolean=false,
    val minimumRuns:Int=3, val offerCooldownHours:Int=24,
    val celebrations:Boolean=true, val celebrationMillis:Int=1100, val milestoneEvery:Int=5,
    val friends:Boolean=true, val reminders:Boolean=true, val rivalReminders:Boolean=true,
    val reminderAwayHours:Int=6, val reminderCooldownHours:Int=24,
    val inviteAfterRuns:Int=3, val trials:Boolean=true,
    val reviews:Boolean=true, val reviewMinRuns:Int=5, val reviewAgeHours:Int=24,
    val googleSignIn:Boolean=false,
    val offerHome:Boolean=false,
) {
    fun homeOfferEligible(runs:Int,owned:Boolean,ready:Boolean,now:Long,lastShown:Long)=
        offerHome && payments && verificationReady && ready && !owned && runs>=minimumRuns && now-lastShown>=offerCooldownHours*3_600_000L
    fun offerEligible(runs:Int,newBest:Boolean,trialCompleted:Boolean,playing:Boolean,owned:Boolean,now:Long,lastShown:Long)=
        payments && verificationReady && !playing && !owned && runs>=minimumRuns &&
            (offerResults && newBest || offerTrials && trialCompleted) && now-lastShown>=offerCooldownHours*3_600_000L
    companion object {
        val defaults=mapOf<String,Any>(
            "payments_enabled" to false,"payment_verification_ready" to false,"style_pack_product_id" to "off_balance_style_pack",
            "style_pack_discovery_enabled" to false,"offer_results_enabled" to false,"offer_trials_enabled" to false,"offer_settings_enabled" to false,
            "offer_min_runs" to 3,"offer_cooldown_hours" to 24,"celebrations_enabled" to true,"celebration_duration_ms" to 1100,"milestone_every" to 5,
            "friends_enabled" to true,"reminders_enabled" to true,"rival_reminders_enabled" to true,"reminder_away_hours" to 6,"reminder_cooldown_hours" to 24,
            "invite_after_runs" to 3,"trials_enabled" to true,
            "reviews_enabled" to true,"review_min_runs" to 5,"review_min_age_hours" to 24,"google_sign_in_enabled" to false,
            "offer_home_enabled" to false,
        )
        fun decode(values:Map<String,Any>):BalanceConfig {
            fun bool(key:String)=values[key].toString().toBooleanStrictOrNull() ?: defaults[key] as Boolean
            fun number(key:String,min:Int,max:Int)=(values[key].toString().toIntOrNull() ?: defaults[key].toString().toInt()).coerceIn(min,max)
            val product=values["style_pack_product_id"].toString().takeIf{it.matches(Regex("[a-z0-9_.]{3,100}"))} ?: "off_balance_style_pack"
            return BalanceConfig(bool("payments_enabled"),bool("payment_verification_ready"),product,bool("style_pack_discovery_enabled"),
                bool("offer_results_enabled"),bool("offer_trials_enabled"),bool("offer_settings_enabled"),number("offer_min_runs",3,100),number("offer_cooldown_hours",24,720),
                bool("celebrations_enabled"),number("celebration_duration_ms",600,1600),number("milestone_every",5,50),bool("friends_enabled"),bool("reminders_enabled"),bool("rival_reminders_enabled"),
                number("reminder_away_hours",6,72),number("reminder_cooldown_hours",24,168),number("invite_after_runs",3,100),bool("trials_enabled"),
                bool("reviews_enabled"),number("review_min_runs",5,100),number("review_min_age_hours",0,720),bool("google_sign_in_enabled"),bool("offer_home_enabled"))
        }
    }
}

class BalanceRemoteConfig(context:Context) {
    private val prefs=context.applicationContext.getSharedPreferences("balance_flags",Context.MODE_PRIVATE)
    private val owner=SupervisorJob()
    private val scope=CoroutineScope(owner+Dispatchers.IO)
    val state=MutableStateFlow(cached(context))
    val updateRevision=MutableStateFlow(0)
    private val remote=if(BuildConfig.USE_FIREBASE && runCatching{FirebaseApp.getInstance()}.isSuccess)FirebaseRemoteConfig.getInstance() else null
    private var listener:ConfigUpdateListenerRegistration?=null
    @Volatile private var pending=false
    init {
        owner.invokeOnCompletion{listener?.remove()}
        if(remote!=null)scope.launch {
            try {
                remote.setDefaultsAsync(BalanceConfig.defaults).await()
                remote.setConfigSettingsAsync(FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(43_200).setFetchTimeoutInSeconds(8).build()).await()
                remote.activate().await();publish()
                listener=remote.addOnConfigUpdateListener(object:ConfigUpdateListener {
                    override fun onUpdate(update:ConfigUpdate){pending=true;updateRevision.update{it+1}}
                    override fun onError(error:FirebaseRemoteConfigException)=Unit
                })
                remote.fetch().await();pending=true;updateRevision.update{it+1}
            }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){ /* Offline defaults remain usable. */ }
        }
    }
    /** Apply staged values on navigation or resume, never in the middle of a tower. */
    suspend fun boundary(){if(remote!=null && pending){
        pending=false
        try{remote.activate().await();publish()}
        catch(cancelled:CancellationException){pending=true;throw cancelled}
        catch(_:Exception){pending=true}
    }}
    private fun publish(){
        val values=BalanceConfig.defaults.mapValues{(key,default)->if(default is Boolean)remote!!.getBoolean(key) else if(default is Number)remote!!.getLong(key).toInt() else remote!!.getString(key)}
        state.value=BalanceConfig.decode(values)
        prefs.edit().putString("values",JSONObject(values).toString()).apply()
    }
    fun close(){listener?.remove();scope.cancel()}
    companion object {
        fun cached(context:Context):BalanceConfig=runCatching{
            val json=JSONObject(context.getSharedPreferences("balance_flags",Context.MODE_PRIVATE).getString("values","{}")!!)
            BalanceConfig.decode(BalanceConfig.defaults.mapValues{(key,value)->json.opt(key)?:value})
        }.getOrDefault(BalanceConfig())
    }
}
