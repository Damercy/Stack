package com.stackapp.stack.offbalance

import android.app.Activity
import android.content.Context
import androidx.credentials.*
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.stackapp.stack.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class AccountState(val configured:Boolean=false,val signedIn:Boolean=false,val busy:Boolean=false,val message:String="",val savedProfile:Boolean=false,val revision:Int=0)
interface BalanceAccount {
    val state:StateFlow<AccountState>
    fun configure(enabled:Boolean)
    suspend fun signIn(activity:Activity)
    suspend fun useSavedProfile()
    fun cancelSwitch()
    suspend fun signOut()
    fun close()
}
object BalanceAccountFactory {
    @Volatile var testAccount:BalanceAccount?=null
    fun create(context:Context):BalanceAccount=if(BuildConfig.DEBUG && testAccount!=null)testAccount!! else GoogleBalanceAccount(context)
}
/** Link guest identities so a new Google sign-in preserves username ownership. */
private class GoogleBalanceAccount(context:Context):BalanceAccount {
    override val state=MutableStateFlow(AccountState())
    private val auth=if(BuildConfig.USE_FIREBASE && runCatching{FirebaseApp.getInstance()}.isSuccess)FirebaseAuth.getInstance() else null
    private val credentials=CredentialManager.create(context)
    private val clientId=runCatching{
        val id=context.resources.getIdentifier("default_web_client_id","string",context.packageName)
        if(id!=0)context.getString(id) else ""
    }.getOrDefault("")
    private val analytics=BalanceAnalytics(context)
    private var saved:AuthCredential?=null
    private var previousId=auth?.currentUser?.uid
    private val listener=FirebaseAuth.AuthStateListener { current ->
        val user=current.currentUser
        val changed=user?.uid!=previousId;previousId=user?.uid
        state.value=state.value.copy(signedIn=user!=null && !user.isAnonymous,revision=state.value.revision+if(changed)1 else 0)
    }
    init{auth?.addAuthStateListener(listener)}
    override fun configure(enabled:Boolean){state.value=state.value.copy(configured=enabled && auth!=null && clientId.endsWith(".apps.googleusercontent.com"))}
    private fun signedIn(){
        // Linking a guest keeps its UID, so an auth-state notification alone is insufficient.
        state.value=state.value.copy(signedIn=auth?.currentUser?.isAnonymous==false,savedProfile=false,message="")
        analytics.commerce("login_complete","google")
    }
    override suspend fun signIn(activity:Activity){
        if(!state.value.configured || state.value.busy || state.value.signedIn)return
        state.value=state.value.copy(busy=true,message="",savedProfile=false);saved=null
        analytics.commerce("login_started","google")
        try {
            val option=GetSignInWithGoogleOption.Builder(clientId).setNonce(UUID.randomUUID().toString()).build()
            val response=credentials.getCredential(activity,GetCredentialRequest.Builder().addCredentialOption(option).build()).credential
            check(response is CustomCredential && response.type==GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
            val token=GoogleIdTokenCredential.createFrom(response.data).idToken
            val credential=GoogleAuthProvider.getCredential(token,null)
            withTimeout(20_000){
                val user=GuestIdentity.user(auth!!)
                try{user.linkWithCredential(credential).await();signedIn()}
                catch(collision:FirebaseAuthUserCollisionException){
                    // A new guest with no public profile has nothing to abandon: finish the login.
                    val guestHasProfile=try{FirebaseFirestore.getInstance().collection("players").document(user.uid).get(Source.SERVER).await().exists()}
                        catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){true}
                    if(!guestHasProfile){auth.signInWithCredential(credential).await();signedIn()}
                    else {saved=credential;state.value=state.value.copy(savedProfile=true,message="This Google account has a saved profile. Switching keeps your device records and uses its saved username.")}
                }
            }
        }catch(_:GetCredentialCancellationException){analytics.commerce("login_cancelled","google")}
        catch(cancelled:CancellationException){if(cancelled !is TimeoutCancellationException)throw cancelled;state.value=state.value.copy(message="Couldn’t connect. Try again.")}
        catch(error:Exception){
            android.util.Log.w("BalanceAccount", "Sign-in failed: ${if(error is FirebaseAuthException)error.errorCode else error.javaClass.simpleName}")
            state.value=state.value.copy(message="Couldn’t sign in. Try again.");analytics.commerce("login_failed","google")
        }
        finally{state.value=state.value.copy(busy=false)}
    }
    override suspend fun useSavedProfile(){
        val credential=saved ?: return
        if(state.value.busy)return
        state.value=state.value.copy(busy=true,message="")
        try{withTimeout(20_000){auth!!.signInWithCredential(credential).await()};saved=null;signedIn()}
        catch(cancelled:CancellationException){if(cancelled !is TimeoutCancellationException)throw cancelled;state.value=state.value.copy(message="Couldn’t connect. Try again.")}
        catch(_:Exception){state.value=state.value.copy(message="Couldn’t load your profile. Retry.")}
        finally{state.value=state.value.copy(busy=false)}
    }
    override fun cancelSwitch(){saved=null;state.value=state.value.copy(savedProfile=false,message="")}
    override suspend fun signOut(){
        if(state.value.busy)return
        state.value=state.value.copy(busy=true,message="")
        try {
            auth?.signOut();saved=null
            try{credentials.clearCredentialState(ClearCredentialStateRequest())}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){ /* Guest play remains available. */ }
            state.value=state.value.copy(signedIn=false,savedProfile=false,message="")
            analytics.commerce("logout","google")
        } finally {state.value=state.value.copy(busy=false)}
    }
    override fun close(){saved=null;auth?.removeAuthStateListener(listener)}
}
