package com.stackapp.stack.identity

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

@JvmInline
value class DeviceId(val value: String) {
    init {
        require(value.isNotBlank()) { "Device ID cannot be blank." }
    }
}

fun newDeviceId(): DeviceId = DeviceId(UUID.randomUUID().toString())

fun stableDeviceId(context: Context): DeviceId {
    val androidId = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID,
    ).orEmpty()
    if (androidId.isBlank()) return newDeviceId()

    val source = "${context.packageName}:stack-profile:$androidId"
    val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
    return DeviceId(digest.joinToString(separator = "") { byte -> "%02x".format(byte) })
}
