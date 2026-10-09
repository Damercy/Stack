package com.stackapp.stack.leaderboard

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Source

class FirestoreLeaderboardRepository(
    private val firestore: FirebaseFirestore,
) : LeaderboardRepository {
    override fun checkUsername(
        displayName: String,
        deviceId: com.stackapp.stack.identity.DeviceId,
        onResult: (Boolean) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        firestore.collection("usernames")
            .document(normalizeUsername(displayName))
            .get(Source.SERVER)
            .addOnSuccessListener { document ->
                onResult(!document.exists() || document.getString("device_id") == deviceId.value)
            }
            .addOnFailureListener(onError)
    }

    override fun availableUsernameSuggestions(
        candidates: List<String>,
        onResult: (List<String>) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val unique = candidates.distinctBy(::normalizeUsername).take(8)
        val tasks = unique.map { candidate ->
            firestore.collection("usernames").document(normalizeUsername(candidate)).get(Source.SERVER)
        }
        Tasks.whenAllSuccess<DocumentSnapshot>(tasks)
            .addOnSuccessListener { documents ->
                onResult(unique.zip(documents).filter { !it.second.exists() }.map { it.first }.take(4))
            }
            .addOnFailureListener(onError)
    }

    override fun claimUsername(
        deviceId: com.stackapp.stack.identity.DeviceId,
        profile: StackProfile,
        onResult: (UsernameClaimResult) -> Unit,
    ) {
        val normalized = normalizeUsername(profile.displayName)
        val usernameRef = firestore.collection("usernames").document(normalized)
        val profileRef = firestore.collection("profiles").document(deviceId.value)
        firestore.runTransaction { transaction ->
            val existing = transaction.get(usernameRef)
            if (existing.exists() && existing.getString("device_id") != deviceId.value) {
                throw UsernameTakenException()
            }
            transaction.set(
                usernameRef,
                mapOf(
                    "normalized_name" to normalized,
                    "display_name" to profile.displayName,
                    "device_id" to deviceId.value,
                    "created_at" to FieldValue.serverTimestamp(),
                ),
            )
            transaction.set(
                profileRef,
                mapOf(
                    "device_id" to deviceId.value,
                    "display_name" to profile.displayName,
                    "country_code" to profile.countryCode,
                    "username_key" to normalized,
                    "updated_at" to FieldValue.serverTimestamp(),
                ),
            )
        }.addOnSuccessListener { onResult(UsernameClaimResult.Success) }
            .addOnFailureListener { error ->
                onResult(
                    if (error is UsernameTakenException || error.cause is UsernameTakenException) {
                        UsernameClaimResult.Taken
                    } else {
                        UsernameClaimResult.Error
                    },
                )
            }
    }
    override fun loadProfile(
        deviceId: com.stackapp.stack.identity.DeviceId,
        onResult: (StackProfile?) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        firestore.collection("profiles")
            .document(deviceId.value)
            .get()
            .addOnSuccessListener { document ->
                val name = document.getString("display_name")
                onResult(
                    if (name.isNullOrBlank()) {
                        null
                    } else {
                        StackProfile(
                            displayName = name,
                            countryCode = document.getString("country_code").orEmpty(),
                        )
                    },
                )
            }
            .addOnFailureListener(onError)
    }

    override fun saveProfile(
        deviceId: com.stackapp.stack.identity.DeviceId,
        profile: StackProfile,
        onError: (Throwable) -> Unit,
    ) {
        firestore.collection("profiles")
            .document(deviceId.value)
            .set(
                mapOf(
                    "device_id" to deviceId.value,
                    "display_name" to profile.displayName,
                    "country_code" to profile.countryCode,
                    "username_key" to normalizeUsername(profile.displayName),
                    "updated_at" to FieldValue.serverTimestamp(),
                ),
            )
            .addOnFailureListener(onError)
    }

    override fun upsertAndLoad(
        score: LeaderboardScore,
        onResult: (LeaderboardState) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val scores = firestore
            .collection("daily_leaderboards")
            .document(score.dayKey)
            .collection("scores")
        val currentUserScore = scores.document(score.deviceId.value)

        currentUserScore
            .set(score.toFirestoreDocument(updatedAt = FieldValue.serverTimestamp()))
            .continueWithTask {
                val topTask = scores
                    .orderBy("today_count", Query.Direction.DESCENDING)
                    .orderBy("updated_at", Query.Direction.ASCENDING)
                    .limit(50)
                    .get(Source.SERVER)
                val betterRankTask = scores
                    .whereGreaterThan("today_count", score.todayCount)
                    .get(Source.SERVER)

                Tasks.whenAllSuccess<Any>(topTask, betterRankTask)
            }
            .addOnSuccessListener { results ->
                val topSnapshot = results[0] as? com.google.firebase.firestore.QuerySnapshot
                val betterRankSnapshot = results[1] as? com.google.firebase.firestore.QuerySnapshot
                val betterCount = betterRankSnapshot?.size() ?: 0
                val currentRank = betterCount + 1
                val entries = topSnapshot
                    ?.documents
                    ?.mapIndexedNotNull { index, document ->
                        document.toLeaderboardEntry(
                            rank = index + 1,
                            currentDeviceId = score.deviceId.value,
                        )
                    }
                    .orEmpty()
                    .withCurrentUser(score, currentRank)

                onResult(
                    LeaderboardState(
                        displayName = score.displayName,
                        todayCount = score.todayCount,
                        entries = entries,
                    ),
                )
            }
            .addOnFailureListener(onError)
    }
}

private class UsernameTakenException : IllegalStateException("Username is already reserved.")

private fun DocumentSnapshot.toLeaderboardEntry(
    rank: Int,
    currentDeviceId: String,
): LeaderboardEntry? {
    val displayName = getString("display_name") ?: return null
    val todayCount = getLong("today_count") ?: return null
    val deviceId = getString("device_id") ?: id
    val countryCode = getString("country_code").orEmpty()

    return LeaderboardEntry(
        rank = rank,
        displayName = displayName,
        todayCount = todayCount,
        countryCode = countryCode,
        isCurrentUser = deviceId == currentDeviceId,
    )
}

private fun List<LeaderboardEntry>.withCurrentUser(
    score: LeaderboardScore,
    currentRank: Int,
): List<LeaderboardEntry> {
    if (any { it.isCurrentUser }) return this

    return this + LeaderboardEntry(
        rank = currentRank,
        displayName = score.displayName,
        todayCount = score.todayCount,
        countryCode = score.countryCode,
        isCurrentUser = true,
    )
}
