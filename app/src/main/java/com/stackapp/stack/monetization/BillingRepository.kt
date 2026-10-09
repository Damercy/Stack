package com.stackapp.stack.monetization

import android.app.Activity

interface BillingRepository {
    fun refreshEntitlement(
        onResult: (EntitlementState) -> Unit,
        onError: (Throwable) -> Unit,
    )

    fun loadOffer(
        onResult: (SubscriptionOffer) -> Unit,
        onError: (Throwable) -> Unit,
    )

    fun launchPurchase(
        activity: Activity,
        onResult: (EntitlementState) -> Unit,
        onError: (Throwable) -> Unit,
    )

    fun openSubscriptionManagement(
        activity: Activity,
        onResult: (EntitlementState) -> Unit,
        onError: (Throwable) -> Unit,
    )
}

class DisabledBillingRepository(private val isAutoMinerActive: () -> Boolean) : BillingRepository {
    override fun refreshEntitlement(onResult: (EntitlementState) -> Unit, onError: (Throwable) -> Unit) {
        onResult(EntitlementState(autoMinerActive = isAutoMinerActive()))
    }
    override fun loadOffer(onResult: (SubscriptionOffer) -> Unit, onError: (Throwable) -> Unit) {
        onResult(SubscriptionOffer())
    }
    override fun launchPurchase(activity: Activity, onResult: (EntitlementState) -> Unit, onError: (Throwable) -> Unit) {
        onError(IllegalStateException("Purchases are not available yet"))
    }
    override fun openSubscriptionManagement(activity: Activity, onResult: (EntitlementState) -> Unit, onError: (Throwable) -> Unit) {
        onError(IllegalStateException("Purchases are not available yet"))
    }
}
