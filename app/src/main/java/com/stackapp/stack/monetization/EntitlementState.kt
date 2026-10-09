package com.stackapp.stack.monetization

data class EntitlementState(
    val autoMinerActive: Boolean = false,
)

data class SubscriptionOffer(
    val productId: String = AUTO_MINER_PRODUCT_ID,
    val title: String = "Stack Auto-Miner",
    val priceText: String = "₹199 / month",
)

const val AUTO_MINER_PRODUCT_ID = "stack_auto_miner_monthly"
