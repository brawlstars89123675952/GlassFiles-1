package com.glassfiles.data.ai.agent

import org.json.JSONArray
import org.json.JSONObject

data class AiAgentApprovalSettings(
    val autoApproveReads: Boolean,
    val autoApproveEdits: Boolean,
    val autoApproveWrites: Boolean,
    val autoApproveCommits: Boolean,
    val autoApproveDestructive: Boolean,
    val yoloMode: Boolean,
    val sessionTrust: Boolean,
    val writeLimitPerTask: Int,
    val protectedPaths: List<String>,
    val activeBranch: String,
)

enum class AiAgentApprovalCategory {
    READ,
    EDIT,
    WRITE,
    COMMIT,
    DESTRUCTIVE,
}

data class AiAgentApprovalCheck(
    val category: AiAgentApprovalCategory,
    val targetPaths: List<String>,
    val protectedPattern: String?,
    val autoApproved: Boolean,
    val reason: String,
) {
    val protected: Boolean get() = protectedPattern != null
    val destructive: Boolean get() = category == AiAgentApprovalCategory.DESTRUCTIVE
    val countsAsWrite: Boolean
        get() = category == AiAgentApprovalCategory.EDIT ||
            category == AiAgentApprovalCategory.WRITE ||
            category == AiAgentApprovalCategory.COMMIT ||
            category == AiAgentApprovalCategory.DESTRUCTIVE
}

object AiAgentApprovalPolicy {
    private val editTools = setOf("edit_file", "local_replace_in_file", "local_apply_patch")
    private val writeTools = setOf(
        "write_file",
        "comment_pr",
        "comment_issue",
        "create_issue",
        "memory_write",
        "memory_append",
        "local_write_file",
        "local_append_file",
        "local_mkdir",
        "local_copy",
        "local_move",
        "local_rename",
        "archive_extract",
        "archive_create",
    )
    private val commitTools = setOf("commit", "open_pr", "create_branch", "commit_changes", "create_pull_request")
    private val destructiveTools = setOf(
        "delete_file",
        "reset_hard",
        "force_push",
        "memory_delete",
        "local_delete_to_trash",
        "local_delete",
    )
    // Only actual git-commit tool calls trigger the "commits to main/master require approval"
    // safety stop. write_file / edit_file no longer fall under it — those are governed by
    // the regular WRITE / EDIT auto-approve toggles (and YOLO when enabled).
    private val branchWriteTools = setOf("commit", "commit_changes")

    fun check(
        call: AiToolCall,
        tool: AiTool?,
        settings: AiAgentApprovalSettings,
        sessionTrusted: Boolean,
    ): AiAgentApprovalCheck {
        val category = categoryFor(call.name, tool)
        val paths = extractPaths(call.argsJson)
        var protectedPattern: String? = null
        for (path in paths) {
            protectedPattern = settings.protectedPaths.firstOrNull { pattern -> matchesProtected(path, pattern) }
            if (protectedPattern != null) break
        }
        val destructiveAutoRequested =
            category == AiAgentApprovalCategory.DESTRUCTIVE && settings.autoApproveDestructive
        val mainBranchWrite = settings.yoloMode &&
            call.name in branchWriteTools &&
            (settings.activeBranch.equals("main", ignoreCase = true) ||
                settings.activeBranch.equals("master", ignoreCase = true))
        val autoApproved = when {
            category == AiAgentApprovalCategory.DESTRUCTIVE -> false
            protectedPattern != null -> false
            mainBranchWrite -> false
            settings.yoloMode -> true
            category == AiAgentApprovalCategory.READ -> settings.autoApproveReads
            category == AiAgentApprovalCategory.EDIT -> settings.autoApproveEdits || sessionTrusted
            category == AiAgentApprovalCategory.WRITE -> settings.autoApproveWrites || sessionTrusted
            category == AiAgentApprovalCategory.COMMIT -> settings.autoApproveCommits
            else -> false
        }
        val reason = when {
            destructiveAutoRequested -> "destructive auto-approve is disabled by policy"
            category == AiAgentApprovalCategory.DESTRUCTIVE -> "destructive actions always require approval"
            protectedPattern != null -> "protected path: $protectedPattern"
            mainBranchWrite -> "commits to main/master require approval"
            settings.yoloMode -> "YOLO mode: approval bypassed"
            autoApproved -> "auto-approved ${category.name.lowercase()}"
            else -> "${category.name.lowercase()} requires approval"
        }
        return AiAgentApprovalCheck(
            category = category,
            targetPaths = paths,
            protectedPattern = protectedPattern,
            autoApproved = autoApproved,
            reason = reason,
        )
    }

    fun categoryFor(toolName: String, tool: AiTool?): AiAgentApprovalCategory = when {
        toolName in destructiveTools || toolName.contains("delete", ignoreCase = true) ->
            AiAgentApprovalCategory.DESTRUCTIVE
        tool?.readOnly == true -> AiAgentApprovalCategory.READ
        toolName in editTools -> AiAgentApprovalCategory.EDIT
        toolName in writeTools -> AiAgentApprovalCategory.WRITE
        toolName in commitTools -> AiAgentApprovalCategory.COMMIT
        else -> AiAgentApprovalCategory.WRITE
    }

    fun extractPaths(argsJson: String): List<String> {
        val root = runCatching { JSONObject(argsJson) }.getOrNull() ?: return emptyList()
        val paths = linkedSetOf<String>()
        collectPaths(root, paths)
        return paths.map { normalizePath(it) }.filter { it.isNotBlank() }
    }

    private fun collectPaths(value: Any?, paths: MutableSet<String>) {
        when (value) {
            is JSONObject -> {
                value.keys().forEachRemaining { key ->
                    val child = value.opt(key)
                    if (key.isPathKey() && child is String) {
                        paths += child
                    } else if (key.isPathKey() && child is JSONArray) {
                        for (i in 0 until child.length()) {
                            child.optString(i).takeIf { it.isNotBlank() }?.let { paths += it }
                        }
                    } else {
                        collectPaths(child, paths)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until value.length()) {
                    collectPaths(value.opt(i), paths)
                }
            }
        }
    }

    private fun String.isPathKey(): Boolean {
        val k = lowercase()
        return k == "path" ||
            k == "file" ||
            k == "filename" ||
            k == "filepath" ||
            k == "file_path" ||
            k == "repo_path" ||
            k == "target_path" ||
            k == "source" ||
            k == "destination" ||
            k == "source_path" ||
            k == "destination_path" ||
            k == "source_paths"
    }

    private fun matchesProtected(path: String, pattern: String): Boolean {
        val cleanPath = normalizePath(path)
        val cleanPattern = normalizePattern(pattern)
        if (cleanPath.isBlank() || cleanPattern.isBlank()) return false
        if (globToRegex(cleanPattern).matches(cleanPath)) return true
        if (cleanPattern.contains("/") && globToRegex("**/$cleanPattern").matches(cleanPath)) return true
        return !cleanPattern.contains("/") && globToRegex(cleanPattern).matches(cleanPath.substringAfterLast('/'))
    }

    private fun normalizePath(path: String): String =
        path.replace('\\', '/')
            .trim()
            .removePrefix("./")
            .trimStart('/')

    private fun normalizePattern(pattern: String): String =
        pattern.replace('\\', '/')
            .trim()
            .removePrefix("./")
            .trimStart('/')

    private fun globToRegex(glob: String): Regex {
        val out = StringBuilder("^")
        var i = 0
        while (i < glob.length) {
            when (val c = glob[i]) {
                '*' -> {
                    if (i + 1 < glob.length && glob[i + 1] == '*') {
                        out.append(".*")
                        i++
                    } else {
                        out.append("[^/]*")
                    }
                }
                '?' -> out.append("[^/]")
                '.', '(', ')', '+', '|', '^', '$', '@', '%', '{', '}', '[', ']' -> {
                    out.append('\\').append(c)
                }
                else -> out.append(c)
            }
            i++
        }
        out.append("$")
        return Regex(out.toString())
    }
}
