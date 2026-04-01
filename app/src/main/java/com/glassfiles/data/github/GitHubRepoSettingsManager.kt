package com.glassfiles.data.github

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object GitHubRepoSettingsManager {
    private const val TAG = "GHRepoSettings"
    private const val API = "https://api.github.com"
    private const val API_VERSION = "2026-03-10"

    data class ApiResult(val success: Boolean, val body: String, val code: Int)

    data class RepoGeneralSettings(
        val name: String,
        val owner: String,
        val description: String,
        val homepage: String,
        val defaultBranch: String,
        val visibility: String,
        val archived: Boolean,
        val hasIssues: Boolean,
        val hasProjects: Boolean,
        val hasWiki: Boolean,
        val hasDiscussions: Boolean,
        val allowForking: Boolean,
        val webCommitSignoffRequired: Boolean
    )

    data class RepoCollaborator(
        val login: String,
        val avatarUrl: String,
        val roleName: String,
        val permissionSummary: String
    )

    data class RepoVariableMeta(
        val name: String,
        val createdAt: String,
        val updatedAt: String,
        val value: String? = null
    )

    data class RepoSecretMeta(
        val name: String,
        val createdAt: String,
        val updatedAt: String
    )

    data class RepoWebhook(
        val id: Long,
        val url: String,
        val active: Boolean,
        val contentType: String,
        val insecureSsl: String,
        val events: List<String>
    )

    data class RepoRulesetSummary(
        val id: Long,
        val name: String,
        val target: String,
        val enforcement: String,
        val sourceType: String
    )

    data class RepoBranchProtectionSummary(
        val isProtected: Boolean,
        val allowForcePushes: Boolean,
        val allowDeletions: Boolean,
        val requiredLinearHistory: Boolean,
        val requiredConversationResolution: Boolean,
        val requiredApprovingReviews: Int,
        val requiredStatusChecksCount: Int
    )

    data class RepoSecuritySettings(
        val automatedSecurityFixes: Boolean,
        val vulnerabilityAlerts: Boolean,
        val privateVulnerabilityReporting: Boolean
    )

    private suspend fun request(context: Context, endpoint: String, method: String = "GET", body: String? = null): ApiResult =
        withContext(Dispatchers.IO) {
            try {
                val token = GitHubManager.getToken(context)
                val url = if (endpoint.startsWith("http")) endpoint else "$API$endpoint"
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("X-GitHub-Api-Version", API_VERSION)
                    setRequestProperty("User-Agent", "GlassFiles")
                    if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
                    if (body != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        OutputStreamWriter(outputStream).use { it.write(body) }
                    }
                    connectTimeout = 15000
                    readTimeout = 20000
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                ApiResult(code in 200..299, text, code)
            } catch (e: Exception) {
                Log.e(TAG, "Request error: ${e.message}")
                ApiResult(false, e.message ?: "Network error", -1)
            }
        }

    suspend fun getGeneral(context: Context, owner: String, repo: String): RepoGeneralSettings? {
        val r = request(context, "/repos/$owner/$repo")
        if (!r.success) return null
        return try {
            val j = JSONObject(r.body)
            RepoGeneralSettings(
                name = j.optString("name"),
                owner = j.optJSONObject("owner")?.optString("login") ?: owner,
                description = j.optString("description", ""),
                homepage = j.optString("homepage", ""),
                defaultBranch = j.optString("default_branch", "main"),
                visibility = if (j.optBoolean("private", false)) "private" else "public",
                archived = j.optBoolean("archived", false),
                hasIssues = j.optBoolean("has_issues", true),
                hasProjects = j.optBoolean("has_projects", false),
                hasWiki = j.optBoolean("has_wiki", false),
                hasDiscussions = j.optBoolean("has_discussions", false),
                allowForking = j.optBoolean("allow_forking", true),
                webCommitSignoffRequired = j.optBoolean("web_commit_signoff_required", false)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Parse general: ${e.message}")
            null
        }
    }

    suspend fun updateGeneral(context: Context, owner: String, repo: String, settings: RepoGeneralSettings): Boolean {
        val body = JSONObject().apply {
            put("name", settings.name)
            put("description", settings.description)
            put("homepage", settings.homepage)
            put("default_branch", settings.defaultBranch)
            put("archived", settings.archived)
            put("has_issues", settings.hasIssues)
            put("has_projects", settings.hasProjects)
            put("has_wiki", settings.hasWiki)
            put("has_discussions", settings.hasDiscussions)
            put("allow_forking", settings.allowForking)
            put("web_commit_signoff_required", settings.webCommitSignoffRequired)
        }.toString()
        return request(context, "/repos/$owner/$repo", "PATCH", body).success
    }

    suspend fun listCollaborators(context: Context, owner: String, repo: String): List<RepoCollaborator> {
        val r = request(context, "/repos/$owner/$repo/collaborators?per_page=100&affiliation=all")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                val permissions = j.optJSONObject("permissions")
                val summary = when {
                    permissions?.optBoolean("admin") == true -> "admin"
                    permissions?.optBoolean("maintain") == true -> "maintain"
                    permissions?.optBoolean("push") == true -> "write"
                    permissions?.optBoolean("triage") == true -> "triage"
                    permissions?.optBoolean("pull") == true -> "read"
                    else -> "unknown"
                }
                RepoCollaborator(
                    login = j.optString("login"),
                    avatarUrl = j.optString("avatar_url", ""),
                    roleName = j.optString("role_name", summary),
                    permissionSummary = summary
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun addCollaborator(context: Context, owner: String, repo: String, username: String, permission: String): Boolean {
        val body = JSONObject().apply { put("permission", permission) }.toString()
        return request(context, "/repos/$owner/$repo/collaborators/${URLEncoder.encode(username, "UTF-8")}", "PUT", body).code in listOf(201, 204)
    }

    suspend fun removeCollaborator(context: Context, owner: String, repo: String, username: String): Boolean {
        return request(context, "/repos/$owner/$repo/collaborators/${URLEncoder.encode(username, "UTF-8")}", "DELETE").code == 204
    }

    suspend fun listVariables(context: Context, owner: String, repo: String): List<RepoVariableMeta> {
        val r = request(context, "/repos/$owner/$repo/actions/variables")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONObject(r.body).optJSONArray("variables") ?: JSONArray()
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                RepoVariableMeta(
                    name = j.optString("name"),
                    createdAt = j.optString("created_at", ""),
                    updatedAt = j.optString("updated_at", "")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getVariable(context: Context, owner: String, repo: String, name: String): RepoVariableMeta? {
        val r = request(context, "/repos/$owner/$repo/actions/variables/${URLEncoder.encode(name, "UTF-8")}")
        if (!r.success) return null
        return try {
            val j = JSONObject(r.body)
            RepoVariableMeta(
                name = j.optString("name"),
                createdAt = j.optString("created_at", ""),
                updatedAt = j.optString("updated_at", ""),
                value = j.optString("value", "")
            )
        } catch (_: Exception) { null }
    }

    suspend fun createVariable(context: Context, owner: String, repo: String, name: String, value: String): Boolean {
        val body = JSONObject().apply {
            put("name", name)
            put("value", value)
        }.toString()
        return request(context, "/repos/$owner/$repo/actions/variables", "POST", body).code == 201
    }

    suspend fun updateVariable(context: Context, owner: String, repo: String, name: String, value: String): Boolean {
        val body = JSONObject().apply {
            put("name", name)
            put("value", value)
        }.toString()
        return request(context, "/repos/$owner/$repo/actions/variables/${URLEncoder.encode(name, "UTF-8")}", "PATCH", body).code == 204
    }

    suspend fun deleteVariable(context: Context, owner: String, repo: String, name: String): Boolean {
        return request(context, "/repos/$owner/$repo/actions/variables/${URLEncoder.encode(name, "UTF-8")}", "DELETE").code == 204
    }

    suspend fun listSecrets(context: Context, owner: String, repo: String): List<RepoSecretMeta> {
        val r = request(context, "/repos/$owner/$repo/actions/secrets")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONObject(r.body).optJSONArray("secrets") ?: JSONArray()
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                RepoSecretMeta(
                    name = j.optString("name"),
                    createdAt = j.optString("created_at", ""),
                    updatedAt = j.optString("updated_at", "")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun deleteSecret(context: Context, owner: String, repo: String, name: String): Boolean {
        return request(context, "/repos/$owner/$repo/actions/secrets/${URLEncoder.encode(name, "UTF-8")}", "DELETE").code == 204
    }

    suspend fun listWebhooks(context: Context, owner: String, repo: String): List<RepoWebhook> {
        val r = request(context, "/repos/$owner/$repo/hooks?per_page=100")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                val cfg = j.optJSONObject("config") ?: JSONObject()
                val events = j.optJSONArray("events") ?: JSONArray()
                RepoWebhook(
                    id = j.optLong("id", 0),
                    url = cfg.optString("url", ""),
                    active = j.optBoolean("active", true),
                    contentType = cfg.optString("content_type", "json"),
                    insecureSsl = cfg.optString("insecure_ssl", "0"),
                    events = (0 until events.length()).map { events.optString(it) }
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun createWebhook(context: Context, owner: String, repo: String, url: String, events: List<String>, secret: String, active: Boolean): Boolean {
        val config = JSONObject().apply {
            put("url", url)
            put("content_type", "json")
            put("insecure_ssl", "0")
            if (secret.isNotBlank()) put("secret", secret)
        }
        val body = JSONObject().apply {
            put("name", "web")
            put("active", active)
            put("events", JSONArray(events))
            put("config", config)
        }.toString()
        return request(context, "/repos/$owner/$repo/hooks", "POST", body).code == 201
    }

    suspend fun updateWebhook(context: Context, owner: String, repo: String, id: Long, url: String, events: List<String>, secret: String, active: Boolean): Boolean {
        val config = JSONObject().apply {
            put("url", url)
            put("content_type", "json")
            put("insecure_ssl", "0")
            if (secret.isNotBlank()) put("secret", secret)
        }
        val body = JSONObject().apply {
            put("active", active)
            put("events", JSONArray(events))
            put("config", config)
        }.toString()
        return request(context, "/repos/$owner/$repo/hooks/$id", "PATCH", body).success
    }

    suspend fun pingWebhook(context: Context, owner: String, repo: String, id: Long): Boolean {
        return request(context, "/repos/$owner/$repo/hooks/$id/pings", "POST", "{}").code == 204
    }

    suspend fun deleteWebhook(context: Context, owner: String, repo: String, id: Long): Boolean {
        return request(context, "/repos/$owner/$repo/hooks/$id", "DELETE").code == 204
    }

    suspend fun listRulesets(context: Context, owner: String, repo: String): List<RepoRulesetSummary> {
        val r = request(context, "/repos/$owner/$repo/rulesets")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                RepoRulesetSummary(
                    id = j.optLong("id", 0),
                    name = j.optString("name"),
                    target = j.optString("target", "branch"),
                    enforcement = j.optString("enforcement", "active"),
                    sourceType = j.optString("source_type", "Repository")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getRulesForBranch(context: Context, owner: String, repo: String, branch: String): List<String> {
        val r = request(context, "/repos/$owner/$repo/rules/branches/${URLEncoder.encode(branch, "UTF-8")}")
        if (!r.success) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                j.optString("type", "rule")
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getBranchProtection(context: Context, owner: String, repo: String, branch: String): RepoBranchProtectionSummary {
        val r = request(context, "/repos/$owner/$repo/branches/${URLEncoder.encode(branch, "UTF-8")}/protection")
        if (!r.success) return RepoBranchProtectionSummary(false, false, false, false, false, 0, 0)
        return try {
            val j = JSONObject(r.body)
            val prReviews = j.optJSONObject("required_pull_request_reviews")
            val statusChecks = j.optJSONObject("required_status_checks")
            RepoBranchProtectionSummary(
                isProtected = true,
                allowForcePushes = j.optJSONObject("allow_force_pushes")?.optBoolean("enabled", false) ?: false,
                allowDeletions = j.optJSONObject("allow_deletions")?.optBoolean("enabled", false) ?: false,
                requiredLinearHistory = j.optJSONObject("required_linear_history")?.optBoolean("enabled", false) ?: false,
                requiredConversationResolution = j.optJSONObject("required_conversation_resolution")?.optBoolean("enabled", false) ?: false,
                requiredApprovingReviews = prReviews?.optInt("required_approving_review_count", 0) ?: 0,
                requiredStatusChecksCount = statusChecks?.optJSONArray("contexts")?.length() ?: 0
            )
        } catch (_: Exception) { RepoBranchProtectionSummary(false, false, false, false, false, 0, 0) }
    }

    suspend fun getSecuritySettings(context: Context, owner: String, repo: String): RepoSecuritySettings {
        val automated = request(context, "/repos/$owner/$repo/automated-security-fixes").code == 200
        val vulnAlerts = request(context, "/repos/$owner/$repo/vulnerability-alerts").success
        val privateVuln = request(context, "/repos/$owner/$repo/private-vulnerability-reporting").success
        return RepoSecuritySettings(
            automatedSecurityFixes = automated,
            vulnerabilityAlerts = vulnAlerts,
            privateVulnerabilityReporting = privateVuln
        )
    }

    suspend fun setAutomatedSecurityFixes(context: Context, owner: String, repo: String, enabled: Boolean): Boolean {
        val method = if (enabled) "PUT" else "DELETE"
        return request(context, "/repos/$owner/$repo/automated-security-fixes", method, if (enabled) "{}" else null).success || request(context, "/repos/$owner/$repo/automated-security-fixes", method, if (enabled) "{}" else null).code == 204
    }

    suspend fun setVulnerabilityAlerts(context: Context, owner: String, repo: String, enabled: Boolean): Boolean {
        val method = if (enabled) "PUT" else "DELETE"
        return request(context, "/repos/$owner/$repo/vulnerability-alerts", method, if (enabled) "{}" else null).success || request(context, "/repos/$owner/$repo/vulnerability-alerts", method, if (enabled) "{}" else null).code == 204
    }

    suspend fun setPrivateVulnerabilityReporting(context: Context, owner: String, repo: String, enabled: Boolean): Boolean {
        val method = if (enabled) "PUT" else "DELETE"
        return request(context, "/repos/$owner/$repo/private-vulnerability-reporting", method, if (enabled) "{}" else null).success || request(context, "/repos/$owner/$repo/private-vulnerability-reporting", method, if (enabled) "{}" else null).code == 204
    }
}
