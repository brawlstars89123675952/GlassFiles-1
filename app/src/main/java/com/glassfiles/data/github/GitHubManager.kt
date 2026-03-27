package com.glassfiles.data.github

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GitHubManager {

    private const val TAG = "GH"
    private const val API = "https://api.github.com"
    private const val PREFS = "github_prefs"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER = "user_json"

    // ═══════════════════════════════════
    // Auth
    // ═══════════════════════════════════

    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, "") ?: ""

    fun isLoggedIn(context: Context): Boolean = getToken(context).isNotBlank()

    fun logout(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    // ═══════════════════════════════════
    // HTTP helper
    // ═══════════════════════════════════

    private suspend fun request(context: Context, endpoint: String, method: String = "GET", body: String? = null): ApiResult =
        withContext(Dispatchers.IO) {
            try {
                val token = getToken(context)
                val url = if (endpoint.startsWith("http")) endpoint else "$API$endpoint"
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "GlassFiles")
                    if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
                    if (body != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        OutputStreamWriter(outputStream).use { it.write(body) }
                    }
                    connectTimeout = 15000
                    readTimeout = 15000
                }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()

                if (code in 200..299) ApiResult(true, text, code)
                else ApiResult(false, text, code)
            } catch (e: Exception) {
                Log.e(TAG, "Request error: ${e.message}")
                ApiResult(false, e.message ?: "Network error", -1)
            }
        }

    // ═══════════════════════════════════
    // User
    // ═══════════════════════════════════

    suspend fun getUser(context: Context): GHUser? {
        val r = request(context, "/user")
        if (!r.success) return null
        return try {
            val j = JSONObject(r.body)
            val user = GHUser(
                login = j.optString("login"),
                name = j.optString("name", ""),
                avatarUrl = j.optString("avatar_url", ""),
                bio = j.optString("bio", ""),
                publicRepos = j.optInt("public_repos", 0),
                privateRepos = j.optInt("total_private_repos", 0),
                followers = j.optInt("followers", 0),
                following = j.optInt("following", 0)
            )
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_USER, r.body).apply()
            user
        } catch (e: Exception) { Log.e(TAG, "Parse user: ${e.message}"); null }
    }

    fun getCachedUser(context: Context): GHUser? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_USER, null) ?: return null
        return try {
            val j = JSONObject(raw)
            GHUser(j.optString("login"), j.optString("name", ""), j.optString("avatar_url", ""),
                j.optString("bio", ""), j.optInt("public_repos", 0), j.optInt("total_private_repos", 0),
                j.optInt("followers", 0), j.optInt("following", 0))
        } catch (_: Exception) { null }
    }

    // ═══════════════════════════════════
    // Repos
    // ═══════════════════════════════════

    suspend fun getRepos(context: Context, page: Int = 1, perPage: Int = 30): List<GHRepo> {
        val r = request(context, "/user/repos?sort=updated&per_page=$perPage&page=$page&type=all")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { parseRepo(arr.getJSONObject(it)) }
        } catch (e: Exception) { Log.e(TAG, "Parse repos: ${e.message}"); emptyList() }
    }

    suspend fun searchRepos(context: Context, query: String): List<GHRepo> {
        val r = request(context, "/search/repositories?q=$query&sort=stars&per_page=20")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONObject(r.body).getJSONArray("items")
            (0 until arr.length()).map { parseRepo(arr.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun createRepo(context: Context, name: String, description: String, isPrivate: Boolean): Boolean {
        val body = JSONObject().apply {
            put("name", name); put("description", description); put("private", isPrivate); put("auto_init", true)
        }.toString()
        return request(context, "/user/repos", "POST", body).success
    }

    suspend fun deleteRepo(context: Context, owner: String, repo: String): Boolean =
        request(context, "/repos/$owner/$repo", "DELETE").success

    suspend fun getRepoContents(context: Context, owner: String, repo: String, path: String = ""): List<GHContent> {
        val r = request(context, "/repos/$owner/$repo/contents/$path")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHContent(j.optString("name"), j.optString("path"), j.optString("type"),
                    j.optLong("size", 0), j.optString("download_url", ""), j.optString("sha", ""))
            }.sortedWith(compareBy<GHContent> { it.type != "dir" }.thenBy { it.name.lowercase() })
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getFileContent(context: Context, owner: String, repo: String, path: String): String {
        val r = request(context, "/repos/$owner/$repo/contents/$path")
        if (!r.success) return ""
        return try {
            val j = JSONObject(r.body)
            val content = j.optString("content", "")
            String(android.util.Base64.decode(content.replace("\n", ""), android.util.Base64.DEFAULT))
        } catch (e: Exception) { "" }
    }

    // ═══════════════════════════════════
    // Commits
    // ═══════════════════════════════════

    suspend fun getCommits(context: Context, owner: String, repo: String, page: Int = 1): List<GHCommit> {
        val r = request(context, "/repos/$owner/$repo/commits?per_page=30&page=$page")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                val commit = j.getJSONObject("commit")
                val author = commit.optJSONObject("author")
                GHCommit(
                    sha = j.optString("sha").take(7),
                    message = commit.optString("message"),
                    author = author?.optString("name") ?: "?",
                    date = author?.optString("date") ?: "",
                    avatarUrl = j.optJSONObject("author")?.optString("avatar_url") ?: ""
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    // ═══════════════════════════════════
    // Issues
    // ═══════════════════════════════════

    suspend fun getIssues(context: Context, owner: String, repo: String, state: String = "open"): List<GHIssue> {
        val r = request(context, "/repos/$owner/$repo/issues?state=$state&per_page=30")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHIssue(j.optInt("number"), j.optString("title"), j.optString("state"),
                    j.optJSONObject("user")?.optString("login") ?: "", j.optString("created_at"),
                    j.optInt("comments", 0), j.has("pull_request"))
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun createIssue(context: Context, owner: String, repo: String, title: String, body: String): Boolean {
        val json = JSONObject().apply { put("title", title); put("body", body) }.toString()
        return request(context, "/repos/$owner/$repo/issues", "POST", json).success
    }

    // ═══════════════════════════════════
    // Branches
    // ═══════════════════════════════════

    suspend fun getBranches(context: Context, owner: String, repo: String): List<String> {
        val r = request(context, "/repos/$owner/$repo/branches?per_page=50")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { arr.getJSONObject(it).optString("name") }
        } catch (e: Exception) { emptyList() }
    }

    // ═══════════════════════════════════
    // Clone (download as zip)
    // ═══════════════════════════════════

    suspend fun cloneRepo(context: Context, owner: String, repo: String, destDir: java.io.File, onProgress: (String) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            try {
                onProgress("Downloading...")
                val zipUrl = "$API/repos/$owner/$repo/zipball"
                val token = getToken(context)
                val conn = (URL(zipUrl).openConnection() as HttpURLConnection).apply {
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    instanceFollowRedirects = true
                }
                val code = conn.responseCode
                if (code != 200) { conn.disconnect(); return@withContext false }

                val zipFile = java.io.File(destDir, "$repo.zip")
                destDir.mkdirs()
                conn.inputStream.use { input -> zipFile.outputStream().use { output -> input.copyTo(output) } }
                conn.disconnect()

                onProgress("Extracting...")
                // Extract zip
                val outDir = java.io.File(destDir, repo)
                outDir.mkdirs()
                val zip = java.util.zip.ZipInputStream(java.io.BufferedInputStream(java.io.FileInputStream(zipFile)))
                var entry = zip.nextEntry
                // GitHub zip has a root folder like "owner-repo-sha/"
                val rootPrefix = entry?.name?.substringBefore("/", "") ?: ""
                while (entry != null) {
                    val name = entry.name.removePrefix("$rootPrefix/")
                    if (name.isNotBlank()) {
                        val f = java.io.File(outDir, name)
                        if (entry.isDirectory) f.mkdirs()
                        else { f.parentFile?.mkdirs(); f.outputStream().use { zip.copyTo(it) } }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                zip.close()
                zipFile.delete()
                onProgress("Done")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Clone error: ${e.message}")
                onProgress("Error: ${e.message}")
                false
            }
        }

    // ═══════════════════════════════════
    // Helpers
    // ═══════════════════════════════════

    private fun parseRepo(j: JSONObject) = GHRepo(
        name = j.optString("name"),
        fullName = j.optString("full_name"),
        description = j.optString("description", ""),
        language = j.optString("language", ""),
        stars = j.optInt("stargazers_count", 0),
        forks = j.optInt("forks_count", 0),
        isPrivate = j.optBoolean("private", false),
        isFork = j.optBoolean("fork", false),
        defaultBranch = j.optString("default_branch", "main"),
        updatedAt = j.optString("updated_at", ""),
        owner = j.optJSONObject("owner")?.optString("login") ?: ""
    )

    data class ApiResult(val success: Boolean, val body: String, val code: Int)
}

// ═══════════════════════════════════
// Data models
// ═══════════════════════════════════

data class GHUser(val login: String, val name: String, val avatarUrl: String, val bio: String,
    val publicRepos: Int, val privateRepos: Int, val followers: Int, val following: Int)

data class GHRepo(val name: String, val fullName: String, val description: String, val language: String,
    val stars: Int, val forks: Int, val isPrivate: Boolean, val isFork: Boolean, val defaultBranch: String,
    val updatedAt: String, val owner: String)

data class GHCommit(val sha: String, val message: String, val author: String, val date: String, val avatarUrl: String)

data class GHIssue(val number: Int, val title: String, val state: String, val author: String,
    val createdAt: String, val comments: Int, val isPR: Boolean)

data class GHContent(val name: String, val path: String, val type: String, val size: Long,
    val downloadUrl: String, val sha: String)
