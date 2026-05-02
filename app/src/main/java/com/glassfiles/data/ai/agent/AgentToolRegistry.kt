package com.glassfiles.data.ai.agent

enum class AiToolDomain {
    REPOSITORY,
    MEMORY,
    LOCAL_FILE,
    ARCHIVE,
    WEB,
    PUBLIC_GITHUB,
    SKILL,
    ARTIFACT,
    TASK,
    SYSTEM,
}

enum class AiToolUiKind {
    READ,
    LIST,
    SEARCH,
    EDIT,
    WRITE,
    DIFF,
    DELETE,
    ARCHIVE,
    TERMINAL,
    MEMORY,
    WEB,
    GITHUB,
    SKILL,
    ARTIFACT,
    TASK,
    SYSTEM,
    OTHER,
}

enum class AiToolRisk {
    READ_ONLY,
    LOW,
    MEDIUM,
    HIGH,
    DESTRUCTIVE,
}

data class AiToolMetadata(
    val name: String,
    val description: String,
    val domain: AiToolDomain,
    val uiKind: AiToolUiKind,
    val readOnly: Boolean,
    val approvalCategory: AiAgentApprovalCategory,
    val risk: AiToolRisk,
    val allowedInChatOnly: Boolean,
    val changesFiles: Boolean = false,
    val destructive: Boolean = false,
    val dangerous: Boolean = false,
    val shouldDefer: Boolean = false,
    val maxResultSizeChars: Int = DEFAULT_MAX_RESULT_SIZE_CHARS,
    val searchHint: String = description,
) {
    companion object {
        const val DEFAULT_MAX_RESULT_SIZE_CHARS = 20_000
        const val LARGE_RESULT_SIZE_CHARS = 100_000
    }
}

object AgentToolRegistry {
    private val legacyDestructiveTools = setOf("delete_file", "reset_hard", "force_push")
    private val legacyCommitTools = setOf("commit_changes", "create_pull_request")

    val all: List<AiToolMetadata> by lazy {
        listOf(
            meta(AgentTools.LIST_DIR, AiToolDomain.REPOSITORY, AiToolUiKind.LIST, AiAgentApprovalCategory.READ, AiToolRisk.READ_ONLY, false, "list repository directories"),
            meta(AgentTools.READ_FILE, AiToolDomain.REPOSITORY, AiToolUiKind.READ, AiAgentApprovalCategory.READ, AiToolRisk.READ_ONLY, false, "read repository files"),
            meta(AgentTools.READ_FILE_RANGE, AiToolDomain.REPOSITORY, AiToolUiKind.READ, AiAgentApprovalCategory.READ, AiToolRisk.READ_ONLY, false, "read repository file ranges"),
            meta(AgentTools.SEARCH_REPO, AiToolDomain.REPOSITORY, AiToolUiKind.SEARCH, AiAgentApprovalCategory.READ, AiToolRisk.READ_ONLY, false, "search repository code"),
            meta(AgentTools.WEB_SEARCH, AiToolDomain.WEB, AiToolUiKind.WEB, AiAgentApprovalCategory.READ, AiToolRisk.READ_ONLY, true, "search the web", shouldDefer = true),
            meta(AgentTools.WEB_FETCH, AiToolDomain.WEB, AiToolUiKind.WEB, AiAgentApprovalCategory.READ, AiToolRisk.READ_ONLY, true, "fetch web pages", shouldDefer = true),
            meta(AgentTools.LIST_BRANCHES, AiToolDomain.REPOSITORY, AiToolUiKind.LIST, AiAgentApprovalCategory.READ, AiToolRisk.READ_ONLY, false, "list repository branches"),
            meta(AgentTools.COMPARE_REFS, AiToolDomain.REPOSITORY, AiToolUiKind.DIFF, AiAgentApprovalCategory.READ, AiToolRisk.READ_ONLY, false, "compare repository refs"),
            meta(AgentTools.LIST_PULLS, AiToolDomain.REPOSITORY, AiToolUiKind.LIST, AiAgentApprovalCategory.READ, AiToolRisk.READ_ONLY, false, "list pull requests"),
            meta(AgentTools.READ_PR, AiToolDomain.REPOSITORY, AiToolUiKind.READ, AiAgentApprovalCategory.READ, AiToolRisk.READ_ONLY, false, "read pull requests"),
            meta(AgentTools.LIST_ISSUES, AiToolDomain.REPOSITORY, AiToolUiKind.LIST, AiAgentApprovalCategory.READ, AiToolRisk.READ_ONLY, false, "list issues"),
            meta(AgentTools.READ_ISSUE, AiToolDomain.REPOSITORY, AiToolUiKind.READ, AiAgentApprovalCategory.READ, AiToolRisk.READ_ONLY, false, "read issues"),
            meta(AgentTools.READ_CHECK_RUNS, AiToolDomain.REPOSITORY, AiToolUiKind.READ, AiAgentApprovalCategory.READ, AiToolRisk.READ_ONLY, false, "read check runs"),
            meta(AgentTools.READ_WORKFLOW_RUN, AiToolDomain.REPOSITORY, AiToolUiKind.READ, AiAgentApprovalCategory.READ, AiToolRisk.READ_ONLY, false, "read workflow run logs"),
            meta(AgentTools.EDIT_FILE, AiToolDomain.REPOSITORY, AiToolUiKind.EDIT, AiAgentApprovalCategory.EDIT, AiToolRisk.MEDIUM, false, "edit repository files", changesFiles = true),
            meta(AgentTools.WRITE_FILE, AiToolDomain.REPOSITORY, AiToolUiKind.WRITE, AiAgentApprovalCategory.WRITE, AiToolRisk.MEDIUM, false, "write repository files", changesFiles = true),
            meta(AgentTools.CREATE_BRANCH, AiToolDomain.REPOSITORY, AiToolUiKind.GITHUB, AiAgentApprovalCategory.COMMIT, AiToolRisk.MEDIUM, false, "create repository branches"),
            meta(AgentTools.COMMIT, AiToolDomain.REPOSITORY, AiToolUiKind.GITHUB, AiAgentApprovalCategory.COMMIT, AiToolRisk.MEDIUM, false, "commit repository changes", changesFiles = true),
            meta(AgentTools.OPEN_PR, AiToolDomain.REPOSITORY, AiToolUiKind.GITHUB, AiAgentApprovalCategory.COMMIT, AiToolRisk.MEDIUM, false, "open pull requests"),
            meta(AgentTools.COMMENT_PR, AiToolDomain.REPOSITORY, AiToolUiKind.WRITE, AiAgentApprovalCategory.WRITE, AiToolRisk.LOW, false, "comment on pull requests"),
            meta(AgentTools.COMMENT_ISSUE, AiToolDomain.REPOSITORY, AiToolUiKind.WRITE, AiAgentApprovalCategory.WRITE, AiToolRisk.LOW, false, "comment on issues"),
            meta(AgentTools.CREATE_ISSUE, AiToolDomain.REPOSITORY, AiToolUiKind.WRITE, AiAgentApprovalCategory.WRITE, AiToolRisk.LOW, false, "create issues"),

            meta(AgentTools.MEMORY_READ, AiToolDomain.MEMORY, AiToolUiKind.MEMORY, AiAgentApprovalCategory.READ, AiToolRisk.READ_ONLY, false, "read agent memory"),
            meta(AgentTools.MEMORY_LIST, AiToolDomain.MEMORY, AiToolUiKind.MEMORY, AiAgentApprovalCategory.READ, AiToolRisk.READ_ONLY, false, "list agent memory"),
            meta(AgentTools.MEMORY_SEARCH, AiToolDomain.MEMORY, AiToolUiKind.MEMORY, AiAgentApprovalCategory.READ, AiToolRisk.READ_ONLY, false, "search agent memory"),
            meta(AgentTools.MEMORY_WRITE, AiToolDomain.MEMORY, AiToolUiKind.MEMORY, AiAgentApprovalCategory.WRITE, AiToolRisk.LOW, false, "write agent memory", changesFiles = true),
            meta(AgentTools.MEMORY_APPEND, AiToolDomain.MEMORY, AiToolUiKind.MEMORY, AiAgentApprovalCategory.WRITE, AiToolRisk.LOW, false, "append agent memory", changesFiles = true),
            meta(AgentTools.MEMORY_DELETE, AiToolDomain.MEMORY, AiToolUiKind.DELETE, AiAgentApprovalCategory.DESTRUCTIVE, AiToolRisk.DESTRUCTIVE, false, "delete agent memory", changesFiles = true, destructive = true, dangerous = true),

            meta(AgentTools.ARTIFACT_WRITE, AiToolDomain.ARTIFACT, AiToolUiKind.ARTIFACT, AiAgentApprovalCategory.WRITE, AiToolRisk.LOW, true, "create chat file attachments", changesFiles = true),
            meta(AgentTools.ARTIFACT_UPDATE, AiToolDomain.ARTIFACT, AiToolUiKind.ARTIFACT, AiAgentApprovalCategory.EDIT, AiToolRisk.LOW, true, "update chat file attachments", changesFiles = true),
            meta(AgentTools.TODO_WRITE, AiToolDomain.TASK, AiToolUiKind.TASK, AiAgentApprovalCategory.READ, AiToolRisk.READ_ONLY, true, "write task checklists"),
            meta(AgentTools.TODO_UPDATE, AiToolDomain.TASK, AiToolUiKind.TASK, AiAgentApprovalCategory.READ, AiToolRisk.READ_ONLY, true, "update task checklists"),

            meta(AgentTools.FILE_PICKER_CURRENT_CONTEXT, AiToolDomain.SYSTEM, AiToolUiKind.SYSTEM, AiAgentApprovalCategory.READ, AiToolRisk.READ_ONLY, true, "describe current file context"),

            *AgentTools.LOCAL_TOOLS.map { localMetadata(it) }.toTypedArray(),
            *AgentTools.ARCHIVE_TOOLS.map { archiveMetadata(it) }.toTypedArray(),
            *AgentTools.PUBLIC_REMOTE_TOOLS.map { tool ->
                meta(tool, AiToolDomain.PUBLIC_GITHUB, AiToolUiKind.GITHUB, AiAgentApprovalCategory.READ, AiToolRisk.READ_ONLY, true, "read public github content", shouldDefer = true)
            }.toTypedArray(),
            *AgentTools.SKILL_TOOLS.map { skillMetadata(it) }.toTypedArray(),
        ).distinctBy { it.name }
    }

    private val metadataByName: Map<String, AiToolMetadata> by lazy {
        all.associateBy { it.name }
    }

    val allNames: Set<String> by lazy { metadataByName.keys }
    val defaultSkillToolNames: List<String> by lazy {
        all.filter { it.allowedInChatOnly || it.domain == AiToolDomain.REPOSITORY }
            .filterNot { it.destructive || it.dangerous }
            .map { it.name }
    }

    fun byName(name: String): AiToolMetadata? = metadataByName[name]

    fun metadataFor(tool: AiTool): AiToolMetadata =
        byName(tool.name) ?: fallbackMetadata(tool)

    fun isKnown(name: String): Boolean =
        byName(name) != null || AgentTools.byName(name) != null

    fun isReadOnly(name: String): Boolean =
        byName(name)?.readOnly ?: (AgentTools.byName(name)?.readOnly == true)

    fun isDestructive(name: String): Boolean =
        byName(name)?.destructive == true ||
            name in legacyDestructiveTools ||
            name.contains("delete", ignoreCase = true)

    fun isDangerous(name: String): Boolean =
        byName(name)?.dangerous == true || isDestructive(name)

    fun approvalCategoryFor(toolName: String, tool: AiTool?): AiAgentApprovalCategory =
        byName(toolName)?.approvalCategory ?: when {
            toolName in legacyDestructiveTools -> AiAgentApprovalCategory.DESTRUCTIVE
            toolName in legacyCommitTools -> AiAgentApprovalCategory.COMMIT
            toolName.contains("delete", ignoreCase = true) -> AiAgentApprovalCategory.DESTRUCTIVE
            tool?.readOnly == true -> AiAgentApprovalCategory.READ
            else -> AiAgentApprovalCategory.WRITE
        }

    fun namesForCategory(category: AiAgentApprovalCategory): Set<String> =
        all.asSequence()
            .filter { it.approvalCategory == category }
            .map { it.name }
            .toSet()

    fun chatOnlyToolNames(): Set<String> =
        all.asSequence()
            .filter { it.allowedInChatOnly }
            .map { it.name }
            .toSet()

    fun uiKindFor(name: String): AiToolUiKind =
        byName(name)?.uiKind ?: AiToolUiKind.OTHER

    private fun localMetadata(tool: AiTool): AiToolMetadata {
        val name = tool.name
        return when (name) {
            AgentTools.LOCAL_LIST_DIR.name -> readLocal(tool, AiToolUiKind.LIST, "list local directories")
            AgentTools.LOCAL_READ_FILE.name -> readLocal(tool, AiToolUiKind.READ, "read local files")
            AgentTools.LOCAL_STAT.name -> readLocal(tool, AiToolUiKind.READ, "stat local files")
            AgentTools.LOCAL_SEARCH_FILES.name -> readLocal(tool, AiToolUiKind.SEARCH, "search local filenames")
            AgentTools.LOCAL_SEARCH_TEXT.name -> readLocal(tool, AiToolUiKind.SEARCH, "search local file text")
            AgentTools.LOCAL_READ_FILE_RANGE.name -> readLocal(tool, AiToolUiKind.READ, "read local file ranges")
            AgentTools.LOCAL_HASH_FILE.name -> readLocal(tool, AiToolUiKind.READ, "hash local files")
            AgentTools.LOCAL_FIND_DUPLICATES.name -> readLocal(tool, AiToolUiKind.SEARCH, "find duplicate files", shouldDefer = true)
            AgentTools.LOCAL_GET_MIME.name -> readLocal(tool, AiToolUiKind.READ, "detect file mime type")
            AgentTools.LOCAL_GET_METADATA.name -> readLocal(tool, AiToolUiKind.READ, "read file metadata")
            AgentTools.LOCAL_DIFF_FILES.name -> readLocal(tool, AiToolUiKind.DIFF, "diff local files")
            AgentTools.LOCAL_DIFF_TEXT.name -> readLocal(tool, AiToolUiKind.DIFF, "diff text")
            AgentTools.LOCAL_PREVIEW_PATCH.name -> readLocal(tool, AiToolUiKind.DIFF, "preview local patches")
            AgentTools.LOCAL_TRASH_LIST.name -> readLocal(tool, AiToolUiKind.LIST, "list app trash")
            AgentTools.APK_INSPECT.name -> readLocal(tool, AiToolUiKind.READ, "inspect apk files", shouldDefer = true)
            AgentTools.IMAGE_OCR.name -> readLocal(tool, AiToolUiKind.READ, "ocr images", shouldDefer = true)
            AgentTools.QR_SCAN_IMAGE.name -> readLocal(tool, AiToolUiKind.READ, "scan qr codes", shouldDefer = true)
            AgentTools.EXIF_READ.name -> readLocal(tool, AiToolUiKind.READ, "read image exif")
            AgentTools.PDF_EXTRACT_TEXT.name -> readLocal(tool, AiToolUiKind.READ, "extract pdf text", shouldDefer = true)
            AgentTools.MEDIA_GET_INFO.name -> readLocal(tool, AiToolUiKind.READ, "read media metadata", shouldDefer = true)
            AgentTools.STORAGE_ANALYZE.name -> readLocal(tool, AiToolUiKind.SEARCH, "analyze storage usage", shouldDefer = true)
            AgentTools.TERMINAL_RUN.name -> meta(tool, AiToolDomain.LOCAL_FILE, AiToolUiKind.TERMINAL, AiAgentApprovalCategory.WRITE, AiToolRisk.HIGH, true, "run terminal commands", dangerous = true, shouldDefer = true)
            AgentTools.LOCAL_DELETE_TO_TRASH.name,
            AgentTools.LOCAL_DELETE.name,
            AgentTools.LOCAL_TRASH_EMPTY.name -> meta(tool, AiToolDomain.LOCAL_FILE, AiToolUiKind.DELETE, AiAgentApprovalCategory.DESTRUCTIVE, AiToolRisk.DESTRUCTIVE, true, "delete local files", changesFiles = true, destructive = true, dangerous = true)
            AgentTools.LOCAL_REPLACE_IN_FILE.name,
            AgentTools.LOCAL_APPLY_PATCH.name,
            AgentTools.LOCAL_APPLY_BATCH_PATCH.name -> meta(tool, AiToolDomain.LOCAL_FILE, AiToolUiKind.EDIT, AiAgentApprovalCategory.EDIT, AiToolRisk.MEDIUM, true, "edit local files", changesFiles = true)
            else -> meta(tool, AiToolDomain.LOCAL_FILE, AiToolUiKind.WRITE, AiAgentApprovalCategory.WRITE, AiToolRisk.LOW, true, "write local files", changesFiles = true)
        }
    }

    private fun archiveMetadata(tool: AiTool): AiToolMetadata {
        val name = tool.name
        return when (name) {
            AgentTools.ARCHIVE_LIST.name,
            AgentTools.ARCHIVE_READ_FILE.name,
            AgentTools.ARCHIVE_TEST.name,
            AgentTools.ARCHIVE_LIST_NESTED.name -> meta(tool, AiToolDomain.ARCHIVE, AiToolUiKind.ARCHIVE, AiAgentApprovalCategory.READ, AiToolRisk.READ_ONLY, true, "read archive contents", shouldDefer = true)
            AgentTools.ARCHIVE_DELETE_ENTRIES.name -> meta(tool, AiToolDomain.ARCHIVE, AiToolUiKind.DELETE, AiAgentApprovalCategory.DESTRUCTIVE, AiToolRisk.DESTRUCTIVE, true, "delete archive entries", changesFiles = true, destructive = true, dangerous = true, shouldDefer = true)
            else -> meta(tool, AiToolDomain.ARCHIVE, AiToolUiKind.ARCHIVE, AiAgentApprovalCategory.WRITE, AiToolRisk.MEDIUM, true, "write archive files", changesFiles = true, shouldDefer = true)
        }
    }

    private fun skillMetadata(tool: AiTool): AiToolMetadata =
        when (tool.name) {
            AgentTools.SKILL_ENABLE.name -> meta(tool, AiToolDomain.SKILL, AiToolUiKind.SKILL, AiAgentApprovalCategory.WRITE, AiToolRisk.LOW, true, "enable or disable skills", changesFiles = true)
            AgentTools.SKILL_DELETE.name -> meta(tool, AiToolDomain.SKILL, AiToolUiKind.DELETE, AiAgentApprovalCategory.DESTRUCTIVE, AiToolRisk.DESTRUCTIVE, true, "delete skill packs", changesFiles = true, destructive = true, dangerous = true)
            else -> meta(tool, AiToolDomain.SKILL, AiToolUiKind.SKILL, AiAgentApprovalCategory.READ, AiToolRisk.READ_ONLY, true, "inspect skills")
        }

    private fun readLocal(
        tool: AiTool,
        uiKind: AiToolUiKind,
        searchHint: String,
        shouldDefer: Boolean = false,
    ): AiToolMetadata =
        meta(tool, AiToolDomain.LOCAL_FILE, uiKind, AiAgentApprovalCategory.READ, AiToolRisk.READ_ONLY, true, searchHint, shouldDefer = shouldDefer)

    private fun meta(
        tool: AiTool,
        domain: AiToolDomain,
        uiKind: AiToolUiKind,
        approvalCategory: AiAgentApprovalCategory,
        risk: AiToolRisk,
        allowedInChatOnly: Boolean,
        searchHint: String,
        changesFiles: Boolean = false,
        destructive: Boolean = false,
        dangerous: Boolean = destructive,
        shouldDefer: Boolean = false,
        maxResultSizeChars: Int = AiToolMetadata.DEFAULT_MAX_RESULT_SIZE_CHARS,
    ): AiToolMetadata =
        AiToolMetadata(
            name = tool.name,
            description = tool.description,
            domain = domain,
            uiKind = uiKind,
            readOnly = tool.readOnly,
            approvalCategory = approvalCategory,
            risk = risk,
            allowedInChatOnly = allowedInChatOnly,
            changesFiles = changesFiles,
            destructive = destructive,
            dangerous = dangerous,
            shouldDefer = shouldDefer,
            maxResultSizeChars = maxResultSizeChars,
            searchHint = searchHint,
        )

    private fun fallbackMetadata(tool: AiTool): AiToolMetadata =
        AiToolMetadata(
            name = tool.name,
            description = tool.description,
            domain = AiToolDomain.SYSTEM,
            uiKind = AiToolUiKind.OTHER,
            readOnly = tool.readOnly,
            approvalCategory = if (tool.readOnly) AiAgentApprovalCategory.READ else AiAgentApprovalCategory.WRITE,
            risk = if (tool.readOnly) AiToolRisk.READ_ONLY else AiToolRisk.MEDIUM,
            allowedInChatOnly = false,
        )
}
