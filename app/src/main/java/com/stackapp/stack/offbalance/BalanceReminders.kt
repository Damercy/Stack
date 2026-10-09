package com.stackapp.stack.offbalance

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.*
import com.stackapp.stack.MainActivity
import com.stackapp.stack.R
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

data class ReminderContext(val enabled:Boolean,val permitted:Boolean,val now:Long,val lastPlayed:Long,val lastSent:Long,val hour:Int)
fun shouldRemind(c:ReminderContext,config:BalanceConfig=BalanceConfig())=config.reminders && c.enabled && c.permitted && c.lastPlayed>0 && c.now-c.lastPlayed in config.reminderAwayHours*3_600_000L..7*24*60*60*1000L && c.now-c.lastSent>=config.reminderCooldownHours*3_600_000L && c.hour in 10..20
fun shouldOfferPurchase(enabled:Boolean,catalogReady:Boolean,playing:Boolean,runs:Int,newBest:Boolean,now:Long,lastShown:Long)=
    enabled && catalogReady && !playing && runs>=3 && newBest && now-lastShown>=24*60*60*1000L

class BalanceReminders(context:Context) {
    private val context=context.applicationContext
    private val prefs=context.getSharedPreferences("balance_reminders",Context.MODE_PRIVATE)
    var enabled:Boolean
        get()=prefs.getBoolean("enabled",false)
        set(v){prefs.edit().putBoolean("enabled",v).apply();sync()}
    fun played(){prefs.edit().putLong("played",System.currentTimeMillis()).apply();context.getSystemService(NotificationManager::class.java).cancel(72)}
    fun permitted()=Build.VERSION.SDK_INT<33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
    fun sync(){
        val work=WorkManager.getInstance(context)
        if(!enabled || !permitted() || !BalanceRemoteConfig.cached(context).reminders){work.cancelUniqueWork("balance-reminders");return}
        val request=PeriodicWorkRequestBuilder<BalanceReminderWorker>(6,TimeUnit.HOURS).setInitialDelay(6,TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()).build()
        work.enqueueUniquePeriodicWork("balance-reminders",ExistingPeriodicWorkPolicy.KEEP,request)
    }
    suspend fun check(repository:CompetitionRepository=CompetitionFactory.create(),now:Long=System.currentTimeMillis(),hour:Int=java.time.LocalTime.now().hour):Boolean {
        val config=BalanceRemoteConfig.cached(context)
        if(!shouldRemind(ReminderContext(enabled,permitted(),now,prefs.getLong("played",0),prefs.getLong("sent",0),hour),config))return false
        val local=BalancePreferences(context);var title="Your next tower";var message="Best: ${local.best(local.difficulty)}. Play again?"
        var alertKey:String?=null;var alertScore=0
        if(repository.available && config.friends && config.rivalReminders) {
            val own=repository.own();val rivals=repository.load(RivalStore(context).ids())
            val rival=rivals.filter{it.score(local.difficulty)>maxOf(local.best(local.difficulty),own?.score(local.difficulty)?:0)}
                .filter{prefs.getInt("rival_${it.id}_${local.difficulty.name}",-1)<it.score(local.difficulty)}.maxByOrNull{it.score(local.difficulty)}
            if(rival!=null){title="${rival.username} moved ahead";message="${rival.score(local.difficulty)} in ${local.difficulty.title.lowercase()}. Your turn?";alertKey="rival_${rival.id}_${local.difficulty.name}";alertScore=rival.score(local.difficulty)}
        }
        // One daily reminder at most, including retries and duplicate worker execution.
        if(prefs.getString("sent_day","")==competitionDay())return false
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("balance-play","Play reminders",NotificationManager.IMPORTANCE_DEFAULT))
        val open=context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .putExtra("open_rivals",config.friends).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending=PendingIntent.getActivity(context,72,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(context,"balance-play").setSmallIcon(R.drawable.ic_launcher_monochrome).setContentTitle(title).setContentText(message).setContentIntent(pending).setAutoCancel(true).build()
        if(!enabled || !permitted() || !BalanceRemoteConfig.cached(context).reminders)return false
        manager.notify(72,notification)
        val edit=prefs.edit().putLong("sent",now).putString("sent_day",competitionDay())
        if(alertKey!=null)edit.putInt(alertKey,alertScore)
        edit.apply();return true
    }
}
class BalanceReminderWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result=try {withTimeout(20_000){BalanceReminders(applicationContext).check()};Result.success()}
        catch(_:TimeoutCancellationException){Result.retry()}
        catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){Result.retry()}
}
