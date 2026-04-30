package com.glassfiles.data.ai.providers

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Shared HTTP helpers used by every provider. Keeps boilerplate (timeouts,
 * error parsing, SSE line iteration) in one place.
 *
 * No third-party HTTP client is added here on purpose — the rest of the app
 * uses raw [HttpURLConnection], so we follow the same convention.
 */
internal object Http {

    fun open(url: String, method: String = "GET", headers: Map<String, String> = emptyMap()): HttpURLConnection {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 120_000
            useCaches = false
        }
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        return conn
    }

    fun postJson(url: String, body: String, headers: Map<String, String>): HttpURLConnection {
        val conn = open(url, "POST", headers + ("Content-Type" to "application/json"))
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toByteArray()) }
        return conn
    }

    /** Reads the response and throws a friendly exception on non-2xx. */
    fun ensureOk(conn: HttpURLConnection, providerName: String) {
        val code = conn.responseCode
        if (code in 200..299) return
        val raw = (if (code >= 400) conn.errorStream else conn.inputStream)
            ?.bufferedReader()?.use { it.readText() }?.take(800)
            ?: "error $code"
        val detail = parseErrorMessage(raw)
        conn.disconnect()
        throw RuntimeException("$providerName HTTP $code: $detail")
    }

    /** Iterates an SSE-style stream, calling [onData] for each `data: ...` line (excluding `[DONE]`). */
    fun iterateSse(conn: HttpURLConnection, onData: (String) -> Unit) {
        BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (!l.startsWith("data:")) continue
                val data = l.removePrefix("data:").trim()
                if (data.isEmpty() || data == "[DONE]") continue
                onData(data)
            }
        }
    }

    /**
     * Returns the string at [key] or `""`. Unlike [JSONObject.optString]
     * which returns the literal `"null"` when the value is JSON `null`,
     * this helper treats both *missing* and *null* values as empty.
     *
     * This is the canonical way to read text fields out of provider
     * responses — OpenAI in particular emits `{"delta":{"content":null,…}}`
     * during tool-call streaming, and the raw [JSONObject.optString] would
     * leak the literal "null" into the rendered transcript.
     */
    fun JSONObject.optStringOrEmpty(key: String): String =
        if (isNull(key)) "" else optString(key, "")

    private fun parseErrorMessage(raw: String): String = try {
        val json = JSONObject(raw)
        when {
            json.has("error") -> {
                val err = json.opt("error")
                if (err is JSONObject) err.optString("message", raw.take(200)) else err.toString().take(200)
            }
            json.has("message") -> json.optString("message", raw.take(200))
            else -> raw.take(200)
        }
    } catch (_: Exception) {
        raw.take(200)
    }
}
