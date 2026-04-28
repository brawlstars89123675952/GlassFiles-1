package com.glassfiles.data.ai.usage

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Append-only on-disk log of [AiUsageRecord]s. One JSON object per
 * line ("JSON Lines" format) — easy to append cheaply and easy to
 * scan in chronological order.
 *
 * Storage location: `<filesDir>/ai_usage.log`. Automatically rotated
 * once the file exceeds [MAX_RECORDS] entries — the oldest 25% are
 * dropped to keep the file bounded.
 *
 * **Never serialise prompts, tool outputs, file contents, or any raw
 * provider response.** Only the labels and counters in
 * [AiUsageRecord] are persisted. Anything else added in future must
 * pass the same privacy bar.
 */
object AiUsageStore {
    private const val FILE_NAME = "ai_usage.log"
    private const val MAX_RECORDS = 10_000
    private const val TAG = "AiUsageStore"

    private fun file(context: Context): File =
        File(context.filesDir, FILE_NAME)

    /**
     * Append one record. Best-effort: any I/O failure is swallowed so
     * a broken disk doesn't crash the agent loop / chat call. Errors
     * are logged at WARN.
     */
    fun append(context: Context, record: AiUsageRecord) {
        try {
            val f = file(context)
            f.appendText(toJson(record).toString() + "\n")
            // Cheap guard: only check size after every append. The
            // line count is computed lazily on file rotation, not
            // every write, so this stays O(1) on the hot path.
            if (f.length() > MAX_RECORDS * 256L) {
                rotate(f)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "append failed", t)
        }
    }

    /** Return all records in chronological order (oldest first). */
    fun list(context: Context): List<AiUsageRecord> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return try {
            f.readLines()
                .asSequence()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    runCatching { fromJson(JSONObject(line)) }.getOrNull()
                }
                .toList()
        } catch (t: Throwable) {
            Log.w(TAG, "list failed", t)
            emptyList()
        }
    }

    /** Wipe all records — privacy reset button on the Usage screen. */
    fun clear(context: Context) {
        try {
            file(context).delete()
        } catch (t: Throwable) {
            Log.w(TAG, "clear failed", t)
        }
    }

    private fun rotate(f: File) {
        val lines = f.readLines()
        if (lines.size <= MAX_RECORDS) return
        val keepFrom = lines.size - (MAX_RECORDS * 3 / 4)
        val kept = lines.drop(keepFrom)
        f.writeText(kept.joinToString("\n", postfix = "\n"))
    }

    private fun toJson(r: AiUsageRecord): JSONObject = JSONObject().apply {
        put("providerId", r.providerId)
        put("modelId", r.modelId)
        put("mode", r.mode.name)
        put("inputTokens", r.inputTokens)
        put("outputTokens", r.outputTokens)
        put("totalTokens", r.totalTokens)
        put("estimatedInputChars", r.estimatedInputChars)
        put("estimatedOutputChars", r.estimatedOutputChars)
        put("toolCallsCount", r.toolCallsCount)
        put("filesReadCount", r.filesReadCount)
        put("filesWrittenCount", r.filesWrittenCount)
        put("writeProposalsCount", r.writeProposalsCount)
        if (r.repoName != null) put("repoName", r.repoName)
        if (r.branchName != null) put("branchName", r.branchName)
        if (r.isPrivateRepo != null) put("isPrivateRepo", r.isPrivateRepo)
        if (r.costMode != null) put("costMode", r.costMode)
        put("estimated", r.estimated)
        put("createdAt", r.createdAt)
    }

    private fun fromJson(o: JSONObject): AiUsageRecord = AiUsageRecord(
        providerId = o.optString("providerId"),
        modelId = o.optString("modelId"),
        mode = runCatching { AiUsageMode.valueOf(o.optString("mode")) }
            .getOrDefault(AiUsageMode.CHAT),
        inputTokens = o.optInt("inputTokens"),
        outputTokens = o.optInt("outputTokens"),
        totalTokens = o.optInt("totalTokens"),
        estimatedInputChars = o.optInt("estimatedInputChars"),
        estimatedOutputChars = o.optInt("estimatedOutputChars"),
        toolCallsCount = o.optInt("toolCallsCount"),
        filesReadCount = o.optInt("filesReadCount"),
        filesWrittenCount = o.optInt("filesWrittenCount"),
        writeProposalsCount = o.optInt("writeProposalsCount"),
        repoName = if (o.has("repoName")) o.optString("repoName") else null,
        branchName = if (o.has("branchName")) o.optString("branchName") else null,
        isPrivateRepo = if (o.has("isPrivateRepo")) o.optBoolean("isPrivateRepo") else null,
        costMode = if (o.has("costMode")) o.optString("costMode") else null,
        estimated = o.optBoolean("estimated", true),
        createdAt = o.optLong("createdAt"),
    )
}
