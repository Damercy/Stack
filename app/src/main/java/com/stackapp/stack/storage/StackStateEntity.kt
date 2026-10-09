package com.stackapp.stack.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stack_state")
data class StackStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val deviceId: String,
    val lifetimeCount: Long,
    val todayCount: Long,
    val todayDayKey: String,
    val displayName: String?,
    val countryCode: String,
    val onboardingComplete: Boolean,
    val autoMinerActive: Boolean,
    val lastAutoMinerSyncAt: Long,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
