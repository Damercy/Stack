package com.stackapp.stack.tap

import android.content.Context
import com.stackapp.stack.identity.DeviceId
import com.stackapp.stack.identity.newDeviceId
import com.stackapp.stack.leaderboard.cleanDisplayName
import com.stackapp.stack.leaderboard.todayIstKey

interface TapStore {
    fun load(): StoredStackState
    fun saveTapState(state: TapState)
    fun saveDisplayName(displayName: String)
    fun saveCountryCode(countryCode: String)
    fun saveOnboardingComplete(complete: Boolean)
    fun saveAutoMinerActive(active: Boolean)
    fun saveLastAutoMinerSyncAt(epochMillis: Long)
}

data class StoredStackState(
    val deviceId: DeviceId,
    val tapState: TapState,
    val displayName: String?,
    val countryCode: String,
    val onboardingComplete: Boolean,
    val autoMinerActive: Boolean,
    val lastAutoMinerSyncAt: Long,
)

class PreferencesTapStore(context: Context) : TapStore {
    private val preferences = context.getSharedPreferences("tap_store", Context.MODE_PRIVATE)

    override fun load(): StoredStackState =
        StoredStackState(
            deviceId = loadDeviceId(),
            tapState = TapState(
                lifetimeCount = preferences.getLong(KEY_LIFETIME_COUNT, 0),
                todayCount = preferences.getLong(KEY_TODAY_COUNT, 0),
                todayDayKey = preferences.getString(KEY_TODAY_DAY_KEY, null)
                    ?.takeIf { it.isNotBlank() }
                    ?: todayIstKey(),
            ),
            displayName = preferences.getString(KEY_DISPLAY_NAME, null),
            countryCode = preferences.getString(KEY_COUNTRY_CODE, null).orEmpty(),
            onboardingComplete = preferences.getBoolean(KEY_ONBOARDING_COMPLETE, false),
            autoMinerActive = preferences.getBoolean(KEY_AUTO_MINER_ACTIVE, false),
            lastAutoMinerSyncAt = preferences.getLong(KEY_LAST_AUTO_MINER_SYNC_AT, 0),
        )

    override fun saveTapState(state: TapState) {
        preferences.edit()
            .putLong(KEY_LIFETIME_COUNT, state.lifetimeCount)
            .putLong(KEY_TODAY_COUNT, state.todayCount)
            .putString(KEY_TODAY_DAY_KEY, state.todayDayKey)
            .apply()
    }

    override fun saveDisplayName(displayName: String) {
        preferences.edit()
            .putString(KEY_DISPLAY_NAME, cleanDisplayName(displayName))
            .apply()
    }

    override fun saveCountryCode(countryCode: String) {
        preferences.edit().putString(KEY_COUNTRY_CODE, countryCode).apply()
    }

    override fun saveOnboardingComplete(complete: Boolean) {
        preferences.edit().putBoolean(KEY_ONBOARDING_COMPLETE, complete).apply()
    }

    override fun saveAutoMinerActive(active: Boolean) {
        preferences.edit()
            .putBoolean(KEY_AUTO_MINER_ACTIVE, active)
            .apply()
    }

    override fun saveLastAutoMinerSyncAt(epochMillis: Long) {
        preferences.edit()
            .putLong(KEY_LAST_AUTO_MINER_SYNC_AT, epochMillis)
            .apply()
    }

    private fun loadDeviceId(): DeviceId {
        val existing = preferences.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return DeviceId(existing)

        val created = newDeviceId()
        preferences.edit()
            .putString(KEY_DEVICE_ID, created.value)
            .commit()
        return created
    }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_LIFETIME_COUNT = "lifetime_count"
        const val KEY_TODAY_COUNT = "today_count"
        const val KEY_TODAY_DAY_KEY = "today_day_key"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_COUNTRY_CODE = "country_code"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        const val KEY_AUTO_MINER_ACTIVE = "auto_miner_active"
        const val KEY_LAST_AUTO_MINER_SYNC_AT = "last_auto_miner_sync_at"
    }
}
