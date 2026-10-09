package com.stackapp.stack.leaderboard

import com.stackapp.stack.identity.DeviceId

data class LeaderboardScore(
    val deviceId: DeviceId,
    val displayName: String,
    val todayCount: Long,
    val dayKey: String,
    val countryCode: String = "",
)

interface LeaderboardRepository {
    fun upsertAndLoad(
        score: LeaderboardScore,
        onResult: (LeaderboardState) -> Unit,
        onError: (Throwable) -> Unit,
    )

    fun loadProfile(
        deviceId: DeviceId,
        onResult: (StackProfile?) -> Unit,
        onError: (Throwable) -> Unit,
    )

    fun saveProfile(
        deviceId: DeviceId,
        profile: StackProfile,
        onError: (Throwable) -> Unit = {},
    )

    fun checkUsername(
        displayName: String,
        deviceId: DeviceId,
        onResult: (Boolean) -> Unit,
        onError: (Throwable) -> Unit,
    )

    fun availableUsernameSuggestions(
        candidates: List<String>,
        onResult: (List<String>) -> Unit,
        onError: (Throwable) -> Unit,
    )

    fun claimUsername(
        deviceId: DeviceId,
        profile: StackProfile,
        onResult: (UsernameClaimResult) -> Unit,
    )
}

class LocalLeaderboardRepository : LeaderboardRepository {
    override fun upsertAndLoad(
        score: LeaderboardScore,
        onResult: (LeaderboardState) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        onResult(
            localLeaderboardState(
                displayName = score.displayName,
                todayCount = score.todayCount,
            ),
        )
    }

    override fun loadProfile(
        deviceId: DeviceId,
        onResult: (StackProfile?) -> Unit,
        onError: (Throwable) -> Unit,
    ) = onResult(null)

    override fun saveProfile(
        deviceId: DeviceId,
        profile: StackProfile,
        onError: (Throwable) -> Unit,
    ) = Unit

    override fun checkUsername(
        displayName: String,
        deviceId: DeviceId,
        onResult: (Boolean) -> Unit,
        onError: (Throwable) -> Unit,
    ) = onResult(true)

    override fun availableUsernameSuggestions(
        candidates: List<String>,
        onResult: (List<String>) -> Unit,
        onError: (Throwable) -> Unit,
    ) = onResult(candidates.take(4))

    override fun claimUsername(
        deviceId: DeviceId,
        profile: StackProfile,
        onResult: (UsernameClaimResult) -> Unit,
    ) = onResult(UsernameClaimResult.Success)
}

fun LeaderboardScore.toFirestoreDocument(updatedAt: Any? = null): Map<String, Any> =
    mapOf(
        "device_id" to deviceId.value,
        "display_name" to displayName,
        "today_count" to todayCount,
        "day_key" to dayKey,
        "country_code" to countryCode,
    ).let { document ->
        if (updatedAt == null) document else document + ("updated_at" to updatedAt)
    }
