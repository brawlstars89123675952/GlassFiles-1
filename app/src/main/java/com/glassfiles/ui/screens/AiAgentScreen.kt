package com.glassfiles.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.widget.Toast
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.data.ai.AiChatSessionStore
import com.glassfiles.data.ai.AiAgentApprovalPrefs
import com.glassfiles.data.ai.AiAgentMemoryPrefs
import com.glassfiles.data.ai.AiAgentMemoryStore
import com.glassfiles.data.ai.AiKeyStore
import com.glassfiles.data.ai.ModelRegistry
import com.glassfiles.data.ai.agent.AiAgentApprovalCategory
import com.glassfiles.data.ai.agent.AiAgentApprovalCheck
import com.glassfiles.data.ai.agent.AiAgentApprovalPolicy
import com.glassfiles.data.ai.agent.AiAgentApprovalSettings
import com.glassfiles.data.ai.agent.AgentTools
import com.glassfiles.data.ai.agent.AiToolCall
import com.glassfiles.data.ai.agent.AiToolResult
import com.glassfiles.data.ai.agent.GitHubToolExecutor
import com.glassfiles.data.ai.agent.LineDiff
import com.glassfiles.data.ai.models.AiCapability
import com.glassfiles.data.ai.models.AiMessage
import com.glassfiles.data.ai.models.AiModel
import com.glassfiles.data.ai.models.AiProviderId
import com.glassfiles.data.ai.providers.AiProviders
import com.glassfiles.data.github.GHRepo
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.data.github.canWrite
import com.glassfiles.ui.components.AiPickerChip
import com.glassfiles.service.AgentProgress
import com.glassfiles.service.AgentTask
import com.glassfiles.service.AiAgentService
import com.glassfiles.ui.screens.ai.ExpensiveActionWarningDialog
import com.glassfiles.ui.theme.JetBrainsMono
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = MaterialTheme.colorScheme
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
    // Per-repo system-prompt override editor visibility. Opens an
    // AlertDialog with a multi-line text field; saves to AiAgentPrefs
    // on confirm so subsequent runs in this repo prepend it as a
    // `system` message.
    var showSystemPrompt by remember { mutableStateOf(false) }
    // D2 — pending resumable run, if any. Read whenever
    // [activeSessionId] changes; once the user resumes/discards we set
    // it back to null without writing anything until the next launch.
    var pendingResume by remember {
        mutableStateOf<com.glassfiles.data.ai.AiAgentResumeStore.Pending?>(null)
    }

    val transcript = remember { mutableStateListOf<AgentEntry>() }
    var input by remember { mutableStateOf(TextFieldValue(initialPrompt.orEmpty())) }
    var pendingImage by remember { mutableStateOf<String?>(null) }
    var autoApproveReads by remember { mutableStateOf(AiAgentApprovalPrefs.getAutoApproveReads(context)) }
    var autoApproveEdits by remember { mutableStateOf(AiAgentApprovalPrefs.getAutoApproveEdits(context)) }
    var autoApproveWrites by remember { mutableStateOf(AiAgentApprovalPrefs.getAutoApproveWrites(context)) }
    var autoApproveCommits by remember { mutableStateOf(AiAgentApprovalPrefs.getAutoApproveCommits(context)) }
    var autoApproveDestructive by remember { mutableStateOf(AiAgentApprovalPrefs.getAutoApproveDestructive(context)) }
    var yoloMode by remember { mutableStateOf(AiAgentApprovalPrefs.getYoloMode(context)) }
    var yoloConfirmed by remember { mutableStateOf(false) }
    var pendingYoloConfirm by remember { mutableStateOf(false) }
    var sessionTrustEnabled by remember { mutableStateOf(AiAgentApprovalPrefs.getSessionTrust(context)) }
    var writeLimitPerTask by remember { mutableStateOf(AiAgentApprovalPrefs.getWriteLimit(context)) }
    var backgroundExecution by remember { mutableStateOf(AiAgentApprovalPrefs.getBackgroundExecution(context)) }
    var keepCpuAwake by remember { mutableStateOf(AiAgentApprovalPrefs.getKeepCpuAwake(context)) }
    var memoryProjectKnowledge by remember { mutableStateOf(AiAgentMemoryPrefs.getProjectKnowledge(context)) }
    var memoryUserPreferences by remember { mutableStateOf(AiAgentMemoryPrefs.getUserPreferences(context)) }
    var memoryChatSummaries by remember { mutableStateOf(AiAgentMemoryPrefs.getChatSummaries(context)) }
    var memorySemanticSearch by remember { mutableStateOf(AiAgentMemoryPrefs.getSemanticSearch(context)) }
    var showMemoryFiles by remember { mutableStateOf(false) }
    var memoryFiles by remember { mutableStateOf<List<AiAgentMemoryStore.MemoryFile>>(emptyList()) }
    var protectedPathsText by remember {
        mutableStateOf(AiAgentApprovalPrefs.getProtectedPaths(context).joinToString("\n"))
    }
    var sessionTrustGranted by remember { mutableStateOf(false) }
    var consecutiveAutoApprovals by remember { mutableStateOf(0) }
    var writesThisTask by remember { mutableStateOf(0) }
    val fileEditCounts = remember { mutableStateMapOf<String, Int>() }
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
    var pendingWarningInput by remember { mutableStateOf<Pair<String, String?>?>(null) }

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

    fun resetApprovalSessionState() {
        sessionTrustGranted = false
        consecutiveAutoApprovals = 0
        writesThisTask = 0
        fileEditCounts.clear()
    }

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
            transcript.addAll(session.messages.toAgentEntries())
            // Pre-seed the desired branch so the branch-loading effect
            // can honour it once the repo is picked.
            if (session.branch.isNotBlank()) selectedBranch = session.branch
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
        if (selectedRepo == null && session.repoFullName.isNotBlank()) {
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
        val repoDone = session.repoFullName.isBlank() ||
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

    LaunchedEffect(Unit) {
        yoloConfirmed = AiAgentApprovalPrefs.isYoloModeConfirmed(context)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────
    fun refreshSessions() {
        sessions = AiChatSessionStore.list(context, AGENT_SESSION_MODE)
    }

    fun transcriptMessages(): List<AiChatSessionStore.Message> = transcript.mapNotNull { entry ->
        when (entry) {
            is AgentEntry.User -> AiChatSessionStore.Message(
                role = "user",
                content = entry.text,
                imageBase64 = entry.imageBase64,
            )
            is AgentEntry.Assistant -> if (entry.text.isBlank()) null else AiChatSessionStore.Message(
                role = "assistant",
                content = entry.text,
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
                repoFullName = selectedRepo?.fullName.orEmpty(),
                branch = selectedBranch.orEmpty(),
            ),
        )
        selectedRepo?.fullName
            ?.takeIf {
                it.isNotBlank() &&
                    (AiAgentMemoryPrefs.getProjectKnowledge(context) ||
                        AiAgentMemoryPrefs.getUserPreferences(context) ||
                        AiAgentMemoryPrefs.getChatSummaries(context) ||
                        AiAgentMemoryPrefs.getSemanticSearch(context))
            }
            ?.let { repoFull ->
            AiAgentMemoryStore.saveChatFull(context, repoFull, id, messages)
        }
        refreshSessions()
    }

    fun requestYoloConfirm() {
        pendingYoloConfirm = true
    }

    fun applyYoloPreset() {
        autoApproveReads = true
        autoApproveEdits = true
        autoApproveWrites = true
        autoApproveCommits = true
        yoloMode = true
        AiAgentApprovalPrefs.setAutoApproveReads(context, true)
        AiAgentApprovalPrefs.setAutoApproveEdits(context, true)
        AiAgentApprovalPrefs.setAutoApproveWrites(context, true)
        AiAgentApprovalPrefs.setAutoApproveCommits(context, true)
        AiAgentApprovalPrefs.setYoloMode(context, true)
        transcript += AgentEntry.Assistant(
            "[system: YOLO mode enabled. Agent will skip approval for most actions.]",
        )
        persistSession()
        Toast.makeText(
            context,
            "YOLO mode enabled. Agent will not ask for most actions.",
            Toast.LENGTH_LONG,
        ).show()
        scope.launch {
            AiAgentApprovalPrefs.setYoloModeConfirmed(context, true)
            yoloConfirmed = true
        }
    }

    fun updateAutoApproveToggles(
        reads: Boolean = autoApproveReads,
        edits: Boolean = autoApproveEdits,
        writes: Boolean = autoApproveWrites,
        commits: Boolean = autoApproveCommits,
    ) {
        if (!yoloMode && reads && edits && writes && commits) {
            requestYoloConfirm()
            return
        }
        autoApproveReads = reads
        autoApproveEdits = edits
        autoApproveWrites = writes
        autoApproveCommits = commits
        AiAgentApprovalPrefs.setAutoApproveReads(context, reads)
        AiAgentApprovalPrefs.setAutoApproveEdits(context, edits)
        AiAgentApprovalPrefs.setAutoApproveWrites(context, writes)
        AiAgentApprovalPrefs.setAutoApproveCommits(context, commits)
    }

    fun openSession(session: AiChatSessionStore.Session) {
        stopActiveAgent(runJob, approvals)
        runJob = null
        running = false
        error = null
        input = TextFieldValue("")
        pendingImage = null
        activeSessionId = session.id
        sessionCreatedAt = session.createdAt
        transcript.clear()
        transcript.addAll(session.messages.toAgentEntries())
        approvals.clear()
        resetApprovalSessionState()
        // Reset selection so the restore effect picks up this session's
        // repo / branch / model. The branch is pre-seeded so the
        // branches-loading effect can honour it.
        selectedRepo = null
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
        activeSessionId = newAgentSessionId()
        sessionCreatedAt = System.currentTimeMillis()
        transcript.clear()
        approvals.clear()
        resetApprovalSessionState()
        // Don't drop repo / branch / model — keep the user's working
        // context so they can fire off another task in the same repo
        // without re-picking everything.
        showHistory = false
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                pendingImage = withContext(Dispatchers.IO) {
                    encodeAgentImage(context, uri)
                }
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted && !AiAgentApprovalPrefs.getNotificationDeniedNoticeShown(context)) {
            transcript += AgentEntry.Assistant(
                "Notifications are disabled. Agent can still work in background, but you won't see progress updates in notification.",
            )
            AiAgentApprovalPrefs.setNotificationDeniedNoticeShown(context, true)
            persistSession()
        }
    }

    fun stop() {
        runJob?.cancel()
        if (backgroundExecution) {
            AiAgentService.stop("Stopped by user")
        }
        runJob = null
        running = false
        approvals.forEach { it.deferred.complete(false) }
        approvals.clear()
        resetApprovalSessionState()
    }

    DisposableEffect(lifecycleOwner, backgroundExecution, running) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && running && !backgroundExecution) {
                stop()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    /**
     * Internal: actually start the agent loop. Assumes input has been
     * sanitised and (if applicable) the warning dialog has been
     * resolved by the user. Always called from `submit` directly or
     * from the warning dialog's "Continue" button.
     */
    fun runTaskInternal(text: String, image: String?) {
        val repo = selectedRepo ?: return
        val branch = selectedBranch ?: return
        val model = selectedModel ?: return
        val key = AiKeyStore.getKey(context, model.providerId)
        if (key.isBlank()) {
            error = Strings.aiAgentNoModels
            return
        }
        error = null
        fallbackNotice = null
        input = TextFieldValue("")
        pendingImage = null
        resetApprovalSessionState()
        transcript += AgentEntry.User(text, image)
        persistSession()
        if (backgroundExecution && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
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
        if (backgroundExecution) {
            AiAgentService.begin(
                context = context,
                task = AgentTask(
                    repo = repo.fullName,
                    branch = branch,
                    prompt = text.take(120),
                ),
                keepCpuAwake = keepCpuAwake,
                onCancel = { stop() },
            )
        }

        runJob = scope.launch {
            var completedNormally = false
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
            val executor = GitHubToolExecutor(
                owner = repoOwner(repo),
                repo = repoName(repo),
                branch = branch,
                estimate = estimate,
                initialCache = warmCache,
            )
            val provider = AiProviders.get(model.providerId)
            // Filter destructive tools out of the schema sent to the model
            // when the active repo is read-only — without it, the model
            // would happily emit `write_file` / `commit` calls that just
            // 403 in the executor and waste a turn.
            val tools = if (repo.canWrite()) {
                AgentTools.ALL
            } else {
                AgentTools.ALL.filter { it.readOnly }
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
            val memoryPrompt = AiAgentMemoryStore
                .buildMemoryPrompt(context, repo.fullName)
                .takeIf { it.isNotBlank() }
            val baseMessages = transcript.toAiMessages()
            val systemMessages = buildList {
                if (memoryPrompt != null) {
                    add(AiMessage(role = "system", content = memoryPrompt))
                }
                if (systemOverride != null) {
                    add(AiMessage(role = "system", content = systemOverride))
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
            try {
                runAgentLoop(
                    seedMessages = seed,
                    initialProvider = provider,
                    initialModelId = model.id,
                    initialApiKey = key,
                    tools = tools,
                    executor = executor,
                    transcript = transcript,
                    approvals = approvals,
                    approvalSettings = AiAgentApprovalSettings(
                        autoApproveReads = autoApproveReads,
                        autoApproveEdits = autoApproveEdits,
                        autoApproveWrites = autoApproveWrites,
                        autoApproveCommits = autoApproveCommits,
                        autoApproveDestructive = autoApproveDestructive,
                        yoloMode = yoloMode,
                        sessionTrust = sessionTrustEnabled,
                        writeLimitPerTask = writeLimitPerTask,
                        protectedPaths = protectedPathsText.lineSequence()
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .toList(),
                        activeBranch = branch,
                    ),
                    isSessionTrusted = { sessionTrustGranted },
                    onSessionTrustGranted = { sessionTrustGranted = true },
                    consecutiveAutoApprovals = { consecutiveAutoApprovals },
                    setConsecutiveAutoApprovals = { consecutiveAutoApprovals = it },
                    writesThisTask = { writesThisTask },
                    setWritesThisTask = { writesThisTask = it },
                    fileEditCounts = fileEditCounts,
                    context = context,
                    fallbackCandidates = fallbacks,
                    estimate = estimate,
                    onFallback = { newModel ->
                        // Reflect the swap in the UI so the user
                        // immediately sees which model is now in
                        // charge — and so cost / token meter switches
                        // to the new pricing rate.
                        selectedModel = newModel
                        fallbackNotice = Strings.aiAgentFallbackToast
                            .replace("{model}", newModel.displayName)
                    },
                    onToolStatus = { status, current, total ->
                        if (backgroundExecution) {
                            AiAgentService.update(status, AgentProgress(current, total))
                        }
                    },
                )
                completedNormally = true
            } catch (e: CancellationException) {
                if (backgroundExecution) {
                    AiAgentService.stop("Stopped by user")
                }
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
                if (backgroundExecution) {
                    AiAgentService.complete("Task failed: ${error.orEmpty().take(80)}")
                }
            } finally {
                persistSession()
                val finalMessages = transcriptMessages()
                if (finalMessages.isNotEmpty() &&
                    (AiAgentMemoryPrefs.getChatSummaries(context) || AiAgentMemoryPrefs.getProjectKnowledge(context))
                ) {
                    scope.launch(Dispatchers.IO) {
                        AiAgentMemoryStore.summarizeAndUpdate(
                            context = context,
                            repoFullName = repo.fullName,
                            chatId = activeSessionId,
                            messages = finalMessages,
                            provider = provider,
                            modelId = model.id,
                            apiKey = key,
                        )
                    }
                }
                running = false
                runJob = null
                if (backgroundExecution && completedNormally) {
                    AiAgentService.complete("Task completed")
                }
                resetApprovalSessionState()
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
                    val readToolNames = setOf(
                        com.glassfiles.data.ai.agent.AgentTools.LIST_DIR.name,
                        com.glassfiles.data.ai.agent.AgentTools.READ_FILE.name,
                        com.glassfiles.data.ai.agent.AgentTools.READ_FILE_RANGE.name,
                        com.glassfiles.data.ai.agent.AgentTools.SEARCH_REPO.name,
                        com.glassfiles.data.ai.agent.AgentTools.LIST_BRANCHES.name,
                        com.glassfiles.data.ai.agent.AgentTools.COMPARE_REFS.name,
                        com.glassfiles.data.ai.agent.AgentTools.LIST_PULLS.name,
                        com.glassfiles.data.ai.agent.AgentTools.READ_PR.name,
                        com.glassfiles.data.ai.agent.AgentTools.LIST_ISSUES.name,
                        com.glassfiles.data.ai.agent.AgentTools.READ_ISSUE.name,
                        com.glassfiles.data.ai.agent.AgentTools.READ_CHECK_RUNS.name,
                        com.glassfiles.data.ai.agent.AgentTools.READ_WORKFLOW_RUN.name,
                    )
                    val writeToolNames = setOf(
                        com.glassfiles.data.ai.agent.AgentTools.EDIT_FILE.name,
                        com.glassfiles.data.ai.agent.AgentTools.WRITE_FILE.name,
                        com.glassfiles.data.ai.agent.AgentTools.COMMIT.name,
                        com.glassfiles.data.ai.agent.AgentTools.OPEN_PR.name,
                    )
                    val toolCalls = transcript.filterIsInstance<AgentEntry.ToolCall>()
                    val readCalls = toolCalls.count { it.call.name in readToolNames }
                    val writeCalls = toolCalls.count { it.call.name in writeToolNames }
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
    fun submit(userText: String, imageBase64: String?) {
        val text = userText.trim()
        val image = imageBase64?.takeIf { selectedModel?.let { m -> AiCapability.VISION in m.capabilities } == true }
        if (text.isEmpty() && image == null) return
        if (running) {
            error = "Agent is busy, finish current task first."
            return
        }
        val repo = selectedRepo ?: return
        val branch = selectedBranch ?: return
        val model = selectedModel ?: return
        // Approximate context size = current transcript char count +
        // pending user text. This is what gets shipped to the provider
        // on the very next call, so it's the most honest number we
        // can show to the user without a real token-counter.
        val approxContext: Int = text.length + transcript.sumOf { entry ->
            when (entry) {
                is AgentEntry.User -> entry.text.length
                is AgentEntry.Assistant -> entry.text.length
                is AgentEntry.ToolCall -> entry.call.argsJson.length
                is AgentEntry.ToolResult -> entry.result.output.length
                is AgentEntry.Pending -> 0
            }
        }
        val limits = com.glassfiles.data.ai.cost.AiCostPolicy.limitsFor(costMode)
        val remembered = com.glassfiles.data.ai.cost.AiCostModeStore.isRemembered(
            context, repo.fullName, model.providerId.name,
        )
        val warningReason: com.glassfiles.ui.screens.ai.ExpensiveActionReason? = when {
            // Private repo + no remembered exception → always warn.
            // The flag is per (repo, provider) so a "remember" for the
            // current provider doesn't leak to a different one.
            repo.isPrivate && !remembered ->
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
                repoFullName = repo.fullName,
                branch = branch,
                providerLabel = model.providerId.displayName,
                modelLabel = model.displayName,
                approxFiles = transcript.count { it is AgentEntry.ToolResult },
                approxContextChars = approxContext,
                isPrivate = repo.isPrivate,
                reason = warningReason,
            )
            pendingWarningInput = text to image
            return
        }
        runTaskInternal(text, image)
    }

    // ─── UI (terminal mode) ────────────────────────────────────────────────
    // Terminal-mode local state. These flags only drive the agent-screen
    // chrome; they are intentionally NOT persisted to the cost / chat
    // store because they are pure presentation toggles.
    var showSettings by remember { mutableStateOf(false) }
    var instantRender by remember { mutableStateOf(false) }
    val terminalColors = com.glassfiles.ui.screens.ai.terminal.AgentTerminalDarkColors

    com.glassfiles.ui.screens.ai.terminal.AgentTerminalSurface(colors = terminalColors) {
    Column(
        Modifier
            .fillMaxSize()
            .let { if (embedded) it else it.statusBarsPadding() }
            .imePadding(),
    ) {
        // Topbar — terminal-style. Cost/token meter is derived from the
        // transcript so it updates per-turn; no streaming hooks needed
        // beyond the existing computeSessionStats helper.
        val costRate = remember(selectedModel?.uniqueKey) {
            selectedModel?.let { com.glassfiles.data.ai.ModelPricing.rateFor(it) }
        }
        val sessionStats by remember(selectedModel?.uniqueKey) {
            derivedStateOf { computeSessionStats(transcript, costRate) }
        }
        val costLabel = if (sessionStats.totalChars > 0) {
            sessionStats.costUsd?.let { c ->
                when {
                    c < 0.01 -> "<\$0.01"
                    c < 1.0 -> String.format(Locale.US, "\$%.3f", c)
                    else -> String.format(Locale.US, "\$%.2f", c)
                }
            } ?: "\$0.00"
        } else null
        val tokenLabel = if (sessionStats.totalChars > 0) {
            val t = sessionStats.tokens
            when {
                t >= 1000 -> String.format(Locale.US, "%.1fk tok", t / 1000.0)
                else -> "$t tok"
            }
        } else null
        val subtitle = listOfNotNull(
            selectedRepo?.fullName,
            selectedBranch?.takeIf { it.isNotBlank() }
                ?.let { "@$it" }
        ).joinToString("").ifBlank { null }
        val autoApproveParts = buildList {
            if (yoloMode) {
                add("yolo")
            } else {
                if (autoApproveReads) add("r")
                if (autoApproveEdits) add("e")
                if (autoApproveWrites) add("w")
                if (autoApproveCommits) add("c")
            }
        }
        val autoApproveIndicator = autoApproveParts
            .takeIf { it.isNotEmpty() }
            ?.joinToString("\u00B7", prefix = "[auto: ", postfix = "]")
        val autoApproveTone = when {
            yoloMode -> com.glassfiles.ui.screens.ai.terminal.AgentAutoApproveTone.ERROR
            autoApproveCommits -> com.glassfiles.ui.screens.ai.terminal.AgentAutoApproveTone.ERROR
            autoApproveEdits || autoApproveWrites -> com.glassfiles.ui.screens.ai.terminal.AgentAutoApproveTone.WARNING
            else -> com.glassfiles.ui.screens.ai.terminal.AgentAutoApproveTone.NEUTRAL
        }
        com.glassfiles.ui.screens.ai.terminal.AgentTopBar(
            title = Strings.aiAgent,
            subtitle = subtitle,
            cost = costLabel,
            tokens = tokenLabel,
            autoApproveIndicator = autoApproveIndicator,
            autoApproveTone = autoApproveTone,
            embedded = embedded,
            running = running,
            onBack = onBack,
            onSettings = { showSettings = true },
            onNewChat = ::startNewSession,
            onSystemPrompt = {
                if (selectedRepo != null) showSystemPrompt = true else showSettings = true
            },
            onStop = ::stop,
            onClose = onClose,
        )

        // Transcript — repo / branch / model / mode / toggles all live
        // in the settings bottom-sheet now (opened via the topbar gear
        // icon), so the chat takes the full screen height.
        val listState = rememberLazyListState()
        val entries by remember { derivedStateOf { transcript.toList() + approvals.map { AgentEntry.Pending(it) } } }
        LaunchedEffect(entries.size) {
            if (entries.isNotEmpty()) listState.animateScrollToItem(entries.size - 1)
        }

        if (selectedRepo == null && repos.isEmpty() && !GitHubManager.isLoggedIn(context)) {
            TerminalEmptyState(
                message = Strings.aiAgentNoRepos,
                modifier = Modifier.weight(1f),
            )
        } else if (models.isEmpty()) {
            TerminalEmptyState(
                message = Strings.aiAgentNoModels,
                modifier = Modifier.weight(1f),
            )
        } else {
            // Pair tool calls with their results so the row can render
            // an accurate status glyph; the result entry is then
            // suppressed below so it doesn't appear twice.
            val toolResults = remember(entries) {
                entries.mapNotNull { (it as? AgentEntry.ToolResult)?.result }
                    .associateBy { it.callId }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val repo = selectedRepo
                if (repo != null && repo.isPrivate && privateRepoDismissed != repo.fullName) {
                    item("banner-private") {
                        TerminalBanner(
                            text = Strings.aiAgentPrivateRepoWarning,
                            tone = TerminalBannerTone.WARNING,
                            actionLabel = Strings.aiAgentPrivateRepoDismiss,
                            onAction = { privateRepoDismissed = repo.fullName },
                        )
                    }
                }
                if (repo != null && !repo.canWrite()) {
                    item("banner-readonly") {
                        TerminalBanner(
                            text = Strings.aiAgentReadOnlyWarning,
                            tone = TerminalBannerTone.ERROR,
                        )
                    }
                }
                if (selectedModel != null && activeApiKey.isBlank()) {
                    item("banner-no-key") {
                        TerminalBanner(
                            title = Strings.aiAgentNoApiKeyTitle,
                            text = Strings.aiAgentNoApiKeySubtitle,
                            tone = TerminalBannerTone.ERROR,
                        )
                    }
                }
                if (selectedRepo == null || selectedBranch.isNullOrBlank()) {
                    item("banner-pick-repo") {
                        TerminalBanner(
                            text = Strings.aiAgentPickRepoHint,
                            tone = TerminalBannerTone.INFO,
                        )
                    }
                }
                if (entries.isEmpty()) {
                    item {
                        Text(
                            Strings.aiAgentEmptyChat,
                            color = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors.textMuted,
                            fontFamily = JetBrainsMono,
                            fontSize = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.type.message,
                            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        )
                    }
                }
                val lastAssistantId = (entries.lastOrNull { it is AgentEntry.Assistant } as? AgentEntry.Assistant)?.id
                entries.forEach { entry ->
                    if (entry is AgentEntry.ToolResult) return@forEach
                    item(key = entry.stableKey()) {
                        TerminalTranscriptEntry(
                            entry = entry,
                            context = context,
                            toolResults = toolResults,
                            isLastAssistant = (entry is AgentEntry.Assistant) && entry.id == lastAssistantId,
                            running = running,
                            instantRender = instantRender,
                        )
                    }
                }
                if (running && approvals.isEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "$ ",
                                color = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors.accentDim,
                                fontFamily = JetBrainsMono,
                                fontSize = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.type.toolCall,
                            )
                            Text(
                                Strings.aiAgentRunning,
                                color = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors.textSecondary,
                                fontFamily = JetBrainsMono,
                                fontSize = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.type.toolCall,
                            )
                            Spacer(Modifier.width(4.dp))
                            com.glassfiles.ui.screens.ai.terminal.AgentBlinkingCursor()
                        }
                    }
                }
            }
        }

        if (error != null) {
            TerminalBanner(
                text = error.orEmpty(),
                tone = TerminalBannerTone.ERROR,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
        }
        fallbackNotice?.let { notice ->
            TerminalBanner(
                text = notice,
                tone = TerminalBannerTone.WARNING,
                actionLabel = "x",
                onAction = { fallbackNotice = null },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
        }
        // D2 — resume banner. Surfaced when the previous run for this
        // session was killed without a clean finish. Tapping Resume
        // re-issues the same prompt; Discard wipes the pointer.
        pendingResume?.takeIf { !running }?.let { pending ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TerminalBanner(
                    text = Strings.aiAgentResumeBannerText,
                    tone = TerminalBannerTone.INFO,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.glassfiles.ui.screens.ai.terminal.AgentTextButton(
                        label = "[ resume ]",
                        color = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors.accent,
                        enabled = true,
                        onClick = {
                            val resume = pending
                            pendingResume = null
                            com.glassfiles.data.ai.AiAgentResumeStore
                                .clear(context, activeSessionId)
                            submit(resume.prompt, resume.imageBase64)
                        },
                    )
                    com.glassfiles.ui.screens.ai.terminal.AgentTextButton(
                        label = "[ discard ]",
                        color = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors.textSecondary,
                        enabled = true,
                        onClick = {
                            pendingResume = null
                            com.glassfiles.data.ai.AiAgentResumeStore
                                .clear(context, activeSessionId)
                        },
                    )
                }
            }
        }

        if (pendingImage != null) {
            AgentAttachmentPreview(
                base64 = pendingImage.orEmpty(),
                visionAvailable = selectedModel?.let { AiCapability.VISION in it.capabilities } == true,
                onRemove = { pendingImage = null },
            )
        }

        // Input bar — terminal-style. Mirrors the legacy gating: send
        // requires repo / branch / model / api-key all set; the field
        // stays editable so the user can draft text while picking
        // their context in the settings sheet.
        val canSend = !running &&
            (input.text.isNotBlank() || pendingImage != null) &&
            selectedRepo != null && selectedBranch != null && selectedModel != null &&
            activeApiKey.isNotBlank()
        com.glassfiles.ui.screens.ai.terminal.AgentInput(
            value = input,
            onValueChange = { input = it },
            onSend = { submit(input.text, pendingImage) },
            onPickImage = { photoPicker.launch("image/*") },
            canSend = canSend,
            enabled = !running,
            placeholder = Strings.aiAgentInputHint,
            modifier = Modifier.navigationBarsPadding(),
        )
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
                    runTaskInternal(pendingInput.first, pendingInput.second)
                },
                onContinueAndRemember = {
                    selectedRepo?.let { r ->
                        selectedModel?.let { m ->
                            com.glassfiles.data.ai.cost.AiCostModeStore.setRemembered(
                                context, r.fullName, m.providerId.name, true,
                            )
                        }
                    }
                    pendingWarning = null
                    pendingWarningInput = null
                    runTaskInternal(pendingInput.first, pendingInput.second)
                },
            )
        }
        // Per-repo system-prompt override dialog. Mounted alongside the
        // warning gate so it survives sheet lifecycle. Only opens when
        // a repo is picked (the IconButton is disabled otherwise).
        val activeRepoForPrompt = selectedRepo
        if (showSystemPrompt && activeRepoForPrompt != null) {
            val currentPrompt = remember(activeRepoForPrompt.fullName) {
                com.glassfiles.data.ai.AiAgentPrefs
                    .getSystemPromptOverride(context, activeRepoForPrompt.fullName)
                    .orEmpty()
            }
            val currentPlanFirst = remember(activeRepoForPrompt.fullName) {
                com.glassfiles.data.ai.AiAgentPrefs
                    .getPlanThenExecute(context, activeRepoForPrompt.fullName)
            }
            com.glassfiles.ui.screens.ai.SystemPromptOverrideDialog(
                repoFullName = activeRepoForPrompt.fullName,
                initialPrompt = currentPrompt,
                initialPlanFirst = currentPlanFirst,
                onSave = { text, planFirst ->
                    com.glassfiles.data.ai.AiAgentPrefs.setSystemPromptOverride(
                        context, activeRepoForPrompt.fullName, text,
                    )
                    com.glassfiles.data.ai.AiAgentPrefs.setPlanThenExecute(
                        context, activeRepoForPrompt.fullName, planFirst,
                    )
                    showSystemPrompt = false
                },
                onDismiss = { showSystemPrompt = false },
            )
        }

        // Agent settings — repo / branch / model / mode / toggles /
        // export / clear. Mounted alongside the warning + system-prompt
        // dialogs so the gear icon in the topbar always surfaces it.
        if (showSettings) {
            val terminalSettingsState = remember(
                selectedRepo,
                selectedBranch,
                selectedModel,
                costMode,
                autoApproveReads,
                autoApproveEdits,
                autoApproveWrites,
                autoApproveCommits,
                autoApproveDestructive,
                yoloMode,
                sessionTrustEnabled,
                writeLimitPerTask,
                protectedPathsText,
                backgroundExecution,
                keepCpuAwake,
                memoryProjectKnowledge,
                memoryUserPreferences,
                memoryChatSummaries,
                memorySemanticSearch,
                instantRender,
            ) {
                com.glassfiles.ui.screens.ai.terminal.AgentSettingsState(
                    repoLabel = selectedRepo?.fullName ?: "—",
                    branchLabel = selectedBranch ?: "—",
                    modelLabel = selectedModel?.let { "${it.providerId.displayName} \u00B7 ${it.displayName}" } ?: "—",
                    mode = when (costMode) {
                        com.glassfiles.data.ai.cost.AiCostMode.Eco -> com.glassfiles.ui.screens.ai.terminal.AgentMode.ECO
                        com.glassfiles.data.ai.cost.AiCostMode.Balanced -> com.glassfiles.ui.screens.ai.terminal.AgentMode.BALANCED
                        com.glassfiles.data.ai.cost.AiCostMode.MaxQuality -> com.glassfiles.ui.screens.ai.terminal.AgentMode.MAX_QUALITY
                    },
                    modeHint = when (costMode) {
                        com.glassfiles.data.ai.cost.AiCostMode.Eco -> Strings.aiCostModeEcoHint
                        com.glassfiles.data.ai.cost.AiCostMode.Balanced -> Strings.aiCostModeBalancedHint
                        com.glassfiles.data.ai.cost.AiCostMode.MaxQuality -> Strings.aiCostModeMaxHint
                    },
                    autoApproveReads = autoApproveReads,
                    autoApproveEdits = autoApproveEdits,
                    autoApproveWrites = autoApproveWrites,
                    autoApproveCommits = autoApproveCommits,
                    autoApproveDestructive = autoApproveDestructive,
                    yoloMode = yoloMode,
                    sessionTrust = sessionTrustEnabled,
                    writeLimitPerTask = writeLimitPerTask,
                    protectedPathsText = protectedPathsText,
                    protectedPathsCount = protectedPathsText.lineSequence().count { it.trim().isNotBlank() },
                    backgroundExecution = backgroundExecution,
                    keepCpuAwake = keepCpuAwake,
                    memoryProjectKnowledge = memoryProjectKnowledge,
                    memoryUserPreferences = memoryUserPreferences,
                    memoryChatSummaries = memoryChatSummaries,
                    memorySemanticSearch = memorySemanticSearch,
                    instantRender = instantRender,
                )
            }
            val repoOptions = com.glassfiles.ui.screens.ai.terminal.AgentSettingsOptions(
                items = repos.toList().map {
                    com.glassfiles.ui.screens.ai.terminal.RepoDisplay(
                        key = it.fullName,
                        title = it.fullName,
                        subtitle = it.description.takeIf { d -> d.isNotBlank() },
                    )
                },
                selected = selectedRepo?.fullName?.let { full ->
                    com.glassfiles.ui.screens.ai.terminal.RepoDisplay(full, full, null)
                },
                label = { it.title },
                subtitle = { it.subtitle },
                enabled = !running,
            )
            val branchOptions = com.glassfiles.ui.screens.ai.terminal.AgentSettingsOptions(
                items = branches.toList(),
                selected = selectedBranch,
                label = { it },
                enabled = !running && branches.isNotEmpty(),
            )
            val modelOptions = com.glassfiles.ui.screens.ai.terminal.AgentSettingsOptions(
                items = models.toList().map {
                    com.glassfiles.ui.screens.ai.terminal.ModelDisplay(
                        key = it.uniqueKey,
                        title = "${it.providerId.displayName} \u00B7 ${it.displayName}",
                        subtitle = null,
                    )
                },
                selected = selectedModel?.uniqueKey?.let { k ->
                    com.glassfiles.ui.screens.ai.terminal.ModelDisplay(k, k, null)
                },
                label = { it.title },
                subtitle = { it.subtitle },
                enabled = !running && models.isNotEmpty(),
            )
            com.glassfiles.ui.screens.ai.terminal.AgentSettingsBottomSheet(
                state = terminalSettingsState,
                repos = repoOptions,
                branches = branchOptions,
                models = modelOptions,
                onRepoSelected = { picked ->
                    repos.firstOrNull { it.fullName == picked.key }?.let { selectedRepo = it }
                },
                onBranchSelected = { selectedBranch = it },
                onModelSelected = { picked ->
                    val match = models.firstOrNull { it.uniqueKey == picked.key } ?: return@AgentSettingsBottomSheet
                    selectedModel = match
                    selectedRepo?.fullName?.let { repoFull ->
                        com.glassfiles.data.ai.AiAgentPrefs.setLastModel(
                            context,
                            repoFull,
                            match.uniqueKey,
                        )
                    }
                },
                onModeChange = { mode ->
                    if (running) return@AgentSettingsBottomSheet
                    val cm = when (mode) {
                        com.glassfiles.ui.screens.ai.terminal.AgentMode.ECO -> com.glassfiles.data.ai.cost.AiCostMode.Eco
                        com.glassfiles.ui.screens.ai.terminal.AgentMode.BALANCED -> com.glassfiles.data.ai.cost.AiCostMode.Balanced
                        com.glassfiles.ui.screens.ai.terminal.AgentMode.MAX_QUALITY -> com.glassfiles.data.ai.cost.AiCostMode.MaxQuality
                    }
                    costMode = cm
                    com.glassfiles.data.ai.cost.AiCostModeStore.setMode(context, cm)
                },
                onAutoApproveReadsChange = {
                    updateAutoApproveToggles(reads = it)
                },
                onAutoApproveEditsChange = {
                    updateAutoApproveToggles(edits = it)
                },
                onAutoApproveWritesChange = {
                    updateAutoApproveToggles(writes = it)
                },
                onAutoApproveCommitsChange = {
                    updateAutoApproveToggles(commits = it)
                },
                onAutoApproveDestructiveChange = {
                    autoApproveDestructive = it
                    AiAgentApprovalPrefs.setAutoApproveDestructive(context, it)
                },
                onYoloModeChange = { enabled ->
                    if (enabled) {
                        requestYoloConfirm()
                    } else {
                        yoloMode = false
                        AiAgentApprovalPrefs.setYoloMode(context, false)
                        transcript += AgentEntry.Assistant("[system: YOLO mode disabled. Approval policy is active again.]")
                        persistSession()
                    }
                },
                onSessionTrustChange = {
                    sessionTrustEnabled = it
                    AiAgentApprovalPrefs.setSessionTrust(context, it)
                },
                onWriteLimitChange = {
                    writeLimitPerTask = it
                    AiAgentApprovalPrefs.setWriteLimit(context, it)
                },
                onProtectedPathsChange = {
                    protectedPathsText = it
                    AiAgentApprovalPrefs.setProtectedPaths(context, it)
                },
                onBackgroundExecutionChange = {
                    backgroundExecution = it
                    AiAgentApprovalPrefs.setBackgroundExecution(context, it)
                },
                onKeepCpuAwakeChange = {
                    keepCpuAwake = it
                    AiAgentApprovalPrefs.setKeepCpuAwake(context, it)
                },
                onMemoryProjectKnowledgeChange = {
                    memoryProjectKnowledge = it
                    AiAgentMemoryPrefs.setProjectKnowledge(context, it)
                },
                onMemoryUserPreferencesChange = {
                    memoryUserPreferences = it
                    AiAgentMemoryPrefs.setUserPreferences(context, it)
                },
                onMemoryChatSummariesChange = {
                    memoryChatSummaries = it
                    AiAgentMemoryPrefs.setChatSummaries(context, it)
                },
                onMemorySemanticSearchChange = {
                    memorySemanticSearch = it
                    AiAgentMemoryPrefs.setSemanticSearch(context, it)
                },
                onViewMemoryFiles = {
                    val repoFull = selectedRepo?.fullName.orEmpty()
                    if (repoFull.isNotBlank()) {
                        memoryFiles = AiAgentMemoryStore.editableFiles(context, repoFull)
                        showMemoryFiles = true
                    }
                },
                onClearMemory = {
                    AiAgentMemoryStore.clearAll(context)
                    memoryFiles = emptyList()
                    showMemoryFiles = false
                    transcript += AgentEntry.Assistant("[system: AI Agent memory cleared.]")
                },
                onInstantRenderChange = { instantRender = it },
                onClearChat = {
                    showSettings = false
                    startNewSession()
                },
                onExportChat = {
                    val text = buildAgentExport(transcript.toList())
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("agent-chat", text))
                    showSettings = false
                },
                onDismiss = { showSettings = false },
            )
        }
        if (pendingYoloConfirm) {
            com.glassfiles.ui.screens.ai.terminal.YoloModeConfirmDialog(
                previouslyConfirmed = yoloConfirmed,
                onEnable = {
                    applyYoloPreset()
                    pendingYoloConfirm = false
                },
                onDismiss = { pendingYoloConfirm = false },
            )
        }
        if (showMemoryFiles) {
            com.glassfiles.ui.screens.ai.terminal.AgentMemoryFilesDialog(
                files = memoryFiles,
                onSave = { key, content ->
                    val repoFull = selectedRepo?.fullName.orEmpty()
                    if (repoFull.isNotBlank()) {
                        AiAgentMemoryStore.saveEditableFile(context, repoFull, key, content)
                        memoryFiles = AiAgentMemoryStore.editableFiles(context, repoFull)
                    }
                },
                onDismiss = { showMemoryFiles = false },
            )
        }
    }
    } // end AgentTerminalSurface
}

// ─── Terminal-mode renderers ─────────────────────────────────────────────────

private enum class TerminalBannerTone { INFO, WARNING, ERROR }

@Composable
private fun TerminalBanner(
    text: String,
    tone: TerminalBannerTone,
    title: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    val border = when (tone) {
        TerminalBannerTone.INFO -> colors.border
        TerminalBannerTone.WARNING -> colors.warning
        TerminalBannerTone.ERROR -> colors.error
    }
    val tint = when (tone) {
        TerminalBannerTone.INFO -> colors.textSecondary
        TerminalBannerTone.WARNING -> colors.warning
        TerminalBannerTone.ERROR -> colors.error
    }
    val glyph = when (tone) {
        TerminalBannerTone.INFO -> "i"
        TerminalBannerTone.WARNING -> "!"
        TerminalBannerTone.ERROR -> "!"
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surface)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "[$glyph]",
            color = tint,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Bold,
            fontSize = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.type.toolCall,
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.type.toolCall,
                )
            }
            Text(
                text = text,
                color = colors.textSecondary,
                fontFamily = JetBrainsMono,
                fontSize = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.type.toolCall,
            )
        }
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            Spacer(Modifier.width(8.dp))
            com.glassfiles.ui.screens.ai.terminal.AgentTextButton(
                label = "[ $actionLabel ]",
                color = tint,
                enabled = true,
                onClick = onAction,
            )
        }
    }
}

@Composable
private fun TerminalEmptyState(message: String, modifier: Modifier = Modifier) {
    val colors = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors
    Box(
        modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = colors.textMuted,
            fontFamily = JetBrainsMono,
            fontSize = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.type.message,
        )
    }
}

/**
 * Renders a single [AgentEntry] in the new terminal style. ToolResult
 * entries are skipped here — their owning ToolCall picks up the
 * matching result from [toolResults] and renders the status glyph
 * inline.
 */
@Composable
private fun TerminalTranscriptEntry(
    entry: AgentEntry,
    context: Context,
    toolResults: Map<String, AiToolResult>,
    isLastAssistant: Boolean,
    running: Boolean,
    instantRender: Boolean,
) {
    when (entry) {
        is AgentEntry.User -> {
            com.glassfiles.ui.screens.ai.terminal.AgentMessageRow(
                role = com.glassfiles.ui.screens.ai.terminal.AgentRole.USER,
            ) {
                com.glassfiles.ui.screens.ai.terminal.AgentMessageText(text = entry.text)
                if (entry.imageBase64 != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "[image attached]",
                        color = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.colors.textMuted,
                        fontFamily = JetBrainsMono,
                        fontSize = com.glassfiles.ui.screens.ai.terminal.AgentTerminal.type.label,
                    )
                }
            }
        }
        is AgentEntry.Assistant -> {
            val streaming = isLastAssistant && running && !instantRender
            com.glassfiles.ui.screens.ai.terminal.AgentMessageRow(
                role = com.glassfiles.ui.screens.ai.terminal.AgentRole.ASSISTANT,
                streaming = streaming,
            ) {
                TerminalAssistantBody(text = entry.text, context = context)
            }
        }
        is AgentEntry.ToolCall -> {
            val result = toolResults[entry.call.id]
            val status = when {
                result == null -> com.glassfiles.ui.screens.ai.terminal.AgentToolStatus.RUNNING
                result.isError -> com.glassfiles.ui.screens.ai.terminal.AgentToolStatus.ERROR
                else -> com.glassfiles.ui.screens.ai.terminal.AgentToolStatus.SUCCESS
            }
            val statusLine = when {
                result == null -> "running…"
                result.isError -> result.output.lineSequence().firstOrNull()
                    ?.takeIf { it.isNotBlank() } ?: "error"
                else -> {
                    val firstLine = result.output.lineSequence().firstOrNull().orEmpty()
                    val total = result.output.length
                    if (firstLine.isBlank()) "ok" else firstLine.take(120)
                        .let { if (total > 120) "$it… (${total}b)" else it }
                }
            }
            com.glassfiles.ui.screens.ai.terminal.AgentToolCallRow(
                call = entry.call,
                status = status,
                statusLine = statusLine,
                result = result,
            )
        }
        is AgentEntry.ToolResult -> Unit // paired into ToolCall above
        is AgentEntry.Pending -> {
            val pending = entry.pending
            val name = pending.call.name
            val destructive = pending.destructive ?: (name in DESTRUCTIVE_TOOLS)
            val fields = pending.fields ?: buildList {
                add("call" to name)
                runCatching {
                    val obj = org.json.JSONObject(pending.call.argsJson)
                    obj.keys().forEachRemaining { k ->
                        val v = obj.opt(k)
                        if (v != null && v != org.json.JSONObject.NULL) {
                            add(k to v.toString().take(120))
                        }
                    }
                }
            }
            com.glassfiles.ui.screens.ai.terminal.AgentMessageRow(
                role = com.glassfiles.ui.screens.ai.terminal.AgentRole.SYSTEM,
            ) {
                com.glassfiles.ui.screens.ai.terminal.AgentApprovalBlock(
                    tool = name,
                    fields = if (pending.fields == null) fields.drop(1) else fields,
                    destructive = destructive,
                    approveLabel = pending.approveLabel,
                    rejectLabel = pending.rejectLabel,
                    secondaryActionLabel = pending.secondaryLabel,
                    onSecondaryAction = pending.secondaryLabel?.let {
                        {
                            pending.secondarySelected = true
                            pending.deferred.complete(false)
                        }
                    },
                    onApprove = { pending.deferred.complete(true) },
                    onReject = { pending.deferred.complete(false) },
                )
            }
        }
    }
}

private val DESTRUCTIVE_TOOLS = setOf(
    "delete_file", "reset_hard", "force_push",
)

/**
 * Splits assistant text into plain segments and triple-backtick code
 * fences. Each fence is rendered with the terminal code block; plain
 * text is rendered as monospace.
 */
@Composable
private fun TerminalAssistantBody(text: String, context: Context) {
    val cleaned = com.glassfiles.ui.screens.ai.terminal.cleanAgentText(text)
    val segments = remember(cleaned) { splitCodeFences(cleaned) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { seg ->
            when (seg) {
                is TerminalSegment.Plain -> {
                    if (seg.text.isNotBlank()) {
                        com.glassfiles.ui.screens.ai.terminal.AgentMessageText(text = seg.text)
                    }
                }
                is TerminalSegment.Code -> {
                    com.glassfiles.ui.screens.ai.terminal.AgentTerminalCodeBlock(
                        text = seg.text,
                        lang = seg.lang,
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
    var i = 0
    while (i < input.length) {
        val open = input.indexOf("```", i)
        if (open < 0) {
            out += TerminalSegment.Plain(input.substring(i))
            break
        }
        if (open > i) out += TerminalSegment.Plain(input.substring(i, open).trimEnd('\n'))
        val nl = input.indexOf('\n', open + 3)
        val langStart = open + 3
        val (lang, contentStart) = if (nl < 0) {
            "" to (open + 3)
        } else {
            input.substring(langStart, nl).trim() to (nl + 1)
        }
        val close = input.indexOf("```", contentStart)
        if (close < 0) {
            out += TerminalSegment.Code(input.substring(contentStart), lang)
            break
        }
        out += TerminalSegment.Code(input.substring(contentStart, close).trimEnd('\n'), lang)
        i = close + 3
        if (i < input.length && input[i] == '\n') i++
    }
    return out
}

private fun buildAgentExport(entries: List<AgentEntry>): String = buildString {
    entries.forEach { entry ->
        when (entry) {
            is AgentEntry.User -> {
                appendLine("> ${entry.text}")
            }
            is AgentEntry.Assistant -> {
                appendLine("\u25A0 ${entry.text}")
            }
            is AgentEntry.ToolCall -> {
                appendLine("\$ ${entry.call.name}(${entry.call.argsJson})")
            }
            is AgentEntry.ToolResult -> {
                val mark = if (entry.result.isError) "x" else "+"
                appendLine("  [$mark] ${entry.result.output.lineSequence().firstOrNull().orEmpty()}")
            }
            is AgentEntry.Pending -> Unit
        }
        appendLine()
    }
}


@Composable
private fun AgentAttachmentPreview(base64: String, visionAvailable: Boolean, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val bitmap = remember(base64) { decodeAgentBitmap(base64) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
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
                color = colors.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                if (visionAvailable) "Attached to next agent message" else "Selected model has no vision input",
                fontSize = 12.sp,
                color = if (visionAvailable) colors.onSurface else colors.error,
                maxLines = 2,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Rounded.Close, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
        }
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
    val colors = MaterialTheme.colorScheme
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
                .border(1.dp, colors.outlineVariant.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
                .padding(vertical = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(Strings.aiAgentHistoryTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                    val counter = if (query.isBlank()) {
                        "${sessions.size} ${Strings.aiHistoryCount}"
                    } else {
                        "${filteredSessions.size}/${sessions.size} ${Strings.aiHistoryCount}"
                    }
                    Text(counter, fontSize = 11.sp, color = colors.onSurfaceVariant)
                }
                if (sessions.isNotEmpty()) {
                    IconButton(onClick = onDeleteAll) {
                        Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
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
                    Text(Strings.aiHistoryEmpty, fontSize = 13.sp, color = colors.onSurfaceVariant)
                }
            } else if (filteredSessions.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    Text(Strings.aiHistorySearchEmpty, fontSize = 13.sp, color = colors.onSurfaceVariant)
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
                                .background(colors.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { onOpen(session) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.Chat, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    session.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onSurface,
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
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                )
                                Text(
                                    "${sdf.format(Date(session.updatedAt))} · ${session.messages.size} msgs",
                                    fontSize = 10.sp,
                                    color = colors.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                )
                            }
                            IconButton(onClick = { onDelete(session) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Rounded.Close, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
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
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.primary)
                    .clickable(onClick = onNew)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp), tint = colors.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text(Strings.aiAgentHistoryNew, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimary)
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
            is AgentEntry.User -> input += entry.text.length + (entry.imageBase64?.length ?: 0) / 8
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
private fun HistorySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = colors.onSurface, fontSize = 13.sp),
                cursorBrush = SolidColor(colors.primary),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            Strings.aiAgentHistorySearchHint,
                            color = colors.onSurfaceVariant,
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
                Icon(Icons.Rounded.Close, null, Modifier.size(14.dp), tint = colors.onSurfaceVariant)
            }
        }
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
    transcript: androidx.compose.runtime.snapshots.SnapshotStateList<AgentEntry>,
    approvals: androidx.compose.runtime.snapshots.SnapshotStateList<PendingApproval>,
    approvalSettings: AiAgentApprovalSettings,
    isSessionTrusted: () -> Boolean,
    onSessionTrustGranted: () -> Unit,
    consecutiveAutoApprovals: () -> Int,
    setConsecutiveAutoApprovals: (Int) -> Unit,
    writesThisTask: () -> Int,
    setWritesThisTask: (Int) -> Unit,
    fileEditCounts: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Int>,
    context: android.content.Context,
    fallbackCandidates: List<FallbackCandidate>,
    onFallback: (AiModel) -> Unit,
    onToolStatus: (String, Int, Int) -> Unit = { _, _, _ -> },
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
) {
    var messages = seedMessages
    var provider = initialProvider
    var modelId = initialModelId
    var apiKey = initialApiKey
    val remainingFallbacks = ArrayDeque(fallbackCandidates)
    // Seed the cost estimate with the initial conversation so a long
    // pre-existing chat doesn't get a fresh "0 / N" budget when the
    // user resumes it.
    estimate?.addInput(seedMessages.sumOf { it.content.length })
    val maxIterations = estimate?.limits?.maxToolCalls ?: MAX_ITERATIONS

    suspend fun awaitApproval(
        call: AiToolCall,
        check: AiAgentApprovalCheck,
        fields: List<Pair<String, String>>,
        approveLabel: String = "[ y \u00B7 approve ]",
        rejectLabel: String = "[ n \u00B7 reject ]",
        secondaryLabel: String? = null,
    ): Pair<Boolean, Boolean> {
        val pending = PendingApproval(
            call = call,
            deferred = CompletableDeferred(),
            fields = fields,
            destructive = check.destructive,
            approveLabel = approveLabel,
            rejectLabel = rejectLabel,
            secondaryLabel = secondaryLabel,
        )
        approvals += pending
        val ok = pending.deferred.await()
        approvals.remove(pending)
        return ok to pending.secondarySelected
    }

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
            content = turn.assistantText,
            toolCalls = turn.toolCalls,
        )
        val results = mutableListOf<AiMessage>()
        for ((callIndex, call) in turn.toolCalls.withIndex()) {
            transcript += AgentEntry.ToolCall(call)
            val tool = AgentTools.byName(call.name)
            onToolStatus(describeToolStatus(call), callIndex + 1, turn.toolCalls.size)
            estimate?.bumpToolCall()
            if (tool?.readOnly == false) estimate?.bumpWriteProposal()
            val check = AiAgentApprovalPolicy.check(
                call = call,
                tool = tool,
                settings = approvalSettings,
                sessionTrusted = isSessionTrusted(),
            )
            val approvalFields = buildList {
                add("reason" to check.reason)
                add("category" to check.category.name.lowercase())
                if (check.targetPaths.isNotEmpty()) add("path" to check.targetPaths.joinToString(", ").take(120))
            }
            var rejectedForReread = false
            val approved = when {
                approvalSettings.yoloMode && check.autoApproved -> true
                approvalSettings.sessionTrust &&
                    !isSessionTrusted() &&
                    !check.protected &&
                    !check.destructive &&
                    (check.category == AiAgentApprovalCategory.EDIT || check.category == AiAgentApprovalCategory.WRITE) -> {
                    val (ok, _) = awaitApproval(
                        call = call,
                        check = check,
                        fields = approvalFields + ("trust" to "allow edits/writes for this task"),
                        approveLabel = "[ y \u00B7 trust task ]",
                    )
                    if (ok) onSessionTrustGranted()
                    setConsecutiveAutoApprovals(0)
                    ok
                }
                check.countsAsWrite &&
                    !check.protected &&
                    !check.destructive &&
                    approvalSettings.writeLimitPerTask > 0 &&
                    writesThisTask() >= approvalSettings.writeLimitPerTask -> {
                    val (ok, _) = awaitApproval(
                        call = call,
                        check = check,
                        fields = approvalFields + ("limit" to "${approvalSettings.writeLimitPerTask} writes reached"),
                        approveLabel = "[ y \u00B7 allow next batch ]",
                    )
                    if (ok) setWritesThisTask(0)
                    setConsecutiveAutoApprovals(0)
                    ok
                }
                check.category == AiAgentApprovalCategory.EDIT &&
                    !check.protected &&
                    !check.destructive &&
                    check.targetPaths.firstOrNull()?.let { (fileEditCounts[it] ?: 0) >= 3 } == true -> {
                    val path = check.targetPaths.first()
                    val (ok, reread) = awaitApproval(
                        call = call,
                        check = check,
                        fields = approvalFields + ("file guard" to "$path edited 3 times"),
                        secondaryLabel = "[ r \u00B7 re-read + consolidate ]",
                    )
                    rejectedForReread = reread
                    setConsecutiveAutoApprovals(0)
                    ok
                }
                check.autoApproved && consecutiveAutoApprovals() >= 50 -> {
                    val (ok, _) = awaitApproval(
                        call = call,
                        check = check,
                        fields = approvalFields + ("auto guard" to "MANY ACTIONS AUTO-APPROVED"),
                        approveLabel = "[ y \u00B7 continue ]",
                    )
                    if (ok) setConsecutiveAutoApprovals(0)
                    ok
                }
                check.autoApproved -> {
                    setConsecutiveAutoApprovals(consecutiveAutoApprovals() + 1)
                    true
                }
                else -> {
                    val (ok, _) = awaitApproval(
                        call = call,
                        check = check,
                        fields = approvalFields,
                    )
                    setConsecutiveAutoApprovals(0)
                    ok
                }
            }
            val result = if (!approved) {
                AiToolResult(
                    callId = call.id,
                    name = call.name,
                    output = if (rejectedForReread) {
                        "Approval paused: re-read the file, consolidate the edit plan, then request approval again."
                    } else {
                        Strings.aiAgentRejected
                    },
                    isError = true,
                )
            } else {
                executor.execute(context, call)
            }
            if (approved && !result.isError && check.countsAsWrite) {
                setWritesThisTask(writesThisTask() + 1)
                if (check.category == AiAgentApprovalCategory.EDIT) {
                    check.targetPaths.firstOrNull()?.let { path ->
                        fileEditCounts[path] = (fileEditCounts[path] ?: 0) + 1
                    }
                }
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

/** Soft char-length trigger for transcript summarisation. At ~2 M chars
 * (~500 k tokens) we fold older turns into a one-line briefing so the
 * next provider call still fits inside a 1 M-token window without
 * losing the entire history. Bumped from the historical 80 000 to give
 * the user a real megacontext budget on providers that support it. */
private const val CONTEXT_COMPACT_CHARS = 2_000_000

/** Number of *most-recent* messages we always keep verbatim before
 * summarising the head. 20 ≈ 10 user/assistant turns — enough room
 * for a multi-step refactor or a PR-sized review thread. */
private const val CONTEXT_KEEP_TAIL = 20

private fun describeToolStatus(call: AiToolCall): String {
    val path = runCatching {
        val obj = org.json.JSONObject(call.argsJson)
        obj.optString("path").takeIf { it.isNotBlank() }
    }.getOrNull()
    return when (call.name) {
        "edit_file" -> "Editing ${path ?: "file"}"
        "write_file" -> "Writing ${path ?: "file"}"
        "read_file", "read_file_range" -> "Reading ${path ?: "file"}"
        "commit" -> "Committing changes"
        "open_pr" -> "Opening pull request"
        "create_branch" -> "Creating branch"
        else -> call.name
    }
}

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
            is AgentEntry.User -> AiMessage(role = "user", content = it.text, imageBase64 = it.imageBase64)
            is AgentEntry.Assistant ->
                if (it.text.isBlank()) null else AiMessage(role = "assistant", content = it.text)
            is AgentEntry.ToolCall, is AgentEntry.ToolResult, is AgentEntry.Pending -> null
        }
    }

private fun List<AiChatSessionStore.Message>.toAgentEntries(): List<AgentEntry> =
    mapNotNull { message ->
        when (message.role) {
            "user" -> AgentEntry.User(message.content, message.imageBase64)
            "assistant" -> AgentEntry.Assistant(message.content)
            else -> null
        }
    }

private fun AgentEntry.stableKey(): String = when (this) {
    is AgentEntry.User -> "u:${text.hashCode()}:${imageBase64?.hashCode() ?: 0}:${System.identityHashCode(this)}"
    is AgentEntry.Assistant -> "a:$id"
    is AgentEntry.ToolCall -> "tc:${call.id}"
    is AgentEntry.ToolResult -> "tr:${result.callId}"
    is AgentEntry.Pending -> "pending:${pending.call.id}"
}

private sealed class AgentEntry {
    data class User(val text: String, val imageBase64: String? = null) : AgentEntry()
    /** [id] keeps the LazyList key stable across streaming text updates;
     * each replacement keeps the same id via `copy(text = ...)`. */
    data class Assistant(val text: String, val id: String = newAssistantId()) : AgentEntry()
    data class ToolCall(val call: AiToolCall) : AgentEntry()
    data class ToolResult(val result: AiToolResult) : AgentEntry()
    data class Pending(val pending: PendingApproval) : AgentEntry()
}

private fun newAssistantId(): String = "a_${System.nanoTime()}"

private data class PendingApproval(
    val call: AiToolCall,
    val deferred: CompletableDeferred<Boolean>,
    val fields: List<Pair<String, String>>? = null,
    val destructive: Boolean? = null,
    val approveLabel: String = "[ y \u00B7 approve ]",
    val rejectLabel: String = "[ n \u00B7 reject ]",
    val secondaryLabel: String? = null,
    var secondarySelected: Boolean = false,
)
