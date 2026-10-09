package com.stackapp.stack.monetization

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

class PlayBillingRepository(
    context: Context,
    private val setAutoMinerActive: (Boolean) -> Unit,
) : BillingRepository, PurchasesUpdatedListener {
    private var lastOffer: SubscriptionOffer = SubscriptionOffer()
    private var lastProductDetails: ProductDetails? = null
    private var pendingPurchaseResult: ((EntitlementState) -> Unit)? = null
    private var pendingPurchaseError: ((Throwable) -> Unit)? = null

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build(),
        )
        .build()

    override fun refreshEntitlement(
        onResult: (EntitlementState) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        withReadyClient(onError) {
            queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build(),
            ) { billingResult, purchases ->
                if (!billingResult.isOk()) {
                    onError(IllegalStateException(billingResult.debugMessage))
                    return@queryPurchasesAsync
                }

                val active = purchases.any { purchase ->
                    purchase.products.contains(AUTO_MINER_PRODUCT_ID) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                setAutoMinerActive(active)
                onResult(EntitlementState(autoMinerActive = active))
            }
        }
    }

    override fun loadOffer(
        onResult: (SubscriptionOffer) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        withReadyClient(onError) {
            queryProductDetailsAsync(
                QueryProductDetailsParams.newBuilder()
                    .setProductList(
                        listOf(
                            QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(AUTO_MINER_PRODUCT_ID)
                                .setProductType(BillingClient.ProductType.SUBS)
                                .build(),
                        ),
                    )
                    .build(),
            ) { billingResult, products ->
                if (!billingResult.isOk()) {
                    onError(IllegalStateException(billingResult.debugMessage))
                    return@queryProductDetailsAsync
                }

                val details = products.productDetailsList.firstOrNull()
                lastProductDetails = details
                lastOffer = details?.toSubscriptionOffer() ?: SubscriptionOffer()
                onResult(lastOffer)
            }
        }
    }

    override fun launchPurchase(
        activity: Activity,
        onResult: (EntitlementState) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val productDetails = lastProductDetails
        val offerToken = productDetails
            ?.subscriptionOfferDetails
            ?.firstOrNull()
            ?.offerToken

        if (productDetails == null || offerToken == null) {
            onError(IllegalStateException("Auto-Miner subscription is not configured in Play Console yet."))
            return
        }

        pendingPurchaseResult = onResult
        pendingPurchaseError = onError

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()
        val result = billingClient.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build(),
        )
        if (!result.isOk()) {
            onError(IllegalStateException(result.debugMessage))
        }
    }

    override fun openSubscriptionManagement(
        activity: Activity,
        onResult: (EntitlementState) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val uri = Uri.parse(
            "https://play.google.com/store/account/subscriptions" +
                "?sku=$AUTO_MINER_PRODUCT_ID&package=${activity.packageName}",
        )
        val intent = Intent(Intent.ACTION_VIEW, uri)
        runCatching {
            activity.startActivity(intent)
        }.onSuccess {
            onResult(EntitlementState(autoMinerActive = true))
        }.onFailure(onError)
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?,
    ) {
        if (!billingResult.isOk()) {
            pendingPurchaseError?.invoke(IllegalStateException(billingResult.debugMessage))
            return
        }

        val purchasedAutoMiner = purchases
            .orEmpty()
            .filter { purchase ->
                purchase.products.contains(AUTO_MINER_PRODUCT_ID) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            }

        purchasedAutoMiner.forEach(::acknowledgeIfNeeded)
        val active = purchasedAutoMiner.isNotEmpty()
        setAutoMinerActive(active)
        pendingPurchaseResult?.invoke(EntitlementState(autoMinerActive = active))
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return

        billingClient.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build(),
        ) { result ->
            if (!result.isOk()) {
                pendingPurchaseError?.invoke(IllegalStateException(result.debugMessage))
            }
        }
    }

    private fun withReadyClient(
        onError: (Throwable) -> Unit,
        block: BillingClient.() -> Unit,
    ) {
        if (billingClient.isReady) {
            billingClient.block()
            return
        }

        billingClient.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.isOk()) {
                        billingClient.block()
                    } else {
                        onError(IllegalStateException(billingResult.debugMessage))
                    }
                }

                override fun onBillingServiceDisconnected() = Unit
            },
        )
    }
}

private fun BillingResult.isOk(): Boolean =
    responseCode == BillingClient.BillingResponseCode.OK

private fun ProductDetails.toSubscriptionOffer(): SubscriptionOffer {
    val pricing = subscriptionOfferDetails
        ?.firstOrNull()
        ?.pricingPhases
        ?.pricingPhaseList
        ?.lastOrNull()

    return SubscriptionOffer(
        productId = productId,
        title = title,
        priceText = pricing?.formattedPrice ?: "INR 49/mo",
    )
}
