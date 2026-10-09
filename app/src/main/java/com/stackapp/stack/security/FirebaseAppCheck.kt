package com.stackapp.stack.security

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

fun installFirebaseAppCheckIfAvailable(context: Context) {
    if (FirebaseApp.getApps(context).isEmpty()) return

    FirebaseAppCheck.getInstance()
        .installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance(),
        )
}
