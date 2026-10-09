package com.stackapp.stack.offbalance

import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewManagerFactory
import com.stackapp.stack.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

data class ReviewMoment(val newBest:Boolean=false,val layers:Int=0,val trialsComplete:Int=0,val returned:Boolean=false)
fun reviewEligible(config:BalanceConfig,runs:Int,firstPlayed:Long,now:Long,attempted:Boolean,lastTried:Long,moment:ReviewMoment):Boolean =
    config.reviews && !attempted && firstPlayed>0 && now-firstPlayed>=config.reviewAgeHours*3_600_000L && runs>=config.reviewMinRuns &&
        (lastTried==0L || now-lastTried>=30L*86_400_000) &&
        (moment.newBest && moment.layers>=10 || moment.trialsComplete>=2 || moment.returned && runs>=8)

interface ReviewGateway { val available:Boolean; suspend fun request(); suspend fun launch(activity:Activity) }
object ReviewGatewayFactory {
    @Volatile var testGateway:ReviewGateway?=null
    fun create(context:Context):ReviewGateway=if(BuildConfig.DEBUG && testGateway!=null)testGateway!! else PlayReviewGateway(context)
}
private class PlayReviewGateway(private val context:Context):ReviewGateway {
    private val manager=ReviewManagerFactory.create(context)
    private var info:com.google.android.play.core.review.ReviewInfo?=null
    override val available get()=runCatching{
        @Suppress("DEPRECATION")
        context.packageManager.getInstallerPackageName(context.packageName)=="com.android.vending"
    }.getOrDefault(false)
    override suspend fun request(){info=manager.requestReviewFlow().await()}
    override suspend fun launch(activity:Activity){val review=info ?: return;info=null;manager.launchReviewFlow(activity,review).await()}
}
/** Play never reports whether a card was displayed or a rating was submitted. */
class BalanceReview(context:Context,private val prefs:BalancePreferences,private val analytics:BalanceAnalytics){
    private val gateway=ReviewGatewayFactory.create(context)
    private var busy=false
    suspend fun consider(activity:Activity,config:BalanceConfig,moment:ReviewMoment,canPresent:()->Boolean){
        val now=System.currentTimeMillis()
        if(busy || !gateway.available || !reviewEligible(config,prefs.completedRuns,prefs.firstPlayedAt,now,prefs.reviewAttempted,prefs.reviewTriedAt,moment) || !canPresent())return
        busy=true;prefs.reviewTriedAt=now
        try {
            withTimeout(8_000){gateway.request()}
            if(!canPresent() || activity.isFinishing || activity.isDestroyed)return
            prefs.reviewAttempted=true
            analytics.event("review_launch_attempt",prefs.difficulty,prefs.best(prefs.difficulty))
            android.util.Log.i("BalanceReview","Launching Play review flow")
            gateway.launch(activity)
            analytics.event("review_flow_completed",prefs.difficulty,prefs.best(prefs.difficulty))
            android.util.Log.i("BalanceReview","Play review flow completed")
        }catch(cancelled:CancellationException){if(cancelled !is TimeoutCancellationException)throw cancelled}
        catch(_:Exception){analytics.event("review_request_failed",prefs.difficulty)}
        finally{busy=false}
    }
}
