package com.stackapp.stack.monetization

import android.content.Context
import android.os.Build
import com.stackapp.stack.BuildConfig

fun createBillingRepository(
    context: Context,
    isAutoMinerActive: () -> Boolean,
    setAutoMinerActive: (Boolean) -> Unit,
): BillingRepository =
    if (!BuildConfig.PAYMENTS_ENABLED) {
        DisabledBillingRepository(isAutoMinerActive)
    } else if (context.installedFromPlayStore()) {
        PlayBillingRepository(context, setAutoMinerActive)
    } else {
        // Sideloaded production apps must never grant a fake paid entitlement.
        DisabledBillingRepository(isAutoMinerActive)
    }

private fun Context.installedFromPlayStore(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        packageManager.getInstallSourceInfo(packageName).installingPackageName == "com.android.vending"
    } else {
        @Suppress("DEPRECATION")
        packageManager.getInstallerPackageName(packageName) == "com.android.vending"
    }
