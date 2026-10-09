package com.stackapp.stack.leaderboard

import android.content.Context
import com.stackapp.stack.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore

fun createLeaderboardRepository(context: Context): LeaderboardRepository {
    if (!BuildConfig.USE_FIREBASE) return LocalLeaderboardRepository()
    val firebaseApp = FirebaseApp.initializeApp(context)
    return if (firebaseApp == null) {
        LocalLeaderboardRepository()
    } else {
        FirestoreLeaderboardRepository(FirebaseFirestore.getInstance(firebaseApp))
    }
}

