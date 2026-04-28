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
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import com.glassfiles.ui.components.AiPickerChip
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

    val transcript = remember { mutableStateListOf<AgentEntry>() }
    var input by remember { mutableStateOf(TextFieldValue(initialPrompt.orEmpty())) }
    var pendingImage by remember { mutableStateOf<String?>(null) }
    var autoApproveReads by remember { mutableStateOf(true) }
    var running by remember { mutableStateOf(false) }
    var runJob by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

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
            selectedModel = saved ?: preferToolUseModel(models)
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

    fun submit(userText: String, imageBase64: String?) {
        val text = userText.trim()
        val image = imageBase64?.takeIf { selectedModel?.let { m -> AiCapability.VISION in m.capabilities } == true }
        if (text.isEmpty() && image == null) return
        val repo = selectedRepo ?: return
        val branch = selectedBranch ?: return
        val model = selectedModel ?: return
        val key = AiKeyStore.getKey(context, model.providerId)
        if (key.isBlank()) {
            error = Strings.aiAgentNoModels
            return
        }
        error = null
        input = TextFieldValue("")
        pendingImage = null
        transcript += AgentEntry.User(text, image)
        persistSession()
        running = true

        runJob = scope.launch {
            val executor = GitHubToolExecutor(repoOwner(repo), repoName(repo), branch)
            val provider = AiProviders.get(model.providerId)
            try {
                runAgentLoop(
                    seedMessages = transcript.toAiMessages(),
                    provider = provider,
                    modelId = model.id,
                    apiKey = key,
                    executor = executor,
                    transcript = transcript,
                    approvals = approvals,
                    autoApproveReads = autoApproveReads,
                    context = context,
                )
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                persistSession()
                running = false
                runJob = null
            }
        }
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
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showHistory = true }) {
                Icon(Icons.Rounded.Chat, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
            }
            IconButton(onClick = ::startNewSession) {
                Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
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
                onSelect = { selectedModel = it },
                enabled = !running && models.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
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
                onCheckedChange = { autoApproveReads = it },
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
            selectedRepo != null && selectedBranch != null && selectedModel != null
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
                    Text("${sessions.size} ${Strings.aiHistoryCount}", fontSize = 11.sp, color = colors.onSurfaceVariant)
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
            Spacer(Modifier.height(8.dp))
            if (sessions.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    Text(Strings.aiHistoryEmpty, fontSize = 13.sp, color = colors.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(sessions, key = { it.id }) { session ->
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

@Composable
private fun TranscriptEntry(
    entry: AgentEntry,
    activeRepoFullName: String?,
    activeBranch: String?,
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
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val showDiff = isPending && entry.call.name in DIFF_PREVIEW_TOOLS
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
        if (isPending) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    label = Strings.aiAgentApprove,
                    icon = Icons.Rounded.Check,
                    bg = colors.primary,
                    fg = colors.onPrimary,
                    onClick = onApprove,
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
    provider: com.glassfiles.data.ai.providers.AiProvider,
    modelId: String,
    apiKey: String,
    executor: GitHubToolExecutor,
    transcript: androidx.compose.runtime.snapshots.SnapshotStateList<AgentEntry>,
    approvals: androidx.compose.runtime.snapshots.SnapshotStateList<PendingApproval>,
    autoApproveReads: Boolean,
    context: android.content.Context,
) {
    var messages = seedMessages
    repeat(MAX_ITERATIONS) {
        // Insert a streaming Assistant placeholder so text deltas land in
        // the UI as they arrive. The placeholder has a stable id, so
        // LazyList keeps its position in the list across replacements.
        val streamingEntry = AgentEntry.Assistant(text = "")
        transcript += streamingEntry
        val streamingIndex = transcript.lastIndex

        val turn = try {
            provider.chatWithToolsStreaming(
                context = context,
                modelId = modelId,
                messages = messages,
                tools = AgentTools.ALL,
                apiKey = apiKey,
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
        for (call in turn.toolCalls) {
            transcript += AgentEntry.ToolCall(call)
            val tool = AgentTools.byName(call.name)
            val approved = if (tool?.readOnly == true && autoApproveReads) {
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
            transcript += AgentEntry.ToolResult(result)
            results += AiMessage(
                role = "tool",
                content = result.output,
                toolCallId = result.callId,
                toolName = result.name,
            )
        }
        messages = messages + assistantMsg + results
    }
}

private const val MAX_ITERATIONS = 8
private const val AGENT_SESSION_MODE = "agent"

// ─── Helpers / data ───────────────────────────────────────────────────────

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

