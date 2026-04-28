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

    /** All tools, in canonical order. */
    val ALL: List<AiTool> = listOf(
        LIST_DIR, READ_FILE, SEARCH_REPO,
        WRITE_FILE, CREATE_BRANCH, COMMIT, OPEN_PR,
    )

    fun byName(name: String): AiTool? = ALL.firstOrNull { it.name == name }
}

/** Tiny helpers to keep the JSON-Schema literals readable above. */
private fun obj(block: JSONObject.() -> Unit): JSONObject = JSONObject().apply(block)
private fun arr(vararg items: String): org.json.JSONArray =
    org.json.JSONArray().apply { items.forEach { put(it) } }
