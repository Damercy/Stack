package com.stackapp.stack.offbalance

import android.app.Activity
import android.content.Context
import androidx.credentials.*
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
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

data class AccountState(val configured:Boolean=false,val signedIn:Boolean=false,val busy:Boolean=false,val message:String="",val savedProfile:Boolean=false,val revision:Int=0,val name:String="",val email:String="",val photoUrl:String="",val demo:Boolean=false)
interface BalanceAccount {
    val state:StateFlow<AccountState>
    fun configure(enabled:Boolean)
    suspend fun signIn(activity:Activity)
    suspend fun promptSignIn(activity:Activity)=signIn(activity)
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
    private var googleName=""
    private var googlePhoto:android.net.Uri?=null
    private var previousId=auth?.currentUser?.uid
    private val listener=FirebaseAuth.AuthStateListener { current ->
        val user=current.currentUser
        val changed=user?.uid!=previousId;previousId=user?.uid
        val connected=user!=null && !user.isAnonymous
        val google=user?.providerData?.firstOrNull{it.providerId==GoogleAuthProvider.PROVIDER_ID}
        state.value=state.value.copy(signedIn=connected,revision=state.value.revision+if(changed)1 else 0,name=if(connected)(user?.displayName ?: google?.displayName).orEmpty() else "",email=if(connected)user?.email.orEmpty() else "",photoUrl=if(connected)(user?.photoUrl ?: google?.photoUrl)?.toString().orEmpty() else "")
    }
    init{auth?.addAuthStateListener(listener)}
    override fun configure(enabled:Boolean){state.value=state.value.copy(configured=enabled && auth!=null && clientId.endsWith(".apps.googleusercontent.com"))}
    private suspend fun signedIn(){
        // Linking a guest keeps its UID, so an auth-state notification alone is insufficient.
        val user=auth?.currentUser
        val google=user?.providerData?.firstOrNull{it.providerId==GoogleAuthProvider.PROVIDER_ID}
        val name=googleName.ifBlank{user?.displayName ?: google?.displayName.orEmpty()}
        val photo=googlePhoto ?: user?.photoUrl ?: google?.photoUrl
        state.value=state.value.copy(signedIn=user?.isAnonymous==false,savedProfile=false,message="",name=name,email=user?.email.orEmpty(),photoUrl=photo?.toString().orEmpty())
        // Linking an anonymous user can leave Firebase's top-level profile empty.
        // Persist the identity supplied by Credential Manager so restart restores it.
        if(user!=null && (user.displayName!=name || user.photoUrl!=photo)){
            try{withTimeout(5_000){user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).setPhotoUri(photo).build()).await()}}
            catch(cancelled:CancellationException){if(cancelled !is TimeoutCancellationException)throw cancelled}
            catch(_:Exception){ /* Sign-in already succeeded; keep the current session usable. */ }
        }
        analytics.commerce("login_complete","google")
    }
    override suspend fun signIn(activity:Activity)=authenticate(activity,false)
    override suspend fun promptSignIn(activity:Activity)=authenticate(activity,true)
    private suspend fun authenticate(activity:Activity,automatic:Boolean){
        if(!state.value.configured || state.value.busy || state.value.signedIn)return
        state.value=state.value.copy(busy=true,message="",savedProfile=false);saved=null
        analytics.commerce("login_started","google")
        try {
            val nonce=UUID.randomUUID().toString()
            suspend fun request(authorized:Boolean):Credential {
                val option=if(automatic)GetGoogleIdOption.Builder().setServerClientId(clientId).setFilterByAuthorizedAccounts(authorized).setAutoSelectEnabled(authorized).setNonce(nonce).build()
                    else GetSignInWithGoogleOption.Builder(clientId).setNonce(nonce).build()
                return credentials.getCredential(activity,GetCredentialRequest.Builder().addCredentialOption(option).build()).credential
            }
            val response=try{request(true)}catch(error:NoCredentialException){if(automatic)request(false) else throw error}
            check(response is CustomCredential && response.type==GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
            val identity=GoogleIdTokenCredential.createFrom(response.data)
            googleName=identity.displayName.orEmpty();googlePhoto=identity.profilePictureUri
            val token=identity.idToken
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
            auth?.signOut();saved=null;googleName="";googlePhoto=null
            try{credentials.clearCredentialState(ClearCredentialStateRequest())}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){ /* Guest play remains available. */ }
            state.value=state.value.copy(signedIn=false,savedProfile=false,message="")
            analytics.commerce("logout","google")
        } finally {state.value=state.value.copy(busy=false)}
    }
    override fun close(){saved=null;auth?.removeAuthStateListener(listener)}
}
