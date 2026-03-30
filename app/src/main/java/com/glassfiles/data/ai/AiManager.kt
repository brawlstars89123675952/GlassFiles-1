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
import java.util.zip.ZipFile

// ═══════════════════════════════════
// AI Models — Gemini + Qwen (full list)
// ═══════════════════════════════════

enum class AiProvider(
    val label: String, val modelId: String, val supportsVision: Boolean,
    val desc: String, val isGemini: Boolean = false, val isQwen: Boolean = false,
    val supportsFiles: Boolean = false, val category: String = ""
) {
    // ── Gemini ──
    GEMINI_FLASH("Gemini 2.5 Flash", "gemini-2.5-flash", true, "Fast, efficient", isGemini = true, category = "Gemini"),
    GEMINI_PRO("Gemini 2.5 Pro", "gemini-2.5-pro", true, "Most capable", isGemini = true, category = "Gemini"),
    GEMINI_FLASH_LITE("Gemini 2.5 Flash-Lite", "gemini-2.5-flash-lite", true, "Lightweight", isGemini = true, category = "Gemini"),
    GEMINI_3_FLASH("Gemini 3 Flash", "gemini-3-flash-preview", true, "Next-gen fast", isGemini = true, category = "Gemini"),
    GEMINI_31_PRO("Gemini 3.1 Pro", "gemini-3.1-pro-preview", true, "Next-gen pro", isGemini = true, category = "Gemini"),

    // ── Qwen Commercial ──
    QWEN3_MAX("Qwen3 Max", "qwen3-max", false, "Best Qwen3, complex tasks", isQwen = true, supportsFiles = true, category = "Qwen Commercial"),
    QWEN35_PLUS("Qwen3.5 Plus", "qwen3.5-plus", true, "Text + Vision, balanced", isQwen = true, supportsVision = true, supportsFiles = true, category = "Qwen Commercial"),
    QWEN_PLUS("Qwen Plus", "qwen-plus", false, "Smart, balanced", isQwen = true, supportsFiles = true, category = "Qwen Commercial"),
    QWEN35_FLASH("Qwen3.5 Flash", "qwen3.5-flash", true, "Fast, multimodal", isQwen = true, supportsVision = true, supportsFiles = true, category = "Qwen Commercial"),
    QWEN_FLASH("Qwen Flash", "qwen-flash", false, "Fastest, cheapest", isQwen = true, supportsFiles = true, category = "Qwen Commercial"),
    QWEN_TURBO("Qwen Turbo", "qwen-turbo", false, "Fast, cost-effective", isQwen = true, supportsFiles = true, category = "Qwen Commercial"),
    QWEN_LONG("Qwen Long", "qwen-long", false, "10M context window", isQwen = true, supportsFiles = true, category = "Qwen Commercial"),

    // ── Qwen Coder ──
    QWEN3_CODER_PLUS("Qwen3 Coder Plus", "qwen3-coder-plus", false, "Code + tool calling", isQwen = true, supportsFiles = true, category = "Qwen Coder"),
    QWEN3_CODER_FLASH("Qwen3 Coder Flash", "qwen3-coder-flash", false, "Fast code generation", isQwen = true, supportsFiles = true, category = "Qwen Coder"),

    // ── Qwen Vision ──
    QWEN3_VL_PLUS("Qwen3 VL Plus", "qwen3-vl-plus", true, "High-res vision", isQwen = true, supportsFiles = true, category = "Qwen Vision"),
    QWEN3_VL_FLASH("Qwen3 VL Flash", "qwen3-vl-flash", true, "Fast vision", isQwen = true, supportsFiles = true, category = "Qwen Vision"),
    QWEN_VL_PLUS("Qwen VL Plus", "qwen-vl-plus", true, "Vision + long text", isQwen = true, supportsFiles = true, category = "Qwen Vision"),
    QWEN_VL_MAX("Qwen VL Max", "qwen-vl-max", true, "Best vision model", isQwen = true, supportsFiles = true, category = "Qwen Vision"),
    QWEN_OCR("Qwen OCR", "qwen-ocr-latest", true, "Text extraction from images", isQwen = true, supportsFiles = true, category = "Qwen Vision"),

    // ── Qwen Reasoning ──
    QWQ_PLUS("QwQ Plus", "qwq-plus", false, "Deep reasoning", isQwen = true, supportsFiles = true, category = "Qwen Reasoning"),
    QWEN_MATH_PLUS("Qwen Math Plus", "qwen-math-plus", false, "Math problem solving", isQwen = true, supportsFiles = true, category = "Qwen Reasoning"),
    QWEN_MATH_TURBO("Qwen Math Turbo", "qwen-math-turbo", false, "Fast math", isQwen = true, supportsFiles = true, category = "Qwen Reasoning"),

    // ── Qwen Open-Source ──
    QWEN3_235B("Qwen3 235B-A22B", "qwen3-235b-a22b", false, "Largest open MoE", isQwen = true, supportsFiles = true, category = "Qwen Open-Source"),
    QWEN3_32B("Qwen3 32B", "qwen3-32b", false, "Dense 32B", isQwen = true, supportsFiles = true, category = "Qwen Open-Source"),
    QWEN3_14B("Qwen3 14B", "qwen3-14b", false, "Dense 14B", isQwen = true, supportsFiles = true, category = "Qwen Open-Source"),
    QWEN3_8B("Qwen3 8B", "qwen3-8b", false, "Dense 8B", isQwen = true, supportsFiles = true, category = "Qwen Open-Source"),
    QWEN3_4B("Qwen3 4B", "qwen3-4b", false, "Dense 4B", isQwen = true, supportsFiles = true, category = "Qwen Open-Source"),
    QWEN25_72B("Qwen2.5 72B", "qwen2.5-72b-instruct", false, "Large dense", isQwen = true, supportsFiles = true, category = "Qwen Open-Source"),
    QWEN25_32B("Qwen2.5 32B", "qwen2.5-32b-instruct", false, "Medium dense", isQwen = true, supportsFiles = true, category = "Qwen Open-Source"),
}

data class ChatMessage(val role: String, val content: String, val imageBase64: String? = null, val fileContent: String? = null)

// ═══════════════════════════════════
// API Key Storage
// ═══════════════════════════════════

object GeminiKeyStore {
    private const val PREFS = "gemini_prefs"
    private const val KEY_GEMINI = "api_key"
    private const val KEY_PROXY = "proxy_url"
    private const val KEY_QWEN = "qwen_api_key"
    private const val KEY_QWEN_REGION = "qwen_region"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getKey(context: Context): String = prefs(context).getString(KEY_GEMINI, "") ?: ""
    fun saveKey(context: Context, key: String) = prefs(context).edit().putString(KEY_GEMINI, key.trim()).apply()
    fun hasKey(context: Context): Boolean = getKey(context).isNotBlank()

    fun getProxy(context: Context): String = prefs(context).getString(KEY_PROXY, "") ?: ""
    fun saveProxy(context: Context, url: String) = prefs(context).edit().putString(KEY_PROXY, url.trim().trimEnd('/')).apply()

    fun getQwenKey(context: Context): String = prefs(context).getString(KEY_QWEN, "") ?: ""
    fun saveQwenKey(context: Context, key: String) = prefs(context).edit().putString(KEY_QWEN, key.trim()).apply()
    fun hasQwenKey(context: Context): Boolean = getQwenKey(context).isNotBlank()

    fun getQwenRegion(context: Context): String = prefs(context).getString(KEY_QWEN_REGION, "intl") ?: "intl"
    fun saveQwenRegion(context: Context, region: String) = prefs(context).edit().putString(KEY_QWEN_REGION, region).apply()
}

// ═══════════════════════════════════
// AI Manager
// ═══════════════════════════════════

object AiManager {
    private const val GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
    private const val SYSTEM_PROMPT = "You are a helpful AI assistant in the GlassFiles app — a file manager for Android. You can analyze files, code, images, and archives. Respond in the same language as the user."

    private fun qwenBaseUrl(region: String): String = when (region) {
        "cn" -> "https://dashscope.aliyuncs.com/compatible-mode/v1"
        "us" -> "https://dashscope-us.aliyuncs.com/compatible-mode/v1"
        "hk" -> "https://cn-hongkong.dashscope.aliyuncs.com/compatible-mode/v1"
        else -> "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"
    }

    suspend fun chat(
        provider: AiProvider, messages: List<ChatMessage>,
        geminiKey: String = "", openRouterKey: String = "", proxyUrl: String = "",
        qwenKey: String = "", qwenRegion: String = "intl",
        onChunk: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        when {
            provider.isGemini -> {
                if (geminiKey.isBlank()) throw Exception("Enter Gemini API key in AI settings")
                doChatGemini(provider.modelId, messages, geminiKey, proxyUrl, onChunk)
            }
            provider.isQwen -> {
                if (qwenKey.isBlank()) throw Exception("Enter Qwen API key in AI settings")
                doChatQwen(provider.modelId, messages, provider.supportsVision, qwenKey, qwenRegion, onChunk)
            }
            else -> throw Exception("Unknown provider")
        }
    }

    // ═══════════════════════════════════
    // Qwen (OpenAI-compatible streaming)
    // ═══════════════════════════════════

    private fun doChatQwen(
        modelId: String, messages: List<ChatMessage>, supportsVision: Boolean,
        apiKey: String, region: String, onChunk: (String) -> Unit
    ): String {
        val url = "${qwenBaseUrl(region)}/chat/completions"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true; connectTimeout = 30000; readTimeout = 120000
        }

        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))

        messages.forEach { msg ->
            when {
                msg.imageBase64 != null && supportsVision -> {
                    val content = JSONArray()
                    content.put(JSONObject().put("type", "text").put("text", msg.content))
                    content.put(JSONObject().put("type", "image_url").put("image_url",
                        JSONObject().put("url", "data:image/jpeg;base64,${msg.imageBase64}")))
                    msgs.put(JSONObject().put("role", msg.role).put("content", content))
                }
                msg.fileContent != null -> {
                    val fullText = if (msg.content.isNotBlank()) "${msg.content}\n\n--- File content ---\n${msg.fileContent}" else msg.fileContent
                    msgs.put(JSONObject().put("role", msg.role).put("content", fullText))
                }
                else -> msgs.put(JSONObject().put("role", msg.role).put("content", msg.content))
            }
        }

        val body = JSONObject().put("model", modelId).put("messages", msgs).put("stream", true).toString()
        conn.outputStream.use { it.write(body.toByteArray()) }

        val code = conn.responseCode
        if (code !in 200..299) {
            val err = (if (code >= 400) conn.errorStream else conn.inputStream)?.bufferedReader()?.readText()?.take(500) ?: "error $code"
            conn.disconnect()
            val detail = try { JSONObject(err).optJSONObject("error")?.optString("message", "") ?: err.take(200) } catch (_: Exception) { err.take(200) }
            throw Exception("Qwen $code: $detail")
        }

        val sb = StringBuilder()
        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val l = line ?: continue
            if (!l.startsWith("data: ")) continue
            val data = l.removePrefix("data: ").trim()
            if (data == "[DONE]") break
            try {
                val chunk = JSONObject(data).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("delta").optString("content", "")
                if (chunk.isNotEmpty()) { sb.append(chunk); onChunk(chunk) }
            } catch (_: Exception) {}
        }
        reader.close(); conn.disconnect()
        return sb.toString()
    }

    // ═══════════════════════════════════
    // Gemini (SSE streaming)
    // ═══════════════════════════════════

    private fun doChatGemini(
        modelId: String, messages: List<ChatMessage>,
        apiKey: String, proxyUrl: String, onChunk: (String) -> Unit
    ): String {
        val base = proxyUrl.ifBlank { GEMINI_BASE }
        val url = "$base/$modelId:streamGenerateContent?alt=sse&key=$apiKey"

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; setRequestProperty("Content-Type", "application/json")
            doOutput = true; connectTimeout = 30000; readTimeout = 120000
        }

        val contents = JSONArray()
        messages.forEach { msg ->
            val role = if (msg.role == "assistant") "model" else "user"
            val parts = JSONArray()
            if (msg.content.isNotBlank()) parts.put(JSONObject().put("text", msg.content))
            if (msg.fileContent != null) parts.put(JSONObject().put("text", "\n--- File content ---\n${msg.fileContent}"))
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
            val err = (if (code >= 400) conn.errorStream else conn.inputStream)?.bufferedReader()?.readText()?.take(800) ?: "error $code"
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

    // ═══════════════════════════════════
    // ZIP Archive Support (up to 60 files)
    // ═══════════════════════════════════

    private val TEXT_EXTENSIONS = setOf(
        "txt", "md", "json", "xml", "html", "css", "js", "ts", "kt", "java",
        "py", "c", "cpp", "h", "hpp", "cs", "swift", "go", "rs", "rb", "php",
        "sh", "bash", "zsh", "fish", "lua", "r", "scala", "dart", "jsx", "tsx",
        "vue", "svelte", "yml", "yaml", "toml", "ini", "cfg", "conf", "properties",
        "gradle", "csv", "sql", "graphql", "proto", "makefile", "dockerfile",
        "log", "env", "gitignore", "editorconfig", "prettierrc", "eslintrc",
        "tf", "hcl", "nix", "cmake", "bat", "ps1", "asm", "s", "v", "vhd",
        "tex", "bib", "rst", "adoc", "org", "wiki", "diff", "patch"
    )

    private val SKIP_EXTENSIONS = setOf("exe", "dll", "so", "bin", "apk", "aab", "dex", "class", "o", "a", "lib", "pyc", "pyd")

    fun extractZipForAi(zipPath: String, context: Context, maxFiles: Int = 60, maxCharsPerFile: Int = 6000): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        val tempDir = File(context.cacheDir, "ai_zip_${System.currentTimeMillis()}")
        try {
            tempDir.mkdirs()
            val zip = ZipFile(File(zipPath))
            zip.entries().asSequence()
                .filter { !it.isDirectory }
                .filter { val ext = it.name.substringAfterLast(".", "").lowercase(); ext !in SKIP_EXTENSIONS }
                .take(maxFiles)
                .forEach { entry ->
                    val ext = entry.name.substringAfterLast(".", "").lowercase()
                    val isText = ext in TEXT_EXTENSIONS || entry.name.lowercase().let { n ->
                        n.endsWith("makefile") || n.endsWith("dockerfile") || n.endsWith("jenkinsfile") || n.endsWith("vagrantfile")
                    }
                    val isImage = ext in listOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg")

                    when {
                        isText && entry.size < 500_000 -> {
                            try {
                                val text = zip.getInputStream(entry).bufferedReader().readText()
                                val truncated = if (text.length > maxCharsPerFile) text.take(maxCharsPerFile) + "\n...[truncated]" else text
                                results.add(Pair(entry.name, truncated))
                            } catch (_: Exception) { results.add(Pair(entry.name, "[read error]")) }
                        }
                        isImage -> results.add(Pair(entry.name, "[image: ${entry.size / 1024}KB]"))
                        else -> results.add(Pair(entry.name, "[binary: ${ext.uppercase()}, ${entry.size / 1024}KB]"))
                    }
                }
            val totalEntries = ZipFile(File(zipPath)).use { z -> z.entries().asSequence().filter { !it.isDirectory }.count() }
            if (totalEntries > maxFiles) results.add(Pair("...", "[${totalEntries - maxFiles} more files not shown]"))
            zip.close()
        } catch (e: Exception) { results.add(Pair("error", "Failed to read ZIP: ${e.message}")) }
        finally { tempDir.deleteRecursively() }
        return results
    }

    fun formatZipContents(entries: List<Pair<String, String>>): String {
        val sb = StringBuilder("Archive contents (${entries.size} files):\n\n")
        entries.forEach { (name, content) -> sb.append("=== $name ===\n$content\n\n") }
        return sb.toString()
    }

    // ═══════════════════════════════════
    // File Reading
    // ═══════════════════════════════════

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

    fun readAnyFile(file: File, context: Context): FileReadResult {
        val ext = file.extension.lowercase()
        return when {
            ext in listOf("zip", "jar") -> {
                val entries = extractZipForAi(file.absolutePath, context)
                FileReadResult(type = "zip", textContent = formatZipContents(entries), fileName = file.name)
            }
            ext in listOf("png", "jpg", "jpeg", "gif", "webp", "bmp") -> {
                FileReadResult(type = "image", imageBase64 = encodeImage(file), fileName = file.name)
            }
            ext in SKIP_EXTENSIONS -> {
                FileReadResult(type = "binary", textContent = "[Unsupported binary: ${ext.uppercase()}, ${file.length() / 1024}KB]", fileName = file.name)
            }
            else -> FileReadResult(type = "text", textContent = readFileForAi(file), fileName = file.name)
        }
    }
}

data class FileReadResult(
    val type: String,
    val textContent: String? = null,
    val imageBase64: String? = null,
    val fileName: String = ""
)
