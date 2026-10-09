package com.stackapp.stack.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StackStateDao {
    @Query("SELECT * FROM stack_state WHERE id = 0")
    suspend fun load(): StackStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: StackStateEntity)

    @Query(
        "UPDATE stack_state SET lifetimeCount = :lifetimeCount, " +
            "todayCount = :todayCount, todayDayKey = :todayDayKey WHERE id = 0",
    )
    suspend fun updateTapState(lifetimeCount: Long, todayCount: Long, todayDayKey: String)

    @Query("UPDATE stack_state SET displayName = :displayName WHERE id = 0")
    suspend fun updateDisplayName(displayName: String)

    @Query("UPDATE stack_state SET countryCode = :countryCode WHERE id = 0")
    suspend fun updateCountryCode(countryCode: String)

    @Query("UPDATE stack_state SET onboardingComplete = :complete WHERE id = 0")
    suspend fun updateOnboardingComplete(complete: Boolean)

    @Query("UPDATE stack_state SET autoMinerActive = :active WHERE id = 0")
    suspend fun updateAutoMinerActive(active: Boolean)

    @Query("UPDATE stack_state SET lastAutoMinerSyncAt = :epochMillis WHERE id = 0")
    suspend fun updateLastAutoMinerSyncAt(epochMillis: Long)
}
