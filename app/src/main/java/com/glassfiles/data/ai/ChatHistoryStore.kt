package com.glassfiles.data.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists per-mode chat transcripts so a user can leave the screen and come
 * back with the conversation intact.
 *
 * Stored as JSON in SharedPreferences under `mode` keys (`coding`, `chat`, …).
 * Single-file, single-conversation per mode for now — multi-thread history
 * would be a separate feature.
 */
object ChatHistoryStore {
    private const val PREFS = "ai_chat_history"

    data class Entry(
        val role: String,
        val content: String,
        val imageBase64: String? = null,
        val isError: Boolean = false,
    )

    fun load(context: Context, mode: String): List<Entry> {
        val raw = prefs(context).getString(mode, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        Entry(
                            role = obj.optString("role", "assistant"),
                            content = obj.optString("content", ""),
                            imageBase64 = obj.optString("imageBase64", "").takeIf { it.isNotBlank() },
                            isError = obj.optBoolean("isError", false),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, mode: String, entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            val obj = JSONObject()
                .put("role", e.role)
                .put("content", e.content)
                .put("isError", e.isError)
            if (e.imageBase64 != null) obj.put("imageBase64", e.imageBase64)
            arr.put(obj)
        }
        prefs(context).edit().putString(mode, arr.toString()).apply()
    }

    fun clear(context: Context, mode: String) {
        prefs(context).edit().remove(mode).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
