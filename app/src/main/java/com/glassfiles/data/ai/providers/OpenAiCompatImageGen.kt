package com.glassfiles.data.ai.providers

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL

/**
 * Shared image-generation helper for providers that follow the OpenAI
 * `POST /v1/images/generations` shape (OpenAI itself, xAI, OpenRouter pass-through).
 *
 * The endpoint accepts:
 * ```
 *   {"model": "...", "prompt": "...", "n": N, "size": "WxH"}
 * ```
 * and returns
 * ```
 *   {"data": [{"b64_json": "..."} | {"url": "..."}, ...]}
 * ```
 *
 * Some models (`gpt-image-1`, `grok-2-image`) inline base64; classic DALL·E
 * returns a CDN URL. We handle both and persist results to `cacheDir/ai_images/`.
 * Caller is responsible for promoting the cache copy into the system gallery
 * via `MediaStore` (see `ImageGallerySaver`).
 */
internal object OpenAiCompatImageGen {

    suspend fun generate(
        baseUrl: String,
        cacheDir: File,
        providerLabel: String,
        fileTag: String,
        modelId: String,
        prompt: String,
        apiKey: String,
        size: String,
        n: Int,
        extraHeaders: Map<String, String> = emptyMap(),
    ): List<String> = withContext(Dispatchers.IO) {
        // "auto" means: let the model pick its own resolution. The OpenAI gpt-image-1
        // API recognises the literal string, but xAI's `grok-2-image` rejects any
        // `size` field at all (it always returns 1024x768). Treat auto / blank as
        // "omit the size field entirely" for non-OpenAI deployments.
        val body = JSONObject()
            .put("model", modelId)
            .put("prompt", prompt)
            .put("n", n.coerceIn(1, 10))
            .apply {
                val isOpenAi = baseUrl.contains("api.openai.com")
                if (size.isNotBlank() && !(size.equals("auto", true) && !isOpenAi)) {
                    put("size", size)
                }
            }
            .toString()

        val conn = Http.postJson(
            "$baseUrl/images/generations",
            body,
            mapOf(
                "Authorization" to "Bearer $apiKey",
                "Accept" to "application/json",
            ) + extraHeaders,
        )
        Http.ensureOk(conn, providerLabel)
        val raw = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val data = JSONObject(raw).optJSONArray("data") ?: JSONArray()
        val outDir = File(cacheDir, "ai_images").apply { mkdirs() }
        val results = mutableListOf<String>()
        val now = System.currentTimeMillis()

        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val target = File(outDir, "${fileTag}_${now}_$i.png")
            when {
                item.has("b64_json") && !item.isNull("b64_json") -> {
                    val bytes = Base64.decode(item.getString("b64_json"), Base64.DEFAULT)
                    target.writeBytes(bytes)
                }
                item.has("url") && !item.isNull("url") -> {
                    URL(item.getString("url")).openStream().use { input ->
                        target.outputStream().use { input.copyTo(it) }
                    }
                }
                else -> continue
            }
            results += target.absolutePath
        }
        if (results.isEmpty()) {
            throw RuntimeException("$providerLabel returned no image data")
        }
        results
    }
}
