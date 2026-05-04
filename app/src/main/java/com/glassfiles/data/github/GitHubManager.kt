package com.glassfiles.data.github

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object GitHubManager {

    private const val TAG = "GH"
    private const val API = "https://api.github.com"
    private const val PREFS = "github_prefs"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER = "user_json"

    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, "") ?: ""

    fun isLoggedIn(context: Context): Boolean = getToken(context).isNotBlank()

    fun logout(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private suspend fun request(context: Context, endpoint: String, method: String = "GET", body: String? = null, extraHeaders: Map<String, String> = emptyMap()): ApiResult =
        withContext(Dispatchers.IO) {
            try {
                val token = getToken(context)
                val url = if (endpoint.startsWith("http")) endpoint else "$API$endpoint"
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "GlassFiles")
                    extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
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

    private suspend fun requestBasic(endpoint: String, method: String, body: String?, username: String, password: String): ApiResult =
        withContext(Dispatchers.IO) {
            try {
                val url = if (endpoint.startsWith("http")) endpoint else "$API$endpoint"
                val auth = android.util.Base64.encodeToString("$username:$password".toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "GlassFiles")
                    setRequestProperty("Authorization", "Basic $auth")
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
                if (code in 200..299) ApiResult(true, text, code) else ApiResult(false, text, code)
            } catch (e: Exception) {
                Log.e(TAG, "Basic request error: ${e.message}")
                ApiResult(false, e.message ?: "Network error", -1)
            }
        }

    private suspend fun graphql(context: Context, query: String, variables: JSONObject = JSONObject()): JSONObject? {
        val body = JSONObject().apply {
            put("query", query)
            put("variables", variables)
        }.toString()
        val r = request(context, "/graphql", "POST", body, extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return null
        return try {
            val root = JSONObject(r.body)
            if (root.has("errors")) null else root.optJSONObject("data")
        } catch (e: Exception) {
            null
        }
    }

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

    suspend fun getRepos(context: Context, page: Int = 1, perPage: Int = 30): List<GHRepo> {
        val r = request(context, "/user/repos?sort=updated&per_page=$perPage&page=$page&type=all")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { parseRepo(arr.getJSONObject(it)) }
        } catch (e: Exception) { Log.e(TAG, "Parse repos: ${e.message}"); emptyList() }
    }

    suspend fun getRepo(context: Context, owner: String, repo: String): GHRepo? {
        val r = request(context, "/repos/$owner/$repo")
        if (!r.success) return null
        return try {
            parseRepo(JSONObject(r.body))
        } catch (e: Exception) {
            Log.e(TAG, "Parse repo: ${e.message}")
            null
        }
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

    private fun refQuery(branch: String?): String {
        val ref = branch?.takeIf { it.isNotBlank() } ?: return ""
        return "?ref=${URLEncoder.encode(ref, "UTF-8")}"
    }

    suspend fun getRepoContents(context: Context, owner: String, repo: String, path: String = "", branch: String? = null): List<GHContent> {
        val r = request(context, "/repos/$owner/$repo/contents/$path${refQuery(branch)}")
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

    suspend fun getFileContent(context: Context, owner: String, repo: String, path: String, branch: String? = null): String {
        val r = request(context, "/repos/$owner/$repo/contents/$path${refQuery(branch)}")
        if (!r.success) return ""
        return try {
            val j = JSONObject(r.body)
            val content = j.optString("content", "")
            String(android.util.Base64.decode(content.replace("\n", ""), android.util.Base64.DEFAULT))
        } catch (e: Exception) { "" }
    }

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

    suspend fun getIssues(context: Context, owner: String, repo: String, state: String = "open", page: Int = 1): List<GHIssue> {
        val r = request(context, "/repos/$owner/$repo/issues?state=$state&per_page=30&page=$page")
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

    suspend fun getBranches(context: Context, owner: String, repo: String): List<String> {
        val branches = mutableListOf<String>()
        var page = 1
        while (true) {
            val r = request(context, "/repos/$owner/$repo/branches?per_page=100&page=$page")
            if (!r.success) break
            val count = try {
                val arr = JSONArray(r.body)
                for (i in 0 until arr.length()) {
                    arr.getJSONObject(i).optString("name").takeIf { it.isNotBlank() }?.let { branches += it }
                }
                arr.length()
            } catch (e: Exception) {
                0
            }
            if (count < 100) break
            page++
        }
        return branches.distinct()
    }

    suspend fun getBranchHeadSha(context: Context, owner: String, repo: String, branch: String): String? {
        if (branch.isBlank()) return null
        val r = request(context, "/repos/$owner/$repo/git/ref/heads/$branch")
        if (!r.success) return null
        return try {
            JSONObject(r.body).optJSONObject("object")?.optString("sha")?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

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
                val outDir = java.io.File(destDir, repo)
                outDir.mkdirs()
                val zip = java.util.zip.ZipInputStream(java.io.BufferedInputStream(java.io.FileInputStream(zipFile)))
                var entry = zip.nextEntry
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

    suspend fun uploadFile(
        context: Context, owner: String, repo: String, path: String,
        content: ByteArray, message: String, branch: String? = null, sha: String? = null
    ): Boolean {
        val b64 = android.util.Base64.encodeToString(content, android.util.Base64.NO_WRAP)
        val body = JSONObject().apply {
            put("message", message)
            put("content", b64)
            if (!sha.isNullOrBlank()) put("sha", sha)
            if (branch != null) put("branch", branch)
        }.toString()
        return request(context, "/repos/$owner/$repo/contents/$path", "PUT", body).success
    }

    suspend fun uploadFileWithResult(
        context: Context, owner: String, repo: String, path: String,
        content: ByteArray, message: String, branch: String? = null, sha: String? = null
    ): GHFileSaveResult {
        val b64 = android.util.Base64.encodeToString(content, android.util.Base64.NO_WRAP)
        val body = JSONObject().apply {
            put("message", message)
            put("content", b64)
            if (!sha.isNullOrBlank()) put("sha", sha)
            if (branch != null) put("branch", branch)
        }.toString()
        val r = request(context, "/repos/$owner/$repo/contents/$path", "PUT", body)
        if (!r.success) return GHFileSaveResult(false, "", r.body)
        val newSha = try {
            JSONObject(r.body).optJSONObject("content")?.optString("sha").orEmpty()
        } catch (_: Exception) { "" }
        return GHFileSaveResult(true, newSha, "")
    }

    suspend fun uploadFileFromPath(
        context: Context, owner: String, repo: String, repoPath: String,
        localPath: String, message: String, branch: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = java.io.File(localPath)
            if (!file.exists()) return@withContext false
            val bytes = file.readBytes()
            uploadFile(context, owner, repo, repoPath, bytes, message, branch)
        } catch (e: Exception) {
            Log.e(TAG, "Upload from path: ${e.message}")
            false
        }
    }

    suspend fun uploadMultipleFiles(
        context: Context, owner: String, repo: String, branch: String,
        files: List<Pair<String, ByteArray>>, message: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val refR = request(context, "/repos/$owner/$repo/git/ref/heads/$branch")
            if (!refR.success) return@withContext false
            val latestSha = JSONObject(refR.body).getJSONObject("object").getString("sha")

            val commitR = request(context, "/repos/$owner/$repo/git/commits/$latestSha")
            if (!commitR.success) return@withContext false
            val baseTree = JSONObject(commitR.body).getJSONObject("tree").getString("sha")

            val treeItems = JSONArray()
            files.forEachIndexed { index, (path, content) ->
                onProgress(index + 1, files.size)
                val b64 = android.util.Base64.encodeToString(content, android.util.Base64.NO_WRAP)
                val blobBody = JSONObject().apply { put("content", b64); put("encoding", "base64") }.toString()
                val blobR = request(context, "/repos/$owner/$repo/git/blobs", "POST", blobBody)
                if (!blobR.success) return@withContext false
                val blobSha = JSONObject(blobR.body).getString("sha")
                treeItems.put(JSONObject().apply {
                    put("path", path); put("mode", "100644"); put("type", "blob"); put("sha", blobSha)
                })
            }

            val treeBody = JSONObject().apply { put("base_tree", baseTree); put("tree", treeItems) }.toString()
            val treeR = request(context, "/repos/$owner/$repo/git/trees", "POST", treeBody)
            if (!treeR.success) return@withContext false
            val newTree = JSONObject(treeR.body).getString("sha")

            val commitBody = JSONObject().apply {
                put("message", message); put("tree", newTree)
                put("parents", JSONArray().put(latestSha))
            }.toString()
            val newCommitR = request(context, "/repos/$owner/$repo/git/commits", "POST", commitBody)
            if (!newCommitR.success) return@withContext false
            val newCommitSha = JSONObject(newCommitR.body).getString("sha")

            val refBody = JSONObject().apply { put("sha", newCommitSha) }.toString()
            request(context, "/repos/$owner/$repo/git/refs/heads/$branch", "PATCH", refBody).success
        } catch (e: Exception) {
            Log.e(TAG, "Multi upload: ${e.message}")
            false
        }
    }

    suspend fun commitWorkspaceChanges(
        context: Context,
        owner: String,
        repo: String,
        branch: String,
        changes: List<Pair<String, ByteArray?>>,
        message: String,
    ): String = withContext(Dispatchers.IO) {
        try {
            if (changes.isEmpty()) return@withContext ""
            val refR = request(context, "/repos/$owner/$repo/git/ref/heads/$branch")
            if (!refR.success) return@withContext ""
            val latestSha = JSONObject(refR.body).getJSONObject("object").getString("sha")

            val commitR = request(context, "/repos/$owner/$repo/git/commits/$latestSha")
            if (!commitR.success) return@withContext ""
            val baseTree = JSONObject(commitR.body).getJSONObject("tree").getString("sha")

            val treeItems = JSONArray()
            changes.forEach { (path, content) ->
                val item = JSONObject().apply {
                    put("path", path)
                    put("mode", "100644")
                    put("type", "blob")
                }
                if (content == null) {
                    item.put("sha", JSONObject.NULL)
                } else {
                    val b64 = android.util.Base64.encodeToString(content, android.util.Base64.NO_WRAP)
                    val blobBody = JSONObject().apply {
                        put("content", b64)
                        put("encoding", "base64")
                    }.toString()
                    val blobR = request(context, "/repos/$owner/$repo/git/blobs", "POST", blobBody)
                    if (!blobR.success) return@withContext ""
                    item.put("sha", JSONObject(blobR.body).getString("sha"))
                }
                treeItems.put(item)
            }

            val treeBody = JSONObject().apply {
                put("base_tree", baseTree)
                put("tree", treeItems)
            }.toString()
            val treeR = request(context, "/repos/$owner/$repo/git/trees", "POST", treeBody)
            if (!treeR.success) return@withContext ""
            val newTree = JSONObject(treeR.body).getString("sha")

            val commitBody = JSONObject().apply {
                put("message", message)
                put("tree", newTree)
                put("parents", JSONArray().put(latestSha))
            }.toString()
            val newCommitR = request(context, "/repos/$owner/$repo/git/commits", "POST", commitBody)
            if (!newCommitR.success) return@withContext ""
            val newCommitSha = JSONObject(newCommitR.body).getString("sha")

            val refBody = JSONObject().apply { put("sha", newCommitSha) }.toString()
            val refUpdate = request(context, "/repos/$owner/$repo/git/refs/heads/$branch", "PATCH", refBody)
            if (refUpdate.success) newCommitSha else ""
        } catch (e: Exception) {
            Log.e(TAG, "Workspace commit: ${e.message}")
            ""
        }
    }

    suspend fun deleteFile(
        context: Context, owner: String, repo: String, path: String,
        sha: String, message: String, branch: String? = null
    ): Boolean {
        val body = JSONObject().apply {
            put("message", message); put("sha", sha)
            if (branch != null) put("branch", branch)
        }.toString()
        return request(context, "/repos/$owner/$repo/contents/$path", "DELETE", body).success
    }

    suspend fun downloadFile(context: Context, owner: String, repo: String, path: String, destFile: java.io.File, branch: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val r = request(context, "/repos/$owner/$repo/contents/$path${refQuery(branch)}")
                if (!r.success) return@withContext false
                val j = JSONObject(r.body)
                val downloadUrl = j.optString("download_url", "")
                if (downloadUrl.isBlank()) return@withContext false

                val token = getToken(context)
                val conn = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                    if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
                    connectTimeout = 15000; readTimeout = 30000
                }
                destFile.parentFile?.mkdirs()
                conn.inputStream.use { input -> destFile.outputStream().use { out -> input.copyTo(out) } }
                conn.disconnect()
                true
            } catch (e: Exception) { Log.e(TAG, "Download: ${e.message}"); false }
        }

    suspend fun createBranch(context: Context, owner: String, repo: String, branchName: String, fromBranch: String): Boolean {
        val refR = request(context, "/repos/$owner/$repo/git/ref/heads/$fromBranch")
        if (!refR.success) return false
        val sha = JSONObject(refR.body).getJSONObject("object").getString("sha")
        val body = JSONObject().apply { put("ref", "refs/heads/$branchName"); put("sha", sha) }.toString()
        return request(context, "/repos/$owner/$repo/git/refs", "POST", body).success
    }

    suspend fun deleteBranch(context: Context, owner: String, repo: String, branch: String): Boolean =
        request(context, "/repos/$owner/$repo/git/refs/heads/$branch", "DELETE").success

    suspend fun getGitRef(context: Context, owner: String, repo: String, ref: String): GHGitRef? {
        val cleanRef = ref.trim().removePrefix("refs/").trim('/')
        if (cleanRef.isBlank()) return null
        val r = request(context, "/repos/$owner/$repo/git/ref/$cleanRef")
        if (!r.success) return null
        return try { parseGitRef(JSONObject(r.body)) } catch (e: Exception) { null }
    }

    suspend fun getMatchingGitRefs(context: Context, owner: String, repo: String, refPrefix: String): List<GHGitRef> {
        val cleanRef = refPrefix.trim().removePrefix("refs/").trim('/')
        if (cleanRef.isBlank()) return emptyList()
        val r = request(context, "/repos/$owner/$repo/git/matching-refs/$cleanRef")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i -> parseGitRef(arr.getJSONObject(i)) }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getGitTree(context: Context, owner: String, repo: String, treeSha: String, recursive: Boolean = true): GHGitTree? {
        val cleanSha = treeSha.trim()
        if (cleanSha.isBlank()) return null
        val recursiveFlag = if (recursive) "?recursive=1" else ""
        val r = request(context, "/repos/$owner/$repo/git/trees/$cleanSha$recursiveFlag")
        if (!r.success) return null
        return try {
            val j = JSONObject(r.body)
            val tree = j.optJSONArray("tree") ?: JSONArray()
            GHGitTree(
                sha = j.optString("sha", ""),
                truncated = j.optBoolean("truncated", false),
                items = (0 until tree.length()).map { i ->
                    val item = tree.getJSONObject(i)
                    GHGitTreeItem(
                        path = item.optString("path", ""),
                        mode = item.optString("mode", ""),
                        type = item.optString("type", ""),
                        sha = item.optString("sha", ""),
                        size = item.optLong("size", 0L),
                        url = item.optString("url", "")
                    )
                }
            )
        } catch (e: Exception) { null }
    }

    suspend fun getGitBlob(context: Context, owner: String, repo: String, fileSha: String): GHGitBlob? {
        val cleanSha = fileSha.trim()
        if (cleanSha.isBlank()) return null
        val r = request(context, "/repos/$owner/$repo/git/blobs/$cleanSha")
        if (!r.success) return null
        return try {
            val j = JSONObject(r.body)
            GHGitBlob(
                sha = j.optString("sha", ""),
                size = j.optLong("size", 0L),
                encoding = j.optString("encoding", ""),
                content = j.optString("content", ""),
                url = j.optString("url", "")
            )
        } catch (e: Exception) { null }
    }

    suspend fun getGitTag(context: Context, owner: String, repo: String, tagSha: String): GHGitTagDetail? {
        val cleanSha = tagSha.trim()
        if (cleanSha.isBlank()) return null
        val r = request(context, "/repos/$owner/$repo/git/tags/$cleanSha")
        if (!r.success) return null
        return try {
            val j = JSONObject(r.body)
            val tagger = j.optJSONObject("tagger")
            val obj = j.optJSONObject("object")
            GHGitTagDetail(
                sha = j.optString("sha", ""),
                tag = j.optString("tag", ""),
                message = j.optString("message", ""),
                taggerName = tagger?.optString("name", "") ?: "",
                taggerEmail = tagger?.optString("email", "") ?: "",
                date = tagger?.optString("date", "") ?: "",
                objectSha = obj?.optString("sha", "") ?: "",
                objectType = obj?.optString("type", "") ?: ""
            )
        } catch (e: Exception) { null }
    }

    suspend fun getGitCommit(context: Context, owner: String, repo: String, commitSha: String): GHGitCommit? {
        val cleanSha = commitSha.trim()
        if (cleanSha.isBlank()) return null
        val r = request(context, "/repos/$owner/$repo/git/commits/$cleanSha")
        if (!r.success) return null
        return try {
            val j = JSONObject(r.body)
            val author = j.optJSONObject("author")
            val committer = j.optJSONObject("committer")
            val parents = j.optJSONArray("parents") ?: JSONArray()
            GHGitCommit(
                sha = j.optString("sha", ""),
                message = j.optString("message", ""),
                treeSha = j.optJSONObject("tree")?.optString("sha", "") ?: "",
                parentShas = (0 until parents.length()).mapNotNull { i -> parents.optJSONObject(i)?.optString("sha")?.takeIf { it.isNotBlank() } },
                authorName = author?.optString("name", "") ?: "",
                authorEmail = author?.optString("email", "") ?: "",
                authorDate = author?.optString("date", "") ?: "",
                committerName = committer?.optString("name", "") ?: "",
                committerEmail = committer?.optString("email", "") ?: "",
                committerDate = committer?.optString("date", "") ?: ""
            )
        } catch (e: Exception) { null }
    }

    suspend fun createGitBlob(context: Context, owner: String, repo: String, content: String, encoding: String = "utf-8"): GHGitBlob? {
        if (content.isBlank()) return null
        val cleanEncoding = if (encoding.equals("base64", ignoreCase = true)) "base64" else "utf-8"
        val body = JSONObject().apply {
            put("content", content)
            put("encoding", cleanEncoding)
        }.toString()
        val r = request(context, "/repos/$owner/$repo/git/blobs", "POST", body)
        if (!r.success) return null
        return try {
            val j = JSONObject(r.body)
            GHGitBlob(
                sha = j.optString("sha", ""),
                size = content.length.toLong(),
                encoding = cleanEncoding,
                content = content,
                url = j.optString("url", "")
            )
        } catch (e: Exception) { null }
    }

    suspend fun createGitTree(
        context: Context,
        owner: String,
        repo: String,
        baseTree: String,
        path: String,
        mode: String,
        type: String,
        sha: String,
        content: String
    ): GHGitTree? {
        val cleanPath = path.trim()
        if (cleanPath.isBlank()) return null
        if (sha.isBlank() && content.isBlank()) return null
        val item = JSONObject().apply {
            put("path", cleanPath)
            put("mode", mode.ifBlank { "100644" })
            put("type", type.ifBlank { "blob" })
            if (content.isNotBlank()) put("content", content) else put("sha", sha.trim())
        }
        val body = JSONObject().apply {
            if (baseTree.isNotBlank()) put("base_tree", baseTree.trim())
            put("tree", JSONArray().put(item))
        }.toString()
        val r = request(context, "/repos/$owner/$repo/git/trees", "POST", body)
        if (!r.success) return null
        return try {
            val j = JSONObject(r.body)
            val tree = j.optJSONArray("tree") ?: JSONArray()
            GHGitTree(
                sha = j.optString("sha", ""),
                truncated = j.optBoolean("truncated", false),
                items = (0 until tree.length()).map { i ->
                    val treeItem = tree.getJSONObject(i)
                    GHGitTreeItem(
                        path = treeItem.optString("path", ""),
                        mode = treeItem.optString("mode", ""),
                        type = treeItem.optString("type", ""),
                        sha = treeItem.optString("sha", ""),
                        size = treeItem.optLong("size", 0L),
                        url = treeItem.optString("url", "")
                    )
                }
            )
        } catch (e: Exception) { null }
    }

    suspend fun createGitTag(
        context: Context,
        owner: String,
        repo: String,
        tag: String,
        message: String,
        objectSha: String,
        objectType: String = "commit",
        taggerName: String = "",
        taggerEmail: String = ""
    ): GHGitTagDetail? {
        if (tag.isBlank() || message.isBlank() || objectSha.isBlank()) return null
        val body = JSONObject().apply {
            put("tag", tag.trim())
            put("message", message)
            put("object", objectSha.trim())
            put("type", objectType.ifBlank { "commit" })
            if (taggerName.isNotBlank() && taggerEmail.isNotBlank()) {
                put("tagger", JSONObject().apply {
                    put("name", taggerName.trim())
                    put("email", taggerEmail.trim())
                })
            }
        }.toString()
        val r = request(context, "/repos/$owner/$repo/git/tags", "POST", body)
        if (!r.success) return null
        return try {
            val j = JSONObject(r.body)
            val tagger = j.optJSONObject("tagger")
            val obj = j.optJSONObject("object")
            GHGitTagDetail(
                sha = j.optString("sha", ""),
                tag = j.optString("tag", ""),
                message = j.optString("message", ""),
                taggerName = tagger?.optString("name", "") ?: "",
                taggerEmail = tagger?.optString("email", "") ?: "",
                date = tagger?.optString("date", "") ?: "",
                objectSha = obj?.optString("sha", "") ?: "",
                objectType = obj?.optString("type", "") ?: ""
            )
        } catch (e: Exception) { null }
    }

    suspend fun createGitCommit(
        context: Context,
        owner: String,
        repo: String,
        message: String,
        treeSha: String,
        parentShas: List<String>
    ): GHGitCommit? {
        if (message.isBlank() || treeSha.isBlank()) return null
        val body = JSONObject().apply {
            put("message", message)
            put("tree", treeSha.trim())
            put("parents", JSONArray().apply { parentShas.filter { it.isNotBlank() }.forEach { put(it.trim()) } })
        }.toString()
        val r = request(context, "/repos/$owner/$repo/git/commits", "POST", body)
        if (!r.success) return null
        return try {
            val j = JSONObject(r.body)
            val author = j.optJSONObject("author")
            val committer = j.optJSONObject("committer")
            val parents = j.optJSONArray("parents") ?: JSONArray()
            GHGitCommit(
                sha = j.optString("sha", ""),
                message = j.optString("message", ""),
                treeSha = j.optJSONObject("tree")?.optString("sha", "") ?: "",
                parentShas = (0 until parents.length()).mapNotNull { i -> parents.optJSONObject(i)?.optString("sha")?.takeIf { it.isNotBlank() } },
                authorName = author?.optString("name", "") ?: "",
                authorEmail = author?.optString("email", "") ?: "",
                authorDate = author?.optString("date", "") ?: "",
                committerName = committer?.optString("name", "") ?: "",
                committerEmail = committer?.optString("email", "") ?: "",
                committerDate = committer?.optString("date", "") ?: ""
            )
        } catch (e: Exception) { null }
    }

    suspend fun updateGitRef(context: Context, owner: String, repo: String, ref: String, sha: String, force: Boolean = false): GHGitRef? {
        val cleanRef = ref.trim().removePrefix("refs/").trim('/')
        if (cleanRef.isBlank() || sha.isBlank()) return null
        val body = JSONObject().apply {
            put("sha", sha.trim())
            put("force", force)
        }.toString()
        val r = request(context, "/repos/$owner/$repo/git/refs/$cleanRef", "PATCH", body)
        if (!r.success) return null
        return try { parseGitRef(JSONObject(r.body)) } catch (e: Exception) { null }
    }

    suspend fun createGitRef(context: Context, owner: String, repo: String, ref: String, sha: String): GHGitRef? {
        val cleanRef = ref.trim().removePrefix("refs/").trim('/')
        if (cleanRef.isBlank() || sha.isBlank()) return null
        val body = JSONObject().apply {
            put("ref", "refs/$cleanRef")
            put("sha", sha.trim())
        }.toString()
        val r = request(context, "/repos/$owner/$repo/git/refs", "POST", body)
        if (!r.success) return null
        return try { parseGitRef(JSONObject(r.body)) } catch (e: Exception) { null }
    }

    suspend fun deleteGitRef(context: Context, owner: String, repo: String, ref: String): Boolean {
        val cleanRef = ref.trim().removePrefix("refs/").trim('/')
        if (cleanRef.isBlank()) return false
        return request(context, "/repos/$owner/$repo/git/refs/$cleanRef", "DELETE").success
    }

    suspend fun getPullRequests(context: Context, owner: String, repo: String, state: String = "open", page: Int = 1): List<GHPullRequest> {
        val r = request(context, "/repos/$owner/$repo/pulls?state=$state&per_page=30&page=$page")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHPullRequest(
                    number = j.optInt("number"), title = j.optString("title"),
                    state = j.optString("state"), author = j.optJSONObject("user")?.optString("login") ?: "",
                    createdAt = j.optString("created_at"),
                    head = j.optJSONObject("head")?.optString("ref") ?: "",
                    base = j.optJSONObject("base")?.optString("ref") ?: "",
                    comments = j.optInt("comments", 0), merged = j.optBoolean("merged", false),
                    body = j.optString("body", ""),
                    draft = j.optBoolean("draft", false),
                    htmlUrl = j.optString("html_url", ""),
                    headSha = j.optJSONObject("head")?.optString("sha") ?: "",
                    reviewComments = j.optInt("review_comments", 0),
                    requestedReviewers = parseUsers(j.optJSONArray("requested_reviewers"))
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getPullRequestDetail(context: Context, owner: String, repo: String, number: Int): GHPullRequest? {
        val r = request(context, "/repos/$owner/$repo/pulls/$number")
        if (!r.success) return null
        return try {
            val j = JSONObject(r.body)
            GHPullRequest(
                number = j.optInt("number"),
                title = j.optString("title"),
                state = j.optString("state"),
                author = j.optJSONObject("user")?.optString("login") ?: "",
                createdAt = j.optString("created_at"),
                head = j.optJSONObject("head")?.optString("ref") ?: "",
                base = j.optJSONObject("base")?.optString("ref") ?: "",
                comments = j.optInt("comments", 0),
                merged = j.optBoolean("merged", false),
                body = j.optString("body", ""),
                draft = j.optBoolean("draft", false),
                htmlUrl = j.optString("html_url", ""),
                headSha = j.optJSONObject("head")?.optString("sha") ?: "",
                mergeable = if (j.isNull("mergeable")) null else j.optBoolean("mergeable"),
                mergeableState = j.optString("mergeable_state", ""),
                reviewComments = j.optInt("review_comments", 0),
                commits = j.optInt("commits", 0),
                additions = j.optInt("additions", 0),
                deletions = j.optInt("deletions", 0),
                changedFiles = j.optInt("changed_files", 0),
                requestedReviewers = parseUsers(j.optJSONArray("requested_reviewers"))
            )
        } catch (e: Exception) { null }
    }

    suspend fun createPullRequest(
        context: Context, owner: String, repo: String,
        title: String, body: String, head: String, base: String
    ): Boolean {
        val json = JSONObject().apply {
            put("title", title); put("body", body); put("head", head); put("base", base)
        }.toString()
        return request(context, "/repos/$owner/$repo/pulls", "POST", json).success
    }

    suspend fun updatePullRequest(context: Context, owner: String, repo: String, number: Int, title: String? = null, body: String? = null, base: String? = null, state: String? = null): Boolean {
        val json = JSONObject().apply {
            title?.let { put("title", it) }
            body?.let { put("body", it) }
            base?.takeIf { it.isNotBlank() }?.let { put("base", it) }
            state?.takeIf { it in listOf("open", "closed") }?.let { put("state", it) }
        }.toString()
        return request(context, "/repos/$owner/$repo/pulls/$number", "PATCH", json).success
    }

    suspend fun mergePullRequest(context: Context, owner: String, repo: String, number: Int, message: String = "", method: String = "merge", title: String = ""): Boolean {
        val body = JSONObject().apply {
            if (title.isNotBlank()) put("commit_title", title)
            if (message.isNotBlank()) put("commit_message", message)
            put("merge_method", method)
        }.toString()
        return request(context, "/repos/$owner/$repo/pulls/$number/merge", "PUT", body).success
    }

    suspend fun getPullRequestMergedStatus(context: Context, owner: String, repo: String, number: Int): GHPullMergeStatus {
        val r = request(context, "/repos/$owner/$repo/pulls/$number/merge")
        return when (r.code) {
            204 -> GHPullMergeStatus(merged = true, checked = true, code = r.code, message = "merged")
            404 -> GHPullMergeStatus(merged = false, checked = true, code = r.code, message = "not merged")
            else -> GHPullMergeStatus(merged = false, checked = false, code = r.code, message = r.body.take(180))
        }
    }

    suspend fun getPullRequestReviews(context: Context, owner: String, repo: String, number: Int): List<GHPullReview> {
        val r = request(context, "/repos/$owner/$repo/pulls/$number/reviews?per_page=100")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                parsePullReview(arr.getJSONObject(i))
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getPullRequestReview(context: Context, owner: String, repo: String, number: Int, reviewId: Long): GHPullReview? {
        val r = request(context, "/repos/$owner/$repo/pulls/$number/reviews/$reviewId")
        if (!r.success) return null
        return try { parsePullReview(JSONObject(r.body)) } catch (e: Exception) { null }
    }

    suspend fun updatePullRequestReview(context: Context, owner: String, repo: String, number: Int, reviewId: Long, body: String): GHPullReview? {
        val json = JSONObject().apply { put("body", body) }.toString()
        val r = request(context, "/repos/$owner/$repo/pulls/$number/reviews/$reviewId", "PUT", json)
        if (!r.success) return null
        return try { parsePullReview(JSONObject(r.body)) } catch (e: Exception) { null }
    }

    suspend fun deletePullRequestReview(context: Context, owner: String, repo: String, number: Int, reviewId: Long): Boolean =
        request(context, "/repos/$owner/$repo/pulls/$number/reviews/$reviewId", "DELETE").let { it.code == 200 || it.code == 204 || it.success }

    suspend fun requestPullRequestReviewers(context: Context, owner: String, repo: String, number: Int, reviewers: List<String>): Boolean {
        val clean = reviewers.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (clean.isEmpty()) return false
        val body = JSONObject().apply { put("reviewers", JSONArray(clean)) }.toString()
        return request(context, "/repos/$owner/$repo/pulls/$number/requested_reviewers", "POST", body).success
    }

    suspend fun removePullRequestReviewers(context: Context, owner: String, repo: String, number: Int, reviewers: List<String>): Boolean {
        val clean = reviewers.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (clean.isEmpty()) return false
        val body = JSONObject().apply { put("reviewers", JSONArray(clean)) }.toString()
        return request(context, "/repos/$owner/$repo/pulls/$number/requested_reviewers", "DELETE", body).success
    }

    private fun parseUsers(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.optString("login")?.takeIf { it.isNotBlank() && it != "null" }
        }
    }

    private fun parseWorkflow(j: JSONObject): GHWorkflow =
        GHWorkflow(
            id = j.optLong("id"),
            name = j.optString("name"),
            state = j.optString("state"),
            path = j.optString("path"),
            htmlUrl = j.optString("html_url", ""),
            badgeUrl = j.optString("badge_url", ""),
            createdAt = j.optString("created_at", ""),
            updatedAt = j.optString("updated_at", "")
        )

    private fun parseGitRef(j: JSONObject): GHGitRef {
        val obj = j.optJSONObject("object")
        return GHGitRef(
            ref = j.optString("ref", ""),
            nodeSha = obj?.optString("sha", "") ?: "",
            nodeType = obj?.optString("type", "") ?: "",
            url = j.optString("url", "")
        )
    }

    private fun parseIssueEvent(j: JSONObject, fallbackIssueNumber: Int = 0): GHIssueEvent {
        val issue = j.optJSONObject("issue")
        val rename = j.optJSONObject("rename")
        val app = j.optJSONObject("performed_via_github_app")
        return GHIssueEvent(
            id = j.optLong("id"),
            event = j.optString("event", ""),
            actor = j.optJSONObject("actor")?.optString("login") ?: "",
            createdAt = j.optString("created_at", ""),
            issueNumber = issue?.optInt("number", fallbackIssueNumber) ?: fallbackIssueNumber,
            issueTitle = issue?.optString("title", "") ?: "",
            label = j.optJSONObject("label")?.optString("name") ?: "",
            assignee = j.optJSONObject("assignee")?.optString("login") ?: "",
            milestone = j.optJSONObject("milestone")?.optString("title") ?: "",
            renameFrom = rename?.optString("from", "") ?: "",
            renameTo = rename?.optString("to", "") ?: "",
            commitId = j.optString("commit_id", ""),
            url = j.optString("url", ""),
            commitUrl = j.optString("commit_url", ""),
            authorAssociation = issue?.optString("author_association", "") ?: "",
            stateReason = issue?.optString("state_reason", "") ?: "",
            performedViaGithubApp = app?.optString("name", "") ?: ""
        )
    }

    private fun parseActionRunner(j: JSONObject): GHActionRunner {
        val labels = j.optJSONArray("labels")?.let { labelArr ->
            (0 until labelArr.length()).mapNotNull { idx -> labelArr.optJSONObject(idx)?.optString("name") }
        } ?: emptyList()
        return GHActionRunner(
            id = j.optLong("id"),
            name = j.optString("name"),
            os = j.optString("os"),
            status = j.optString("status"),
            busy = j.optBoolean("busy", false),
            labels = labels
        )
    }

    private fun parseOAuthTokenInfo(j: JSONObject): GHOAuthTokenInfo {
        val app = j.optJSONObject("app")
        val scopes = j.optJSONArray("scopes")?.let { arr ->
            (0 until arr.length()).mapNotNull { idx -> arr.optString(idx).takeIf { it.isNotBlank() } }
        } ?: emptyList()
        return GHOAuthTokenInfo(
            id = j.optLong("id"),
            url = j.optString("url", ""),
            appName = app?.optString("name", "") ?: "",
            appUrl = app?.optString("url", "") ?: "",
            clientId = app?.optString("client_id", "") ?: "",
            tokenLastEight = j.optString("token_last_eight", ""),
            note = j.optString("note", ""),
            noteUrl = j.optString("note_url", ""),
            createdAt = j.optString("created_at", ""),
            updatedAt = j.optString("updated_at", ""),
            scopes = scopes,
            fingerprint = j.optString("fingerprint", ""),
            token = j.optString("token", "")
        )
    }

    private fun parseTrafficSeries(j: JSONObject, itemKey: String): GHTrafficSeries {
        val items = j.optJSONArray(itemKey) ?: JSONArray()
        return GHTrafficSeries(
            count = j.optInt("count", 0),
            uniques = j.optInt("uniques", 0),
            items = (0 until items.length()).map { i ->
                val item = items.getJSONObject(i)
                GHTrafficPoint(
                    timestamp = item.optString("timestamp", ""),
                    count = item.optInt("count", 0),
                    uniques = item.optInt("uniques", 0)
                )
            }
        )
    }

    private fun parseRepoPerson(j: JSONObject, starred: Boolean): GHRepoPerson? {
        val user = if (starred && j.has("user")) j.optJSONObject("user") else j
        val login = user?.optString("login", "").orEmpty()
        if (login.isBlank()) return null
        return GHRepoPerson(
            login = login,
            avatarUrl = user?.optString("avatar_url", "").orEmpty(),
            htmlUrl = user?.optString("html_url", "").orEmpty(),
            starredAt = if (starred) j.optString("starred_at", "") else ""
        )
    }

    private fun parsePullReview(j: JSONObject): GHPullReview =
        GHPullReview(
            id = j.optLong("id"),
            user = j.optJSONObject("user")?.optString("login") ?: "",
            state = j.optString("state", ""),
            body = j.optString("body", ""),
            submittedAt = j.optString("submitted_at", ""),
            commitId = j.optString("commit_id", ""),
            htmlUrl = j.optString("html_url", "")
        )

    suspend fun getIssueComments(context: Context, owner: String, repo: String, number: Int): List<GHComment> {
        val r = request(context, "/repos/$owner/$repo/issues/$number/comments?per_page=50")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHComment(
                    id = j.optLong("id"), body = j.optString("body"),
                    author = j.optJSONObject("user")?.optString("login") ?: "",
                    avatarUrl = j.optJSONObject("user")?.optString("avatar_url") ?: "",
                    createdAt = j.optString("created_at")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getIssueEvents(context: Context, owner: String, repo: String, page: Int = 1): List<GHIssueEvent> {
        val r = request(context, "/repos/$owner/$repo/issues/events?per_page=100&page=$page")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i -> parseIssueEvent(arr.getJSONObject(i)) }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getIssueEventsForIssue(context: Context, owner: String, repo: String, issueNumber: Int, page: Int = 1): List<GHIssueEvent> {
        val r = request(context, "/repos/$owner/$repo/issues/$issueNumber/events?per_page=100&page=$page")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i -> parseIssueEvent(arr.getJSONObject(i), fallbackIssueNumber = issueNumber) }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getIssueEvent(context: Context, owner: String, repo: String, eventId: Long): GHIssueEvent? {
        if (eventId <= 0L) return null
        val r = request(context, "/repos/$owner/$repo/issues/events/$eventId")
        if (!r.success) return null
        return try { parseIssueEvent(JSONObject(r.body)) } catch (e: Exception) { null }
    }

    suspend fun addComment(context: Context, owner: String, repo: String, number: Int, body: String): Boolean {
        val json = JSONObject().apply { put("body", body) }.toString()
        return request(context, "/repos/$owner/$repo/issues/$number/comments", "POST", json).success
    }

    suspend fun updateIssueComment(context: Context, owner: String, repo: String, commentId: Long, body: String): Boolean {
        val json = JSONObject().apply { put("body", body) }.toString()
        return request(context, "/repos/$owner/$repo/issues/comments/$commentId", "PATCH", json).success
    }

    suspend fun deleteIssueComment(context: Context, owner: String, repo: String, commentId: Long): Boolean =
        request(context, "/repos/$owner/$repo/issues/comments/$commentId", "DELETE").let { it.code == 204 || it.success }

    suspend fun closeIssue(context: Context, owner: String, repo: String, number: Int): Boolean {
        val json = JSONObject().apply { put("state", "closed") }.toString()
        return request(context, "/repos/$owner/$repo/issues/$number", "PATCH", json).success
    }

    suspend fun reopenIssue(context: Context, owner: String, repo: String, number: Int): Boolean {
        val json = JSONObject().apply { put("state", "open") }.toString()
        return request(context, "/repos/$owner/$repo/issues/$number", "PATCH", json).success
    }

    suspend fun lockIssue(context: Context, owner: String, repo: String, number: Int, reason: String = ""): Boolean {
        val json = JSONObject().apply {
            if (reason.isNotBlank()) put("lock_reason", reason)
        }.toString()
        return request(context, "/repos/$owner/$repo/issues/$number/lock", "PUT", json).let { it.code == 204 || it.success }
    }

    suspend fun unlockIssue(context: Context, owner: String, repo: String, number: Int): Boolean =
        request(context, "/repos/$owner/$repo/issues/$number/lock", "DELETE").let { it.code == 204 || it.success }

    suspend fun getIssueDetail(context: Context, owner: String, repo: String, number: Int): GHIssueDetail? {
        val r = request(context, "/repos/$owner/$repo/issues/$number")
        if (!r.success) return null
        return try {
            val j = JSONObject(r.body)
            val labels = mutableListOf<String>()
            val labelsArr = j.optJSONArray("labels")
            if (labelsArr != null) for (i in 0 until labelsArr.length()) labels.add(labelsArr.getJSONObject(i).optString("name"))
            GHIssueDetail(
                number = j.optInt("number"), title = j.optString("title"),
                body = j.optString("body", ""), state = j.optString("state"),
                author = j.optJSONObject("user")?.optString("login") ?: "",
                avatarUrl = j.optJSONObject("user")?.optString("avatar_url") ?: "",
                createdAt = j.optString("created_at"), comments = j.optInt("comments", 0),
                labels = labels, isPR = j.has("pull_request"),
                assignee = j.optJSONObject("assignee")?.optString("login") ?: "",
                milestoneTitle = j.optJSONObject("milestone")?.optString("title") ?: "",
                locked = j.optBoolean("locked", false),
                activeLockReason = j.optString("active_lock_reason", "")
            )
        } catch (e: Exception) { null }
    }

    suspend fun isStarred(context: Context, owner: String, repo: String): Boolean =
        request(context, "/user/starred/$owner/$repo").code == 204

    suspend fun starRepo(context: Context, owner: String, repo: String): Boolean =
        request(context, "/user/starred/$owner/$repo", "PUT").let { it.code == 204 || it.success }

    suspend fun unstarRepo(context: Context, owner: String, repo: String): Boolean =
        request(context, "/user/starred/$owner/$repo", "DELETE").let { it.code == 204 || it.success }

    suspend fun forkRepo(context: Context, owner: String, repo: String): Boolean =
        request(context, "/repos/$owner/$repo/forks", "POST", "{}").success

    suspend fun getReadme(context: Context, owner: String, repo: String): String {
        val r = request(context, "/repos/$owner/$repo/readme")
        if (!r.success) return ""
        return try {
            val j = JSONObject(r.body)
            val content = j.optString("content", "")
            String(android.util.Base64.decode(content.replace("\n", ""), android.util.Base64.DEFAULT))
        } catch (e: Exception) { "" }
    }

    suspend fun getLanguages(context: Context, owner: String, repo: String): Map<String, Long> {
        val r = request(context, "/repos/$owner/$repo/languages")
        if (!r.success) return emptyMap()
        return try {
            val j = JSONObject(r.body)
            val map = mutableMapOf<String, Long>()
            j.keys().forEach { key -> map[key] = j.optLong(key) }
            map
        } catch (e: Exception) { emptyMap() }
    }

    suspend fun getContributors(context: Context, owner: String, repo: String): List<GHContributor> {
        val r = request(context, "/repos/$owner/$repo/contributors?per_page=30")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHContributor(j.optString("login"), j.optString("avatar_url", ""), j.optInt("contributions", 0))
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getRepoTrafficViews(context: Context, owner: String, repo: String): GHTrafficSeries? {
        val r = request(context, "/repos/$owner/$repo/traffic/views?per=day")
        if (!r.success) return null
        return try { parseTrafficSeries(JSONObject(r.body), "views") } catch (e: Exception) { null }
    }

    suspend fun getRepoTrafficClones(context: Context, owner: String, repo: String): GHTrafficSeries? {
        val r = request(context, "/repos/$owner/$repo/traffic/clones?per=day")
        if (!r.success) return null
        return try { parseTrafficSeries(JSONObject(r.body), "clones") } catch (e: Exception) { null }
    }

    suspend fun getRepoTrafficReferrers(context: Context, owner: String, repo: String): List<GHTrafficReferrer> {
        val r = request(context, "/repos/$owner/$repo/traffic/popular/referrers")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHTrafficReferrer(
                    referrer = j.optString("referrer", ""),
                    count = j.optInt("count", 0),
                    uniques = j.optInt("uniques", 0)
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getRepoTrafficPaths(context: Context, owner: String, repo: String): List<GHTrafficPath> {
        val r = request(context, "/repos/$owner/$repo/traffic/popular/paths")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHTrafficPath(
                    path = j.optString("path", ""),
                    title = j.optString("title", ""),
                    count = j.optInt("count", 0),
                    uniques = j.optInt("uniques", 0)
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getRepoStargazers(context: Context, owner: String, repo: String, page: Int = 1): List<GHRepoPerson> {
        val r = request(
            context,
            "/repos/$owner/$repo/stargazers?per_page=50&page=$page",
            extraHeaders = mapOf("Accept" to "application/vnd.github.star+json")
        )
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).mapNotNull { i -> parseRepoPerson(arr.getJSONObject(i), starred = true) }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getRepoWatchers(context: Context, owner: String, repo: String, page: Int = 1): List<GHRepoPerson> {
        val r = request(context, "/repos/$owner/$repo/subscribers?per_page=50&page=$page")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).mapNotNull { i -> parseRepoPerson(arr.getJSONObject(i), starred = false) }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getRepoEvents(context: Context, owner: String, repo: String, page: Int = 1): List<GHRepoEvent> {
        val r = request(context, "/repos/$owner/$repo/events?per_page=50&page=$page")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                val payload = j.optJSONObject("payload")
                GHRepoEvent(
                    id = j.optString("id", ""),
                    type = j.optString("type", ""),
                    actor = j.optJSONObject("actor")?.optString("login") ?: "",
                    createdAt = j.optString("created_at", ""),
                    action = payload?.optString("action", "") ?: "",
                    ref = payload?.optString("ref", "") ?: "",
                    refType = payload?.optString("ref_type", "") ?: "",
                    size = payload?.optInt("size", 0) ?: 0
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getReleases(context: Context, owner: String, repo: String): List<GHRelease> {
        val r = request(context, "/repos/$owner/$repo/releases?per_page=20")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                val assets = mutableListOf<GHAsset>()
                val assetsArr = j.optJSONArray("assets")
                if (assetsArr != null) for (a in 0 until assetsArr.length()) {
                    val aj = assetsArr.getJSONObject(a)
                    assets.add(parseReleaseAsset(aj))
                }
                parseRelease(j, assets)
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun createRelease(context: Context, owner: String, repo: String, tag: String, name: String, body: String, prerelease: Boolean = false): Boolean {
        val json = JSONObject().apply {
            put("tag_name", tag)
            put("name", name)
            put("body", body)
            put("prerelease", prerelease)
        }.toString()
        return request(context, "/repos/$owner/$repo/releases", "POST", json).success
    }

    suspend fun createReleaseDetailed(
        context: Context,
        owner: String,
        repo: String,
        tag: String,
        name: String,
        body: String,
        prerelease: Boolean = false,
        draft: Boolean = false,
        targetCommitish: String = ""
    ): GHRelease? {
        val json = JSONObject().apply {
            put("tag_name", tag)
            if (targetCommitish.isNotBlank()) put("target_commitish", targetCommitish)
            put("name", name)
            put("body", body)
            put("prerelease", prerelease)
            put("draft", draft)
        }.toString()
        val r = request(context, "/repos/$owner/$repo/releases", "POST", json)
        if (!r.success) return null
        return try { parseRelease(JSONObject(r.body)) } catch (e: Exception) { null }
    }

    suspend fun getReleaseByTag(context: Context, owner: String, repo: String, tag: String): GHRelease? {
        val encodedTag = URLEncoder.encode(tag, "UTF-8")
        val r = request(context, "/repos/$owner/$repo/releases/tags/$encodedTag")
        if (!r.success) return null
        return try { parseRelease(JSONObject(r.body)) } catch (e: Exception) { null }
    }

    suspend fun updateRelease(context: Context, owner: String, repo: String, tag: String, name: String, body: String, prerelease: Boolean): Boolean =
        updateReleaseDetailed(context, owner, repo, tag, name, body, prerelease, draft = null) != null

    suspend fun updateReleaseDetailed(
        context: Context,
        owner: String,
        repo: String,
        tag: String,
        name: String,
        body: String,
        prerelease: Boolean,
        draft: Boolean? = null,
        releaseId: Long = 0L
    ): GHRelease? {
        val resolvedReleaseId = if (releaseId > 0L) releaseId else {
            val encodedTag = URLEncoder.encode(tag, "UTF-8")
            val r = request(context, "/repos/$owner/$repo/releases/tags/$encodedTag")
            if (!r.success) return null
            JSONObject(r.body).optLong("id")
        }
        if (resolvedReleaseId == 0L) return null
        val json = JSONObject().apply {
            put("tag_name", tag)
            put("name", name)
            put("body", body)
            put("prerelease", prerelease)
            if (draft != null) put("draft", draft)
        }.toString()
        val r = request(context, "/repos/$owner/$repo/releases/$resolvedReleaseId", "PATCH", json)
        if (!r.success) return null
        return try { parseRelease(JSONObject(r.body)) } catch (e: Exception) { null }
    }

    suspend fun publishRelease(context: Context, owner: String, repo: String, release: GHRelease): GHRelease? {
        if (!release.draft) return release
        val tag = release.tag.ifBlank { return null }
        val name = release.name.ifBlank { tag }
        return updateReleaseDetailed(
            context = context,
            owner = owner,
            repo = repo,
            tag = tag,
            name = name,
            body = release.body,
            prerelease = release.prerelease,
            draft = false,
            releaseId = release.id
        )
    }

    suspend fun deleteRelease(context: Context, owner: String, repo: String, tag: String): Boolean {
        val encodedTag = URLEncoder.encode(tag, "UTF-8")
        val r = request(context, "/repos/$owner/$repo/releases/tags/$encodedTag")
        if (!r.success) return false
        val releaseId = JSONObject(r.body).optLong("id")
        if (releaseId == 0L) return false
        return request(context, "/repos/$owner/$repo/releases/$releaseId", "DELETE").let { it.code == 204 || it.success }
    }

    suspend fun deleteReleaseAsset(context: Context, owner: String, repo: String, assetId: Long): Boolean =
        request(context, "/repos/$owner/$repo/releases/assets/$assetId", "DELETE").let { it.code == 204 || it.success }

    suspend fun downloadReleaseAsset(context: Context, asset: GHAsset, destFile: java.io.File): Boolean =
        withContext(Dispatchers.IO) {
            try {
                if (asset.downloadUrl.isBlank()) return@withContext false
                val token = getToken(context)
                val conn = (URL(asset.downloadUrl).openConnection() as HttpURLConnection).apply {
                    if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Accept", "application/octet-stream")
                    connectTimeout = 15000
                    readTimeout = 60000
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    conn.disconnect()
                    return@withContext false
                }
                destFile.parentFile?.mkdirs()
                conn.inputStream.use { input -> destFile.outputStream().use { out -> input.copyTo(out) } }
                conn.disconnect()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Download release asset: ${e.message}")
                false
            }
        }

    suspend fun uploadReleaseAsset(context: Context, owner: String, repo: String, releaseId: Long, file: java.io.File, label: String = ""): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val token = getToken(context)
                val uploadUrl = "$API/repos/$owner/$repo/releases/$releaseId/assets?name=${URLEncoder.encode(file.name, "UTF-8")}"
                val conn = (URL(uploadUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("Content-Type", getContentType(file.name))
                    doOutput = true
                    connectTimeout = 30000
                    readTimeout = 120000
                }
                file.inputStream().use { input -> conn.outputStream.use { output -> input.copyTo(output) } }
                val code = conn.responseCode
                conn.disconnect()
                code in 200..299
            } catch (e: Exception) {
                Log.e(TAG, "Upload asset: ${e.message}")
                false
            }
        }

    suspend fun uploadReleaseAssetDetailed(context: Context, owner: String, repo: String, releaseId: Long, file: java.io.File, label: String = ""): GHAsset? =
        withContext(Dispatchers.IO) {
            try {
                val token = getToken(context)
                val labelQuery = label.takeIf { it.isNotBlank() }?.let { "&label=${URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
                val uploadUrl = "$API/repos/$owner/$repo/releases/$releaseId/assets?name=${URLEncoder.encode(file.name, "UTF-8")}$labelQuery"
                val conn = (URL(uploadUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("Content-Type", getContentType(file.name))
                    doOutput = true
                    connectTimeout = 30000
                    readTimeout = 120000
                }
                file.inputStream().use { input -> conn.outputStream.use { output -> input.copyTo(output) } }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                conn.disconnect()
                if (code in 200..299) parseReleaseAsset(JSONObject(text)) else null
            } catch (e: Exception) {
                Log.e(TAG, "Upload asset: ${e.message}")
                null
            }
        }

    private fun parseRelease(j: JSONObject, parsedAssets: List<GHAsset>? = null): GHRelease {
        val assets = parsedAssets ?: run {
            val arr = j.optJSONArray("assets") ?: JSONArray()
            (0 until arr.length()).map { i -> parseReleaseAsset(arr.getJSONObject(i)) }
        }
        return GHRelease(
            id = j.optLong("id"),
            tag = j.optString("tag_name"),
            name = j.optString("name", ""),
            body = j.optString("body", ""),
            prerelease = j.optBoolean("prerelease", false),
            draft = j.optBoolean("draft", false),
            createdAt = j.optString("published_at", j.optString("created_at", "")),
            htmlUrl = j.optString("html_url", ""),
            uploadUrl = j.optString("upload_url", "").substringBefore("{"),
            assets = assets
        )
    }

    private fun parseReleaseAsset(j: JSONObject): GHAsset = GHAsset(
        id = j.optLong("id"),
        name = j.optString("name"),
        size = j.optLong("size", 0),
        downloadUrl = j.optString("browser_download_url", ""),
        downloadCount = j.optInt("download_count", 0),
        contentType = j.optString("content_type", ""),
        state = j.optString("state", "")
    )

    private fun getContentType(filename: String): String {
        return when (filename.substringAfterLast(".", "").lowercase()) {
            "zip" -> "application/zip"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            "jar" -> "application/java-archive"
            "apk" -> "application/vnd.android.package-archive"
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            else -> "application/octet-stream"
        }
    }

    suspend fun getGists(context: Context): List<GHGist> {
        val r = request(context, "/gists?per_page=30")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                val filesObj = j.optJSONObject("files")
                val files = mutableListOf<String>()
                filesObj?.keys()?.forEach { files.add(it) }
                GHGist(
                    id = j.optString("id"), description = j.optString("description", ""),
                    isPublic = j.optBoolean("public", true), files = files,
                    createdAt = j.optString("created_at", ""), updatedAt = j.optString("updated_at", "")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun createGist(context: Context, description: String, isPublic: Boolean, files: Map<String, String>): Boolean {
        val filesObj = JSONObject()
        files.forEach { (name, content) -> filesObj.put(name, JSONObject().apply { put("content", content) }) }
        val body = JSONObject().apply {
            put("description", description); put("public", isPublic); put("files", filesObj)
        }.toString()
        return request(context, "/gists", "POST", body).success
    }

    suspend fun getGistContent(context: Context, gistId: String): Map<String, String> {
        val r = request(context, "/gists/$gistId")
        if (!r.success) return emptyMap()
        return try {
            val filesObj = JSONObject(r.body).optJSONObject("files") ?: return emptyMap()
            val result = mutableMapOf<String, String>()
            filesObj.keys().forEach { key ->
                result[key] = filesObj.getJSONObject(key).optString("content", "")
            }
            result
        } catch (e: Exception) { emptyMap() }
    }

    suspend fun deleteGist(context: Context, gistId: String): Boolean =
        request(context, "/gists/$gistId", "DELETE").let { it.code == 204 || it.success }

    suspend fun searchUsers(context: Context, query: String): List<GHUser> {
        val q = URLEncoder.encode(query, "UTF-8")
        val r = request(context, "/search/users?q=$q&per_page=30")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONObject(r.body).getJSONArray("items")
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHUser(j.optString("login"), "", j.optString("avatar_url", ""), "", 0, 0, 0, 0)
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getCommitDiff(context: Context, owner: String, repo: String, sha: String): GHCommitDetail? {
        val r = request(context, "/repos/$owner/$repo/commits/$sha")
        if (!r.success) return null
        return try {
            val j = JSONObject(r.body)
            val filesArr = j.optJSONArray("files")
            val files = mutableListOf<GHDiffFile>()
            if (filesArr != null) for (i in 0 until filesArr.length()) {
                val fj = filesArr.getJSONObject(i)
                files.add(GHDiffFile(
                    filename = fj.optString("filename"), status = fj.optString("status"),
                    additions = fj.optInt("additions"), deletions = fj.optInt("deletions"),
                    patch = fj.optString("patch", "")
                ))
            }
            GHCommitDetail(
                sha = j.optString("sha"), message = j.getJSONObject("commit").optString("message"),
                author = j.getJSONObject("commit").optJSONObject("author")?.optString("name") ?: "",
                date = j.getJSONObject("commit").optJSONObject("author")?.optString("date") ?: "",
                files = files, totalAdditions = j.optJSONObject("stats")?.optInt("additions") ?: 0,
                totalDeletions = j.optJSONObject("stats")?.optInt("deletions") ?: 0
            )
        } catch (e: Exception) { null }
    }

    suspend fun getWorkflows(context: Context, owner: String, repo: String): List<GHWorkflow> {
        val workflows = mutableListOf<GHWorkflow>()
        var page = 1
        while (true) {
            val r = request(context, "/repos/$owner/$repo/actions/workflows?per_page=100&page=$page")
            if (!r.success) break
            val count = try {
                val arr = JSONObject(r.body).getJSONArray("workflows")
                for (i in 0 until arr.length()) {
                    val j = arr.getJSONObject(i)
                    workflows += parseWorkflow(j)
                }
                arr.length()
            } catch (e: Exception) {
                0
            }
            if (count < 100) break
            page++
        }
        return workflows.distinctBy { it.id }
    }

    suspend fun getWorkflow(context: Context, owner: String, repo: String, workflowId: Long): GHWorkflow? {
        val r = request(context, "/repos/$owner/$repo/actions/workflows/$workflowId")
        if (!r.success) return null
        return try { parseWorkflow(JSONObject(r.body)) } catch (e: Exception) { null }
    }

    suspend fun getWorkflowRuns(
        context: Context,
        owner: String,
        repo: String,
        workflowId: Long? = null,
        perPage: Int = 20,
        page: Int = 1,
        branch: String? = null,
        event: String? = null,
        status: String? = null
    ): List<GHWorkflowRun> {
        val params = mutableListOf("per_page=$perPage", "page=$page")
        branch?.takeIf { it.isNotBlank() }?.let { params += "branch=${URLEncoder.encode(it, "UTF-8")}" }
        event?.takeIf { it.isNotBlank() }?.let { params += "event=${URLEncoder.encode(it, "UTF-8")}" }
        status?.takeIf { it.isNotBlank() }?.let { params += "status=${URLEncoder.encode(it, "UTF-8")}" }
        val query = params.joinToString("&")
        val endpoint = if (workflowId != null) "/repos/$owner/$repo/actions/workflows/$workflowId/runs?$query"
            else "/repos/$owner/$repo/actions/runs?$query"
        val r = request(context, endpoint)
        if (!r.success) return emptyList()
        return try {
            val arr = JSONObject(r.body).getJSONArray("workflow_runs")
            (0 until arr.length()).map { i -> parseWorkflowRun(arr.getJSONObject(i)) }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getWorkflowRun(context: Context, owner: String, repo: String, runId: Long): GHWorkflowRun? {
        val r = request(context, "/repos/$owner/$repo/actions/runs/$runId")
        if (!r.success) return null
        return try {
            parseWorkflowRun(JSONObject(r.body))
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getWorkflowRunJobs(context: Context, owner: String, repo: String, runId: Long): List<GHJob> {
        val r = request(context, "/repos/$owner/$repo/actions/runs/$runId/jobs?filter=all&per_page=100")
        if (!r.success) return emptyList()
        return parseJobs(r.body)
    }

    suspend fun getWorkflowRunLogs(context: Context, owner: String, repo: String, runId: Long): String =
        withContext(Dispatchers.IO) {
            try {
                val token = getToken(context)
                val url = "$API/repos/$owner/$repo/actions/runs/$runId/logs"
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    instanceFollowRedirects = false; connectTimeout = 15000; readTimeout = 15000
                }
                val code = conn.responseCode
                if (code == 302) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location != null) "Logs URL: $location" else "No logs available"
                } else {
                    conn.disconnect()
                    "Logs: HTTP $code"
                }
            } catch (e: Exception) { "Error: ${e.message}" }
        }

    suspend fun getJobLogs(context: Context, owner: String, repo: String, jobId: Long): String =
        withContext(Dispatchers.IO) {
            try {
                val token = getToken(context)
                val url = "$API/repos/$owner/$repo/actions/jobs/$jobId/logs"
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    instanceFollowRedirects = true; connectTimeout = 15000; readTimeout = 30000
                }
                val code = conn.responseCode
                if (code == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    text
                } else {
                    conn.disconnect()
                    "Error: HTTP $code"
                }
            } catch (e: Exception) { "Error: ${e.message}" }
        }

    suspend fun rerunWorkflow(context: Context, owner: String, repo: String, runId: Long): Boolean =
        request(context, "/repos/$owner/$repo/actions/runs/$runId/rerun", "POST", "{}").success

    suspend fun rerunFailedJobs(context: Context, owner: String, repo: String, runId: Long): Boolean =
        request(context, "/repos/$owner/$repo/actions/runs/$runId/rerun-failed-jobs", "POST", "{}").success

    suspend fun cancelWorkflowRun(context: Context, owner: String, repo: String, runId: Long): Boolean =
        request(context, "/repos/$owner/$repo/actions/runs/$runId/cancel", "POST", "{}").success

    suspend fun enableWorkflow(context: Context, owner: String, repo: String, workflowId: Long): Boolean =
        request(context, "/repos/$owner/$repo/actions/workflows/$workflowId/enable", "PUT", "{}").let { it.code == 204 || it.success }

    suspend fun disableWorkflow(context: Context, owner: String, repo: String, workflowId: Long): Boolean =
        request(context, "/repos/$owner/$repo/actions/workflows/$workflowId/disable", "PUT", "{}").let { it.code == 204 || it.success }

    suspend fun dispatchWorkflow(context: Context, owner: String, repo: String, workflowId: Long, branch: String, inputs: Map<String, String> = emptyMap()): Boolean {
        val body = JSONObject().apply {
            put("ref", branch)
            if (inputs.isNotEmpty()) {
                val inputsObj = JSONObject()
                inputs.forEach { (k, v) -> inputsObj.put(k, v) }
                put("inputs", inputsObj)
            }
        }.toString()
        return request(context, "/repos/$owner/$repo/actions/workflows/$workflowId/dispatches", "POST", body).let { it.code == 204 || it.success }
    }

    suspend fun dispatchWorkflowDetailed(context: Context, owner: String, repo: String, workflowId: Long, branch: String, inputs: Map<String, String> = emptyMap()): GHActionResult {
        val body = JSONObject().apply {
            put("ref", branch)
            if (inputs.isNotEmpty()) {
                val inputsObj = JSONObject()
                inputs.forEach { (k, v) -> inputsObj.put(k, v) }
                put("inputs", inputsObj)
            }
        }.toString()
        val r = request(context, "/repos/$owner/$repo/actions/workflows/$workflowId/dispatches", "POST", body)
        val success = r.code == 204 || r.success
        return GHActionResult(success, r.code, if (success) "" else apiErrorMessage(r))
    }

    suspend fun dispatchWorkflow(context: Context, owner: String, repo: String, workflowId: String, ref: String, inputs: Map<String, String> = emptyMap()): Boolean {
        val body = JSONObject().apply {
            put("ref", ref)
            if (inputs.isNotEmpty()) {
                val inputsObj = JSONObject()
                inputs.forEach { (k, v) -> inputsObj.put(k, v) }
                put("inputs", inputsObj)
            }
        }.toString()
        val encodedId = URLEncoder.encode(workflowId, "UTF-8")
        return request(context, "/repos/$owner/$repo/actions/workflows/$encodedId/dispatches", "POST", body).let { it.code == 204 || it.success }
    }

    suspend fun getWorkflowDispatchSchema(context: Context, owner: String, repo: String, workflowPath: String, branch: String? = null): GHWorkflowDispatchSchema? {
        val content = getFileContent(context, owner, repo, workflowPath, branch)
        if (content.isBlank()) return null
        return parseWorkflowDispatchSchema(workflowPath, content)
    }

    suspend fun getWorkflowDispatchSchemas(context: Context, owner: String, repo: String, workflows: List<GHWorkflow>, branch: String? = null): List<GHWorkflowDispatchSchema> {
        return workflows.mapNotNull { workflow ->
            getWorkflowDispatchSchema(context, owner, repo, workflow.path, branch)?.copy(
                workflowName = workflow.name.ifBlank { workflow.path.substringAfterLast('/') }
            )
        }
    }

    private fun parseWorkflowDispatchSchema(workflowPath: String, yaml: String): GHWorkflowDispatchSchema? {
        val lines = yaml.lines()
        val workflowName = lines.firstOrNull { it.trimStart().startsWith("name:") }
            ?.substringAfter(":")
            ?.trim()
            ?.trim('"', '\'')
            .orEmpty()
            .ifBlank { workflowPath.substringAfterLast('/') }

        val inlineDispatch = lines.any { line ->
            val clean = yamlClean(line)
            clean == "on: workflow_dispatch" ||
                clean.matches(Regex("""on:\s*\[.*\bworkflow_dispatch\b.*]""")) ||
                clean == "- workflow_dispatch"
        }
        val dispatchIndex = lines.indexOfFirst { line ->
            val clean = yamlClean(line)
            clean == "workflow_dispatch" || clean.startsWith("workflow_dispatch:")
        }
        if (dispatchIndex < 0) {
            return if (inlineDispatch) GHWorkflowDispatchSchema(workflowPath, workflowName, emptyList()) else null
        }

        val dispatchIndent = lines[dispatchIndex].takeWhile { it == ' ' }.length
        val inputsIndex = lines.indexOfFirstIndexed(dispatchIndex + 1) { _, line ->
            val indent = line.takeWhile { it == ' ' }.length
            yamlClean(line).startsWith("inputs:") && indent > dispatchIndent
        }
        if (inputsIndex < 0) return GHWorkflowDispatchSchema(workflowPath, workflowName, emptyList())

        val inputsIndent = lines[inputsIndex].takeWhile { it == ' ' }.length
        val results = mutableListOf<GHWorkflowDispatchInput>()
        var i = inputsIndex + 1
        while (i < lines.size) {
            val raw = lines[i]
            if (raw.trim().isBlank()) { i++; continue }
            val indent = raw.takeWhile { it == ' ' }.length
            if (indent <= inputsIndent) break
            val trimmed = yamlClean(raw)
            if (trimmed.endsWith(":") && !trimmed.startsWith("#")) {
                val key = trimmed.removeSuffix(":").trim().trim('"', '\'')
                var description = ""
                var required = false
                var defaultValue = ""
                var type = ""
                val options = mutableListOf<String>()
                val keyIndent = indent
                i++
                while (i < lines.size) {
                    val childRaw = lines[i]
                    if (childRaw.trim().isBlank()) { i++; continue }
                    val childIndent = childRaw.takeWhile { it == ' ' }.length
                    if (childIndent <= keyIndent) break
                    val childTrim = yamlClean(childRaw)
                    when {
                        childTrim.startsWith("description:") -> description = yamlScalar(childTrim.substringAfter(":"))
                        childTrim.startsWith("required:") -> required = yamlScalar(childTrim.substringAfter(":")).equals("true", true)
                        childTrim.startsWith("default:") -> defaultValue = yamlScalar(childTrim.substringAfter(":"))
                        childTrim.startsWith("type:") -> type = yamlScalar(childTrim.substringAfter(":")).lowercase()
                        childTrim.startsWith("options:") -> {
                            val inlineOptions = yamlInlineList(childTrim.substringAfter(":"))
                            if (inlineOptions.isNotEmpty()) {
                                options += inlineOptions
                                i++
                                continue
                            }
                            i++
                            while (i < lines.size) {
                                val optionRaw = lines[i]
                                if (optionRaw.trim().isBlank()) { i++; continue }
                                val optionIndent = optionRaw.takeWhile { it == ' ' }.length
                                if (optionIndent <= childIndent) break
                                val optionTrim = yamlClean(optionRaw)
                                if (optionTrim.startsWith("- ")) options += yamlScalar(optionTrim.removePrefix("- "))
                                i++
                            }
                            continue
                        }
                    }
                    i++
                }
                results += GHWorkflowDispatchInput(
                    key = key,
                    description = description,
                    required = required,
                    defaultValue = defaultValue,
                    type = type,
                    options = options
                )
                continue
            }
            i++
        }
        return GHWorkflowDispatchSchema(workflowPath, workflowName, results)
    }

    private fun yamlClean(line: String): String = line.substringBefore("#").trim()

    private fun yamlScalar(value: String): String = value.trim().trim('"', '\'')

    private fun yamlInlineList(value: String): List<String> {
        val trimmed = value.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
        return trimmed.removePrefix("[")
            .removeSuffix("]")
            .split(',')
            .map { yamlScalar(it) }
            .filter { it.isNotBlank() }
    }

    private inline fun List<String>.indexOfFirstIndexed(startIndex: Int, predicate: (Int, String) -> Boolean): Int {
        for (index in startIndex until size) if (predicate(index, this[index])) return index
        return -1
    }

    suspend fun getRunArtifacts(context: Context, owner: String, repo: String, runId: Long): List<GHArtifact> {
        val r = request(context, "/repos/$owner/$repo/actions/runs/$runId/artifacts?per_page=100")
        if (!r.success) return emptyList()
        return parseArtifacts(r.body)
    }

    suspend fun downloadArtifact(context: Context, owner: String, repo: String, artifactId: Long, destFile: java.io.File): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val token = getToken(context)
                val url = "$API/repos/$owner/$repo/actions/artifacts/$artifactId/zip"
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    instanceFollowRedirects = true
                    connectTimeout = 15000; readTimeout = 60000
                }
                val code = conn.responseCode
                if (code != 200) { conn.disconnect(); return@withContext false }
                destFile.parentFile?.mkdirs()
                conn.inputStream.use { input -> destFile.outputStream().use { out -> input.copyTo(out) } }
                conn.disconnect()
                true
            } catch (e: Exception) { Log.e(TAG, "Download artifact: ${e.message}"); false }
        }

    suspend fun deleteArtifact(context: Context, owner: String, repo: String, artifactId: Long): Boolean =
        request(context, "/repos/$owner/$repo/actions/artifacts/$artifactId", "DELETE").let { it.code == 204 || it.success }

    suspend fun getRepositoryArtifacts(context: Context, owner: String, repo: String, page: Int = 1, name: String? = null): List<GHArtifact> {
        val params = mutableListOf("per_page=100", "page=$page")
        name?.takeIf { it.isNotBlank() }?.let { params += "name=${URLEncoder.encode(it, "UTF-8")}" }
        val r = request(context, "/repos/$owner/$repo/actions/artifacts?${params.joinToString("&")}")
        if (!r.success) return emptyList()
        return parseArtifacts(r.body)
    }

    suspend fun getArtifact(context: Context, owner: String, repo: String, artifactId: Long): GHArtifact? {
        val r = request(context, "/repos/$owner/$repo/actions/artifacts/$artifactId")
        if (!r.success) return null
        return try { parseArtifact(JSONObject(r.body)) } catch (e: Exception) { null }
    }

    suspend fun getWorkflowRunAttempt(context: Context, owner: String, repo: String, runId: Long, attempt: Int): GHWorkflowRun? {
        val r = request(context, "/repos/$owner/$repo/actions/runs/$runId/attempts/$attempt")
        if (!r.success) return null
        return try { parseWorkflowRun(JSONObject(r.body)) } catch (e: Exception) { null }
    }

    suspend fun getWorkflowRunAttemptJobs(context: Context, owner: String, repo: String, runId: Long, attempt: Int): List<GHJob> {
        val r = request(context, "/repos/$owner/$repo/actions/runs/$runId/attempts/$attempt/jobs?per_page=100")
        if (!r.success) return emptyList()
        return parseJobs(r.body)
    }

    suspend fun getWorkflowRunAttemptLogs(context: Context, owner: String, repo: String, runId: Long, attempt: Int): String =
        getRedirectLocationOrText(context, "$API/repos/$owner/$repo/actions/runs/$runId/attempts/$attempt/logs")

    suspend fun rerunJob(context: Context, owner: String, repo: String, jobId: Long): Boolean =
        request(context, "/repos/$owner/$repo/actions/jobs/$jobId/rerun", "POST", "{}").success

    suspend fun deleteWorkflowRun(context: Context, owner: String, repo: String, runId: Long): Boolean =
        request(context, "/repos/$owner/$repo/actions/runs/$runId", "DELETE").let { it.code == 204 || it.success }

    suspend fun deleteWorkflowRunLogs(context: Context, owner: String, repo: String, runId: Long): Boolean =
        request(context, "/repos/$owner/$repo/actions/runs/$runId/logs", "DELETE").let { it.code == 204 || it.success }

    suspend fun forceCancelWorkflowRun(context: Context, owner: String, repo: String, runId: Long): Boolean =
        request(context, "/repos/$owner/$repo/actions/runs/$runId/force-cancel", "POST", "{}").success

    suspend fun getWorkflowUsage(context: Context, owner: String, repo: String, workflowId: Long): GHActionsUsage? {
        val r = request(context, "/repos/$owner/$repo/actions/workflows/$workflowId/timing")
        if (!r.success) return null
        return parseActionsUsage(r.body)
    }

    suspend fun getWorkflowRunUsage(context: Context, owner: String, repo: String, runId: Long): GHActionsUsage? {
        val r = request(context, "/repos/$owner/$repo/actions/runs/$runId/timing")
        if (!r.success) return null
        return parseActionsUsage(r.body)
    }

    suspend fun getCheckRunsForRef(context: Context, owner: String, repo: String, ref: String): List<GHCheckRun> {
        if (ref.isBlank()) return emptyList()
        val encodedRef = URLEncoder.encode(ref, "UTF-8")
        val r = request(context, "/repos/$owner/$repo/commits/$encodedRef/check-runs?per_page=100")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONObject(r.body).optJSONArray("check_runs") ?: JSONArray()
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                val output = j.optJSONObject("output")
                GHCheckRun(
                    id = j.optLong("id"),
                    name = j.optString("name"),
                    status = j.optString("status"),
                    conclusion = j.optString("conclusion", ""),
                    detailsUrl = j.optString("details_url", ""),
                    htmlUrl = j.optString("html_url", ""),
                    startedAt = j.optString("started_at", ""),
                    completedAt = j.optString("completed_at", ""),
                    title = output?.optString("title") ?: "",
                    summary = output?.optString("summary") ?: "",
                    annotationsCount = output?.optInt("annotations_count", 0) ?: 0
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getCheckRunAnnotations(context: Context, owner: String, repo: String, checkRunId: Long): List<GHCheckAnnotation> {
        val r = request(context, "/repos/$owner/$repo/check-runs/$checkRunId/annotations?per_page=100")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHCheckAnnotation(
                    path = j.optString("path").trim().takeUnless { it.equals("null", ignoreCase = true) } ?: "",
                    startLine = j.optInt("start_line", 0),
                    endLine = j.optInt("end_line", 0),
                    annotationLevel = j.optString("annotation_level").trim().takeUnless { it.equals("null", ignoreCase = true) } ?: "",
                    message = j.optString("message").trim().takeUnless { it.equals("null", ignoreCase = true) } ?: "",
                    title = j.optString("title", "").trim().takeUnless { it.equals("null", ignoreCase = true) } ?: "",
                    rawDetails = j.optString("raw_details", "").trim().takeUnless { it.equals("null", ignoreCase = true) } ?: ""
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getPendingDeployments(context: Context, owner: String, repo: String, runId: Long): List<GHPendingDeployment> {
        val r = request(context, "/repos/$owner/$repo/actions/runs/$runId/pending_deployments")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                val env = j.optJSONObject("environment")
                GHPendingDeployment(
                    environmentId = env?.optLong("id") ?: 0L,
                    environmentName = env?.optString("name") ?: "",
                    currentUserCanApprove = j.optBoolean("current_user_can_approve", false),
                    waitTimer = j.optInt("wait_timer", 0),
                    waitTimerStartedAt = j.optString("wait_timer_started_at", ""),
                    reviewers = j.optJSONArray("reviewers")?.let { reviewers ->
                        (0 until reviewers.length()).mapNotNull { idx ->
                            reviewers.optJSONObject(idx)?.optJSONObject("reviewer")?.optString("login")
                        }
                    } ?: emptyList()
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun reviewPendingDeployments(context: Context, owner: String, repo: String, runId: Long, environmentIds: List<Long>, approve: Boolean, comment: String): Boolean {
        val body = JSONObject().apply {
            put("environment_ids", JSONArray(environmentIds))
            put("state", if (approve) "approved" else "rejected")
            put("comment", comment)
        }.toString()
        return request(context, "/repos/$owner/$repo/actions/runs/$runId/pending_deployments", "POST", body).success
    }

    suspend fun getWorkflowRunReviewHistory(context: Context, owner: String, repo: String, runId: Long): List<GHWorkflowRunReview> {
        val r = request(context, "/repos/$owner/$repo/actions/runs/$runId/approvals")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHWorkflowRunReview(
                    state = j.optString("state"),
                    comment = j.optString("comment", ""),
                    user = j.optJSONObject("user")?.optString("login") ?: "",
                    environments = j.optJSONArray("environments")?.let { envs ->
                        (0 until envs.length()).mapNotNull { idx -> envs.optJSONObject(idx)?.optString("name") }
                    } ?: emptyList()
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun approveWorkflowRunForFork(context: Context, owner: String, repo: String, runId: Long): Boolean =
        request(context, "/repos/$owner/$repo/actions/runs/$runId/approve", "POST", "{}").success

    suspend fun getActionsCacheUsage(context: Context, owner: String, repo: String): GHActionsCacheUsage? {
        val r = request(context, "/repos/$owner/$repo/actions/cache/usage")
        if (!r.success) return null
        return try {
            val j = JSONObject(r.body)
            GHActionsCacheUsage(j.optString("full_name", ""), j.optLong("active_caches_size_in_bytes", 0), j.optInt("active_caches_count", 0))
        } catch (e: Exception) { null }
    }

    suspend fun getActionsCaches(context: Context, owner: String, repo: String, page: Int = 1, key: String? = null, ref: String? = null): List<GHActionsCacheEntry> {
        val params = mutableListOf("per_page=100", "page=$page")
        key?.takeIf { it.isNotBlank() }?.let { params += "key=${URLEncoder.encode(it, "UTF-8")}" }
        ref?.takeIf { it.isNotBlank() }?.let { params += "ref=${URLEncoder.encode(it, "UTF-8")}" }
        val r = request(context, "/repos/$owner/$repo/actions/caches?${params.joinToString("&")}")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONObject(r.body).optJSONArray("actions_caches") ?: JSONArray()
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHActionsCacheEntry(
                    id = j.optLong("id"),
                    ref = j.optString("ref"),
                    key = j.optString("key"),
                    version = j.optString("version"),
                    lastAccessedAt = j.optString("last_accessed_at"),
                    createdAt = j.optString("created_at"),
                    sizeInBytes = j.optLong("size_in_bytes", 0)
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun deleteActionsCache(context: Context, owner: String, repo: String, cacheId: Long): Boolean =
        request(context, "/repos/$owner/$repo/actions/caches/$cacheId", "DELETE").let { it.code == 204 || it.success }

    suspend fun getRepoActionsSecrets(context: Context, owner: String, repo: String): List<GHActionSecret> {
        val r = request(context, "/repos/$owner/$repo/actions/secrets?per_page=100")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONObject(r.body).optJSONArray("secrets") ?: JSONArray()
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHActionSecret(j.optString("name"), j.optString("created_at", ""), j.optString("updated_at", ""))
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getRepoActionsPublicKey(context: Context, owner: String, repo: String): GHActionPublicKey? {
        val r = request(context, "/repos/$owner/$repo/actions/secrets/public-key")
        if (!r.success) return null
        return try {
            val j = JSONObject(r.body)
            GHActionPublicKey(j.optString("key_id"), j.optString("key"))
        } catch (e: Exception) { null }
    }

    suspend fun createOrUpdateRepoActionsSecret(context: Context, owner: String, repo: String, name: String, value: String): Boolean {
        return try {
            val publicKey = getRepoActionsPublicKey(context, owner, repo) ?: return false
            val encrypted = withContext(Dispatchers.Default) {
                GitHubSecretCrypto.encryptSecret(publicKey.key, value)
            }
            val encodedName = URLEncoder.encode(name, "UTF-8")
            val body = JSONObject().apply {
                put("encrypted_value", encrypted)
                put("key_id", publicKey.keyId)
            }.toString()
            request(context, "/repos/$owner/$repo/actions/secrets/$encodedName", "PUT", body).let {
                it.code == 201 || it.code == 204 || it.success
            }
        } catch (e: Exception) {
            Log.e(TAG, "Save actions secret: ${e.message}")
            false
        }
    }

    suspend fun deleteRepoActionsSecret(context: Context, owner: String, repo: String, name: String): Boolean {
        val encodedName = URLEncoder.encode(name, "UTF-8")
        return request(context, "/repos/$owner/$repo/actions/secrets/$encodedName", "DELETE").let { it.code == 204 || it.success }
    }

    suspend fun getRepoActionsVariables(context: Context, owner: String, repo: String): List<GHActionVariable> {
        val r = request(context, "/repos/$owner/$repo/actions/variables?per_page=100")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONObject(r.body).optJSONArray("variables") ?: JSONArray()
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHActionVariable(j.optString("name"), j.optString("value"), j.optString("created_at", ""), j.optString("updated_at", ""))
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun createRepoActionsVariable(context: Context, owner: String, repo: String, name: String, value: String): Boolean {
        val body = JSONObject().apply { put("name", name); put("value", value) }.toString()
        return request(context, "/repos/$owner/$repo/actions/variables", "POST", body).success
    }

    suspend fun updateRepoActionsVariable(context: Context, owner: String, repo: String, name: String, value: String): Boolean {
        val encodedName = URLEncoder.encode(name, "UTF-8")
        val body = JSONObject().apply { put("name", name); put("value", value) }.toString()
        return request(context, "/repos/$owner/$repo/actions/variables/$encodedName", "PATCH", body).success
    }

    suspend fun deleteRepoActionsVariable(context: Context, owner: String, repo: String, name: String): Boolean {
        val encodedName = URLEncoder.encode(name, "UTF-8")
        return request(context, "/repos/$owner/$repo/actions/variables/$encodedName", "DELETE").let { it.code == 204 || it.success }
    }

    suspend fun getRepoSelfHostedRunners(context: Context, owner: String, repo: String): List<GHActionRunner> {
        val r = request(context, "/repos/$owner/$repo/actions/runners?per_page=100")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONObject(r.body).optJSONArray("runners") ?: JSONArray()
            (0 until arr.length()).map { i -> parseActionRunner(arr.getJSONObject(i)) }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getOrgRunnerGroups(context: Context, org: String): List<GHActionRunnerGroup> {
        val cleanOrg = org.trim()
        if (cleanOrg.isBlank()) return emptyList()
        val r = request(context, "/orgs/$cleanOrg/actions/runner-groups?per_page=100")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONObject(r.body).optJSONArray("runner_groups") ?: JSONArray()
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                val workflows = j.optJSONArray("selected_workflows")?.let { wf ->
                    (0 until wf.length()).mapNotNull { idx -> wf.optString(idx).takeIf { it.isNotBlank() } }
                } ?: emptyList()
                GHActionRunnerGroup(
                    id = j.optLong("id"),
                    name = j.optString("name"),
                    visibility = j.optString("visibility"),
                    isDefault = j.optBoolean("default", false),
                    inherited = j.optBoolean("inherited", false),
                    allowsPublicRepositories = j.optBoolean("allows_public_repositories", false),
                    restrictedToWorkflows = j.optBoolean("restricted_to_workflows", false),
                    selectedWorkflows = workflows,
                    runnersUrl = j.optString("runners_url", ""),
                    selectedRepositoriesUrl = j.optString("selected_repositories_url", "")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getOrgRunnerGroupRunners(context: Context, org: String, groupId: Long): List<GHActionRunner> {
        val cleanOrg = org.trim()
        if (cleanOrg.isBlank() || groupId <= 0L) return emptyList()
        val r = request(context, "/orgs/$cleanOrg/actions/runner-groups/$groupId/runners?per_page=100")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONObject(r.body).optJSONArray("runners") ?: JSONArray()
            (0 until arr.length()).map { i -> parseActionRunner(arr.getJSONObject(i)) }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getEnterpriseRunners(context: Context, enterprise: String): List<GHActionRunner> {
        val cleanEnterprise = enterprise.trim()
        if (cleanEnterprise.isBlank()) return emptyList()
        val r = request(context, "/enterprises/$cleanEnterprise/actions/runners?per_page=100")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONObject(r.body).optJSONArray("runners") ?: JSONArray()
            (0 until arr.length()).map { i -> parseActionRunner(arr.getJSONObject(i)) }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getOrgAuditLog(context: Context, org: String, phrase: String = "", page: Int = 1): List<GHAuditLogEntry> {
        val cleanOrg = org.trim()
        if (cleanOrg.isBlank()) return emptyList()
        val query = buildString {
            append("?per_page=100&page=$page")
            if (phrase.isNotBlank()) append("&phrase=${URLEncoder.encode(phrase.trim(), "UTF-8")}")
        }
        val r = request(context, "/orgs/$cleanOrg/audit-log$query")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHAuditLogEntry(
                    id = j.optString("_document_id", j.optString("id", "")),
                    action = j.optString("action", ""),
                    actor = j.optString("actor", ""),
                    createdAt = j.optString("@timestamp", j.optString("created_at", "")),
                    org = j.optString("org", cleanOrg),
                    repo = j.optString("repo", ""),
                    user = j.optString("user", ""),
                    operationType = j.optString("operation_type", ""),
                    transportProtocol = j.optString("transport_protocol", "")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getOrgScimUsers(context: Context, org: String, startIndex: Int = 1, count: Int = 50): GHScimUsersPage {
        val cleanOrg = org.trim()
        if (cleanOrg.isBlank()) return GHScimUsersPage()
        val r = request(context, "/scim/v2/organizations/$cleanOrg/Users?startIndex=$startIndex&count=$count")
        if (!r.success) return GHScimUsersPage(error = r.body.ifBlank { "HTTP ${r.code}" })
        return try {
            val j = JSONObject(r.body)
            val resources = j.optJSONArray("Resources") ?: JSONArray()
            GHScimUsersPage(
                totalResults = j.optInt("totalResults", 0),
                startIndex = j.optInt("startIndex", startIndex),
                itemsPerPage = j.optInt("itemsPerPage", count),
                users = (0 until resources.length()).map { i ->
                    val user = resources.getJSONObject(i)
                    val name = user.optJSONObject("name")
                    val emails = user.optJSONArray("emails")?.let { arr ->
                        (0 until arr.length()).mapNotNull { idx ->
                            arr.optJSONObject(idx)?.optString("value")?.takeIf { it.isNotBlank() }
                        }
                    } ?: emptyList()
                    GHScimUser(
                        id = user.optString("id", ""),
                        userName = user.optString("userName", ""),
                        displayName = user.optString("displayName", ""),
                        givenName = name?.optString("givenName", "") ?: "",
                        familyName = name?.optString("familyName", "") ?: "",
                        active = user.optBoolean("active", false),
                        externalId = user.optString("externalId", ""),
                        emails = emails
                    )
                }
            )
        } catch (e: Exception) { GHScimUsersPage(error = e.message ?: "parse error") }
    }

    suspend fun getOrgSamlAuthorizations(context: Context, org: String, login: String = "", page: Int = 1): List<GHSamlAuthorization> {
        val cleanOrg = org.trim()
        if (cleanOrg.isBlank()) return emptyList()
        val query = buildString {
            append("?per_page=100&page=$page")
            if (login.isNotBlank()) append("&login=${URLEncoder.encode(login.trim(), "UTF-8")}")
        }
        val r = request(context, "/orgs/$cleanOrg/credential-authorizations$query")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                val scopes = j.optJSONArray("scopes")?.let { scopeArr ->
                    (0 until scopeArr.length()).mapNotNull { idx -> scopeArr.optString(idx).takeIf { it.isNotBlank() } }
                } ?: emptyList()
                GHSamlAuthorization(
                    login = j.optString("login", ""),
                    credentialId = j.optLong("credential_id"),
                    credentialType = j.optString("credential_type", ""),
                    tokenLastEight = j.optString("token_last_eight", ""),
                    authorizedAt = j.optString("credential_authorized_at", ""),
                    accessedAt = j.optString("credential_accessed_at", ""),
                    expiresAt = j.optString("authorized_credential_expires_at", ""),
                    scopes = scopes
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun removeOrgSamlAuthorization(context: Context, org: String, credentialId: Long): Boolean {
        val cleanOrg = org.trim()
        if (cleanOrg.isBlank() || credentialId <= 0L) return false
        return request(context, "/orgs/$cleanOrg/credential-authorizations/$credentialId", "DELETE").let { it.code == 204 || it.success }
    }

    suspend fun checkOAuthAppToken(clientId: String, clientSecret: String, accessToken: String): GHOAuthTokenInfo? {
        val body = JSONObject().apply { put("access_token", accessToken.trim()) }.toString()
        val r = requestBasic("/applications/${clientId.trim()}/token", "POST", body, clientId.trim(), clientSecret)
        if (!r.success) return null
        return try { parseOAuthTokenInfo(JSONObject(r.body)) } catch (e: Exception) { null }
    }

    suspend fun resetOAuthAppToken(clientId: String, clientSecret: String, accessToken: String): GHOAuthTokenInfo? {
        val body = JSONObject().apply { put("access_token", accessToken.trim()) }.toString()
        val r = requestBasic("/applications/${clientId.trim()}/token", "PATCH", body, clientId.trim(), clientSecret)
        if (!r.success) return null
        return try { parseOAuthTokenInfo(JSONObject(r.body)) } catch (e: Exception) { null }
    }

    suspend fun deleteOAuthAppToken(clientId: String, clientSecret: String, accessToken: String): Boolean {
        val body = JSONObject().apply { put("access_token", accessToken.trim()) }.toString()
        return requestBasic("/applications/${clientId.trim()}/token", "DELETE", body, clientId.trim(), clientSecret).let { it.code == 204 || it.success }
    }

    suspend fun deleteOAuthAppGrant(clientId: String, clientSecret: String, accessToken: String): Boolean {
        val body = JSONObject().apply { put("access_token", accessToken.trim()) }.toString()
        return requestBasic("/applications/${clientId.trim()}/grant", "DELETE", body, clientId.trim(), clientSecret).let { it.code == 204 || it.success }
    }

    suspend fun deleteRepoSelfHostedRunner(context: Context, owner: String, repo: String, runnerId: Long): Boolean =
        request(context, "/repos/$owner/$repo/actions/runners/$runnerId", "DELETE").let { it.code == 204 || it.success }

    suspend fun createRepoRunnerRegistrationToken(context: Context, owner: String, repo: String): GHRunnerToken? {
        val r = request(context, "/repos/$owner/$repo/actions/runners/registration-token", "POST", "{}")
        if (!r.success) return null
        return try {
            val j = JSONObject(r.body)
            GHRunnerToken(j.optString("token"), j.optString("expires_at", ""))
        } catch (e: Exception) { null }
    }

    suspend fun createRepoRunnerRemoveToken(context: Context, owner: String, repo: String): GHRunnerToken? {
        val r = request(context, "/repos/$owner/$repo/actions/runners/remove-token", "POST", "{}")
        if (!r.success) return null
        return try {
            val j = JSONObject(r.body)
            GHRunnerToken(j.optString("token"), j.optString("expires_at", ""))
        } catch (e: Exception) { null }
    }

    suspend fun getRepoActionsPermissions(context: Context, owner: String, repo: String): GHActionsPermissions? {
        val r = request(context, "/repos/$owner/$repo/actions/permissions")
        if (!r.success) return null
        return try {
            val j = JSONObject(r.body)
            GHActionsPermissions(
                enabled = j.optBoolean("enabled", false),
                allowedActions = j.optString("allowed_actions", ""),
                selectedActionsUrl = j.optString("selected_actions_url", "")
            )
        } catch (e: Exception) { null }
    }

    suspend fun setRepoActionsPermissions(context: Context, owner: String, repo: String, enabled: Boolean, allowedActions: String): Boolean {
        val body = JSONObject().apply {
            put("enabled", enabled)
            if (allowedActions.isNotBlank()) put("allowed_actions", allowedActions)
        }.toString()
        return request(context, "/repos/$owner/$repo/actions/permissions", "PUT", body).let { it.code == 204 || it.success }
    }

    suspend fun getRepoActionsWorkflowPermissions(context: Context, owner: String, repo: String): GHWorkflowPermissions? {
        val r = request(context, "/repos/$owner/$repo/actions/permissions/workflow")
        if (!r.success) return null
        return try {
            val j = JSONObject(r.body)
            GHWorkflowPermissions(j.optString("default_workflow_permissions", ""), j.optBoolean("can_approve_pull_request_reviews", false))
        } catch (e: Exception) { null }
    }

    suspend fun setRepoActionsWorkflowPermissions(context: Context, owner: String, repo: String, defaultWorkflowPermissions: String, canApprovePullRequestReviews: Boolean): Boolean {
        val body = JSONObject().apply {
            put("default_workflow_permissions", defaultWorkflowPermissions)
            put("can_approve_pull_request_reviews", canApprovePullRequestReviews)
        }.toString()
        return request(context, "/repos/$owner/$repo/actions/permissions/workflow", "PUT", body).let { it.code == 204 || it.success }
    }

    suspend fun getRepoActionsRetention(context: Context, owner: String, repo: String): GHActionsRetention? {
        val r = request(context, "/repos/$owner/$repo/actions/permissions/artifact-and-log-retention")
        if (!r.success) return null
        return try {
            val j = JSONObject(r.body)
            GHActionsRetention(j.optInt("days", 0))
        } catch (e: Exception) { null }
    }

    suspend fun setRepoActionsRetention(context: Context, owner: String, repo: String, days: Int): Boolean {
        val body = JSONObject().apply { put("days", days) }.toString()
        return request(context, "/repos/$owner/$repo/actions/permissions/artifact-and-log-retention", "PUT", body).let { it.code == 204 || it.success }
    }

    private fun parseArtifacts(body: String): List<GHArtifact> = try {
        val arr = JSONObject(body).optJSONArray("artifacts") ?: JSONArray()
        (0 until arr.length()).map { i -> parseArtifact(arr.getJSONObject(i)) }
    } catch (e: Exception) { emptyList() }

    private fun parseArtifact(j: JSONObject): GHArtifact {
        val workflowRun = j.optJSONObject("workflow_run")
        return GHArtifact(
            id = j.optLong("id"),
            name = j.optString("name"),
            sizeInBytes = j.optLong("size_in_bytes", 0),
            expired = j.optBoolean("expired", false),
            createdAt = j.optString("created_at", ""),
            expiresAt = j.optString("expires_at", ""),
            updatedAt = j.optString("updated_at", ""),
            digest = j.optString("digest", ""),
            workflowRunId = workflowRun?.optLong("id") ?: 0L,
            workflowRunBranch = workflowRun?.optString("head_branch") ?: "",
            workflowRunSha = workflowRun?.optString("head_sha") ?: ""
        )
    }

    private fun parseJobs(body: String): List<GHJob> = try {
        val arr = JSONObject(body).getJSONArray("jobs")
        (0 until arr.length()).map { i ->
            val j = arr.getJSONObject(i)
            val steps = mutableListOf<GHStep>()
            val stepsArr = j.optJSONArray("steps")
            if (stepsArr != null) for (s in 0 until stepsArr.length()) {
                val sj = stepsArr.getJSONObject(s)
                steps.add(GHStep(name = sj.optString("name"), status = sj.optString("status"), conclusion = sj.optString("conclusion", ""), number = sj.optInt("number")))
            }
            GHJob(id = j.optLong("id"), name = j.optString("name"), status = j.optString("status"),
                conclusion = j.optString("conclusion", ""), startedAt = j.optString("started_at", ""),
                completedAt = j.optString("completed_at", ""), steps = steps)
        }
    } catch (e: Exception) { emptyList() }

    private fun parseActionsUsage(body: String): GHActionsUsage? = try {
        val j = JSONObject(body)
        val billable = j.optJSONObject("billable")
        val minutes = mutableMapOf<String, Int>()
        val ms = mutableMapOf<String, Long>()
        billable?.keys()?.forEach { key ->
            val platform = billable.optJSONObject(key)
            minutes[key] = platform?.optInt("total_ms", 0)?.div(60000) ?: 0
            ms[key] = platform?.optLong("total_ms", 0) ?: 0L
        }
        GHActionsUsage(runDurationMs = j.optLong("run_duration_ms", 0), billableMs = ms, billableMinutes = minutes)
    } catch (e: Exception) { null }

    private suspend fun getRedirectLocationOrText(context: Context, url: String): String =
        withContext(Dispatchers.IO) {
            try {
                val token = getToken(context)
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    instanceFollowRedirects = false
                    connectTimeout = 15000
                    readTimeout = 15000
                }
                val code = conn.responseCode
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location != null) "Logs URL: $location" else "Logs: HTTP $code"
            } catch (e: Exception) { "Error: ${e.message}" }
        }

    private fun parseWorkflowRun(j: JSONObject) = GHWorkflowRun(
        id = j.optLong("id"), name = j.optString("name"), status = j.optString("status"),
        conclusion = j.optString("conclusion", ""), branch = j.optString("head_branch", ""),
        event = j.optString("event", ""), createdAt = j.optString("created_at", ""),
        updatedAt = j.optString("updated_at", ""), runNumber = j.optInt("run_number"),
        actor = j.optJSONObject("actor")?.optString("login") ?: "",
        actorAvatar = j.optJSONObject("actor")?.optString("avatar_url") ?: "",
        workflowId = j.optLong("workflow_id"),
        displayTitle = j.optString("display_title", ""),
        headSha = j.optString("head_sha", ""),
        headRepository = j.optJSONObject("head_repository")?.optString("full_name") ?: "",
        runAttempt = j.optInt("run_attempt", 1),
        htmlUrl = j.optString("html_url", ""),
        cancelUrl = j.optString("cancel_url", ""),
        rerunUrl = j.optString("rerun_url", ""),
        checkSuiteId = j.optLong("check_suite_id", 0)
    )

    suspend fun getNotifications(
        context: Context,
        all: Boolean = false,
        participating: Boolean = false,
        since: String? = null
    ): List<GHNotification> = fetchNotifications(
        context = context,
        all = all,
        participating = participating,
        since = since,
        strictErrors = false
    )

    suspend fun listNotifications(
        context: Context,
        all: Boolean = false,
        participating: Boolean = false,
        since: String? = null
    ): List<GHNotification> = fetchNotifications(
        context = context,
        all = all,
        participating = participating,
        since = since,
        strictErrors = true
    )

    private suspend fun fetchNotifications(
        context: Context,
        all: Boolean,
        participating: Boolean,
        since: String?,
        strictErrors: Boolean
    ): List<GHNotification> {
        val sinceQ = if (!since.isNullOrBlank()) "&since=${URLEncoder.encode(since, "UTF-8")}" else ""
        val r = request(context, "/notifications?all=$all&participating=$participating&per_page=50$sinceQ")
        if (!r.success) {
            val rateLimited = r.code == 403 &&
                (r.body.contains("rate limit", ignoreCase = true) ||
                    r.body.contains("API rate limit exceeded", ignoreCase = true))
            if (strictErrors && !rateLimited) {
                throw IOException("GitHub notifications request failed: HTTP ${r.code}")
            }
            return emptyList()
        }
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                val sub = j.optJSONObject("subject")
                val repo = j.optJSONObject("repository")
                val subjectUrl = sub?.optString("url") ?: ""
                val repoHtmlUrl = repo?.optString("html_url") ?: ""
                val htmlUrl = githubApiUrlToWebUrl(subjectUrl).ifBlank { repoHtmlUrl }
                GHNotification(
                    id = j.optString("id"), unread = j.optBoolean("unread", false),
                    reason = j.optString("reason", ""),
                    title = sub?.optString("title") ?: "", type = sub?.optString("type") ?: "",
                    repoName = repo?.optString("full_name") ?: "",
                    updatedAt = j.optString("updated_at", ""),
                    url = subjectUrl,
                    lastReadAt = j.optString("last_read_at", "").takeIf { it.isNotBlank() && it != "null" },
                    subjectUrl = subjectUrl,
                    repositoryUrl = repo?.optString("url") ?: "",
                    htmlUrl = htmlUrl
                )
            }
        } catch (e: Exception) {
            if (strictErrors) throw IOException("Failed to parse GitHub notifications", e)
            emptyList()
        }
    }

    private fun githubApiUrlToWebUrl(apiUrl: String): String {
        if (apiUrl.isBlank()) return ""
        if (apiUrl.startsWith("https://github.com/")) return apiUrl
        return apiUrl
            .replace("https://api.github.com/repos/", "https://github.com/")
            .replace("/pulls/", "/pull/")
    }

    suspend fun markNotificationRead(context: Context, threadId: String): Boolean =
        request(context, "/notifications/threads/$threadId", "PATCH").let { it.code == 205 || it.success }

    /** Alias matching the notifications spec naming. */
    suspend fun markThreadRead(context: Context, threadId: String): Boolean =
        markNotificationRead(context, threadId)

    suspend fun markAllNotificationsRead(context: Context): Boolean =
        request(context, "/notifications", "PUT", "{\"read\":true}").let { it.code == 205 || it.success }

    suspend fun getThreadSubscription(context: Context, threadId: String): GHThreadSubscription {
        val r = request(context, "/notifications/threads/$threadId/subscription", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return GHThreadSubscription(subscribed = false, ignored = false, reason = "", createdAt = "", url = "")
        return try {
            val j = JSONObject(r.body)
            GHThreadSubscription(
                subscribed = j.optBoolean("subscribed", false),
                ignored = j.optBoolean("ignored", false),
                reason = j.optString("reason", ""),
                createdAt = j.optString("created_at", ""),
                url = j.optString("url", "")
            )
        } catch (e: Exception) {
            GHThreadSubscription(subscribed = false, ignored = false, reason = "", createdAt = "", url = "")
        }
    }

    suspend fun setThreadSubscription(context: Context, threadId: String, subscribed: Boolean, ignored: Boolean): Boolean {
        val body = JSONObject().apply {
            put("subscribed", subscribed)
            put("ignored", ignored)
        }.toString()
        return request(context, "/notifications/threads/$threadId/subscription", "PUT", body, extraHeaders = mapOf("Accept" to "application/vnd.github+json")).success
    }

    suspend fun deleteThreadSubscription(context: Context, threadId: String): Boolean =
        request(context, "/notifications/threads/$threadId/subscription", "DELETE", extraHeaders = mapOf("Accept" to "application/vnd.github+json")).let { it.code == 204 || it.success }

    suspend fun isWatching(context: Context, owner: String, repo: String): Boolean {
        val r = request(context, "/repos/$owner/$repo/subscription")
        return r.success && JSONObject(r.body).optBoolean("subscribed", false)
    }

    suspend fun watchRepo(context: Context, owner: String, repo: String): Boolean =
        request(context, "/repos/$owner/$repo/subscription", "PUT", "{\"subscribed\":true}").success

    suspend fun unwatchRepo(context: Context, owner: String, repo: String): Boolean =
        request(context, "/repos/$owner/$repo/subscription", "DELETE").let { it.code == 204 || it.success }

    suspend fun searchCode(context: Context, query: String, owner: String, repo: String): List<GHCodeResult> {
        val q = URLEncoder.encode("$query repo:$owner/$repo", "UTF-8")
        val r = request(context, "/search/code?q=$q&per_page=20")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONObject(r.body).getJSONArray("items")
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHCodeResult(
                    name = j.optString("name"), path = j.optString("path"),
                    sha = j.optString("sha"), htmlUrl = j.optString("html_url", ""),
                    score = j.optDouble("score", 0.0)
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun searchIssuesAdvanced(context: Context, query: String, page: Int = 1): List<GHSearchIssueResult> {
        val q = URLEncoder.encode(query, "UTF-8")
        val r = request(context, "/search/issues?q=$q&sort=updated&order=desc&per_page=30&page=$page", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return emptyList()
        return try {
            val arr = JSONObject(r.body).optJSONArray("items") ?: JSONArray()
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parseSearchIssue) }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun searchCommitsAdvanced(context: Context, query: String, page: Int = 1): List<GHSearchCommitResult> {
        val q = URLEncoder.encode(query, "UTF-8")
        val r = request(context, "/search/commits?q=$q&sort=author-date&order=desc&per_page=30&page=$page", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return emptyList()
        return try {
            val arr = JSONObject(r.body).optJSONArray("items") ?: JSONArray()
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parseSearchCommit) }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun searchTopics(context: Context, query: String, page: Int = 1): List<GHTopicSearchResult> {
        val q = URLEncoder.encode(query, "UTF-8")
        val r = request(context, "/search/topics?q=$q&per_page=30&page=$page", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return emptyList()
        return try {
            val arr = JSONObject(r.body).optJSONArray("items") ?: JSONArray()
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parseTopicSearchResult) }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun searchLabels(context: Context, repositoryFullName: String, query: String, page: Int = 1): List<GHLabelSearchResult> {
        val parts = repositoryFullName.trim().split("/")
        if (parts.size != 2) return emptyList()
        val repositoryId = getRepositoryId(context, parts[0], parts[1])
        if (repositoryId <= 0L) return emptyList()
        val q = URLEncoder.encode(query, "UTF-8")
        val r = request(context, "/search/labels?q=$q&repository_id=$repositoryId&per_page=30&page=$page", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return emptyList()
        return try {
            val arr = JSONObject(r.body).optJSONArray("items") ?: JSONArray()
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { j ->
                    GHLabelSearchResult(
                        name = j.optString("name", ""),
                        color = j.optString("color", ""),
                        description = j.optString("description", ""),
                        repository = repositoryFullName,
                        score = j.optDouble("score", 0.0)
                    )
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    private suspend fun getRepositoryId(context: Context, owner: String, repo: String): Long {
        val r = request(context, "/repos/${URLEncoder.encode(owner, "UTF-8")}/${URLEncoder.encode(repo, "UTF-8")}", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return 0L
        return try { JSONObject(r.body).optLong("id", 0L) } catch (e: Exception) { 0L }
    }

    private fun parseSearchIssue(j: JSONObject): GHSearchIssueResult {
        val user = j.optJSONObject("user")
        val repo = repoNameFromIssueSearch(j)
        return GHSearchIssueResult(
            id = j.optLong("id"),
            number = j.optInt("number"),
            title = j.optString("title", ""),
            body = j.optString("body", ""),
            state = j.optString("state", ""),
            author = user?.optString("login") ?: "",
            avatarUrl = user?.optString("avatar_url") ?: "",
            comments = j.optInt("comments", 0),
            labels = parseLabelNames(j.optJSONArray("labels")),
            createdAt = j.optString("created_at", ""),
            updatedAt = j.optString("updated_at", ""),
            htmlUrl = j.optString("html_url", ""),
            repository = repo,
            isPullRequest = j.has("pull_request"),
            score = j.optDouble("score", 0.0)
        )
    }

    private fun parseSearchCommit(j: JSONObject): GHSearchCommitResult {
        val commit = j.optJSONObject("commit")
        val author = commit?.optJSONObject("author")
        val repo = j.optJSONObject("repository")
        val user = j.optJSONObject("author") ?: j.optJSONObject("committer")
        return GHSearchCommitResult(
            sha = j.optString("sha", ""),
            message = commit?.optString("message", "") ?: "",
            author = user?.optString("login") ?: author?.optString("name") ?: "",
            avatarUrl = user?.optString("avatar_url") ?: "",
            date = author?.optString("date", "") ?: "",
            repository = repo?.optString("full_name", "") ?: "",
            htmlUrl = j.optString("html_url", ""),
            score = j.optDouble("score", 0.0)
        )
    }

    private fun parseTopicSearchResult(j: JSONObject): GHTopicSearchResult =
        GHTopicSearchResult(
            name = j.optString("name", ""),
            displayName = j.optString("display_name", ""),
            shortDescription = j.optString("short_description", ""),
            description = j.optString("description", ""),
            createdBy = j.optString("created_by", ""),
            released = j.optString("released", ""),
            updatedAt = j.optString("updated_at", ""),
            featured = j.optBoolean("featured", false),
            curated = j.optBoolean("curated", false),
            score = j.optDouble("score", 0.0),
            aliases = parseTopicNameArray(j.optJSONArray("aliases")),
            related = parseTopicNameArray(j.optJSONArray("related"))
        )

    private fun repoNameFromIssueSearch(j: JSONObject): String {
        val repositoryUrl = j.optString("repository_url", "")
        if (repositoryUrl.contains("/repos/")) return repositoryUrl.substringAfter("/repos/")
        val html = j.optString("html_url", "").removePrefix("https://github.com/")
        val parts = html.split("/")
        return if (parts.size >= 2) "${parts[0]}/${parts[1]}" else ""
    }

    private fun parseLabelNames(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }
        }
    }

    private fun parseTopicNameArray(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val item = arr.opt(i)
            when (item) {
                is JSONObject -> item.optString("name").takeIf { it.isNotBlank() }
                else -> item?.toString()?.takeIf { it.isNotBlank() && it != "null" }
            }
        }
    }

    suspend fun getUserProfile(context: Context, username: String): GHUserProfile? {
        val r = request(context, "/users/$username")
        if (!r.success) return null
        return try {
            val j = JSONObject(r.body)
            GHUserProfile(
                login = j.optString("login"), name = j.optString("name", ""),
                avatarUrl = j.optString("avatar_url", ""), bio = j.optString("bio", ""),
                company = j.optString("company", ""), location = j.optString("location", ""),
                blog = j.optString("blog", ""), publicRepos = j.optInt("public_repos", 0),
                followers = j.optInt("followers", 0), following = j.optInt("following", 0),
                createdAt = j.optString("created_at", "")
            )
        } catch (e: Exception) { null }
    }

    suspend fun getUserRepos(context: Context, username: String): List<GHRepo> {
        val r = request(context, "/users/$username/repos?sort=updated&per_page=30")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { parseRepo(arr.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun isFollowing(context: Context, username: String): Boolean =
        request(context, "/user/following/$username").code == 204

    suspend fun followUser(context: Context, username: String): Boolean =
        request(context, "/user/following/$username", "PUT").let { it.code == 204 || it.success }

    suspend fun unfollowUser(context: Context, username: String): Boolean =
        request(context, "/user/following/$username", "DELETE").let { it.code == 204 || it.success }

    suspend fun getStarredRepos(context: Context, page: Int = 1): List<GHRepo> {
        val r = request(context, "/user/starred?sort=created&per_page=30&page=$page")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { parseRepo(arr.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getOrganizations(context: Context): List<GHOrg> {
        val r = request(context, "/user/orgs?per_page=30")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHOrg(login = j.optString("login"), avatarUrl = j.optString("avatar_url", ""),
                    description = j.optString("description", ""))
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getOrgRepos(context: Context, org: String): List<GHRepo> {
        val r = request(context, "/orgs/$org/repos?sort=updated&per_page=30")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { parseRepo(arr.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
    }

    // ═══════════════════════════════════
    // GitHub Apps / Installations
    // ═══════════════════════════════════

    suspend fun getAppInstallations(context: Context, page: Int = 1, perPage: Int = 30): GHAppInstallationsPage {
        val r = request(
            context,
            "/user/installations?per_page=$perPage&page=$page",
            extraHeaders = mapOf("Accept" to "application/vnd.github+json")
        )
        if (!r.success) return GHAppInstallationsPage(error = apiErrorMessage(r), code = r.code)
        return try {
            val root = JSONObject(r.body)
            val arr = root.optJSONArray("installations") ?: JSONArray()
            GHAppInstallationsPage(
                installations = (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parseAppInstallation) },
                totalCount = root.optInt("total_count", arr.length()),
                code = r.code
            )
        } catch (e: Exception) {
            GHAppInstallationsPage(error = e.message ?: "Parse error", code = r.code)
        }
    }

    suspend fun getAppInstallationRepositories(context: Context, installationId: Long, page: Int = 1, perPage: Int = 30): GHAppInstallationReposPage {
        val r = request(
            context,
            "/user/installations/$installationId/repositories?per_page=$perPage&page=$page",
            extraHeaders = mapOf("Accept" to "application/vnd.github+json")
        )
        if (!r.success) return GHAppInstallationReposPage(error = apiErrorMessage(r), code = r.code)
        return try {
            val root = JSONObject(r.body)
            val arr = root.optJSONArray("repositories") ?: JSONArray()
            GHAppInstallationReposPage(
                repositories = (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parseRepo) },
                totalCount = root.optInt("total_count", arr.length()),
                code = r.code
            )
        } catch (e: Exception) {
            GHAppInstallationReposPage(error = e.message ?: "Parse error", code = r.code)
        }
    }

    suspend fun addRepositoryToAppInstallation(context: Context, installationId: Long, repositoryId: Long): GHActionResult {
        val r = request(
            context,
            "/user/installations/$installationId/repositories/$repositoryId",
            "PUT",
            extraHeaders = mapOf("Accept" to "application/vnd.github+json")
        )
        return GHActionResult(r.code == 204 || r.success, r.code, if (r.success || r.code == 204) "Repository added" else apiErrorMessage(r))
    }

    suspend fun removeRepositoryFromAppInstallation(context: Context, installationId: Long, repositoryId: Long): GHActionResult {
        val r = request(
            context,
            "/user/installations/$installationId/repositories/$repositoryId",
            "DELETE",
            extraHeaders = mapOf("Accept" to "application/vnd.github+json")
        )
        return GHActionResult(r.code == 204 || r.success, r.code, if (r.success || r.code == 204) "Repository removed" else apiErrorMessage(r))
    }

    suspend fun getLabels(context: Context, owner: String, repo: String): List<GHLabel> {
        val r = request(context, "/repos/$owner/$repo/labels?per_page=50")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHLabel(name = j.optString("name"), color = j.optString("color", ""), description = j.optString("description", ""))
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun createLabel(context: Context, owner: String, repo: String, name: String, color: String, description: String = ""): Boolean {
        val body = JSONObject().apply { put("name", name); put("color", color.removePrefix("#")); put("description", description) }.toString()
        return request(context, "/repos/$owner/$repo/labels", "POST", body).success
    }

    suspend fun deleteLabel(context: Context, owner: String, repo: String, name: String): Boolean =
        request(context, "/repos/$owner/$repo/labels/${URLEncoder.encode(name, "UTF-8")}", "DELETE").let { it.code == 204 || it.success }

    suspend fun addLabelsToIssue(context: Context, owner: String, repo: String, issueNumber: Int, labels: List<String>): Boolean {
        val body = JSONObject().apply { put("labels", JSONArray(labels)) }.toString()
        return request(context, "/repos/$owner/$repo/issues/$issueNumber/labels", "POST", body).success
    }

    suspend fun getMilestones(context: Context, owner: String, repo: String): List<GHMilestone> {
        val r = request(context, "/repos/$owner/$repo/milestones?per_page=30")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHMilestone(
                    number = j.optInt("number"), title = j.optString("title"),
                    description = j.optString("description", ""), state = j.optString("state"),
                    openIssues = j.optInt("open_issues"), closedIssues = j.optInt("closed_issues"),
                    dueOn = j.optString("due_on", "")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun createMilestone(context: Context, owner: String, repo: String, title: String, description: String = "", dueOn: String? = null): Boolean {
        val body = JSONObject().apply {
            put("title", title); put("description", description)
            if (dueOn != null) put("due_on", dueOn)
        }.toString()
        return request(context, "/repos/$owner/$repo/milestones", "POST", body).success
    }

    suspend fun getAssignees(context: Context, owner: String, repo: String): List<GHUserLite> {
        val r = request(context, "/repos/$owner/$repo/assignees")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i -> val j = arr.getJSONObject(i)
                GHUserLite(j.optString("login"), j.optString("avatar_url", ""))
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun updateIssueMeta(context: Context, owner: String, repo: String, issueNumber: Int, labels: List<String>, assignees: List<String>, milestoneNumber: Int?, clearMilestone: Boolean = false): Boolean {
        val body = JSONObject().apply {
            put("labels", JSONArray(labels))
            put("assignees", JSONArray(assignees))
            if (clearMilestone) put("milestone", JSONObject.NULL)
            else if (milestoneNumber != null) put("milestone", milestoneNumber)
        }.toString()
        return request(context, "/repos/$owner/$repo/issues/$issueNumber", "PATCH", body).success
    }

    suspend fun submitPullRequestReview(context: Context, owner: String, repo: String, number: Int, event: String, body: String = ""): Boolean {
        val json = JSONObject().apply { put("event", event); if (body.isNotBlank()) put("body", body) }.toString()
        return request(context, "/repos/$owner/$repo/pulls/$number/reviews", "POST", json).success
    }

    suspend fun getPullRequestFiles(context: Context, owner: String, repo: String, number: Int): List<GHPullFile> {
        val r = request(context, "/repos/$owner/$repo/pulls/$number/files?per_page=100")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i -> val j = arr.getJSONObject(i)
                GHPullFile(j.optString("filename"), j.optString("status"), j.optInt("additions", 0), j.optInt("deletions", 0), j.optString("patch", ""))
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun uploadDirectory(
        context: Context, owner: String, repo: String, branch: String,
        localDir: java.io.File, repoBasePath: String = "", message: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Boolean {
        val allFiles = mutableListOf<Pair<String, ByteArray>>()
        collectFiles(localDir, localDir, repoBasePath, allFiles)
        if (allFiles.isEmpty()) return false
        return uploadMultipleFiles(context, owner, repo, branch, allFiles, message, onProgress)
    }

    private fun collectFiles(root: java.io.File, current: java.io.File, basePath: String, result: MutableList<Pair<String, ByteArray>>) {
        current.listFiles()?.forEach { f ->
            val rel = if (basePath.isNotBlank()) "$basePath/${f.name}" else f.name
            if (f.isDirectory) collectFiles(root, f, rel, result)
            else if (f.length() < 50 * 1024 * 1024) {
                try { result.add(rel to f.readBytes()) } catch (_: Exception) {}
            }
        }
    }


    suspend fun getCurrentUserProfile(context: Context): GHUserProfile? {
        val login = getCachedUser(context)?.login ?: getUser(context)?.login ?: return null
        return getUserProfile(context, login)
    }

    suspend fun updateCurrentUserProfile(
        context: Context,
        name: String,
        bio: String,
        company: String,
        location: String,
        blog: String
    ): Boolean {
        val body = JSONObject().apply {
            put("name", name)
            put("bio", bio)
            put("company", company)
            put("location", location)
            put("blog", blog)
        }.toString()
        val ok = request(context, "/user", "PATCH", body).success
        if (ok) getUser(context)
        return ok
    }

    suspend fun getEmailEntries(context: Context): List<GHEmailEntry> {
        val r = request(context, "/user/emails")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHEmailEntry(
                    email = j.optString("email"),
                    primary = j.optBoolean("primary", false),
                    verified = j.optBoolean("verified", false),
                    visibility = j.optString("visibility", "")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun addEmailAddress(context: Context, email: String): Boolean {
        val body = JSONArray().put(email).toString()
        return request(context, "/user/emails", "POST", body).success
    }

    suspend fun deleteEmailAddress(context: Context, email: String): Boolean {
        val body = JSONArray().put(email).toString()
        return request(context, "/user/emails", "DELETE", body).success
    }

    suspend fun setEmailVisibility(context: Context, visibility: String): Boolean {
        val body = JSONObject().apply { put("visibility", visibility) }.toString()
        return request(context, "/user/email/visibility", "PATCH", body).success
    }

    suspend fun getSshKeysNative(context: Context): List<GHUserKeyEntry> {
        val r = request(context, "/user/keys")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHUserKeyEntry(j.optLong("id"), j.optString("title"), j.optString("key"), j.optString("created_at", ""), "ssh")
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getSshSigningKeysNative(context: Context): List<GHUserKeyEntry> {
        val r = request(context, "/user/ssh_signing_keys")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHUserKeyEntry(j.optLong("id"), j.optString("title"), j.optString("key"), j.optString("created_at", ""), "ssh_signing")
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getGpgKeysNative(context: Context): List<GHUserKeyEntry> {
        val r = request(context, "/user/gpg_keys")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHUserKeyEntry(j.optLong("id"), j.optString("name"), j.optString("public_key"), j.optString("created_at", ""), "gpg")
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun addSshKeyNative(context: Context, title: String, key: String): Boolean {
        val body = JSONObject().apply { put("title", title); put("key", key) }.toString()
        return request(context, "/user/keys", "POST", body).success
    }

    suspend fun addSshSigningKeyNative(context: Context, title: String, key: String): Boolean {
        val body = JSONObject().apply { put("title", title); put("key", key) }.toString()
        return request(context, "/user/ssh_signing_keys", "POST", body).success
    }

    suspend fun addGpgKeyNative(context: Context, armoredKey: String): Boolean {
        val body = JSONObject().apply { put("armored_public_key", armoredKey) }.toString()
        return request(context, "/user/gpg_keys", "POST", body).success
    }

    suspend fun deleteSshKeyNative(context: Context, id: Long): Boolean =
        request(context, "/user/keys/$id", "DELETE").let { it.code == 204 || it.success }

    suspend fun deleteSshSigningKeyNative(context: Context, id: Long): Boolean =
        request(context, "/user/ssh_signing_keys/$id", "DELETE").let { it.code == 204 || it.success }

    suspend fun deleteGpgKeyNative(context: Context, id: Long): Boolean =
        request(context, "/user/gpg_keys/$id", "DELETE").let { it.code == 204 || it.success }

    suspend fun getSocialAccountsNative(context: Context): List<GHSocialAccountEntry> {
        val r = request(context, "/user/social_accounts")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHSocialAccountEntry(j.optString("provider", "social"), j.optString("url"))
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun addSocialAccountNative(context: Context, url: String): Boolean {
        val body = JSONArray().put(url).toString()
        return request(context, "/user/social_accounts", "POST", body).success
    }

    suspend fun deleteSocialAccountNative(context: Context, url: String): Boolean {
        val body = JSONArray().put(url).toString()
        return request(context, "/user/social_accounts", "DELETE", body).success
    }

    suspend fun getFollowersNative(context: Context): List<GHFollowerEntry> {
        val r = request(context, "/user/followers?per_page=100")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHFollowerEntry(j.optString("login"), j.optString("avatar_url", ""))
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getFollowingNative(context: Context): List<GHFollowerEntry> {
        val r = request(context, "/user/following?per_page=100")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHFollowerEntry(j.optString("login"), j.optString("avatar_url", ""))
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getBlockedUsersNative(context: Context): List<GHBlockedEntry> {
        val r = request(context, "/user/blocks?per_page=100")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHBlockedEntry(j.optString("login"), j.optString("avatar_url", ""))
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun blockUserNative(context: Context, username: String): Boolean =
        request(context, "/user/blocks/${URLEncoder.encode(username, "UTF-8")}", "PUT", "").let { it.code == 204 || it.success }

    suspend fun unblockUserNative(context: Context, username: String): Boolean =
        request(context, "/user/blocks/${URLEncoder.encode(username, "UTF-8")}", "DELETE").let { it.code == 204 || it.success }

    suspend fun getInteractionLimitNative(context: Context): GHInteractionLimitEntry? {
        val r = request(context, "/user/interaction-limits")
        if (!r.success || r.body.isBlank()) return null
        return try {
            val j = JSONObject(r.body)
            GHInteractionLimitEntry(j.optString("limit"), j.optString("expires_at", "").ifBlank { null })
        } catch (_: Exception) { null }
    }

    suspend fun setInteractionLimitNative(context: Context, limit: String, expiry: String): Boolean {
        val body = JSONObject().apply {
            put("limit", limit)
            put("expiry", expiry)
        }.toString()
        return request(context, "/user/interaction-limits", "PUT", body).success
    }

    suspend fun removeInteractionLimitNative(context: Context): Boolean =
        request(context, "/user/interaction-limits", "DELETE").let { it.code == 204 || it.success }

    suspend fun getRateLimitSummaryNative(context: Context): String {
        val r = request(context, "/rate_limit")
        if (!r.success || r.body.isBlank()) return "Unavailable"
        return try {
            val core = JSONObject(r.body).getJSONObject("resources").getJSONObject("core")
            val remaining = core.optInt("remaining")
            val limit = core.optInt("limit")
            val reset = core.optLong("reset")
            "$remaining / $limit · reset $reset"
        } catch (_: Exception) { "Unavailable" }
    }

    // ═══════════════════════════════════
    // Repository Settings
    // ═══════════════════════════════════

    suspend fun getRepoSettings(context: Context, owner: String, repo: String): GHRepoSettings? {
        val r = request(context, "/repos/$owner/$repo")
        if (!r.success) return null
        return try {
            val j = JSONObject(r.body)
            GHRepoSettings(
                name = j.optString("name"),
                description = j.optString("description", ""),
                homepage = j.optString("homepage", ""),
                isPrivate = j.optBoolean("private", false),
                hasIssues = j.optBoolean("has_issues", true),
                hasProjects = j.optBoolean("has_projects", true),
                hasWiki = j.optBoolean("has_wiki", true),
                hasDiscussions = j.optBoolean("has_discussions", false),
                allowForking = j.optBoolean("allow_forking", true),
                isTemplate = j.optBoolean("is_template", false),
                archived = j.optBoolean("archived", false),
                disabled = j.optBoolean("disabled", false),
                defaultBranch = j.optString("default_branch", "main"),
                topics = mutableListOf<String>().apply {
                    val arr = j.optJSONArray("topics")
                    if (arr != null) for (i in 0 until arr.length()) add(arr.getString(i))
                },
                allowMergeCommit = j.optBoolean("allow_merge_commit", true),
                allowSquashMerge = j.optBoolean("allow_squash_merge", true),
                allowRebaseMerge = j.optBoolean("allow_rebase_merge", true),
                deleteBranchOnMerge = j.optBoolean("delete_branch_on_merge", false)
            )
        } catch (e: Exception) { null }
    }

    suspend fun updateRepoSettings(
        context: Context, owner: String, repo: String,
        name: String? = null,
        description: String? = null,
        homepage: String? = null,
        isPrivate: Boolean? = null,
        hasIssues: Boolean? = null,
        hasProjects: Boolean? = null,
        hasWiki: Boolean? = null,
        hasDiscussions: Boolean? = null,
        allowForking: Boolean? = null,
        isTemplate: Boolean? = null,
        archived: Boolean? = null,
        topics: List<String>? = null,
        allowMergeCommit: Boolean? = null,
        allowSquashMerge: Boolean? = null,
        allowRebaseMerge: Boolean? = null,
        deleteBranchOnMerge: Boolean? = null
    ): Boolean {
        val body = JSONObject().apply {
            if (name != null) put("name", name)
            if (description != null) put("description", description)
            if (homepage != null) put("homepage", homepage)
            if (isPrivate != null) put("private", isPrivate)
            if (hasIssues != null) put("has_issues", hasIssues)
            if (hasProjects != null) put("has_projects", hasProjects)
            if (hasWiki != null) put("has_wiki", hasWiki)
            if (hasDiscussions != null) put("has_discussions", hasDiscussions)
            if (allowForking != null) put("allow_forking", allowForking)
            if (isTemplate != null) put("is_template", isTemplate)
            if (archived != null) put("archived", archived)
            if (topics != null) put("topics", JSONArray(topics))
            if (allowMergeCommit != null) put("allow_merge_commit", allowMergeCommit)
            if (allowSquashMerge != null) put("allow_squash_merge", allowSquashMerge)
            if (allowRebaseMerge != null) put("allow_rebase_merge", allowRebaseMerge)
            if (deleteBranchOnMerge != null) put("delete_branch_on_merge", deleteBranchOnMerge)
        }.toString()
        return request(context, "/repos/$owner/$repo", "PATCH", body).success
    }

    suspend fun getRepoTopics(context: Context, owner: String, repo: String): List<String> {
        val r = request(context, "/repos/$owner/$repo/topics")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONObject(r.body).optJSONArray("names") ?: return emptyList()
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getRepoTags(context: Context, owner: String, repo: String, page: Int = 1): List<GHTag> {
        val r = request(context, "/repos/$owner/$repo/tags?per_page=50&page=$page")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                val commit = j.optJSONObject("commit")
                GHTag(
                    name = j.optString("name"),
                    zipballUrl = j.optString("zipball_url", ""),
                    tarballUrl = j.optString("tarball_url", ""),
                    commitSha = commit?.optString("sha", "") ?: "",
                    commitUrl = commit?.optString("url", "") ?: ""
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun replaceRepoTopics(context: Context, owner: String, repo: String, topics: List<String>): Boolean {
        val body = JSONObject().apply { put("names", JSONArray(topics)) }.toString()
        return request(context, "/repos/$owner/$repo/topics", "PUT", body).success
    }

    suspend fun transferRepo(context: Context, owner: String, repo: String, newOwner: String, newName: String? = null): Boolean {
        val body = JSONObject().apply {
            put("new_owner", newOwner)
            if (newName != null) put("new_name", newName)
        }.toString()
        return request(context, "/repos/$owner/$repo/transfer", "POST", body).success
    }

    suspend fun mergeBranch(
        context: Context,
        owner: String,
        repo: String,
        base: String,
        head: String,
        commitMessage: String? = null
    ): Boolean {
        val body = JSONObject().apply {
            put("base", base)
            put("head", head)
            if (!commitMessage.isNullOrBlank()) put("commit_message", commitMessage)
        }.toString()
        return request(context, "/repos/$owner/$repo/merges", "POST", body).success
    }

    suspend fun renameBranch(context: Context, owner: String, repo: String, branch: String, newName: String): Boolean {
        val encodedBranch = URLEncoder.encode(branch, "UTF-8")
        val body = JSONObject().apply { put("new_name", newName) }.toString()
        return request(context, "/repos/$owner/$repo/branches/$encodedBranch/rename", "POST", body).success
    }

    // ═══════════════════════════════════
    // Branch Protection
    // ═══════════════════════════════════

    suspend fun getBranchProtection(context: Context, owner: String, repo: String, branch: String): GHBranchProtection? {
        val encodedBranch = URLEncoder.encode(branch, "UTF-8")
        val r = request(context, "/repos/$owner/$repo/branches/$encodedBranch/protection")
        if (!r.success) return null
        return try {
            val j = JSONObject(r.body)
            val requiredStatusChecks = j.optJSONObject("required_status_checks")
            val requiredPRReviews = j.optJSONObject("required_pull_request_reviews")
            val restrictions = j.optJSONObject("restrictions")

            GHBranchProtection(
                enabled = true,
                requiredStatusChecks = if (requiredStatusChecks != null) GHRequiredStatusChecks(
                    strict = requiredStatusChecks.optBoolean("strict", false),
                    contexts = mutableListOf<String>().apply {
                        val arr = requiredStatusChecks.optJSONArray("contexts")
                        if (arr != null) for (i in 0 until arr.length()) add(arr.getString(i))
                    }
                ) else null,
                requiredPRReviews = if (requiredPRReviews != null) GHRequiredPRReviews(
                    requiredApprovingReviewCount = requiredPRReviews.optInt("required_approving_review_count", 1),
                    dismissStaleReviews = requiredPRReviews.optBoolean("dismiss_stale_reviews", false),
                    requireCodeOwnerReviews = requiredPRReviews.optBoolean("require_code_owner_reviews", false)
                ) else null,
                restrictions = if (restrictions != null) GHBranchRestrictions(
                    users = mutableListOf<String>().apply {
                        val arr = restrictions.optJSONArray("users")
                        if (arr != null) for (i in 0 until arr.length()) add(arr.getJSONObject(i).optString("login"))
                    },
                    teams = mutableListOf<String>().apply {
                        val arr = restrictions.optJSONArray("teams")
                        if (arr != null) for (i in 0 until arr.length()) add(arr.getJSONObject(i).optString("slug"))
                    }
                ) else null,
                allowForcePushes = j.optJSONObject("allow_force_pushes")?.optBoolean("enabled") ?: true,
                allowDeletions = j.optJSONObject("allow_deletions")?.optBoolean("enabled") ?: true,
                requiredConversationResolution = j.optJSONObject("required_conversation_resolution")?.optBoolean("enabled") ?: false,
                enforceAdmins = j.optJSONObject("enforce_admins")?.optBoolean("enabled") ?: false,
                requiredSignatures = j.optJSONObject("required_signatures")?.optBoolean("enabled") ?: false
            )
        } catch (e: Exception) { null }
    }

    suspend fun getBranchRequiredSignatures(context: Context, owner: String, repo: String, branch: String): Boolean {
        val encodedBranch = URLEncoder.encode(branch, "UTF-8")
        val r = request(context, "/repos/$owner/$repo/branches/$encodedBranch/protection/required_signatures")
        if (!r.success) return false
        return try { JSONObject(r.body).optBoolean("enabled", true) } catch (e: Exception) { true }
    }

    suspend fun enableBranchRequiredSignatures(context: Context, owner: String, repo: String, branch: String): Boolean {
        val encodedBranch = URLEncoder.encode(branch, "UTF-8")
        return request(context, "/repos/$owner/$repo/branches/$encodedBranch/protection/required_signatures", "POST", "{}").success
    }

    suspend fun disableBranchRequiredSignatures(context: Context, owner: String, repo: String, branch: String): Boolean {
        val encodedBranch = URLEncoder.encode(branch, "UTF-8")
        return request(context, "/repos/$owner/$repo/branches/$encodedBranch/protection/required_signatures", "DELETE").let { it.code == 204 || it.success }
    }

    suspend fun updateBranchProtection(
        context: Context, owner: String, repo: String, branch: String,
        requiredStatusChecks: GHRequiredStatusChecks? = null,
        requiredPRReviews: GHRequiredPRReviews? = null,
        restrictions: GHBranchRestrictions? = null,
        allowForcePushes: Boolean? = null,
        allowDeletions: Boolean? = null,
        requiredConversationResolution: Boolean? = null,
        enforceAdmins: Boolean? = null
    ): Boolean {
        val encodedBranch = URLEncoder.encode(branch, "UTF-8")
        val body = JSONObject().apply {
            if (requiredStatusChecks != null) {
                put("required_status_checks", JSONObject().apply {
                    put("strict", requiredStatusChecks.strict)
                    put("contexts", JSONArray(requiredStatusChecks.contexts))
                })
            } else {
                put("required_status_checks", JSONObject.NULL)
            }
            if (requiredPRReviews != null) {
                put("required_pull_request_reviews", JSONObject().apply {
                    put("required_approving_review_count", requiredPRReviews.requiredApprovingReviewCount)
                    put("dismiss_stale_reviews", requiredPRReviews.dismissStaleReviews)
                    put("require_code_owner_reviews", requiredPRReviews.requireCodeOwnerReviews)
                })
            } else {
                put("required_pull_request_reviews", JSONObject.NULL)
            }
            if (restrictions != null) {
                put("restrictions", JSONObject().apply {
                    put("users", JSONArray(restrictions.users))
                    put("teams", JSONArray(restrictions.teams))
                })
            } else {
                put("restrictions", JSONObject.NULL)
            }
            if (allowForcePushes != null) put("allow_force_pushes", JSONObject().apply { put("enabled", allowForcePushes) })
            if (allowDeletions != null) put("allow_deletions", JSONObject().apply { put("enabled", allowDeletions) })
            if (requiredConversationResolution != null) put("required_conversation_resolution", JSONObject().apply { put("enabled", requiredConversationResolution) })
            if (enforceAdmins != null) put("enforce_admins", JSONObject().apply { put("enabled", enforceAdmins) })
        }.toString()
        return request(context, "/repos/$owner/$repo/branches/$encodedBranch/protection", "PUT", body).success
    }

    suspend fun deleteBranchProtection(context: Context, owner: String, repo: String, branch: String): Boolean {
        val encodedBranch = URLEncoder.encode(branch, "UTF-8")
        return request(context, "/repos/$owner/$repo/branches/$encodedBranch/protection", "DELETE").let { it.code == 204 || it.success }
    }

    // ═══════════════════════════════════
    // Collaborators
    // ═══════════════════════════════════

    suspend fun getCollaborators(context: Context, owner: String, repo: String): List<GHCollaborator> {
        val r = request(context, "/repos/$owner/$repo/collaborators?per_page=100")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                val perms = j.optJSONObject("permissions")
                GHCollaborator(
                    login = j.optString("login"),
                    avatarUrl = j.optString("avatar_url", ""),
                    role = perms?.let {
                        when {
                            it.optBoolean("admin", false) -> "admin"
                            it.optBoolean("maintain", false) -> "maintain"
                            it.optBoolean("push", false) -> "push"
                            it.optBoolean("triage", false) -> "triage"
                            it.optBoolean("pull", false) -> "pull"
                            else -> "pull"
                        }
                    } ?: "pull"
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun addCollaborator(context: Context, owner: String, repo: String, username: String, permission: String = "push"): Boolean {
        val body = JSONObject().apply { put("permission", permission) }.toString()
        return request(context, "/repos/$owner/$repo/collaborators/${URLEncoder.encode(username, "UTF-8")}", "PUT", body).let { it.code == 201 || it.code == 204 || it.success }
    }

    suspend fun removeCollaborator(context: Context, owner: String, repo: String, username: String): Boolean =
        request(context, "/repos/$owner/$repo/collaborators/${URLEncoder.encode(username, "UTF-8")}", "DELETE").let { it.code == 204 || it.success }

    suspend fun updateCollaboratorPermission(context: Context, owner: String, repo: String, username: String, permission: String): Boolean {
        val body = JSONObject().apply { put("permission", permission) }.toString()
        return request(context, "/repos/$owner/$repo/collaborators/${URLEncoder.encode(username, "UTF-8")}", "PUT", body).success
    }

    suspend fun getRepoInvitations(context: Context, owner: String, repo: String): List<GHRepoInvitation> {
        val r = request(context, "/repos/$owner/$repo/invitations?per_page=100")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHRepoInvitation(
                    id = j.optLong("id"),
                    invitee = j.optJSONObject("invitee")?.optString("login") ?: "",
                    inviter = j.optJSONObject("inviter")?.optString("login") ?: "",
                    permissions = j.optString("permissions", ""),
                    createdAt = j.optString("created_at", ""),
                    expired = j.optBoolean("expired", false),
                    htmlUrl = j.optString("html_url", "")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun updateRepoInvitation(context: Context, owner: String, repo: String, invitationId: Long, permission: String): Boolean {
        val body = JSONObject().apply { put("permissions", permission) }.toString()
        return request(context, "/repos/$owner/$repo/invitations/$invitationId", "PATCH", body).success
    }

    suspend fun deleteRepoInvitation(context: Context, owner: String, repo: String, invitationId: Long): Boolean =
        request(context, "/repos/$owner/$repo/invitations/$invitationId", "DELETE").let { it.code == 204 || it.success }

    // ═══════════════════════════════════
    // Repository Teams
    // ═══════════════════════════════════

    suspend fun getRepoTeams(context: Context, owner: String, repo: String): List<GHRepoTeam> {
        val r = request(context, "/repos/$owner/$repo/teams?per_page=100")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i -> parseRepoTeam(arr.getJSONObject(i)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getOrgTeams(context: Context, org: String): List<GHOrgTeam> {
        val r = request(context, "/orgs/${URLEncoder.encode(org, "UTF-8")}/teams?per_page=100")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHOrgTeam(
                    id = j.optLong("id"),
                    name = j.optString("name"),
                    slug = j.optString("slug"),
                    description = j.optString("description", ""),
                    privacy = j.optString("privacy", ""),
                    permission = normalizeRepoTeamPermission(j.optString("permission", "")),
                    membersCount = j.optInt("members_count", 0),
                    reposCount = j.optInt("repos_count", 0),
                    htmlUrl = j.optString("html_url", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addRepoTeam(context: Context, org: String, teamSlug: String, owner: String, repo: String, permission: String): Boolean {
        val body = JSONObject().apply { put("permission", normalizeRepoTeamPermission(permission)) }.toString()
        val encodedTeam = URLEncoder.encode(teamSlug, "UTF-8")
        return request(context, "/orgs/${URLEncoder.encode(org, "UTF-8")}/teams/$encodedTeam/repos/$owner/$repo", "PUT", body).let { it.code == 204 || it.success }
    }

    suspend fun updateRepoTeamPermission(context: Context, org: String, teamSlug: String, owner: String, repo: String, permission: String): Boolean =
        addRepoTeam(context, org, teamSlug, owner, repo, permission)

    suspend fun removeRepoTeam(context: Context, org: String, teamSlug: String, owner: String, repo: String): Boolean {
        val encodedTeam = URLEncoder.encode(teamSlug, "UTF-8")
        return request(context, "/orgs/${URLEncoder.encode(org, "UTF-8")}/teams/$encodedTeam/repos/$owner/$repo", "DELETE").let { it.code == 204 || it.success }
    }

    private fun parseRepoTeam(j: JSONObject): GHRepoTeam {
        val permissions = j.optJSONObject("permissions")
        val permission = when {
            permissions?.optBoolean("admin", false) == true -> "admin"
            permissions?.optBoolean("maintain", false) == true -> "maintain"
            permissions?.optBoolean("push", false) == true -> "push"
            permissions?.optBoolean("triage", false) == true -> "triage"
            permissions?.optBoolean("pull", false) == true -> "pull"
            else -> normalizeRepoTeamPermission(j.optString("permission", "pull"))
        }
        val org = j.optJSONObject("organization")
        return GHRepoTeam(
            id = j.optLong("id"),
            name = j.optString("name"),
            slug = j.optString("slug"),
            description = j.optString("description", ""),
            privacy = j.optString("privacy", ""),
            permission = permission,
            membersCount = j.optInt("members_count", 0),
            reposCount = j.optInt("repos_count", 0),
            htmlUrl = j.optString("html_url", ""),
            organization = org?.optString("login", "") ?: ""
        )
    }

    private fun normalizeRepoTeamPermission(permission: String): String = when (permission.lowercase()) {
        "read", "pull" -> "pull"
        "write", "push" -> "push"
        "triage", "maintain", "admin" -> permission.lowercase()
        else -> "pull"
    }

    // ═══════════════════════════════════
    // PR Review Comments
    // ═══════════════════════════════════

    suspend fun getPullRequestReviewComments(context: Context, owner: String, repo: String, pullNumber: Int): List<GHReviewComment> {
        val r = request(context, "/repos/$owner/$repo/pulls/$pullNumber/comments?per_page=100")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHReviewComment(
                    id = j.optLong("id"),
                    body = j.optString("body"),
                    path = j.optString("path"),
                    line = j.optInt("line", 0),
                    originalLine = j.optInt("original_line", 0),
                    diffHunk = j.optString("diff_hunk", ""),
                    author = j.optJSONObject("user")?.optString("login") ?: "",
                    avatarUrl = j.optJSONObject("user")?.optString("avatar_url") ?: "",
                    createdAt = j.optString("created_at", ""),
                    inReplyToId = j.optLong("in_reply_to_id", 0L).takeIf { it > 0 }
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun createPullRequestReviewComment(
        context: Context, owner: String, repo: String, pullNumber: Int,
        body: String, path: String, line: Int, side: String = "RIGHT",
        inReplyToId: Long? = null
    ): Boolean {
        val json = JSONObject().apply {
            put("body", body)
            put("path", path)
            put("line", line)
            put("side", side)
            if (inReplyToId != null) put("in_reply_to", inReplyToId)
        }.toString()
        return request(context, "/repos/$owner/$repo/pulls/$pullNumber/comments", "POST", json).success
    }

    suspend fun updatePullRequestReviewComment(context: Context, owner: String, repo: String, commentId: Long, body: String): Boolean {
        val json = JSONObject().apply { put("body", body) }.toString()
        return request(context, "/repos/$owner/$repo/pulls/comments/$commentId", "PATCH", json).success
    }

    suspend fun deletePullRequestReviewComment(context: Context, owner: String, repo: String, commentId: Long): Boolean =
        request(context, "/repos/$owner/$repo/pulls/comments/$commentId", "DELETE").let { it.code == 204 || it.success }

    // ═══════════════════════════════════
    // PR Check Runs
    // ═══════════════════════════════════

    suspend fun getPullRequestCheckRuns(context: Context, owner: String, repo: String, ref: String): List<GHCheckRun> {
        val encodedRef = URLEncoder.encode(ref, "UTF-8")
        val r = request(context, "/repos/$owner/$repo/commits/$encodedRef/check-runs?per_page=100")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONObject(r.body).getJSONArray("check_runs")
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHCheckRun(
                    id = j.optLong("id"),
                    name = j.optString("name"),
                    status = j.optString("status"),
                    conclusion = j.optString("conclusion", ""),
                    detailsUrl = j.optString("details_url", ""),
                    startedAt = j.optString("started_at", ""),
                    completedAt = j.optString("completed_at", ""),
                    outputTitle = j.optJSONObject("output")?.optString("title") ?: "",
                    outputSummary = j.optJSONObject("output")?.optString("summary") ?: ""
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getPullRequestCheckSuites(context: Context, owner: String, repo: String, ref: String): List<GHCheckSuite> {
        if (ref.isBlank()) return emptyList()
        val encodedRef = URLEncoder.encode(ref, "UTF-8")
        val r = request(context, "/repos/$owner/$repo/commits/$encodedRef/check-suites?per_page=100")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONObject(r.body).optJSONArray("check_suites") ?: JSONArray()
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHCheckSuite(
                    id = j.optLong("id"),
                    status = j.optString("status"),
                    conclusion = j.optString("conclusion", ""),
                    app = j.optJSONObject("app")?.optString("name") ?: "",
                    headBranch = j.optString("head_branch", ""),
                    headSha = j.optString("head_sha", ""),
                    before = j.optString("before", ""),
                    after = j.optString("after", ""),
                    createdAt = j.optString("created_at", ""),
                    updatedAt = j.optString("updated_at", ""),
                    latestCheckRunsCount = j.optInt("latest_check_runs_count", 0)
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    // ═══════════════════════════════════
    // Compare Commits
    // ═══════════════════════════════════

    suspend fun compareCommits(context: Context, owner: String, repo: String, base: String, head: String): GHCompareResult? {
        val encodedBase = URLEncoder.encode(base, "UTF-8")
        val encodedHead = URLEncoder.encode(head, "UTF-8")
        val r = request(context, "/repos/$owner/$repo/compare/$encodedBase...$encodedHead")
        if (!r.success) return null
        return try {
            val j = JSONObject(r.body)
            val filesArr = j.optJSONArray("files")
            val files = mutableListOf<GHDiffFile>()
            if (filesArr != null) for (i in 0 until filesArr.length()) {
                val fj = filesArr.getJSONObject(i)
                files.add(GHDiffFile(
                    filename = fj.optString("filename"),
                    status = fj.optString("status"),
                    additions = fj.optInt("additions"),
                    deletions = fj.optInt("deletions"),
                    patch = fj.optString("patch", "")
                ))
            }
            val commitsArr = j.optJSONArray("commits")
            val commits = mutableListOf<GHCommit>()
            if (commitsArr != null) for (i in 0 until commitsArr.length()) {
                val cj = commitsArr.getJSONObject(i)
                val commit = cj.optJSONObject("commit")
                val author = commit?.optJSONObject("author")
                val user = cj.optJSONObject("author")
                commits.add(GHCommit(
                    sha = cj.optString("sha"),
                    message = commit?.optString("message", "") ?: "",
                    author = user?.optString("login", "")?.ifBlank { author?.optString("name", "") ?: "" } ?: "",
                    date = author?.optString("date", "") ?: "",
                    avatarUrl = user?.optString("avatar_url", "") ?: ""
                ))
            }
            GHCompareResult(
                status = j.optString("status"),
                aheadBy = j.optInt("ahead_by"),
                behindBy = j.optInt("behind_by"),
                totalCommits = j.optInt("total_commits"),
                files = files,
                commits = commits,
                htmlUrl = j.optString("html_url", "")
            )
        } catch (e: Exception) { null }
    }

    fun clearGitHubUserCache(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_USER).apply()
    }

    // ═══════════════════════════════════
    // Issue Reactions
    // ═══════════════════════════════════

    suspend fun getIssueReactions(context: Context, owner: String, repo: String, issueNumber: Int): List<GHReaction> {
        val r = request(context, "/repos/$owner/$repo/issues/$issueNumber/reactions?per_page=100")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHReaction(
                    id = j.optLong("id"),
                    content = j.optString("content"),
                    user = j.optJSONObject("user")?.optString("login") ?: ""
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun addIssueReaction(context: Context, owner: String, repo: String, issueNumber: Int, content: String): Boolean {
        val body = JSONObject().apply { put("content", content) }.toString()
        val r = request(context, "/repos/$owner/$repo/issues/$issueNumber/reactions", "POST", body)
        return r.success
    }

    suspend fun deleteIssueReaction(context: Context, owner: String, repo: String, reactionId: Long): Boolean {
        val r = request(context, "/repos/$owner/$repo/reactions/$reactionId", "DELETE")
        return r.code == 204 || r.success
    }

    suspend fun getIssueCommentReactions(context: Context, owner: String, repo: String, commentId: Long): List<GHReaction> {
        val r = request(context, "/repos/$owner/$repo/issues/comments/$commentId/reactions?per_page=100")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHReaction(
                    id = j.optLong("id"),
                    content = j.optString("content"),
                    user = j.optJSONObject("user")?.optString("login") ?: ""
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun addIssueCommentReaction(context: Context, owner: String, repo: String, commentId: Long, content: String): Boolean {
        val body = JSONObject().apply { put("content", content) }.toString()
        return request(context, "/repos/$owner/$repo/issues/comments/$commentId/reactions", "POST", body).success
    }

    // ═══════════════════════════════════
    // Issue Timeline
    // ═══════════════════════════════════

    suspend fun getIssueTimeline(context: Context, owner: String, repo: String, issueNumber: Int): List<GHTimelineEvent> {
        val r = request(context, "/repos/$owner/$repo/issues/$issueNumber/timeline?per_page=100", extraHeaders = mapOf("Accept" to "application/vnd.github.mockingbird-preview+json"))
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHTimelineEvent(
                    id = j.optLong("id"),
                    event = j.optString("event"),
                    actor = j.optJSONObject("actor")?.optString("login") ?: "",
                    createdAt = j.optString("created_at", ""),
                    label = j.optJSONObject("label")?.optString("name") ?: "",
                    milestone = j.optJSONObject("milestone")?.optString("title") ?: "",
                    assignee = j.optJSONObject("assignee")?.optString("login") ?: "",
                    source = j.optJSONObject("source")?.optString("issue") ?: ""
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    // ═══════════════════════════════════
    // Webhooks
    // ═══════════════════════════════════

    suspend fun getWebhooks(context: Context, owner: String, repo: String): List<GHWebhook> {
        val r = request(context, "/repos/$owner/$repo/hooks?per_page=100")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                parseWebhook(arr.getJSONObject(i))
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getWebhook(context: Context, owner: String, repo: String, hookId: Long): GHWebhook? {
        val r = request(context, "/repos/$owner/$repo/hooks/$hookId")
        if (!r.success) return null
        return try { parseWebhook(JSONObject(r.body)) } catch (e: Exception) { null }
    }

    suspend fun createWebhook(context: Context, owner: String, repo: String, config: Map<String, String>, events: List<String>, active: Boolean = true): Boolean {
        val configJson = JSONObject().apply { config.forEach { (k, v) -> put(k, v) } }
        val body = JSONObject().apply {
            put("name", "web")
            put("config", configJson)
            put("events", JSONArray(events))
            put("active", active)
        }.toString()
        val r = request(context, "/repos/$owner/$repo/hooks", "POST", body)
        return r.success
    }

    suspend fun updateWebhook(context: Context, owner: String, repo: String, hookId: Long, config: Map<String, String>, events: List<String>, active: Boolean = true): Boolean {
        val configJson = JSONObject().apply { config.forEach { (k, v) -> put(k, v) } }
        val body = JSONObject().apply {
            put("config", configJson)
            put("events", JSONArray(events))
            put("active", active)
        }.toString()
        val r = request(context, "/repos/$owner/$repo/hooks/$hookId", "PATCH", body)
        return r.success
    }

    suspend fun pingWebhook(context: Context, owner: String, repo: String, hookId: Long): Boolean {
        val r = request(context, "/repos/$owner/$repo/hooks/$hookId/pings", "POST", "{}")
        return r.code == 204 || r.success
    }

    suspend fun testWebhook(context: Context, owner: String, repo: String, hookId: Long): Boolean {
        val r = request(context, "/repos/$owner/$repo/hooks/$hookId/tests", "POST", "{}")
        return r.code == 204 || r.success
    }

    suspend fun getWebhookConfig(context: Context, owner: String, repo: String, hookId: Long): GHWebhookConfig? {
        val r = request(context, "/repos/$owner/$repo/hooks/$hookId/config")
        if (!r.success) return null
        return try { parseWebhookConfig(JSONObject(r.body)) } catch (e: Exception) { null }
    }

    suspend fun updateWebhookConfig(context: Context, owner: String, repo: String, hookId: Long, config: Map<String, String>): Boolean {
        val body = JSONObject().apply { config.forEach { (k, v) -> put(k, v) } }.toString()
        val r = request(context, "/repos/$owner/$repo/hooks/$hookId/config", "PATCH", body)
        return r.success
    }

    suspend fun getWebhookDeliveries(context: Context, owner: String, repo: String, hookId: Long): List<GHWebhookDelivery> {
        val r = request(context, "/repos/$owner/$repo/hooks/$hookId/deliveries?per_page=100")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).mapNotNull { i ->
                parseWebhookDelivery(arr.optJSONObject(i) ?: return@mapNotNull null)
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getWebhookDelivery(context: Context, owner: String, repo: String, hookId: Long, deliveryId: Long): GHWebhookDelivery? {
        val r = request(context, "/repos/$owner/$repo/hooks/$hookId/deliveries/$deliveryId")
        if (!r.success) return null
        return try { parseWebhookDelivery(JSONObject(r.body)) } catch (e: Exception) { null }
    }

    suspend fun redeliverWebhookDelivery(context: Context, owner: String, repo: String, hookId: Long, deliveryId: Long): Boolean {
        val r = request(context, "/repos/$owner/$repo/hooks/$hookId/deliveries/$deliveryId/attempts", "POST", "{}")
        return r.code == 202 || r.success
    }

    suspend fun deleteWebhook(context: Context, owner: String, repo: String, hookId: Long): Boolean {
        val r = request(context, "/repos/$owner/$repo/hooks/$hookId", "DELETE")
        return r.code == 204 || r.success
    }

    private fun parseWebhookDelivery(j: JSONObject): GHWebhookDelivery {
        val request = j.optJSONObject("request")
        val response = j.optJSONObject("response")
        return GHWebhookDelivery(
            id = j.optLong("id"),
            guid = j.optString("guid", ""),
            event = j.optString("event", ""),
            action = j.optString("action", ""),
            deliveredAt = j.optString("delivered_at", ""),
            duration = j.optDouble("duration", 0.0),
            status = j.optString("status", ""),
            statusCode = j.optInt("status_code", 0),
            redelivery = j.optBoolean("redelivery", false),
            requestHeaders = parseHeaderMap(request?.optJSONObject("headers")),
            requestPayload = request?.opt("payload")?.toString() ?: "",
            responseHeaders = parseHeaderMap(response?.optJSONObject("headers")),
            responsePayload = response?.opt("payload")?.toString() ?: ""
        )
    }

    private fun parseWebhookConfig(j: JSONObject): GHWebhookConfig =
        GHWebhookConfig(
            url = j.optString("url", ""),
            contentType = j.optString("content_type", "json"),
            insecureSsl = j.optString("insecure_ssl", "0"),
            secret = j.optString("secret", "")
        )

    private fun parseWebhook(j: JSONObject): GHWebhook {
        val config = j.optJSONObject("config")
        val lastResponse = j.optJSONObject("last_response")
        return GHWebhook(
            id = j.optLong("id"),
            name = j.optString("name"),
            url = config?.optString("url") ?: "",
            contentType = config?.optString("content_type") ?: "json",
            insecureSsl = config?.optString("insecure_ssl") ?: "0",
            events = j.optJSONArray("events")?.let { ev -> (0 until ev.length()).map { ev.getString(it) } } ?: emptyList(),
            active = j.optBoolean("active", true),
            createdAt = j.optString("created_at", ""),
            updatedAt = j.optString("updated_at", ""),
            lastResponseCode = lastResponse?.optInt("code", 0) ?: 0,
            lastResponseStatus = lastResponse?.optString("status", "") ?: "",
            lastResponseMessage = lastResponse?.optString("message", "") ?: ""
        )
    }

    private fun parseHeaderMap(headers: JSONObject?): List<Pair<String, String>> {
        if (headers == null) return emptyList()
        return headers.keys().asSequence().map { key -> key to headers.optString(key, "") }.toList()
    }

    // ═══════════════════════════════════
    // Discussions
    // ═══════════════════════════════════

    suspend fun getDiscussions(context: Context, owner: String, repo: String): List<GHDiscussion> {
        val data = graphql(context, """
            query(${'$'}owner: String!, ${'$'}repo: String!) {
              repository(owner: ${'$'}owner, name: ${'$'}repo) {
                discussions(first: 50, orderBy: {field: UPDATED_AT, direction: DESC}) {
                  nodes {
                    id
                    number
                    title
                    body
                    createdAt
                    updatedAt
                    closed
                    locked
                    url
                    upvoteCount
                    answer { id }
                    category { id name emoji isAnswerable }
                    author { login avatarUrl }
                    comments { totalCount }
                  }
                }
              }
            }
        """.trimIndent(), JSONObject().apply {
            put("owner", owner)
            put("repo", repo)
        }) ?: return emptyList()
        return try {
            val nodes = data.optJSONObject("repository")?.optJSONObject("discussions")?.optJSONArray("nodes") ?: return emptyList()
            parseDiscussionNodes(nodes)
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getDiscussionCategories(context: Context, owner: String, repo: String): List<GHDiscussionCategory> {
        val data = graphql(context, """
            query(${'$'}owner: String!, ${'$'}repo: String!) {
              repository(owner: ${'$'}owner, name: ${'$'}repo) {
                discussionCategories(first: 50) {
                  nodes {
                    id
                    name
                    slug
                    emoji
                    description
                    isAnswerable
                  }
                }
              }
            }
        """.trimIndent(), JSONObject().apply {
            put("owner", owner)
            put("repo", repo)
        }) ?: return emptyList()
        return try {
            val nodes = data.optJSONObject("repository")?.optJSONObject("discussionCategories")?.optJSONArray("nodes") ?: return emptyList()
            (0 until nodes.length()).mapNotNull { i ->
                nodes.optJSONObject(i)?.let { j ->
                    GHDiscussionCategory(
                        id = j.optString("id"),
                        name = j.optString("name"),
                        slug = j.optString("slug"),
                        emoji = j.optString("emoji"),
                        description = j.optString("description", ""),
                        isAnswerable = j.optBoolean("isAnswerable", false)
                    )
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getDiscussionDetail(context: Context, owner: String, repo: String, discussionNumber: Int): GHDiscussion? {
        val data = graphql(context, """
            query(${'$'}owner: String!, ${'$'}repo: String!, ${'$'}number: Int!) {
              repository(owner: ${'$'}owner, name: ${'$'}repo) {
                discussion(number: ${'$'}number) {
                  id
                  number
                  title
                  body
                  createdAt
                  updatedAt
                  closed
                  locked
                  url
                  upvoteCount
                  answer { id }
                  category { id name emoji isAnswerable }
                  author { login avatarUrl }
                  comments { totalCount }
                }
              }
            }
        """.trimIndent(), JSONObject().apply {
            put("owner", owner)
            put("repo", repo)
            put("number", discussionNumber)
        }) ?: return null
        return try {
            data.optJSONObject("repository")?.optJSONObject("discussion")?.let(::parseDiscussion)
        } catch (e: Exception) { null }
    }

    suspend fun createDiscussion(context: Context, owner: String, repo: String, title: String, body: String, categoryId: String): Boolean {
        val repositoryId = getRepositoryNodeId(context, owner, repo) ?: return false
        val data = graphql(context, """
            mutation(${'$'}repositoryId: ID!, ${'$'}categoryId: ID!, ${'$'}title: String!, ${'$'}body: String!) {
              createDiscussion(input: {repositoryId: ${'$'}repositoryId, categoryId: ${'$'}categoryId, title: ${'$'}title, body: ${'$'}body}) {
                discussion { id }
              }
            }
        """.trimIndent(), JSONObject().apply {
            put("repositoryId", repositoryId)
            put("categoryId", categoryId)
            put("title", title)
            put("body", body)
        })
        return data?.optJSONObject("createDiscussion")?.optJSONObject("discussion")?.optString("id").orEmpty().isNotBlank()
    }

    suspend fun updateDiscussion(context: Context, discussionId: String, title: String, body: String, categoryId: String? = null): Boolean {
        val data = graphql(context, """
            mutation(${'$'}discussionId: ID!, ${'$'}title: String!, ${'$'}body: String!, ${'$'}categoryId: ID) {
              updateDiscussion(input: {discussionId: ${'$'}discussionId, title: ${'$'}title, body: ${'$'}body, categoryId: ${'$'}categoryId}) {
                discussion { id }
              }
            }
        """.trimIndent(), JSONObject().apply {
            put("discussionId", discussionId)
            put("title", title)
            put("body", body)
            if (categoryId.isNullOrBlank()) put("categoryId", JSONObject.NULL) else put("categoryId", categoryId)
        })
        return data?.optJSONObject("updateDiscussion")?.optJSONObject("discussion")?.optString("id").orEmpty().isNotBlank()
    }

    suspend fun deleteDiscussion(context: Context, discussionId: String): Boolean {
        val data = graphql(context, """
            mutation(${'$'}discussionId: ID!) {
              deleteDiscussion(input: {id: ${'$'}discussionId}) {
                discussion { id }
              }
            }
        """.trimIndent(), JSONObject().apply {
            put("discussionId", discussionId)
        })
        return data?.optJSONObject("deleteDiscussion")?.optJSONObject("discussion")?.optString("id").orEmpty().isNotBlank()
    }

    suspend fun getDiscussionComments(context: Context, owner: String, repo: String, discussionNumber: Int): List<GHComment> {
        val data = graphql(context, """
            query(${'$'}owner: String!, ${'$'}repo: String!, ${'$'}number: Int!) {
              repository(owner: ${'$'}owner, name: ${'$'}repo) {
                discussion(number: ${'$'}number) {
                  comments(first: 100) {
                    nodes {
                      id
                      databaseId
                      body
                      createdAt
                      author { login avatarUrl }
                    }
                  }
                }
              }
            }
        """.trimIndent(), JSONObject().apply {
            put("owner", owner)
            put("repo", repo)
            put("number", discussionNumber)
        }) ?: return emptyList()
        return try {
            val nodes = data.optJSONObject("repository")
                ?.optJSONObject("discussion")
                ?.optJSONObject("comments")
                ?.optJSONArray("nodes") ?: return emptyList()
            (0 until nodes.length()).mapNotNull { i ->
                nodes.optJSONObject(i)?.let { j ->
                    val author = j.optJSONObject("author")
                    GHComment(
                        id = j.optLong("databaseId", 0L),
                        body = j.optString("body"),
                        author = author?.optString("login") ?: "",
                        avatarUrl = author?.optString("avatarUrl") ?: "",
                        createdAt = j.optString("createdAt")
                    )
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun addDiscussionComment(context: Context, discussionId: String, body: String): Boolean {
        val data = graphql(context, """
            mutation(${'$'}discussionId: ID!, ${'$'}body: String!) {
              addDiscussionComment(input: {discussionId: ${'$'}discussionId, body: ${'$'}body}) {
                comment { id }
              }
            }
        """.trimIndent(), JSONObject().apply {
            put("discussionId", discussionId)
            put("body", body)
        })
        return data?.optJSONObject("addDiscussionComment")?.optJSONObject("comment")?.optString("id").orEmpty().isNotBlank()
    }

    private suspend fun getRepositoryNodeId(context: Context, owner: String, repo: String): String? {
        val data = graphql(context, """
            query(${'$'}owner: String!, ${'$'}repo: String!) {
              repository(owner: ${'$'}owner, name: ${'$'}repo) {
                id
              }
            }
        """.trimIndent(), JSONObject().apply {
            put("owner", owner)
            put("repo", repo)
        }) ?: return null
        return data.optJSONObject("repository")?.optString("id")?.takeIf { it.isNotBlank() }
    }

    private fun parseDiscussionNodes(nodes: JSONArray): List<GHDiscussion> =
        (0 until nodes.length()).mapNotNull { i -> nodes.optJSONObject(i)?.let(::parseDiscussion) }

    private fun parseDiscussion(j: JSONObject): GHDiscussion {
        val author = j.optJSONObject("author")
        val category = j.optJSONObject("category")
        return GHDiscussion(
            id = j.optString("id"),
            number = j.optInt("number"),
            title = j.optString("title"),
            body = j.optString("body", ""),
            author = author?.optString("login") ?: "",
            avatarUrl = author?.optString("avatarUrl") ?: "",
            state = if (j.optBoolean("closed", false)) "closed" else "open",
            comments = j.optJSONObject("comments")?.optInt("totalCount", 0) ?: 0,
            createdAt = j.optString("createdAt", ""),
            updatedAt = j.optString("updatedAt", ""),
            categoryId = category?.optString("id") ?: "",
            categoryName = category?.optString("name") ?: "",
            categoryEmoji = category?.optString("emoji") ?: "",
            isAnswerable = category?.optBoolean("isAnswerable", false) ?: false,
            isAnswered = j.optJSONObject("answer") != null,
            locked = j.optBoolean("locked", false),
            upvotes = j.optInt("upvoteCount", 0),
            htmlUrl = j.optString("url", "")
        )
    }

    // ═══════════════════════════════════
    // Projects
    // ═══════════════════════════════════

    suspend fun getRepoProjects(context: Context, owner: String, repo: String, state: String = "all"): List<GHProject> {
        val r = request(context, "/repos/$owner/$repo/projects?state=$state&per_page=100", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parseProject) }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getProject(context: Context, projectId: Long): GHProject? {
        val r = request(context, "/projects/$projectId", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return null
        return try { parseProject(JSONObject(r.body)) } catch (e: Exception) { null }
    }

    suspend fun createRepoProject(context: Context, owner: String, repo: String, name: String, body: String): GHProject? {
        val payload = JSONObject().apply {
            put("name", name)
            put("body", body)
        }.toString()
        val r = request(context, "/repos/$owner/$repo/projects", "POST", payload, extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return null
        return try { parseProject(JSONObject(r.body)) } catch (e: Exception) { null }
    }

    suspend fun updateProject(context: Context, projectId: Long, name: String, body: String, state: String): Boolean {
        val payload = JSONObject().apply {
            put("name", name)
            put("body", body)
            put("state", state)
        }.toString()
        return request(context, "/projects/$projectId", "PATCH", payload, extraHeaders = mapOf("Accept" to "application/vnd.github+json")).success
    }

    suspend fun deleteProject(context: Context, projectId: Long): Boolean =
        request(context, "/projects/$projectId", "DELETE", extraHeaders = mapOf("Accept" to "application/vnd.github+json")).let { it.code == 204 || it.success }

    suspend fun getProjectColumns(context: Context, projectId: Long): List<GHProjectColumn> {
        val r = request(context, "/projects/$projectId/columns?per_page=100", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parseProjectColumn) }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun createProjectColumn(context: Context, projectId: Long, name: String): GHProjectColumn? {
        val payload = JSONObject().apply { put("name", name) }.toString()
        val r = request(context, "/projects/$projectId/columns", "POST", payload, extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return null
        return try { parseProjectColumn(JSONObject(r.body)) } catch (e: Exception) { null }
    }

    suspend fun getProjectCards(context: Context, columnId: Long): List<GHProjectCard> {
        val r = request(context, "/projects/columns/$columnId/cards?archived_state=all&per_page=100", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parseProjectCard) }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun createProjectCard(context: Context, columnId: Long, note: String): GHProjectCard? {
        val payload = JSONObject().apply { put("note", note) }.toString()
        val r = request(context, "/projects/columns/$columnId/cards", "POST", payload, extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return null
        return try { parseProjectCard(JSONObject(r.body)) } catch (e: Exception) { null }
    }

    suspend fun moveProjectCard(context: Context, cardId: Long, position: String, columnId: Long? = null): Boolean {
        val payload = JSONObject().apply {
            put("position", position)
            if (columnId != null) put("column_id", columnId)
        }.toString()
        return request(context, "/projects/columns/cards/$cardId/moves", "POST", payload, extraHeaders = mapOf("Accept" to "application/vnd.github+json")).let { it.code == 201 || it.success }
    }

    suspend fun deleteProjectCard(context: Context, cardId: Long): Boolean =
        request(context, "/projects/columns/cards/$cardId", "DELETE", extraHeaders = mapOf("Accept" to "application/vnd.github+json")).let { it.code == 204 || it.success }

    suspend fun getRepoProjectsV2(context: Context, owner: String, repo: String): List<GHProjectV2> {
        val data = graphql(context, """
            query(${'$'}owner: String!, ${'$'}repo: String!) {
              repository(owner: ${'$'}owner, name: ${'$'}repo) {
                projectsV2(first: 30, orderBy: {field: UPDATED_AT, direction: DESC}) {
                  nodes {
                    id
                    number
                    title
                    shortDescription
                    url
                    closed
                    public
                    updatedAt
                    items { totalCount }
                  }
                }
              }
            }
        """.trimIndent(), JSONObject().apply {
            put("owner", owner)
            put("repo", repo)
        }) ?: return emptyList()
        return try {
            val nodes = data.optJSONObject("repository")?.optJSONObject("projectsV2")?.optJSONArray("nodes") ?: return emptyList()
            (0 until nodes.length()).mapNotNull { i ->
                nodes.optJSONObject(i)?.let { j ->
                    GHProjectV2(
                        id = j.optString("id"),
                        number = j.optInt("number"),
                        title = j.optString("title"),
                        shortDescription = j.optString("shortDescription", ""),
                        url = j.optString("url", ""),
                        closed = j.optBoolean("closed", false),
                        isPublic = j.optBoolean("public", false),
                        updatedAt = j.optString("updatedAt", ""),
                        itemsCount = j.optJSONObject("items")?.optInt("totalCount", 0) ?: 0
                    )
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getProjectV2Detail(context: Context, projectId: String): GHProjectV2Detail? {
        val data = graphql(context, """
            query(${'$'}projectId: ID!) {
              node(id: ${'$'}projectId) {
                ... on ProjectV2 {
                  id
                  number
                  title
                  shortDescription
                  readme
                  url
                  closed
                  public
                  updatedAt
                  fields(first: 50) {
                    nodes {
                      __typename
                      ... on ProjectV2FieldCommon {
                        id
                        name
                        dataType
                      }
                      ... on ProjectV2SingleSelectField {
                        id
                        name
                        dataType
                        options { id name color description }
                      }
                      ... on ProjectV2IterationField {
                        id
                        name
                        dataType
                      }
                    }
                  }
                  views(first: 30) {
                    totalCount
                    nodes {
                      id
                      number
                      name
                      layout
                      filter
                      updatedAt
                      fields(first: 20) {
                        nodes {
                          ... on ProjectV2FieldCommon {
                            id
                            name
                            dataType
                          }
                        }
                      }
                    }
                  }
                  workflows(first: 30) {
                    totalCount
                    nodes {
                      id
                      number
                      name
                      enabled
                      updatedAt
                    }
                  }
                  items(first: 100) {
                    totalCount
                    nodes {
                      id
                      type
                      archived
                      updatedAt
                      creator { login }
                      content {
                        __typename
                        ... on DraftIssue { id title body createdAt updatedAt }
                        ... on Issue { id title body number state url createdAt updatedAt }
                        ... on PullRequest { id title body number state url createdAt updatedAt }
                      }
                      fieldValues(first: 50) {
                        nodes {
                          __typename
                          ... on ProjectV2ItemFieldTextValue {
                            text
                            field { ... on ProjectV2FieldCommon { id name dataType } }
                          }
                          ... on ProjectV2ItemFieldNumberValue {
                            number
                            field { ... on ProjectV2FieldCommon { id name dataType } }
                          }
                          ... on ProjectV2ItemFieldDateValue {
                            date
                            field { ... on ProjectV2FieldCommon { id name dataType } }
                          }
                          ... on ProjectV2ItemFieldSingleSelectValue {
                            name
                            optionId
                            field { ... on ProjectV2FieldCommon { id name dataType } }
                          }
                          ... on ProjectV2ItemFieldIterationValue {
                            title
                            iterationId
                            field { ... on ProjectV2FieldCommon { id name dataType } }
                          }
                          ... on ProjectV2ItemFieldPullRequestValue {
                            pullRequests(first: 3) { totalCount }
                            field { ... on ProjectV2FieldCommon { id name dataType } }
                          }
                          ... on ProjectV2ItemFieldRepositoryValue {
                            repository { nameWithOwner }
                            field { ... on ProjectV2FieldCommon { id name dataType } }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent(), JSONObject().apply { put("projectId", projectId) }) ?: return null
        return try {
            data.optJSONObject("node")?.let(::parseProjectV2Detail)
        } catch (e: Exception) { null }
    }

    suspend fun updateProjectV2(context: Context, projectId: String, title: String, shortDescription: String, readme: String, closed: Boolean, isPublic: Boolean): Boolean {
        val data = graphql(context, """
            mutation(${'$'}projectId: ID!, ${'$'}title: String!, ${'$'}shortDescription: String, ${'$'}readme: String, ${'$'}closed: Boolean!, ${'$'}public: Boolean!) {
              updateProjectV2(input: {projectId: ${'$'}projectId, title: ${'$'}title, shortDescription: ${'$'}shortDescription, readme: ${'$'}readme, closed: ${'$'}closed, public: ${'$'}public}) {
                projectV2 { id }
              }
            }
        """.trimIndent(), JSONObject().apply {
            put("projectId", projectId)
            put("title", title)
            put("shortDescription", shortDescription)
            put("readme", readme)
            put("closed", closed)
            put("public", isPublic)
        })
        return data?.optJSONObject("updateProjectV2")?.optJSONObject("projectV2")?.optString("id").orEmpty().isNotBlank()
    }

    suspend fun createProjectV2Field(context: Context, projectId: String, name: String, dataType: String, options: List<String> = emptyList()): GHProjectV2Field? {
        val variables = JSONObject().apply {
            put("projectId", projectId)
            put("name", name)
            put("dataType", dataType)
            if (dataType == "SINGLE_SELECT") put("singleSelectOptions", projectV2SingleSelectOptionsJson(options))
        }
        val optionVariable = if (dataType == "SINGLE_SELECT") ", ${'$'}singleSelectOptions: [ProjectV2SingleSelectFieldOptionInput!]" else ""
        val optionInput = if (dataType == "SINGLE_SELECT") ", singleSelectOptions: ${'$'}singleSelectOptions" else ""
        val data = graphql(context, """
            mutation(${'$'}projectId: ID!, ${'$'}name: String!, ${'$'}dataType: ProjectV2CustomFieldType!$optionVariable) {
              createProjectV2Field(input: {projectId: ${'$'}projectId, name: ${'$'}name, dataType: ${'$'}dataType$optionInput}) {
                projectV2Field {
                  ... on ProjectV2FieldCommon { id name dataType }
                  ... on ProjectV2SingleSelectField { options { id name color description } }
                }
              }
            }
        """.trimIndent(), variables) ?: return null
        return try {
            data.optJSONObject("createProjectV2Field")?.optJSONObject("projectV2Field")?.let(::parseProjectV2Field)
        } catch (e: Exception) { null }
    }

    suspend fun updateProjectV2Field(context: Context, field: GHProjectV2Field, name: String, options: List<String> = emptyList()): GHProjectV2Field? {
        val variables = JSONObject().apply {
            put("fieldId", field.id)
            put("name", name)
            if (field.dataType == "SINGLE_SELECT") put("singleSelectOptions", projectV2SingleSelectOptionsJson(options))
        }
        val optionVariable = if (field.dataType == "SINGLE_SELECT") ", ${'$'}singleSelectOptions: [ProjectV2SingleSelectFieldOptionInput!]" else ""
        val optionInput = if (field.dataType == "SINGLE_SELECT") ", singleSelectOptions: ${'$'}singleSelectOptions" else ""
        val data = graphql(context, """
            mutation(${'$'}fieldId: ID!, ${'$'}name: String$optionVariable) {
              updateProjectV2Field(input: {fieldId: ${'$'}fieldId, name: ${'$'}name$optionInput}) {
                projectV2Field {
                  ... on ProjectV2FieldCommon { id name dataType }
                  ... on ProjectV2SingleSelectField { options { id name color description } }
                }
              }
            }
        """.trimIndent(), variables) ?: return null
        return try {
            data.optJSONObject("updateProjectV2Field")?.optJSONObject("projectV2Field")?.let(::parseProjectV2Field)
        } catch (e: Exception) { null }
    }

    suspend fun deleteProjectV2Field(context: Context, fieldId: String): Boolean {
        val data = graphql(context, """
            mutation(${'$'}fieldId: ID!) {
              deleteProjectV2Field(input: {fieldId: ${'$'}fieldId}) {
                projectV2Field {
                  ... on ProjectV2FieldCommon { id }
                }
              }
            }
        """.trimIndent(), JSONObject().apply { put("fieldId", fieldId) })
        return data?.optJSONObject("deleteProjectV2Field")?.optJSONObject("projectV2Field")?.optString("id").orEmpty().isNotBlank()
    }

    private fun projectV2SingleSelectOptionsJson(options: List<String>): JSONArray =
        JSONArray(options.map {
            JSONObject().apply {
                put("name", it)
                put("description", "")
                put("color", "GRAY")
            }
        })

    suspend fun addProjectV2DraftIssue(context: Context, projectId: String, title: String, body: String): GHProjectV2Item? {
        val data = graphql(context, """
            mutation(${'$'}projectId: ID!, ${'$'}title: String!, ${'$'}body: String) {
              addProjectV2DraftIssue(input: {projectId: ${'$'}projectId, title: ${'$'}title, body: ${'$'}body}) {
                projectItem {
                  id
                  type
                  archived
                  updatedAt
                  creator { login }
                  content {
                    __typename
                    ... on DraftIssue { id title body createdAt updatedAt }
                  }
                  fieldValues(first: 1) { nodes { __typename } }
                }
              }
            }
        """.trimIndent(), JSONObject().apply {
            put("projectId", projectId)
            put("title", title)
            put("body", body)
        }) ?: return null
        return try {
            data.optJSONObject("addProjectV2DraftIssue")?.optJSONObject("projectItem")?.let(::parseProjectV2Item)
        } catch (e: Exception) { null }
    }

    suspend fun updateProjectV2DraftIssue(context: Context, draftIssueId: String, title: String, body: String): Boolean {
        val data = graphql(context, """
            mutation(${'$'}draftIssueId: ID!, ${'$'}title: String!, ${'$'}body: String) {
              updateProjectV2DraftIssue(input: {draftIssueId: ${'$'}draftIssueId, title: ${'$'}title, body: ${'$'}body}) {
                draftIssue { id }
              }
            }
        """.trimIndent(), JSONObject().apply {
            put("draftIssueId", draftIssueId)
            put("title", title)
            put("body", body)
        })
        return data?.optJSONObject("updateProjectV2DraftIssue")?.optJSONObject("draftIssue")?.optString("id").orEmpty().isNotBlank()
    }

    suspend fun deleteProjectV2Item(context: Context, projectId: String, itemId: String): Boolean {
        val data = graphql(context, """
            mutation(${'$'}projectId: ID!, ${'$'}itemId: ID!) {
              deleteProjectV2Item(input: {projectId: ${'$'}projectId, itemId: ${'$'}itemId}) {
                deletedItemId
              }
            }
        """.trimIndent(), JSONObject().apply {
            put("projectId", projectId)
            put("itemId", itemId)
        })
        return data?.optJSONObject("deleteProjectV2Item")?.optString("deletedItemId").orEmpty().isNotBlank()
    }

    suspend fun archiveProjectV2Item(context: Context, projectId: String, itemId: String, archived: Boolean): Boolean {
        val mutation = if (archived) "archiveProjectV2Item" else "unarchiveProjectV2Item"
        val data = graphql(context, """
            mutation(${'$'}projectId: ID!, ${'$'}itemId: ID!) {
              $mutation(input: {projectId: ${'$'}projectId, itemId: ${'$'}itemId}) {
                item { id }
              }
            }
        """.trimIndent(), JSONObject().apply {
            put("projectId", projectId)
            put("itemId", itemId)
        })
        return data?.optJSONObject(mutation)?.optJSONObject("item")?.optString("id").orEmpty().isNotBlank()
    }

    suspend fun updateProjectV2ItemFieldValue(context: Context, projectId: String, itemId: String, field: GHProjectV2Field, value: String): Boolean {
        val normalized = value.trim()
        if (normalized.isBlank()) return clearProjectV2ItemFieldValue(context, projectId, itemId, field.id)
        val valueObject = JSONObject()
        when (field.dataType.lowercase()) {
            "number" -> valueObject.put("number", normalized.toDoubleOrNull() ?: return false)
            "date" -> valueObject.put("date", normalized)
            "single_select" -> {
                val option = field.options.firstOrNull { it.id == normalized || it.name.equals(normalized, ignoreCase = true) } ?: return false
                valueObject.put("singleSelectOptionId", option.id)
            }
            else -> valueObject.put("text", normalized)
        }
        val data = graphql(context, """
            mutation(${'$'}projectId: ID!, ${'$'}itemId: ID!, ${'$'}fieldId: ID!, ${'$'}value: ProjectV2FieldValue!) {
              updateProjectV2ItemFieldValue(input: {projectId: ${'$'}projectId, itemId: ${'$'}itemId, fieldId: ${'$'}fieldId, value: ${'$'}value}) {
                projectV2Item { id }
              }
            }
        """.trimIndent(), JSONObject().apply {
            put("projectId", projectId)
            put("itemId", itemId)
            put("fieldId", field.id)
            put("value", valueObject)
        })
        return data?.optJSONObject("updateProjectV2ItemFieldValue")?.optJSONObject("projectV2Item")?.optString("id").orEmpty().isNotBlank()
    }

    suspend fun clearProjectV2ItemFieldValue(context: Context, projectId: String, itemId: String, fieldId: String): Boolean {
        val data = graphql(context, """
            mutation(${'$'}projectId: ID!, ${'$'}itemId: ID!, ${'$'}fieldId: ID!) {
              clearProjectV2ItemFieldValue(input: {projectId: ${'$'}projectId, itemId: ${'$'}itemId, fieldId: ${'$'}fieldId}) {
                projectV2Item { id }
              }
            }
        """.trimIndent(), JSONObject().apply {
            put("projectId", projectId)
            put("itemId", itemId)
            put("fieldId", fieldId)
        })
        return data?.optJSONObject("clearProjectV2ItemFieldValue")?.optJSONObject("projectV2Item")?.optString("id").orEmpty().isNotBlank()
    }

    suspend fun moveProjectV2Item(context: Context, projectId: String, itemId: String, afterId: String?): Boolean {
        val data = graphql(context, """
            mutation(${'$'}projectId: ID!, ${'$'}itemId: ID!, ${'$'}afterId: ID) {
              updateProjectV2ItemPosition(input: {projectId: ${'$'}projectId, itemId: ${'$'}itemId, afterId: ${'$'}afterId}) {
                items { totalCount }
              }
            }
        """.trimIndent(), JSONObject().apply {
            put("projectId", projectId)
            put("itemId", itemId)
            if (afterId.isNullOrBlank()) put("afterId", JSONObject.NULL) else put("afterId", afterId)
        })
        return data?.optJSONObject("updateProjectV2ItemPosition")?.optJSONObject("items") != null
    }

    private fun parseProject(j: JSONObject): GHProject =
        GHProject(
            id = j.optLong("id"),
            nodeId = j.optString("node_id", ""),
            name = j.optString("name"),
            body = j.optString("body", ""),
            state = j.optString("state", "open"),
            number = j.optInt("number", 0),
            columnsUrl = j.optString("columns_url", ""),
            htmlUrl = j.optString("html_url", ""),
            createdAt = j.optString("created_at", ""),
            updatedAt = j.optString("updated_at", ""),
            creator = j.optJSONObject("creator")?.optString("login") ?: ""
        )

    private fun parseProjectColumn(j: JSONObject): GHProjectColumn =
        GHProjectColumn(
            id = j.optLong("id"),
            nodeId = j.optString("node_id", ""),
            name = j.optString("name"),
            cardsUrl = j.optString("cards_url", ""),
            createdAt = j.optString("created_at", ""),
            updatedAt = j.optString("updated_at", "")
        )

    private fun parseProjectCard(j: JSONObject): GHProjectCard =
        GHProjectCard(
            id = j.optLong("id"),
            nodeId = j.optString("node_id", ""),
            note = j.optString("note", ""),
            creator = j.optJSONObject("creator")?.optString("login") ?: "",
            contentUrl = j.optString("content_url", ""),
            archived = j.optBoolean("archived", false),
            createdAt = j.optString("created_at", ""),
            updatedAt = j.optString("updated_at", ""),
            columnUrl = j.optString("column_url", "")
        )

    private fun parseProjectV2Detail(j: JSONObject): GHProjectV2Detail {
        val fields = j.optJSONObject("fields")?.optJSONArray("nodes")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parseProjectV2Field) }
        }.orEmpty()
        val itemsObject = j.optJSONObject("items")
        val items = itemsObject?.optJSONArray("nodes")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parseProjectV2Item) }
        }.orEmpty()
        val viewsObject = j.optJSONObject("views")
        val views = viewsObject?.optJSONArray("nodes")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parseProjectV2View) }
        }.orEmpty()
        val workflowsObject = j.optJSONObject("workflows")
        val workflows = workflowsObject?.optJSONArray("nodes")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parseProjectV2Workflow) }
        }.orEmpty()
        return GHProjectV2Detail(
            id = j.optString("id"),
            number = j.optInt("number"),
            title = j.optString("title"),
            shortDescription = j.optString("shortDescription", ""),
            readme = j.optString("readme", ""),
            url = j.optString("url", ""),
            closed = j.optBoolean("closed", false),
            isPublic = j.optBoolean("public", false),
            updatedAt = j.optString("updatedAt", ""),
            itemsCount = itemsObject?.optInt("totalCount", items.size) ?: items.size,
            fields = fields,
            items = items,
            views = views,
            workflows = workflows
        )
    }

    private fun parseProjectV2Field(j: JSONObject): GHProjectV2Field? {
        val id = j.optString("id").takeIf { it.isNotBlank() } ?: return null
        val options = j.optJSONArray("options")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { option ->
                    GHProjectV2FieldOption(
                        id = option.optString("id"),
                        name = option.optString("name"),
                        color = option.optString("color", ""),
                        description = option.optString("description", "")
                    )
                }
            }
        }.orEmpty()
        return GHProjectV2Field(
            id = id,
            name = j.optString("name"),
            dataType = j.optString("dataType", ""),
            options = options
        )
    }

    private fun parseProjectV2View(j: JSONObject): GHProjectV2View {
        val fields = j.optJSONObject("fields")?.optJSONArray("nodes")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }
            }
        }.orEmpty()
        return GHProjectV2View(
            id = j.optString("id"),
            number = j.optInt("number"),
            name = j.optString("name", ""),
            layout = j.optString("layout", ""),
            filter = j.optString("filter", ""),
            updatedAt = j.optString("updatedAt", ""),
            fields = fields
        )
    }

    private fun parseProjectV2Workflow(j: JSONObject): GHProjectV2Workflow =
        GHProjectV2Workflow(
            id = j.optString("id"),
            number = j.optInt("number"),
            name = j.optString("name", ""),
            enabled = j.optBoolean("enabled", false),
            updatedAt = j.optString("updatedAt", "")
        )

    private fun parseProjectV2Item(j: JSONObject): GHProjectV2Item {
        val content = j.optJSONObject("content")
        val typename = content?.optString("__typename", j.optString("type", "")) ?: j.optString("type", "")
        val fieldValues = j.optJSONObject("fieldValues")?.optJSONArray("nodes")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parseProjectV2ItemFieldValue) }
        }.orEmpty()
        return GHProjectV2Item(
            id = j.optString("id"),
            type = j.optString("type", typename),
            contentId = content?.optString("id", "") ?: "",
            contentType = typename,
            title = content?.optString("title") ?: "",
            body = content?.optString("body", "") ?: "",
            number = content?.optInt("number", 0) ?: 0,
            state = content?.optString("state", "") ?: "",
            url = content?.optString("url", "") ?: "",
            creator = j.optJSONObject("creator")?.optString("login") ?: "",
            archived = j.optBoolean("archived", false),
            createdAt = content?.optString("createdAt", "") ?: "",
            updatedAt = content?.optString("updatedAt", j.optString("updatedAt", "")) ?: j.optString("updatedAt", ""),
            fieldValues = fieldValues
        )
    }

    private fun parseProjectV2ItemFieldValue(j: JSONObject): GHProjectV2ItemFieldValue? {
        val field = j.optJSONObject("field") ?: return null
        val fieldId = field.optString("id").takeIf { it.isNotBlank() } ?: return null
        val type = field.optString("dataType", "")
        val value = when (j.optString("__typename")) {
            "ProjectV2ItemFieldTextValue" -> j.optString("text", "")
            "ProjectV2ItemFieldNumberValue" -> j.optDouble("number", 0.0).toString()
            "ProjectV2ItemFieldDateValue" -> j.optString("date", "")
            "ProjectV2ItemFieldSingleSelectValue" -> j.optString("name", "")
            "ProjectV2ItemFieldIterationValue" -> j.optString("title", "")
            "ProjectV2ItemFieldRepositoryValue" -> j.optJSONObject("repository")?.optString("nameWithOwner") ?: ""
            "ProjectV2ItemFieldPullRequestValue" -> "${j.optJSONObject("pullRequests")?.optInt("totalCount", 0) ?: 0} pull requests"
            else -> ""
        }
        return GHProjectV2ItemFieldValue(
            fieldId = fieldId,
            fieldName = field.optString("name", ""),
            dataType = type,
            value = value,
            optionId = j.optString("optionId", ""),
            iterationId = j.optString("iterationId", "")
        )
    }

    // ═══════════════════════════════════
    // Packages
    // ═══════════════════════════════════

    suspend fun getUserPackages(context: Context, username: String, packageType: String = "all"): List<GHPackage> {
        if (packageType == "all") {
            return githubPackageTypes.flatMap { getUserPackages(context, username, it) }
                .distinctBy { "${it.packageType}/${it.name}/${it.ownerLogin}" }
        }
        val typeQuery = packageType.takeIf { it.isNotBlank() && it != "all" }?.let { "?package_type=${URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
        val r = request(context, "/users/${URLEncoder.encode(username, "UTF-8")}/packages$typeQuery", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return emptyList()
        return parsePackages(r.body)
    }

    suspend fun getOrgPackages(context: Context, org: String, packageType: String = "all"): List<GHPackage> {
        if (packageType == "all") {
            return githubPackageTypes.flatMap { getOrgPackages(context, org, it) }
                .distinctBy { "${it.packageType}/${it.name}/${it.ownerLogin}" }
        }
        val typeQuery = packageType.takeIf { it.isNotBlank() && it != "all" }?.let { "?package_type=${URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
        val r = request(context, "/orgs/${URLEncoder.encode(org, "UTF-8")}/packages$typeQuery", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return emptyList()
        return parsePackages(r.body)
    }

    suspend fun getPackage(context: Context, ownerType: String, owner: String, packageType: String, packageName: String): GHPackage? {
        val r = request(context, "${packageOwnerPath(ownerType, owner)}/packages/${URLEncoder.encode(packageType, "UTF-8")}/${URLEncoder.encode(packageName, "UTF-8")}", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return null
        return try { parsePackage(JSONObject(r.body)) } catch (e: Exception) { null }
    }

    suspend fun deletePackage(context: Context, ownerType: String, owner: String, packageType: String, packageName: String): Boolean {
        val r = request(context, "${packageOwnerPath(ownerType, owner)}/packages/${URLEncoder.encode(packageType, "UTF-8")}/${URLEncoder.encode(packageName, "UTF-8")}", "DELETE", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        return r.code == 204 || r.success
    }

    suspend fun getPackageVersions(context: Context, ownerType: String, owner: String, packageType: String, packageName: String): List<GHPackageVersion> {
        val r = request(context, "${packageOwnerPath(ownerType, owner)}/packages/${URLEncoder.encode(packageType, "UTF-8")}/${URLEncoder.encode(packageName, "UTF-8")}/versions?per_page=100", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parsePackageVersion) }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun deletePackageVersion(context: Context, ownerType: String, owner: String, packageType: String, packageName: String, versionId: Long): Boolean {
        val r = request(context, "${packageOwnerPath(ownerType, owner)}/packages/${URLEncoder.encode(packageType, "UTF-8")}/${URLEncoder.encode(packageName, "UTF-8")}/versions/$versionId", "DELETE", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        return r.code == 204 || r.success
    }

    private fun packageOwnerPath(ownerType: String, owner: String): String {
        val encodedOwner = URLEncoder.encode(owner, "UTF-8")
        return if (ownerType == "org") "/orgs/$encodedOwner" else "/users/$encodedOwner"
    }

    private val githubPackageTypes = listOf("container", "docker", "npm", "maven", "nuget", "rubygems")

    private fun parsePackages(body: String): List<GHPackage> =
        try {
            val arr = JSONArray(body)
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parsePackage) }
        } catch (e: Exception) { emptyList() }

    private fun parsePackage(j: JSONObject): GHPackage {
        val owner = j.optJSONObject("owner")
        val repository = j.optJSONObject("repository")
        return GHPackage(
            id = j.optLong("id"),
            name = j.optString("name", ""),
            packageType = j.optString("package_type", ""),
            visibility = j.optString("visibility", ""),
            versionCount = j.optInt("version_count", 0),
            url = j.optString("url", ""),
            htmlUrl = j.optString("html_url", ""),
            createdAt = j.optString("created_at", ""),
            updatedAt = j.optString("updated_at", ""),
            ownerLogin = owner?.optString("login") ?: "",
            repositoryName = repository?.optString("full_name") ?: repository?.optString("name") ?: "",
            repositoryUrl = repository?.optString("html_url") ?: ""
        )
    }

    private fun parsePackageVersion(j: JSONObject): GHPackageVersion {
        val metadata = j.optJSONObject("metadata")
        val container = metadata?.optJSONObject("container")
        val tags = container?.optJSONArray("tags")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
        }.orEmpty()
        return GHPackageVersion(
            id = j.optLong("id"),
            name = j.optString("name", ""),
            url = j.optString("url", ""),
            htmlUrl = j.optString("html_url", j.optString("package_html_url", "")),
            createdAt = j.optString("created_at", ""),
            updatedAt = j.optString("updated_at", ""),
            tags = tags,
            packageType = metadata?.optString("package_type", "") ?: "",
            downloadCount = j.optInt("download_count", 0)
        )
    }

    // ═══════════════════════════════════
    // Repository Rulesets
    // ═══════════════════════════════════

    suspend fun getRulesets(context: Context, owner: String, repo: String): List<GHRuleset> {
        val r = request(context, "/repos/$owner/$repo/rulesets?per_page=100", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHRuleset(
                    id = j.optInt("id"),
                    name = j.optString("name"),
                    enforcement = j.optString("enforcement"),
                    rulesCount = j.optJSONArray("rules")?.length() ?: 0,
                    target = j.optString("target", ""),
                    sourceType = j.optString("source_type", ""),
                    createdAt = j.optString("created_at", ""),
                    updatedAt = j.optString("updated_at", ""),
                    htmlUrl = "https://github.com/$owner/$repo/settings/rules/${j.optInt("id")}"
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getRulesetDetail(context: Context, owner: String, repo: String, rulesetId: Int): GHRulesetDetail? {
        val r = request(context, "/repos/$owner/$repo/rulesets/$rulesetId", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return null
        return try {
            val j = JSONObject(r.body)
            GHRulesetDetail(
                id = j.optInt("id"),
                name = j.optString("name", ""),
                target = j.optString("target", ""),
                sourceType = j.optString("source_type", ""),
                source = j.optString("source", ""),
                enforcement = j.optString("enforcement", ""),
                createdAt = j.optString("created_at", ""),
                updatedAt = j.optString("updated_at", ""),
                rules = parseRulesetRules(j.optJSONArray("rules")),
                bypassActors = parseRulesetBypassActors(j.optJSONArray("bypass_actors")),
                refNameIncludes = parseStringArray(j.optJSONObject("conditions")?.optJSONObject("ref_name")?.optJSONArray("include")),
                refNameExcludes = parseStringArray(j.optJSONObject("conditions")?.optJSONObject("ref_name")?.optJSONArray("exclude")),
                htmlUrl = "https://github.com/$owner/$repo/settings/rules/${j.optInt("id")}"
            )
        } catch (e: Exception) { null }
    }

    suspend fun getRuleSuites(context: Context, owner: String, repo: String): List<GHRuleSuite> {
        val r = request(context, "/repos/$owner/$repo/rule-suites?per_page=100", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return emptyList()
        return try {
            val arr = JSONObject(r.body).optJSONArray("rule_suites") ?: JSONArray(r.body)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let(::parseRuleSuite)
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getRuleSuite(context: Context, owner: String, repo: String, suiteId: Long): GHRuleSuite? {
        val r = request(context, "/repos/$owner/$repo/rule-suites/$suiteId", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return null
        return try { parseRuleSuite(JSONObject(r.body)) } catch (e: Exception) { null }
    }

    suspend fun createRuleset(
        context: Context,
        owner: String,
        repo: String,
        name: String,
        target: String,
        enforcement: String,
        includeRefs: List<String>,
        excludeRefs: List<String>,
        rulesJson: String
    ): GHRulesetDetail? {
        val body = buildRulesetPayload(name, target, enforcement, includeRefs, excludeRefs, rulesJson) ?: return null
        val r = request(context, "/repos/$owner/$repo/rulesets", "POST", body, extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return null
        return try { parseRulesetDetail(JSONObject(r.body), owner, repo) } catch (e: Exception) { null }
    }

    suspend fun updateRuleset(
        context: Context,
        owner: String,
        repo: String,
        rulesetId: Int,
        name: String,
        target: String,
        enforcement: String,
        includeRefs: List<String>,
        excludeRefs: List<String>,
        rulesJson: String
    ): GHRulesetDetail? {
        val body = buildRulesetPayload(name, target, enforcement, includeRefs, excludeRefs, rulesJson) ?: return null
        val r = request(context, "/repos/$owner/$repo/rulesets/$rulesetId", "PUT", body, extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return null
        return try { parseRulesetDetail(JSONObject(r.body), owner, repo) } catch (e: Exception) { null }
    }

    suspend fun deleteRuleset(context: Context, owner: String, repo: String, rulesetId: Int): Boolean =
        request(context, "/repos/$owner/$repo/rulesets/$rulesetId", "DELETE", extraHeaders = mapOf("Accept" to "application/vnd.github+json")).let { it.code == 204 || it.success }

    private fun buildRulesetPayload(
        name: String,
        target: String,
        enforcement: String,
        includeRefs: List<String>,
        excludeRefs: List<String>,
        rulesJson: String
    ): String? {
        val rules = try { JSONArray(rulesJson.ifBlank { "[]" }) } catch (e: Exception) { return null }
        return JSONObject().apply {
            put("name", name)
            put("target", target)
            put("enforcement", enforcement)
            put("conditions", JSONObject().apply {
                put("ref_name", JSONObject().apply {
                    put("include", JSONArray(includeRefs.ifEmpty { listOf("~DEFAULT_BRANCH") }))
                    put("exclude", JSONArray(excludeRefs))
                })
            })
            put("rules", rules)
        }.toString()
    }

    private fun parseRulesetDetail(j: JSONObject, owner: String, repo: String): GHRulesetDetail =
        GHRulesetDetail(
            id = j.optInt("id"),
            name = j.optString("name", ""),
            target = j.optString("target", ""),
            sourceType = j.optString("source_type", ""),
            source = j.optString("source", ""),
            enforcement = j.optString("enforcement", ""),
            createdAt = j.optString("created_at", ""),
            updatedAt = j.optString("updated_at", ""),
            rules = parseRulesetRules(j.optJSONArray("rules")),
            bypassActors = parseRulesetBypassActors(j.optJSONArray("bypass_actors")),
            refNameIncludes = parseStringArray(j.optJSONObject("conditions")?.optJSONObject("ref_name")?.optJSONArray("include")),
            refNameExcludes = parseStringArray(j.optJSONObject("conditions")?.optJSONObject("ref_name")?.optJSONArray("exclude")),
            htmlUrl = j.optJSONObject("_links")?.optJSONObject("html")?.optString("href")
                ?: "https://github.com/$owner/$repo/settings/rules/${j.optInt("id")}"
        )

    private fun parseRuleSuite(j: JSONObject): GHRuleSuite =
        GHRuleSuite(
            id = j.optLong("id"),
            actor = j.optJSONObject("actor")?.optString("login") ?: "",
            beforeSha = j.optString("before_sha", ""),
            afterSha = j.optString("after_sha", ""),
            ref = j.optString("ref", ""),
            status = j.optString("status", ""),
            result = j.optString("result", ""),
            evaluationResult = j.optString("evaluation_result", ""),
            createdAt = j.optString("created_at", ""),
            updatedAt = j.optString("updated_at", "")
        )

    private fun parseRulesetRules(arr: JSONArray?): List<GHRulesetRule> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val j = arr.optJSONObject(i) ?: return@mapNotNull null
            val parameters = j.optJSONObject("parameters")
            GHRulesetRule(
                type = j.optString("type", ""),
                parameters = parameters?.keys()?.asSequence()?.map { key ->
                    key to parameters.opt(key).toString()
                }?.toList().orEmpty()
            )
        }
    }

    private fun parseRulesetBypassActors(arr: JSONArray?): List<GHRulesetBypassActor> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val j = arr.optJSONObject(i) ?: return@mapNotNull null
            GHRulesetBypassActor(
                actorId = j.optLong("actor_id"),
                actorType = j.optString("actor_type", ""),
                bypassMode = j.optString("bypass_mode", "")
            )
        }
    }

    private fun parseStringArray(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() && it != "null" } }
    }

    // ═══════════════════════════════════
    // Security - Dependabot Alerts
    // ═══════════════════════════════════

    suspend fun getDependabotAlerts(context: Context, owner: String, repo: String): List<GHDependabotAlert> {
        val r = request(context, "/repos/$owner/$repo/dependabot/alerts?per_page=100", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                parseDependabotAlert(arr.getJSONObject(i))
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getDependabotAlert(context: Context, owner: String, repo: String, number: Int): GHDependabotAlert? {
        val r = request(context, "/repos/$owner/$repo/dependabot/alerts/$number", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return null
        return try { parseDependabotAlert(JSONObject(r.body)) } catch (e: Exception) { null }
    }

    suspend fun getCodeScanningAlerts(context: Context, owner: String, repo: String): List<GHCodeScanningAlert> {
        val r = request(context, "/repos/$owner/$repo/code-scanning/alerts?per_page=100", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                parseCodeScanningAlert(arr.getJSONObject(i))
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getCodeScanningAlert(context: Context, owner: String, repo: String, number: Int): GHCodeScanningAlert? {
        val r = request(context, "/repos/$owner/$repo/code-scanning/alerts/$number", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return null
        return try { parseCodeScanningAlert(JSONObject(r.body)) } catch (e: Exception) { null }
    }

    suspend fun getSecretScanningAlerts(context: Context, owner: String, repo: String): List<GHSecretScanningAlert> {
        val r = request(context, "/repos/$owner/$repo/secret-scanning/alerts?per_page=100", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                parseSecretScanningAlert(arr.getJSONObject(i))
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getSecretScanningAlert(context: Context, owner: String, repo: String, number: Int): GHSecretScanningAlert? {
        val r = request(context, "/repos/$owner/$repo/secret-scanning/alerts/$number", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return null
        return try { parseSecretScanningAlert(JSONObject(r.body)) } catch (e: Exception) { null }
    }

    suspend fun getRepositorySecurityAdvisories(context: Context, owner: String, repo: String): List<GHRepositorySecurityAdvisory> {
        val r = request(context, "/repos/$owner/$repo/security-advisories?per_page=100&sort=updated&direction=desc", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let(::parseRepositorySecurityAdvisory)
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getRepositorySecurityAdvisory(context: Context, owner: String, repo: String, ghsaId: String): GHRepositorySecurityAdvisory? {
        val encoded = URLEncoder.encode(ghsaId, "UTF-8")
        val r = request(context, "/repos/$owner/$repo/security-advisories/$encoded", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return null
        return try { parseRepositorySecurityAdvisory(JSONObject(r.body)) } catch (e: Exception) { null }
    }

    suspend fun getCommunityProfile(context: Context, owner: String, repo: String): GHCommunityProfile? {
        val r = request(context, "/repos/$owner/$repo/community/profile", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        if (!r.success) return null
        return try {
            val j = JSONObject(r.body)
            val files = j.optJSONObject("files")
            GHCommunityProfile(
                healthPercentage = j.optInt("health_percentage", 0),
                description = j.optString("description", ""),
                documentationUrl = j.optString("documentation_url", ""),
                updatedAt = j.optString("updated_at", ""),
                files = parseCommunityFiles(files)
            )
        } catch (e: Exception) { null }
    }

    suspend fun getRepositorySecuritySettings(context: Context, owner: String, repo: String): GHRepositorySecuritySettings {
        val automated = request(context, "/repos/$owner/$repo/automated-security-fixes", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        val vulnerability = request(context, "/repos/$owner/$repo/vulnerability-alerts", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        val privateReporting = request(context, "/repos/$owner/$repo/private-vulnerability-reporting", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        return GHRepositorySecuritySettings(
            automatedSecurityFixes = automated.success && parseEnabledFlag(automated.body, default = true),
            automatedSecurityFixesPaused = parsePausedFlag(automated.body),
            vulnerabilityAlerts = vulnerability.success || vulnerability.code == 204,
            privateVulnerabilityReporting = privateReporting.success && parseEnabledFlag(privateReporting.body, default = true)
        )
    }

    suspend fun setAutomatedSecurityFixes(context: Context, owner: String, repo: String, enabled: Boolean): Boolean {
        val r = request(context, "/repos/$owner/$repo/automated-security-fixes", if (enabled) "PUT" else "DELETE", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        return r.code == 204 || r.success
    }

    suspend fun setVulnerabilityAlerts(context: Context, owner: String, repo: String, enabled: Boolean): Boolean {
        val r = request(context, "/repos/$owner/$repo/vulnerability-alerts", if (enabled) "PUT" else "DELETE", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        return r.code == 204 || r.success
    }

    suspend fun setPrivateVulnerabilityReporting(context: Context, owner: String, repo: String, enabled: Boolean): Boolean {
        val r = request(context, "/repos/$owner/$repo/private-vulnerability-reporting", if (enabled) "PUT" else "DELETE", extraHeaders = mapOf("Accept" to "application/vnd.github+json"))
        return r.code == 204 || r.success
    }

    private fun parseDependabotAlert(j: JSONObject): GHDependabotAlert {
        val adv = j.optJSONObject("security_advisory")
        return GHDependabotAlert(
            number = j.optInt("number"),
            state = j.optString("state"),
            severity = adv?.optString("severity") ?: "",
            summary = adv?.optString("summary") ?: "",
            description = adv?.optString("description") ?: "",
            packageName = j.optJSONObject("dependency")?.optJSONObject("package")?.optString("name") ?: "",
            ecosystem = j.optJSONObject("dependency")?.optJSONObject("package")?.optString("ecosystem") ?: "",
            manifestPath = j.optJSONObject("dependency")?.optString("manifest_path") ?: "",
            vulnerableRequirements = j.optJSONObject("dependency")?.optString("vulnerable_requirements") ?: "",
            ghsaId = adv?.optString("ghsa_id") ?: "",
            cveId = adv?.optString("cve_id") ?: "",
            htmlUrl = j.optString("html_url", ""),
            createdAt = j.optString("created_at", ""),
            updatedAt = j.optString("updated_at", ""),
            fixedIn = adv?.optJSONArray("vulnerabilities")?.let { vulns ->
                (0 until vulns.length()).mapNotNull { index ->
                    vulns.optJSONObject(index)?.optJSONObject("first_patched_version")?.optString("identifier")?.takeIf { it.isNotBlank() }
                }.distinct()
            } ?: emptyList()
        )
    }

    private fun parseCodeScanningAlert(j: JSONObject): GHCodeScanningAlert {
        val rule = j.optJSONObject("rule")
        val tool = j.optJSONObject("tool")
        val instance = j.optJSONObject("most_recent_instance")
        val message = instance?.optJSONObject("message")
        val location = instance?.optJSONObject("location")
        return GHCodeScanningAlert(
            number = j.optInt("number"),
            state = j.optString("state", ""),
            ruleId = rule?.optString("id") ?: "",
            ruleName = rule?.optString("name") ?: "",
            severity = rule?.optString("security_severity_level")?.takeIf { it.isNotBlank() && it != "null" }
                ?: rule?.optString("severity") ?: "",
            description = rule?.optString("description") ?: "",
            toolName = tool?.optString("name") ?: "",
            message = message?.optString("text") ?: "",
            path = location?.optString("path") ?: "",
            startLine = location?.optInt("start_line", 0) ?: 0,
            ref = instance?.optString("ref") ?: "",
            category = instance?.optString("category") ?: "",
            createdAt = j.optString("created_at", ""),
            fixedAt = j.optString("fixed_at", ""),
            dismissedAt = j.optString("dismissed_at", ""),
            dismissedReason = j.optString("dismissed_reason", ""),
            htmlUrl = j.optString("html_url", "")
        )
    }

    private fun parseSecretScanningAlert(j: JSONObject): GHSecretScanningAlert =
        GHSecretScanningAlert(
            number = j.optInt("number"),
            state = j.optString("state", ""),
            resolution = j.optString("resolution", ""),
            secretType = j.optString("secret_type", ""),
            secretTypeDisplayName = j.optString("secret_type_display_name", ""),
            secret = j.optString("secret", ""),
            validity = j.optString("validity", ""),
            public = j.optBoolean("public", false),
            pushProtectionBypassed = j.optBoolean("push_protection_bypassed", false),
            createdAt = j.optString("created_at", ""),
            resolvedAt = j.optString("resolved_at", ""),
            htmlUrl = j.optString("html_url", "")
        )

    private fun parseRepositorySecurityAdvisory(j: JSONObject): GHRepositorySecurityAdvisory =
        GHRepositorySecurityAdvisory(
            ghsaId = j.optString("ghsa_id", ""),
            cveId = j.optString("cve_id", ""),
            url = j.optString("url", ""),
            htmlUrl = j.optString("html_url", ""),
            summary = j.optString("summary", ""),
            description = j.optString("description", ""),
            severity = j.optString("severity", ""),
            state = j.optString("state", ""),
            publishedAt = j.optString("published_at", ""),
            updatedAt = j.optString("updated_at", ""),
            withdrawnAt = j.optString("withdrawn_at", ""),
            cvssScore = j.optJSONObject("cvss")?.optDouble("score", 0.0) ?: 0.0,
            cweIds = parseStringArray(j.optJSONArray("cwe_ids")),
            vulnerabilities = parseSecurityAdvisoryVulnerabilities(j.optJSONArray("vulnerabilities"))
        )

    private fun parseCommunityFiles(files: JSONObject?): List<GHCommunityProfileFile> {
        if (files == null) return emptyList()
        return files.keys().asSequence().map { key ->
            val value = files.opt(key)
            val objectValue = value as? JSONObject
            GHCommunityProfileFile(
                key = key,
                name = objectValue?.optString("name")?.takeIf { it.isNotBlank() } ?: key.replace('_', ' '),
                htmlUrl = objectValue?.optString("html_url") ?: "",
                present = objectValue != null && value.toString() != "null"
            )
        }.toList()
    }

    private fun parseSecurityAdvisoryVulnerabilities(arr: JSONArray?): List<GHAdvisoryVulnerability> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { j ->
                val pkg = j.optJSONObject("package")
                GHAdvisoryVulnerability(
                    ecosystem = pkg?.optString("ecosystem", "") ?: "",
                    packageName = pkg?.optString("name", "") ?: "",
                    vulnerableRange = j.optString("vulnerable_version_range", ""),
                    patchedVersions = j.optString("patched_versions", "")
                )
            }
        }
    }

    private fun parseEnabledFlag(body: String, default: Boolean): Boolean =
        try {
            if (body.isBlank()) default else JSONObject(body).optBoolean("enabled", default)
        } catch (_: Exception) {
            default
        }

    private fun parsePausedFlag(body: String): Boolean =
        try {
            if (body.isBlank()) false else JSONObject(body).optBoolean("paused", false)
        } catch (_: Exception) {
            false
        }

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
        owner = j.optJSONObject("owner")?.optString("login") ?: "",
        htmlUrl = j.optString("html_url", ""),
        isArchived = j.optBoolean("archived", false),
        isTemplate = j.optBoolean("is_template", false),
        id = j.optLong("id", 0L),
        permissions = j.optJSONObject("permissions")?.let { p ->
            GHPermissions(
                admin = p.optBoolean("admin", false),
                maintain = p.optBoolean("maintain", false),
                push = p.optBoolean("push", false),
                triage = p.optBoolean("triage", false),
                pull = p.optBoolean("pull", false)
            )
        }
    )

    private fun parseAppInstallation(j: JSONObject): GHAppInstallation {
        val account = j.optJSONObject("account")
        val suspendedBy = j.optJSONObject("suspended_by")
        return GHAppInstallation(
            id = j.optLong("id"),
            appId = j.optLong("app_id"),
            appSlug = j.optString("app_slug", ""),
            targetId = j.optLong("target_id"),
            targetType = j.optString("target_type", ""),
            targetLogin = account?.optString("login") ?: "",
            targetAvatarUrl = account?.optString("avatar_url") ?: "",
            repositorySelection = j.optString("repository_selection", ""),
            permissions = parseStringMap(j.optJSONObject("permissions")),
            events = parseStringArray(j.optJSONArray("events")),
            singleFileName = j.optString("single_file_name", ""),
            singleFilePaths = parseStringArray(j.optJSONArray("single_file_paths")),
            htmlUrl = j.optString("html_url", ""),
            createdAt = j.optString("created_at", ""),
            updatedAt = j.optString("updated_at", ""),
            suspendedAt = j.optString("suspended_at", ""),
            suspendedBy = suspendedBy?.optString("login") ?: ""
        )
    }

    private fun parseStringMap(j: JSONObject?): List<Pair<String, String>> {
        if (j == null) return emptyList()
        return j.keys().asSequence()
            .mapNotNull { key -> key.takeIf { it.isNotBlank() }?.let { it to j.optString(it, "") } }
            .sortedBy { it.first }
            .toList()
    }

    data class ApiResult(val success: Boolean, val body: String, val code: Int)

    private fun apiErrorMessage(result: ApiResult): String {
        val fallback = if (result.code > 0) "HTTP ${result.code}" else "Network error"
        if (result.body.isBlank()) return fallback
        return try {
            val json = JSONObject(result.body)
            val message = json.optString("message").takeIf { it.isNotBlank() }
            val errors = json.optJSONArray("errors")
            val details = if (errors != null) {
                (0 until errors.length()).mapNotNull { index ->
                    val item = errors.opt(index)
                    when (item) {
                        is JSONObject -> listOf(
                            item.optString("field"),
                            item.optString("code"),
                            item.optString("message")
                        ).filter { it.isNotBlank() && it != "null" }.joinToString(" ")
                        else -> item?.toString()
                    }?.takeIf { it.isNotBlank() && it != "null" }
                }.take(3).joinToString("; ")
            } else {
                ""
            }
            listOfNotNull(message, details.takeIf { it.isNotBlank() }).joinToString(": ").ifBlank { fallback }
        } catch (_: Exception) {
            result.body.trim().take(220).ifBlank { fallback }
        }
    }
}

data class GHUser(val login: String, val name: String, val avatarUrl: String, val bio: String,
    val publicRepos: Int, val privateRepos: Int, val followers: Int, val following: Int)

data class GHRepo(val name: String, val fullName: String, val description: String, val language: String,
    val stars: Int, val forks: Int, val isPrivate: Boolean, val isFork: Boolean, val defaultBranch: String,
    val updatedAt: String, val owner: String, val htmlUrl: String = "", val isArchived: Boolean = false,
    val isTemplate: Boolean = false,
    val id: Long = 0L,
    val permissions: GHPermissions? = null)

/**
 * Mirror of the `permissions` object returned by the GitHub REST API for a repo.
 * Only present on responses authenticated as a user; null for unauthenticated /
 * search results / older endpoints.
 */
data class GHPermissions(
    val admin: Boolean = false,
    val maintain: Boolean = false,
    val push: Boolean = false,
    val triage: Boolean = false,
    val pull: Boolean = false
)

/** True if the current user can push commits / edit content / run workflows. */
fun GHRepo.canWrite(): Boolean = permissions?.let { it.push || it.maintain || it.admin } == true

/** True if the current user has full admin rights (settings, webhooks, collaborators). */
fun GHRepo.canAdmin(): Boolean = permissions?.admin == true

data class GHActionResult(val success: Boolean, val code: Int, val message: String)

data class GHAppInstallationsPage(
    val installations: List<GHAppInstallation> = emptyList(),
    val totalCount: Int = 0,
    val error: String = "",
    val code: Int = 0
)

data class GHAppInstallationReposPage(
    val repositories: List<GHRepo> = emptyList(),
    val totalCount: Int = 0,
    val error: String = "",
    val code: Int = 0
)

data class GHAppInstallation(
    val id: Long,
    val appId: Long,
    val appSlug: String,
    val targetId: Long,
    val targetType: String,
    val targetLogin: String,
    val targetAvatarUrl: String,
    val repositorySelection: String,
    val permissions: List<Pair<String, String>>,
    val events: List<String>,
    val singleFileName: String,
    val singleFilePaths: List<String>,
    val htmlUrl: String,
    val createdAt: String,
    val updatedAt: String,
    val suspendedAt: String,
    val suspendedBy: String
)

data class GHCommit(val sha: String, val message: String, val author: String, val date: String, val avatarUrl: String)

data class GHIssue(val number: Int, val title: String, val state: String, val author: String,
    val createdAt: String, val comments: Int, val isPR: Boolean)

data class GHIssueEvent(
    val id: Long,
    val event: String,
    val actor: String,
    val createdAt: String,
    val issueNumber: Int,
    val issueTitle: String,
    val label: String,
    val assignee: String,
    val milestone: String,
    val renameFrom: String,
    val renameTo: String,
    val commitId: String,
    val url: String = "",
    val commitUrl: String = "",
    val authorAssociation: String = "",
    val stateReason: String = "",
    val performedViaGithubApp: String = ""
)

data class GHIssueDetail(val number: Int, val title: String, val body: String, val state: String,
    val author: String, val avatarUrl: String, val createdAt: String, val comments: Int,
    val labels: List<String>, val isPR: Boolean, val assignee: String, val milestoneTitle: String = "",
    val locked: Boolean = false, val activeLockReason: String = "")

data class GHContent(val name: String, val path: String, val type: String, val size: Long,
    val downloadUrl: String, val sha: String)

data class GHFileSaveResult(val success: Boolean, val sha: String, val error: String)

data class GHGitRef(
    val ref: String,
    val nodeSha: String,
    val nodeType: String,
    val url: String
)

data class GHGitTree(
    val sha: String,
    val truncated: Boolean,
    val items: List<GHGitTreeItem>
)

data class GHGitTreeItem(
    val path: String,
    val mode: String,
    val type: String,
    val sha: String,
    val size: Long,
    val url: String
)

data class GHGitBlob(
    val sha: String,
    val size: Long,
    val encoding: String,
    val content: String,
    val url: String
)

data class GHGitTagDetail(
    val sha: String,
    val tag: String,
    val message: String,
    val taggerName: String,
    val taggerEmail: String,
    val date: String,
    val objectSha: String,
    val objectType: String
)

data class GHGitCommit(
    val sha: String,
    val message: String,
    val treeSha: String,
    val parentShas: List<String>,
    val authorName: String,
    val authorEmail: String,
    val authorDate: String,
    val committerName: String,
    val committerEmail: String,
    val committerDate: String
)

data class GHPullRequest(val number: Int, val title: String, val state: String, val author: String,
    val createdAt: String, val head: String, val base: String, val comments: Int,
    val merged: Boolean, val body: String,
    val draft: Boolean = false,
    val htmlUrl: String = "",
    val headSha: String = "",
    val mergeable: Boolean? = null,
    val mergeableState: String = "",
    val reviewComments: Int = 0,
    val commits: Int = 0,
    val additions: Int = 0,
    val deletions: Int = 0,
    val changedFiles: Int = 0,
    val requestedReviewers: List<String> = emptyList())

data class GHPullMergeStatus(
    val merged: Boolean,
    val checked: Boolean,
    val code: Int,
    val message: String
)

data class GHPullReview(
    val id: Long,
    val user: String,
    val state: String,
    val body: String,
    val submittedAt: String,
    val commitId: String,
    val htmlUrl: String
)

data class GHComment(val id: Long, val body: String, val author: String, val avatarUrl: String, val createdAt: String)

data class GHContributor(val login: String, val avatarUrl: String, val contributions: Int)

data class GHTrafficSeries(
    val count: Int,
    val uniques: Int,
    val items: List<GHTrafficPoint>
)

data class GHTrafficPoint(
    val timestamp: String,
    val count: Int,
    val uniques: Int
)

data class GHTrafficReferrer(
    val referrer: String,
    val count: Int,
    val uniques: Int
)

data class GHTrafficPath(
    val path: String,
    val title: String,
    val count: Int,
    val uniques: Int
)

data class GHRepoPerson(
    val login: String,
    val avatarUrl: String,
    val htmlUrl: String,
    val starredAt: String = ""
)

data class GHRepoEvent(
    val id: String,
    val type: String,
    val actor: String,
    val createdAt: String,
    val action: String,
    val ref: String,
    val refType: String,
    val size: Int
)

data class GHRelease(
    val tag: String,
    val name: String,
    val body: String,
    val prerelease: Boolean,
    val createdAt: String,
    val assets: List<GHAsset>,
    val id: Long = 0L,
    val draft: Boolean = false,
    val htmlUrl: String = "",
    val uploadUrl: String = ""
)

data class GHAsset(
    val name: String,
    val size: Long,
    val downloadUrl: String,
    val downloadCount: Int,
    val id: Long = 0L,
    val contentType: String = "",
    val state: String = ""
)

data class GHGist(val id: String, val description: String, val isPublic: Boolean, val files: List<String>,
    val createdAt: String, val updatedAt: String)

data class GHCommitDetail(val sha: String, val message: String, val author: String, val date: String,
    val files: List<GHDiffFile>, val totalAdditions: Int, val totalDeletions: Int)

data class GHDiffFile(val filename: String, val status: String, val additions: Int, val deletions: Int, val patch: String)

data class GHWorkflow(
    val id: Long,
    val name: String,
    val state: String,
    val path: String,
    val htmlUrl: String = "",
    val badgeUrl: String = "",
    val createdAt: String = "",
    val updatedAt: String = ""
)

data class GHWorkflowDispatchInput(
    val key: String,
    val description: String,
    val required: Boolean,
    val defaultValue: String,
    val type: String,
    val options: List<String>
)

data class GHWorkflowDispatchSchema(
    val workflowPath: String,
    val workflowName: String,
    val inputs: List<GHWorkflowDispatchInput>
)

data class GHWorkflowRun(val id: Long, val name: String, val status: String, val conclusion: String,
    val branch: String, val event: String, val createdAt: String, val updatedAt: String,
    val runNumber: Int, val actor: String, val actorAvatar: String, val workflowId: Long,
    val displayTitle: String = "", val headSha: String = "", val headRepository: String = "",
    val runAttempt: Int = 1, val htmlUrl: String = "", val cancelUrl: String = "",
    val rerunUrl: String = "", val checkSuiteId: Long = 0)

data class GHJob(val id: Long, val name: String, val status: String, val conclusion: String,
    val startedAt: String, val completedAt: String, val steps: List<GHStep>)

data class GHStep(val name: String, val status: String, val conclusion: String, val number: Int)

data class GHNotification(
    val id: String,
    val unread: Boolean,
    val reason: String,
    val title: String,
    val type: String,
    val repoName: String,
    val updatedAt: String,
    val url: String,
    val lastReadAt: String? = null,
    val subjectUrl: String = "",
    val repositoryUrl: String = "",
    val htmlUrl: String = ""
) {
    /** Alias for spec parity. */
    val subjectTitle: String get() = title
    val subjectType: String get() = type
    val repositoryFullName: String get() = repoName
}

data class GHThreadSubscription(
    val subscribed: Boolean,
    val ignored: Boolean,
    val reason: String,
    val createdAt: String,
    val url: String
)

data class GHArtifact(val id: Long, val name: String, val sizeInBytes: Long,
    val expired: Boolean, val createdAt: String, val expiresAt: String,
    val updatedAt: String = "", val digest: String = "", val workflowRunId: Long = 0,
    val workflowRunBranch: String = "", val workflowRunSha: String = "")

data class GHCheckAnnotation(val path: String, val startLine: Int, val endLine: Int,
    val annotationLevel: String, val message: String, val title: String, val rawDetails: String)

data class GHPendingDeployment(val environmentId: Long, val environmentName: String,
    val currentUserCanApprove: Boolean, val waitTimer: Int, val waitTimerStartedAt: String,
    val reviewers: List<String>)

data class GHWorkflowRunReview(val state: String, val comment: String, val user: String,
    val environments: List<String>)

data class GHActionsUsage(val runDurationMs: Long, val billableMs: Map<String, Long>,
    val billableMinutes: Map<String, Int>)

data class GHActionsCacheUsage(val fullName: String, val activeCachesSizeInBytes: Long,
    val activeCachesCount: Int)

data class GHActionsCacheEntry(val id: Long, val ref: String, val key: String, val version: String,
    val lastAccessedAt: String, val createdAt: String, val sizeInBytes: Long)

data class GHActionPublicKey(val keyId: String, val key: String)

data class GHActionSecret(val name: String, val createdAt: String, val updatedAt: String)

data class GHActionVariable(val name: String, val value: String, val createdAt: String, val updatedAt: String)

data class GHActionRunner(val id: Long, val name: String, val os: String, val status: String,
    val busy: Boolean, val labels: List<String>)

data class GHActionRunnerGroup(
    val id: Long,
    val name: String,
    val visibility: String,
    val isDefault: Boolean,
    val inherited: Boolean,
    val allowsPublicRepositories: Boolean,
    val restrictedToWorkflows: Boolean,
    val selectedWorkflows: List<String>,
    val runnersUrl: String,
    val selectedRepositoriesUrl: String
)

data class GHAuditLogEntry(
    val id: String,
    val action: String,
    val actor: String,
    val createdAt: String,
    val org: String,
    val repo: String,
    val user: String,
    val operationType: String,
    val transportProtocol: String
)

data class GHScimUsersPage(
    val totalResults: Int = 0,
    val startIndex: Int = 1,
    val itemsPerPage: Int = 0,
    val users: List<GHScimUser> = emptyList(),
    val error: String = ""
)

data class GHScimUser(
    val id: String,
    val userName: String,
    val displayName: String,
    val givenName: String,
    val familyName: String,
    val active: Boolean,
    val externalId: String,
    val emails: List<String>
)

data class GHSamlAuthorization(
    val login: String,
    val credentialId: Long,
    val credentialType: String,
    val tokenLastEight: String,
    val authorizedAt: String,
    val accessedAt: String,
    val expiresAt: String,
    val scopes: List<String>
)

data class GHOAuthTokenInfo(
    val id: Long,
    val url: String,
    val appName: String,
    val appUrl: String,
    val clientId: String,
    val tokenLastEight: String,
    val note: String,
    val noteUrl: String,
    val createdAt: String,
    val updatedAt: String,
    val scopes: List<String>,
    val fingerprint: String,
    val token: String = ""
)

data class GHRunnerToken(val token: String, val expiresAt: String)

data class GHActionsPermissions(val enabled: Boolean, val allowedActions: String,
    val selectedActionsUrl: String)

data class GHWorkflowPermissions(val defaultWorkflowPermissions: String,
    val canApprovePullRequestReviews: Boolean)

data class GHActionsRetention(val days: Int)

data class GHCodeResult(val name: String, val path: String, val sha: String, val htmlUrl: String, val score: Double)

data class GHSearchIssueResult(
    val id: Long,
    val number: Int,
    val title: String,
    val body: String,
    val state: String,
    val author: String,
    val avatarUrl: String,
    val comments: Int,
    val labels: List<String>,
    val createdAt: String,
    val updatedAt: String,
    val htmlUrl: String,
    val repository: String,
    val isPullRequest: Boolean,
    val score: Double
)

data class GHSearchCommitResult(
    val sha: String,
    val message: String,
    val author: String,
    val avatarUrl: String,
    val date: String,
    val repository: String,
    val htmlUrl: String,
    val score: Double
)

data class GHTopicSearchResult(
    val name: String,
    val displayName: String,
    val shortDescription: String,
    val description: String,
    val createdBy: String,
    val released: String,
    val updatedAt: String,
    val featured: Boolean,
    val curated: Boolean,
    val score: Double,
    val aliases: List<String>,
    val related: List<String>
)

data class GHLabelSearchResult(
    val name: String,
    val color: String,
    val description: String,
    val repository: String,
    val score: Double
)

data class GHUserProfile(val login: String, val name: String, val avatarUrl: String, val bio: String,
    val company: String, val location: String, val blog: String,
    val publicRepos: Int, val followers: Int, val following: Int, val createdAt: String)

data class GHOrg(val login: String, val avatarUrl: String, val description: String)

data class GHLabel(val name: String, val color: String, val description: String)

data class GHMilestone(val number: Int, val title: String, val description: String, val state: String,
    val openIssues: Int, val closedIssues: Int, val dueOn: String)


data class GHEmailEntry(val email: String, val primary: Boolean, val verified: Boolean, val visibility: String)
data class GHUserKeyEntry(val id: Long, val title: String, val key: String, val createdAt: String, val kind: String)
data class GHSocialAccountEntry(val provider: String, val url: String)
data class GHFollowerEntry(val login: String, val avatarUrl: String)
data class GHBlockedEntry(val login: String, val avatarUrl: String)
data class GHInteractionLimitEntry(val limit: String, val expiry: String?)
data class GHUserLite(val login: String, val avatarUrl: String = "")
data class GHPullFile(val filename: String, val status: String, val additions: Int, val deletions: Int, val patch: String)

data class GHRepoSettings(
    val name: String,
    val description: String,
    val homepage: String,
    val isPrivate: Boolean,
    val hasIssues: Boolean,
    val hasProjects: Boolean,
    val hasWiki: Boolean,
    val hasDiscussions: Boolean,
    val allowForking: Boolean,
    val isTemplate: Boolean,
    val archived: Boolean,
    val disabled: Boolean,
    val defaultBranch: String,
    val topics: List<String>,
    val allowMergeCommit: Boolean,
    val allowSquashMerge: Boolean,
    val allowRebaseMerge: Boolean,
    val deleteBranchOnMerge: Boolean
)

data class GHTag(
    val name: String,
    val zipballUrl: String,
    val tarballUrl: String,
    val commitSha: String,
    val commitUrl: String
)

data class GHBranchProtection(
    val enabled: Boolean,
    val requiredStatusChecks: GHRequiredStatusChecks?,
    val requiredPRReviews: GHRequiredPRReviews?,
    val restrictions: GHBranchRestrictions?,
    val allowForcePushes: Boolean,
    val allowDeletions: Boolean,
    val requiredConversationResolution: Boolean,
    val enforceAdmins: Boolean,
    val requiredSignatures: Boolean = false
)

data class GHRequiredStatusChecks(
    val strict: Boolean,
    val contexts: List<String>
)

data class GHRequiredPRReviews(
    val requiredApprovingReviewCount: Int,
    val dismissStaleReviews: Boolean,
    val requireCodeOwnerReviews: Boolean
)

data class GHBranchRestrictions(
    val users: List<String>,
    val teams: List<String>
)

data class GHCollaborator(
    val login: String,
    val avatarUrl: String,
    val role: String
)

data class GHRepoInvitation(
    val id: Long,
    val invitee: String,
    val inviter: String,
    val permissions: String,
    val createdAt: String,
    val expired: Boolean,
    val htmlUrl: String
)

data class GHRepoTeam(
    val id: Long,
    val name: String,
    val slug: String,
    val description: String,
    val privacy: String,
    val permission: String,
    val membersCount: Int,
    val reposCount: Int,
    val htmlUrl: String,
    val organization: String
)

data class GHOrgTeam(
    val id: Long,
    val name: String,
    val slug: String,
    val description: String,
    val privacy: String,
    val permission: String,
    val membersCount: Int,
    val reposCount: Int,
    val htmlUrl: String
)

data class GHReviewComment(
    val id: Long,
    val body: String,
    val path: String,
    val line: Int,
    val originalLine: Int,
    val diffHunk: String,
    val author: String,
    val avatarUrl: String,
    val createdAt: String,
    val inReplyToId: Long?
)

data class GHCheckRun(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String,
    val detailsUrl: String,
    val startedAt: String,
    val completedAt: String,
    val outputTitle: String = "",
    val outputSummary: String = "",
    val htmlUrl: String = "",
    val title: String = outputTitle,
    val summary: String = outputSummary,
    val annotationsCount: Int = 0
)

data class GHCheckSuite(
    val id: Long,
    val status: String,
    val conclusion: String,
    val app: String,
    val headBranch: String,
    val headSha: String,
    val before: String,
    val after: String,
    val createdAt: String,
    val updatedAt: String,
    val latestCheckRunsCount: Int
)

data class GHCompareResult(
    val status: String,
    val aheadBy: Int,
    val behindBy: Int,
    val totalCommits: Int,
    val files: List<GHDiffFile>,
    val commits: List<GHCommit> = emptyList(),
    val htmlUrl: String = ""
)

data class GHReaction(
    val id: Long,
    val content: String,
    val user: String
)

data class GHTimelineEvent(
    val id: Long,
    val event: String,
    val actor: String,
    val createdAt: String,
    val label: String,
    val milestone: String,
    val assignee: String,
    val source: String
)

data class GHWebhook(
    val id: Long,
    val name: String,
    val url: String,
    val events: List<String>,
    val active: Boolean,
    val contentType: String = "json",
    val insecureSsl: String = "0",
    val createdAt: String = "",
    val updatedAt: String = "",
    val lastResponseCode: Int = 0,
    val lastResponseStatus: String = "",
    val lastResponseMessage: String = ""
)

data class GHWebhookConfig(
    val url: String,
    val contentType: String,
    val insecureSsl: String,
    val secret: String = ""
)

data class GHWebhookDelivery(
    val id: Long,
    val guid: String,
    val event: String,
    val action: String,
    val deliveredAt: String,
    val duration: Double,
    val status: String,
    val statusCode: Int,
    val redelivery: Boolean,
    val requestHeaders: List<Pair<String, String>> = emptyList(),
    val requestPayload: String = "",
    val responseHeaders: List<Pair<String, String>> = emptyList(),
    val responsePayload: String = ""
)

data class GHDiscussion(
    val id: String = "",
    val number: Int,
    val title: String,
    val body: String,
    val author: String,
    val avatarUrl: String = "",
    val state: String,
    val comments: Int,
    val createdAt: String,
    val updatedAt: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val categoryEmoji: String = "",
    val isAnswerable: Boolean = false,
    val isAnswered: Boolean = false,
    val locked: Boolean = false,
    val upvotes: Int = 0,
    val htmlUrl: String = ""
)

data class GHDiscussionCategory(
    val id: String,
    val name: String,
    val slug: String,
    val emoji: String,
    val description: String,
    val isAnswerable: Boolean
)

data class GHProject(
    val id: Long,
    val nodeId: String,
    val name: String,
    val body: String,
    val state: String,
    val number: Int,
    val columnsUrl: String,
    val htmlUrl: String,
    val createdAt: String,
    val updatedAt: String,
    val creator: String
)

data class GHProjectColumn(
    val id: Long,
    val nodeId: String,
    val name: String,
    val cardsUrl: String,
    val createdAt: String,
    val updatedAt: String
)

data class GHProjectCard(
    val id: Long,
    val nodeId: String,
    val note: String,
    val creator: String,
    val contentUrl: String,
    val archived: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val columnUrl: String
)

data class GHProjectV2(
    val id: String,
    val number: Int,
    val title: String,
    val shortDescription: String,
    val url: String,
    val closed: Boolean,
    val isPublic: Boolean,
    val updatedAt: String,
    val itemsCount: Int
)

data class GHProjectV2Detail(
    val id: String,
    val number: Int,
    val title: String,
    val shortDescription: String,
    val readme: String,
    val url: String,
    val closed: Boolean,
    val isPublic: Boolean,
    val updatedAt: String,
    val itemsCount: Int,
    val fields: List<GHProjectV2Field>,
    val items: List<GHProjectV2Item>,
    val views: List<GHProjectV2View> = emptyList(),
    val workflows: List<GHProjectV2Workflow> = emptyList()
)

data class GHProjectV2Field(
    val id: String,
    val name: String,
    val dataType: String,
    val options: List<GHProjectV2FieldOption> = emptyList()
)

data class GHProjectV2FieldOption(
    val id: String,
    val name: String,
    val color: String = "",
    val description: String = ""
)

data class GHProjectV2View(
    val id: String,
    val number: Int,
    val name: String,
    val layout: String,
    val filter: String,
    val updatedAt: String,
    val fields: List<String>
)

data class GHProjectV2Workflow(
    val id: String,
    val number: Int,
    val name: String,
    val enabled: Boolean,
    val updatedAt: String
)

data class GHProjectV2Item(
    val id: String,
    val type: String,
    val contentId: String,
    val contentType: String,
    val title: String,
    val body: String,
    val number: Int,
    val state: String,
    val url: String,
    val creator: String,
    val archived: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val fieldValues: List<GHProjectV2ItemFieldValue>
)

data class GHProjectV2ItemFieldValue(
    val fieldId: String,
    val fieldName: String,
    val dataType: String,
    val value: String,
    val optionId: String = "",
    val iterationId: String = ""
)

data class GHPackage(
    val id: Long,
    val name: String,
    val packageType: String,
    val visibility: String,
    val versionCount: Int,
    val url: String,
    val htmlUrl: String,
    val createdAt: String,
    val updatedAt: String,
    val ownerLogin: String,
    val repositoryName: String,
    val repositoryUrl: String
)

data class GHPackageVersion(
    val id: Long,
    val name: String,
    val url: String,
    val htmlUrl: String,
    val createdAt: String,
    val updatedAt: String,
    val tags: List<String>,
    val packageType: String,
    val downloadCount: Int
)

data class GHRuleset(
    val id: Int,
    val name: String,
    val enforcement: String,
    val rulesCount: Int,
    val target: String = "",
    val sourceType: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val htmlUrl: String = ""
)

data class GHRulesetDetail(
    val id: Int,
    val name: String,
    val target: String,
    val sourceType: String,
    val source: String,
    val enforcement: String,
    val createdAt: String,
    val updatedAt: String,
    val rules: List<GHRulesetRule>,
    val bypassActors: List<GHRulesetBypassActor>,
    val refNameIncludes: List<String>,
    val refNameExcludes: List<String>,
    val htmlUrl: String
)

data class GHRulesetRule(
    val type: String,
    val parameters: List<Pair<String, String>>
)

data class GHRulesetBypassActor(
    val actorId: Long,
    val actorType: String,
    val bypassMode: String
)

data class GHRuleSuite(
    val id: Long,
    val actor: String,
    val beforeSha: String,
    val afterSha: String,
    val ref: String,
    val status: String,
    val result: String,
    val evaluationResult: String,
    val createdAt: String,
    val updatedAt: String
)

data class GHDependabotAlert(
    val number: Int,
    val state: String,
    val severity: String,
    val summary: String,
    val description: String,
    val packageName: String,
    val createdAt: String,
    val ecosystem: String = "",
    val manifestPath: String = "",
    val vulnerableRequirements: String = "",
    val ghsaId: String = "",
    val cveId: String = "",
    val htmlUrl: String = "",
    val updatedAt: String = "",
    val fixedIn: List<String> = emptyList()
)

data class GHCodeScanningAlert(
    val number: Int,
    val state: String,
    val ruleId: String,
    val ruleName: String,
    val severity: String,
    val description: String,
    val toolName: String,
    val message: String,
    val path: String,
    val startLine: Int,
    val ref: String,
    val category: String,
    val createdAt: String,
    val fixedAt: String,
    val dismissedAt: String,
    val dismissedReason: String,
    val htmlUrl: String
)

data class GHSecretScanningAlert(
    val number: Int,
    val state: String,
    val resolution: String,
    val secretType: String,
    val secretTypeDisplayName: String,
    val secret: String,
    val validity: String,
    val public: Boolean,
    val pushProtectionBypassed: Boolean,
    val createdAt: String,
    val resolvedAt: String,
    val htmlUrl: String
)

data class GHRepositorySecurityAdvisory(
    val ghsaId: String,
    val cveId: String,
    val url: String,
    val htmlUrl: String,
    val summary: String,
    val description: String,
    val severity: String,
    val state: String,
    val publishedAt: String,
    val updatedAt: String,
    val withdrawnAt: String,
    val cvssScore: Double,
    val cweIds: List<String>,
    val vulnerabilities: List<GHAdvisoryVulnerability>
)

data class GHAdvisoryVulnerability(
    val ecosystem: String,
    val packageName: String,
    val vulnerableRange: String,
    val patchedVersions: String
)

data class GHRepositorySecuritySettings(
    val automatedSecurityFixes: Boolean,
    val automatedSecurityFixesPaused: Boolean,
    val vulnerabilityAlerts: Boolean,
    val privateVulnerabilityReporting: Boolean
)

data class GHCommunityProfile(
    val healthPercentage: Int,
    val description: String,
    val documentationUrl: String,
    val updatedAt: String,
    val files: List<GHCommunityProfileFile>
)

data class GHCommunityProfileFile(
    val key: String,
    val name: String,
    val htmlUrl: String,
    val present: Boolean
)
