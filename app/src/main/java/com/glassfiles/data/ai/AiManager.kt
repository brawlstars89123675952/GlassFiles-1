package com.glassfiles.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

// ═══════════════════════════════════
// Gemini Models
// ═══════════════════════════════════

enum class AiProvider(val label: String, val modelId: String, val supportsVision: Boolean, val desc: String) {
    GEMINI_FLASH("Gemini 2.5 Flash", "gemini-2.5-flash", true, "Fast, efficient"),
    GEMINI_PRO("Gemini 2.5 Pro", "gemini-2.5-pro", true, "Most capable"),
    GEMINI_FLASH_LITE("Gemini 2.5 Flash-Lite", "gemini-2.5-flash-lite", true, "Lightweight"),
    GEMINI_3_FLASH("Gemini 3 Flash", "gemini-3-flash-preview", true, "Next-gen fast"),
    GEMINI_31_PRO("Gemini 3.1 Pro", "gemini-3.1-pro-preview", true, "Next-gen pro");

    val isGemini: Boolean get() = true
}

data class ChatMessage(val role: String, val content: String, val imageBase64: String? = null)

// ═══════════════════════════════════
// API Key Storage — no hardcoded keys
// ═══════════════════════════════════

object GeminiKeyStore {
    private const val PREFS = "gemini_prefs"
    private const val KEY_GEMINI = "api_key"
    private const val KEY_PROXY = "proxy_url"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getKey(context: Context): String = prefs(context).getString(KEY_GEMINI, "") ?: ""
    fun saveKey(context: Context, key: String) = prefs(context).edit().putString(KEY_GEMINI, key.trim()).apply()
    fun hasKey(context: Context): Boolean = getKey(context).isNotBlank()

    fun getProxy(context: Context): String = prefs(context).getString(KEY_PROXY, "") ?: ""
    fun saveProxy(context: Context, url: String) = prefs(context).edit().putString(KEY_PROXY, url.trim().trimEnd('/')).apply()
}

// ═══════════════════════════════════
// AI Manager — Gemini only
// ═══════════════════════════════════

object AiManager {
    private const val GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
    private const val SYSTEM_PROMPT = "You are a helpful AI assistant in the GlassFiles app — a file manager for Android. Respond in the same language as the user."

    suspend fun chat(
        provider: AiProvider,
        messages: List<ChatMessage>,
        geminiKey: String = "",
        openRouterKey: String = "", // kept for compatibility, unused
        proxyUrl: String = "",
        onChunk: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        if (geminiKey.isBlank()) throw Exception("Enter your Gemini API key in AI settings")
        doChatGemini(provider.modelId, messages, geminiKey, proxyUrl, onChunk)
    }

    private fun doChatGemini(
        modelId: String, messages: List<ChatMessage>,
        apiKey: String, proxyUrl: String, onChunk: (String) -> Unit
    ): String {
        val base = proxyUrl.ifBlank { GEMINI_BASE }
        val url = "$base/$modelId:streamGenerateContent?alt=sse&key=$apiKey"

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true; connectTimeout = 30000; readTimeout = 120000
        }

        val contents = JSONArray()
        messages.forEach { msg ->
            val role = if (msg.role == "assistant") "model" else "user"
            val parts = JSONArray()
            if (msg.content.isNotBlank()) parts.put(JSONObject().put("text", msg.content))
            if (msg.imageBase64 != null) {
                parts.put(JSONObject().put("inlineData",
                    JSONObject().put("mimeType", "image/jpeg").put("data", msg.imageBase64)))
            }
            contents.put(JSONObject().put("role", role).put("parts", parts))
        }

        val body = JSONObject()
            .put("contents", contents)
            .put("systemInstruction", JSONObject().put("parts",
                JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT))))
            .toString()

        conn.outputStream.use { it.write(body.toByteArray()) }

        val code = conn.responseCode
        if (code !in 200..299) {
            val err = (if (code >= 400) conn.errorStream else conn.inputStream)
                ?.bufferedReader()?.readText()?.take(800) ?: "error $code"
            conn.disconnect()
            val googleMsg = try { JSONObject(err).optJSONObject("error")?.optString("message", "") ?: "" } catch (_: Exception) { "" }
            val detail = googleMsg.ifBlank { err.take(200) }
            when (code) {
                400 -> throw Exception("400: $detail")
                403 -> throw Exception("403 Access denied: $detail")
                404 -> throw Exception("Model $modelId not found")
                429 -> throw Exception("429 Rate limit: $detail")
                else -> throw Exception("$code: $detail")
            }
        }

        val sb = StringBuilder()
        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val l = line ?: continue
            if (!l.startsWith("data: ")) continue
            val data = l.removePrefix("data: ").trim()
            if (data == "[DONE]" || data.isEmpty()) continue
            try {
                val json = JSONObject(data)
                val candidates = json.optJSONArray("candidates") ?: continue
                if (candidates.length() == 0) continue
                val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: continue
                for (i in 0 until parts.length()) {
                    val text = parts.getJSONObject(i).optString("text", "")
                    if (text.isNotEmpty()) { sb.append(text); onChunk(text) }
                }
            } catch (_: Exception) {}
        }
        reader.close(); conn.disconnect()
        return sb.toString()
    }

    fun encodeImage(file: File): String? = try {
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val maxSide = 1024
        val scaled = if (bmp.width > maxSide || bmp.height > maxSide) {
            val ratio = minOf(maxSide.toFloat() / bmp.width, maxSide.toFloat() / bmp.height)
            Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt(), (bmp.height * ratio).toInt(), true)
        } else bmp
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    } catch (_: Exception) { null }

    fun readFileForAi(file: File, maxChars: Int = 8000): String? = try {
        if (file.length() > 500_000) "[File too large: ${file.length() / 1024}KB]"
        else {
            val text = file.readText()
            if (text.length > maxChars) text.take(maxChars) + "\n...[truncated, ${text.length} chars]" else text
        }
    } catch (_: Exception) { null }
}
