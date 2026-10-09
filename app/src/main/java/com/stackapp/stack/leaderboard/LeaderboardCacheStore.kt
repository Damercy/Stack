package com.stackapp.stack.leaderboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LeaderboardCache(
    val entries: List<LeaderboardEntry>,
    val dayKey: String,
    val fetchedAtMillis: Long,
)

class LeaderboardCacheStore(context: Context) {
    private val preferences = context.getSharedPreferences("leaderboard_cache", Context.MODE_PRIVATE)

    fun load(): LeaderboardCache? {
        val raw = preferences.getString(KEY_PAYLOAD, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val entriesJson = json.getJSONArray("entries")
            val entries = buildList {
                repeat(entriesJson.length()) { index ->
                    val item = entriesJson.getJSONObject(index)
                    add(
                        LeaderboardEntry(
                            rank = item.getInt("rank"),
                            displayName = item.getString("displayName"),
                            todayCount = item.getLong("todayCount"),
                            countryCode = item.optString("countryCode"),
                            isCurrentUser = item.optBoolean("isCurrentUser"),
                        ),
                    )
                }
            }
            LeaderboardCache(entries, json.getString("dayKey"), json.getLong("fetchedAtMillis"))
        }.getOrNull()
    }

    fun save(cache: LeaderboardCache) {
        val entries = JSONArray()
        cache.entries.forEach { entry ->
            entries.put(
                JSONObject()
                    .put("rank", entry.rank)
                    .put("displayName", entry.displayName)
                    .put("todayCount", entry.todayCount)
                    .put("countryCode", entry.countryCode)
                    .put("isCurrentUser", entry.isCurrentUser),
            )
        }
        preferences.edit().putString(
            KEY_PAYLOAD,
            JSONObject()
                .put("entries", entries)
                .put("dayKey", cache.dayKey)
                .put("fetchedAtMillis", cache.fetchedAtMillis)
                .toString(),
        ).apply()
    }

    fun invalidate() {
        preferences.edit().remove(KEY_PAYLOAD).apply()
    }

    companion object {
        const val MAX_AGE_MILLIS = 30 * 60 * 1_000L
        private const val KEY_PAYLOAD = "snapshot"

        fun isFresh(cache: LeaderboardCache?, dayKey: String, nowMillis: Long): Boolean =
            cache != null && cache.dayKey == dayKey && nowMillis - cache.fetchedAtMillis < MAX_AGE_MILLIS

        fun requiresResumeRefresh(backgroundedAtMillis: Long, nowMillis: Long): Boolean =
            backgroundedAtMillis > 0 && nowMillis - backgroundedAtMillis >= MAX_AGE_MILLIS
    }
}
