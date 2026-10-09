package com.stackapp.stack.offbalance

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.stackapp.stack.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume

data class StylePackState(val owned:Boolean=false,val ready:Boolean=false,val price:String="",val busy:Boolean=false,val message:String="",val pending:Boolean=false)
interface StylePackStore {
    val state:StateFlow<StylePackState>
    suspend fun sync(config:BalanceConfig)
    fun buy(activity:Activity,config:BalanceConfig)
    fun restore()
    fun close()
}
object StylePackFactory {
    @Volatile var testStore:StylePackStore?=null
    fun create(context:Context):StylePackStore=if(BuildConfig.DEBUG && testStore!=null)testStore!! else PlayStylePack(context)
}

/** Nonconsumable cosmetics. Entitlements only come from server-verified receipts. */
private class PlayStylePack(context:Context):StylePackStore, PurchasesUpdatedListener {
    private val analytics=BalanceAnalytics(context)
    private val prefs=context.applicationContext.getSharedPreferences("style_pack",Context.MODE_PRIVATE)
    override val state=MutableStateFlow(StylePackState(owned=prefs.getBoolean("verified",false)))
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val lock=Mutex()
    private val available=BuildConfig.USE_FIREBASE && runCatching{FirebaseApp.getInstance()}.isSuccess
    private var config=BalanceConfig()
    private var closed=false
    private var detail:ProductDetails?=null
    private var offerToken:String?=null
    private var currency=""
    private var amount=0.0
    private val billing=BillingClient.newBuilder(context).setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection().build()
    private suspend fun connected(){
        if(billing.isReady)return
        withTimeout(8_000){suspendCancellableCoroutine<Unit>{continuation->
            billing.startConnection(object:BillingClientStateListener {
                override fun onBillingSetupFinished(result:BillingResult){if(continuation.isActive){if(result.responseCode==BillingClient.BillingResponseCode.OK)continuation.resume(Unit) else continuation.cancel(java.io.IOException("Store unavailable"))}}
                override fun onBillingServiceDisconnected()=Unit
            })
        }}
    }
    private suspend fun call(name:String,data:Map<String,Any> = emptyMap()):Map<*,*> {
        val auth=FirebaseAuth.getInstance()
        if(auth.currentUser==null)auth.signInAnonymously().await()
        return FirebaseFunctions.getInstance().getHttpsCallable(name).call(data).await().data as? Map<*,*> ?: error("Invalid store response")
    }
    private fun grant(owned:Boolean){prefs.edit().putBoolean("verified",owned).apply();state.value=state.value.copy(owned=owned,pending=false,message=if(owned)"PACK UNLOCKED" else "")}
    private suspend fun receipts():List<Purchase> = suspendCancellableCoroutine{continuation->
        billing.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()){result,purchases->
            if(continuation.isActive){if(result.responseCode==BillingClient.BillingResponseCode.OK)continuation.resume(purchases) else continuation.cancel(java.io.IOException("Restore unavailable"))}
        }
    }
    private suspend fun verify(purchase:Purchase,source:String="restore"){
        if(config.product !in purchase.products)return
        if(purchase.purchaseState==Purchase.PurchaseState.PENDING){state.value=state.value.copy(pending=true,message="PAYMENT PENDING");analytics.commerce("style_payment_pending",config.product);return}
        if(purchase.purchaseState!=Purchase.PurchaseState.PURCHASED)return
        val verified=call("verifyStylePack",mapOf("productId" to config.product,"token" to purchase.purchaseToken))
        check(verified["productId"]==config.product)
        val owned=verified["owned"]==true
        grant(owned)
        if(owned && source=="checkout"){
            val receipt=java.security.MessageDigest.getInstance("SHA-256").digest(purchase.purchaseToken.toByteArray()).joinToString(""){"%02x".format(it)}
            if(prefs.getString("event_receipt","")!=receipt){prefs.edit().putString("event_receipt",receipt).apply();analytics.commerce("style_purchase_verified",config.product,currency,amount)}
        }
    }
    override suspend fun sync(config:BalanceConfig)=lock.withLock {
        this.config=config
        state.value=state.value.copy(ready=false)
        if(!available || closed || !config.payments && !config.styleDiscovery && !state.value.owned)return@withLock
        try {
            withTimeout(20_000){
                connected()
                val status=call("stylePackStatus")
                check(status["productId"]==config.product)
                grant(status["owned"]==true)
                receipts().forEach{verify(it)}
                detail=null;offerToken=null
                if(config.payments && config.verificationReady && status["ready"]==true){
                    val details=suspendCancellableCoroutine<List<ProductDetails>>{continuation->
                        val query=QueryProductDetailsParams.newBuilder().setProductList(listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(config.product).setProductType(BillingClient.ProductType.INAPP).build())).build()
                        billing.queryProductDetailsAsync(query){result,products->if(continuation.isActive){if(result.responseCode==BillingClient.BillingResponseCode.OK)continuation.resume(products.productDetailsList) else continuation.cancel(java.io.IOException("Catalog unavailable"))}}
                    }
                    detail=details.singleOrNull{it.productId==config.product}
                    val offer=detail?.oneTimePurchaseOfferDetailsList?.firstOrNull()
                    offerToken=offer?.offerToken
                    val price=offer?.formattedPrice ?: detail?.oneTimePurchaseOfferDetails?.formattedPrice.orEmpty()
                    currency=offer?.priceCurrencyCode ?: detail?.oneTimePurchaseOfferDetails?.priceCurrencyCode.orEmpty()
                    amount=(offer?.priceAmountMicros ?: detail?.oneTimePurchaseOfferDetails?.priceAmountMicros ?: 0L)/1_000_000.0
                    state.value=state.value.copy(ready=detail!=null && price.isNotBlank(),price=price)
                }
            }
        }catch(error:CancellationException){if(error !is TimeoutCancellationException)throw error;state.value=state.value.copy(ready=false,message="STORE UNAVAILABLE. RETRY.")}
        catch(_:Exception){state.value=state.value.copy(ready=false,message="STORE UNAVAILABLE. RETRY.")}
    }
    override fun buy(activity:Activity,config:BalanceConfig){
        if(state.value.busy || state.value.owned || state.value.pending || !config.payments || !config.verificationReady)return
        state.value=state.value.copy(busy=true,message="")
        scope.launch {
            try {
                sync(config) // Fresh catalog and server preflight before charging.
                if(!state.value.ready)return@launch
                val product=detail ?: return@launch
                val params=BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product)
                offerToken?.let{params.setOfferToken(it)}
                val result=billing.launchBillingFlow(activity,BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params.build())).build())
                if(result.responseCode==BillingClient.BillingResponseCode.OK)analytics.commerce("begin_checkout",config.product,currency,amount)
                if(result.responseCode!=BillingClient.BillingResponseCode.OK)state.value=state.value.copy(message="PURCHASE NOT STARTED. RETRY.")
            }finally{state.value=state.value.copy(busy=false)}
        }
    }
    override fun onPurchasesUpdated(result:BillingResult,purchases:List<Purchase>?){
        scope.launch {
            if(result.responseCode==BillingClient.BillingResponseCode.USER_CANCELED){state.value=state.value.copy(busy=false,message="");analytics.commerce("style_purchase_cancelled",config.product);return@launch}
            if(result.responseCode==BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED){restore();return@launch}
            if(result.responseCode!=BillingClient.BillingResponseCode.OK){state.value=state.value.copy(busy=false,message="PURCHASE UNAVAILABLE. RETRY.");return@launch}
            state.value=state.value.copy(busy=true)
            try{withTimeout(20_000){lock.withLock{purchases.orEmpty().forEach{verify(it,"checkout")}}}}
            catch(cancelled:CancellationException){if(cancelled !is TimeoutCancellationException)throw cancelled;state.value=state.value.copy(message="RESTORE TO RETRY VERIFICATION.")}
            catch(_:Exception){state.value=state.value.copy(message="RESTORE TO RETRY VERIFICATION.");analytics.commerce("style_verification_failed",config.product)}
            finally{state.value=state.value.copy(busy=false)}
        }
    }
    override fun restore(){if(!state.value.busy)scope.launch {
        state.value=state.value.copy(busy=true,message="")
        try{
            check(available)
            withTimeout(20_000){lock.withLock{connected();receipts().forEach{verify(it)}}}
            if(!state.value.owned && state.value.message.isBlank())state.value=state.value.copy(message="NO PURCHASES FOUND.")
            if(state.value.owned)analytics.commerce("style_restore_complete",config.product)
        }
        catch(cancelled:CancellationException){if(cancelled !is TimeoutCancellationException)throw cancelled;state.value=state.value.copy(message="RESTORE UNAVAILABLE. RETRY.")}
        catch(_:Exception){state.value=state.value.copy(message="RESTORE UNAVAILABLE. RETRY.")}
        finally{state.value=state.value.copy(busy=false)}
    }}
    override fun close(){closed=true;scope.cancel();billing.endConnection()}
}
