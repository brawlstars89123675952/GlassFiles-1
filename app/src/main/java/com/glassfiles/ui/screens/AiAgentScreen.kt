package com.glassfiles.ui.screens

import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.data.ai.AiAttachmentProcessor
import com.glassfiles.data.ai.AiChatSessionStore
import com.glassfiles.data.ai.AiKeyStore
import com.glassfiles.data.ai.AiPreparedAttachment
import com.glassfiles.data.ai.ModelRegistry
import com.glassfiles.data.ai.SystemPrompts
import com.glassfiles.data.ai.agent.AgentToolRegistry
import com.glassfiles.data.ai.agent.AgentTools
import com.glassfiles.data.ai.agent.AiAgentApprovalCategory
import com.glassfiles.data.ai.agent.AiToolUiKind
import com.glassfiles.data.ai.agent.AiToolCall
import com.glassfiles.data.ai.agent.AiToolResult
import com.glassfiles.data.ai.agent.GitHubToolExecutor
import com.glassfiles.data.ai.agent.LineDiff
import com.glassfiles.data.ai.agent.LocalToolExecutor
import com.glassfiles.data.ai.models.AiCapability
import com.glassfiles.data.ai.models.AiMessage
import com.glassfiles.data.ai.models.AiModel
import com.glassfiles.data.ai.models.AiProviderId
import com.glassfiles.data.ai.providers.AiProviders
import com.glassfiles.data.ai.skills.AiSkill
import com.glassfiles.data.ai.skills.AiSkillImportPreview
import com.glassfiles.data.ai.skills.AiSkillPrefs
import com.glassfiles.data.ai.skills.AiSkillRouter
import com.glassfiles.data.ai.skills.AiSkillStore
import com.glassfiles.data.ai.skills.AppAgentContext
import com.glassfiles.data.github.GHRepo
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.data.github.canWrite
import com.glassfiles.ui.screens.ai.ExpensiveActionWarningDialog
import com.glassfiles.ui.screens.ai.terminal.AgentTextButton
import com.glassfiles.ui.screens.ai.terminal.Icon
import com.glassfiles.ui.screens.ai.terminal.IconButton
import com.glassfiles.ui.screens.ai.terminal.Text
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AI agent over GitHub.
 *
 * The screen drives a non-streaming agent loop using the new
 * [com.glassfiles.data.ai.providers.AiProvider.chatWithTools] entry point.
 * Each turn the model can either answer with text or emit one or more
 * [AiToolCall]s. Read-only calls (`list_dir`, `read_file`, `search_repo`)
 * may run automatically when the user opts in; everything destructive
 * (`write_file`, `create_branch`, `commit`, `open_pr`) is gated behind a
 * per-call Approve / Reject card.
 *
 * The active repo / branch / model are persisted with each session so
 * reopening a chat restores the full context (transcript + repo + branch
 * + model). Switching any of them mid-chat is honoured for *future*
 * messages but never wipes the visible transcript — the user's history
 * stays put.
 */
@Composable
fun AiAgentScreen(
    onBack: () -> Unit,
    initialRepoFullName: String? = null,
    initialBranch: String? = null,
    initialPrompt: String? = null,
    onInitialConsumed: () -> Unit = {},
    /** When true, the screen is rendered inside a bottom sheet:
     *  - skip status-bar and back-handler wiring (the host owns both),
     *  - skip the redundant top-bar back arrow. */
    embedded: Boolean = false,
    /** When non-null, an X icon is rendered on the top bar that fully
     * dismisses the screen (vs. [onBack] which only minimises the sheet
     * inside the bottom-sheet host). */
    onClose: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (!embedded) BackHandler(onBack = onBack)

    // ─── State ────────────────────────────────────────────────────────────
    val repos = remember { mutableStateListOf<GHRepo>() }
    var selectedRepo by remember { mutableStateOf<GHRepo?>(null) }

    val branches = remember { mutableStateListOf<String>() }
    var selectedBranch by remember { mutableStateOf<String?>(null) }

    val models = remember { mutableStateListOf<AiModel>() }
    var selectedModel by remember { mutableStateOf<AiModel?>(null) }

    // When the screen was opened from a GitHub entry point (with a
    // preselected repo / prompt) we start a *fresh* session instead of
    // continuing the most recent one — otherwise the user lands on an
    // unrelated chat from another repo.
    val launchedFromGitHub = !initialRepoFullName.isNullOrBlank() || !initialPrompt.isNullOrBlank()

    var sessions by remember { mutableStateOf(AiChatSessionStore.list(context, AGENT_SESSION_MODE)) }
    var activeSessionId by remember {
        mutableStateOf(if (launchedFromGitHub) newAgentSessionId() else (sessions.firstOrNull()?.id ?: newAgentSessionId()))
    }
    var sessionCreatedAt by remember {
        mutableStateOf(sessions.firstOrNull { it.id == activeSessionId }?.createdAt ?: System.currentTimeMillis())
    }
    var showHistory by remember { mutableStateOf(false) }
    // Per-repo system-prompt override editor visibility. Opens a
    // terminal dialog with a multi-line text field; saves to AiAgentPrefs
    // on confirm so subsequent runs in this repo prepend it as a
    // `system` message.
    var showSystemPrompt by remember { mutableStateOf(false) }
    var systemPromptScopeFullName by remember { mutableStateOf<String?>(null) }
    // Terminal-style settings sheet (REPO / BRANCH / MODEL / mode /
    // approval toggles / write limit / protected paths / memory / etc.).
    // Replaces the inline picker chips + switch row
    // that previously lived under the topbar; opens via the gear icon in
    // [AgentTopBar].
    var showSettings by remember { mutableStateOf(false) }
    // Pure presentation toggle — when on, transcript entries appear as
    // a single block (no gradual append) so streaming is invisible. Local
    // to this screen; not persisted because it's a UX preference rather
    // than a behaviour-altering setting.
    var instantRender by remember { mutableStateOf(false) }
    // Memory editor sheets launched from settings. They are mounted next
    // to history / system-prompt dialogs so the settings sheet can close
    // without leaving overlays stranded.
    var showMemoryFiles by remember { mutableStateOf(false) }
    var showWorkingMemory by remember { mutableStateOf(false) }
    var showSkills by remember { mutableStateOf(false) }
    var skillImportPreview by remember { mutableStateOf<AiSkillImportPreview?>(null) }
    var skillImportError by remember { mutableStateOf<String?>(null) }
    var skillsVersion by remember { mutableStateOf(0) }
    var expandToolCallsByDefault by remember {
        mutableStateOf(com.glassfiles.data.ai.AiAgentApprovalPrefs.getExpandToolCalls(context))
    }
    var expandedToolRows by remember { mutableStateOf<Set<String>>(emptySet()) }
    var collapsedToolRows by remember { mutableStateOf<Set<String>>(emptySet()) }
    var memorySheetRepoFullName by remember { mutableStateOf<String?>(null) }
    var pendingWorkspaceId by remember { mutableStateOf<String?>(null) }
    var pendingWorkspaceDiff by remember {
        mutableStateOf<com.glassfiles.data.ai.workspace.WorkspaceDiff?>(null)
    }
    var showWorkspaceReview by remember { mutableStateOf(false) }
    var showWorkspaceCommit by remember { mutableStateOf(false) }
    var workspaceCommitMessage by remember { mutableStateOf("") }
    var workspaceReviewError by remember { mutableStateOf<String?>(null) }
    var workspaceCommitBusy by remember { mutableStateOf(false) }
    // D2 — pending resumable run, if any. Read whenever
    // [activeSessionId] changes; once the user resumes/discards we set
    // it back to null without writing anything until the next launch.
    var pendingResume by remember {
        mutableStateOf<com.glassfiles.data.ai.AiAgentResumeStore.Pending?>(null)
    }

    val transcript = remember { mutableStateListOf<AgentEntry>() }
    val todoItems = remember { mutableStateListOf<AgentTodoItem>() }
    var input by remember { mutableStateOf(TextFieldValue(initialPrompt.orEmpty())) }
    var pendingImage by remember { mutableStateOf<String?>(null) }
    var pendingFile by remember { mutableStateOf<AiPreparedAttachment?>(null) }
    var previewGeneratedFile by remember { mutableStateOf<AiChatSessionStore.GeneratedFile?>(null) }
    var chatOnlyMode by remember { mutableStateOf(false) }
    // Approval prefs are owned by AiAgentApprovalPrefs (DataStore-backed). The agent
    // loop reads them via [snapshotApprovalSettings] at run start so YOLO / write
    // limit / per-category toggles set anywhere in the app actually take effect.
    // We mirror the read-auto-approve flag here purely so the inline switch in the
    // top bar stays in sync without round-tripping through prefs every recomposition.
    var autoApproveReads by remember {
        mutableStateOf(com.glassfiles.data.ai.AiAgentApprovalPrefs.getAutoApproveReads(context))
    }
    var approvalPrefsVersion by remember { mutableStateOf(0) }
    var workspaceMode by remember {
        mutableStateOf(com.glassfiles.data.ai.AiAgentApprovalPrefs.getWorkspaceMode(context))
    }
    var running by remember { mutableStateOf(false) }
    var runJob by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // Banner text shown when the active provider 429/5xx'd and we
    // automatically swapped to a different tool-use model. Cleared on
    // the next successful send.
    var fallbackNotice by remember { mutableStateOf<String?>(null) }
    // Per-session "I've seen the warning" flag for private repos. Re-shown
    // each time the user opens a different private repo.
    var privateRepoDismissed by remember { mutableStateOf<String?>(null) }
    val activeApiKey = selectedModel?.let { AiKeyStore.getKey(context, it.providerId) }.orEmpty()

    // Cost-policy UI state. The selected mode is persisted in
    // AiCostModeStore; we keep a snapshot in compose state so the
    // selector chips re-paint immediately on tap.
    var costMode by remember {
        mutableStateOf(com.glassfiles.data.ai.cost.AiCostModeStore.getMode(context))
    }
    // Pending warning slot. Set by submit() when the cost-policy
    // detects a heavy task; cleared after the user picks Cancel /
    // Continue / Continue+remember. While non-null, the dialog blocks
    // the run.
    var pendingWarning by remember {
        mutableStateOf<com.glassfiles.ui.screens.ai.ExpensiveActionWarning?>(null)
    }
    var pendingWarningInput by remember { mutableStateOf<PendingAgentSend?>(null) }

    // ID of the session whose repo / branch / model we still want to
    // re-apply once the async repo + model lists finish loading. Cleared
    // once everything that *can* be restored has been. Skipped entirely
    // when launched from GitHub — in that case the caller picks repo /
    // branch and we don't want to overlay a stale saved session on top.
    var pendingRestoreSessionId by remember {
        mutableStateOf<String?>(if (launchedFromGitHub) null else activeSessionId)
    }

    // GitHub-launch overrides we still want to apply once `repos` finishes
    // loading (since `getRepos` is async). Cleared once applied so subsequent
    // user picks aren't fought.
    var pendingInitialRepoFullName by remember {
        mutableStateOf(initialRepoFullName?.takeIf { it.isNotBlank() })
    }
    val pendingInitialBranch = remember { initialBranch?.takeIf { it.isNotBlank() } }

    // Pending approvals: tool calls awaiting user decision. The agent loop
    // suspends on these CompletableDeferred until Approve/Reject flips them.
    val approvals = remember { mutableStateListOf<PendingApproval>() }

    // ─── Initial loads ────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        // Repos
        if (GitHubManager.isLoggedIn(context)) {
            try { repos.addAll(GitHubManager.getRepos(context)) } catch (_: Exception) {}
        }
        // Models — chat-capable from any provider with a key
        for (p in AiProviderId.values()) {
            val key = AiKeyStore.getKey(context, p)
            if (key.isBlank()) continue
            try {
                val list = ModelRegistry.getModels(context, p, key, force = false)
                    .filter { AiCapability.TEXT in it.capabilities && !it.deprecated }
                models.addAll(list)
            } catch (_: Exception) { /* swallow per-provider failures */ }
        }
    }

    // Restore the saved transcript synchronously when the screen first
    // opens. Repo / branch / model selection happens later (see below)
    // because those lists are populated asynchronously. Skipped when
    // launched from a GitHub entry point — the caller wants a clean slate
    // scoped to their repo, not a continuation of the most recent chat.
    LaunchedEffect(Unit) {
        if (launchedFromGitHub) {
            // Pre-seed the branch so the branch-loading effect can honour
            // it as soon as `selectedRepo` is set.
            pendingInitialBranch?.let { selectedBranch = it }
            return@LaunchedEffect
        }
        sessions.firstOrNull { it.id == activeSessionId }?.let { session ->
            sessionCreatedAt = session.createdAt
            transcript.clear()
            todoItems.clear()
            transcript.addAll(session.messages.toAgentEntries())
            chatOnlyMode = session.repoFullName == CHAT_ONLY_REPO_KEY
            // Pre-seed the desired branch so the branch-loading effect
            // can honour it once the repo is picked.
            if (!chatOnlyMode && session.branch.isNotBlank()) selectedBranch = session.branch
        }
    }

    // Apply GitHub-launch repo override as soon as `repos` is populated.
    LaunchedEffect(repos.size, pendingInitialRepoFullName) {
        val want = pendingInitialRepoFullName ?: return@LaunchedEffect
        val match = repos.firstOrNull { it.fullName == want }
        if (match != null) {
            selectedRepo = match
            pendingInitialRepoFullName = null
            // Tell the host we've consumed the initial state so back-nav
            // won't replay it on the next visit to the screen.
            onInitialConsumed()
        } else if (repos.isNotEmpty()) {
            // Repo not in the list (e.g. user logged out, private repo).
            // Stop trying so the user can pick manually.
            pendingInitialRepoFullName = null
            onInitialConsumed()
        }
    }

    // Re-attempt repo / model restoration whenever the async lists grow
    // (provider keys may take a beat to enumerate models). We never
    // overwrite a user's manual pick — only fill in the gaps.
    LaunchedEffect(repos.size, models.size, pendingRestoreSessionId) {
        val sessionId = pendingRestoreSessionId ?: return@LaunchedEffect
        val session = AiChatSessionStore.get(context, AGENT_SESSION_MODE, sessionId)
        if (session == null) {
            pendingRestoreSessionId = null
            return@LaunchedEffect
        }
        if (session.repoFullName == CHAT_ONLY_REPO_KEY) {
            chatOnlyMode = true
            selectedRepo = null
            selectedBranch = null
        } else if (selectedRepo == null && session.repoFullName.isNotBlank()) {
            repos.firstOrNull { it.fullName == session.repoFullName }
                ?.let { selectedRepo = it }
        }
        if (selectedModel == null) {
            val saved = if (session.providerId.isNotBlank() && session.modelId.isNotBlank()) {
                runCatching { AiProviderId.valueOf(session.providerId) }.getOrNull()?.let { pid ->
                    models.firstOrNull { it.providerId == pid && it.id == session.modelId }
                }
            } else null
            // Per-repo last-model — when the session itself doesn't pin
            // a model, fall back to whatever the user last used for
            // this exact repo. Saves them from re-picking on every new
            // chat in a familiar repo.
            val perRepo = selectedRepo?.fullName?.let { repoFull ->
                com.glassfiles.data.ai.AiAgentPrefs.getLastModel(context, repoFull)
            }?.let { saved ->
                models.firstOrNull { it.uniqueKey == saved }
            }
            selectedModel = saved ?: perRepo ?: preferToolUseModel(models)
        }
        // Stop watching once we've done what we can. The pickers happily
        // honor any manual changes the user makes from here on.
        val repoDone = session.repoFullName == CHAT_ONLY_REPO_KEY ||
            session.repoFullName.isBlank() ||
            selectedRepo?.fullName == session.repoFullName ||
            (repos.isNotEmpty() && repos.none { it.fullName == session.repoFullName })
        val modelDone = selectedModel != null ||
            (models.isEmpty()) // nothing to choose from yet, but we'll be re-invoked when models grows
        if (repoDone && (modelDone && models.isNotEmpty())) {
            pendingRestoreSessionId = null
        }
    }

    // Reload branches whenever the active repo changes. Crucially this
    // does NOT wipe the transcript — switching repos in an active chat
    // is a context change for *future* messages, not a reason to drop
    // the conversation the user can still see and reference.
    LaunchedEffect(selectedRepo) {
        val repo = selectedRepo
        if (repo == null) {
            branches.clear()
            selectedBranch = null
            return@LaunchedEffect
        }
        // Per-repo last-model: if this repo has a remembered model and
        // it differs from the current selection, swap to it. Done here
        // (not just in the session-restore block) so manual repo
        // switches in mid-chat also pick up the right model.
        val rememberedModelKey = com.glassfiles.data.ai.AiAgentPrefs
            .getLastModel(context, repo.fullName)
        if (rememberedModelKey != null) {
            val match = models.firstOrNull { it.uniqueKey == rememberedModelKey }
            if (match != null && match != selectedModel) {
                selectedModel = match
            }
        }
        val previous = selectedBranch
        try {
            val list = GitHubManager.getBranches(context, repoOwner(repo), repoName(repo))
            branches.clear()
            branches.addAll(list)
            // Prefer keeping a branch the caller already had (e.g. one we
            // just restored from the session) when it is valid for the
            // newly-loaded repo. Otherwise fall back to the default branch.
            selectedBranch = previous?.takeIf { it in list }
                ?: repo.defaultBranch.takeIf { it.isNotBlank() && it in list }
                ?: list.firstOrNull()
        } catch (_: Exception) {
            // Network/permission failure — keep whatever was selected so
            // the input doesn't become stuck disabled.
        }
        approvals.clear()
    }

    // D2 — peek at the resume store every time the active session
    // changes. When the previous run ended cleanly, [getPending]
    // returns null and no banner is rendered.
    LaunchedEffect(activeSessionId) {
        pendingResume = com.glassfiles.data.ai.AiAgentResumeStore
            .getPending(context, activeSessionId)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────
    fun refreshSessions() {
        sessions = AiChatSessionStore.list(context, AGENT_SESSION_MODE)
    }

    fun activeAgentScopeFullName(): String {
        if (chatOnlyMode) return chatOnlyScopeFullName()
        selectedRepo?.fullName?.takeIf { it.isNotBlank() }?.let { return it }
        AiChatSessionStore
            .get(context, AGENT_SESSION_MODE, activeSessionId)
            ?.repoFullName
            ?.takeIf { it.isNotBlank() && it != CHAT_ONLY_REPO_KEY }
            ?.let { return it }
        return "$CHAT_MEMORY_SCOPE_PREFIX$activeSessionId"
    }

    fun agentScopeLabel(scopeFullName: String): String =
        if (scopeFullName.startsWith(CHAT_MEMORY_SCOPE_PREFIX)) "chat only" else scopeFullName

    fun chatOnlyScopeFullName(): String =
        "$CHAT_MEMORY_SCOPE_PREFIX$activeSessionId"

    fun chatOnlyMemoryPrompt(scopeFullName: String): List<AiMessage> {
        val messages = mutableListOf<AiMessage>()
        val workingMemoryBlock = if (com.glassfiles.data.ai.AiWorkingMemoryPrefs.getEnabled(context)) {
            com.glassfiles.data.ai.AiAgentMemoryStore.workingMemoryPrompt(context, scopeFullName)
        } else ""
        val memoryBlock = com.glassfiles.data.ai.AiAgentMemoryStore.buildMemoryPrompt(context, scopeFullName)
        val systemOverride = com.glassfiles.data.ai.AiAgentPrefs
            .getSystemPromptOverride(context, scopeFullName)
            ?.takeIf { it.isNotBlank() }
        val planFirst = com.glassfiles.data.ai.AiAgentPrefs
            .getPlanThenExecute(context, scopeFullName)
        if (workingMemoryBlock.isNotBlank()) {
            messages += AiMessage(role = "system", content = workingMemoryBlock)
        }
        if (memoryBlock.isNotBlank()) {
            messages += AiMessage(role = "system", content = memoryBlock)
        }
        if (systemOverride != null) {
            messages += AiMessage(role = "system", content = systemOverride)
        }
        if (planFirst) {
            messages += AiMessage(role = "system", content = AGENT_PLAN_FIRST_SYSTEM_PROMPT)
        }
        return messages
    }

    fun transcriptMessages(): List<AiChatSessionStore.Message> = transcript.mapNotNull { entry ->
        when (entry) {
            is AgentEntry.User -> AiChatSessionStore.Message(
                role = "user",
                content = entry.text,
                imageBase64 = entry.imageBase64,
                fileContent = entry.fileContent,
            )
            is AgentEntry.Assistant -> if (entry.text.isBlank() && entry.generatedFiles.isEmpty()) null else AiChatSessionStore.Message(
                role = "assistant",
                content = entry.text,
                generatedFiles = entry.generatedFiles,
            )
            is AgentEntry.ToolCall, is AgentEntry.ToolResult, is AgentEntry.Pending -> null
        }
    }

    fun persistSession() {
        val id = activeSessionId
        val messages = transcriptMessages()
        if (messages.isEmpty()) return
        AiChatSessionStore.upsert(
            context = context,
            session = AiChatSessionStore.Session(
                id = id,
                mode = AGENT_SESSION_MODE,
                title = AiChatSessionStore.deriveTitle(messages),
                providerId = selectedModel?.providerId?.name.orEmpty(),
                modelId = selectedModel?.id.orEmpty(),
                messages = messages,
                createdAt = sessionCreatedAt,
                updatedAt = System.currentTimeMillis(),
                repoFullName = if (chatOnlyMode) CHAT_ONLY_REPO_KEY else selectedRepo?.fullName.orEmpty(),
                branch = if (chatOnlyMode) "" else selectedBranch.orEmpty(),
            ),
        )
        refreshSessions()
    }

    fun openSession(session: AiChatSessionStore.Session) {
        stopActiveAgent(runJob, approvals)
        runJob = null
        running = false
        error = null
        input = TextFieldValue("")
        pendingImage = null
        pendingFile = null
        activeSessionId = session.id
        sessionCreatedAt = session.createdAt
        transcript.clear()
        todoItems.clear()
        transcript.addAll(session.messages.toAgentEntries())
        approvals.clear()
        // Reset selection so the restore effect picks up this session's
        // repo / branch / model. The branch is pre-seeded so the
        // branches-loading effect can honour it.
        selectedRepo = null
        chatOnlyMode = session.repoFullName == CHAT_ONLY_REPO_KEY
        selectedBranch = session.branch.takeIf { it.isNotBlank() }
        selectedModel = null
        pendingRestoreSessionId = session.id
        showHistory = false
    }

    fun startNewSession() {
        stopActiveAgent(runJob, approvals)
        runJob = null
        running = false
        error = null
        input = TextFieldValue("")
        pendingImage = null
        pendingFile = null
        val nextSessionId = newAgentSessionId()
        runCatching { LocalToolExecutor.ensureSessionWorkspace(context, nextSessionId) }
            .onFailure { error = "Unable to create agent sandbox: ${it.message ?: it.javaClass.simpleName}" }
        activeSessionId = nextSessionId
        sessionCreatedAt = System.currentTimeMillis()
        transcript.clear()
        todoItems.clear()
        approvals.clear()
        // Don't drop repo / branch / model — keep the user's working
        // context so they can fire off another task in the same repo
        // without re-picking everything.
        showHistory = false
    }

    fun enterChatOnlyMode() {
        if (!chatOnlyMode && transcript.isNotEmpty()) {
            startNewSession()
        }
        chatOnlyMode = true
        selectedRepo = null
        selectedBranch = null
        if (transcript.isEmpty()) todoItems.clear()
        pendingRestoreSessionId = null
        pendingWorkspaceId = null
        pendingWorkspaceDiff = null
        workspaceReviewError = null
    }

    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val mime = context.contentResolver.getType(uri).orEmpty()
                val name = uri.lastPathSegment.orEmpty()
                if (AiAttachmentProcessor.isImage(mime, name)) {
                    pendingImage = withContext(Dispatchers.IO) { encodeAgentImage(context, uri) }
                    pendingFile = null
                } else {
                    pendingFile = runCatching { AiAttachmentProcessor.prepare(context, uri) }
                        .getOrElse { e ->
                            AiPreparedAttachment(
                                name = name.ifBlank { "attachment" },
                                mimeType = mime,
                                extension = "",
                                tempPath = "",
                                isArchive = false,
                                promptContent = "[attachment error: ${e.message ?: e.javaClass.simpleName}]",
                                previewContent = null,
                                summary = "attachment error",
                            )
                        }
                    pendingImage = null
                }
            }
        }
    }

    val skillPackPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val imported = runCatching {
                    withContext(Dispatchers.IO) {
                        val preview = AiSkillStore.prepareImport(context, uri)
                        AiSkillStore.commitImport(context, preview)
                    }
                }
                imported
                    .onSuccess {
                        skillsVersion += 1
                        skillImportError = null
                        skillImportPreview = null
                        showSkills = true
                        Toast.makeText(context, "Imported: ${it.name}", Toast.LENGTH_SHORT).show()
                    }
                    .onFailure {
                        skillImportError = it.message ?: it.javaClass.simpleName
                        skillImportPreview = null
                        showSkills = true
                        Toast.makeText(context, "Import failed: $skillImportError", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    fun stop() {
        runJob?.cancel()
        runJob = null
        running = false
        approvals.forEach { it.deferred.complete(false) }
        approvals.clear()
    }

    fun resetPendingInput() {
        input = TextFieldValue("")
        pendingImage = null
        pendingFile = null
    }

    fun appendLocalCommandResult(commandText: String, response: String) {
        transcript += AgentEntry.User(text = commandText)
        transcript += AgentEntry.Assistant(text = response)
        resetPendingInput()
        persistSession()
    }

    fun openWorkingMemorySheet() {
        memorySheetRepoFullName = activeAgentScopeFullName()
        showSettings = false
        showWorkingMemory = true
    }

    fun openMemoryFilesSheet() {
        memorySheetRepoFullName = activeAgentScopeFullName()
        showSettings = false
        showMemoryFiles = true
    }

    fun openSystemPromptEditor() {
        systemPromptScopeFullName = activeAgentScopeFullName()
        showSettings = false
        showSystemPrompt = true
    }

    fun buildSlashHelp(): String = """
        [slash commands]
        /help            show this list
        /clear           clear current chat session
        /cost            show local token/cost estimate
        /memory          open working memory
        /memory files    open memory files
        /skills          open installed skills
        /permissions     show or set permission mode
        /plan [on|off]   toggle plan-first mode for this scope
        /system          open system prompt
        /compact         compact visible transcript locally
        /resume          open chat history
        /diff            open pending workspace diff
    """.trimIndent()

    fun buildCostSummary(): String {
        val rate = selectedModel?.let { com.glassfiles.data.ai.ModelPricing.rateFor(it) }
        val stats = computeSessionStats(transcript, rate)
        val cost = stats.costUsd?.let { value ->
            when {
                value < 0.01 -> "<\$0.01"
                value < 1.0 -> String.format(Locale.US, "\$%.3f", value)
                else -> String.format(Locale.US, "\$%.2f", value)
            }
        } ?: "\$0.00"
        val toolCalls = transcript.count { it is AgentEntry.ToolCall }
        return buildString {
            appendLine("[cost estimate]")
            appendLine("scope: ${agentScopeLabel(activeAgentScopeFullName())}")
            appendLine("model: ${selectedModel?.displayName ?: "not selected"}")
            appendLine("input chars: ${stats.inputChars}")
            appendLine("output chars: ${stats.outputChars}")
            appendLine("total chars: ${stats.totalChars}")
            appendLine("tokens: ~${stats.tokens}")
            appendLine("cost: ~$cost")
            appendLine("tool calls: $toolCalls")
        }.trimEnd()
    }

    fun buildCompactSummary(source: List<AgentEntry>): String {
        val userMessages = source.filterIsInstance<AgentEntry.User>()
            .map { it.text.trim() }
            .filter { it.isNotBlank() }
            .takeLast(6)
        val generatedFiles = source.filterIsInstance<AgentEntry.Assistant>()
            .flatMap { it.generatedFiles }
            .takeLast(8)
        val toolCalls = source.filterIsInstance<AgentEntry.ToolCall>()
        val todos = todoItems.toList()
        return buildString {
            appendLine("[compact summary]")
            appendLine("entries: ${source.size}")
            appendLine("tool calls: ${toolCalls.size}")
            if (todos.isNotEmpty()) {
                appendLine()
                appendLine("todos:")
                todos.forEach { item -> appendLine("- ${todoStatusMarker(item.status)} ${item.title}") }
            }
            if (generatedFiles.isNotEmpty()) {
                appendLine()
                appendLine("generated files:")
                generatedFiles.forEach { file -> appendLine("- ${file.name} (${file.content.length} chars)") }
            }
            if (userMessages.isNotEmpty()) {
                appendLine()
                appendLine("recent user requests:")
                userMessages.forEach { message ->
                    appendLine("- ${message.lineSequence().firstOrNull().orEmpty().take(160)}")
                }
            }
        }.trimEnd()
    }

    fun compactTranscriptLocally(commandText: String) {
        val source = transcript.toList()
        if (source.size <= 10) {
            appendLocalCommandResult(commandText, "[system] nothing to compact yet.")
            return
        }
        val summary = buildCompactSummary(source)
        val tail = source
            .filter { it is AgentEntry.User || it is AgentEntry.Assistant || it is AgentEntry.Pending }
            .takeLast(8)
        transcript.clear()
        transcript += AgentEntry.Assistant(text = summary)
        transcript.addAll(tail)
        resetPendingInput()
        persistSession()
    }

    fun handleSlashCommand(rawText: String): Boolean {
        if (!rawText.startsWith("/")) return false
        val body = rawText.drop(1).trim()
        val name = body.substringBefore(' ', missingDelimiterValue = body).lowercase(Locale.US)
        val args = body.substringAfter(' ', missingDelimiterValue = "").trim().lowercase(Locale.US)
        when (name) {
            "help", "?" -> appendLocalCommandResult(rawText, buildSlashHelp())
            "clear" -> {
                startNewSession()
                Toast.makeText(context, "Chat cleared", Toast.LENGTH_SHORT).show()
            }
            "cost" -> appendLocalCommandResult(rawText, buildCostSummary())
            "memory" -> {
                resetPendingInput()
                if (args == "files" || args == "file") openMemoryFilesSheet() else openWorkingMemorySheet()
            }
            "skills" -> {
                resetPendingInput()
                showSettings = false
                showSkills = true
            }
            "permissions", "permission", "perms" -> {
                val mode = when (args.replace("_", "-")) {
                    "ask", "manual" -> com.glassfiles.data.ai.AiAgentPermissionMode.ASK
                    "read", "reads", "auto-read", "auto-reads" -> com.glassfiles.data.ai.AiAgentPermissionMode.AUTO_READS
                    "edit", "edits", "accept", "accept-edit", "accept-edits" ->
                        com.glassfiles.data.ai.AiAgentPermissionMode.ACCEPT_EDITS
                    "yolo", "bypass" -> com.glassfiles.data.ai.AiAgentPermissionMode.YOLO
                    "" -> null
                    else -> {
                        appendLocalCommandResult(
                            rawText,
                            "[system] unknown permission mode: $args\nknown: ask, reads, accept-edits, yolo",
                        )
                        return true
                    }
                }
                if (mode != null) {
                    com.glassfiles.data.ai.AiAgentApprovalPrefs.applyPermissionMode(context, mode)
                    autoApproveReads = com.glassfiles.data.ai.AiAgentApprovalPrefs.getAutoApproveReads(context)
                    approvalPrefsVersion += 1
                }
                val current = com.glassfiles.data.ai.AiAgentApprovalPrefs.getPermissionMode(context)
                appendLocalCommandResult(
                    rawText,
                    "[system] permission mode: ${current.label}\n${current.description}",
                )
            }
            "plan" -> {
                val scopeFullName = activeAgentScopeFullName()
                val enabled = args !in setOf("off", "false", "0", "disable", "disabled")
                com.glassfiles.data.ai.AiAgentPrefs.setPlanThenExecute(context, scopeFullName, enabled)
                appendLocalCommandResult(
                    rawText,
                    "[system] plan-first mode ${if (enabled) "enabled" else "disabled"} for ${agentScopeLabel(scopeFullName)}.",
                )
            }
            "system", "prompt" -> {
                resetPendingInput()
                openSystemPromptEditor()
            }
            "compact" -> {
                if (running) {
                    appendLocalCommandResult(rawText, "[system] wait for the current agent run to finish before compacting.")
                } else {
                    compactTranscriptLocally(rawText)
                }
            }
            "resume", "history" -> {
                resetPendingInput()
                showSettings = false
                refreshSessions()
                showHistory = true
            }
            "diff" -> {
                resetPendingInput()
                if (pendingWorkspaceDiff != null) {
                    showWorkspaceReview = true
                } else {
                    appendLocalCommandResult(rawText, "[system] no pending workspace diff.")
                }
            }
            else -> appendLocalCommandResult(rawText, "[system] unknown slash command: /$name\n\n${buildSlashHelp()}")
        }
        return true
    }

    fun runChatOnlyInternal(text: String, image: String?, file: AiPreparedAttachment?, model: AiModel, key: String) {
        error = null
        fallbackNotice = null
        input = TextFieldValue("")
        pendingImage = null
        pendingFile = null
        transcript += AgentEntry.User(text, image, file?.promptContent)
        transcript += AgentEntry.Assistant(text = "")
        persistSession()
        running = true
        runJob = scope.launch {
            try {
                val provider = AiProviders.get(model.providerId)
                val chatScope = chatOnlyScopeFullName()
                val localExecutor = LocalToolExecutor(
                    sessionId = activeSessionId,
                    currentAttachment = file,
                    allowExternalPaths = false,
                )
                val selectedSkill = AiSkillPrefs.getSelectedSkillId(context)
                    ?.let { AiSkillStore.readSkill(context, it) }
                    ?.takeIf { it.enabled && AiSkillPrefs.getEnableSkills(context) }
                val skillMatch = if (selectedSkill == null) {
                    AiSkillRouter().match(
                        context = context,
                        userMessage = text,
                        appContext = AppAgentContext(repoFullName = null, chatOnly = true),
                    )
                } else null
                val activeSkill = selectedSkill ?: skillMatch?.skill
                val skillAllowedTools = activeSkill?.let { AiSkillStore.allowedToolsForSkill(context, it) }
                val taskToolNames = AgentTools.TASK_TOOLS.map { it.name }.toSet()
                val messages = mutableListOf<AiMessage>().apply {
                    addAll(chatOnlyMemoryPrompt(chatScope))
                    add(AiMessage("system", CHAT_ONLY_SYSTEM_PROMPT))
                    add(AiMessage("system", AGENT_TODO_SYSTEM_PROMPT))
                    AiSkillStore.catalogPrompt(context).takeIf { it.isNotBlank() }?.let {
                        add(AiMessage("system", it))
                    }
                    if (activeSkill != null && skillAllowedTools != null) {
                        add(AiMessage("system", AiSkillStore.promptFor(activeSkill, skillAllowedTools)))
                    }
                    addAll(transcript.dropLast(1).toAiMessages())
                }
                val chatTools = if (skillAllowedTools != null) {
                    AgentTools.CHAT_TOOLS.filter { it.name in skillAllowedTools || it.name in taskToolNames }
                } else {
                    AgentTools.CHAT_TOOLS
                }
                var turnIndex = 0
                while (turnIndex < 8) {
                    if (transcript.lastOrNull() !is AgentEntry.Assistant) {
                        transcript += AgentEntry.Assistant(text = "")
                    }
                    val assistantIndex = transcript.lastIndex
                    val turn = provider.chatWithToolsStreaming(
                        context = context,
                        modelId = model.id,
                        messages = messages,
                        tools = chatTools,
                        apiKey = key,
                        onTextDelta = { chunk ->
                            val current = transcript.getOrNull(assistantIndex)
                            if (current is AgentEntry.Assistant) {
                                transcript[assistantIndex] = current.copy(text = current.text + chunk)
                            }
                        },
                    )
                    val assistant = transcript.getOrNull(assistantIndex) as? AgentEntry.Assistant
                    val assistantText = assistant?.text?.ifBlank { turn.assistantText } ?: turn.assistantText
                    val parsedFiles = extractAgentGeneratedFiles(assistantText)
                    val displayText = stripAgentGeneratedFileBlocks(assistantText)
                    if (assistant != null) {
                        transcript[assistantIndex] = assistant.copy(
                            text = displayText,
                            generatedFiles = mergeGeneratedFiles(assistant.generatedFiles, parsedFiles),
                        )
                    }
                    com.glassfiles.data.ai.usage.AiUsageAccounting.appendReportedOrEstimated(
                        context = context,
                        providerId = model.providerId.name,
                        modelId = model.id,
                        mode = com.glassfiles.data.ai.usage.AiUsageMode.CHAT,
                        messages = messages,
                        output = turn.assistantText,
                        reported = turn.usage,
                    )
                    if (turn.toolCalls.isEmpty()) break

                    messages += AiMessage(
                        role = "assistant",
                        content = displayText,
                        toolCalls = turn.toolCalls,
                    )
                    for (call in turn.toolCalls) {
                        transcript += AgentEntry.ToolCall(call)
                        val existingFiles = currentChatArtifacts(transcript)
                        val execution = if (call.isTodoTool()) {
                            ChatArtifactExecution(result = executeTodoTool(call, todoItems))
                        } else if (skillAllowedTools != null && call.name !in skillAllowedTools) {
                            ChatArtifactExecution(
                                result = AiToolResult(
                                    callId = call.id,
                                    name = call.name,
                                    output = "deny: tool not allowed by active skill (${activeSkill?.id})",
                                    isError = true,
                                ),
                            )
                        } else {
                            executeChatOnlyTool(context, call, existingFiles, localExecutor, activeSessionId)
                        }
                        execution.generatedFile?.let { fileOut ->
                            val current = transcript.getOrNull(assistantIndex) as? AgentEntry.Assistant
                            if (current != null) {
                                val visibleFiles = removeGeneratedFilesForArchive(
                                    current.generatedFiles,
                                    execution.replacedGeneratedPaths,
                                    fileOut.name,
                                )
                                transcript[assistantIndex] = current.copy(
                                    generatedFiles = mergeGeneratedFiles(visibleFiles, listOf(fileOut)),
                                )
                            }
                        }
                        transcript += AgentEntry.ToolResult(execution.result)
                        messages += AiMessage(
                            role = "tool",
                            content = execution.result.output,
                            toolCallId = execution.result.callId,
                            toolName = execution.result.name,
                        )
                    }
                    if (turnIndex < 7) {
                        transcript += AgentEntry.Assistant(text = "")
                    }
                    turnIndex += 1
                }
            } catch (e: Exception) {
                val last = transcript.lastIndex
                val message = "${e.javaClass.simpleName}: ${e.message ?: ""}"
                if (last >= 0 && transcript[last] is AgentEntry.Assistant) {
                    transcript[last] = (transcript[last] as AgentEntry.Assistant).copy(text = message)
                } else {
                    transcript += AgentEntry.Assistant(message)
                }
                error = e.message ?: e.javaClass.simpleName
            } finally {
                persistSession()
                running = false
                runJob = null
            }
        }
    }

    /**
     * Internal: actually start the agent loop. Assumes input has been
     * sanitised and (if applicable) the warning dialog has been
     * resolved by the user. Always called from `submit` directly or
     * from the warning dialog's "Continue" button.
     */
    fun runTaskInternal(text: String, image: String?, file: AiPreparedAttachment?) {
        val model = selectedModel ?: return
        val key = AiKeyStore.getKey(context, model.providerId)
        if (key.isBlank()) {
            error = Strings.aiAgentNoModels
            return
        }
        val displayText = attachmentDisplayText(text, file)
        if (chatOnlyMode) {
            runChatOnlyInternal(displayText, image, file, model, key)
            return
        }
        val repo = selectedRepo ?: return
        val branch = selectedBranch ?: return
        error = null
        fallbackNotice = null
        input = TextFieldValue("")
        pendingImage = null
        pendingFile = null
        transcript += AgentEntry.User(displayText, image, file?.promptContent)
        persistSession()
        // D2: persist a "task started, not finished" pointer so a
        // process kill mid-run leaves a recoverable trail. Cleared in
        // the finally block when the run completes (success, error,
        // or user stop).
        com.glassfiles.data.ai.AiAgentResumeStore.markStarted(
            context,
            activeSessionId,
            com.glassfiles.data.ai.AiAgentResumeStore.Pending(
                prompt = text,
                imageBase64 = image,
                repoFullName = repo.fullName,
                branch = branch,
                modelKey = model.uniqueKey,
                startedAt = System.currentTimeMillis(),
            ),
        )
        running = true

        runJob = scope.launch {
            // Cost-policy estimate for this task. Limits come from the
            // user's selected mode (Eco / Balanced / MaxQuality). The
            // executor uses it to cap individual file sizes / log lines /
            // diff sizes; the agent loop uses it to cap total iterations
            // and total context chars.
            val costMode = com.glassfiles.data.ai.cost.AiCostModeStore.getMode(context)
            val limits = com.glassfiles.data.ai.cost.AiCostPolicy.limitsFor(costMode)
            val estimate = com.glassfiles.data.ai.cost.AiContextEstimate(limits)
            // Warm the executor's file cache with anything we already
            // have on disk for this (repo, branch). Across-session reuse
            // turns the second "open this repo and ask about File.kt"
            // into a no-op for files that were already touched
            // recently. Stale entries fall off after 24h.
            val warmCache = com.glassfiles.data.ai.agent.ReadFileDiskCache
                .load(context, repo.fullName, branch)
            val workspaceDb = if (workspaceMode && repo.canWrite()) {
                com.glassfiles.data.ai.workspace.WorkspaceDatabase(context)
            } else null
            val workspaceRecord = workspaceDb?.createWorkspace(
                taskId = "${activeSessionId}_${System.currentTimeMillis()}",
                chatId = activeSessionId,
                repoFullName = repo.fullName,
                baseCommitSha = GitHubManager
                    .getBranchHeadSha(context, repoOwner(repo), repoName(repo), branch)
                    ?: branch,
                title = text.lineSequence().firstOrNull()
                    ?.take(64)
                    ?.ifBlank { null }
                    ?: "AI agent workspace",
            )
            val workspaceVfs = if (workspaceRecord != null && workspaceDb != null) {
                com.glassfiles.data.ai.workspace.WorkspaceFileSystem(
                    workspaceId = workspaceRecord.id,
                    realFs = com.glassfiles.data.ai.workspace.GitHubRepositoryFileSystem(
                        context = context,
                        owner = repoOwner(repo),
                        repo = repoName(repo),
                        branch = branch,
                    ),
                    db = workspaceDb,
                    committer = com.glassfiles.data.ai.workspace.GitHubWorkspaceCommitter(
                        context = context,
                        owner = repoOwner(repo),
                        repo = repoName(repo),
                        branch = branch,
                    ),
                )
            } else null
            val executor = GitHubToolExecutor(
                owner = repoOwner(repo),
                repo = repoName(repo),
                branch = branch,
                estimate = estimate,
                virtualFileSystem = workspaceVfs,
                initialCache = warmCache,
                localToolExecutor = LocalToolExecutor(
                    sessionId = activeSessionId,
                    repoFullName = repo.fullName,
                    branch = branch,
                    currentAttachment = file,
                    allowExternalPaths = true,
                ),
            )
            val provider = AiProviders.get(model.providerId)
            // Filter destructive tools out of the schema sent to the model
            // when the active repo is read-only — without it, the model
            // would happily emit `write_file` / `commit` calls that just
            // 403 in the executor and waste a turn.
            val tools = if (repo.canWrite()) {
                AgentTools.ALL
            } else {
                AgentTools.ALL.filter { AgentToolRegistry.isReadOnly(it.name) }
            }
            val selectedSkill = AiSkillPrefs.getSelectedSkillId(context)
                ?.let { AiSkillStore.readSkill(context, it) }
                ?.takeIf { it.enabled && AiSkillPrefs.getEnableSkills(context) }
            val skillMatch = if (selectedSkill == null) {
                AiSkillRouter().match(
                    context = context,
                    userMessage = displayText,
                    appContext = AppAgentContext(repoFullName = repo.fullName, chatOnly = false),
                )
            } else null
            val activeSkill = selectedSkill ?: skillMatch?.skill
            val skillAllowedTools = activeSkill?.let { AiSkillStore.allowedToolsForSkill(context, it) }
            val taskToolNames = AgentTools.TASK_TOOLS.map { it.name }.toSet()
            val effectiveTools = if (skillAllowedTools != null) {
                tools.filter { it.name in skillAllowedTools || it.name in taskToolNames }
            } else {
                tools
            }
            // Build the ordered fallback list: every other tool-use
            // capable model the user has a key for, ranked by the same
            // heuristic that picked `selectedModel` originally. We
            // deliberately exclude models without tools support since
            // the agent loop cannot survive on a non-tool model.
            val fallbacks = buildFallbackCandidates(
                context = context,
                allModels = models,
                exclude = model,
            )
            // Per-repo system-prompt override. When set, prepended as a
            // `system` role message ahead of the transcript so the
            // model picks up the user's house rules. Empty / blank
            // overrides are ignored — the agent works fine without one.
            val systemOverride = com.glassfiles.data.ai.AiAgentPrefs
                .getSystemPromptOverride(context, repo.fullName)
                ?.takeIf { it.isNotBlank() }
            // C3 — plan-then-execute. When the per-repo toggle is on,
            // prepend a planning preamble ahead of the user's prompt
            // so the very first model turn outputs only a numbered
            // plan (no tool calls). Tools resume on the next user turn,
            // i.e. when the user types "go", clicks "Approve plan", or
            // simply continues the conversation.
            val planFirst = com.glassfiles.data.ai.AiAgentPrefs
                .getPlanThenExecute(context, repo.fullName)
            val baseMessages = transcript.toAiMessages()
            // Working memory (BUGS_FIX.md Section 3). When the user has the
            // toggle on AND working_memory.md is non-empty for this repo we
            // prepend its contents as a system message — BEFORE the plan-
            // first / system-override messages — so the agent sees its own
            // task plan / progress / decisions before it picks the next
            // tool call. Capped at ~2 KB inside [workingMemoryPrompt] so a
            // long working memory file can never blow the prompt budget on
            // its own.
            val workingMemoryEnabled = com.glassfiles.data.ai.AiWorkingMemoryPrefs.getEnabled(context)
            val workingMemoryBlock = if (workingMemoryEnabled) {
                com.glassfiles.data.ai.AiAgentMemoryStore.workingMemoryPrompt(context, repo.fullName)
            } else ""
            val systemMessages = buildList {
                add(AiMessage(role = "system", content = AGENT_TODO_SYSTEM_PROMPT))
                if (workingMemoryBlock.isNotBlank()) {
                    add(AiMessage(role = "system", content = workingMemoryBlock))
                }
                if (systemOverride != null) {
                    add(AiMessage(role = "system", content = systemOverride))
                }
                AiSkillStore.catalogPrompt(context).takeIf { it.isNotBlank() }?.let {
                    add(AiMessage(role = "system", content = it))
                }
                if (activeSkill != null && skillAllowedTools != null) {
                    add(AiMessage(role = "system", content = AiSkillStore.promptFor(activeSkill, skillAllowedTools)))
                }
                if (workspaceRecord != null) {
                    add(
                        AiMessage(
                            role = "system",
                            content = "Workspace mode is active. Repo file tools stage changes in workspace ${workspaceRecord.id}; they do not create GitHub commits. Finish the task normally so the app can move the workspace to pending review.",
                        ),
                    )
                }
                if (planFirst) {
                    add(
                        AiMessage(
                            role = "system",
                            content = AGENT_PLAN_FIRST_SYSTEM_PROMPT,
                        ),
                    )
                }
            }
            val seed = systemMessages + baseMessages
            // Snapshot every approval-related preference at the moment the task
            // starts, so the loop's behaviour matches what the user saw in the
            // settings sheet — even if a prefs read on a slow disk lagged the
            // first turn. The loop itself does NOT re-read prefs mid-run.
            val approvalSettings = com.glassfiles.data.ai.agent.AiAgentApprovalSettings(
                autoApproveReads = com.glassfiles.data.ai.AiAgentApprovalPrefs.getAutoApproveReads(context),
                autoApproveEdits = com.glassfiles.data.ai.AiAgentApprovalPrefs.getAutoApproveEdits(context),
                autoApproveWrites = com.glassfiles.data.ai.AiAgentApprovalPrefs.getAutoApproveWrites(context),
                autoApproveCommits = com.glassfiles.data.ai.AiAgentApprovalPrefs.getAutoApproveCommits(context),
                autoApproveDestructive = com.glassfiles.data.ai.AiAgentApprovalPrefs.getAutoApproveDestructive(context),
                yoloMode = com.glassfiles.data.ai.AiAgentApprovalPrefs.getYoloMode(context),
                sessionTrust = com.glassfiles.data.ai.AiAgentApprovalPrefs.getSessionTrust(context),
                writeLimitPerTask = com.glassfiles.data.ai.AiAgentApprovalPrefs.getWriteLimit(context),
                protectedPaths = com.glassfiles.data.ai.AiAgentApprovalPrefs.getProtectedPaths(context),
                activeBranch = branch,
            )
            // Surface the resolved write-limit in the transcript so power-users
            // running long tasks can verify the ∞ / 20 / 50 / 100 toggle landed
            // where they expect. Hidden behind a "[debug] " prefix so casual
            // users can ignore it.
            transcript += AgentEntry.Assistant(
                text = "[debug] write limit: " + writeLimitLabel(approvalSettings.writeLimitPerTask) +
                    " · yolo=${approvalSettings.yoloMode}" +
                    " · workspace=${workspaceRecord?.id ?: "off"}",
            )
            var taskCompleted = false
            try {
                runAgentLoop(
                    seedMessages = seed,
                    initialProvider = provider,
                    initialModelId = model.id,
                    initialApiKey = key,
                    tools = effectiveTools,
                    executor = executor,
                    localSessionId = activeSessionId,
                    transcript = transcript,
                    todoItems = todoItems,
                    approvals = approvals,
                    approvalSettings = approvalSettings,
                    activeSkill = activeSkill,
                    allowedSkillTools = skillAllowedTools,
                    context = context,
                    fallbackCandidates = fallbacks,
                    estimate = estimate,
                    workingMemoryRepo = if (workingMemoryEnabled) repo.fullName else "",
                    workingMemoryReminders = workingMemoryEnabled &&
                        com.glassfiles.data.ai.AiWorkingMemoryPrefs.getReminders(context),
                    onFallback = { newModel ->
                        // Reflect the swap in the UI so the user
                        // immediately sees which model is now in
                        // charge — and so cost / token meter switches
                        // to the new pricing rate.
                        selectedModel = newModel
                        fallbackNotice = Strings.aiAgentFallbackToast
                            .replace("{model}", newModel.displayName)
                    },
                )
                taskCompleted = true
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                if (taskCompleted && workspaceDb != null && workspaceRecord != null && workspaceVfs != null) {
                    runCatching {
                        val diff = workspaceVfs.diff()
                        if (diff.changes.isEmpty()) {
                            workspaceVfs.discard()
                        } else {
                            workspaceDb.markPendingReview(workspaceRecord.id)
                            pendingWorkspaceId = workspaceRecord.id
                            pendingWorkspaceDiff = diff
                            workspaceCommitMessage = generateWorkspaceCommitMessage(diff)
                            workspaceReviewError = null
                            transcript += AgentEntry.Assistant(
                                text = "[workspace] pending review: ${diff.filesChanged} file(s), +${diff.additions}/-${diff.deletions}. Review the diff before commit.",
                            )
                        }
                    }
                }
                persistSession()
                running = false
                runJob = null
                // Persist the executor's file cache so the next session
                // on this (repo, branch) starts warm. Failures are
                // logged inside the store and do not affect the agent
                // outcome.
                runCatching {
                    com.glassfiles.data.ai.agent.ReadFileDiskCache.save(
                        context,
                        repo.fullName,
                        branch,
                        executor.snapshotCache(),
                    )
                }
                // D2: clear the resume pointer — the run is over, so
                // there's nothing to recover from a future process kill.
                com.glassfiles.data.ai.AiAgentResumeStore.markFinished(
                    context, activeSessionId,
                )
                // Local-only usage record. We never store prompt /
                // file contents — only counters and labels. See
                // AiUsageRecord kdoc for the privacy contract.
                runCatching {
                    val toolCalls = transcript.filterIsInstance<AgentEntry.ToolCall>()
                    val readCalls = toolCalls.count {
                        AgentToolRegistry.approvalCategoryFor(
                            it.call.name,
                            AgentTools.byName(it.call.name),
                        ) == AiAgentApprovalCategory.READ
                    }
                    val writeCalls = toolCalls.size - readCalls
                    com.glassfiles.data.ai.usage.AiUsageStore.append(
                        context,
                        com.glassfiles.data.ai.usage.AiUsageRecord(
                            providerId = model.providerId.name,
                            modelId = model.id,
                            mode = com.glassfiles.data.ai.usage.AiUsageMode.GITHUB_AGENT,
                            estimatedInputChars = estimate.totalChars,
                            estimatedOutputChars = 0,
                            toolCallsCount = estimate.toolCallsExecuted,
                            filesReadCount = readCalls,
                            filesWrittenCount = writeCalls,
                            writeProposalsCount = estimate.writeProposals,
                            repoName = repo.fullName,
                            branchName = branch,
                            isPrivateRepo = repo.isPrivate,
                            costMode = costMode.name,
                            estimated = true,
                        ),
                    )
                }
            }
        }
    }

    /**
     * Public submit. Sanitises input, then either runs the task
     * directly or stages a warning dialog (private repo / MaxQuality /
     * very large seed conversation).
     *
     * The "remember this for {repo, provider}" flag is persisted in
     * AiCostModeStore — if the user has previously dismissed for this
     * pair the warning is skipped silently.
     */
    fun submit(userText: String, imageBase64: String?, file: AiPreparedAttachment?) {
        val text = userText.trim()
        if (text.startsWith("/") && imageBase64 == null && file == null) {
            if (handleSlashCommand(text)) return
        }
        val image = imageBase64?.takeIf { selectedModel?.let { m -> AiCapability.VISION in m.capabilities } == true }
        if (text.isEmpty() && image == null && file == null) return
        val model = selectedModel ?: return
        val repo = selectedRepo
        val branch = selectedBranch
        if (!chatOnlyMode && (repo == null || branch == null)) return
        // Manual working-memory chat commands (BUGS_FIX.md Section 3
        // "Manual control"). We handle them here, BEFORE the message
        // hits the model, so they never burn tokens. The user sees
        // their own line in the transcript followed by the system's
        // response, exactly like a normal turn.
        val command = text.lowercase().trim().trim('.')
        when (command) {
            "clear working memory" -> {
                transcript += AgentEntry.User(text = userText, imageBase64 = image)
                com.glassfiles.data.ai.AiAgentMemoryStore.clearWorkingMemory(context, activeAgentScopeFullName())
                transcript += AgentEntry.Assistant(text = "[system] working_memory.md cleared.")
                input = TextFieldValue("")
                pendingImage = null
                pendingFile = null
                return
            }
            "show working memory", "what are you working on", "what are you working on?" -> {
                transcript += AgentEntry.User(text = userText, imageBase64 = image)
                val blob = com.glassfiles.data.ai.AiAgentMemoryStore
                    .readWorkingMemory(context, activeAgentScopeFullName())
                    .ifBlank { "[system] working_memory.md is empty." }
                transcript += AgentEntry.Assistant(text = blob)
                input = TextFieldValue("")
                pendingImage = null
                pendingFile = null
                return
            }
        }
        // Approximate context size = current transcript char count +
        // pending user text. This is what gets shipped to the provider
        // on the very next call, so it's the most honest number we
        // can show to the user without a real token-counter.
        val approxContext: Int = text.length + (file?.promptContent?.length ?: 0) + transcript.sumOf { entry ->
            when (entry) {
                is AgentEntry.User -> entry.text.length + (entry.fileContent?.length ?: 0)
                is AgentEntry.Assistant -> entry.text.length
                is AgentEntry.ToolCall -> entry.call.argsJson.length
                is AgentEntry.ToolResult -> entry.result.output.length
                is AgentEntry.Pending -> 0
            }
        }
        val limits = com.glassfiles.data.ai.cost.AiCostPolicy.limitsFor(costMode)
        val warningScope = repo?.fullName ?: CHAT_ONLY_REPO_KEY
        val remembered = com.glassfiles.data.ai.cost.AiCostModeStore.isRemembered(
            context, warningScope, model.providerId.name,
        )
        val warningReason: com.glassfiles.ui.screens.ai.ExpensiveActionReason? = when {
            // Private repo + no remembered exception → always warn.
            // The flag is per (repo, provider) so a "remember" for the
            // current provider doesn't leak to a different one.
            repo?.isPrivate == true && !remembered ->
                com.glassfiles.ui.screens.ai.ExpensiveActionReason.PrivateRepo
            // MaxQuality is opt-in expensive — surface that.
            costMode == com.glassfiles.data.ai.cost.AiCostMode.MaxQuality && !remembered ->
                com.glassfiles.ui.screens.ai.ExpensiveActionReason.MaxQualityMode
            // Heuristic: existing context is past half the cost-mode
            // budget. After the model's reply the next turn would cross
            // the cap, so warn now while it's still reversible.
            approxContext > (limits.maxTotalContextChars / 2) && !remembered ->
                com.glassfiles.ui.screens.ai.ExpensiveActionReason.LargeContext
            else -> null
        }
        if (warningReason != null) {
            pendingWarning = com.glassfiles.ui.screens.ai.ExpensiveActionWarning(
                repoFullName = repo?.fullName ?: "chat",
                branch = branch ?: "",
                providerLabel = model.providerId.displayName,
                modelLabel = model.displayName,
                approxFiles = transcript.count { it is AgentEntry.ToolResult },
                approxContextChars = approxContext,
                isPrivate = repo?.isPrivate == true,
                reason = warningReason,
            )
            pendingWarningInput = PendingAgentSend(text, image, file)
            return
        }
        runTaskInternal(text, image, file)
    }

    // ─── UI (terminal mode) ───────────────────────────────────────────────
    // The whole agent screen lives inside an [AgentTerminalSurface] so
    // descendants (AgentTopBar, AgentSettingsBottomSheet, AgentInput,
    // banners…) read their palette/typography from the terminal theme.
    // Repo / branch / model pickers, the
    // cost-mode selector and the auto-approve switch live in the
    // bottom-sheet now (opened via the gear icon in the topbar) — the
    // chat takes the full screen height and the topbar stays a single
    // monospace row so cost / `[auto: …]` / actions are scannable at a
    // glance.
    com.glassfiles.ui.screens.ai.terminal.AgentTerminalSurface(
        colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminalDarkColors,
    ) {
    // Re-bind `colors` to the terminal palette inside the surface so
    // every legacy `colors.surface` / `colors.accent` / etc. reference
    // resolves to terminal HEX colors. This must
    // live INSIDE the surface composition because [AgentTerminal.colors]
    // reads from a CompositionLocal that is only provided here.
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .let { if (embedded) it else it.statusBarsPadding() }
            .imePadding(),
    ) {
        // Topbar — terminal-style. Cost / token meter is computed from
        // the transcript, prefixed with `~` because the figures come
        // from a chars-based estimator (no provider-reported usage),
        // exactly like the AI Module chips elsewhere. The
        // `[auto: yolo]` / `[auto: reads]` indicator is computed from
        // the same DataStore-backed prefs the loop snapshots before
        // every run, so it can never drift from actual loop behaviour.
        val costRate = remember(selectedModel?.uniqueKey) {
            selectedModel?.let { com.glassfiles.data.ai.ModelPricing.rateFor(it) }
        }
        val sessionStats by remember(selectedModel?.uniqueKey) {
            derivedStateOf { computeSessionStats(transcript, costRate) }
        }
        val costLabel = if (sessionStats.totalChars > 0) {
            "~" + (sessionStats.costUsd?.let { c ->
                when {
                    c < 0.01 -> "<\$0.01"
                    c < 1.0 -> String.format(Locale.US, "\$%.3f", c)
                    else -> String.format(Locale.US, "\$%.2f", c)
                }
            } ?: "\$0.00")
        } else null
        val tokenLabel = if (sessionStats.totalChars > 0) {
            val t = sessionStats.tokens
            "~" + when {
                t >= 1000 -> String.format(Locale.US, "%.1fk tok", t / 1000.0)
                else -> "$t tok"
            }
        } else null
        val subtitle = listOfNotNull(
            if (chatOnlyMode) "chat" else selectedRepo?.fullName,
            if (chatOnlyMode) null else selectedBranch?.takeIf { it.isNotBlank() }?.let { "@$it" },
        ).joinToString("").ifBlank { null }
        val approvalIndicator = remember(autoApproveReads, approvalPrefsVersion, workspaceMode, selectedRepo?.fullName) {
            val yolo = com.glassfiles.data.ai.AiAgentApprovalPrefs.getYoloMode(context)
            val edits = com.glassfiles.data.ai.AiAgentApprovalPrefs.getAutoApproveEdits(context)
            val writes = com.glassfiles.data.ai.AiAgentApprovalPrefs.getAutoApproveWrites(context)
            val commits = com.glassfiles.data.ai.AiAgentApprovalPrefs.getAutoApproveCommits(context)
            when {
                workspaceMode -> "workspace" to com.glassfiles.ui.screens.ai.terminal.AgentAutoApproveTone.NEUTRAL
                yolo -> "auto: yolo" to com.glassfiles.ui.screens.ai.terminal.AgentAutoApproveTone.ERROR
                commits || writes -> "auto: writes" to com.glassfiles.ui.screens.ai.terminal.AgentAutoApproveTone.WARNING
                edits -> "auto: edits" to com.glassfiles.ui.screens.ai.terminal.AgentAutoApproveTone.WARNING
                autoApproveReads -> "auto: reads" to com.glassfiles.ui.screens.ai.terminal.AgentAutoApproveTone.NEUTRAL
                else -> null to com.glassfiles.ui.screens.ai.terminal.AgentAutoApproveTone.NEUTRAL
            }
        }
        // BUGS_FIX.md Section 3 — `▸ N files` indicator. Recomputed every
        // recomposition by reading working_memory.md from disk; not great
        // if the file was huge but we cap reads in
        // [workingMemoryActiveFileCount]. We intentionally re-read instead
        // of caching because the agent updates the file via memory_write
        // during the run and caching would lag the indicator.
        val workingFiles = remember(running, transcript.size, selectedRepo?.fullName) {
            val repoFull = selectedRepo?.fullName.orEmpty()
            if (repoFull.isBlank() ||
                !com.glassfiles.data.ai.AiWorkingMemoryPrefs.getEnabled(context)
            ) 0 else com.glassfiles.data.ai.AiAgentMemoryStore.workingMemoryActiveFileCount(
                context, repoFull,
            )
        }
        val todoProgress = if (todoItems.isNotEmpty()) {
            "todo ${todoProgressLabel(todoItems)}"
        } else null
        com.glassfiles.ui.screens.ai.terminal.AgentTopBar(
            title = Strings.aiAgent,
            subtitle = subtitle,
            cost = costLabel,
            tokens = tokenLabel,
            autoApproveIndicator = approvalIndicator.first,
            autoApproveTone = approvalIndicator.second,
            todoProgress = todoProgress,
            workingFiles = workingFiles.takeIf { it > 0 },
            embedded = embedded,
            running = running,
            onBack = onBack,
            onSettings = { showSettings = true },
            onStop = ::stop,
            onClose = onClose,
        )

        // Transcript — repo / branch / model / mode / approval toggles
        // / write limit / protected paths / memory / etc. all live in the
        // settings bottom-sheet now (opened via the topbar gear icon),
        // so the chat takes the full screen height.
        val listState = rememberLazyListState()
        val entries by remember { derivedStateOf { transcript.toList() + approvals.map { AgentEntry.Pending(it) } } }
        val displayEntries by remember { derivedStateOf { entries.toDisplayEntries() } }
        val toolActionCount by remember { derivedStateOf { displayEntries.count { it.isToolAction } } }
        LaunchedEffect(displayEntries.size) {
            if (displayEntries.isNotEmpty()) listState.animateScrollToItem(displayEntries.size - 1)
        }

        if (!chatOnlyMode && selectedRepo == null && repos.isEmpty() && !GitHubManager.isLoggedIn(context)) {
            EmptyAgentState(message = Strings.aiAgentNoRepos)
        } else if (models.isEmpty()) {
            EmptyAgentState(message = Strings.aiAgentNoModels)
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Banners — only render when the corresponding condition
                // is live. Each is its own `item` so they can collapse/
                // appear independently without disturbing scroll position.
                val repo = selectedRepo
                if (repo != null && repo.isPrivate && privateRepoDismissed != repo.fullName) {
                    item("banner-private") {
                        AgentBanner(
                            icon = Icons.Rounded.Lock,
                            tint = colors.warning,
                            text = Strings.aiAgentPrivateRepoWarning,
                            actionLabel = Strings.aiAgentPrivateRepoDismiss,
                            onAction = { privateRepoDismissed = repo.fullName },
                        )
                    }
                }
                if (repo != null && !repo.canWrite()) {
                    item("banner-readonly") {
                        AgentBanner(
                            icon = Icons.Rounded.Warning,
                            tint = colors.error,
                            text = Strings.aiAgentReadOnlyWarning,
                        )
                    }
                }
                if (selectedModel != null && activeApiKey.isBlank()) {
                    item("banner-no-key") {
                        AgentBanner(
                            icon = Icons.Rounded.Warning,
                            tint = colors.error,
                            title = Strings.aiAgentNoApiKeyTitle,
                            text = Strings.aiAgentNoApiKeySubtitle,
                        )
                    }
                }
                if (!chatOnlyMode && (selectedRepo == null || selectedBranch.isNullOrBlank())) {
                    item("banner-pick-repo") {
                        AgentBanner(
                            icon = Icons.Rounded.Build,
                            tint = colors.textSecondary,
                            text = Strings.aiAgentPickRepoHint,
                        )
                    }
                }
                if (entries.isEmpty()) {
                    item {
                        Text(
                            Strings.aiAgentEmptyChat,
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        )
                    }
                }
                if (todoItems.isNotEmpty()) {
                    item("agent-todo-checklist") {
                        AgentTodoChecklistBlock(todoItems)
                    }
                }
                if (toolActionCount > 0) {
                    item("tool-progress") {
                        ToolProgressHeader(
                            count = toolActionCount,
                            onExpandAll = {
                                val ids = displayEntries.mapNotNull { (it as? AgentDisplayEntry.ToolAction)?.id }.toSet()
                                expandedToolRows = expandedToolRows + ids
                                collapsedToolRows = collapsedToolRows - ids
                            },
                        )
                    }
                }
                displayEntries.forEach { displayEntry ->
                    item(key = displayEntry.stableKey()) {
                        TranscriptDisplayEntry(
                            entry = displayEntry,
                            activeRepoFullName = if (chatOnlyMode) null else selectedRepo?.fullName,
                            activeBranch = if (chatOnlyMode) null else selectedBranch,
                            activeDefaultBranch = if (chatOnlyMode) null else selectedRepo?.defaultBranch,
                            expandToolCallsByDefault = expandToolCallsByDefault,
                            expandedToolRows = expandedToolRows,
                            collapsedToolRows = collapsedToolRows,
                            onToggleToolRow = { id ->
                                val expanded = if (expandToolCallsByDefault) id !in collapsedToolRows else id in expandedToolRows
                                if (expanded) {
                                    expandedToolRows = expandedToolRows - id
                                    collapsedToolRows = collapsedToolRows + id
                                } else {
                                    expandedToolRows = expandedToolRows + id
                                    collapsedToolRows = collapsedToolRows - id
                                }
                            },
                            onGeneratedFileClick = { previewGeneratedFile = it },
                        )
                    }
                }
                if (running && approvals.isEmpty()) {
                    item {
                        // Terminal-style running line: `■ [running…]` +
                        // blinking cursor instead of a spinner.
                        com.glassfiles.ui.screens.ai.terminal.AgentMessageRow(
                            role = com.glassfiles.ui.screens.ai.terminal.AgentRole.ASSISTANT,
                            streaming = true,
                        ) {
                            Text(
                                "[${Strings.aiAgentRunning}]",
                                fontSize = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.type.message,
                                fontFamily = com.glassfiles.ui.theme.JetBrainsMono,
                                color = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }

        if (error != null) {
            Text(
                error.orEmpty(),
                fontSize = 12.sp,
                color = colors.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        fallbackNotice?.let { notice ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Warning,
                    null,
                    Modifier.size(16.dp),
                    tint = colors.warning,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    notice,
                    fontSize = 12.sp,
                    color = colors.warning,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { fallbackNotice = null }, modifier = Modifier.size(20.dp)) {
                    Icon(
                        Icons.Rounded.Close,
                        null,
                        Modifier.size(14.dp),
                        tint = colors.warning,
                    )
                }
            }
        }
        // D2 — resume banner. Surfaced when the previous run for this
        // session was killed without a clean finish. Tapping Resume
        // re-issues the same prompt; Discard wipes the pointer.
        // Hidden while a run is already in flight (don't compete with
        // streaming UI).
        pendingResume?.takeIf { !running }?.let { pending ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    Strings.aiAgentResumeBannerText,
                    fontSize = 12.sp,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                AgentTextButton(
                    label = "[ ${Strings.aiAgentResumeBannerAction} ]",
                    color = colors.accent,
                    enabled = true,
                ) {
                    val resume = pending
                    pendingResume = null
                    com.glassfiles.data.ai.AiAgentResumeStore
                        .clear(context, activeSessionId)
                    submit(resume.prompt, resume.imageBase64, null)
                }
                AgentTextButton(
                    label = "[ ${Strings.aiAgentResumeBannerDiscard} ]",
                    color = colors.textSecondary,
                    enabled = true,
                ) {
                    pendingResume = null
                    com.glassfiles.data.ai.AiAgentResumeStore
                        .clear(context, activeSessionId)
                }
            }
        }

        pendingWorkspaceDiff?.let { diff ->
            WorkspacePendingReviewBlock(
                diff = diff,
                error = workspaceReviewError,
                onReview = { showWorkspaceReview = true },
                onCommit = {
                    workspaceCommitMessage = workspaceCommitMessage.ifBlank {
                        generateWorkspaceCommitMessage(diff)
                    }
                    showWorkspaceCommit = true
                },
                onDiscard = {
                    val workspaceId = pendingWorkspaceId
                    if (workspaceId != null) {
                        scope.launch {
                            runCatching {
                                com.glassfiles.data.ai.workspace.WorkspaceDatabase(context)
                                    .deleteWorkspace(workspaceId)
                            }.onSuccess {
                                pendingWorkspaceId = null
                                pendingWorkspaceDiff = null
                                workspaceReviewError = null
                            }.onFailure {
                                workspaceReviewError = it.message ?: it.javaClass.simpleName
                            }
                        }
                    }
                },
            )
        }

        if (pendingImage != null) {
            AgentAttachmentPreview(
                base64 = pendingImage.orEmpty(),
                visionAvailable = selectedModel?.let { AiCapability.VISION in it.capabilities } == true,
                onRemove = { pendingImage = null },
            )
        }
        pendingFile?.let { file ->
            AgentFileAttachmentPreview(
                attachment = file,
                onRemove = { pendingFile = null },
            )
        }

        // Input bar — terminal-style. The `>` glyph is fixed inside
        // [AgentInput], the field stays editable as long as the agent
        // isn't busy (even before a repo / branch / model is picked, so
        // the user can pre-draft) and Send is gated on the full set
        // being present + an API key being available.
        val canSend = !running &&
            (input.text.isNotBlank() || pendingImage != null || pendingFile != null) &&
            (chatOnlyMode || (selectedRepo != null && selectedBranch != null)) &&
            selectedModel != null &&
            activeApiKey.isNotBlank()
        com.glassfiles.ui.screens.ai.terminal.AgentInput(
            value = input,
            onValueChange = { input = it },
            onSend = { submit(input.text, pendingImage, pendingFile) },
            onPickImage = { attachmentPicker.launch("*/*") },
            canSend = canSend,
            enabled = !running,
            placeholder = Strings.aiAgentInputHint,
            modifier = Modifier.navigationBarsPadding(),
        )
        pendingWorkspaceDiff?.let { diff ->
            if (showWorkspaceReview) {
                WorkspaceReviewDialog(
                    diff = diff,
                    onDismiss = { showWorkspaceReview = false },
                )
            }
            if (showWorkspaceCommit) {
                WorkspaceCommitDialog(
                    message = workspaceCommitMessage,
                    busy = workspaceCommitBusy,
                    error = workspaceReviewError,
                    onMessageChange = { workspaceCommitMessage = it },
                    onDismiss = { if (!workspaceCommitBusy) showWorkspaceCommit = false },
                    onCommit = {
                        val workspaceId = pendingWorkspaceId ?: return@WorkspaceCommitDialog
                        val repoForCommit = selectedRepo ?: return@WorkspaceCommitDialog
                        val branchForCommit = selectedBranch.orEmpty()
                        workspaceCommitBusy = true
                        workspaceReviewError = null
                        scope.launch {
                            runCatching {
                                val db = com.glassfiles.data.ai.workspace.WorkspaceDatabase(context)
                                val vfs = com.glassfiles.data.ai.workspace.WorkspaceFileSystem(
                                    workspaceId = workspaceId,
                                    realFs = com.glassfiles.data.ai.workspace.GitHubRepositoryFileSystem(
                                        context = context,
                                        owner = repoOwner(repoForCommit),
                                        repo = repoName(repoForCommit),
                                        branch = branchForCommit,
                                    ),
                                    db = db,
                                    committer = com.glassfiles.data.ai.workspace.GitHubWorkspaceCommitter(
                                        context = context,
                                        owner = repoOwner(repoForCommit),
                                        repo = repoName(repoForCommit),
                                        branch = branchForCommit,
                                    ),
                                )
                                vfs.commit(workspaceCommitMessage.trim().ifBlank {
                                    generateWorkspaceCommitMessage(diff)
                                })
                            }.onSuccess { sha ->
                                transcript += AgentEntry.Assistant(
                                    text = "[workspace] committed ${sha.take(7)}",
                                )
                                pendingWorkspaceId = null
                                pendingWorkspaceDiff = null
                                showWorkspaceCommit = false
                                showWorkspaceReview = false
                                workspaceReviewError = null
                            }.onFailure {
                                workspaceReviewError = it.message ?: it.javaClass.simpleName
                            }
                            workspaceCommitBusy = false
                        }
                    },
                )
            }
        }
        if (showHistory) {
            AgentHistorySheet(
                sessions = sessions,
                onOpen = ::openSession,
                onNew = ::startNewSession,
                onDelete = { session ->
                    AiChatSessionStore.delete(context, AGENT_SESSION_MODE, session.id)
                    refreshSessions()
                    if (session.id == activeSessionId) startNewSession()
                },
                onDeleteAll = {
                    AiChatSessionStore.clear(context, AGENT_SESSION_MODE)
                    refreshSessions()
                    startNewSession()
                },
                onDismiss = { showHistory = false },
            )
        }
        // Cost-policy warning gate. Mounted at the top of the screen
        // so it survives swipes / lifecycle changes the bottom-sheet
        // would otherwise dismiss. Only one can be active at a time.
        val warning = pendingWarning
        val pendingInput = pendingWarningInput
        if (warning != null && pendingInput != null) {
            // "Remember" is offered for every reason — even on private
            // repos. The user is explicitly opting in to skip the
            // warning for that (repo, provider) pair; trust them.
            ExpensiveActionWarningDialog(
                warning = warning,
                allowRemember = true,
                onCancel = {
                    pendingWarning = null
                    pendingWarningInput = null
                },
                onContinueOnce = {
                    pendingWarning = null
                    pendingWarningInput = null
                    runTaskInternal(pendingInput.text, pendingInput.image, pendingInput.file)
                },
                onContinueAndRemember = {
                    selectedModel?.let { m ->
                        com.glassfiles.data.ai.cost.AiCostModeStore.setRemembered(
                            context,
                            selectedRepo?.fullName ?: CHAT_ONLY_REPO_KEY,
                            m.providerId.name,
                            true,
                        )
                    }
                    pendingWarning = null
                    pendingWarningInput = null
                    runTaskInternal(pendingInput.text, pendingInput.image, pendingInput.file)
                },
            )
        }
        // Context-scoped system-prompt override dialog. Repo chats use
        // the repo key; chat-only sessions use their own stable chat key.
        if (showSystemPrompt) {
            val promptScopeFullName = systemPromptScopeFullName ?: activeAgentScopeFullName()
            val promptScopeLabel = agentScopeLabel(promptScopeFullName)
            val currentPrompt = remember(promptScopeFullName) {
                com.glassfiles.data.ai.AiAgentPrefs
                    .getSystemPromptOverride(context, promptScopeFullName)
                    .orEmpty()
            }
            val currentPlanFirst = remember(promptScopeFullName) {
                com.glassfiles.data.ai.AiAgentPrefs
                    .getPlanThenExecute(context, promptScopeFullName)
            }
            com.glassfiles.ui.screens.ai.SystemPromptOverrideDialog(
                repoFullName = promptScopeLabel,
                initialPrompt = currentPrompt,
                initialPlanFirst = currentPlanFirst,
                onSave = { text, planFirst ->
                    com.glassfiles.data.ai.AiAgentPrefs.setSystemPromptOverride(
                        context, promptScopeFullName, text,
                    )
                    com.glassfiles.data.ai.AiAgentPrefs.setPlanThenExecute(
                        context, promptScopeFullName, planFirst,
                    )
                    showSystemPrompt = false
                    systemPromptScopeFullName = null
                },
                onDismiss = {
                    showSystemPrompt = false
                    systemPromptScopeFullName = null
                },
            )
        }

        // Terminal-style settings sheet. Lives inside the Column tree so it
        // shares the [AgentTerminalSurface] composition locals (palette,
        // typography). All approval / cost-mode / memory toggles flow back
        // into the same DataStore-backed prefs the agent loop snapshots
        // before every run, so the loop can never disagree with what the
        // user just toggled here.
        if (showSettings) {
            val mode = when (costMode) {
                com.glassfiles.data.ai.cost.AiCostMode.Eco ->
                    com.glassfiles.ui.screens.ai.terminal.AgentMode.ECO
                com.glassfiles.data.ai.cost.AiCostMode.Balanced ->
                    com.glassfiles.ui.screens.ai.terminal.AgentMode.BALANCED
                com.glassfiles.data.ai.cost.AiCostMode.MaxQuality ->
                    com.glassfiles.ui.screens.ai.terminal.AgentMode.MAX_QUALITY
            }
            val modeHint = when (costMode) {
                com.glassfiles.data.ai.cost.AiCostMode.Eco -> Strings.aiCostModeEcoHint
                com.glassfiles.data.ai.cost.AiCostMode.Balanced -> Strings.aiCostModeBalancedHint
                com.glassfiles.data.ai.cost.AiCostMode.MaxQuality -> Strings.aiCostModeMaxHint
            }
            val approvalPrefsRefresh = approvalPrefsVersion
            val protectedPaths = remember(showSettings, approvalPrefsRefresh) {
                com.glassfiles.data.ai.AiAgentApprovalPrefs.getProtectedPaths(context)
            }
            val protectedPathsText = remember(protectedPaths) { protectedPaths.joinToString("\n") }
            val skillsRefresh = skillsVersion
            val installedSkillsCount = remember(skillsRefresh) { AiSkillStore.listSkills(context).size }
            val selectedSkillForSettings = remember(skillsRefresh) {
                AiSkillPrefs.getSelectedSkillId(context)?.let { AiSkillStore.readSkill(context, it) }
            }
            val state = com.glassfiles.ui.screens.ai.terminal.AgentSettingsState(
                repoLabel = if (chatOnlyMode) "chat only" else selectedRepo?.fullName ?: "—",
                branchLabel = if (chatOnlyMode) "—" else selectedBranch ?: "—",
                modelLabel = selectedModel?.let { "${it.providerId.displayName} · ${it.displayName}" } ?: "—",
                mode = mode,
                modeHint = modeHint,
                permissionMode = com.glassfiles.data.ai.AiAgentApprovalPrefs.getPermissionMode(context),
                autoApproveReads = autoApproveReads,
                autoApproveEdits = com.glassfiles.data.ai.AiAgentApprovalPrefs.getAutoApproveEdits(context),
                autoApproveWrites = com.glassfiles.data.ai.AiAgentApprovalPrefs.getAutoApproveWrites(context),
                autoApproveCommits = com.glassfiles.data.ai.AiAgentApprovalPrefs.getAutoApproveCommits(context),
                autoApproveDestructive = com.glassfiles.data.ai.AiAgentApprovalPrefs.getAutoApproveDestructive(context),
                yoloMode = com.glassfiles.data.ai.AiAgentApprovalPrefs.getYoloMode(context),
                sessionTrust = com.glassfiles.data.ai.AiAgentApprovalPrefs.getSessionTrust(context),
                writeLimitPerTask = com.glassfiles.data.ai.AiAgentApprovalPrefs.getWriteLimit(context),
                protectedPathsText = protectedPathsText,
                protectedPathsCount = protectedPaths.size,
                backgroundExecution = com.glassfiles.data.ai.AiAgentApprovalPrefs.getBackgroundExecution(context),
                keepCpuAwake = com.glassfiles.data.ai.AiAgentApprovalPrefs.getKeepCpuAwake(context),
                workspaceMode = workspaceMode,
                memoryProjectKnowledge = com.glassfiles.data.ai.AiAgentMemoryPrefs.getProjectKnowledge(context),
                memoryUserPreferences = com.glassfiles.data.ai.AiAgentMemoryPrefs.getUserPreferences(context),
                memoryChatSummaries = com.glassfiles.data.ai.AiAgentMemoryPrefs.getChatSummaries(context),
                memorySemanticSearch = com.glassfiles.data.ai.AiAgentMemoryPrefs.getSemanticSearch(context),
                workingMemoryEnabled = com.glassfiles.data.ai.AiWorkingMemoryPrefs.getEnabled(context),
                workingMemoryReminders = com.glassfiles.data.ai.AiWorkingMemoryPrefs.getReminders(context),
                skillsEnabled = AiSkillPrefs.getEnableSkills(context),
                skillsAutoSuggest = AiSkillPrefs.getAutoSuggest(context),
                skillsAllowUntrustedDangerous = AiSkillPrefs.getAllowUntrustedDangerousTools(context),
                selectedSkillLabel = selectedSkillForSettings?.let { "${it.packId}/${it.id}" } ?: "auto",
                installedSkillsCount = installedSkillsCount,
                instantRender = instantRender,
                expandToolCallsByDefault = expandToolCallsByDefault,
            )
            val chatRepoDisplay = com.glassfiles.ui.screens.ai.terminal.RepoDisplay(
                key = CHAT_ONLY_REPO_KEY,
                title = "chat only",
                subtitle = "no repository tools",
            )
            val repoOptions = com.glassfiles.ui.screens.ai.terminal.AgentSettingsOptions(
                items = listOf(chatRepoDisplay) + repos.map {
                    com.glassfiles.ui.screens.ai.terminal.RepoDisplay(
                        key = it.fullName,
                        title = it.fullName,
                        subtitle = it.description.takeIf { d -> d.isNotBlank() },
                    )
                },
                selected = if (chatOnlyMode) chatRepoDisplay else selectedRepo?.let {
                    com.glassfiles.ui.screens.ai.terminal.RepoDisplay(
                        key = it.fullName,
                        title = it.fullName,
                        subtitle = it.description.takeIf { d -> d.isNotBlank() },
                    )
                },
                label = { it.title },
                subtitle = { it.subtitle },
                enabled = !running,
            )
            val branchOptions = com.glassfiles.ui.screens.ai.terminal.AgentSettingsOptions(
                items = branches.toList(),
                selected = selectedBranch,
                label = { it },
                enabled = !chatOnlyMode && !running && branches.isNotEmpty(),
            )
            val modelOptions = com.glassfiles.ui.screens.ai.terminal.AgentSettingsOptions(
                items = models.map {
                    com.glassfiles.ui.screens.ai.terminal.ModelDisplay(
                        key = it.uniqueKey,
                        title = it.displayName,
                        subtitle = it.providerId.displayName,
                    )
                },
                selected = selectedModel?.let {
                    com.glassfiles.ui.screens.ai.terminal.ModelDisplay(
                        key = it.uniqueKey,
                        title = it.displayName,
                        subtitle = it.providerId.displayName,
                    )
                },
                label = { it.title },
                subtitle = { it.subtitle },
                enabled = !running && models.isNotEmpty(),
            )
            com.glassfiles.ui.screens.ai.terminal.AgentSettingsBottomSheet(
                state = state,
                repos = repoOptions,
                branches = branchOptions,
                models = modelOptions,
                onRepoSelected = { picked ->
                    if (picked.key == CHAT_ONLY_REPO_KEY) {
                        enterChatOnlyMode()
                    } else {
                        if (chatOnlyMode && transcript.isNotEmpty()) {
                            startNewSession()
                        }
                        chatOnlyMode = false
                        selectedRepo = repos.firstOrNull { it.fullName == picked.key }
                    }
                },
                onBranchSelected = { picked -> selectedBranch = picked },
                onModelSelected = { picked ->
                    val match = models.firstOrNull { it.uniqueKey == picked.key }
                    if (match != null) {
                        selectedModel = match
                        selectedRepo?.fullName?.let { repoFull ->
                            com.glassfiles.data.ai.AiAgentPrefs.setLastModel(
                                context,
                                repoFull,
                                match.uniqueKey,
                            )
                        }
                    }
                },
                onModeChange = { picked ->
                    val newMode = when (picked) {
                        com.glassfiles.ui.screens.ai.terminal.AgentMode.ECO ->
                            com.glassfiles.data.ai.cost.AiCostMode.Eco
                        com.glassfiles.ui.screens.ai.terminal.AgentMode.BALANCED ->
                            com.glassfiles.data.ai.cost.AiCostMode.Balanced
                        com.glassfiles.ui.screens.ai.terminal.AgentMode.MAX_QUALITY ->
                            com.glassfiles.data.ai.cost.AiCostMode.MaxQuality
                    }
                    costMode = newMode
                    com.glassfiles.data.ai.cost.AiCostModeStore.setMode(context, newMode)
                },
                onPermissionModeChange = { mode ->
                    com.glassfiles.data.ai.AiAgentApprovalPrefs.applyPermissionMode(context, mode)
                    autoApproveReads = com.glassfiles.data.ai.AiAgentApprovalPrefs.getAutoApproveReads(context)
                    approvalPrefsVersion += 1
                },
                onAutoApproveReadsChange = { value ->
                    autoApproveReads = value
                    com.glassfiles.data.ai.AiAgentApprovalPrefs
                        .setAutoApproveReads(context, value)
                    approvalPrefsVersion += 1
                },
                onAutoApproveEditsChange = {
                    com.glassfiles.data.ai.AiAgentApprovalPrefs.setAutoApproveEdits(context, it)
                    approvalPrefsVersion += 1
                },
                onAutoApproveWritesChange = {
                    com.glassfiles.data.ai.AiAgentApprovalPrefs.setAutoApproveWrites(context, it)
                    approvalPrefsVersion += 1
                },
                onAutoApproveCommitsChange = {
                    com.glassfiles.data.ai.AiAgentApprovalPrefs.setAutoApproveCommits(context, it)
                    approvalPrefsVersion += 1
                },
                onAutoApproveDestructiveChange = {
                    com.glassfiles.data.ai.AiAgentApprovalPrefs.setAutoApproveDestructive(context, it)
                    approvalPrefsVersion += 1
                },
                onYoloModeChange = {
                    com.glassfiles.data.ai.AiAgentApprovalPrefs.setYoloMode(context, it)
                    approvalPrefsVersion += 1
                },
                onSessionTrustChange = {
                    com.glassfiles.data.ai.AiAgentApprovalPrefs.setSessionTrust(context, it)
                    approvalPrefsVersion += 1
                },
                onWriteLimitChange = {
                    com.glassfiles.data.ai.AiAgentApprovalPrefs.setWriteLimit(context, it)
                    approvalPrefsVersion += 1
                },
                onProtectedPathsChange = {
                    com.glassfiles.data.ai.AiAgentApprovalPrefs.setProtectedPaths(context, it)
                    approvalPrefsVersion += 1
                },
                onBackgroundExecutionChange = {
                    com.glassfiles.data.ai.AiAgentApprovalPrefs.setBackgroundExecution(context, it)
                },
                onKeepCpuAwakeChange = {
                    com.glassfiles.data.ai.AiAgentApprovalPrefs.setKeepCpuAwake(context, it)
                },
                onWorkspaceModeChange = {
                    workspaceMode = it
                    com.glassfiles.data.ai.AiAgentApprovalPrefs.setWorkspaceMode(context, it)
                },
                onMemoryProjectKnowledgeChange = {
                    com.glassfiles.data.ai.AiAgentMemoryPrefs.setProjectKnowledge(context, it)
                },
                onMemoryUserPreferencesChange = {
                    com.glassfiles.data.ai.AiAgentMemoryPrefs.setUserPreferences(context, it)
                },
                onMemoryChatSummariesChange = {
                    com.glassfiles.data.ai.AiAgentMemoryPrefs.setChatSummaries(context, it)
                },
                onMemorySemanticSearchChange = {
                    com.glassfiles.data.ai.AiAgentMemoryPrefs.setSemanticSearch(context, it)
                },
                onWorkingMemoryEnabledChange = {
                    com.glassfiles.data.ai.AiWorkingMemoryPrefs.setEnabled(context, it)
                },
                onWorkingMemoryRemindersChange = {
                    com.glassfiles.data.ai.AiWorkingMemoryPrefs.setReminders(context, it)
                },
                onViewWorkingMemory = {
                    memorySheetRepoFullName = activeAgentScopeFullName()
                    showSettings = false
                    showWorkingMemory = true
                },
                onViewMemoryFiles = {
                    memorySheetRepoFullName = activeAgentScopeFullName()
                    showSettings = false
                    showMemoryFiles = true
                },
                onClearMemory = {
                    com.glassfiles.data.ai.AiAgentMemoryStore.clearAll(context)
                },
                onSkillsEnabledChange = {
                    AiSkillPrefs.setEnableSkills(context, it)
                    skillsVersion += 1
                },
                onSkillsAutoSuggestChange = {
                    AiSkillPrefs.setAutoSuggest(context, it)
                    skillsVersion += 1
                },
                onSkillsAllowUntrustedDangerousChange = {
                    AiSkillPrefs.setAllowUntrustedDangerousTools(context, it)
                    skillsVersion += 1
                },
                onViewSkills = {
                    showSettings = false
                    showSkills = true
                },
                onImportSkillPack = {
                    showSettings = false
                    skillPackPicker.launch(arrayOf("*/*"))
                },
                onInstantRenderChange = { instantRender = it },
                onExpandToolCallsChange = {
                    expandToolCallsByDefault = it
                    com.glassfiles.data.ai.AiAgentApprovalPrefs.setExpandToolCalls(context, it)
                    expandedToolRows = emptySet()
                    collapsedToolRows = emptySet()
                },
                onOpenHistory = {
                    showSettings = false
                    showHistory = true
                },
                onOpenSystemPrompt = {
                    systemPromptScopeFullName = activeAgentScopeFullName()
                    showSettings = false
                    showSystemPrompt = true
                },
                onClearChat = {
                    transcript.clear()
                    startNewSession()
                },
                onExportChat = {
                    // Export hook is wired through AiChatSessionStore by
                    // the host activity; the bottom-sheet doesn't need
                    // direct file IO. Closing the sheet is enough — the
                    // host shows its own share-intent picker.
                    showSettings = false
                },
                onDismiss = { showSettings = false },
            )
        }
        if (showSkills) {
            AgentSkillsDialog(
                version = skillsVersion,
                importError = skillImportError,
                selectedSkillId = AiSkillPrefs.getSelectedSkillId(context),
                onImport = {
                    showSkills = false
                    skillPackPicker.launch(arrayOf("*/*"))
                },
                onSelectSkill = { skill ->
                    AiSkillPrefs.setSelectedSkillId(context, skill?.let { "${it.packId}/${it.id}" })
                    skillsVersion += 1
                },
                onToggleSkill = { skill, enabled ->
                    AiSkillStore.setSkillEnabled(context, skill.packId, skill.id, enabled)
                    if (!enabled && AiSkillPrefs.getSelectedSkillId(context) == "${skill.packId}/${skill.id}") {
                        AiSkillPrefs.setSelectedSkillId(context, null)
                    }
                    skillsVersion += 1
                },
                onTogglePack = { packId, enabled ->
                    AiSkillStore.setPackEnabled(context, packId, enabled)
                    if (!enabled && AiSkillPrefs.getSelectedSkillId(context)?.startsWith("$packId/") == true) {
                        AiSkillPrefs.setSelectedSkillId(context, null)
                    }
                    skillsVersion += 1
                },
                onDeletePack = { packId ->
                    AiSkillStore.deletePack(context, packId)
                    if (AiSkillPrefs.getSelectedSkillId(context)?.startsWith("$packId/") == true) {
                        AiSkillPrefs.setSelectedSkillId(context, null)
                    }
                    skillsVersion += 1
                },
                onDismiss = {
                    skillImportError = null
                    showSkills = false
                },
            )
        }
        skillImportPreview?.let { preview ->
            AgentSkillImportPreviewDialog(
                preview = preview,
                onImport = {
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) { AiSkillStore.commitImport(context, preview) }
                        }
                            .onSuccess {
                                skillsVersion += 1
                                skillImportPreview = null
                                showSkills = true
                            }
                            .onFailure {
                                skillImportError = it.message ?: it.javaClass.simpleName
                                skillImportPreview = null
                                showSkills = true
                            }
                    }
                },
                onDismiss = { skillImportPreview = null },
            )
        }
        val memoryRepoFullName = memorySheetRepoFullName
        if (showMemoryFiles && memoryRepoFullName != null) {
            val memoryFiles = remember(memoryRepoFullName, showMemoryFiles) {
                com.glassfiles.data.ai.AiAgentMemoryStore.editableFiles(context, memoryRepoFullName)
            }
            com.glassfiles.ui.screens.ai.terminal.AgentMemoryFilesDialog(
                files = memoryFiles,
                onRebuildIndex = {
                    com.glassfiles.data.ai.AiAgentMemoryStore.rebuildIndex(
                        context,
                        memoryRepoFullName,
                    )
                },
                onSave = { key, content ->
                    com.glassfiles.data.ai.AiAgentMemoryStore.saveEditableFile(
                        context,
                        memoryRepoFullName,
                        key,
                        content,
                    )
                },
                onDismiss = {
                    showMemoryFiles = false
                    memorySheetRepoFullName = null
                },
            )
        }
        if (showWorkingMemory && memoryRepoFullName != null) {
            val workingMemory = remember(memoryRepoFullName, showWorkingMemory) {
                com.glassfiles.data.ai.AiAgentMemoryStore.readWorkingMemory(
                    context,
                    memoryRepoFullName,
                )
            }
            com.glassfiles.ui.screens.ai.terminal.AgentWorkingMemoryDialog(
                content = workingMemory,
                onSave = { content ->
                    com.glassfiles.data.ai.AiAgentMemoryStore.writeWorkingMemory(
                        context,
                        memoryRepoFullName,
                        content,
                    )
                },
                onClear = {
                    com.glassfiles.data.ai.AiAgentMemoryStore.clearWorkingMemory(
                        context,
                        memoryRepoFullName,
                    )
                },
                onDismiss = {
                    showWorkingMemory = false
                    memorySheetRepoFullName = null
                },
            )
        }
        previewGeneratedFile?.let { file ->
            AgentGeneratedFilePreviewSheet(
                file = file,
                onDismiss = { previewGeneratedFile = null },
            )
        }
    }
    } // end AgentTerminalSurface
}

/**
 * Compact informational banner rendered inside the transcript LazyColumn
 * to surface session-wide warnings (private repo, read-only, missing
 * key, etc). Optional [actionLabel]/[onAction] turns the banner into a
 * one-tap dismiss / navigate affordance.
 */
@Composable
private fun AgentBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    text: String,
    title: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    // Terminal-style inline banner: `[!]` glyph + monospace body, no
    // rounded backgrounds. The [tint] arg is honoured on the glyph so
    // callers can still distinguish info / warning / error visually,
    // but the row itself has no surface.
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    val role = when {
        tint == colors.error -> com.glassfiles.ui.screens.ai.terminal.AgentRole.ERROR
        tint == colors.warning -> com.glassfiles.ui.screens.ai.terminal.AgentRole.SYSTEM
        else -> com.glassfiles.ui.screens.ai.terminal.AgentRole.SYSTEM
    }
    com.glassfiles.ui.screens.ai.terminal.AgentMessageRow(role = role) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (!title.isNullOrBlank()) {
                Text(
                    title,
                    fontSize = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.type.label,
                    fontWeight = FontWeight.Bold,
                    color = tint,
                    fontFamily = com.glassfiles.ui.theme.JetBrainsMono,
                )
            }
            Text(
                text,
                fontSize = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.type.message,
                color = colors.textPrimary,
                fontFamily = com.glassfiles.ui.theme.JetBrainsMono,
            )
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                Text(
                    "[$actionLabel]",
                    fontSize = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.type.label,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent,
                    fontFamily = com.glassfiles.ui.theme.JetBrainsMono,
                    modifier = Modifier.clickable { onAction() },
                )
            }
        }
    }
}

@Composable
private fun EmptyAgentState(message: String) {
    // Terminal-style empty hint: `$` prompt + monospace message, no
    // Large terminal glyph centered in the empty transcript area.
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "\$ $message",
            fontFamily = com.glassfiles.ui.theme.JetBrainsMono,
            fontSize = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.type.message,
            color = colors.textMuted,
        )
    }
}

@Composable
private fun WorkspacePendingReviewBlock(
    diff: com.glassfiles.data.ai.workspace.WorkspaceDiff,
    error: String?,
    onReview: () -> Unit,
    onCommit: () -> Unit,
    onDiscard: () -> Unit,
) {
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.warning, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "workspace: pending review",
            color = colors.warning,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        )
        Text(
            "${diff.filesChanged} files modified · ${diff.additions} (+) · ${diff.deletions} (-)",
            color = colors.textPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        diff.changes.take(4).forEach { change ->
            Text(
                "${change.operation.name.lowercase().padEnd(8)} ${change.path} (+${change.additions} -${change.deletions})",
                color = colors.textSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
        if (diff.changes.size > 4) {
            Text(
                "... ${diff.changes.size - 4} more",
                color = colors.textMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
        if (!error.isNullOrBlank()) {
            Text(error, color = colors.error, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AgentTextButton("[ review diff ]", colors.accent, true, onReview)
            AgentTextButton("[ commit ]", colors.warning, true, onCommit)
            AgentTextButton("[ discard ]", colors.error, true, onDiscard)
        }
    }
}

@Composable
private fun WorkspaceReviewDialog(
    diff: com.glassfiles.data.ai.workspace.WorkspaceDiff,
    onDismiss: () -> Unit,
) {
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    var selectedPath by remember(diff.workspaceId) {
        mutableStateOf(diff.changes.firstOrNull()?.path.orEmpty())
    }
    val selected = diff.changes.firstOrNull { it.path == selectedPath } ?: diff.changes.firstOrNull()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "workspace diff",
                    color = colors.textPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                AgentTextButton("[ close ]", colors.textSecondary, true, onDismiss)
            }
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LazyColumn(
                    Modifier
                        .weight(0.42f)
                        .fillMaxSize()
                        .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(diff.changes, key = { it.path }) { change ->
                        val selectedFile = change.path == selected?.path
                        Text(
                            "${if (selectedFile) ">" else " "} ${change.path} (+${change.additions} -${change.deletions})",
                            color = if (selectedFile) colors.accent else colors.textSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { selectedPath = change.path }
                                .padding(horizontal = 6.dp, vertical = 5.dp),
                        )
                    }
                }
                LazyColumn(
                    Modifier
                        .weight(0.58f)
                        .fillMaxSize()
                        .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                        .padding(6.dp),
                ) {
                    val lines = selected?.unifiedDiff?.lines().orEmpty()
                    items(lines.size) { index ->
                        val line = lines[index]
                        val color = when {
                            line.startsWith("+") -> colors.accent
                            line.startsWith("-") -> colors.error
                            line.startsWith("@@") -> colors.warning
                            else -> colors.textSecondary
                        }
                        Text(line, color = color, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceCommitDialog(
    message: String,
    busy: Boolean,
    error: String?,
    onMessageChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCommit: () -> Unit,
) {
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "commit message:",
                color = colors.textPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
            BasicTextField(
                value = message,
                onValueChange = onMessageChange,
                enabled = !busy,
                textStyle = TextStyle(
                    color = colors.textPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                ),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                    .padding(10.dp),
            )
            if (!error.isNullOrBlank()) {
                Text(error, color = colors.error, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AgentTextButton(
                    label = if (busy) "[ committing... ]" else "[ commit & push ]",
                    color = if (busy) colors.textMuted else colors.accent,
                    enabled = !busy && message.isNotBlank(),
                    onClick = onCommit,
                )
                AgentTextButton("[ cancel ]", colors.textSecondary, !busy, onDismiss)
            }
        }
    }
}

private fun generateWorkspaceCommitMessage(
    diff: com.glassfiles.data.ai.workspace.WorkspaceDiff,
): String {
    val first = diff.changes.firstOrNull()?.path?.substringAfterLast('/') ?: "workspace changes"
    return buildString {
        append("chore: update ")
        append(first)
        appendLine()
        appendLine()
        diff.changes.take(6).forEach { change ->
            append("- ")
            append(change.operation.name.lowercase())
            append(" ")
            append(change.path)
            appendLine()
        }
    }.trimEnd()
}

@Composable
private fun AgentAttachmentPreview(base64: String, visionAvailable: Boolean, onRemove: () -> Unit) {
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    val bitmap = remember(base64) { decodeAgentBitmap(base64) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "IMAGE",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = colors.textSecondary,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                if (visionAvailable) "Attached to next agent message" else "Selected model has no vision input",
                fontSize = 12.sp,
                color = if (visionAvailable) colors.textPrimary else colors.error,
                maxLines = 2,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Rounded.Close, null, Modifier.size(18.dp), tint = colors.textSecondary)
        }
    }
}

@Composable
private fun AgentFileAttachmentPreview(attachment: AiPreparedAttachment, onRemove: () -> Unit) {
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AttachFile, null, Modifier.size(18.dp), tint = colors.textSecondary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    attachment.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
                Text(
                    attachment.summary,
                    fontSize = 10.sp,
                    color = colors.textMuted,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, null, Modifier.size(18.dp), tint = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun AgentFileContentSummary(fileContent: String) {
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.AttachFile, null, Modifier.size(14.dp), tint = colors.textSecondary)
        Spacer(Modifier.width(8.dp))
        Text(
            fileContent.lineSequence().firstOrNull().orEmpty().ifBlank { "Attached file" },
            fontSize = 11.sp,
            color = colors.textSecondary,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AgentGeneratedFilesRow(
    files: List<AiChatSessionStore.GeneratedFile>,
    onClick: (AiChatSessionStore.GeneratedFile) -> Unit,
) {
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    val pulse by rememberInfiniteTransition().animateFloat(
        initialValue = 0.22f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse,
        ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        files.forEach { file ->
            val isArchive = isGeneratedArchiveFile(file)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.accent.copy(alpha = pulse), RoundedCornerShape(8.dp))
                    .clickable { onClick(file) }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.accent.copy(alpha = pulse * 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.AttachFile,
                        null,
                        Modifier.size(14.dp),
                        tint = if (isArchive) colors.warning else colors.accent,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        file.name,
                        fontSize = 11.sp,
                        color = colors.accent,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                    )
                    Text(
                        generatedFileSubtitle(file),
                        fontSize = 10.sp,
                        color = colors.textMuted,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentGeneratedFilePreviewSheet(
    file: AiChatSessionStore.GeneratedFile,
    onDismiss: () -> Unit,
) {
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isArchive = isGeneratedArchiveFile(file)
    var saving by remember(file.name, file.content, file.sourcePath) { mutableStateOf(false) }
    var saveStatus by remember(file.name, file.content, file.sourcePath) { mutableStateOf<String?>(null) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.58f))
                .padding(top = 48.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .background(colors.background)
                    .border(
                        1.dp,
                        colors.accent,
                        RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .width(38.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.border)
                        .align(Alignment.CenterHorizontally),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (isArchive) "ARCHIVE FILE" else "FILE PREVIEW",
                            color = if (isArchive) colors.warning else colors.accent,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                        Text(
                            file.name,
                            color = colors.textSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AgentTextButton(
                            if (saving) "[ saving... ]" else "[ download ]",
                            if (saving) colors.textMuted else colors.accent,
                            !saving,
                        ) {
                            saving = true
                            saveStatus = null
                            scope.launch {
                                val result = runCatching {
                                    withContext(Dispatchers.IO) {
                                        saveGeneratedFileToDownloads(context, file)
                                    }
                                }
                                saveStatus = result.fold(
                                    onSuccess = { "saved: $it" },
                                    onFailure = { "error: ${it.message ?: it.javaClass.simpleName}" },
                                )
                                saving = false
                            }
                        }
                        AgentTextButton("[ done ]", colors.textSecondary, true, onDismiss)
                    }
                }
                if (!saveStatus.isNullOrBlank()) {
                    Text(
                        saveStatus.orEmpty(),
                        color = if (saveStatus?.startsWith("error:") == true) colors.error else colors.accent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }
                if (isArchive) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceElevated)
                            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AgentTerminalKeyValue("name", file.name)
                        AgentTerminalKeyValue("type", "archive")
                        AgentTerminalKeyValue("size", generatedFileSizeLabel(file))
                        Text(
                            "Archive contents are hidden in chat. Use [ download ] to save the archive file itself.",
                            color = colors.textSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                } else {
                    com.glassfiles.ui.screens.ai.terminal.AgentTerminalCodeBlock(
                        text = file.content,
                        lang = file.language,
                        filePath = file.name,
                        context = context,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        scrollBody = true,
                        fillBody = true,
                        plainText = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentSkillsDialog(
    version: Int,
    importError: String?,
    selectedSkillId: String?,
    onImport: () -> Unit,
    onSelectSkill: (AiSkill?) -> Unit,
    onToggleSkill: (AiSkill, Boolean) -> Unit,
    onTogglePack: (String, Boolean) -> Unit,
    onDeletePack: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    val packs = remember(version) { AiSkillStore.listPacks(context) }
    val skills = remember(version) { AiSkillStore.listSkills(context) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.58f))
                .padding(top = 48.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .background(colors.background)
                    .border(1.dp, colors.accent, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AgentTerminalSectionTitle("AI SKILLS")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AgentTerminalCommand("[+ import .gskill]", colors.accent, onImport)
                    AgentTerminalCommand("[ auto skill ]", colors.warning) { onSelectSkill(null) }
                    AgentTerminalCommand("[ done ]", colors.textSecondary, onDismiss)
                }
                Text(
                    "selected: ${selectedSkillId ?: "auto"}",
                    color = colors.textSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
                if (!importError.isNullOrBlank()) {
                    Text(
                        "IMPORT ERROR: $importError",
                        color = colors.error,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }
                AgentTerminalSectionTitle("INSTALLED PACKS")
                if (packs.isEmpty()) {
                    Text(
                        "(no installed skills)",
                        color = colors.textMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                }
                packs.forEach { pack ->
                    val packSkills = skills.filter { it.packId == pack.id }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceElevated)
                            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${if (pack.enabled) "[✓]" else "[ ]"} ${pack.name}",
                                color = colors.textPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                pack.risk.name.lowercase(Locale.US),
                                color = riskColor(pack.risk, colors),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            )
                        }
                        Text(
                            "${pack.author ?: "unknown"} · ${if (pack.trusted) "trusted" else "untrusted"} · ${packSkills.size} skills",
                            color = colors.textMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AgentTerminalCommand(
                                if (pack.enabled) "[ disable ]" else "[ enable ]",
                                colors.warning,
                            ) { onTogglePack(pack.id, !pack.enabled) }
                            AgentTerminalCommand("[ delete ]", colors.error) { onDeletePack(pack.id) }
                        }
                        packSkills.forEach { skill ->
                            val skillKey = "${skill.packId}/${skill.id}"
                            val isSelected = selectedSkillId == skillKey || selectedSkillId == skill.id
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.surface)
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    "${if (skill.enabled) "[✓]" else "[ ]"} ${if (isSelected) ">" else " "} ${skill.id}",
                                    color = if (isSelected) colors.warning else colors.accent,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "risk: ${skill.risk.name.lowercase(Locale.US)} · tools: ${skill.tools.joinToString(", ")}",
                                    color = colors.textSecondary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                )
                                if (!skill.description.isNullOrBlank()) {
                                    Text(
                                        skill.description,
                                        color = colors.textMuted,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    AgentTerminalCommand(
                                        if (isSelected) "[ selected ]" else "[ use ]",
                                        if (isSelected) colors.warning else colors.accent,
                                    ) { onSelectSkill(skill) }
                                    AgentTerminalCommand(
                                        if (skill.enabled) "[ disable ]" else "[ enable ]",
                                        colors.warning,
                                    ) { onToggleSkill(skill, !skill.enabled) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentSkillImportPreviewDialog(
    preview: AiSkillImportPreview,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.accent, RoundedCornerShape(8.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AgentTerminalSectionTitle("IMPORT SKILL PACK")
            AgentTerminalKeyValue("name", preview.pack.name)
            AgentTerminalKeyValue("version", preview.pack.version)
            AgentTerminalKeyValue("author", preview.pack.author ?: "unknown")
            AgentTerminalKeyValue("risk", preview.pack.risk.name.lowercase(Locale.US))
            AgentTerminalKeyValue("skills", preview.skills.size.toString())
            preview.pack.source?.let { AgentTerminalKeyValue("source", it) }
            AgentTerminalSectionTitle("REQUESTED TOOLS")
            preview.pack.tools.forEach {
                Text("[ ] $it", color = colors.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
            if (preview.warnings.isNotEmpty()) {
                AgentTerminalSectionTitle("WARNINGS")
                preview.warnings.forEach {
                    Text("! $it", color = colors.warning, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AgentTerminalCommand("[ import ]", colors.accent, onImport)
                AgentTerminalCommand("[ cancel ]", colors.textSecondary, onDismiss)
            }
        }
    }
}

@Composable
private fun AgentTerminalSectionTitle(text: String) {
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    Text(
        text,
        color = colors.accent,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
    )
}

@Composable
private fun AgentTerminalKeyValue(key: String, value: String) {
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    Text(
        "${key.padEnd(12)} $value",
        color = colors.textSecondary,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
    )
}

@Composable
private fun AgentTerminalCommand(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

private fun riskColor(risk: com.glassfiles.data.ai.skills.AiSkillRisk, colors: com.glassfiles.ui.screens.ai.terminal.AgentTerminalColors): Color =
    when (risk) {
        com.glassfiles.data.ai.skills.AiSkillRisk.READ_ONLY,
        com.glassfiles.data.ai.skills.AiSkillRisk.LOW -> colors.accent
        com.glassfiles.data.ai.skills.AiSkillRisk.MEDIUM -> colors.warning
        com.glassfiles.data.ai.skills.AiSkillRisk.HIGH,
        com.glassfiles.data.ai.skills.AiSkillRisk.DANGEROUS -> colors.error
    }

@Composable
private fun AgentMessageImage(base64: String) {
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    val bitmap = remember(base64) { decodeAgentBitmap(base64) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surface),
        )
    }
}

@Composable
private fun AgentHistorySheet(
    sessions: List<AiChatSessionStore.Session>,
    onOpen: (AiChatSessionStore.Session) -> Unit,
    onNew: () -> Unit,
    onDelete: (AiChatSessionStore.Session) -> Unit,
    onDeleteAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    val sdf = remember { SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault()) }
    var query by remember { mutableStateOf("") }
    // Filter on title / repo / branch / model / first user message. Each
    // session is matched once per token (whitespace-split), so the user
    // can type multiple keywords ("kotlin main") and progressively narrow
    // the list. Empty query short-circuits to the full list.
    val filteredSessions = remember(sessions, query) {
        val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) sessions
        else sessions.filter { session ->
            val haystack = buildString {
                appendLine(session.title)
                appendLine(session.repoFullName)
                appendLine(session.branch)
                appendLine(session.modelId)
                session.messages.take(6).forEach { appendLine(it.content.take(400)) }
            }.lowercase()
            tokens.all { haystack.contains(it) }
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(18.dp))
                .padding(vertical = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(Strings.aiAgentHistoryTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    val counter = if (query.isBlank()) {
                        "${sessions.size} ${Strings.aiHistoryCount}"
                    } else {
                        "${filteredSessions.size}/${sessions.size} ${Strings.aiHistoryCount}"
                    }
                    Text(counter, fontSize = 11.sp, color = colors.textSecondary)
                }
                if (sessions.isNotEmpty()) {
                    IconButton(onClick = onDeleteAll) {
                        Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(20.dp), tint = colors.textSecondary)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, null, Modifier.size(20.dp), tint = colors.textSecondary)
                }
            }
            if (sessions.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                HistorySearchField(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            if (sessions.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    Text(Strings.aiHistoryEmpty, fontSize = 13.sp, color = colors.textSecondary)
                }
            } else if (filteredSessions.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    Text(Strings.aiHistorySearchEmpty, fontSize = 13.sp, color = colors.textSecondary)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filteredSessions, key = { it.id }) { session ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surfaceElevated)
                                .clickable { onOpen(session) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.Chat, null, Modifier.size(18.dp), tint = colors.textSecondary)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    session.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                )
                                val repoLine = buildString {
                                    val repo = session.repoFullName.takeIf { it.isNotBlank() }
                                        ?: Strings.aiAgentHistoryNoRepo
                                    append(repo)
                                    if (session.branch.isNotBlank()) {
                                        append(" · ")
                                        append(session.branch)
                                    }
                                    if (session.modelId.isNotBlank()) {
                                        append(" · ")
                                        append(session.modelId)
                                    }
                                }
                                Text(
                                    repoLine,
                                    fontSize = 11.sp,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                )
                                Text(
                                    "${sdf.format(Date(session.updatedAt))} · ${session.messages.size} msgs",
                                    fontSize = 10.sp,
                                    color = colors.textSecondary,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                )
                            }
                            IconButton(onClick = { onDelete(session) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Rounded.Close, null, Modifier.size(16.dp), tint = colors.textSecondary)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.accent, RoundedCornerShape(4.dp))
                    .clickable(onClick = onNew)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "[+ ${Strings.aiAgentHistoryNew}]",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent,
                    fontFamily = com.glassfiles.ui.theme.JetBrainsMono,
                )
            }
        }
    }
}

/** Per-session usage rollup — derived from the transcript. */
private data class SessionStats(
    val inputChars: Int,
    val outputChars: Int,
    val totalChars: Int,
    val tokens: Int,
    val costUsd: Double?,
)

private fun computeSessionStats(
    transcript: List<AgentEntry>,
    rate: com.glassfiles.data.ai.ModelPricing.Rate?,
): SessionStats {
    // Input = what the model has to ingest each turn — the user's
    // prompts plus every tool result we feed back to it. Output = what
    // it generated for us (assistant text and tool-call JSON args).
    var input = 0
    var output = 0
    transcript.forEach { entry ->
        when (entry) {
            is AgentEntry.User -> input += entry.text.length + (entry.fileContent?.length ?: 0) + (entry.imageBase64?.length ?: 0) / 8
            is AgentEntry.Assistant -> output += entry.text.length
            is AgentEntry.ToolCall -> output += entry.call.argsJson.length
            is AgentEntry.ToolResult -> input += entry.result.output.length
            is AgentEntry.Pending -> Unit
        }
    }
    val total = input + output
    val tokens = com.glassfiles.data.ai.ModelPricing.estimateTokens(total)
    val cost = rate?.let { com.glassfiles.data.ai.ModelPricing.estimateCostUsd(it, input, output) }
    return SessionStats(input, output, total, tokens, cost)
}

@Composable
private fun CostMeter(stats: SessionStats) {
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    // SessionStats values come from a chars-based estimator (no
    // provider-reported usage), so prefix the visible numbers with the
    // module-wide "~" estimated marker. Keeping the marker right next
    // to the value avoids any ambiguity for cost-sensitive users.
    val tokenLabel = "~" + when {
        stats.tokens >= 1000 -> "%.1fk".format(stats.tokens / 1000.0)
        else -> stats.tokens.toString()
    }
    val costLabel = stats.costUsd?.let { c ->
        "~" + when {
            c < 0.01 -> "<\$0.01"
            c < 1.0 -> "\$%.3f".format(c)
            else -> "\$%.2f".format(c)
        }
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceElevated)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (costLabel != null) {
            Text(
                costLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(width = 1.dp, height = 10.dp)
                    .background(colors.border)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            "$tokenLabel ${Strings.aiAgentTokensLabel}",
            fontSize = 11.sp,
            color = colors.textSecondary,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun HistorySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, Modifier.size(16.dp), tint = colors.textSecondary)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = colors.textPrimary, fontSize = 13.sp),
                cursorBrush = SolidColor(colors.accent),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            Strings.aiAgentHistorySearchHint,
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                        )
                    }
                    inner()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Rounded.Close, null, Modifier.size(14.dp), tint = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun TranscriptDisplayEntry(
    entry: AgentDisplayEntry,
    activeRepoFullName: String?,
    activeBranch: String?,
    activeDefaultBranch: String?,
    expandToolCallsByDefault: Boolean,
    expandedToolRows: Set<String>,
    collapsedToolRows: Set<String>,
    onToggleToolRow: (String) -> Unit,
    onGeneratedFileClick: (AiChatSessionStore.GeneratedFile) -> Unit,
) {
    when (entry) {
        is AgentDisplayEntry.Raw -> TranscriptEntry(
            entry = entry.entry,
            activeRepoFullName = activeRepoFullName,
            activeBranch = activeBranch,
            activeDefaultBranch = activeDefaultBranch,
            onGeneratedFileClick = onGeneratedFileClick,
        )
        is AgentDisplayEntry.ToolAction -> {
            val expanded = if (expandToolCallsByDefault) {
                entry.id !in collapsedToolRows
            } else {
                entry.id in expandedToolRows
            }
            ToolActionRow(
                call = entry.call,
                result = entry.result,
                expanded = expanded,
                onToggle = { onToggleToolRow(entry.id) },
            )
        }
    }
}

@Composable
private fun ToolProgressHeader(
    count: Int,
    onExpandAll: () -> Unit,
) {
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "[task progress · $count actions]",
            color = colors.textSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            "[⤢ expand all]",
            color = colors.warning,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onExpandAll)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun AgentTodoChecklistBlock(items: List<AgentTodoItem>) {
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "[todo ${todoProgressLabel(items)}]",
                color = colors.accent,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            val active = items.firstOrNull { it.status == AgentTodoStatus.IN_PROGRESS }
            if (active != null) {
                Text(
                    active.title.shortenMiddle(28),
                    color = colors.textMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
        }
        items.forEach { item ->
            val color = when (item.status) {
                AgentTodoStatus.COMPLETED -> colors.textMuted
                AgentTodoStatus.IN_PROGRESS -> colors.warning
                AgentTodoStatus.CANCELED -> colors.error
                AgentTodoStatus.PENDING -> colors.textPrimary
            }
            Text(
                "${todoStatusMarker(item.status)} ${item.title}",
                color = color,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun ToolActionRow(
    call: AiToolCall,
    result: AiToolResult?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    val accent = if (result?.isError == true) colors.error else colors.textSecondary
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (expanded) colors.surfaceElevated else colors.background)
            .border(
                1.dp,
                if (expanded) colors.border else colors.border.copy(alpha = 0.42f),
                RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$ ${compactToolVerb(call.name).padEnd(8)} | ",
                color = colors.accent,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                compactToolTarget(call, result),
                color = colors.textPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                compactToolStatus(result),
                color = accent,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Text(
                redactedToolArgsJson(call),
                color = colors.textSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                result?.output ?: "(waiting for result)",
                color = if (result?.isError == true) colors.error else colors.textPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "[tap to collapse]",
                color = colors.textMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun TranscriptEntry(
    entry: AgentEntry,
    activeRepoFullName: String?,
    activeBranch: String?,
    activeDefaultBranch: String?,
    onGeneratedFileClick: (AiChatSessionStore.GeneratedFile) -> Unit,
) {
    val context = LocalContext.current
    when (entry) {
        is AgentEntry.User -> {
            com.glassfiles.ui.screens.ai.terminal.AgentMessageRow(
                role = com.glassfiles.ui.screens.ai.terminal.AgentRole.USER,
            ) {
                if (entry.imageBase64 != null) {
                    AgentMessageImage(entry.imageBase64)
                    Spacer(Modifier.height(6.dp))
                }
                if (entry.fileContent != null) {
                    AgentFileContentSummary(entry.fileContent)
                    Spacer(Modifier.height(6.dp))
                }
                if (entry.text.isNotBlank()) {
                    TerminalMessageBody(
                        text = entry.text,
                        context = context,
                        plainColor = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors.accent,
                    )
                }
            }
        }
        is AgentEntry.Assistant -> {
            if (entry.text.isNotBlank() || entry.generatedFiles.isNotEmpty()) {
                com.glassfiles.ui.screens.ai.terminal.AgentMessageRow(
                    role = com.glassfiles.ui.screens.ai.terminal.AgentRole.ASSISTANT,
                ) {
                    val displayText = stripAgentGeneratedFileBlocks(entry.text)
                    if (displayText.isNotBlank()) {
                        TerminalMessageBody(text = displayText, context = context)
                    }
                    if (entry.generatedFiles.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        AgentGeneratedFilesRow(
                            files = entry.generatedFiles,
                            onClick = onGeneratedFileClick,
                        )
                    }
                }
            }
        }
        is AgentEntry.ToolCall -> {
            // Completed tool calls are implementation details. Pending calls
            // still render below so approval-required actions stay visible.
        }
        is AgentEntry.ToolResult -> {
            if (entry.result.isError) ToolResultCard(entry)
        }
        is AgentEntry.Pending -> {
            ToolCallCard(
                entry = AgentEntry.ToolCall(entry.pending.call),
                isPending = true,
                activeRepoFullName = activeRepoFullName,
                activeBranch = activeBranch,
                activeDefaultBranch = activeDefaultBranch,
                onApprove = { entry.pending.deferred.complete(true) },
                onReject = { entry.pending.deferred.complete(false) },
            )
        }
    }
}

/**
 * Splits chat text into plain segments and triple-backtick code fences.
 * Each fence uses [AgentTerminalCodeBlock], whose header exposes a copy
 * command; plain text keeps the message role color.
 */
@Composable
private fun TerminalMessageBody(
    text: String,
    context: Context,
    plainColor: Color = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors.textPrimary,
) {
    val cleaned = com.glassfiles.ui.screens.ai.terminal.cleanAgentText(text)
    val segments = remember(cleaned) { splitCodeFences(cleaned) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is TerminalSegment.Plain -> {
                    if (segment.text.isNotBlank()) {
                        com.glassfiles.ui.screens.ai.terminal.AgentMessageText(
                            text = segment.text,
                            color = plainColor,
                        )
                    }
                }
                is TerminalSegment.Code -> {
                    com.glassfiles.ui.screens.ai.terminal.AgentTerminalCodeBlock(
                        text = segment.text,
                        lang = segment.lang,
                        filePath = null,
                        context = context,
                    )
                }
            }
        }
    }
}

private sealed class TerminalSegment {
    data class Plain(val text: String) : TerminalSegment()
    data class Code(val text: String, val lang: String) : TerminalSegment()
}

private fun splitCodeFences(input: String): List<TerminalSegment> {
    if (!input.contains("```")) return listOf(TerminalSegment.Plain(input))
    val out = mutableListOf<TerminalSegment>()
    var index = 0
    while (index < input.length) {
        val open = input.indexOf("```", index)
        if (open < 0) {
            out += TerminalSegment.Plain(input.substring(index))
            break
        }
        if (open > index) out += TerminalSegment.Plain(input.substring(index, open).trimEnd('\n'))
        val newline = input.indexOf('\n', open + 3)
        val langStart = open + 3
        val (lang, contentStart) = if (newline < 0) {
            "" to (open + 3)
        } else {
            input.substring(langStart, newline).trim() to (newline + 1)
        }
        val close = input.indexOf("```", contentStart)
        if (close < 0) {
            out += TerminalSegment.Code(input.substring(contentStart), lang)
            break
        }
        out += TerminalSegment.Code(input.substring(contentStart, close).trimEnd('\n'), lang)
        index = close + 3
        if (index < input.length && input[index] == '\n') index++
    }
    return out
}

@Composable
private fun ToolCallCard(
    entry: AgentEntry.ToolCall,
    isPending: Boolean,
    activeRepoFullName: String?,
    activeBranch: String?,
    activeDefaultBranch: String?,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    val showDiff = isPending && entry.call.name in DIFF_PREVIEW_TOOLS
    val showOpenPrPreview = isPending && entry.call.name == AgentTools.OPEN_PR.name
    val targetsProtectedBranch = remember(entry.call.argsJson, activeBranch, activeDefaultBranch) {
        targetsProtectedBranch(entry.call, activeBranch, activeDefaultBranch)
    }
    // When a destructive call lands on the default branch we make the
    // user explicitly tick a confirmation — Approve stays disabled until
    // they do. Read-only browsing or pending = false bypasses the gate.
    var protectedConfirmed by remember(entry.call.argsJson) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Build, null, Modifier.size(14.dp), tint = colors.warning)
            Spacer(Modifier.width(6.dp))
            Text(
                Strings.aiAgentToolCallTitle.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = colors.textSecondary,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                entry.call.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            redactedToolArgsJson(entry.call),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = colors.textSecondary,
        )
        if (showDiff) {
            Spacer(Modifier.height(10.dp))
            DiffPreview(
                call = entry.call,
                activeRepoFullName = activeRepoFullName,
                activeBranch = activeBranch,
            )
        }
        if (showOpenPrPreview) {
            Spacer(Modifier.height(10.dp))
            OpenPrPreview(call = entry.call, defaultBase = activeDefaultBranch)
        }
        if (isPending && targetsProtectedBranch) {
            Spacer(Modifier.height(10.dp))
            ProtectedBranchWarning(
                branch = activeDefaultBranch.orEmpty(),
                checked = protectedConfirmed,
                onCheckedChange = { protectedConfirmed = it },
            )
        }
        if (isPending) {
            Spacer(Modifier.height(10.dp))
            val approveEnabled = !targetsProtectedBranch || protectedConfirmed
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    label = Strings.aiAgentApprove,
                    icon = Icons.Rounded.Check,
                    bg = if (approveEnabled) colors.accent else colors.surfaceElevated,
                    fg = if (approveEnabled) colors.background else colors.textSecondary,
                    onClick = if (approveEnabled) onApprove else ({}),
                )
                ActionButton(
                    label = Strings.aiAgentReject,
                    icon = Icons.Rounded.Close,
                    bg = colors.surfaceElevated,
                    fg = colors.textPrimary,
                    onClick = onReject,
                )
            }
        }
    }
}

/**
 * True if [call] is a destructive tool call that will land on the
 * repo's default branch (typically `main`/`master`). The user must
 * explicitly confirm such calls — most projects expect changes to flow
 * through a feature branch + PR.
 */
private fun targetsProtectedBranch(
    call: AiToolCall,
    activeBranch: String?,
    defaultBranch: String?,
): Boolean {
    val def = defaultBranch?.takeIf { it.isNotBlank() } ?: return false
    val args = runCatching { org.json.JSONObject(call.argsJson) }.getOrNull()
    val target = when (call.name) {
        AgentTools.WRITE_FILE.name,
        AgentTools.EDIT_FILE.name,
        AgentTools.COMMIT.name -> activeBranch
        AgentTools.OPEN_PR.name -> args?.optString("base").orEmpty().ifBlank { def }
        AgentTools.CREATE_BRANCH.name -> null
        else -> null
    } ?: return false
    return target == def
}

private fun redactedToolArgsJson(call: AiToolCall): String =
    runCatching {
        val obj = org.json.JSONObject(call.argsJson)
        val redacted = redactJsonValue(obj)
        if (redacted is org.json.JSONObject) redacted.toString(2) else redacted.toString()
    }.getOrElse { call.argsJson }

private fun redactJsonValue(value: Any?): Any? = when (value) {
    is org.json.JSONObject -> org.json.JSONObject().also { out ->
        value.keys().forEachRemaining { key ->
            val child = value.opt(key)
            out.put(
                key,
                if (key.shouldHideToolArg()) hiddenToolArgLabel(child) else redactJsonValue(child),
            )
        }
    }
    is org.json.JSONArray -> org.json.JSONArray().also { out ->
        for (i in 0 until value.length()) out.put(redactJsonValue(value.opt(i)))
    }
    else -> value
}

private fun String.shouldHideToolArg(): Boolean {
    val key = lowercase(Locale.US)
    return key == "content" ||
        key == "old_string" ||
        key == "new_string" ||
        key == "patch"
}

private fun hiddenToolArgLabel(value: Any?): String {
    val length = when (value) {
        is String -> value.length
        is org.json.JSONArray -> value.length()
        is org.json.JSONObject -> value.length()
        else -> 0
    }
    return "[hidden ${if (length > 0) "$length chars" else "value"}]"
}

private fun compactToolVerb(name: String): String = when (AgentToolRegistry.uiKindFor(name)) {
    AiToolUiKind.SEARCH -> "search"
    AiToolUiKind.LIST -> "list"
    AiToolUiKind.READ -> "read"
    AiToolUiKind.WRITE -> "write"
    AiToolUiKind.EDIT -> "edit"
    AiToolUiKind.DELETE -> "delete"
    AiToolUiKind.DIFF -> "diff"
    AiToolUiKind.ARCHIVE -> "archive"
    AiToolUiKind.TERMINAL -> "run"
    AiToolUiKind.MEMORY -> "memory"
    AiToolUiKind.WEB -> "web"
    AiToolUiKind.GITHUB -> "github"
    AiToolUiKind.SKILL -> "skill"
    AiToolUiKind.ARTIFACT -> "file"
    AiToolUiKind.TASK -> "todo"
    AiToolUiKind.SYSTEM -> "context"
    AiToolUiKind.OTHER -> when {
        name.contains("move", ignoreCase = true) || name.contains("rename", ignoreCase = true) || name.contains("copy", ignoreCase = true) -> "move"
        name.contains("think", ignoreCase = true) -> "thinking"
        else -> name.substringBefore("_").take(8).ifBlank { "tool" }
    }
}

private fun compactToolTarget(call: AiToolCall, result: AiToolResult?): String {
    val args = runCatching { org.json.JSONObject(call.argsJson) }.getOrNull()
    val target = args?.let { obj ->
        listOf("path", "file", "filename", "filepath", "directory", "query", "command", "url", "skill_id", "pack_id", "repo", "owner")
            .firstNotNullOfOrNull { key ->
                obj.optString(key, "").takeIf { it.isNotBlank() }?.let { value ->
                    if (key == "query") "\"${value.shortenMiddle(42)}\"" else value.shortenPath()
                }
            }
    }.orEmpty().ifBlank { call.name }
    val resultHint = compactToolResultHint(result)
    return if (resultHint.isBlank()) target else "$target -> $resultHint"
}

private fun compactToolResultHint(result: AiToolResult?): String {
    if (result == null) return ""
    if (result.isError) return "error"
    val output = result.output.trim()
    if (output.isBlank()) return "done"
    return when {
        output.contains("no matches", ignoreCase = true) -> "no matches"
        output.contains("(empty", ignoreCase = true) -> "empty"
        output.length > 900 -> "${output.length}b"
        else -> output.lineSequence().firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.removePrefix("✓")
            ?.shortenMiddle(38)
            .orEmpty()
    }
}

private fun compactToolStatus(result: AiToolResult?): String = when {
    result == null -> "..."
    result.isError -> "!"
    else -> "✓"
}

private fun String.shortenPath(max: Int = 52): String {
    val clean = trim().replace('\\', '/')
    if (clean.length <= max) return clean
    val parts = clean.split('/').filter { it.isNotBlank() }
    if (parts.size >= 3) {
        val compact = parts.takeLast(3).joinToString("/")
        if (compact.length <= max) return compact
    }
    return clean.shortenMiddle(max)
}

private fun String.shortenMiddle(max: Int): String {
    if (length <= max) return this
    if (max <= 3) return take(max)
    val left = (max - 1) / 2
    val right = max - left - 1
    return take(left) + "…" + takeLast(right)
}

private fun saveGeneratedFileToDownloads(
    context: Context,
    file: AiChatSessionStore.GeneratedFile,
): String {
    val displayName = safeDownloadFileName(file.name)
    val bytes = generatedFileBytes(context, file)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeForGeneratedFile(displayName))
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert returned null")
        resolver.openOutputStream(uri)?.use { output ->
            output.write(bytes)
        } ?: error("Unable to open Downloads output stream")
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return "${Environment.DIRECTORY_DOWNLOADS}/$displayName"
    }
    val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    downloads.mkdirs()
    val outFile = uniqueDownloadFile(downloads, displayName)
    outFile.writeBytes(bytes)
    return outFile.absolutePath
}

private fun generatedFileBytes(context: Context, file: AiChatSessionStore.GeneratedFile): ByteArray {
    val sourcePath = file.sourcePath
    if (!sourcePath.isNullOrBlank()) {
        val source = File(sourcePath).canonicalFile
        val allowedRoots = listOfNotNull(
            context.filesDir,
            context.cacheDir,
            context.getExternalFilesDir(null),
            runCatching { Environment.getExternalStorageDirectory() }.getOrNull(),
        ).map { it.canonicalFile }
        require(allowedRoots.any { root -> source.path == root.path || source.path.startsWith(root.path + File.separator) }) {
            "Generated file source is outside app storage"
        }
        require(source.isFile) { "Generated file source is missing" }
        return source.readBytes()
    }
    return file.content.toByteArray(Charsets.UTF_8)
}

private val generatedArchiveExtensions = setOf("zip", "gskill", "jar", "aar", "7z", "tar", "gz", "tgz", "rar")

private fun isGeneratedArchiveName(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase(Locale.US) in generatedArchiveExtensions

private fun isGeneratedArchiveFile(file: AiChatSessionStore.GeneratedFile): Boolean =
    isGeneratedArchiveName(file.name)

private fun generatedFileSubtitle(file: AiChatSessionStore.GeneratedFile): String =
    if (isGeneratedArchiveFile(file)) {
        "archive · ${generatedFileSizeLabel(file)}"
    } else {
        "${file.content.length} chars"
    }

private fun generatedFileSizeLabel(file: AiChatSessionStore.GeneratedFile): String {
    file.sourcePath?.takeIf { it.isNotBlank() }?.let { path ->
        val length = runCatching { File(path).length() }.getOrDefault(0L)
        if (length > 0L) return formatAgentBytes(length)
    }
    return formatAgentBytes(file.content.toByteArray(Charsets.UTF_8).size.toLong())
}

private fun formatAgentBytes(bytes: Long): String =
    when {
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

private fun safeDownloadFileName(name: String): String {
    val base = name.replace('\\', '/')
        .substringAfterLast('/')
        .replace(Regex("[^A-Za-z0-9._ -]"), "_")
        .trim()
        .trim('.')
    return base.ifBlank { "agent_file.txt" }
}

private fun uniqueDownloadFile(directory: java.io.File, name: String): java.io.File {
    val initial = java.io.File(directory, name)
    if (!initial.exists()) return initial
    val stem = name.substringBeforeLast('.', name)
    val ext = name.substringAfterLast('.', "")
        .takeIf { it != name }
        ?.let { ".$it" }
        .orEmpty()
    var index = 1
    while (true) {
        val candidate = java.io.File(directory, "$stem ($index)$ext")
        if (!candidate.exists()) return candidate
        index += 1
    }
}

private fun mimeForGeneratedFile(name: String): String =
    when (name.substringAfterLast('.', "").lowercase(Locale.US)) {
        "zip" -> "application/zip"
        "gskill" -> "application/zip"
        "jar" -> "application/java-archive"
        "aar" -> "application/zip"
        "7z" -> "application/x-7z-compressed"
        "tar" -> "application/x-tar"
        "gz", "tgz" -> "application/gzip"
        "rar" -> "application/vnd.rar"
        "md", "markdown" -> "text/markdown"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "csv" -> "text/csv"
        "txt", "kt", "kts", "java", "dart", "js", "ts", "py", "sh", "gradle", "log" -> "text/plain"
        else -> "application/octet-stream"
    }

@Composable
private fun ProtectedBranchWarning(
    branch: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.error.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, null, Modifier.size(16.dp), tint = colors.error)
            Spacer(Modifier.width(8.dp))
            Text(
                Strings.aiAgentProtectedBranchTitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.error,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            Strings.aiAgentProtectedBranchSubtitle.replace("{branch}", branch),
            fontSize = 11.sp,
            color = colors.error,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable { onCheckedChange(!checked) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (checked) "[✓]" else "[ ]",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.error,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                Strings.aiAgentProtectedBranchConfirm,
                fontSize = 12.sp,
                color = colors.error,
            )
        }
    }
}

@Composable
private fun OpenPrPreview(call: AiToolCall, defaultBase: String?) {
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    val args = runCatching { org.json.JSONObject(call.argsJson) }.getOrNull()
    val title = args?.optString("title").orEmpty()
    val body = args?.optString("body").orEmpty()
    val head = args?.optString("head").orEmpty()
    val base = args?.optString("base").orEmpty().ifBlank { defaultBase.orEmpty() }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            Strings.aiAgentOpenPrPreview.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            color = colors.textSecondary,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            title.ifBlank { "(no title)" },
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "$head  →  $base",
            fontSize = 12.sp,
            color = colors.textSecondary,
            fontFamily = FontFamily.Monospace,
        )
        if (body.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                body.take(800) + if (body.length > 800) "…" else "",
                fontSize = 12.sp,
                color = colors.textPrimary,
            )
        }
    }
}

private val DIFF_PREVIEW_TOOLS = setOf(
    AgentTools.WRITE_FILE.name,
    AgentTools.EDIT_FILE.name,
    AgentTools.COMMIT.name,
)

/** Diff preview rendered inside [ToolCallCard] when a destructive tool
 * call is awaiting approval. Fetches the current file content from
 * GitHub for `write_file` / `commit`, derives the old text locally for
 * `edit_file`, and renders a compact line-level `+/-` diff. */
@Composable
private fun DiffPreview(
    call: AiToolCall,
    activeRepoFullName: String?,
    activeBranch: String?,
) {
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    val context = LocalContext.current
    val args = remember(call.argsJson) {
        runCatching { org.json.JSONObject(call.argsJson) }.getOrElse { org.json.JSONObject() }
    }
    val owner = activeRepoFullName?.substringBefore('/').orEmpty()
    val repo = activeRepoFullName?.substringAfter('/').orEmpty()
    val branch = activeBranch.orEmpty()

    when (call.name) {
        AgentTools.EDIT_FILE.name -> {
            val path = args.optString("path", "?")
            val oldStr = args.optString("old_string", "")
            val newStr = args.optString("new_string", "")
            DiffBlockHeader(label = "$path  (edit)")
            Spacer(Modifier.height(4.dp))
            DiffLines(LineDiff.diff(oldStr, newStr))
        }
        AgentTools.WRITE_FILE.name -> {
            val path = args.optString("path", "?")
            val content = args.optString("content", "")
            val original = produceState<String?>(
                initialValue = null,
                key1 = owner, key2 = repo, key3 = branch,
            ) {
                value = if (owner.isBlank() || repo.isBlank() || branch.isBlank() || path.isBlank()) {
                    ""
                } else {
                    runCatching {
                        kotlinx.coroutines.withContext(Dispatchers.IO) {
                            GitHubManager.getFileContent(context, owner, repo, path, branch)
                        }
                    }.getOrDefault("")
                }
            }
            val orig = original.value
            DiffBlockHeader(label = "$path  (write)")
            Spacer(Modifier.height(4.dp))
            if (orig == null) {
                Text(Strings.aiAgentDiffLoading, fontSize = 11.sp, color = colors.textSecondary)
            } else {
                DiffLines(LineDiff.diff(orig, content))
            }
        }
        AgentTools.COMMIT.name -> {
            val files = args.optJSONArray("files")
            if (files == null || files.length() == 0) {
                Text(Strings.aiAgentDiffEmpty, fontSize = 11.sp, color = colors.textSecondary)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val limit = minOf(files.length(), 5)
                    for (i in 0 until limit) {
                        val f = files.optJSONObject(i) ?: continue
                        val path = f.optString("path", "?")
                        val content = f.optString("content", "")
                        val original = produceState<String?>(
                            initialValue = null,
                            key1 = "$owner/$repo/$branch/$path",
                        ) {
                            value = if (owner.isBlank() || repo.isBlank() || branch.isBlank() || path.isBlank()) {
                                ""
                            } else {
                                runCatching {
                                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                                        GitHubManager.getFileContent(context, owner, repo, path, branch)
                                    }
                                }.getOrDefault("")
                            }
                        }
                        DiffBlockHeader(label = path)
                        Spacer(Modifier.height(2.dp))
                        val orig = original.value
                        if (orig == null) {
                            Text(Strings.aiAgentDiffLoading, fontSize = 11.sp, color = colors.textSecondary)
                        } else {
                            DiffLines(LineDiff.diff(orig, content))
                        }
                    }
                    if (files.length() > limit) {
                        Text(
                            "+${files.length() - limit} more file(s)",
                            fontSize = 11.sp,
                            color = colors.textSecondary,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffBlockHeader(label: String) {
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    Text(
        label,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
        color = colors.textSecondary,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun DiffLines(diff: List<LineDiff.Line>) {
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    val stats = LineDiff.stats(diff)
    val compacted = LineDiff.compact(diff, contextLines = 2)
    val maxRender = 60
    val display = if (compacted.size > maxRender) compacted.take(maxRender) else compacted
    val addBg = colors.warning.copy(alpha = 0.15f)
    val delBg = colors.error.copy(alpha = 0.12f)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .border(0.5.dp, colors.border, RoundedCornerShape(8.dp))
            .padding(vertical = 4.dp),
    ) {
        Text(
            "+${stats.added}  -${stats.removed}",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textSecondary,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
        for (line in display) {
            if (line == null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        "…",
                        fontSize = 10.sp,
                        color = colors.textSecondary,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                continue
            }
            val (prefix, bg, fg) = when (line) {
                is LineDiff.Line.Add -> Triple("+ ", addBg, colors.textPrimary)
                is LineDiff.Line.Del -> Triple("- ", delBg, colors.textPrimary)
                is LineDiff.Line.Same -> Triple("  ", androidx.compose.ui.graphics.Color.Transparent, colors.textSecondary)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .padding(horizontal = 8.dp, vertical = 1.dp),
            ) {
                Text(
                    "$prefix${line.text}",
                    fontSize = 11.sp,
                    color = fg,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
        if (compacted.size > maxRender) {
            Text(
                "+${compacted.size - maxRender} more lines",
                fontSize = 10.sp,
                color = colors.textSecondary,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    bg: androidx.compose.ui.graphics.Color,
    fg: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = fg)
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = fg)
    }
}

@Composable
private fun ToolResultCard(entry: AgentEntry.ToolResult) {
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    val accent = if (entry.result.isError) colors.error else colors.warning
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (entry.result.isError) Strings.aiAgentToolError.uppercase()
                else Strings.aiAgentToolResultTitle.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = accent,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                entry.result.name,
                fontSize = 12.sp,
                color = colors.textSecondary,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            entry.result.output,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = colors.textPrimary,
        )
    }
}

// ─── Agent loop ───────────────────────────────────────────────────────────

private suspend fun runAgentLoop(
    seedMessages: List<AiMessage>,
    initialProvider: com.glassfiles.data.ai.providers.AiProvider,
    initialModelId: String,
    initialApiKey: String,
    tools: List<com.glassfiles.data.ai.agent.AiTool>,
    executor: GitHubToolExecutor,
    localSessionId: String? = null,
    transcript: androidx.compose.runtime.snapshots.SnapshotStateList<AgentEntry>,
    todoItems: androidx.compose.runtime.snapshots.SnapshotStateList<AgentTodoItem>,
    approvals: androidx.compose.runtime.snapshots.SnapshotStateList<PendingApproval>,
    /**
     * Snapshot of all approval-related prefs at the start of the task. The loop
     * runs every tool call through [com.glassfiles.data.ai.agent.AiAgentApprovalPolicy]
     * with these settings, so YOLO / per-category auto-approve / session-trust /
     * write-limit / protected-path toggles all take effect — not just
     * `autoApproveReads`, which used to be the only one wired in.
     */
    approvalSettings: com.glassfiles.data.ai.agent.AiAgentApprovalSettings,
    activeSkill: AiSkill? = null,
    allowedSkillTools: Set<String>? = null,
    context: android.content.Context,
    fallbackCandidates: List<FallbackCandidate>,
    onFallback: (AiModel) -> Unit,
    /**
     * Cost-policy accumulator for this task. The loop:
     *  - bumps [com.glassfiles.data.ai.cost.AiContextEstimate.toolCallsExecuted]
     *    once per tool call (regardless of approval)
     *  - bumps [com.glassfiles.data.ai.cost.AiContextEstimate.writeProposals]
     *    once per non-readOnly tool call submitted for approval
     *  - approximates input/output context size by char count of every
     *    AiMessage that flows through, then stops the loop early when
     *    [com.glassfiles.data.ai.cost.AiContextEstimate.contextExhausted]
     *    or [com.glassfiles.data.ai.cost.AiContextEstimate.toolCallsExhausted]
     *    fire. When `null`, behaviour matches pre-cost-policy code.
     */
    estimate: com.glassfiles.data.ai.cost.AiContextEstimate? = null,
    /**
     * Repo full name used for working-memory bookkeeping (BUGS_FIX.md
     * Section 3). Empty string disables the feature for this run, e.g.
     * when the user is talking to the agent without a repo selected.
     */
    workingMemoryRepo: String = "",
    /**
     * When true, the loop:
     *  • injects a `[system] Update working memory…` reminder after a
     *    write_file / edit_file / memory_write (other than to
     *    working_memory.md itself) call when more than 3 tool calls have
     *    elapsed since the last working-memory update OR when the file
     *    being edited is new for this task,
     *  • passes those reminders straight to the model on the next turn.
     *
     * The blob itself is prepended to the system prompt by the caller
     * (see seed-message construction inside [AiAgentScreen]). This
     * parameter only controls the in-loop reminder cadence.
     */
    workingMemoryReminders: Boolean = false,
) {
    // Files that have been touched by write_file / edit_file in this run.
    // Used to detect "new file in working memory" for the reminder rule
    // ("if file was not in working memory" — BUGS_FIX.md Section 3 auto-
    // update logic).
    val filesEditedThisTask = mutableSetOf<String>()
    // Number of tool calls since the agent last wrote / appended to
    // working_memory.md. We start at infinity so the very first edit
    // always triggers a reminder.
    var callsSinceWorkingMemoryUpdate = Int.MAX_VALUE / 2
    /**
     * Pending working-memory reminder to inject as a system message on
     * the next turn. Drained at the top of each iteration so the model
     * sees it before it picks the next tool. Null when there's nothing
     * to remind about.
     */
    var pendingWorkingMemoryReminder: String? = null
    var messages = seedMessages
    var provider = initialProvider
    var modelId = initialModelId
    var apiKey = initialApiKey
    val remainingFallbacks = ArrayDeque(fallbackCandidates)
    // Per-task counter for the user's WRITE LIMIT PER TASK setting. Bumped
    // on every approved write/edit/commit/destructive (anything that
    // [AiAgentApprovalCheck.countsAsWrite] flags). When the configured limit
    // is hit the loop stops with an explanatory message. A limit of 0 means
    // "unlimited" — see [AiAgentApprovalPrefs.WRITE_LIMIT_UNLIMITED].
    var writeCount = 0
    val writeLimit = approvalSettings.writeLimitPerTask
    val effectiveWriteLimit =
        if (writeLimit <= com.glassfiles.data.ai.AiAgentApprovalPrefs.WRITE_LIMIT_UNLIMITED) {
            Int.MAX_VALUE
        } else writeLimit
    // Seed the cost estimate with the initial conversation so a long
    // pre-existing chat doesn't get a fresh "0 / N" budget when the
    // user resumes it.
    estimate?.addInput(seedMessages.sumOf { it.content.length })
    val maxIterations = estimate?.limits?.maxToolCalls ?: MAX_ITERATIONS
    repeat(maxIterations) {
        // Cost-policy backstops. If the user's selected mode says
        // "you've burned enough already" we drop a final assistant
        // message and bail. The model has no way to override this
        // — it's enforced client-side.
        if (estimate?.contextExhausted == true) {
            transcript += AgentEntry.Assistant(
                text = "[cost-policy: context budget reached " +
                    "(${estimate.totalChars} / ${estimate.limits.maxTotalContextChars} chars). " +
                    "Stopping to protect your token budget. Increase the cost mode in settings " +
                    "or start a new task to continue.]",
            )
            return
        }
        if (estimate?.writeProposalsExhausted == true) {
            transcript += AgentEntry.Assistant(
                text = "[cost-policy: too many write proposals in this task " +
                    "(${estimate.writeProposals} / ${estimate.limits.maxWriteProposals}). " +
                    "Stopping to avoid runaway edits.]",
            )
            return
        }
        // Drain any pending working-memory reminder (BUGS_FIX.md Section
        // 3 — "Reminder появляется как system message"). We add it as a
        // role=system message so providers that distinguish system vs
        // user instructions still treat it as a directive instead of
        // user input. The agent is expected to respond by issuing a
        // memory_write / memory_append against working_memory.md before
        // its next file edit.
        pendingWorkingMemoryReminder?.let { reminder ->
            messages = messages + AiMessage(role = "system", content = reminder)
            pendingWorkingMemoryReminder = null
        }
        // Compact older turns when the rolling transcript grows past the
        // soft limit — without this the next provider call eventually
        // 4xx's on the model's context window. We keep the most recent
        // turns intact and replace the older prefix with a single
        // summary message, generated by a one-shot non-tool call.
        messages = compactIfNeeded(provider, modelId, apiKey, context, messages)
        // Insert a streaming Assistant placeholder so text deltas land in
        // the UI as they arrive. The placeholder has a stable id, so
        // LazyList keeps its position in the list across replacements.
        val streamingEntry = AgentEntry.Assistant(text = "")
        transcript += streamingEntry
        val streamingIndex = transcript.lastIndex

        val turn = try {
            callWithFallback(
                context = context,
                tools = tools,
                messages = messages,
                streamingIndex = streamingIndex,
                transcript = transcript,
                remainingFallbacks = remainingFallbacks,
                onFallback = { model, newProvider, newModelId, newKey ->
                    provider = newProvider
                    modelId = newModelId
                    apiKey = newKey
                    onFallback(model)
                },
                provider = provider,
                modelId = modelId,
                apiKey = apiKey,
            )
        } catch (e: Exception) {
            // Drop the empty placeholder before rethrowing so the run-job
            // catch block doesn't have to deal with it.
            if (streamingIndex in transcript.indices) {
                val current = transcript[streamingIndex]
                if (current is AgentEntry.Assistant && current.text.isEmpty()) {
                    transcript.removeAt(streamingIndex)
                }
            }
            throw e
        }

        com.glassfiles.data.ai.usage.AiUsageAccounting.appendReportedOrEstimated(
            context = context,
            providerId = provider.id.name,
            modelId = modelId,
            mode = com.glassfiles.data.ai.usage.AiUsageMode.GITHUB_AGENT,
            messages = messages,
            output = turn.assistantText,
            reported = turn.usage,
        )

        if (streamingIndex in transcript.indices) {
            val current = transcript[streamingIndex]
            if (current is AgentEntry.Assistant) {
                val fullText = current.text.ifBlank { turn.assistantText }
                val generatedFiles = extractAgentGeneratedFiles(fullText)
                val displayText = stripAgentGeneratedFileBlocks(fullText)
                if (generatedFiles.isNotEmpty() || displayText != current.text) {
                    transcript[streamingIndex] = current.copy(
                        text = displayText,
                        generatedFiles = mergeGeneratedFiles(current.generatedFiles, generatedFiles),
                    )
                }
            }
        }

        // If the model produced no text at all, drop the placeholder so
        // we don't leave an empty assistant card sitting between tool
        // call cards.
        if (turn.assistantText.isBlank() && streamingIndex in transcript.indices) {
            val current = transcript[streamingIndex]
            if (current is AgentEntry.Assistant && current.text.isEmpty()) {
                transcript.removeAt(streamingIndex)
            }
        }
        if (turn.toolCalls.isEmpty()) return

        val assistantMsg = AiMessage(
            role = "assistant",
            content = stripAgentGeneratedFileBlocks(turn.assistantText),
            toolCalls = turn.toolCalls,
        )
        val results = mutableListOf<AiMessage>()
        for (call in turn.toolCalls) {
            transcript += AgentEntry.ToolCall(call)
            val tool = AgentTools.byName(call.name)
            estimate?.bumpToolCall()
            if (!AgentToolRegistry.isReadOnly(call.name)) estimate?.bumpWriteProposal()
            if (call.isTodoTool()) {
                val result = executeTodoTool(call, todoItems)
                transcript += AgentEntry.ToolResult(result)
                results += AiMessage(
                    role = "tool",
                    content = result.output,
                    toolCallId = result.callId,
                    toolName = result.name,
                )
                continue
            }
            if (allowedSkillTools != null && call.name !in allowedSkillTools) {
                val result = AiToolResult(
                    callId = call.id,
                    name = call.name,
                    output = "deny: tool not allowed by active skill (${activeSkill?.id.orEmpty()})",
                    isError = true,
                )
                transcript += AgentEntry.ToolResult(result)
                results += AiMessage(
                    role = "tool",
                    content = result.output,
                    toolCallId = result.callId,
                    toolName = result.name,
                )
                continue
            }
            // Run the call through the central approval policy so YOLO,
            // per-category auto-approve toggles, session-trust and
            // protected-paths all line up with what the user picked in
            // settings — instead of the old "only autoApproveReads is wired"
            // shortcut. Destructive / protected-path / commits-to-main
            // calls still always require explicit approval, regardless of
            // YOLO. See AiAgentApprovalPolicy.check kdoc.
            val policyCheck = com.glassfiles.data.ai.agent.AiAgentApprovalPolicy.check(
                call = call,
                tool = tool,
                settings = approvalSettings,
                sessionTrusted = approvalSettings.sessionTrust,
            )
            val approved = if (policyCheck.autoApproved) {
                true
            } else {
                val pending = PendingApproval(call = call, deferred = CompletableDeferred())
                approvals += pending
                val ok = pending.deferred.await()
                approvals.remove(pending)
                ok
            }
            val result = if (!approved) {
                AiToolResult(callId = call.id, name = call.name, output = Strings.aiAgentRejected, isError = true)
            } else {
                executor.execute(context, call)
            }
            val localGeneratedFile = if (approved && !result.isError && localSessionId != null) {
                generatedFileForLocalTool(context, localSessionId, call)
            } else {
                null
            }
            localGeneratedFile?.let { fileOut ->
                val replacedPaths = if (call.name == AgentTools.ARCHIVE_CREATE.name) {
                    archiveSourceGeneratedPaths(runCatching { JSONObject(call.argsJson) }.getOrElse { JSONObject() })
                } else {
                    emptyList()
                }
                attachGeneratedFileToAssistant(transcript, streamingIndex, fileOut, replacedPaths)
            }
            // Bump the per-task write counter on any approved non-readOnly
            // tool call. Read-only calls (list_dir / read_file / search_repo /
            // …) are explicitly excluded so the limit reflects "destructive"
            // work, not "look around" work.
            val hitWriteLimit = if (approved && policyCheck.countsAsWrite) {
                writeCount += 1
                writeCount >= effectiveWriteLimit
            } else {
                false
            }
            // Accumulate the size of the tool result against the
            // per-task context budget. We cap each individual result
            // so a single mis-tooled call cannot blow the budget on
            // its own — the next iteration's [contextExhausted] check
            // catches the cumulative case.
            val (capped, _) = estimate?.fitToBudget(result.output)
                ?: (result.output to false)
            estimate?.addInput(capped.length)
            transcript += AgentEntry.ToolResult(
                if (capped === result.output) result
                else AiToolResult(
                    callId = result.callId,
                    name = result.name,
                    output = capped,
                    isError = result.isError,
                ),
            )
            results += AiMessage(
                role = "tool",
                content = capped,
                toolCallId = result.callId,
                toolName = result.name,
            )
            if (hitWriteLimit) {
                transcript += AgentEntry.Assistant(
                    text = "[write-limit: per-task write cap reached " +
                        "($writeCount / $effectiveWriteLimit). " +
                        "Stopping. Raise WRITE LIMIT PER TASK in settings to continue.]",
                )
                return
            }
            // Working-memory bookkeeping (BUGS_FIX.md Section 3 auto-update
            // logic). Counts of "calls since last working-memory update"
            // increment on every approved tool call; updates to
            // working_memory.md itself reset the counter to 0. After a
            // file-modifying call we queue a reminder for the NEXT turn
            // when more than 3 calls have elapsed OR the file is new for
            // this task. Only fires when approved AND when the caller
            // opted into reminders (working memory toggle ON).
            if (approved && workingMemoryReminders && workingMemoryRepo.isNotBlank()) {
                callsSinceWorkingMemoryUpdate += 1
                val isMemoryWriteToWorkingMemory = call.name in setOf("memory_write", "memory_append") &&
                    extractMemoryPathArg(call) == com.glassfiles.data.ai.AiAgentMemoryStore.WORKING_MEMORY_FILE
                val isFileEdit = call.name in setOf("write_file", "edit_file")
                if (isMemoryWriteToWorkingMemory) {
                    callsSinceWorkingMemoryUpdate = 0
                } else if (isFileEdit) {
                    val path = extractFilePathArg(call)
                    val isNewFile = path.isNotBlank() && filesEditedThisTask.add(path)
                    if (isNewFile || callsSinceWorkingMemoryUpdate > 3) {
                        val target = if (path.isNotBlank()) "`$path`" else "this file"
                        pendingWorkingMemoryReminder =
                            "[system] Update working memory: $target was just edited. " +
                                "Note your plan / done / next / decisions for it in working_memory.md " +
                                "before continuing with the next tool call."
                    }
                }
            }
        }
        // Approximate the assistant text against the running total too,
        // so the loop stops fairly even when the model just monologues.
        estimate?.addOutput(turn.assistantText.length)
        messages = messages + assistantMsg + results
        // Loop-end backstop on the iteration cap. Called here in
        // addition to the `repeat` count so a small estimate cap (eg
        // Eco's 15) wins even when [MAX_ITERATIONS] would still allow.
        if (estimate?.toolCallsExhausted == true) {
            transcript += AgentEntry.Assistant(
                text = "[cost-policy: per-task tool-call cap reached " +
                    "(${estimate.toolCallsExecuted} / ${estimate.limits.maxToolCalls}). " +
                    "Stopping. Switch to a higher cost mode if you need more iterations.]",
            )
            return
        }
    }
}

private const val MAX_ITERATIONS = 8
private const val AGENT_SESSION_MODE = "agent"

/**
 * Human-readable label for the user's WRITE LIMIT PER TASK setting. The
 * storage convention is `0` ⇒ unlimited (see
 * [com.glassfiles.data.ai.AiAgentApprovalPrefs.WRITE_LIMIT_UNLIMITED]); the
 * UI renders that as ∞ and so do we.
 */
private fun writeLimitLabel(limit: Int): String =
    if (limit <= com.glassfiles.data.ai.AiAgentApprovalPrefs.WRITE_LIMIT_UNLIMITED) "\u221E" else limit.toString()

/**
 * Best-effort extractor for the `path` argument of a memory_* tool call.
 * Returns the raw string after stripping surrounding quotes / whitespace.
 * Empty when the call doesn't carry a `path` arg or the JSON fails to
 * parse — both cases are treated as "not a working_memory.md update" by
 * the auto-update logic.
 */
private fun extractMemoryPathArg(call: com.glassfiles.data.ai.agent.AiToolCall): String =
    runCatching {
        org.json.JSONObject(call.argsJson).optString("path", "").trim()
    }.getOrDefault("")

/**
 * Best-effort extractor for the `path` argument of a write_file /
 * edit_file tool call. We try the conventional key names for both tools
 * because providers occasionally rename `file` to `path` and back.
 */
private fun extractFilePathArg(call: com.glassfiles.data.ai.agent.AiToolCall): String =
    runCatching {
        val obj = org.json.JSONObject(call.argsJson)
        listOf("path", "file", "filename", "filepath")
            .firstNotNullOfOrNull { key -> obj.optString(key, "").takeIf { it.isNotBlank() } }
            ?.trim()
            .orEmpty()
    }.getOrDefault("")

/**
 * C3 — plan-first preamble used when the per-repo plan-then-execute
 * toggle is on. We deliberately keep it short and prescriptive so the
 * model doesn't get tempted to call a tool on the first turn; the
 * "wait for the user" phrasing has been more reliable than vaguer
 * "ask first" in our smoke tests across providers.
 */
private const val AGENT_PLAN_FIRST_SYSTEM_PROMPT =
    "Plan-then-execute mode is enabled. On your very next turn, output " +
        "ONLY a short numbered plan describing what you will do — do NOT " +
        "call any tools yet, do NOT modify any files. Then stop and wait " +
        "for the user. Once the user replies (e.g. 'go', 'approve', or " +
        "any further instructions), proceed normally with tool calls."

/** Hard char-length budget for the rolling tool-use transcript. ~80 000
 * chars ≈ 20–25 k tokens, well under the smallest tool-use windows of
 * the providers we ship. */
private const val CONTEXT_COMPACT_CHARS = 80_000

/** Number of *most-recent* messages we always keep verbatim. Older
 * messages can be folded into a summary. 6 ≈ 3 user/assistant turns. */
private const val CONTEXT_KEEP_TAIL = 6

/**
 * If [messages] exceeds [CONTEXT_COMPACT_CHARS] in total payload size,
 * collapses everything before the last [CONTEXT_KEEP_TAIL] entries into
 * one summary assistant message generated by a one-shot non-tool [chat]
 * call. Falls through unchanged otherwise. Failures (no api key,
 * network) leave [messages] as-is — agent loop can still try the next
 * turn and hit a real provider error if it overflows.
 */
private suspend fun compactIfNeeded(
    provider: com.glassfiles.data.ai.providers.AiProvider,
    modelId: String,
    apiKey: String,
    context: android.content.Context,
    messages: List<AiMessage>,
): List<AiMessage> {
    val totalChars = messages.sumOf { it.content.length }
    if (totalChars <= CONTEXT_COMPACT_CHARS) return messages
    if (messages.size <= CONTEXT_KEEP_TAIL + 1) return messages
    val tail = messages.takeLast(CONTEXT_KEEP_TAIL)
    val head = messages.dropLast(CONTEXT_KEEP_TAIL)
    val summaryRequest = buildString {
        appendLine("Summarise the following AI-agent turns into a compact briefing for a continuing conversation.")
        appendLine("Preserve every file path mentioned, branch name, decision taken, and pending TODO.")
        appendLine("Drop greetings, restated requirements, and tool-call boilerplate.")
        appendLine("Aim for under 1500 characters.")
        appendLine()
        head.forEach { m ->
            appendLine("--- ${m.role}${m.toolName?.let { " [tool=$it]" } ?: ""}")
            appendLine(m.content.take(4000))
        }
    }
    val summary = runCatching {
        buildString {
            provider.chat(
                context = context,
                modelId = modelId,
                messages = listOf(AiMessage(role = "user", content = summaryRequest)),
                apiKey = apiKey,
                onChunk = { append(it) },
            )
        }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: return messages
    val compacted = AiMessage(
        role = "assistant",
        content = "[Earlier turns summarised:]\n$summary",
    )
    return listOf(compacted) + tail
}

// ─── Helpers / data ───────────────────────────────────────────────────────

/** One fallback target — model + already-resolved provider/key pair. */
private data class FallbackCandidate(
    val model: AiModel,
    val provider: com.glassfiles.data.ai.providers.AiProvider,
    val apiKey: String,
)

/**
 * Build the ordered fallback list used by [runAgentLoop] when the
 * active provider returns 429 / 5xx. We keep only models the user has
 * a key for, sort them with the same heuristic the initial picker
 * uses, and exclude the currently-selected model so we don't bounce
 * back to the failing one.
 */
private fun buildFallbackCandidates(
    context: android.content.Context,
    allModels: List<AiModel>,
    exclude: AiModel,
): List<FallbackCandidate> {
    val rank: (AiModel) -> Int = { m ->
        val id = m.id.lowercase()
        when {
            id.contains("gpt-4.1") || id.contains("gpt-4o") -> 0
            id.contains("claude-3.5") || id.contains("claude-sonnet") || id.contains("claude-opus") -> 1
            id.contains("gpt-4") -> 2
            id.contains("o1") || id.contains("o3") -> 3
            id.contains("claude") -> 4
            id.contains("grok") -> 5
            else -> 9
        }
    }
    return allModels
        .asSequence()
        .filter { it.uniqueKey != exclude.uniqueKey }
        .sortedBy(rank)
        .mapNotNull { m ->
            val key = AiKeyStore.getKey(context, m.providerId)
            if (key.isBlank()) null
            else FallbackCandidate(m, AiProviders.get(m.providerId), key)
        }
        .toList()
}

/** Heuristic: is this an HTTP error worth retrying on a different provider? */
private fun isFallbackEligibleError(t: Throwable): Boolean {
    val msg = t.message ?: return false
    // Provider HTTP errors are formatted as "<Name> HTTP <code>: <detail>"
    // by [Http.ensureOk] — we look for 429 (rate-limited) and 5xx
    // (server-side outage). Network-layer IOException without a code
    // also qualifies since the user's intent is "try something else".
    return msg.contains("HTTP 429") ||
        msg.contains("HTTP 500") ||
        msg.contains("HTTP 502") ||
        msg.contains("HTTP 503") ||
        msg.contains("HTTP 504") ||
        msg.contains("HTTP 529")
}

/**
 * Wrap one [AiProvider.chatWithToolsStreaming] call with provider
 * fallback. On a 429/5xx-style failure we drop to the next
 * [FallbackCandidate], notify the screen, and try again. The
 * [streamingIndex] entry is reset between attempts so the user sees
 * the new model start fresh instead of the previous half-streamed
 * text.
 */
private suspend fun callWithFallback(
    context: android.content.Context,
    tools: List<com.glassfiles.data.ai.agent.AiTool>,
    messages: List<AiMessage>,
    streamingIndex: Int,
    transcript: androidx.compose.runtime.snapshots.SnapshotStateList<AgentEntry>,
    remainingFallbacks: ArrayDeque<FallbackCandidate>,
    onFallback: (
        AiModel,
        com.glassfiles.data.ai.providers.AiProvider,
        String,
        String,
    ) -> Unit,
    provider: com.glassfiles.data.ai.providers.AiProvider,
    modelId: String,
    apiKey: String,
): com.glassfiles.data.ai.providers.AiToolTurn {
    var p = provider
    var id = modelId
    var k = apiKey
    while (true) {
        try {
            return p.chatWithToolsStreaming(
                context = context,
                modelId = id,
                messages = messages,
                tools = tools,
                apiKey = k,
                onTextDelta = { delta ->
                    if (streamingIndex in transcript.indices) {
                        val current = transcript[streamingIndex] as? AgentEntry.Assistant
                        if (current != null) {
                            transcript[streamingIndex] = current.copy(text = current.text + delta)
                        }
                    }
                },
            )
        } catch (e: Exception) {
            if (!isFallbackEligibleError(e) || remainingFallbacks.isEmpty()) throw e
            val next = remainingFallbacks.removeFirst()
            // Reset the streamed placeholder so the new model's text
            // doesn't append to whatever the failed attempt produced.
            if (streamingIndex in transcript.indices) {
                val current = transcript[streamingIndex] as? AgentEntry.Assistant
                if (current != null) {
                    transcript[streamingIndex] = current.copy(text = "")
                }
            }
            p = next.provider
            id = next.model.id
            k = next.apiKey
            onFallback(next.model, p, id, k)
        }
    }
}

private fun preferToolUseModel(models: List<AiModel>): AiModel? {
    val rank: (AiModel) -> Int = { m ->
        val id = m.id.lowercase()
        when {
            id.contains("gpt-4.1") || id.contains("gpt-4o") -> 0
            id.contains("claude-3.5") || id.contains("claude-sonnet") || id.contains("claude-opus") -> 1
            id.contains("gpt-4") -> 2
            id.contains("o1") || id.contains("o3") -> 3
            id.contains("claude") -> 4
            id.contains("grok") -> 5
            else -> 9
        }
    }
    return models.minByOrNull(rank)
}

private fun repoOwner(repo: GHRepo): String =
    repo.fullName.substringBefore('/').ifBlank { "" }

private fun repoName(repo: GHRepo): String =
    repo.fullName.substringAfter('/').ifBlank { repo.name }

private fun newAgentSessionId(): String = "agent_${System.currentTimeMillis()}"

private fun stopActiveAgent(
    job: Job?,
    approvals: androidx.compose.runtime.snapshots.SnapshotStateList<PendingApproval>,
) {
    job?.cancel()
    approvals.forEach { it.deferred.complete(false) }
    approvals.clear()
}

private fun encodeAgentImage(context: Context, uri: Uri): String? {
    return runCatching {
        val source = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: return@runCatching null
        val maxEdge = 1024
        val scale = (maxEdge.toFloat() / maxOf(source.width, source.height)).coerceAtMost(1f)
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(source, w, h, true) else source
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()
}

private fun decodeAgentBitmap(base64: String): Bitmap? = runCatching {
    val bytes = Base64.decode(base64, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

private fun List<AgentEntry>.toAiMessages(): List<AiMessage> =
    mapNotNull {
        when (it) {
            is AgentEntry.User -> AiMessage(
                role = "user",
                content = it.text,
                imageBase64 = it.imageBase64,
                fileContent = it.fileContent,
            )
            is AgentEntry.Assistant ->
                if (it.text.isBlank()) null else AiMessage(role = "assistant", content = it.text)
            is AgentEntry.ToolCall, is AgentEntry.ToolResult, is AgentEntry.Pending -> null
        }
    }

private fun List<AiChatSessionStore.Message>.toAgentEntries(): List<AgentEntry> =
    mapNotNull { message ->
        when (message.role) {
            "user" -> AgentEntry.User(message.content, message.imageBase64, message.fileContent)
            "assistant" -> AgentEntry.Assistant(message.content, generatedFiles = message.generatedFiles)
            else -> null
        }
    }

private const val CHAT_ONLY_REPO_KEY = "__chat_only__"
private const val CHAT_MEMORY_SCOPE_PREFIX = "chat/"
private val AGENT_TODO_SYSTEM_PROMPT: String = """
For multi-step work, maintain a short task checklist with todo_write and todo_update.
Use todo_write once you understand the task; keep exactly one item in_progress when actively working; mark items completed as soon as they are done.
Keep todo titles concise and user-visible. Do not use todos for single-answer questions.
""".trimIndent()

private val CHAT_ONLY_SYSTEM_PROMPT: String = """
${SystemPrompts.DEFAULT}

Chat-only mode is active. Do not assume access to any repository, branch, repo files, or GitHub tools. Use only chat messages, attachments, generated chat files, and any memory/system-prompt context explicitly provided by the app.

You may use local_* and archive_* tools inside the current chat workspace. Call file_picker_current_context when you need the temporary path of an attached file or archive.

When you need to send a file to the user, include it as a fenced block with an explicit file marker:
```file:relative/path.ext
file contents here
```
The app will turn that block into a clickable chat attachment. You can also use artifact_write or local_write_file to create a visible chat attachment.

Do not send archives as fenced file blocks. To send a zip/tar/7z/.gskill archive, write the source files in the local workspace, call archive_create, and then reply with a short note naming the archive.

For any long prompt, skill, template, code file, markdown document, or other output longer than about 2000 characters, create a chat attachment with artifact_write instead of writing the full content inline in the chat. After the tool succeeds, reply with a short note naming the file. Do not split long files across chat messages unless the user explicitly asks for inline text.
""".trimIndent()

private data class PendingAgentSend(
    val text: String,
    val image: String?,
    val file: AiPreparedAttachment?,
)

private enum class AgentTodoStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELED,
}

private data class AgentTodoItem(
    val id: String,
    val title: String,
    val status: AgentTodoStatus = AgentTodoStatus.PENDING,
)

private fun AiToolCall.isTodoTool(): Boolean =
    name == AgentTools.TODO_WRITE.name || name == AgentTools.TODO_UPDATE.name

private fun executeTodoTool(
    call: AiToolCall,
    todoItems: MutableList<AgentTodoItem>,
): AiToolResult {
    val args = runCatching { JSONObject(call.argsJson) }.getOrElse { JSONObject() }
    return runCatching {
        when (call.name) {
            AgentTools.TODO_WRITE.name -> {
                val arr = args.optJSONArray("items") ?: JSONArray()
                if (arr.length() == 0) error("todo_write: items must not be empty")
                val parsed = buildList {
                    for (i in 0 until arr.length().coerceAtMost(24)) {
                        val item = arr.optJSONObject(i) ?: continue
                        val title = item.optString("title").trim()
                        if (title.isBlank()) continue
                        add(
                            AgentTodoItem(
                                id = cleanTodoId(item.optString("id").ifBlank { title }, i),
                                title = title.take(120),
                                status = parseTodoStatus(item.optString("status")),
                            ),
                        )
                    }
                }.normalizedTodoProgress()
                if (parsed.isEmpty()) error("todo_write: no valid todo items")
                todoItems.clear()
                todoItems.addAll(parsed)
                "todo: ${todoProgressLabel(todoItems)}\n" + todoItems.joinToString("\n") { it.toToolLine() }
            }
            AgentTools.TODO_UPDATE.name -> {
                val updates = args.optJSONArray("items")?.let { arr ->
                    buildList {
                        for (i in 0 until arr.length().coerceAtMost(24)) {
                            arr.optJSONObject(i)?.let { add(it) }
                        }
                    }
                } ?: listOf(args)
                if (updates.isEmpty()) error("todo_update: no updates provided")
                updates.forEachIndexed { index, update ->
                    val rawId = update.optString("id").trim()
                    val title = update.optString("title").trim()
                    val status = parseTodoStatus(update.optString("status"))
                    if (rawId.isBlank() && title.isBlank()) error("todo_update: id or title is required")
                    val id = rawId.ifBlank { cleanTodoId(title, index) }
                    val existingIndex = todoItems.indexOfFirst { it.id == id }
                        .takeIf { it >= 0 }
                        ?: todoItems.indexOfFirst { title.isNotBlank() && it.title.equals(title, ignoreCase = true) }
                            .takeIf { it >= 0 }
                    if (existingIndex != null) {
                        val current = todoItems[existingIndex]
                        todoItems[existingIndex] = current.copy(
                            title = title.ifBlank { current.title }.take(120),
                            status = status,
                        )
                    } else {
                        todoItems += AgentTodoItem(
                            id = cleanTodoId(id.ifBlank { title }, todoItems.size),
                            title = title.ifBlank { id }.take(120),
                            status = status,
                        )
                    }
                }
                val normalized = todoItems.toList().normalizedTodoProgress()
                todoItems.clear()
                todoItems.addAll(normalized)
                "todo: ${todoProgressLabel(todoItems)}\n" + todoItems.joinToString("\n") { it.toToolLine() }
            }
            else -> error("unknown todo tool: ${call.name}")
        }
    }.fold(
        onSuccess = {
            AiToolResult(callId = call.id, name = call.name, output = it)
        },
        onFailure = {
            AiToolResult(
                callId = call.id,
                name = call.name,
                output = it.message ?: it.javaClass.simpleName,
                isError = true,
            )
        },
    )
}

private fun parseTodoStatus(raw: String): AgentTodoStatus =
    when (raw.trim().lowercase(Locale.US).replace("-", "_")) {
        "in_progress", "active", "doing", "current" -> AgentTodoStatus.IN_PROGRESS
        "completed", "complete", "done", "success" -> AgentTodoStatus.COMPLETED
        "canceled", "cancelled", "skipped", "blocked" -> AgentTodoStatus.CANCELED
        else -> AgentTodoStatus.PENDING
    }

private fun cleanTodoId(raw: String, fallbackIndex: Int): String {
    val clean = raw.lowercase(Locale.US)
        .replace(Regex("[^a-z0-9._-]+"), "-")
        .trim('-')
        .take(40)
    return clean.ifBlank { "todo-${fallbackIndex + 1}" }
}

private fun List<AgentTodoItem>.normalizedTodoProgress(): List<AgentTodoItem> {
    var hasActive = false
    return map { item ->
        if (item.status == AgentTodoStatus.IN_PROGRESS) {
            if (hasActive) item.copy(status = AgentTodoStatus.PENDING) else {
                hasActive = true
                item
            }
        } else {
            item
        }
    }
}

private fun todoProgressLabel(items: List<AgentTodoItem>): String {
    val total = items.count { it.status != AgentTodoStatus.CANCELED }
    val done = items.count { it.status == AgentTodoStatus.COMPLETED }
    return if (total > 0) "$done/$total" else "0/0"
}

private fun AgentTodoItem.toToolLine(): String =
    "${todoStatusMarker(status)} $id — $title"

private fun todoStatusMarker(status: AgentTodoStatus): String = when (status) {
    AgentTodoStatus.PENDING -> "[ ]"
    AgentTodoStatus.IN_PROGRESS -> "[>]"
    AgentTodoStatus.COMPLETED -> "[x]"
    AgentTodoStatus.CANCELED -> "[-]"
}

private data class ChatArtifactExecution(
    val result: AiToolResult,
    val generatedFile: AiChatSessionStore.GeneratedFile? = null,
    val replacedGeneratedPaths: List<String> = emptyList(),
)

private suspend fun executeChatOnlyTool(
    context: Context,
    call: AiToolCall,
    existingFiles: List<AiChatSessionStore.GeneratedFile>,
    localExecutor: LocalToolExecutor,
    sessionId: String,
): ChatArtifactExecution {
    val args = runCatching { JSONObject(call.argsJson) }.getOrElse { JSONObject() }
    return runCatching {
        when (call.name) {
            AgentTools.ARTIFACT_WRITE.name -> {
                val path = cleanGeneratedFileName(
                    args.optString("path").ifBlank { args.optString("name") },
                ) ?: error("artifact_write: path is required")
                val content = args.optString("content", "")
                val language = args.optString("language", "")
                    .ifBlank { languageForGeneratedFile(path, "") }
                val file = AiChatSessionStore.GeneratedFile(
                    name = path,
                    language = language,
                    content = content,
                )
                ChatArtifactExecution(
                    result = AiToolResult(
                        callId = call.id,
                        name = call.name,
                        output = "Created chat attachment $path (${content.length} chars). The file is visible to the user in the chat.",
                    ),
                    generatedFile = file,
                )
            }
            AgentTools.ARTIFACT_UPDATE.name -> {
                val path = cleanGeneratedFileName(
                    args.optString("path").ifBlank { args.optString("name") },
                ) ?: error("artifact_update: path is required")
                val current = existingFiles.lastOrNull { it.name == path }
                    ?: error("artifact_update: $path does not exist")
                val oldString = args.optString("old_string", "")
                val newString = args.optString("new_string", "")
                if (oldString.isEmpty()) error("artifact_update: old_string is required")
                val count = Regex.escape(oldString).toRegex().findAll(current.content).count()
                if (count != 1) error("artifact_update: old_string must appear exactly once, found $count")
                val updated = current.copy(content = current.content.replace(oldString, newString))
                ChatArtifactExecution(
                    result = AiToolResult(
                        callId = call.id,
                        name = call.name,
                        output = "Updated chat attachment $path (${updated.content.length} chars). The new version is visible to the user in the chat.",
                    ),
                    generatedFile = updated,
                )
            }
            else -> {
                if (!AgentTools.isChatRuntimeTool(call.name)) {
                    error("Unknown chat tool: ${call.name}")
                }
                val result = localExecutor.execute(context, call)
                val generatedFile = if (!result.isError) {
                    when (call.name) {
                        AgentTools.LOCAL_WRITE_FILE.name -> {
                            val rawPath = args.optString("path")
                            val path = cleanGeneratedFileName(
                                if (rawPath.startsWith("/")) rawPath.substringAfterLast('/') else rawPath,
                            )
                            val content = args.optString("content", "")
                            path?.let {
                                AiChatSessionStore.GeneratedFile(
                                    name = it,
                                    language = languageForGeneratedFile(it, ""),
                                    content = content,
                                )
                            }
                        }
                        AgentTools.ARCHIVE_CREATE.name -> generatedArchiveFile(context, sessionId, args)
                        else -> null
                    }
                } else {
                    null
                }
                ChatArtifactExecution(
                    result = result,
                    generatedFile = generatedFile,
                    replacedGeneratedPaths = if (generatedFile != null && call.name == AgentTools.ARCHIVE_CREATE.name) {
                        archiveSourceGeneratedPaths(args)
                    } else {
                        emptyList()
                    },
                )
            }
        }
    }.getOrElse { error ->
        ChatArtifactExecution(
            result = AiToolResult(
                callId = call.id,
                name = call.name,
                output = error.message ?: error.javaClass.simpleName,
                isError = true,
            ),
        )
    }
}

private fun currentChatArtifacts(
    transcript: List<AgentEntry>,
): List<AiChatSessionStore.GeneratedFile> =
    transcript
        .filterIsInstance<AgentEntry.Assistant>()
        .flatMap { it.generatedFiles }

private fun generatedArchiveFile(
    context: Context,
    sessionId: String,
    args: JSONObject,
): AiChatSessionStore.GeneratedFile? {
    val destination = args.optString("destination", "").trim()
    if (destination.isBlank()) return null
    val archive = runCatching {
        val raw = File(destination)
        val file = if (raw.isAbsolute) {
            raw.takeIf { it.isFile }
                ?: File(LocalToolExecutor.ensureSessionWorkspace(context, sessionId), raw.name)
        } else {
            File(LocalToolExecutor.ensureSessionWorkspace(context, sessionId), destination)
        }.canonicalFile
        file.takeIf { it.isFile && isGeneratedArchiveName(it.name) }
    }.getOrNull() ?: return null
    val name = cleanGeneratedFileName(archive.name) ?: return null
    return AiChatSessionStore.GeneratedFile(
        name = name,
        language = languageForGeneratedFile(name, ""),
        content = "",
        sourcePath = archive.absolutePath,
    )
}

private fun generatedFileForLocalTool(
    context: Context,
    sessionId: String,
    call: AiToolCall,
): AiChatSessionStore.GeneratedFile? {
    val args = runCatching { JSONObject(call.argsJson) }.getOrElse { JSONObject() }
    return when (call.name) {
        AgentTools.LOCAL_WRITE_FILE.name -> {
            val rawPath = args.optString("path")
            val path = cleanGeneratedFileName(
                if (rawPath.startsWith("/")) rawPath.substringAfterLast('/') else rawPath,
            )
            val content = args.optString("content", "")
            path?.let {
                AiChatSessionStore.GeneratedFile(
                    name = it,
                    language = languageForGeneratedFile(it, ""),
                    content = content,
                )
            }
        }
        AgentTools.ARCHIVE_CREATE.name -> generatedArchiveFile(context, sessionId, args)
        else -> null
    }
}

private fun attachGeneratedFileToAssistant(
    transcript: MutableList<AgentEntry>,
    assistantIndex: Int,
    file: AiChatSessionStore.GeneratedFile,
    replacedPaths: List<String>,
) {
    if (replacedPaths.isNotEmpty()) {
        transcript.indices.forEach { index ->
            val entry = transcript[index] as? AgentEntry.Assistant ?: return@forEach
            val visibleFiles = removeGeneratedFilesForArchive(entry.generatedFiles, replacedPaths, file.name)
            if (visibleFiles.size != entry.generatedFiles.size) {
                transcript[index] = entry.copy(generatedFiles = visibleFiles)
            }
        }
    }
    val current = transcript.getOrNull(assistantIndex) as? AgentEntry.Assistant
    if (current != null) {
        val visibleFiles = removeGeneratedFilesForArchive(current.generatedFiles, replacedPaths, file.name)
        transcript[assistantIndex] = current.copy(
            generatedFiles = mergeGeneratedFiles(visibleFiles, listOf(file)),
        )
    } else {
        transcript += AgentEntry.Assistant(text = "", generatedFiles = listOf(file))
    }
}

private fun archiveSourceGeneratedPaths(args: JSONObject): List<String> {
    val arr = args.optJSONArray("source_paths") ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            archiveSourceGeneratedPath(arr.optString(i))?.let { add(it) }
        }
    }.distinct()
}

private fun archiveSourceGeneratedPath(raw: String): String? {
    val normalized = raw.trim().replace('\\', '/')
    val clean = normalized.removePrefix("./").trim('/')
    if (clean.isBlank() || clean == ".") return ""
    val displayPath = if (File(normalized).isAbsolute) normalized.substringAfterLast('/') else clean
    return cleanGeneratedFileName(displayPath)
}

private fun removeGeneratedFilesForArchive(
    files: List<AiChatSessionStore.GeneratedFile>,
    sourcePaths: List<String>,
    archiveName: String,
): List<AiChatSessionStore.GeneratedFile> {
    if (sourcePaths.isEmpty()) return files
    if (sourcePaths.any { it.isBlank() }) return files.filter { it.name == archiveName }
    return files.filter { file ->
        file.name == archiveName || sourcePaths.none { source ->
            file.name == source || file.name.startsWith("$source/")
        }
    }
}

private fun mergeGeneratedFiles(
    existing: List<AiChatSessionStore.GeneratedFile>,
    incoming: List<AiChatSessionStore.GeneratedFile>,
): List<AiChatSessionStore.GeneratedFile> {
    if (incoming.isEmpty()) return existing
    val merged = existing.toMutableList()
    incoming.forEach { file ->
        val idx = merged.indexOfLast { it.name == file.name }
        if (idx >= 0) merged[idx] = file else merged += file
    }
    return merged
}

private fun attachmentDisplayText(text: String, file: AiPreparedAttachment?): String =
    text.ifBlank { file?.let { "Analyze ${it.name}" }.orEmpty() }

private fun AgentEntry.stableKey(): String = when (this) {
    is AgentEntry.User -> "u:${text.hashCode()}:${imageBase64?.hashCode() ?: 0}:${fileContent?.hashCode() ?: 0}:${System.identityHashCode(this)}"
    is AgentEntry.Assistant -> "a:$id"
    is AgentEntry.ToolCall -> "tc:${call.id}"
    is AgentEntry.ToolResult -> "tr:${result.callId}"
    is AgentEntry.Pending -> "pending:${pending.call.id}"
}

private fun AgentDisplayEntry.stableKey(): String = when (this) {
    is AgentDisplayEntry.Raw -> entry.stableKey()
    is AgentDisplayEntry.ToolAction -> "ta:${call.id}"
}

private val AgentDisplayEntry.isToolAction: Boolean
    get() = this is AgentDisplayEntry.ToolAction ||
        (this is AgentDisplayEntry.Raw && entry is AgentEntry.Pending)

private fun List<AgentEntry>.toDisplayEntries(): List<AgentDisplayEntry> {
    val resultByCallId = filterIsInstance<AgentEntry.ToolResult>()
        .associateBy { it.result.callId }
    val pairedResultIds = mutableSetOf<String>()
    return mapNotNull { entry ->
        when (entry) {
            is AgentEntry.ToolCall -> {
                val result = resultByCallId[entry.call.id]?.result
                if (result != null) pairedResultIds += result.callId
                AgentDisplayEntry.ToolAction(entry.call, result)
            }
            is AgentEntry.ToolResult -> {
                if (entry.result.callId in pairedResultIds) null else AgentDisplayEntry.Raw(entry)
            }
            else -> AgentDisplayEntry.Raw(entry)
        }
    }
}

private sealed class AgentDisplayEntry {
    data class Raw(val entry: AgentEntry) : AgentDisplayEntry()
    data class ToolAction(val call: AiToolCall, val result: AiToolResult?) : AgentDisplayEntry() {
        val id: String = call.id
    }
}

private sealed class AgentEntry {
    data class User(
        val text: String,
        val imageBase64: String? = null,
        val fileContent: String? = null,
    ) : AgentEntry()
    /** [id] keeps the LazyList key stable across streaming text updates;
     * each replacement keeps the same id via `copy(text = ...)`. */
    data class Assistant(
        val text: String,
        val id: String = newAssistantId(),
        val generatedFiles: List<AiChatSessionStore.GeneratedFile> = emptyList(),
    ) : AgentEntry()
    data class ToolCall(val call: AiToolCall) : AgentEntry()
    data class ToolResult(val result: AiToolResult) : AgentEntry()
    data class Pending(val pending: PendingApproval) : AgentEntry()
}

private fun newAssistantId(): String = "a_${System.nanoTime()}"

private fun extractAgentGeneratedFiles(text: String): List<AiChatSessionStore.GeneratedFile> {
    if (!text.contains("```")) return emptyList()
    val result = mutableListOf<AiChatSessionStore.GeneratedFile>()
    val fence = Regex("```([^\\n`]*)\\n([\\s\\S]*?)```")
    for (match in fence.findAll(text)) {
        val info = match.groupValues[1].trim()
        var body = match.groupValues[2].trimEnd()
        val firstLine = body.lineSequence().firstOrNull().orEmpty()
        val markerFromInfo = fileNameFromFenceInfo(info)
        val markerFromBody = fileNameFromMarkerLine(firstLine)
        val name = markerFromInfo ?: markerFromBody ?: continue
        if (markerFromBody != null) {
            body = body.lines().drop(1).joinToString("\n").trimEnd()
        }
        if (body.isBlank()) continue
        val language = languageForGeneratedFile(name, info)
        result += AiChatSessionStore.GeneratedFile(
            name = name.take(160),
            language = language,
            content = body,
        )
        if (result.size >= 12) break
    }
    return result
}

private fun stripAgentGeneratedFileBlocks(text: String): String {
    if (!text.contains("```")) return text
    val fence = Regex("```([^\\n`]*)\\n([\\s\\S]*?)```")
    val withoutClosedBlocks = fence.replace(text) { match ->
        val info = match.groupValues[1].trim()
        val body = match.groupValues[2].trimEnd()
        val firstLine = body.lineSequence().firstOrNull().orEmpty()
        val isGeneratedFile = fileNameFromFenceInfo(info) != null || fileNameFromMarkerLine(firstLine) != null
        if (isGeneratedFile) "" else match.value
    }
    val fenceCount = Regex("```").findAll(withoutClosedBlocks).count()
    val openFenceStart = withoutClosedBlocks.lastIndexOf("```")
    val withoutOpenFileBlock = if (fenceCount % 2 == 1 && openFenceStart >= 0) {
        val openBlock = withoutClosedBlocks.substring(openFenceStart + 3)
        val info = openBlock.substringBefore('\n', missingDelimiterValue = openBlock).trim()
        val body = openBlock.substringAfter('\n', missingDelimiterValue = "")
        val firstLine = body.lineSequence().firstOrNull().orEmpty()
        val isGeneratedFile = fileNameFromFenceInfo(info) != null || fileNameFromMarkerLine(firstLine) != null
        if (isGeneratedFile) withoutClosedBlocks.substring(0, openFenceStart) else withoutClosedBlocks
    } else {
        withoutClosedBlocks
    }
    return withoutOpenFileBlock.replace(Regex("\n{3,}"), "\n\n").trim()
}

private fun fileNameFromFenceInfo(info: String): String? {
    if (info.isBlank()) return null
    val filePrefix = Regex("""(?i)^(?:file|path|filename)\s*:\s*(\S+)""")
        .find(info)
        ?.groupValues
        ?.getOrNull(1)
    if (!filePrefix.isNullOrBlank()) return cleanGeneratedFileName(filePrefix)
    val pathProp = Regex("""(?i)(?:path|file|filename)=["']?([^"'\s]+)""")
        .find(info)
        ?.groupValues
        ?.getOrNull(1)
    if (!pathProp.isNullOrBlank()) return cleanGeneratedFileName(pathProp)
    return info
        .split(Regex("\\s+"))
        .firstOrNull { it.contains('.') || it.contains('/') }
        ?.let(::cleanGeneratedFileName)
}

private fun fileNameFromMarkerLine(line: String): String? {
    val match = Regex("""^\s*(?://|#|/\*+|<!--|--|;)?\s*(?:file|path|filename)\s*:\s*([^*>\s]+)""", RegexOption.IGNORE_CASE)
        .find(line)
        ?: return null
    return cleanGeneratedFileName(match.groupValues[1])
}

private fun cleanGeneratedFileName(raw: String): String? {
    val cleaned = raw
        .trim()
        .trim('"', '\'', '`')
        .replace('\\', '/')
        .removePrefix("./")
    if (cleaned.isBlank() || cleaned.endsWith("/")) return null
    if (cleaned.contains("..")) return null
    return cleaned
}

private fun languageForGeneratedFile(name: String, info: String): String {
    val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
    if (ext.isNotBlank()) return ext
    val lang = info.substringBefore(' ').substringBefore(':').trim()
    return lang.takeIf { it.isNotBlank() && it != "file" && it != "path" && it != "filename" }.orEmpty()
}

private data class PendingApproval(
    val call: AiToolCall,
    val deferred: CompletableDeferred<Boolean>,
)
