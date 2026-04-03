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
        val r = request(context, "/repos/$owner/$repo/branches?per_page=50")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { arr.getJSONObject(it).optString("name") }
        } catch (e: Exception) { emptyList() }
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
        val r = request(context, "/repos/$owner/$repo/actions/workflows?per_page=30")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONObject(r.body).getJSONArray("workflows")
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHWorkflow(id = j.optLong("id"), name = j.optString("name"), state = j.optString("state"), path = j.optString("path"))
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getWorkflowRuns(context: Context, owner: String, repo: String, workflowId: Long? = null, perPage: Int = 20): List<GHWorkflowRun> {
        val endpoint = if (workflowId != null) "/repos/$owner/$repo/actions/workflows/$workflowId/runs?per_page=$perPage"
            else "/repos/$owner/$repo/actions/runs?per_page=$perPage"
        val r = request(context, endpoint)
        if (!r.success) return emptyList()
        return try {
            val arr = JSONObject(r.body).getJSONArray("workflow_runs")
            (0 until arr.length()).map { i -> parseWorkflowRun(arr.getJSONObject(i)) }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getWorkflowRunJobs(context: Context, owner: String, repo: String, runId: Long): List<GHJob> {
        val r = request(context, "/repos/$owner/$repo/actions/runs/$runId/jobs")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONObject(r.body).getJSONArray("jobs")
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

    suspend fun cancelWorkflowRun(context: Context, owner: String, repo: String, runId: Long): Boolean =
        request(context, "/repos/$owner/$repo/actions/runs/$runId/cancel", "POST", "{}").success

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

    suspend fun getRunArtifacts(context: Context, owner: String, repo: String, runId: Long): List<GHArtifact> {
        val r = request(context, "/repos/$owner/$repo/actions/runs/$runId/artifacts")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONObject(r.body).getJSONArray("artifacts")
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                GHArtifact(
                    id = j.optLong("id"), name = j.optString("name"),
                    sizeInBytes = j.optLong("size_in_bytes", 0),
                    expired = j.optBoolean("expired", false),
                    createdAt = j.optString("created_at", ""),
                    expiresAt = j.optString("expires_at", "")
                )
            }
        } catch (e: Exception) { emptyList() }
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

    private fun parseWorkflowRun(j: JSONObject) = GHWorkflowRun(
        id = j.optLong("id"), name = j.optString("name"), status = j.optString("status"),
        conclusion = j.optString("conclusion", ""), branch = j.optString("head_branch", ""),
        event = j.optString("event", ""), createdAt = j.optString("created_at", ""),
        updatedAt = j.optString("updated_at", ""), runNumber = j.optInt("run_number"),
        actor = j.optJSONObject("actor")?.optString("login") ?: "",
        actorAvatar = j.optJSONObject("actor")?.optString("avatar_url") ?: "",
        workflowId = j.optLong("workflow_id")
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

    fun clearGitHubUserCache(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_USER).apply()
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

data class GHWorkflowRun(val id: Long, val name: String, val status: String, val conclusion: String,
    val branch: String, val event: String, val createdAt: String, val updatedAt: String,
    val runNumber: Int, val actor: String, val actorAvatar: String, val workflowId: Long)

data class GHJob(val id: Long, val name: String, val status: String, val conclusion: String,
    val startedAt: String, val completedAt: String, val steps: List<GHStep>)

data class GHStep(val name: String, val status: String, val conclusion: String, val number: Int)

data class GHNotification(val id: String, val unread: Boolean, val reason: String,
    val title: String, val type: String, val repoName: String, val updatedAt: String, val url: String)

data class GHArtifact(val id: Long, val name: String, val sizeInBytes: Long,
    val expired: Boolean, val createdAt: String, val expiresAt: String)

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
