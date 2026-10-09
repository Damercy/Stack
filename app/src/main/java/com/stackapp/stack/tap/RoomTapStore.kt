package com.stackapp.stack.tap

import android.content.Context
import android.content.SharedPreferences
import com.stackapp.stack.identity.DeviceId
import com.stackapp.stack.identity.newDeviceId
import com.stackapp.stack.identity.stableDeviceId
import com.stackapp.stack.leaderboard.todayIstKey
import com.stackapp.stack.leaderboard.cleanDisplayName
import com.stackapp.stack.storage.StackDatabase
import com.stackapp.stack.storage.StackStateEntity
import androidx.room.withTransaction
import com.stackapp.stack.autominer.AutoMinerResult
import com.stackapp.stack.autominer.applyAutoMinerCatchUpIfActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class RoomTapStore(
    context: Context,
    private val legacyPreferences: SharedPreferences =
        context.getSharedPreferences(LEGACY_PREFERENCES_NAME, Context.MODE_PRIVATE),
) : TapStore {
    private val database = StackDatabase.get(context)
    private val dao = database.stackStateDao()
    private val stableDeviceId = stableDeviceId(context)

    override fun load(): StoredStackState =
        runBlocking(Dispatchers.IO) {
            var entity = dao.load() ?: legacyState().also { dao.upsert(it) }
            if (entity.deviceId != stableDeviceId.value) {
                entity = entity.copy(deviceId = stableDeviceId.value)
                dao.upsert(entity)
            }
            val storedState = entity.toStoredStackState().forCurrentDay()
            if (storedState.tapState != entity.toStoredStackState().tapState) {
                dao.updateTapState(
                    storedState.tapState.lifetimeCount,
                    storedState.tapState.todayCount,
                    storedState.tapState.todayDayKey,
                )
            }
            storedState
        }

    override fun saveTapState(state: TapState) {
        runBlocking(Dispatchers.IO) {
            ensureStateExists()
            dao.updateTapState(state.lifetimeCount, state.todayCount, state.todayDayKey)
        }
    }

    fun recordTap(dayKey: String = todayIstKey()): TapState =
        runBlocking(Dispatchers.IO) {
            database.withTransaction {
                ensureStateExists()
                val current = dao.load()!!.toStoredStackState().tapState.forDay(dayKey)
                val next = current.copy(
                    lifetimeCount = current.lifetimeCount + 1,
                    todayCount = current.todayCount + 1,
                )
                dao.updateTapState(next.lifetimeCount, next.todayCount, next.todayDayKey)
                next
            }
        }

    fun applyAutoMiner(nowMillis: Long): AutoMinerResult =
        runBlocking(Dispatchers.IO) {
            database.withTransaction {
                ensureStateExists()
                val entity = dao.load()!!
                val result = applyAutoMinerCatchUpIfActive(
                    state = entity.toStoredStackState().tapState,
                    autoMinerActive = entity.autoMinerActive,
                    lastSyncAtMillis = entity.lastAutoMinerSyncAt,
                    nowMillis = nowMillis,
                )
                if (result.earnedTaps > 0) {
                    dao.updateTapState(
                        result.state.lifetimeCount,
                        result.state.todayCount,
                        result.state.todayDayKey,
                    )
                    dao.updateLastAutoMinerSyncAt(result.syncedAtMillis)
                } else if (entity.autoMinerActive && entity.lastAutoMinerSyncAt <= 0) {
                    dao.updateLastAutoMinerSyncAt(nowMillis)
                }
                result
            }
        }

    override fun saveDisplayName(displayName: String) {
        runBlocking(Dispatchers.IO) {
            ensureStateExists()
            dao.updateDisplayName(cleanDisplayName(displayName))
        }
    }

    override fun saveCountryCode(countryCode: String) {
        runBlocking(Dispatchers.IO) {
            ensureStateExists()
            dao.updateCountryCode(countryCode.uppercase().take(2))
        }
    }

    override fun saveOnboardingComplete(complete: Boolean) {
        runBlocking(Dispatchers.IO) {
            ensureStateExists()
            dao.updateOnboardingComplete(complete)
        }
    }

    override fun saveAutoMinerActive(active: Boolean) {
        runBlocking(Dispatchers.IO) {
            ensureStateExists()
            dao.updateAutoMinerActive(active)
        }
    }

    override fun saveLastAutoMinerSyncAt(epochMillis: Long) {
        runBlocking(Dispatchers.IO) {
            ensureStateExists()
            dao.updateLastAutoMinerSyncAt(epochMillis)
        }
    }

    private suspend fun ensureStateExists() {
        if (dao.load() == null) {
            dao.upsert(legacyState())
        }
    }

    private fun legacyState(): StackStateEntity {
        return StackStateEntity(
            deviceId = stableDeviceId.value,
            lifetimeCount = legacyPreferences.getLong(KEY_LIFETIME_COUNT, 0),
            todayCount = legacyPreferences.getLong(KEY_TODAY_COUNT, 0),
            todayDayKey = todayIstKey(),
            displayName = legacyPreferences.getString(KEY_DISPLAY_NAME, null),
            countryCode = legacyPreferences.getString(KEY_COUNTRY_CODE, null).orEmpty(),
            onboardingComplete = legacyPreferences.getBoolean(KEY_ONBOARDING_COMPLETE, false),
            autoMinerActive = legacyPreferences.getBoolean(KEY_AUTO_MINER_ACTIVE, false),
            lastAutoMinerSyncAt = legacyPreferences.getLong(KEY_LAST_AUTO_MINER_SYNC_AT, 0),
        )
    }

    private fun StackStateEntity.toStoredStackState(): StoredStackState =
        StoredStackState(
            deviceId = DeviceId(deviceId),
            tapState = TapState(
                lifetimeCount = lifetimeCount,
                todayCount = todayCount,
                todayDayKey = todayDayKey.takeIf { it.isNotBlank() } ?: todayIstKey(),
            ),
            displayName = displayName,
            countryCode = countryCode,
            onboardingComplete = onboardingComplete || !displayName.isNullOrBlank(),
            autoMinerActive = autoMinerActive,
            lastAutoMinerSyncAt = lastAutoMinerSyncAt,
        )

    private fun StoredStackState.forCurrentDay(): StoredStackState {
        val currentDayKey = todayIstKey()
        return if (tapState.todayDayKey == currentDayKey) {
            this
        } else {
            copy(tapState = tapState.copy(todayCount = 0, todayDayKey = currentDayKey))
        }
    }

    private companion object {
        const val LEGACY_PREFERENCES_NAME = "tap_store"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_LIFETIME_COUNT = "lifetime_count"
        const val KEY_TODAY_COUNT = "today_count"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_COUNTRY_CODE = "country_code"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        const val KEY_AUTO_MINER_ACTIVE = "auto_miner_active"
        const val KEY_LAST_AUTO_MINER_SYNC_AT = "last_auto_miner_sync_at"
    }
}
