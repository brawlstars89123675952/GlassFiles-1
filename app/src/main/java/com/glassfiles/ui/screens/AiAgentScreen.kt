package com.glassfiles.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.data.ai.AiKeyStore
import com.glassfiles.data.ai.ModelRegistry
import com.glassfiles.data.ai.agent.AgentTools
import com.glassfiles.data.ai.agent.AiToolCall
import com.glassfiles.data.ai.agent.AiToolResult
import com.glassfiles.data.ai.agent.GitHubToolExecutor
import com.glassfiles.data.ai.models.AiCapability
import com.glassfiles.data.ai.models.AiMessage
import com.glassfiles.data.ai.models.AiModel
import com.glassfiles.data.ai.models.AiProviderId
import com.glassfiles.data.ai.providers.AiProviders
import com.glassfiles.data.github.GHRepo
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.components.AiPickerChip
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
 * The active repo / branch / model are session-scoped; switching any of
 * them resets the transcript so the model never sees stale context.
 */
@Composable
fun AiAgentScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onBack)

    // ─── State ────────────────────────────────────────────────────────────
    val repos = remember { mutableStateListOf<GHRepo>() }
    var selectedRepo by remember { mutableStateOf<GHRepo?>(null) }

    val branches = remember { mutableStateListOf<String>() }
    var selectedBranch by remember { mutableStateOf<String?>(null) }

    val models = remember { mutableStateListOf<AiModel>() }
    var selectedModel by remember { mutableStateOf<AiModel?>(null) }

    val transcript = remember { mutableStateListOf<AgentEntry>() }
    var input by remember { mutableStateOf("") }
    var autoApproveReads by remember { mutableStateOf(true) }
    var running by remember { mutableStateOf(false) }
    var runJob by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

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
        if (selectedModel == null) selectedModel = preferToolUseModel(models)
    }

    LaunchedEffect(selectedRepo) {
        branches.clear()
        selectedBranch = null
        val repo = selectedRepo ?: return@LaunchedEffect
        try {
            val list = GitHubManager.getBranches(context, repoOwner(repo), repoName(repo))
            branches.addAll(list)
            selectedBranch = repo.defaultBranch.takeIf { it.isNotBlank() && it in list }
                ?: list.firstOrNull()
        } catch (_: Exception) {}
        // Repo / branch change invalidates any prior context.
        transcript.clear()
        approvals.clear()
    }

    // ─── Helpers ──────────────────────────────────────────────────────────
    fun stop() {
        runJob?.cancel()
        runJob = null
        running = false
        approvals.forEach { it.deferred.complete(false) }
        approvals.clear()
    }

    fun submit(userText: String) {
        val text = userText.trim()
        if (text.isEmpty()) return
        val repo = selectedRepo ?: return
        val branch = selectedBranch ?: return
        val model = selectedModel ?: return
        val key = AiKeyStore.getKey(context, model.providerId)
        if (key.isBlank()) {
            error = Strings.aiAgentNoModels
            return
        }
        error = null
        input = ""
        transcript += AgentEntry.User(text)
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
            .statusBarsPadding()
            .imePadding(),
    ) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = colors.onSurface)
            }
            Text(
                Strings.aiAgent,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            Spacer(Modifier.weight(1f))
            if (running) {
                IconButton(onClick = ::stop) {
                    Icon(Icons.Rounded.Stop, null, Modifier.size(20.dp), tint = colors.error)
                }
            }
        }

        // Pickers row
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
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
            )
            AiPickerChip(
                label = "MODEL",
                value = selectedModel?.displayName ?: "—",
                title = Strings.aiAgentSelectModel,
                options = models.toList(),
                optionLabel = { "${it.providerId.displayName} · ${it.displayName}" },
                selected = selectedModel,
                onSelect = { selectedModel = it },
                enabled = !running && models.isNotEmpty(),
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
                    item(key = entry.stableKey()) { TranscriptEntry(entry) }
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

        // Input bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                    enabled = !running && selectedRepo != null && selectedBranch != null && selectedModel != null,
                    textStyle = TextStyle(color = colors.onSurface, fontSize = 14.sp),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 22.dp, max = 120.dp),
                )
                if (input.isEmpty()) {
                    Text(
                        Strings.aiAgentInputHint,
                        color = colors.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { submit(input) },
                enabled = !running && input.isNotBlank()
                    && selectedRepo != null && selectedBranch != null && selectedModel != null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (input.isNotBlank() && !running) colors.primary else colors.surfaceVariant),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    null,
                    Modifier.size(18.dp),
                    tint = if (input.isNotBlank() && !running) colors.onPrimary else colors.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── Pieces ───────────────────────────────────────────────────────────────

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
private fun TranscriptEntry(entry: AgentEntry) {
    val colors = MaterialTheme.colorScheme
    when (entry) {
        is AgentEntry.User -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.primary)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(entry.text, color = colors.onPrimary, fontSize = 14.sp)
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
        is AgentEntry.ToolCall -> ToolCallCard(entry, isPending = false, onApprove = {}, onReject = {})
        is AgentEntry.ToolResult -> ToolResultCard(entry)
        is AgentEntry.Pending -> {
            ToolCallCard(
                entry = AgentEntry.ToolCall(entry.pending.call),
                isPending = true,
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
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
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
        val turn = provider.chatWithTools(
            context = context,
            modelId = modelId,
            messages = messages,
            tools = AgentTools.ALL,
            apiKey = apiKey,
        )
        if (turn.assistantText.isNotBlank()) {
            transcript += AgentEntry.Assistant(turn.assistantText)
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

private fun List<AgentEntry>.toAiMessages(): List<AiMessage> =
    mapNotNull {
        when (it) {
            is AgentEntry.User -> AiMessage(role = "user", content = it.text)
            is AgentEntry.Assistant ->
                if (it.text.isBlank()) null else AiMessage(role = "assistant", content = it.text)
            is AgentEntry.ToolCall, is AgentEntry.ToolResult, is AgentEntry.Pending -> null
        }
    }

private fun AgentEntry.stableKey(): String = when (this) {
    is AgentEntry.User -> "u:${text.hashCode()}:${System.identityHashCode(this)}"
    is AgentEntry.Assistant -> "a:${text.hashCode()}:${System.identityHashCode(this)}"
    is AgentEntry.ToolCall -> "tc:${call.id}"
    is AgentEntry.ToolResult -> "tr:${result.callId}"
    is AgentEntry.Pending -> "pending:${pending.call.id}"
}

private sealed class AgentEntry {
    data class User(val text: String) : AgentEntry()
    data class Assistant(val text: String) : AgentEntry()
    data class ToolCall(val call: AiToolCall) : AgentEntry()
    data class ToolResult(val result: AiToolResult) : AgentEntry()
    data class Pending(val pending: PendingApproval) : AgentEntry()
}

private data class PendingApproval(
    val call: AiToolCall,
    val deferred: CompletableDeferred<Boolean>,
)

