package com.stackapp.stack.offbalance

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/** Competition, purchases and Google linking share one guest creation request. */
internal object GuestIdentity {
    private val creation=Mutex()
    suspend fun user(auth:FirebaseAuth):FirebaseUser=creation.withLock {
        auth.currentUser ?: checkNotNull(auth.signInAnonymously().await().user)
    }
}
