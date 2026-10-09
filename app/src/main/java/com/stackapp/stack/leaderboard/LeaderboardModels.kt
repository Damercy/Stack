package com.stackapp.stack.leaderboard

import java.util.Locale

data class LeaderboardEntry(
    val rank: Int,
    val displayName: String,
    val todayCount: Long,
    val countryCode: String = "",
    val isCurrentUser: Boolean = false,
)

data class StackProfile(
    val displayName: String,
    val countryCode: String,
)

enum class UsernameClaimResult {
    Success,
    Taken,
    Error,
}

fun normalizeUsername(input: String): String =
    cleanDisplayName(input).lowercase(Locale.ROOT).replace(" ", "_")

data class LeaderboardState(
    val displayName: String?,
    val todayCount: Long,
    val entries: List<LeaderboardEntry>,
) {
    val needsDisplayName: Boolean = displayName.isNullOrBlank()
    val currentUserRank: Int? = entries.firstOrNull { it.isCurrentUser }?.rank
}

fun cleanDisplayName(input: String): String =
    input
        .trim()
        .filter { character ->
            character.isLetterOrDigit() ||
                character == '_' ||
                character == '-' ||
                character == '.' ||
                character == ' '
        }
        .take(20)
        .trim()

fun localLeaderboardState(displayName: String?, todayCount: Long): LeaderboardState {
    val currentName = displayName?.takeIf { it.isNotBlank() }
    val seededEntries = listOf(
        LeaderboardEntry(rank = 1, displayName = "tapgod42", todayCount = 8387, countryCode = "US"),
        LeaderboardEntry(rank = 2, displayName = "chai_clink", todayCount = 6210, countryCode = "IN"),
        LeaderboardEntry(rank = 3, displayName = "midnighttap", todayCount = 4978, countryCode = "GB"),
        LeaderboardEntry(rank = 4, displayName = "screenburn", todayCount = 3847, countryCode = "JP"),
    )

    val userEntry = LeaderboardEntry(
        rank = 0,
        displayName = currentName ?: "You",
        todayCount = todayCount,
        isCurrentUser = true,
    )

    val ranked = (seededEntries + userEntry)
        .sortedByDescending { it.todayCount }
        .mapIndexed { index, entry -> entry.copy(rank = index + 1) }

    return LeaderboardState(
        displayName = currentName,
        todayCount = todayCount,
        entries = ranked.take(50),
    )
}
