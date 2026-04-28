package com.glassfiles.data.ai.agent

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Persists the in-session [GitHubToolExecutor] file-content cache
 * across sessions. The on-disk format is a single JSON file per
 * `(repo, branch)` pair, keyed by cleaned file path → text content,
 * with a TTL stamp that lets old entries fall off after [TTL_MS].
 *
 * Why disk-backed: when the user starts a new chat session against a
 * repo they have been working on, the model usually re-reads the same
 * 5–10 files first. Persisting the cache across sessions skips those
 * round trips entirely and keeps the agent loop snappy.
 *
 * Staleness contract: entries older than 24 hours are dropped on
 * load. Entries that are still inside the window may be stale if
 * the file was edited on the remote — the caller is expected to be
 * OK with that for chat purposes (the model will read again if it
 * suspects a mismatch). For read-then-edit flows the executor's
 * own writeback already updates the cache atomically.
 */
object ReadFileDiskCache {
    private const val TAG = "ReadFileDiskCache"
    private const val DIR = "ai_filecache"
    private const val TTL_MS = 24L * 60L * 60L * 1000L
    /** Hard cap on the per-(repo,branch) cache file size in bytes. */
    private const val MAX_FILE_BYTES = 4L * 1024L * 1024L

    private fun dir(context: Context): File =
        File(context.filesDir, DIR).also { it.mkdirs() }

    private fun file(context: Context, repoFullName: String, branch: String): File {
        // Replace slash with __ so the file name stays flat on disk.
        val safeRepo = repoFullName.replace("/", "__")
        val safeBranch = branch.replace("/", "__")
        return File(dir(context), "${safeRepo}__${safeBranch}.json")
    }

    /**
     * Returns the persisted cache for [repoFullName] / [branch], with
     * stale entries (> TTL) filtered out. Returns an empty map when
     * the file is missing, malformed, or oversize.
     */
    fun load(context: Context, repoFullName: String, branch: String): MutableMap<String, String> {
        val f = file(context, repoFullName, branch)
        if (!f.exists()) return mutableMapOf()
        if (f.length() > MAX_FILE_BYTES) {
            // Defensive: someone managed to bloat the cache. Drop it.
            f.delete()
            return mutableMapOf()
        }
        return try {
            val root = JSONObject(f.readText())
            val now = System.currentTimeMillis()
            val out = mutableMapOf<String, String>()
            val entries = root.optJSONObject("entries") ?: return mutableMapOf()
            entries.keys().forEach { k ->
                val e = entries.optJSONObject(k) ?: return@forEach
                val ts = e.optLong("ts", 0L)
                if (now - ts > TTL_MS) return@forEach
                val content = e.optString("content", "")
                if (content.isNotEmpty()) out[k] = content
            }
            out
        } catch (t: Throwable) {
            Log.w(TAG, "load failed for $repoFullName@$branch", t)
            mutableMapOf()
        }
    }

    /**
     * Persists [cache] for [repoFullName] / [branch]. Empty caches
     * trigger a delete instead of writing a stub file. Best-effort:
     * I/O failures are swallowed and logged at WARN.
     */
    fun save(
        context: Context,
        repoFullName: String,
        branch: String,
        cache: Map<String, String>,
    ) {
        try {
            val f = file(context, repoFullName, branch)
            if (cache.isEmpty()) {
                if (f.exists()) f.delete()
                return
            }
            val now = System.currentTimeMillis()
            val entries = JSONObject()
            cache.forEach { (path, content) ->
                if (content.isEmpty()) return@forEach
                entries.put(path, JSONObject().apply {
                    put("ts", now)
                    put("content", content)
                })
            }
            val root = JSONObject().apply {
                put("repo", repoFullName)
                put("branch", branch)
                put("entries", entries)
            }
            // Soft cap by serialised size: drop oldest paths if we
            // overflow MAX_FILE_BYTES. Keys() iteration order is
            // insertion order on Kotlin maps, so we drop the
            // earliest-inserted entries first.
            val text = root.toString()
            if (text.length > MAX_FILE_BYTES) {
                Log.w(TAG, "cache for $repoFullName@$branch over cap; trimming")
                f.delete()
                return
            }
            f.writeText(text)
        } catch (t: Throwable) {
            Log.w(TAG, "save failed for $repoFullName@$branch", t)
        }
    }

    /** Wipes every cache file — bound to a privacy reset button. */
    fun clearAll(context: Context) {
        try {
            dir(context).listFiles()?.forEach { it.delete() }
        } catch (t: Throwable) {
            Log.w(TAG, "clearAll failed", t)
        }
    }
}
