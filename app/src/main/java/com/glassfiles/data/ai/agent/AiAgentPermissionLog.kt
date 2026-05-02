package com.glassfiles.data.ai.agent

import android.content.Context
import org.json.JSONObject
import java.io.File

object AiAgentPermissionLog {
    data class Entry(
        val timestamp: Long = System.currentTimeMillis(),
        val sessionId: String,
        val repoFullName: String,
        val tool: String,
        val category: String,
        val decision: String,
        val reason: String,
        val autoApproved: Boolean,
        val yoloMode: Boolean,
        val sessionTrust: Boolean,
        val protectedPattern: String?,
        val activeSkill: String,
    )

    fun append(context: Context, entry: Entry) {
        synchronized(this) {
            val file = logFile(context)
            file.parentFile?.mkdirs()
            file.appendText(entry.toJson().toString() + "\n")
            trimIfNeeded(file)
        }
    }

    fun recent(context: Context, limit: Int = 100): List<Entry> {
        val file = logFile(context)
        if (!file.isFile) return emptyList()
        return file.readLines()
            .asReversed()
            .asSequence()
            .take(limit.coerceIn(1, 500))
            .mapNotNull { line -> runCatching { fromJson(JSONObject(line)) }.getOrNull() }
            .toList()
    }

    fun clear(context: Context) {
        logFile(context).delete()
    }

    private fun Entry.toJson(): JSONObject =
        JSONObject()
            .put("timestamp", timestamp)
            .put("sessionId", sessionId)
            .put("repoFullName", repoFullName)
            .put("tool", tool)
            .put("category", category)
            .put("decision", decision)
            .put("reason", reason)
            .put("autoApproved", autoApproved)
            .put("yoloMode", yoloMode)
            .put("sessionTrust", sessionTrust)
            .put("activeSkill", activeSkill)
            .apply {
                protectedPattern?.let { put("protectedPattern", it) }
            }

    private fun fromJson(json: JSONObject): Entry =
        Entry(
            timestamp = json.optLong("timestamp"),
            sessionId = json.optString("sessionId"),
            repoFullName = json.optString("repoFullName"),
            tool = json.optString("tool"),
            category = json.optString("category"),
            decision = json.optString("decision"),
            reason = json.optString("reason"),
            autoApproved = json.optBoolean("autoApproved"),
            yoloMode = json.optBoolean("yoloMode"),
            sessionTrust = json.optBoolean("sessionTrust"),
            protectedPattern = json.optString("protectedPattern").takeIf { it.isNotBlank() },
            activeSkill = json.optString("activeSkill"),
        )

    private fun logFile(context: Context): File =
        File(context.filesDir, "ai_agent/permission_log.jsonl")

    private fun trimIfNeeded(file: File) {
        if (file.length() <= 512_000L) return
        val tail = file.readLines().takeLast(300)
        file.writeText(tail.joinToString("\n", postfix = "\n"))
    }
}
