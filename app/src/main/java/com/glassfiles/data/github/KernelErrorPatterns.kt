package com.glassfiles.data.github

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val KERNEL_ERRORS_ASSET = "kernel_errors.json"
private const val KERNEL_ERRORS_CACHE = "kernel_errors_cache.json"
private const val REMOTE_KERNEL_ERRORS_URL = "" // TODO: Fill with raw kernel_errors.json URL.

data class KernelErrorCatalog(
    val version: Int,
    val source: KernelErrorSource,
    val patterns: List<KernelErrorPattern>
)

data class KernelErrorPattern(
    val id: String,
    val matches: List<String>,
    val titleKey: String,
    val descriptionKey: String,
    val category: String
)

enum class KernelErrorSource(val label: String) {
    REMOTE("remote"),
    CACHED("cached"),
    BUNDLED("bundled")
}

object KernelErrorPatterns {
    @Volatile private var memoryCatalog: KernelErrorCatalog? = null

    suspend fun load(context: Context): KernelErrorCatalog = withContext(Dispatchers.IO) {
        memoryCatalog?.let { return@withContext it }

        val remote = fetchRemoteOrNull()
        if (remote != null) {
            cacheFile(context).writeText(remote)
            return@withContext parse(remote, KernelErrorSource.REMOTE).also { memoryCatalog = it }
        }

        val cached = runCatching { cacheFile(context).takeIf { it.exists() }?.readText() }.getOrNull()
        if (!cached.isNullOrBlank()) {
            parse(cached, KernelErrorSource.CACHED).also {
                memoryCatalog = it
                return@withContext it
            }
        }

        val bundled = context.assets.open(KERNEL_ERRORS_ASSET).bufferedReader().use { it.readText() }
        parse(bundled, KernelErrorSource.BUNDLED).also { memoryCatalog = it }
    }

    fun diagnose(context: Context, catalog: KernelErrorCatalog?, log: String): List<String> {
        val activeCatalog = catalog ?: return emptyList()
        val tail = log.lines().takeLast(200).joinToString("\n")
        return activeCatalog.patterns.mapNotNull { pattern ->
            val matched = pattern.matches.any { raw ->
                runCatching { Regex(raw, setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)).containsMatchIn(tail) }
                    .getOrDefault(false)
            }
            if (matched) {
                val title = context.stringByName(pattern.titleKey).ifBlank { pattern.id }
                val description = context.stringByName(pattern.descriptionKey)
                listOf(title, description).filter { it.isNotBlank() }.joinToString(". ")
            } else {
                null
            }
        }.distinct()
    }

    private fun fetchRemoteOrNull(): String? {
        if (REMOTE_KERNEL_ERRORS_URL.isBlank()) return null
        return runCatching {
            val conn = (URL(REMOTE_KERNEL_ERRORS_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "GlassFiles")
            }
            try {
                if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().use { it.readText() } else null
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun parse(raw: String, source: KernelErrorSource): KernelErrorCatalog {
        val root = JSONObject(raw)
        val patterns = root.optJSONArray("patterns") ?: JSONArray()
        return KernelErrorCatalog(
            version = root.optInt("version", 1),
            source = source,
            patterns = (0 until patterns.length()).mapNotNull { index ->
                val item = patterns.optJSONObject(index) ?: return@mapNotNull null
                KernelErrorPattern(
                    id = item.optString("id"),
                    matches = item.optJSONArray("match")?.let { arr ->
                        (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { value -> value.isNotBlank() } }
                    }.orEmpty(),
                    titleKey = item.optString("title_key"),
                    descriptionKey = item.optString("description_key"),
                    category = item.optString("category")
                )
            }.filter { it.id.isNotBlank() && it.matches.isNotEmpty() }
        )
    }

    private fun cacheFile(context: Context): File = File(context.filesDir, KERNEL_ERRORS_CACHE)
}

private fun Context.stringByName(name: String): String {
    if (name.isBlank()) return ""
    val id = resources.getIdentifier(name, "string", packageName)
    return if (id == 0) "" else getString(id)
}
