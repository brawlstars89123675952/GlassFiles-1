package com.glassfiles.data.ai.agent

import org.json.JSONObject

/**
 * Definition of a single tool the agent can call. Models receive these as
 * JSON-Schema function definitions (`tools` array in the OpenAI / Anthropic
 * REST shape) and respond with a [AiToolCall] referencing one by [name].
 *
 * The agent layer is provider-agnostic: each provider's `chatWithTools(...)`
 * implementation translates this list into its own wire format.
 */
data class AiTool(
    /** Unique identifier — must match what the model emits in `function_call.name`. */
    val name: String,
    /** Single-sentence description shown to the model. */
    val description: String,
    /** JSON-Schema object describing the parameters. */
    val parameters: JSONObject,
    /**
     * Whether the tool merely *reads* state. Read-only tools may auto-execute
     * when the user opts in; everything else needs explicit per-call approval.
     */
    val readOnly: Boolean,
)

/**
 * The seven canonical tools surfaced to the agent. Tools speak GitHub —
 * [GitHubToolExecutor] translates calls into [com.glassfiles.data.github.GitHubManager]
 * invocations.
 */
object AgentTools {

    /** Lists files / directories at a path inside the active repo + branch. */
    val LIST_DIR = AiTool(
        name = "list_dir",
        description = "List the contents (files and folders) of a directory in the active repository.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("path", obj {
                    put("type", "string")
                    put("description", "Repo-relative directory path. Use \"\" or \"/\" for the repo root.")
                })
            })
            put("required", arr())
        },
        readOnly = true,
    )

    /** Reads a single file's contents. */
    val READ_FILE = AiTool(
        name = "read_file",
        description = "Read the full contents of a file in the active repository.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("path", obj {
                    put("type", "string")
                    put("description", "Repo-relative path to a file (e.g. \"app/build.gradle\").")
                })
            })
            put("required", arr("path"))
        },
        readOnly = true,
    )

    /**
     * Reads a slice of a file by line range. Cheaper than [READ_FILE] for
     * large files when the model only needs a window of context. The result
     * is prefixed with each line's 1-based line number so the model can
     * reference them by [EDIT_FILE] anchor strings.
     */
    val READ_FILE_RANGE = AiTool(
        name = "read_file_range",
        description = "Read a contiguous range of lines from a file in the active repository. Cheaper than read_file when only part of a large file is needed.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("path", obj {
                    put("type", "string")
                    put("description", "Repo-relative path to the file.")
                })
                put("start_line", obj {
                    put("type", "integer")
                    put("description", "1-based line number to start reading from (inclusive).")
                })
                put("end_line", obj {
                    put("type", "integer")
                    put("description", "1-based line number to stop reading at (inclusive). Clamped to the end of the file.")
                })
            })
            put("required", arr("path", "start_line", "end_line"))
        },
        readOnly = true,
    )

    /** Code search inside the active repo via the GitHub Search API. */
    val SEARCH_REPO = AiTool(
        name = "search_repo",
        description = "Search for code or text inside the active repository using GitHub's code-search API.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("query", obj {
                    put("type", "string")
                    put("description", "Free-text query. Surround multi-word phrases with double quotes for exact matches.")
                })
            })
            put("required", arr("query"))
        },
        readOnly = true,
    )

    /**
     * Surgical find-and-replace edit on a single file. Fails when the
     * `old_string` anchor is missing or appears more than once, so the
     * model is forced to disambiguate (instead of accidentally rewriting
     * the wrong region). Strongly preferred over [WRITE_FILE] for small
     * targeted changes.
     */
    val EDIT_FILE = AiTool(
        name = "edit_file",
        description = "Surgically replace a unique substring inside an existing file. Fails if old_string is not found or appears more than once. Use this for targeted edits instead of rewriting the whole file.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("path", obj {
                    put("type", "string")
                    put("description", "Repo-relative path to the file.")
                })
                put("old_string", obj {
                    put("type", "string")
                    put("description", "Exact text to replace. Must appear exactly once in the file. Include enough surrounding lines to be unique.")
                })
                put("new_string", obj {
                    put("type", "string")
                    put("description", "Replacement text. Use the same indentation style as the surrounding code.")
                })
                put("message", obj {
                    put("type", "string")
                    put("description", "Commit message for the edit.")
                })
            })
            put("required", arr("path", "old_string", "new_string", "message"))
        },
        readOnly = false,
    )

    /**
     * Creates or overwrites a file on the active branch. The destructive
     * action is gated by user approval before [GitHubToolExecutor] runs it.
     */
    val WRITE_FILE = AiTool(
        name = "write_file",
        description = "Create a new file or overwrite an existing one on the active branch. Triggers a single-file commit.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("path", obj {
                    put("type", "string")
                    put("description", "Repo-relative path. Parent directories are created automatically.")
                })
                put("content", obj {
                    put("type", "string")
                    put("description", "Full text content of the file (UTF-8). For binary files, send base64 with mediaType=binary.")
                })
                put("message", obj {
                    put("type", "string")
                    put("description", "Commit message for the file change.")
                })
            })
            put("required", arr("path", "content", "message"))
        },
        readOnly = false,
    )

    /** Branches off the current/default branch. */
    val CREATE_BRANCH = AiTool(
        name = "create_branch",
        description = "Create a new branch in the active repository, branched off an existing one.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("name", obj {
                    put("type", "string")
                    put("description", "Name of the new branch (e.g. \"agent/refactor-login\").")
                })
                put("from", obj {
                    put("type", "string")
                    put("description", "Source branch to branch from. Defaults to the active branch.")
                })
            })
            put("required", arr("name"))
        },
        readOnly = false,
    )

    /**
     * Multi-file commit. Conceptually a batch of [WRITE_FILE]s — backed by
     * sequential `PUT /contents` calls on the active branch.
     */
    val COMMIT = AiTool(
        name = "commit",
        description = "Commit a set of file changes to the active branch in one logical batch.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("message", obj {
                    put("type", "string")
                    put("description", "Commit message describing the change set.")
                })
                put("files", obj {
                    put("type", "array")
                    put("description", "List of files to write.")
                    put("items", obj {
                        put("type", "object")
                        put("properties", obj {
                            put("path", obj { put("type", "string") })
                            put("content", obj { put("type", "string") })
                        })
                        put("required", arr("path", "content"))
                    })
                })
            })
            put("required", arr("message", "files"))
        },
        readOnly = false,
    )

    /** Opens a pull request from a head branch into a base branch. */
    val OPEN_PR = AiTool(
        name = "open_pr",
        description = "Open a pull request in the active repository from a head branch into a base branch.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("title", obj { put("type", "string") })
                put("body", obj {
                    put("type", "string")
                    put("description", "PR description in markdown.")
                })
                put("head", obj {
                    put("type", "string")
                    put("description", "Head branch (the one with new commits).")
                })
                put("base", obj {
                    put("type", "string")
                    put("description", "Base branch to merge into. Defaults to the repo default branch.")
                })
            })
            put("required", arr("title", "head"))
        },
        readOnly = false,
    )

    // ─── Read-only repo introspection ────────────────────────────────

    /** Lists the names of every branch in the active repository. */
    val LIST_BRANCHES = AiTool(
        name = "list_branches",
        description = "List the names of all branches in the active repository.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {})
            put("required", arr())
        },
        readOnly = true,
    )

    /**
     * Returns the diff (file-by-file patch + commit summary) between two
     * refs of the active repo. Useful for "what changed on branch X
     * compared to main" or "show me the diff of PR #N".
     */
    val COMPARE_REFS = AiTool(
        name = "compare_refs",
        description = "Compare two refs (branches, tags, or SHAs) in the active repository and return the unified diff plus the list of commits.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("base", obj {
                    put("type", "string")
                    put("description", "Base ref — what we're comparing against (e.g. \"main\").")
                })
                put("head", obj {
                    put("type", "string")
                    put("description", "Head ref — the newer side (e.g. \"feature/foo\").")
                })
            })
            put("required", arr("base", "head"))
        },
        readOnly = true,
    )

    /** Lists pull requests with optional state filter. */
    val LIST_PULLS = AiTool(
        name = "list_pulls",
        description = "List pull requests in the active repository.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("state", obj {
                    put("type", "string")
                    put("description", "One of \"open\", \"closed\", \"all\". Defaults to \"open\".")
                })
            })
            put("required", arr())
        },
        readOnly = true,
    )

    /** Reads a single PR's metadata, description, and head/base refs. */
    val READ_PR = AiTool(
        name = "read_pr",
        description = "Read a pull request's title, description, head/base branches, and stats.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("number", obj {
                    put("type", "integer")
                    put("description", "PR number (the integer after \"#\").")
                })
            })
            put("required", arr("number"))
        },
        readOnly = true,
    )

    /** Lists issues with optional state filter. */
    val LIST_ISSUES = AiTool(
        name = "list_issues",
        description = "List issues in the active repository (excludes PRs).",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("state", obj {
                    put("type", "string")
                    put("description", "One of \"open\", \"closed\", \"all\". Defaults to \"open\".")
                })
            })
            put("required", arr())
        },
        readOnly = true,
    )

    /** Reads a single issue's full metadata, description, and recent comments. */
    val READ_ISSUE = AiTool(
        name = "read_issue",
        description = "Read an issue's title, description, labels, and recent comments.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("number", obj {
                    put("type", "integer")
                    put("description", "Issue number.")
                })
            })
            put("required", arr("number"))
        },
        readOnly = true,
    )

    /**
     * Reads the check-runs registered for a specific git ref. Different
     * from [READ_WORKFLOW_RUN] in that this includes external checks
     * too (Devin Review, Vercel previews, third-party CI, etc.) — not
     * just GitHub Actions runs. Use this when the user asks "why is
     * the PR not green".
     */
    val READ_CHECK_RUNS = AiTool(
        name = "read_check_runs",
        description = "List all check-run statuses (GitHub Actions + external checks like Devin Review, preview deploys, etc.) for a git ref in the active repository.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("ref", obj {
                    put("type", "string")
                    put("description", "Git ref to inspect: a branch name, a tag, or a commit SHA. For PRs, use the head SHA.")
                })
            })
            put("required", arr("ref"))
        },
        readOnly = true,
    )

    /**
     * Reads a workflow run summary plus the per-job statuses, plus the
     * full logs of any failed jobs. Designed to support the use case
     * "tell me why CI failed and propose a fix".
     */
    val READ_WORKFLOW_RUN = AiTool(
        name = "read_workflow_run",
        description = "Read a GitHub Actions workflow run: status, jobs, and logs of failed jobs.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("run_id", obj {
                    put("type", "integer")
                    put("description", "Numeric workflow run ID.")
                })
            })
            put("required", arr("run_id"))
        },
        readOnly = true,
    )

    /**
     * Posts a comment on an existing pull request. Write tool — gated
     * by the same approve/reject card as `write_file`. The comment
     * body is shown to the user verbatim before the call goes out.
     */
    val COMMENT_PR = AiTool(
        name = "comment_pr",
        description = "Post a comment on an existing pull request in the active repository.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("number", obj {
                    put("type", "integer")
                    put("description", "PR number.")
                })
                put("body", obj {
                    put("type", "string")
                    put("description", "Comment body in markdown.")
                })
            })
            put("required", arr("number", "body"))
        },
        readOnly = false,
    )

    /** Posts a comment on an existing issue. Write tool. */
    val COMMENT_ISSUE = AiTool(
        name = "comment_issue",
        description = "Post a comment on an existing issue in the active repository.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("number", obj {
                    put("type", "integer")
                    put("description", "Issue number.")
                })
                put("body", obj {
                    put("type", "string")
                    put("description", "Comment body in markdown.")
                })
            })
            put("required", arr("number", "body"))
        },
        readOnly = false,
    )

    /** Creates a new issue. Write tool. */
    val CREATE_ISSUE = AiTool(
        name = "create_issue",
        description = "Create a new issue in the active repository.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("title", obj { put("type", "string") })
                put("body", obj {
                    put("type", "string")
                    put("description", "Issue body in markdown.")
                })
            })
            put("required", arr("title"))
        },
        readOnly = false,
    )

    /** All tools, in canonical order. */
    val ALL: List<AiTool> = listOf(
        LIST_DIR, READ_FILE, READ_FILE_RANGE, SEARCH_REPO,
        LIST_BRANCHES, COMPARE_REFS,
        LIST_PULLS, READ_PR,
        LIST_ISSUES, READ_ISSUE,
        READ_CHECK_RUNS, READ_WORKFLOW_RUN,
        EDIT_FILE, WRITE_FILE, CREATE_BRANCH, COMMIT, OPEN_PR,
        COMMENT_PR, COMMENT_ISSUE, CREATE_ISSUE,
    )

    fun byName(name: String): AiTool? = ALL.firstOrNull { it.name == name }
}

/** Tiny helpers to keep the JSON-Schema literals readable above. */
private fun obj(block: JSONObject.() -> Unit): JSONObject = JSONObject().apply(block)
private fun arr(vararg items: String): org.json.JSONArray =
    org.json.JSONArray().apply { items.forEach { put(it) } }
