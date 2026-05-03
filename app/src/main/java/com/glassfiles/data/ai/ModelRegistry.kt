package com.glassfiles.data.ai

import android.content.Context
import com.glassfiles.data.ai.models.AiCapability
import com.glassfiles.data.ai.models.AiModel
import com.glassfiles.data.ai.models.AiProviderId
import com.glassfiles.data.ai.providers.AiProviders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Caches model catalogs per provider in `SharedPreferences`.
 *
 * Single source of truth for "which models are available right now". Pickers
 * call [getModels] / [getAllModels] and never hit a provider directly. UI also
 * exposes a "Refresh" action that calls [getModels] with `force = true`.
 */
object ModelRegistry {
    private const val PREFS = "ai_model_registry"
    private const val TTL_MS = 24 * 60 * 60 * 1000L

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun keyData(p: AiProviderId) = "models_${p.name}"
    private fun keyTimestamp(p: AiProviderId) = "models_${p.name}_ts"

    /**
     * Returns the cached list if fresh (< 24h) and non-empty; otherwise fetches
     * from the provider and updates the cache. With [force] = true, always fetches.
     *
     * If [apiKey] is blank, returns the cached list (or empty) — never throws.
     * If the network call fails, returns the *stale* cache (if any) instead of
     * propagating, so a flaky network doesn't wipe the picker.
     */
    suspend fun getModels(
        context: Context,
        provider: AiProviderId,
        apiKey: String,
        force: Boolean = false,
    ): List<AiModel> = withContext(Dispatchers.IO) {
        val cached = readCache(context, provider)
        val ts = prefs(context).getLong(keyTimestamp(provider), 0L)
        val fresh = (System.currentTimeMillis() - ts) < TTL_MS && cached.isNotEmpty()
        if (!force && fresh) return@withContext cached
        if (apiKey.isBlank()) return@withContext cached

        try {
            val live = AiProviders.get(provider).listModels(context, apiKey)
            if (live.isNotEmpty()) {
                writeCache(context, provider, live)
                return@withContext live
            }
            cached
        } catch (_: Exception) {
            cached
        }
    }

    /**
     * Force-refreshes the cache for a provider and propagates exceptions on
     * failure (auth error, network error, parsing error). Use this when the
     * caller needs to surface a real error to the user — e.g. the manual
     * "Refresh" button in `AiModelsScreen`. Unlike [getModels], this NEVER
     * silently falls back to a stale cache: on success it stores and returns
     * the live list; on failure it throws.
     *
     * Throws [IllegalStateException] if [apiKey] is blank.
     */
    suspend fun refreshOrThrow(
        context: Context,
        provider: AiProviderId,
        apiKey: String,
    ): List<AiModel> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw IllegalStateException("Enter the ${provider.displayName} API key first")
        }
        val live = AiProviders.get(provider).listModels(context, apiKey)
        writeCache(context, provider, live)
        live
    }

    /** Aggregated view across every provider that has a non-blank API key configured. */
    suspend fun getAllModels(
        context: Context,
        force: Boolean = false,
    ): Map<AiProviderId, List<AiModel>> = withContext(Dispatchers.IO) {
        val out = mutableMapOf<AiProviderId, List<AiModel>>()
        AiKeyStore.configuredProviders(context).forEach { p ->
            val key = AiKeyStore.getKey(context, p)
            out[p] = getModels(context, p, key, force)
        }
        out
    }

    /** Filters [getAllModels] to those exposing at least one of [required]. */
    suspend fun getModelsWith(
        context: Context,
        required: Set<AiCapability>,
        force: Boolean = false,
    ): List<AiModel> {
        val all = getAllModels(context, force)
        return all.values.flatten().filter { m -> required.any { it in m.capabilities } }
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    // ─── persistence ────────────────────────────────────────────────────────
    private fun readCache(context: Context, provider: AiProviderId): List<AiModel> {
        val json = prefs(context).getString(keyData(provider), null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val capsArr = o.getJSONArray("caps")
                val caps = (0 until capsArr.length())
                    .mapNotNull { runCatching { AiCapability.valueOf(capsArr.getString(it)) }.getOrNull() }
                    .toSet()
                val id = normalizeCachedId(provider, o.getString("id"))
                AiModel(
                    providerId = provider,
                    id = id,
                    displayName = normalizeCachedDisplayName(provider, id, o.optString("name", id)),
                    capabilities = caps,
                    contextWindow = o.optInt("ctx").takeIf { it > 0 },
                    deprecated = o.optBoolean("dep", false),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun normalizeCachedId(provider: AiProviderId, rawId: String): String {
        val clean = rawId.trim()
        if (provider != AiProviderId.ACEMUSIC) return clean
        if (clean.equals("ACE Step", ignoreCase = true) || clean.equals("ACE Steps", ignoreCase = true)) {
            return "acemusic/acestep-v15-turbo"
        }
        return if (clean.any(Char::isWhitespace) && '/' !in clean) "acemusic/acestep-v15-turbo" else clean
    }

    private fun normalizeCachedDisplayName(provider: AiProviderId, id: String, rawName: String): String {
        val clean = rawName.trim().ifBlank { id }
        if (provider != AiProviderId.ACEMUSIC) return clean
        val shortId = id.substringAfterLast('/')
        return if (shortId !in clean) "$clean · $shortId" else clean
    }

    private fun writeCache(context: Context, provider: AiProviderId, models: List<AiModel>) {
        val arr = JSONArray()
        models.forEach { m ->
            arr.put(
                JSONObject()
                    .put("id", m.id)
                    .put("name", m.displayName)
                    .put("caps", JSONArray(m.capabilities.map { it.name }))
                    .put("ctx", m.contextWindow ?: 0)
                    .put("dep", m.deprecated),
            )
        }
        prefs(context).edit()
            .putString(keyData(provider), arr.toString())
            .putLong(keyTimestamp(provider), System.currentTimeMillis())
            .apply()
    }
}
