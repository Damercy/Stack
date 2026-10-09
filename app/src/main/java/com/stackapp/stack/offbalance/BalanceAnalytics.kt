package com.stackapp.stack.offbalance

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.stackapp.stack.BuildConfig

/** Aggregate product events; usernames, account IDs and receipts are excluded. */
class BalanceAnalytics(context:Context) {
    private val analytics=if(BuildConfig.USE_FIREBASE && !BuildConfig.DEBUG && runCatching{FirebaseApp.getInstance()}.isSuccess)FirebaseAnalytics.getInstance(context) else null
    fun event(name:String,mode:Difficulty,score:Int=0){
        analytics?.logEvent(name,Bundle().apply{putString("difficulty",mode.name.lowercase());putLong("layers",score.toLong())})
    }
}
