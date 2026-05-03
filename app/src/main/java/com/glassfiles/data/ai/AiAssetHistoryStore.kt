package com.glassfiles.data.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists generated image / video records (the metadata, plus a path to the
 * cached file on disk) so the user can scroll back through previous outputs
 * after leaving the screen.
 *
 * Stored as JSON in SharedPreferences under per-mode keys (`image`, `video`,
 * `music`).
 * Capped at [MAX_PER_MODE]; oldest entries are evicted when the cap is hit.
 *
 * The cached file itself lives under `cacheDir/ai_images/`, `ai_videos/`,
 * or `ai_music/`
 * and is owned by the screens that produce it; this store only remembers
 * the path. If the file is deleted by the OS cache eviction the row is
 * still listed but rendering will fail gracefully.
 */
object AiAssetHistoryStore {
    private const val PREFS = "ai_asset_history"
    private const val MAX_PER_MODE = 100

    const val MODE_IMAGE = "image"
    const val MODE_VIDEO = "video"
    const val MODE_MUSIC = "music"

    /**
     * Single record describing one generated asset.
     *
     * @param id stable identifier (epoch-millis at creation).
     * @param mode [MODE_IMAGE], [MODE_VIDEO], or [MODE_MUSIC].
     * @param providerId enum name of the provider that produced the asset.
     * @param modelId raw model id (e.g. `gpt-image-1`, `veo-2`).
     * @param modelDisplay human-readable label shown in the UI.
     * @param prompt the user prompt used to generate.
     * @param size for images: `WxH` or `auto`; for videos: aspect-ratio token;
     *  for music: duration/format summary.
     * @param filePath absolute path of the cached file on disk.
     * @param savedToGalleryUri non-null if the user has already saved this
     *  asset to the system gallery (used to render a check icon).
     * @param createdAt epoch millis.
     */
    data class Record(
        val id: Long,
        val mode: String,
        val providerId: String,
        val modelId: String,
        val modelDisplay: String,
        val prompt: String,
        val size: String,
        val filePath: String,
        val savedToGalleryUri: String? = null,
        val createdAt: Long,
    )

    fun list(context: Context, mode: String): List<Record> {
        val raw = prefs(context).getString(mode, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        Record(
                            id = obj.optLong("id", 0L),
                            mode = obj.optString("mode", mode),
                            providerId = obj.optString("providerId", ""),
                            modelId = obj.optString("modelId", ""),
                            modelDisplay = obj.optString("modelDisplay", ""),
                            prompt = obj.optString("prompt", ""),
                            size = obj.optString("size", ""),
                            filePath = obj.optString("filePath", ""),
                            savedToGalleryUri = obj.optString("savedToGalleryUri", "")
                                .takeIf { it.isNotBlank() },
                            createdAt = obj.optLong("createdAt", 0L),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(context: Context, record: Record) {
        val current = list(context, record.mode).toMutableList()
        // newest-first, FIFO eviction
        current.add(0, record)
        while (current.size > MAX_PER_MODE) current.removeAt(current.lastIndex)
        write(context, record.mode, current)
    }

    fun remove(context: Context, mode: String, id: Long) {
        val current = list(context, mode).filter { it.id != id }
        write(context, mode, current)
    }

    fun update(context: Context, record: Record) {
        val current = list(context, record.mode).map { if (it.id == record.id) record else it }
        write(context, record.mode, current)
    }

    fun clear(context: Context, mode: String) {
        prefs(context).edit().remove(mode).apply()
    }

    private fun write(context: Context, mode: String, records: List<Record>) {
        val arr = JSONArray()
        records.forEach { r ->
            val obj = JSONObject()
                .put("id", r.id)
                .put("mode", r.mode)
                .put("providerId", r.providerId)
                .put("modelId", r.modelId)
                .put("modelDisplay", r.modelDisplay)
                .put("prompt", r.prompt)
                .put("size", r.size)
                .put("filePath", r.filePath)
                .put("createdAt", r.createdAt)
            if (r.savedToGalleryUri != null) obj.put("savedToGalleryUri", r.savedToGalleryUri)
            arr.put(obj)
        }
        prefs(context).edit().putString(mode, arr.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
