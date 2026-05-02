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
 * Canonical tools surfaced to the agent. Repo tools speak GitHub via
 * [GitHubToolExecutor]; memory tools operate on app-owned local files.
 */
object AgentTools {
    val ARTIFACT_WRITE = AiTool(
        name = "artifact_write",
        description = "Create a file attachment in the current chat. Use this in chat-only mode when the user asks you to produce a downloadable file.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("path", obj {
                    put("type", "string")
                    put("description", "Attachment path or filename, e.g. notes.md, src/Main.kt, report.txt.")
                })
                put("content", obj {
                    put("type", "string")
                    put("description", "Full UTF-8 text content of the attachment.")
                })
                put("language", obj {
                    put("type", "string")
                    put("description", "Optional syntax language, usually the file extension.")
                })
            })
            put("required", arr("path", "content"))
        },
        readOnly = false,
    )

    val ARTIFACT_UPDATE = AiTool(
        name = "artifact_update",
        description = "Update an existing chat attachment by replacing a unique substring. Use artifact_write if replacing the whole file is simpler.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("path", obj {
                    put("type", "string")
                    put("description", "Existing attachment path or filename.")
                })
                put("old_string", obj {
                    put("type", "string")
                    put("description", "Exact text to replace. Must appear exactly once.")
                })
                put("new_string", obj {
                    put("type", "string")
                    put("description", "Replacement text.")
                })
            })
            put("required", arr("path", "old_string", "new_string"))
        },
        readOnly = false,
    )

    val TODO_WRITE = AiTool(
        name = "todo_write",
        description = "Replace the current task checklist with concise todo items. Use at the start of multi-step work and whenever the plan changes.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("items", obj {
                    put("type", "array")
                    put("description", "Checklist items in execution order.")
                    put("items", obj {
                        put("type", "object")
                        put("properties", obj {
                            put("id", obj {
                                put("type", "string")
                                put("description", "Stable short id, e.g. inspect, patch, verify.")
                            })
                            put("title", obj {
                                put("type", "string")
                                put("description", "Short user-visible task title.")
                            })
                            put("status", obj {
                                put("type", "string")
                                put("description", "pending, in_progress, completed, or canceled.")
                            })
                        })
                        put("required", arr("title"))
                    })
                })
            })
            put("required", arr("items"))
        },
        readOnly = true,
    )

    val TODO_UPDATE = AiTool(
        name = "todo_update",
        description = "Update one or more items in the current task checklist. Use this as work progresses.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("id", obj {
                    put("type", "string")
                    put("description", "Todo id to update.")
                })
                put("title", obj {
                    put("type", "string")
                    put("description", "Optional replacement title or title for a new item.")
                })
                put("status", obj {
                    put("type", "string")
                    put("description", "pending, in_progress, completed, or canceled.")
                })
                put("items", obj {
                    put("type", "array")
                    put("description", "Optional batch of updates; each object accepts id, title, and status.")
                    put("items", obj {
                        put("type", "object")
                        put("properties", obj {
                            put("id", obj { put("type", "string") })
                            put("title", obj { put("type", "string") })
                            put("status", obj { put("type", "string") })
                        })
                    })
                })
            })
            put("required", arr())
        },
        readOnly = true,
    )

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

    val WEB_SEARCH = AiTool(
        name = "web_search",
        description = "Search the public web for current information. Returns compact title, URL and snippet results.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("query", obj {
                    put("type", "string")
                    put("description", "Search query.")
                })
                put("limit", obj {
                    put("type", "integer")
                    put("description", "Maximum number of results to return, clamped to 1-10. Defaults to 5.")
                })
            })
            put("required", arr("query"))
        },
        readOnly = true,
    )

    val WEB_FETCH = AiTool(
        name = "web_fetch",
        description = "Fetch a public HTTP/HTTPS URL and return readable text, capped by maxChars.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("url", obj {
                    put("type", "string")
                    put("description", "HTTP or HTTPS URL to fetch.")
                })
                put("maxChars", obj {
                    put("type", "integer")
                    put("description", "Maximum returned text characters, clamped to 1,000-20,000. Defaults to 8,000.")
                })
            })
            put("required", arr("url"))
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

    val MEMORY_READ = AiTool(
        name = "memory_read",
        description = "Read a local AI Agent memory file for the active repository. Path is relative to the repo memory root, or preferences.md for global preferences.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("path", obj {
                    put("type", "string")
                    put("description", "Memory path, e.g. project.md, decisions.md, chats/<chat_id>/summary.md, or preferences.md.")
                })
            })
            put("required", arr("path"))
        },
        readOnly = true,
    )

    val MEMORY_WRITE = AiTool(
        name = "memory_write",
        description = "Overwrite a local AI Agent memory file for the active repository, or global preferences.md.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("path", obj { put("type", "string") })
                put("content", obj { put("type", "string") })
            })
            put("required", arr("path", "content"))
        },
        readOnly = false,
    )

    val MEMORY_APPEND = AiTool(
        name = "memory_append",
        description = "Append text to a local AI Agent memory file, creating it if needed.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("path", obj { put("type", "string") })
                put("content", obj { put("type", "string") })
            })
            put("required", arr("path", "content"))
        },
        readOnly = false,
    )

    val MEMORY_LIST = AiTool(
        name = "memory_list",
        description = "List files in the active repository's local AI Agent memory directory.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("directory", obj {
                    put("type", "string")
                    put("description", "Directory relative to the repo memory root. Empty string lists the repo memory root.")
                })
            })
            put("required", arr())
        },
        readOnly = true,
    )

    val MEMORY_SEARCH = AiTool(
        name = "memory_search",
        description = "Case-insensitive exact text search across local memory markdown files. Returns JSON results with path, line and snippet.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("query", obj { put("type", "string") })
            })
            put("required", arr("query"))
        },
        readOnly = true,
    )

    val MEMORY_DELETE = AiTool(
        name = "memory_delete",
        description = "Delete a local AI Agent memory file. Destructive: always requires explicit user approval.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("path", obj { put("type", "string") })
            })
            put("required", arr("path"))
        },
        readOnly = false,
    )

    val LOCAL_LIST_DIR = AiTool(
        name = "local_list_dir",
        description = "List files in the current local file context. Relative paths resolve inside the chat/session local workspace.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("path", obj { put("type", "string") })
                put("recursive", obj { put("type", "boolean") })
                put("max_entries", obj { put("type", "integer") })
            })
            put("required", arr())
        },
        readOnly = true,
    )

    val LOCAL_READ_FILE = AiTool(
        name = "local_read_file",
        description = "Read a UTF-8 local file from the current local file context. For binary data, set base64=true.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("path", obj { put("type", "string") })
                put("max_chars", obj { put("type", "integer") })
                put("base64", obj { put("type", "boolean") })
            })
            put("required", arr("path"))
        },
        readOnly = true,
    )

    val LOCAL_WRITE_FILE = AiTool(
        name = "local_write_file",
        description = "Create or overwrite a UTF-8 local file in the current local file context.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("path", obj { put("type", "string") })
                put("content", obj { put("type", "string") })
            })
            put("required", arr("path", "content"))
        },
        readOnly = false,
    )

    val LOCAL_APPEND_FILE = AiTool(
        name = "local_append_file",
        description = "Append UTF-8 text to a local file, creating it if needed.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("path", obj { put("type", "string") })
                put("content", obj { put("type", "string") })
            })
            put("required", arr("path", "content"))
        },
        readOnly = false,
    )

    val LOCAL_MKDIR = AiTool(
        name = "local_mkdir",
        description = "Create a local directory and any missing parent directories.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("path", obj { put("type", "string") })
            })
            put("required", arr("path"))
        },
        readOnly = false,
    )

    val LOCAL_STAT = AiTool(
        name = "local_stat",
        description = "Return metadata for a local file or directory.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("path", obj { put("type", "string") })
            })
            put("required", arr("path"))
        },
        readOnly = true,
    )

    val LOCAL_REPLACE_IN_FILE = AiTool(
        name = "local_replace_in_file",
        description = "Replace an exact substring inside a local UTF-8 file. By default old_string must appear exactly once.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("path", obj { put("type", "string") })
                put("old_string", obj { put("type", "string") })
                put("new_string", obj { put("type", "string") })
                put("replace_all", obj { put("type", "boolean") })
            })
            put("required", arr("path", "old_string", "new_string"))
        },
        readOnly = false,
    )

    val LOCAL_APPLY_PATCH = AiTool(
        name = "local_apply_patch",
        description = "Apply a small apply_patch-style text patch to local files. Supports Add File, Delete File and Update File hunks.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("patch", obj {
                    put("type", "string")
                    put("description", "Patch text beginning with *** Begin Patch and ending with *** End Patch.")
                })
            })
            put("required", arr("patch"))
        },
        readOnly = false,
    )

    val LOCAL_COPY = AiTool(
        name = "local_copy",
        description = "Copy a local file or directory to another local path. File destinations under /storage/emulated/0/Download are exported through Android MediaStore.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("source", obj { put("type", "string") })
                put("destination", obj { put("type", "string") })
                put("overwrite", obj { put("type", "boolean") })
            })
            put("required", arr("source", "destination"))
        },
        readOnly = false,
    )

    val LOCAL_MOVE = AiTool(
        name = "local_move",
        description = "Move a local file or directory to another local path. File destinations under /storage/emulated/0/Download are exported through Android MediaStore.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("source", obj { put("type", "string") })
                put("destination", obj { put("type", "string") })
                put("overwrite", obj { put("type", "boolean") })
            })
            put("required", arr("source", "destination"))
        },
        readOnly = false,
    )

    val LOCAL_RENAME = AiTool(
        name = "local_rename",
        description = "Rename a local file or directory within its current parent directory.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("path", obj { put("type", "string") })
                put("new_name", obj { put("type", "string") })
            })
            put("required", arr("path", "new_name"))
        },
        readOnly = false,
    )

    val LOCAL_DELETE_TO_TRASH = AiTool(
        name = "local_delete_to_trash",
        description = "Move a local file or directory to the app trash instead of deleting it permanently.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("path", obj { put("type", "string") })
            })
            put("required", arr("path"))
        },
        readOnly = false,
    )

    val LOCAL_DELETE = AiTool(
        name = "local_delete",
        description = "Permanently delete a local file or directory. Destructive.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("path", obj { put("type", "string") })
            })
            put("required", arr("path"))
        },
        readOnly = false,
    )

    val LOCAL_SEARCH_FILES = tool("local_search_files", "Search local file names under a directory.", true, arrayOf("query"), "path" to "string", "query" to "string", "max_results" to "integer")
    val LOCAL_SEARCH_TEXT = tool("local_search_text", "Search UTF-8 text files under a directory and return matching path/line snippets.", true, arrayOf("query"), "path" to "string", "query" to "string", "max_results" to "integer")
    val LOCAL_READ_FILE_RANGE = tool("local_read_file_range", "Read a 1-based inclusive line range from a local UTF-8 file.", true, arrayOf("path", "start_line", "end_line"), "path" to "string", "start_line" to "integer", "end_line" to "integer")
    val LOCAL_HASH_FILE = tool("local_hash_file", "Compute a local file hash. Defaults to sha256.", true, arrayOf("path"), "path" to "string", "algorithm" to "string")
    val LOCAL_FIND_DUPLICATES = tool("local_find_duplicates", "Find duplicate local files by size and sha256 hash.", true, arrayOf(), "path" to "string", "max_results" to "integer")
    val LOCAL_GET_MIME = tool("local_get_mime", "Guess the MIME type for a local file.", true, arrayOf("path"), "path" to "string")
    val LOCAL_GET_METADATA = tool("local_get_metadata", "Return local file metadata including stat, MIME and sha256.", true, arrayOf("path"), "path" to "string")
    val LOCAL_CREATE_TEMP_FILE = tool("local_create_temp_file", "Create a temporary UTF-8 file in the current local workspace.", false, arrayOf(), "prefix" to "string", "suffix" to "string", "content" to "string")
    val LOCAL_DIFF_FILES = tool("local_diff_files", "Create a line diff between two local UTF-8 files.", true, arrayOf("left_path", "right_path"), "left_path" to "string", "right_path" to "string")
    val LOCAL_DIFF_TEXT = tool("local_diff_text", "Create a line diff between two text strings.", true, arrayOf("left_text", "right_text"), "left_text" to "string", "right_text" to "string")
    val LOCAL_PREVIEW_PATCH = tool("local_preview_patch", "Preview an apply_patch-style patch without modifying files.", true, arrayOf("patch"), "patch" to "string")
    val LOCAL_APPLY_BATCH_PATCH = tool("local_apply_batch_patch", "Apply multiple apply_patch-style patches to local files.", false, arrayOf("patches"), "patches" to "array")
    val LOCAL_REVERT_FILE = tool("local_revert_file", "Restore the latest backup captured before a local tool modified a file.", false, arrayOf("path"), "path" to "string")
    val LOCAL_TRASH_LIST = tool("local_trash_list", "List files currently in the app trash.", true, arrayOf())
    val LOCAL_TRASH_RESTORE = tool("local_trash_restore", "Restore an item from app trash by trash_path or original_path.", false, arrayOf(), "trash_path" to "string", "original_path" to "string")
    val LOCAL_TRASH_EMPTY = tool("local_trash_empty", "Empty the app trash permanently.", false, arrayOf())

    val ARCHIVE_LIST = AiTool(
        name = "archive_list",
        description = "List entries in a supported local archive: zip, jar, aar, tar, tar.gz, tgz or 7z.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("path", obj { put("type", "string") })
                put("max_entries", obj { put("type", "integer") })
            })
            put("required", arr("path"))
        },
        readOnly = true,
    )

    val ARCHIVE_READ_FILE = AiTool(
        name = "archive_read_file",
        description = "Read a UTF-8 text entry from a supported local archive without extracting the whole archive.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("path", obj { put("type", "string") })
                put("entry", obj { put("type", "string") })
                put("max_chars", obj { put("type", "integer") })
            })
            put("required", arr("path", "entry"))
        },
        readOnly = true,
    )

    val ARCHIVE_EXTRACT = AiTool(
        name = "archive_extract",
        description = "Extract a supported local archive to a destination directory.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("path", obj { put("type", "string") })
                put("destination", obj { put("type", "string") })
            })
            put("required", arr("path"))
        },
        readOnly = false,
    )

    val ARCHIVE_CREATE = AiTool(
        name = "archive_create",
        description = "Create a local archive from one or more files or directories. Supports zip, tar, tar.gz and 7z. Destination can be /storage/emulated/0/Download/name.zip to save through Android MediaStore.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("source_paths", obj {
                    put("type", "array")
                    put("items", obj { put("type", "string") })
                })
                put("destination", obj { put("type", "string") })
                put("format", obj {
                    put("type", "string")
                    put("description", "zip, tar, tar.gz, tgz or 7z. Defaults from destination extension.")
                })
            })
            put("required", arr("source_paths", "destination"))
        },
        readOnly = false,
    )

    val ARCHIVE_TEST = AiTool(
        name = "archive_test",
        description = "Validate that a supported local archive can be opened and read.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("path", obj { put("type", "string") })
            })
            put("required", arr("path"))
        },
        readOnly = true,
    )

    val ARCHIVE_ADD_ENTRIES = tool("archive_add_entries", "Add local files to a ZIP archive. ZIP archives only.", false, arrayOf("path", "source_paths"), "path" to "string", "source_paths" to "array", "entry_prefix" to "string")
    val ARCHIVE_DELETE_ENTRIES = tool("archive_delete_entries", "Delete entries from a ZIP archive. ZIP archives only.", false, arrayOf("path", "entries"), "path" to "string", "entries" to "array")
    val ARCHIVE_UPDATE_ENTRY = tool("archive_update_entry", "Create or replace a UTF-8 text entry in a ZIP archive. ZIP archives only.", false, arrayOf("path", "entry", "content"), "path" to "string", "entry" to "string", "content" to "string")
    val ARCHIVE_LIST_NESTED = tool("archive_list_nested", "List entries in a nested archive path like outer.zip!inner.zip.", true, arrayOf("path"), "path" to "string", "max_entries" to "integer")
    val ARCHIVE_EXTRACT_NESTED = tool("archive_extract_nested", "Extract a nested archive path like outer.zip!inner.zip to a destination directory.", false, arrayOf("path"), "path" to "string", "destination" to "string")

    val APK_INSPECT = tool("apk_inspect", "Inspect APK package name, version and requested permissions.", true, arrayOf("path"), "path" to "string")
    val IMAGE_OCR = tool("image_ocr", "Run OCR on a local image using ML Kit text recognition.", true, arrayOf("path"), "path" to "string")
    val QR_SCAN_IMAGE = tool("qr_scan_image", "Scan QR/barcodes from a local image using ML Kit barcode scanning.", true, arrayOf("path"), "path" to "string")
    val EXIF_READ = tool("exif_read", "Read common EXIF tags from a local image.", true, arrayOf("path"), "path" to "string")
    val EXIF_REMOVE = tool("exif_remove", "Remove common EXIF location/camera tags from a local image.", false, arrayOf("path"), "path" to "string")
    val PDF_EXTRACT_TEXT = tool("pdf_extract_text", "Best-effort text extraction from a local PDF.", true, arrayOf("path"), "path" to "string", "max_chars" to "integer")
    val MEDIA_GET_INFO = tool("media_get_info", "Read duration, dimensions and media metadata from a local audio/video file.", true, arrayOf("path"), "path" to "string")
    val STORAGE_ANALYZE = tool("storage_analyze", "Analyze local storage usage under a directory.", true, arrayOf(), "path" to "string", "max_entries" to "integer")
    val TERMINAL_RUN = tool("terminal_run", "Run a local command in the current workspace with a short timeout. Write tool: requires approval in agent mode.", false, arrayOf("command"), "command" to "string", "args" to "array", "timeout_ms" to "integer")

    val GITHUB_READ_PUBLIC_FILE = tool("github_read_public_file", "Read a public GitHub file without authentication.", true, arrayOf("owner", "repo", "path"), "owner" to "string", "repo" to "string", "path" to "string", "ref" to "string", "max_chars" to "integer")
    val GITHUB_LIST_PUBLIC_DIR = tool("github_list_public_dir", "List a public GitHub repository directory without authentication.", true, arrayOf("owner", "repo"), "owner" to "string", "repo" to "string", "path" to "string", "ref" to "string")

    val SKILL_IMPORT = tool("skill_import", "Validate a local .gskill/.zip skill pack and return an import preview. UI confirmation is still required before installing.", true, arrayOf("path"), "path" to "string")
    val SKILL_LIST = tool("skill_list", "List installed AI skills and packs.", true, arrayOf())
    val SKILL_READ = tool("skill_read", "Read metadata, instructions, or a bundled text resource for an installed AI skill.", true, arrayOf("skill_id"), "skill_id" to "string", "path" to "string", "max_chars" to "integer")
    val SKILL_ENABLE = tool("skill_enable", "Enable or disable an installed AI skill.", false, arrayOf("skill_id", "enabled"), "skill_id" to "string", "enabled" to "boolean")
    val SKILL_DELETE = tool("skill_delete", "Delete an installed AI skill pack. Destructive.", false, arrayOf("pack_id"), "pack_id" to "string")

    val FILE_PICKER_CURRENT_CONTEXT = AiTool(
        name = "file_picker_current_context",
        description = "Describe the current local file context: session workspace, attached file path, selected repository, and supported local/archive tool roots.",
        parameters = obj {
            put("type", "object")
            put("properties", obj {})
            put("required", arr())
        },
        readOnly = true,
    )

    val LOCAL_TOOLS: List<AiTool> = listOf(
        LOCAL_LIST_DIR, LOCAL_READ_FILE, LOCAL_WRITE_FILE, LOCAL_APPEND_FILE,
        LOCAL_MKDIR, LOCAL_STAT, LOCAL_REPLACE_IN_FILE, LOCAL_APPLY_PATCH,
        LOCAL_COPY, LOCAL_MOVE, LOCAL_RENAME, LOCAL_DELETE_TO_TRASH, LOCAL_DELETE,
        LOCAL_SEARCH_FILES, LOCAL_SEARCH_TEXT, LOCAL_READ_FILE_RANGE, LOCAL_HASH_FILE,
        LOCAL_FIND_DUPLICATES, LOCAL_GET_MIME, LOCAL_GET_METADATA, LOCAL_CREATE_TEMP_FILE,
        LOCAL_DIFF_FILES, LOCAL_DIFF_TEXT, LOCAL_PREVIEW_PATCH, LOCAL_APPLY_BATCH_PATCH,
        LOCAL_REVERT_FILE, LOCAL_TRASH_LIST, LOCAL_TRASH_RESTORE, LOCAL_TRASH_EMPTY,
        APK_INSPECT, IMAGE_OCR, QR_SCAN_IMAGE, EXIF_READ, EXIF_REMOVE, PDF_EXTRACT_TEXT,
        MEDIA_GET_INFO, STORAGE_ANALYZE, TERMINAL_RUN,
        FILE_PICKER_CURRENT_CONTEXT,
    )

    val ARCHIVE_TOOLS: List<AiTool> = listOf(
        ARCHIVE_LIST, ARCHIVE_READ_FILE, ARCHIVE_EXTRACT, ARCHIVE_CREATE, ARCHIVE_TEST,
        ARCHIVE_ADD_ENTRIES, ARCHIVE_DELETE_ENTRIES, ARCHIVE_UPDATE_ENTRY,
        ARCHIVE_LIST_NESTED, ARCHIVE_EXTRACT_NESTED,
    )

    val PUBLIC_REMOTE_TOOLS: List<AiTool> = listOf(GITHUB_READ_PUBLIC_FILE, GITHUB_LIST_PUBLIC_DIR)
    val SKILL_TOOLS: List<AiTool> = listOf(SKILL_IMPORT, SKILL_LIST, SKILL_READ, SKILL_ENABLE, SKILL_DELETE)

    /** All tools, in canonical order. */
    val ALL: List<AiTool> = listOf(
        TODO_WRITE, TODO_UPDATE,
        LIST_DIR, READ_FILE, READ_FILE_RANGE, SEARCH_REPO,
        WEB_SEARCH, WEB_FETCH,
        LIST_BRANCHES, COMPARE_REFS,
        LIST_PULLS, READ_PR,
        LIST_ISSUES, READ_ISSUE,
        READ_CHECK_RUNS, READ_WORKFLOW_RUN,
        EDIT_FILE, WRITE_FILE, CREATE_BRANCH, COMMIT, OPEN_PR,
        COMMENT_PR, COMMENT_ISSUE, CREATE_ISSUE,
        MEMORY_READ, MEMORY_WRITE, MEMORY_APPEND, MEMORY_LIST, MEMORY_SEARCH, MEMORY_DELETE,
    ) + LOCAL_TOOLS + ARCHIVE_TOOLS + PUBLIC_REMOTE_TOOLS + SKILL_TOOLS

    val CHAT_ARTIFACTS: List<AiTool> = listOf(ARTIFACT_WRITE, ARTIFACT_UPDATE)
    val TASK_TOOLS: List<AiTool> = listOf(TODO_WRITE, TODO_UPDATE)
    val CHAT_TOOLS: List<AiTool> = TASK_TOOLS + CHAT_ARTIFACTS + listOf(WEB_SEARCH, WEB_FETCH) + LOCAL_TOOLS + ARCHIVE_TOOLS + PUBLIC_REMOTE_TOOLS + SKILL_TOOLS

    fun byName(name: String): AiTool? =
        (ALL + CHAT_ARTIFACTS).firstOrNull { it.name == name }

    fun isLocalOrArchive(name: String): Boolean =
        (LOCAL_TOOLS + ARCHIVE_TOOLS + PUBLIC_REMOTE_TOOLS + SKILL_TOOLS).any { it.name == name }

    fun isChatRuntimeTool(name: String): Boolean =
        isLocalOrArchive(name) || name == WEB_SEARCH.name || name == WEB_FETCH.name
}

/** Tiny helpers to keep the JSON-Schema literals readable above. */
private fun obj(block: JSONObject.() -> Unit): JSONObject = JSONObject().apply(block)
private fun arr(vararg items: String): org.json.JSONArray =
    org.json.JSONArray().apply { items.forEach { put(it) } }

private fun tool(
    name: String,
    description: String,
    readOnly: Boolean,
    required: Array<String>,
    vararg properties: Pair<String, String>,
): AiTool = AiTool(
    name = name,
    description = description,
    parameters = obj {
        put("type", "object")
        put("properties", obj {
            properties.forEach { (key, type) ->
                put(key, obj {
                    put("type", type)
                    if (type == "array") put("items", obj { put("type", "string") })
                })
            }
        })
        put("required", org.json.JSONArray().apply { required.forEach { put(it) } })
    },
    readOnly = readOnly,
)
