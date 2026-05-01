package com.glassfiles.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.data.ai.AiChatSessionStore
import com.glassfiles.data.ai.AiKeyStore
import com.glassfiles.data.ai.ModelRegistry
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
import com.glassfiles.ui.screens.ai.ExpensiveActionWarningDialog
import kotlinx.coroutines.CompletableDeferred
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
    // Approval prefs are owned by AiAgentApprovalPrefs (DataStore-backed). The agent
    // loop reads them via [snapshotApprovalSettings] at run start so YOLO / write
    // limit / per-category toggles set anywhere in the app actually take effect.
    // We mirror the read-auto-approve flag here purely so the inline switch in the
    // top bar stays in sync without round-tripping through prefs every recomposition.
    var autoApproveReads by remember {
        mutableStateOf(com.glassfiles.data.ai.AiAgentApprovalPrefs.getAutoApproveReads(context))
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
        refreshSessions()
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

    fun stop() {
        runJob?.cancel()
        runJob = null
        running = false
        approvals.forEach { it.deferred.complete(false) }
        approvals.clear()
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
        transcript += AgentEntry.User(text, image)
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
            val baseMessages = transcript.toAiMessages()
            val systemMessages = buildList {
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
                    " · yolo=${approvalSettings.yoloMode}",
            )
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
                    approvalSettings = approvalSettings,
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
                )
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
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

    // ─── UI ───────────────────────────────────────────────────────────────
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.surface)
            .let { if (embedded) it else it.statusBarsPadding() }
            .imePadding(),
    ) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(
                start = if (embedded) 16.dp else 4.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 6.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!embedded) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = colors.onSurface)
                }
            }
            Text(
                Strings.aiAgent,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            // Cost / token meter — derived from the transcript so it
            // updates as new turns land. No live tracking inside
            // runAgentLoop is needed; the rate table provides USD
            // estimates only when we know the model's pricing.
            val costRate = remember(selectedModel?.uniqueKey) {
                selectedModel?.let { com.glassfiles.data.ai.ModelPricing.rateFor(it) }
            }
            val sessionStats by remember(selectedModel?.uniqueKey) {
                derivedStateOf { computeSessionStats(transcript, costRate) }
            }
            if (sessionStats.totalChars > 0) {
                Spacer(Modifier.width(8.dp))
                CostMeter(stats = sessionStats)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showHistory = true }) {
                Icon(Icons.Rounded.Chat, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
            }
            IconButton(onClick = ::startNewSession) {
                Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
            }
            // Per-repo settings — system prompt override etc. Disabled
            // until a repo is picked because the override is keyed by
            // repo full-name.
            IconButton(
                onClick = { showSystemPrompt = true },
                enabled = selectedRepo != null,
            ) {
                Icon(Icons.Rounded.Tune, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
            }
            if (running) {
                IconButton(onClick = ::stop) {
                    Icon(Icons.Rounded.Stop, null, Modifier.size(20.dp), tint = colors.error)
                }
            }
            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                }
            }
        }

        // Pickers: fixed constraints prevent labels from collapsing into one-letter columns.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AiPickerChip(
                    label = "REPO",
                    value = selectedRepo?.fullName ?: Strings.aiAgentSelectRepo,
                    title = Strings.aiAgentSelectRepo,
                    options = repos.toList(),
                    optionLabel = { it.fullName },
                    optionSubtitle = { it.description.takeIf { d -> d.isNotBlank() } },
                    selected = selectedRepo,
                    onSelect = { selectedRepo = it },
                    enabled = !running,
                    modifier = Modifier.weight(1f).widthIn(min = 0.dp),
                )
                AiPickerChip(
                    label = "BRANCH",
                    value = selectedBranch ?: "—",
                    title = Strings.aiAgentSelectBranch,
                    options = branches.toList(),
                    optionLabel = { it },
                    selected = selectedBranch,
                    onSelect = { selectedBranch = it },
                    enabled = !running && branches.isNotEmpty(),
                    modifier = Modifier.weight(1f).widthIn(min = 0.dp),
                )
            }
            AiPickerChip(
                label = "MODEL",
                value = selectedModel?.displayName ?: "—",
                title = Strings.aiAgentSelectModel,
                options = models.toList(),
                optionLabel = { "${it.providerId.displayName} · ${it.displayName}" },
                selected = selectedModel,
                onSelect = { picked ->
                    selectedModel = picked
                    // Per-repo last-model. Tied to the active repo so
                    // re-opening the same repo re-picks the same
                    // model without prompting the user.
                    selectedRepo?.fullName?.let { repoFull ->
                        com.glassfiles.data.ai.AiAgentPrefs.setLastModel(
                            context,
                            repoFull,
                            picked.uniqueKey,
                        )
                    }
                },
                enabled = !running && models.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Cost-mode selector. Three FilterChip-style buttons that map
        // to AiCostMode. Selection is persisted immediately so a
        // subsequent task pulls the new mode from AiCostModeStore.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(Strings.aiCostMode, fontSize = 13.sp, color = colors.onSurface)
                Text(
                    when (costMode) {
                        com.glassfiles.data.ai.cost.AiCostMode.Eco -> Strings.aiCostModeEcoHint
                        com.glassfiles.data.ai.cost.AiCostMode.Balanced -> Strings.aiCostModeBalancedHint
                        com.glassfiles.data.ai.cost.AiCostMode.MaxQuality -> Strings.aiCostModeMaxHint
                    },
                    fontSize = 10.sp,
                    color = colors.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    com.glassfiles.data.ai.cost.AiCostMode.Eco to Strings.aiCostModeEco,
                    com.glassfiles.data.ai.cost.AiCostMode.Balanced to Strings.aiCostModeBalanced,
                    com.glassfiles.data.ai.cost.AiCostMode.MaxQuality to Strings.aiCostModeMax,
                ).forEach { (mode, label) ->
                    val selected = mode == costMode
                    FilterChip(
                        selected = selected,
                        onClick = {
                            if (!running) {
                                costMode = mode
                                com.glassfiles.data.ai.cost.AiCostModeStore.setMode(context, mode)
                            }
                        },
                        label = { Text(label, fontSize = 11.sp) },
                        enabled = !running,
                    )
                }
            }
        }

        // Auto-approve switch
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(Strings.aiAgentAutoApprove, fontSize = 13.sp, color = colors.onSurface)
                Text(
                    Strings.aiAgentAutoApproveHint,
                    fontSize = 10.sp,
                    color = colors.onSurfaceVariant,
                )
            }
            Switch(
                checked = autoApproveReads,
                onCheckedChange = { value ->
                    autoApproveReads = value
                    com.glassfiles.data.ai.AiAgentApprovalPrefs
                        .setAutoApproveReads(context, value)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onPrimary,
                    checkedTrackColor = colors.primary,
                ),
            )
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant.copy(alpha = 0.4f)))

        // Transcript
        val listState = rememberLazyListState()
        val entries by remember { derivedStateOf { transcript.toList() + approvals.map { AgentEntry.Pending(it) } } }
        LaunchedEffect(entries.size) {
            if (entries.isNotEmpty()) listState.animateScrollToItem(entries.size - 1)
        }

        if (selectedRepo == null && repos.isEmpty() && !GitHubManager.isLoggedIn(context)) {
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
                            tint = colors.tertiary,
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
                if (selectedRepo == null || selectedBranch.isNullOrBlank()) {
                    item("banner-pick-repo") {
                        AgentBanner(
                            icon = Icons.Rounded.Build,
                            tint = colors.onSurfaceVariant,
                            text = Strings.aiAgentPickRepoHint,
                        )
                    }
                }
                if (entries.isEmpty()) {
                    item {
                        Text(
                            Strings.aiAgentEmptyChat,
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        )
                    }
                }
                entries.forEach { entry ->
                    item(key = entry.stableKey()) {
                        TranscriptEntry(
                            entry = entry,
                            activeRepoFullName = selectedRepo?.fullName,
                            activeBranch = selectedBranch,
                            activeDefaultBranch = selectedRepo?.defaultBranch,
                        )
                    }
                }
                if (running && approvals.isEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = colors.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(Strings.aiAgentRunning, fontSize = 12.sp, color = colors.onSurfaceVariant)
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
                    .background(colors.tertiaryContainer.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Warning,
                    null,
                    Modifier.size(16.dp),
                    tint = colors.onTertiaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    notice,
                    fontSize = 12.sp,
                    color = colors.onTertiaryContainer,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { fallbackNotice = null }, modifier = Modifier.size(20.dp)) {
                    Icon(
                        Icons.Rounded.Close,
                        null,
                        Modifier.size(14.dp),
                        tint = colors.onTertiaryContainer,
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
                    .background(colors.secondaryContainer.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    Strings.aiAgentResumeBannerText,
                    fontSize = 12.sp,
                    color = colors.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    val resume = pending
                    pendingResume = null
                    com.glassfiles.data.ai.AiAgentResumeStore
                        .clear(context, activeSessionId)
                    submit(resume.prompt, resume.imageBase64)
                }) {
                    Text(
                        Strings.aiAgentResumeBannerAction,
                        color = colors.primary,
                        fontSize = 12.sp,
                    )
                }
                TextButton(onClick = {
                    pendingResume = null
                    com.glassfiles.data.ai.AiAgentResumeStore
                        .clear(context, activeSessionId)
                }) {
                    Text(
                        Strings.aiAgentResumeBannerDiscard,
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp,
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

        // Input bar.
        //
        // The input field stays editable as long as the agent isn't busy:
        // even if the user hasn't picked repo/branch/model yet they should
        // be able to draft a message. Send is gated on the full set being
        // present, the placeholder lives inside `decorationBox` (so it
        // never overlaps the field's hit-target / IME state), and we use
        // a `TextFieldValue` so cursor + selection survive recomposition.
        val canSend = !running &&
            (input.text.isNotBlank() || pendingImage != null) &&
            selectedRepo != null && selectedBranch != null && selectedModel != null &&
            activeApiKey.isNotBlank()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { photoPicker.launch("image/*") },
                enabled = !running && selectedModel != null,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    Icons.Rounded.AddPhotoAlternate,
                    null,
                    Modifier.size(22.dp),
                    tint = if (!running && selectedModel != null) colors.onSurfaceVariant else colors.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, colors.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    enabled = !running,
                    textStyle = TextStyle(color = colors.onSurface, fontSize = 14.sp),
                    cursorBrush = SolidColor(colors.primary),
                    decorationBox = { inner ->
                        if (input.text.isEmpty()) {
                            Text(
                                Strings.aiAgentInputHint,
                                color = colors.onSurfaceVariant,
                                fontSize = 14.sp,
                            )
                        }
                        inner()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 22.dp, max = 120.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { submit(input.text, pendingImage) },
                enabled = canSend,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (canSend) colors.primary else colors.surfaceVariant),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    null,
                    Modifier.size(18.dp),
                    tint = if (canSend) colors.onPrimary else colors.onSurfaceVariant,
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
    }
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
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = tint)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (!title.isNullOrBlank()) {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                Spacer(Modifier.height(2.dp))
            }
            Text(text, fontSize = 12.sp, color = colors.onSurfaceVariant)
        }
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                actionLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onAction() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun EmptyAgentState(message: String) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.Build,
                null,
                Modifier.size(40.dp),
                tint = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(message, fontSize = 13.sp, color = colors.onSurfaceVariant)
        }
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
private fun AgentMessageImage(base64: String) {
    val colors = MaterialTheme.colorScheme
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
private fun CostMeter(stats: SessionStats) {
    val colors = MaterialTheme.colorScheme
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
            .background(colors.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (costLabel != null) {
            Text(
                costLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(width = 1.dp, height = 10.dp)
                    .background(colors.outlineVariant.copy(alpha = 0.6f))
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            "$tokenLabel ${Strings.aiAgentTokensLabel}",
            fontSize = 11.sp,
            color = colors.onSurfaceVariant,
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

@Composable
private fun TranscriptEntry(
    entry: AgentEntry,
    activeRepoFullName: String?,
    activeBranch: String?,
    activeDefaultBranch: String?,
) {
    val colors = MaterialTheme.colorScheme
    when (entry) {
        is AgentEntry.User -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Column(
                    Modifier
                        .padding(start = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.primary)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (entry.imageBase64 != null) {
                        AgentMessageImage(entry.imageBase64)
                    }
                    if (entry.text.isNotBlank()) {
                        Text(entry.text, color = colors.onPrimary, fontSize = 14.sp)
                    }
                }
            }
        }
        is AgentEntry.Assistant -> {
            if (entry.text.isNotBlank()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(entry.text, color = colors.onSurface, fontSize = 14.sp)
                }
            }
        }
        is AgentEntry.ToolCall -> ToolCallCard(
            entry = entry,
            isPending = false,
            activeRepoFullName = activeRepoFullName,
            activeBranch = activeBranch,
            activeDefaultBranch = activeDefaultBranch,
            onApprove = {},
            onReject = {},
        )
        is AgentEntry.ToolResult -> ToolResultCard(entry)
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
    val colors = MaterialTheme.colorScheme
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
            .background(colors.surfaceVariant.copy(alpha = 0.4f))
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Build, null, Modifier.size(14.dp), tint = colors.tertiary)
            Spacer(Modifier.width(6.dp))
            Text(
                Strings.aiAgentToolCallTitle.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = colors.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                entry.call.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            entry.call.argsJson,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = colors.onSurfaceVariant,
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
                    bg = if (approveEnabled) colors.primary else colors.surfaceVariant,
                    fg = if (approveEnabled) colors.onPrimary else colors.onSurfaceVariant,
                    onClick = if (approveEnabled) onApprove else ({}),
                )
                ActionButton(
                    label = Strings.aiAgentReject,
                    icon = Icons.Rounded.Close,
                    bg = colors.surfaceVariant,
                    fg = colors.onSurface,
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

@Composable
private fun ProtectedBranchWarning(
    branch: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.errorContainer.copy(alpha = 0.55f))
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
                color = colors.onErrorContainer,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            Strings.aiAgentProtectedBranchSubtitle.replace("{branch}", branch),
            fontSize = 11.sp,
            color = colors.onErrorContainer,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = colors.error,
                    uncheckedColor = colors.onErrorContainer.copy(alpha = 0.5f),
                ),
            )
            Text(
                Strings.aiAgentProtectedBranchConfirm,
                fontSize = 12.sp,
                color = colors.onErrorContainer,
            )
        }
    }
}

@Composable
private fun OpenPrPreview(call: AiToolCall, defaultBase: String?) {
    val colors = MaterialTheme.colorScheme
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
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            Strings.aiAgentOpenPrPreview.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            color = colors.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            title.ifBlank { "(no title)" },
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "$head  →  $base",
            fontSize = 12.sp,
            color = colors.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
        )
        if (body.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                body.take(800) + if (body.length > 800) "…" else "",
                fontSize = 12.sp,
                color = colors.onSurface,
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
    val colors = MaterialTheme.colorScheme
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
                Text(Strings.aiAgentDiffLoading, fontSize = 11.sp, color = colors.onSurfaceVariant)
            } else {
                DiffLines(LineDiff.diff(orig, content))
            }
        }
        AgentTools.COMMIT.name -> {
            val files = args.optJSONArray("files")
            if (files == null || files.length() == 0) {
                Text(Strings.aiAgentDiffEmpty, fontSize = 11.sp, color = colors.onSurfaceVariant)
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
                            Text(Strings.aiAgentDiffLoading, fontSize = 11.sp, color = colors.onSurfaceVariant)
                        } else {
                            DiffLines(LineDiff.diff(orig, content))
                        }
                    }
                    if (files.length() > limit) {
                        Text(
                            "+${files.length() - limit} more file(s)",
                            fontSize = 11.sp,
                            color = colors.onSurfaceVariant,
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
    val colors = MaterialTheme.colorScheme
    Text(
        label,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
        color = colors.onSurfaceVariant,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun DiffLines(diff: List<LineDiff.Line>) {
    val colors = MaterialTheme.colorScheme
    val stats = LineDiff.stats(diff)
    val compacted = LineDiff.compact(diff, contextLines = 2)
    val maxRender = 60
    val display = if (compacted.size > maxRender) compacted.take(maxRender) else compacted
    val addBg = colors.tertiary.copy(alpha = 0.15f)
    val delBg = colors.error.copy(alpha = 0.12f)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface.copy(alpha = 0.6f))
            .border(0.5.dp, colors.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(vertical = 4.dp),
    ) {
        Text(
            "+${stats.added}  -${stats.removed}",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurfaceVariant,
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
                        color = colors.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                continue
            }
            val (prefix, bg, fg) = when (line) {
                is LineDiff.Line.Add -> Triple("+ ", addBg, colors.onSurface)
                is LineDiff.Line.Del -> Triple("- ", delBg, colors.onSurface)
                is LineDiff.Line.Same -> Triple("  ", androidx.compose.ui.graphics.Color.Transparent, colors.onSurfaceVariant)
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
                color = colors.onSurfaceVariant,
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
    val colors = MaterialTheme.colorScheme
    val accent = if (entry.result.isError) colors.error else colors.tertiary
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.3f))
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
                color = colors.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            entry.result.output,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = colors.onSurface,
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
    transcript: androidx.compose.runtime.snapshots.SnapshotStateList<AgentEntry>,
    approvals: androidx.compose.runtime.snapshots.SnapshotStateList<PendingApproval>,
    /**
     * Snapshot of all approval-related prefs at the start of the task. The loop
     * runs every tool call through [com.glassfiles.data.ai.agent.AiAgentApprovalPolicy]
     * with these settings, so YOLO / per-category auto-approve / session-trust /
     * write-limit / protected-path toggles all take effect — not just
     * `autoApproveReads`, which used to be the only one wired in.
     */
    approvalSettings: com.glassfiles.data.ai.agent.AiAgentApprovalSettings,
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
) {
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
        for (call in turn.toolCalls) {
            transcript += AgentEntry.ToolCall(call)
            val tool = AgentTools.byName(call.name)
            estimate?.bumpToolCall()
            if (tool?.readOnly == false) estimate?.bumpWriteProposal()
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
)

