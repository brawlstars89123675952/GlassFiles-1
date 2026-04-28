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
    /**
     * In-memory cache of file contents for the current session. Keyed by
     * cleaned path; values mirror the latest known content on [branch].
     * Populated by [readFile] and invalidated by [editFile] / [writeFile]
     * after a successful upload (so subsequent tool calls see the new
     * version without a round-trip).
     *
     * Models often re-read the same file multiple times within a single
     * loop ("read it, plan, then check before edit"). Caching collapses
     * those repeated reads into one network call.
     */
    private val fileCache = mutableMapOf<String, String>()

    suspend fun execute(context: Context, call: AiToolCall): AiToolResult {
        val args = runCatching { JSONObject(call.argsJson) }.getOrElse { JSONObject() }
        return try {
            val output = when (call.name) {
                AgentTools.LIST_DIR.name -> listDir(context, args.optString("path", ""))
                AgentTools.READ_FILE.name -> readFile(context, args.getString("path"))
                AgentTools.READ_FILE_RANGE.name -> readFileRange(
                    context,
                    args.getString("path"),
                    args.getInt("start_line"),
                    args.getInt("end_line"),
                )
                AgentTools.SEARCH_REPO.name -> searchRepo(context, args.getString("query"))
                AgentTools.LIST_BRANCHES.name -> listBranches(context)
                AgentTools.COMPARE_REFS.name -> compareRefs(
                    context,
                    args.getString("base"),
                    args.getString("head"),
                )
                AgentTools.LIST_PULLS.name -> listPulls(context, args.optString("state", "open"))
                AgentTools.READ_PR.name -> readPr(context, args.getInt("number"))
                AgentTools.LIST_ISSUES.name -> listIssues(context, args.optString("state", "open"))
                AgentTools.READ_ISSUE.name -> readIssue(context, args.getInt("number"))
                AgentTools.READ_WORKFLOW_RUN.name -> readWorkflowRun(context, args.getLong("run_id"))
                AgentTools.EDIT_FILE.name -> editFile(
                    context,
                    args.getString("path"),
                    args.getString("old_string"),
                    args.getString("new_string"),
                    args.optString("message", "AI agent: edit ${args.getString("path")}"),
                )
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
        val cleaned = path.trim().trim('/')
        val text = fetchFileContent(context, cleaned)
        return if (text.isBlank()) "(empty file)" else text
    }

    /**
     * Returns the contents of [cleanedPath] on the active branch — from
     * the in-session [fileCache] when present, otherwise via a
     * [GitHubManager.getFileContent] round-trip whose result is then
     * memoised. Empty/missing files are NOT cached so that newly
     * created files become visible to later tool calls.
     */
    private suspend fun fetchFileContent(context: Context, cleanedPath: String): String {
        fileCache[cleanedPath]?.let { return it }
        val text = GitHubManager.getFileContent(context, owner, repo, cleanedPath, branch)
        if (text.isNotBlank()) fileCache[cleanedPath] = text
        return text
    }

    private suspend fun readFileRange(
        context: Context,
        path: String,
        startLine: Int,
        endLine: Int,
    ): String {
        if (startLine < 1) throw IllegalArgumentException("start_line must be >= 1, got $startLine")
        if (endLine < startLine) throw IllegalArgumentException("end_line ($endLine) must be >= start_line ($startLine)")
        val cleaned = path.trim().trim('/')
        val text = fetchFileContent(context, cleaned)
        if (text.isBlank()) return "(empty file)"
        val lines = text.split('\n')
        val total = lines.size
        val from = (startLine - 1).coerceIn(0, total)
        val to = endLine.coerceIn(from, total)
        if (from >= total) return "(file has only $total line(s); range $startLine-$endLine is past the end)"
        val width = to.toString().length
        val sliced = lines.subList(from, to)
        val rendered = sliced.mapIndexed { i, line ->
            val ln = (from + i + 1).toString().padStart(width)
            "$ln: $line"
        }.joinToString("\n")
        val suffix = if (to < total) "\n[file continues to line $total]" else ""
        return "$rendered$suffix"
    }

    private suspend fun searchRepo(context: Context, query: String): String {
        val results = GitHubManager.searchCode(context, query, owner, repo)
        if (results.isEmpty()) return "No matches."
        return results.joinToString("\n") { "${it.path}  (sha=${it.sha.take(7)})" }
    }

    private suspend fun listBranches(context: Context): String {
        val branches = GitHubManager.getBranches(context, owner, repo)
        if (branches.isEmpty()) return "(no branches)"
        return branches.joinToString("\n") { name ->
            if (name == branch) "* $name (active)" else "  $name"
        }
    }

    private suspend fun compareRefs(context: Context, base: String, head: String): String {
        val result = GitHubManager.compareCommits(context, owner, repo, base, head)
            ?: throw RuntimeException("compare_refs: GitHub returned no comparison for $base...$head.")
        return buildString {
            append("status=${result.status}, ahead_by=${result.aheadBy}, behind_by=${result.behindBy}, total_commits=${result.totalCommits}")
            if (result.commits.isNotEmpty()) {
                appendLine().appendLine().appendLine("Commits (${result.commits.size}):")
                result.commits.take(20).forEach { c ->
                    val firstLine = c.message.lineSequence().firstOrNull().orEmpty()
                    appendLine("  ${c.sha.take(7)}  $firstLine")
                }
                if (result.commits.size > 20) appendLine("  …${result.commits.size - 20} more")
            }
            if (result.files.isNotEmpty()) {
                appendLine().appendLine("Files (${result.files.size}):")
                result.files.take(40).forEach { f ->
                    appendLine("  [${f.status}] +${f.additions}/-${f.deletions}  ${f.filename}")
                }
                if (result.files.size > 40) appendLine("  …${result.files.size - 40} more")
                // Inline a few patches so the model can reason about the
                // actual change without another tool round-trip.
                val patches = result.files.mapNotNull { f ->
                    f.patch.takeIf { it.isNotBlank() }?.let { f.filename to it }
                }
                if (patches.isNotEmpty()) {
                    appendLine().appendLine("Patches (first ${minOf(3, patches.size)} files):")
                    patches.take(3).forEach { (name, patch) ->
                        appendLine("--- $name")
                        appendLine(patch.take(1500))
                        if (patch.length > 1500) appendLine("[…patch truncated]")
                    }
                }
            }
        }.trimEnd()
    }

    private suspend fun listPulls(context: Context, state: String): String {
        val effective = state.ifBlank { "open" }.lowercase()
        val pulls = GitHubManager.getPullRequests(context, owner, repo, effective)
        if (pulls.isEmpty()) return "(no $effective pull requests)"
        return pulls.take(30).joinToString("\n") { p ->
            val mergedTag = if (p.merged) " merged" else ""
            val draftTag = if (p.draft) " draft" else ""
            "#${p.number}  [${p.state}$mergedTag$draftTag]  ${p.head} → ${p.base}  by @${p.author}  — ${p.title}"
        }
    }

    private suspend fun readPr(context: Context, number: Int): String {
        val pr = GitHubManager.getPullRequestDetail(context, owner, repo, number)
            ?: throw RuntimeException("read_pr: PR #$number not found.")
        return buildString {
            appendLine("#${pr.number}  ${pr.title}")
            appendLine("state: ${pr.state}${if (pr.merged) " (merged)" else ""}${if (pr.draft) " (draft)" else ""}")
            appendLine("author: @${pr.author}    created: ${pr.createdAt}")
            appendLine("head:   ${pr.head}")
            appendLine("base:   ${pr.base}")
            appendLine("commits=${pr.commits}, +${pr.additions}/-${pr.deletions} across ${pr.changedFiles} file(s)")
            if (pr.mergeable != null) appendLine("mergeable=${pr.mergeable}, state=${pr.mergeableState}")
            if (pr.requestedReviewers.isNotEmpty()) appendLine("reviewers: ${pr.requestedReviewers.joinToString(", ") { "@$it" }}")
            if (pr.htmlUrl.isNotBlank()) appendLine("url: ${pr.htmlUrl}")
            if (pr.body.isNotBlank()) {
                appendLine().appendLine("---")
                appendLine(pr.body.take(3000))
                if (pr.body.length > 3000) appendLine("[…body truncated]")
            }
        }.trimEnd()
    }

    private suspend fun listIssues(context: Context, state: String): String {
        val effective = state.ifBlank { "open" }.lowercase()
        val items = GitHubManager.getIssues(context, owner, repo, effective)
            .filterNot { it.isPR }
        if (items.isEmpty()) return "(no $effective issues)"
        return items.take(30).joinToString("\n") { i ->
            "#${i.number}  [${i.state}]  by @${i.author}  comments=${i.comments}  — ${i.title}"
        }
    }

    private suspend fun readIssue(context: Context, number: Int): String {
        val issue = GitHubManager.getIssueDetail(context, owner, repo, number)
            ?: throw RuntimeException("read_issue: issue #$number not found.")
        if (issue.isPR) {
            // GitHub's issues endpoint surfaces PRs too — redirect the
            // model rather than hide the fact.
            return "Issue #$number is actually a pull request — call read_pr with number=$number for full PR details."
        }
        val comments = runCatching { GitHubManager.getIssueComments(context, owner, repo, number) }
            .getOrDefault(emptyList())
        return buildString {
            appendLine("#${issue.number}  ${issue.title}")
            appendLine("state: ${issue.state}${if (issue.locked) " (locked)" else ""}")
            appendLine("author: @${issue.author}    created: ${issue.createdAt}")
            if (issue.assignee.isNotBlank()) appendLine("assignee: @${issue.assignee}")
            if (issue.labels.isNotEmpty()) appendLine("labels: ${issue.labels.joinToString(", ")}")
            if (issue.milestoneTitle.isNotBlank()) appendLine("milestone: ${issue.milestoneTitle}")
            if (issue.body.isNotBlank()) {
                appendLine().appendLine("---")
                appendLine(issue.body.take(2500))
                if (issue.body.length > 2500) appendLine("[…body truncated]")
            }
            if (comments.isNotEmpty()) {
                appendLine().appendLine("Comments (${comments.size}):")
                comments.takeLast(5).forEach { c ->
                    appendLine("--- @${c.author}  ${c.createdAt}")
                    appendLine(c.body.take(800))
                    if (c.body.length > 800) appendLine("[…comment truncated]")
                }
                if (comments.size > 5) appendLine("[earlier ${comments.size - 5} comment(s) omitted]")
            }
        }.trimEnd()
    }

    private suspend fun readWorkflowRun(context: Context, runId: Long): String {
        val run = GitHubManager.getWorkflowRun(context, owner, repo, runId)
            ?: throw RuntimeException("read_workflow_run: run $runId not found.")
        val jobs = runCatching { GitHubManager.getWorkflowRunJobs(context, owner, repo, runId) }
            .getOrDefault(emptyList())
        return buildString {
            appendLine("run #${run.runNumber}  ${run.name}")
            appendLine("status=${run.status}, conclusion=${run.conclusion}")
            appendLine("branch=${run.branch}, event=${run.event}, sha=${run.headSha.take(7)}")
            appendLine("by @${run.actor}    created: ${run.createdAt}    updated: ${run.updatedAt}")
            if (run.htmlUrl.isNotBlank()) appendLine("url: ${run.htmlUrl}")
            if (jobs.isNotEmpty()) {
                appendLine().appendLine("Jobs (${jobs.size}):")
                jobs.forEach { j ->
                    appendLine("  ${j.name}: status=${j.status}, conclusion=${j.conclusion}")
                }
                val failed = jobs.filter { it.conclusion == "failure" }
                if (failed.isNotEmpty()) {
                    appendLine().appendLine("Failed-job logs (tail):")
                    failed.take(3).forEach { j ->
                        val raw = runCatching { GitHubManager.getJobLogs(context, owner, repo, j.id) }
                            .getOrDefault("")
                        // Logs come back gigantic — keep only the tail
                        // (where the error usually lives) so the model
                        // gets actionable context without burning the
                        // whole turn budget.
                        val tail = raw.takeLast(2000)
                        appendLine("--- ${j.name} (id=${j.id})")
                        appendLine(if (tail.isBlank()) "(no logs)" else tail)
                    }
                    if (failed.size > 3) appendLine("[${failed.size - 3} more failed job(s) omitted]")
                }
            }
        }.trimEnd()
    }

    private suspend fun editFile(
        context: Context,
        path: String,
        oldString: String,
        newString: String,
        message: String,
    ): String {
        if (oldString.isEmpty()) {
            throw RuntimeException("edit_file: old_string must not be empty. Use write_file to create a new file.")
        }
        if (oldString == newString) {
            throw RuntimeException("edit_file: old_string and new_string are identical — nothing to change.")
        }
        val cleaned = path.trim().trim('/')
        val current = fetchFileContent(context, cleaned)
        if (current.isBlank()) {
            throw RuntimeException("edit_file: file \"$cleaned\" is empty or could not be read on branch \"$branch\". Use write_file to create it.")
        }
        val occurrences = countOccurrences(current, oldString)
        if (occurrences == 0) {
            throw RuntimeException("edit_file: old_string was not found in \"$cleaned\". Re-read the file and pass an exact match.")
        }
        if (occurrences > 1) {
            throw RuntimeException("edit_file: old_string appears $occurrences times in \"$cleaned\". Add more surrounding context so it is unique.")
        }
        val updated = current.replaceFirst(oldString, newString)
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
            content = updated.toByteArray(Charsets.UTF_8),
            message = message,
            branch = branch,
            sha = existingSha,
        )
        return if (ok) {
            // Reflect the new content in the cache so the model sees its
            // own edits on subsequent reads without another API call.
            fileCache[cleaned] = updated
            val delta = updated.length - current.length
            val sign = if (delta >= 0) "+" else ""
            "Edited $cleaned ($sign$delta chars) on $branch."
        } else throw RuntimeException("edit_file: GitHub rejected the commit. Check token scope or path.")
    }

    private fun countOccurrences(haystack: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var count = 0
        var idx = haystack.indexOf(needle)
        while (idx >= 0) {
            count++
            idx = haystack.indexOf(needle, idx + needle.length)
        }
        return count
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
        return if (ok) {
            fileCache[cleaned] = content
            "Wrote $cleaned (${content.length} chars) on $branch."
        } else throw RuntimeException("write_file: GitHub rejected the commit. Check token scope or path.")
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
            if (ok) {
                written += path
                fileCache[path] = content
            }
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
