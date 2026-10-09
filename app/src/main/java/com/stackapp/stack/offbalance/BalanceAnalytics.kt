package com.stackapp.stack.offbalance

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.stackapp.stack.BuildConfig

data class ProductEvent(val name:String,val fields:Map<String,Any>)
object BalanceAnalyticsTestSink {
    @Volatile var accept:((ProductEvent)->Unit)?=null
}

/** Aggregate product events; usernames, account IDs and receipts are excluded. */
class BalanceAnalytics(context:Context) {
    private val analytics=if(BuildConfig.USE_FIREBASE && !BuildConfig.DEBUG && runCatching{FirebaseApp.getInstance()}.isSuccess)FirebaseAnalytics.getInstance(context) else null
    private fun log(name:String,fields:Map<String,Any>){
        if(BuildConfig.DEBUG)BalanceAnalyticsTestSink.accept?.invoke(ProductEvent(name,fields))
        analytics?.logEvent(name,Bundle().apply{fields.forEach{(k,v)->when(v){is String->putString(k,v);is Long->putLong(k,v);is Int->putLong(k,v.toLong());is Double->putDouble(k,v)}}})
    }
    fun event(name:String,mode:Difficulty,score:Int=0,reason:String="")=log(name,buildMap{put("difficulty",mode.name.lowercase());put("layers",score.coerceAtLeast(0));if(reason.isNotBlank())put("reason",reason)})
    fun screen(name:String)=log("screen_view",mapOf("screen_name" to name,"screen_class" to "Stack"))
    fun tutorial(name:String,step:Int,replay:Boolean)=log(name,mapOf("step" to step,"journey" to if(replay)"replay" else "first_play"))
    fun commerce(name:String,product:String,currency:String="",value:Double=0.0)=log(name,buildMap{put("item_id",product);if(currency.matches(Regex("[A-Z]{3}")))put("currency",currency);if(value>0 && value.isFinite())put("value",value)})
}
