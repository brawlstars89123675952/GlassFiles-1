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
            if (sha != null) put("sha", sha)
            if (branch != null) put("branch", branch)
        }.toString()
        return request(context, "/repos/$owner/$repo/contents/$path", "PUT", body).success
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
                    body = j.optString("body", "")
                )
            }
        } catch (e: Exception) { emptyList() }
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

    suspend fun mergePullRequest(context: Context, owner: String, repo: String, number: Int, message: String = ""): Boolean {
        val body = if (message.isNotBlank()) JSONObject().apply { put("commit_message", message) }.toString() else null
        return request(context, "/repos/$owner/$repo/pulls/$number/merge", "PUT", body ?: "{}").success
    }

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

    suspend fun addComment(context: Context, owner: String, repo: String, number: Int, body: String): Boolean {
        val json = JSONObject().apply { put("body", body) }.toString()
        return request(context, "/repos/$owner/$repo/issues/$number/comments", "POST", json).success
    }

    suspend fun closeIssue(context: Context, owner: String, repo: String, number: Int): Boolean {
        val json = JSONObject().apply { put("state", "closed") }.toString()
        return request(context, "/repos/$owner/$repo/issues/$number", "PATCH", json).success
    }

    suspend fun reopenIssue(context: Context, owner: String, repo: String, number: Int): Boolean {
        val json = JSONObject().apply { put("state", "open") }.toString()
        return request(context, "/repos/$owner/$repo/issues/$number", "PATCH", json).success
    }

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
                milestoneTitle = j.optJSONObject("milestone")?.optString("title") ?: ""
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
                    assets.add(GHAsset(aj.optString("name"), aj.optLong("size", 0), aj.optString("browser_download_url", ""), aj.optInt("download_count", 0)))
                }
                GHRelease(
                    tag = j.optString("tag_name"), name = j.optString("name", ""),
                    body = j.optString("body", ""), prerelease = j.optBoolean("prerelease", false),
                    createdAt = j.optString("published_at", ""), assets = assets
                )
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

    suspend fun updateRelease(context: Context, owner: String, repo: String, tag: String, name: String, body: String, prerelease: Boolean): Boolean {
        val releases = getReleases(context, owner, repo)
        val release = releases.find { it.tag == tag } ?: return false
        val releaseId = JSONObject(request(context, "/repos/$owner/$repo/releases/tags/$tag").body).optLong("id")
        if (releaseId == 0L) return false
        val json = JSONObject().apply {
            put("tag_name", tag)
            put("name", name)
            put("body", body)
            put("prerelease", prerelease)
        }.toString()
        return request(context, "/repos/$owner/$repo/releases/$releaseId", "PATCH", json).success
    }

    suspend fun deleteRelease(context: Context, owner: String, repo: String, tag: String): Boolean {
        val r = request(context, "/repos/$owner/$repo/releases/tags/$tag")
        if (!r.success) return false
        val releaseId = JSONObject(r.body).optLong("id")
        if (releaseId == 0L) return false
        return request(context, "/repos/$owner/$repo/releases/$releaseId", "DELETE").let { it.code == 204 || it.success }
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
        val r = request(context, "/search/users?q=$query&per_page=10")
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
                    workflows += GHWorkflow(id = j.optLong("id"), name = j.optString("name"), state = j.optString("state"), path = j.optString("path"))
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
                    path = j.optString("path"),
                    startLine = j.optInt("start_line", 0),
                    endLine = j.optInt("end_line", 0),
                    annotationLevel = j.optString("annotation_level"),
                    message = j.optString("message"),
                    title = j.optString("title", ""),
                    rawDetails = j.optString("raw_details", "")
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
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                val labels = j.optJSONArray("labels")?.let { labelArr ->
                    (0 until labelArr.length()).mapNotNull { idx -> labelArr.optJSONObject(idx)?.optString("name") }
                } ?: emptyList()
                GHActionRunner(
                    id = j.optLong("id"),
                    name = j.optString("name"),
                    os = j.optString("os"),
                    status = j.optString("status"),
                    busy = j.optBoolean("busy", false),
                    labels = labels
                )
            }
        } catch (e: Exception) { emptyList() }
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

    suspend fun getNotifications(context: Context, all: Boolean = false): List<GHNotification> {
        val r = request(context, "/notifications?all=$all&per_page=50")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                val sub = j.optJSONObject("subject")
                val repo = j.optJSONObject("repository")
                GHNotification(
                    id = j.optString("id"), unread = j.optBoolean("unread", false),
                    reason = j.optString("reason", ""),
                    title = sub?.optString("title") ?: "", type = sub?.optString("type") ?: "",
                    repoName = repo?.optString("full_name") ?: "",
                    updatedAt = j.optString("updated_at", ""),
                    url = sub?.optString("url") ?: ""
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun markNotificationRead(context: Context, threadId: String): Boolean =
        request(context, "/notifications/threads/$threadId", "PATCH").let { it.code == 205 || it.success }

    suspend fun markAllNotificationsRead(context: Context): Boolean =
        request(context, "/notifications", "PUT", "{\"read\":true}").let { it.code == 205 || it.success }

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

    // ═══════════════════════════════════
    // Branch Protection
    // ═══════════════════════════════════

    suspend fun getBranchProtection(context: Context, owner: String, repo: String, branch: String): GHBranchProtection? {
        val r = request(context, "/repos/$owner/$repo/branches/$branch/protection")
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
                enforceAdmins = j.optJSONObject("enforce_admins")?.optBoolean("enabled") ?: false
            )
        } catch (e: Exception) { null }
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
        return request(context, "/repos/$owner/$repo/branches/$branch/protection", "PUT", body).success
    }

    suspend fun deleteBranchProtection(context: Context, owner: String, repo: String, branch: String): Boolean =
        request(context, "/repos/$owner/$repo/branches/$branch/protection", "DELETE").let { it.code == 204 || it.success }

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
                            it.optBoolean("push", false) -> "write"
                            it.optBoolean("triage", false) -> "triage"
                            it.optBoolean("pull", false) -> "read"
                            else -> "read"
                        }
                    } ?: "read"
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
        val r = request(context, "/repos/$owner/$repo/commits/$ref/check-runs?per_page=100")
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

    // ═══════════════════════════════════
    // Compare Commits
    // ═══════════════════════════════════

    suspend fun compareCommits(context: Context, owner: String, repo: String, base: String, head: String): GHCompareResult? {
        val r = request(context, "/repos/$owner/$repo/compare/$base...$head")
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
            GHCompareResult(
                status = j.optString("status"),
                aheadBy = j.optInt("ahead_by"),
                behindBy = j.optInt("behind_by"),
                totalCommits = j.optInt("total_commits"),
                files = files
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
                val j = arr.getJSONObject(i)
                GHWebhook(
                    id = j.optLong("id"),
                    name = j.optString("name"),
                    url = j.optJSONObject("config")?.optString("url") ?: "",
                    events = j.optJSONArray("events")?.let { ev -> (0 until ev.length()).map { ev.getString(it) } } ?: emptyList(),
                    active = j.optBoolean("active", true)
                )
            }
        } catch (e: Exception) { emptyList() }
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

    suspend fun deleteWebhook(context: Context, owner: String, repo: String, hookId: Long): Boolean {
        val r = request(context, "/repos/$owner/$repo/hooks/$hookId", "DELETE")
        return r.code == 204 || r.success
    }

    // ═══════════════════════════════════
    // Discussions
    // ═══════════════════════════════════

    suspend fun getDiscussions(context: Context, owner: String, repo: String): List<GHDiscussion> {
        val r = request(context, "/repos/$owner/$repo/discussions?per_page=100", extraHeaders = mapOf("Accept" to "application/vnd.github.echo-preview+json"))
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHDiscussion(
                    number = j.optInt("number"),
                    title = j.optString("title"),
                    body = j.optString("body", ""),
                    author = j.optJSONObject("user")?.optString("login") ?: "",
                    state = j.optString("state"),
                    comments = j.optInt("comments", 0),
                    createdAt = j.optString("created_at", "")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun createDiscussion(context: Context, owner: String, repo: String, title: String, body: String, categoryId: Int): Boolean {
        val reqBody = JSONObject().apply {
            put("title", title)
            put("body", body)
            put("category_id", categoryId)
        }.toString()
        val r = request(context, "/repos/$owner/$repo/discussions", "POST", reqBody, extraHeaders = mapOf("Accept" to "application/vnd.github.echo-preview+json"))
        return r.success
    }

    suspend fun getDiscussionComments(context: Context, owner: String, repo: String, discussionNumber: Int): List<GHComment> {
        val r = request(context, "/repos/$owner/$repo/discussions/$discussionNumber/comments?per_page=100")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHComment(
                    id = j.optLong("id"),
                    author = j.optJSONObject("user")?.optString("login") ?: "",
                    body = j.optString("body"),
                    createdAt = j.optString("created_at"),
                    avatarUrl = j.optJSONObject("user")?.optString("avatar_url") ?: ""
                )
            }
        } catch (e: Exception) { emptyList() }
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
                    rulesCount = j.optJSONArray("rules")?.length() ?: 0
                )
            }
        } catch (e: Exception) { emptyList() }
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
                val j = arr.getJSONObject(i)
                val adv = j.optJSONObject("security_advisory")
                GHDependabotAlert(
                    number = j.optInt("number"),
                    state = j.optString("state"),
                    severity = adv?.optString("severity") ?: "",
                    summary = adv?.optString("summary") ?: "",
                    description = adv?.optString("description") ?: "",
                    packageName = j.optJSONObject("dependency")?.optJSONObject("package")?.optString("name") ?: "",
                    createdAt = j.optString("created_at", "")
                )
            }
        } catch (e: Exception) { emptyList() }
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
        htmlUrl = j.optString("html_url", "")
    )

    data class ApiResult(val success: Boolean, val body: String, val code: Int)
}

data class GHUser(val login: String, val name: String, val avatarUrl: String, val bio: String,
    val publicRepos: Int, val privateRepos: Int, val followers: Int, val following: Int)

data class GHRepo(val name: String, val fullName: String, val description: String, val language: String,
    val stars: Int, val forks: Int, val isPrivate: Boolean, val isFork: Boolean, val defaultBranch: String,
    val updatedAt: String, val owner: String, val htmlUrl: String = "")

data class GHCommit(val sha: String, val message: String, val author: String, val date: String, val avatarUrl: String)

data class GHIssue(val number: Int, val title: String, val state: String, val author: String,
    val createdAt: String, val comments: Int, val isPR: Boolean)

data class GHIssueDetail(val number: Int, val title: String, val body: String, val state: String,
    val author: String, val avatarUrl: String, val createdAt: String, val comments: Int,
    val labels: List<String>, val isPR: Boolean, val assignee: String, val milestoneTitle: String = "")

data class GHContent(val name: String, val path: String, val type: String, val size: Long,
    val downloadUrl: String, val sha: String)

data class GHPullRequest(val number: Int, val title: String, val state: String, val author: String,
    val createdAt: String, val head: String, val base: String, val comments: Int,
    val merged: Boolean, val body: String)

data class GHComment(val id: Long, val body: String, val author: String, val avatarUrl: String, val createdAt: String)

data class GHContributor(val login: String, val avatarUrl: String, val contributions: Int)

data class GHRelease(val tag: String, val name: String, val body: String, val prerelease: Boolean,
    val createdAt: String, val assets: List<GHAsset>)

data class GHAsset(val name: String, val size: Long, val downloadUrl: String, val downloadCount: Int)

data class GHGist(val id: String, val description: String, val isPublic: Boolean, val files: List<String>,
    val createdAt: String, val updatedAt: String)

data class GHCommitDetail(val sha: String, val message: String, val author: String, val date: String,
    val files: List<GHDiffFile>, val totalAdditions: Int, val totalDeletions: Int)

data class GHDiffFile(val filename: String, val status: String, val additions: Int, val deletions: Int, val patch: String)

data class GHWorkflow(val id: Long, val name: String, val state: String, val path: String)

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

data class GHNotification(val id: String, val unread: Boolean, val reason: String,
    val title: String, val type: String, val repoName: String, val updatedAt: String, val url: String)

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

data class GHRunnerToken(val token: String, val expiresAt: String)

data class GHActionsPermissions(val enabled: Boolean, val allowedActions: String,
    val selectedActionsUrl: String)

data class GHWorkflowPermissions(val defaultWorkflowPermissions: String,
    val canApprovePullRequestReviews: Boolean)

data class GHActionsRetention(val days: Int)

data class GHCodeResult(val name: String, val path: String, val sha: String, val htmlUrl: String, val score: Double)

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

data class GHBranchProtection(
    val enabled: Boolean,
    val requiredStatusChecks: GHRequiredStatusChecks?,
    val requiredPRReviews: GHRequiredPRReviews?,
    val restrictions: GHBranchRestrictions?,
    val allowForcePushes: Boolean,
    val allowDeletions: Boolean,
    val requiredConversationResolution: Boolean,
    val enforceAdmins: Boolean
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

data class GHCompareResult(
    val status: String,
    val aheadBy: Int,
    val behindBy: Int,
    val totalCommits: Int,
    val files: List<GHDiffFile>
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
    val active: Boolean
)

data class GHDiscussion(
    val number: Int,
    val title: String,
    val body: String,
    val author: String,
    val state: String,
    val comments: Int,
    val createdAt: String
)

data class GHRuleset(
    val id: Int,
    val name: String,
    val enforcement: String,
    val rulesCount: Int
)

data class GHDependabotAlert(
    val number: Int,
    val state: String,
    val severity: String,
    val summary: String,
    val description: String,
    val packageName: String,
    val createdAt: String
)
