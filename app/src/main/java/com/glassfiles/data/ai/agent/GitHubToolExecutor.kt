package com.glassfiles.data.ai.agent

import android.content.Context
import com.glassfiles.data.github.GHContent
import com.glassfiles.data.github.GitHubManager
import org.json.JSONObject

/**
 * Maps an [AiToolCall] onto a sequence of [GitHubManager] calls scoped to
 * the session's active [owner]/[repo]/[branch].
 *
 * The executor performs zero authorisation of its own — destructive tools
 * (`write_file`, `create_branch`, `commit`, `open_pr`) must be gated by the
 * UI layer. The agent loop in [AiAgentSession] enforces that contract.
 *
 * Outputs returned to the model are short, plain-text summaries. Anything
 * larger than 6 kB is truncated with an explicit "[truncated]" marker so
 * the model doesn't choke on the next-turn payload size.
 */
class GitHubToolExecutor(
    private val owner: String,
    private val repo: String,
    private val branch: String,
) {

    suspend fun execute(context: Context, call: AiToolCall): AiToolResult {
        val args = runCatching { JSONObject(call.argsJson) }.getOrElse { JSONObject() }
        return try {
            val output = when (call.name) {
                AgentTools.LIST_DIR.name -> listDir(context, args.optString("path", ""))
                AgentTools.READ_FILE.name -> readFile(context, args.getString("path"))
                AgentTools.SEARCH_REPO.name -> searchRepo(context, args.getString("query"))
                AgentTools.WRITE_FILE.name -> writeFile(
                    context,
                    args.getString("path"),
                    args.getString("content"),
                    args.optString("message", "AI agent: update ${args.getString("path")}"),
                )
                AgentTools.CREATE_BRANCH.name -> createBranch(
                    context,
                    args.getString("name"),
                    args.optString("from", branch),
                )
                AgentTools.COMMIT.name -> commitMany(
                    context,
                    args.getString("message"),
                    args.optJSONArray("files"),
                )
                AgentTools.OPEN_PR.name -> openPr(
                    context,
                    args.getString("title"),
                    args.optString("body", ""),
                    args.getString("head"),
                    args.optString("base", ""),
                )
                else -> "Unknown tool: ${call.name}"
            }
            AiToolResult(callId = call.id, name = call.name, output = capped(output))
        } catch (e: Exception) {
            AiToolResult(
                callId = call.id,
                name = call.name,
                output = "Error: ${e.message ?: e.javaClass.simpleName}",
                isError = true,
            )
        }
    }

    // ─── tool impls ───────────────────────────────────────────────────────

    private suspend fun listDir(context: Context, path: String): String {
        val cleaned = path.trim().trim('/')
        val items: List<GHContent> =
            GitHubManager.getRepoContents(context, owner, repo, cleaned, branch)
        if (items.isEmpty()) return "(empty directory)"
        return buildString {
            items.forEach {
                val tag = if (it.type == "dir") "[dir] " else "      "
                appendLine("$tag${it.path}")
            }
        }.trimEnd()
    }

    private suspend fun readFile(context: Context, path: String): String {
        val text = GitHubManager.getFileContent(context, owner, repo, path.trim().trim('/'), branch)
        return if (text.isBlank()) "(empty file)" else text
    }

    private suspend fun searchRepo(context: Context, query: String): String {
        val results = GitHubManager.searchCode(context, query, owner, repo)
        if (results.isEmpty()) return "No matches."
        return results.joinToString("\n") { "${it.path}  (sha=${it.sha.take(7)})" }
    }

    private suspend fun writeFile(
        context: Context,
        path: String,
        content: String,
        message: String,
    ): String {
        val cleaned = path.trim().trim('/')
        // Look up existing file's sha so the PUT counts as an update, not a fail-on-exists.
        val existingSha = runCatching {
            GitHubManager
                .getRepoContents(context, owner, repo, parentOf(cleaned), branch)
                .firstOrNull { it.path == cleaned }?.sha
        }.getOrNull()
        val ok = GitHubManager.uploadFile(
            context = context,
            owner = owner,
            repo = repo,
            path = cleaned,
            content = content.toByteArray(Charsets.UTF_8),
            message = message,
            branch = branch,
            sha = existingSha,
        )
        return if (ok) "Wrote $cleaned (${content.length} chars) on $branch."
        else throw RuntimeException("write_file: GitHub rejected the commit. Check token scope or path.")
    }

    private suspend fun createBranch(context: Context, name: String, from: String): String {
        val ok = GitHubManager.createBranch(context, owner, repo, name, from.ifBlank { branch })
        return if (ok) "Branch \"$name\" created from \"$from\"."
        else throw RuntimeException("create_branch: GitHub rejected the request. Branch may already exist.")
    }

    private suspend fun commitMany(
        context: Context,
        message: String,
        files: org.json.JSONArray?,
    ): String {
        val arr = files ?: org.json.JSONArray()
        if (arr.length() == 0) return "Nothing to commit."
        val written = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val f = arr.optJSONObject(i) ?: continue
            val path = f.optString("path", "").trim().trim('/')
            val content = f.optString("content", "")
            if (path.isBlank()) continue
            val existingSha = runCatching {
                GitHubManager
                    .getRepoContents(context, owner, repo, parentOf(path), branch)
                    .firstOrNull { it.path == path }?.sha
            }.getOrNull()
            val ok = GitHubManager.uploadFile(
                context = context,
                owner = owner,
                repo = repo,
                path = path,
                content = content.toByteArray(Charsets.UTF_8),
                message = "$message ($path)",
                branch = branch,
                sha = existingSha,
            )
            if (ok) written += path
        }
        return if (written.isEmpty()) throw RuntimeException("commit: no files were written.")
        else "Committed ${written.size} file(s) to $branch:\n${written.joinToString("\n")}"
    }

    private suspend fun openPr(
        context: Context,
        title: String,
        body: String,
        head: String,
        base: String,
    ): String {
        val effectiveBase = base.ifBlank {
            // The repo's default branch — fall back to active session branch if API fails.
            runCatching { GitHubManager.getRepo(context, owner, repo)?.defaultBranch }
                .getOrNull().orEmpty().ifBlank { branch }
        }
        val ok = GitHubManager.createPullRequest(
            context = context,
            owner = owner,
            repo = repo,
            title = title,
            body = body,
            head = head,
            base = effectiveBase,
        )
        return if (ok) "Opened PR \"$title\": $head → $effectiveBase."
        else throw RuntimeException("open_pr: GitHub rejected the request. Check head/base names.")
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private fun parentOf(path: String): String {
        val idx = path.lastIndexOf('/')
        return if (idx <= 0) "" else path.substring(0, idx)
    }

    private fun capped(s: String, max: Int = 6_000): String =
        if (s.length <= max) s else s.take(max) + "\n\n[truncated, ${s.length - max} chars omitted]"
}
