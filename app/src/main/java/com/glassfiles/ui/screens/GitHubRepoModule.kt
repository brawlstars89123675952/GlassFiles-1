package com.glassfiles.ui.screens

import android.content.Intent
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import androidx.compose.material.icons.outlined.Link
import com.glassfiles.data.Strings
import com.glassfiles.data.github.*
import com.glassfiles.notifications.GitHubNotificationTarget
import com.glassfiles.ui.components.AiModuleAlertDialog
import com.glassfiles.ui.components.AiModuleGlyph
import com.glassfiles.ui.components.AiModuleGlyphAction
import com.glassfiles.ui.components.AiModulePageBar
import com.glassfiles.ui.components.AiModulePillButton
import com.glassfiles.ui.components.AiModuleHairline
import com.glassfiles.ui.components.AiModuleSearchField
import com.glassfiles.ui.components.AiModuleTextAction
import com.glassfiles.ui.components.AiModuleTextField
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import okhttp3.OkHttpClient
import org.json.JSONObject

// Compact mode — propagates through all sub-screens automatically

internal enum class RepoTab { FILES, COMMITS, ISSUES, PULLS, RELEASES, ACTIONS, BUILDS, HISTORY, PROJECTS, README, CODE_SEARCH }

private const val README_RENDER_TAG = "ReadmeRender"
private const val README_MAX_RENDER_BYTES = 500 * 1024
private const val README_FETCH_TIMEOUT_MS = 10_000L
private const val README_TOTAL_TIMEOUT_MS = 15_000L
private const val README_IMAGE_TIMEOUT_MS = 5_000L
private const val README_MAX_CODE_LINES = 1_000
private const val README_MAX_TABLE_ROWS = 50
private const val README_MAX_LINE_CHARS = 4_000
private const val README_DEFAULT_IMAGE_ASPECT_RATIO = 16f / 9f
private const val README_IMAGE_USER_AGENT = "GlassFiles-Android/1.0"
private val README_PLAIN_URL_REGEX = Regex("""https?://[^\s<>)"]+""")

// Regression test repos (must not freeze):
// - d2phap/imageglass (large with HTML and images)
// - microsoft/vscode (large general)
// - public-apis/public-apis (huge table)

@Composable
internal fun RepoDetailScreen(
    repo: GHRepo,
    onBack: () -> Unit,
    onMinimize: () -> Unit = {},
    onClose: (() -> Unit)? = null,
    initialTarget: GitHubNotificationTarget? = null,
    onInitialTargetConsumed: () -> Unit = {},
    onOpenAiAgent: ((repoFullName: String, branch: String?, prompt: String?) -> Unit)? = null
) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    var selectedTab by rememberSaveable(repo.fullName) { mutableStateOf(RepoTab.FILES) }; var contents by remember { mutableStateOf<List<GHContent>>(emptyList()) }
    var currentPath by rememberSaveable(repo.fullName) { mutableStateOf("") }; var commits by remember { mutableStateOf<List<GHCommit>>(emptyList()) }
    var issues by remember { mutableStateOf<List<GHIssue>>(emptyList()) }; var pulls by remember { mutableStateOf<List<GHPullRequest>>(emptyList()) }
    var releases by remember { mutableStateOf<List<GHRelease>>(emptyList()) }; var readme by remember { mutableStateOf<String?>(null) }
    var readmeBlocks by remember { mutableStateOf<List<ReadmeRenderBlock>?>(null) }
    var readmeError by remember { mutableStateOf<String?>(null) }
    var readmeReloadNonce by remember { mutableIntStateOf(0) }
    var workflowRuns by remember { mutableStateOf<List<GHWorkflowRun>>(emptyList()) }; var selectedRunId by remember { mutableStateOf<Long?>(null) }
    var workflows by remember { mutableStateOf<List<GHWorkflow>>(emptyList()) }; var showDispatch by remember { mutableStateOf(false) }
    var branches by remember { mutableStateOf<List<String>>(emptyList()) }; var loading by remember { mutableStateOf(true) }
    var fileContent by remember { mutableStateOf<String?>(null) }; var openedFile by remember { mutableStateOf<GHContent?>(null) }; var editingFile by remember { mutableStateOf<GHContent?>(null) }
    var repoQuery by rememberSaveable(repo.fullName) { mutableStateOf("") }
    var cloneProgress by remember { mutableStateOf<String?>(null) }; var isStarred by remember { mutableStateOf(false) }
    var selectedBranch by rememberSaveable(repo.fullName) { mutableStateOf(repo.defaultBranch) }
    val childCache = remember(repo.fullName, selectedBranch) { mutableStateMapOf<String, List<GHContent>>() }
    var expandedPaths by rememberSaveable(
        repo.fullName, selectedBranch,
        stateSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() }),
    ) { mutableStateOf(setOf<String>()) }
    var loadingPaths by remember(repo.fullName, selectedBranch) { mutableStateOf(setOf<String>()) }
    var showUpload by remember { mutableStateOf(false) }; var showCreateFile by remember { mutableStateOf(false) }
    var showCreateBranch by remember { mutableStateOf(false) }; var showCreateIssue by remember { mutableStateOf(false) }
    var showCreatePR by remember { mutableStateOf(false) }; var selectedIssue by remember { mutableStateOf<GHIssue?>(null) }
    var selectedCommitSha by remember { mutableStateOf<String?>(null) }; var deleteTarget by remember { mutableStateOf<GHContent?>(null) }
    var showBranchPicker by remember { mutableStateOf(false) }
    var selectedPRNumber by remember { mutableStateOf<Int?>(null) }
    var selectedPullNumber by remember { mutableStateOf<Int?>(null) }
    var showRepoSettings by remember { mutableStateOf(false) }
    var showBranchProtection by remember { mutableStateOf(false) }
    var showCollaborators by remember { mutableStateOf(false) }
    var showTeams by remember { mutableStateOf(false) }
    var showCompare by remember { mutableStateOf(false) }
    var showWebhooks by remember { mutableStateOf(false) }
    var showDiscussions by remember { mutableStateOf(false) }
    var showRulesets by remember { mutableStateOf(false) }
    var showSecurity by remember { mutableStateOf(false) }
    var languages by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }; var contributors by remember { mutableStateOf<List<GHContributor>>(emptyList()) }
    // Pagination
    var commitsPage by rememberSaveable(repo.fullName) { mutableIntStateOf(1) }; var commitsHasMore by rememberSaveable(repo.fullName) { mutableStateOf(true) }
    var issuesPage by rememberSaveable(repo.fullName) { mutableIntStateOf(1) }; var issuesHasMore by rememberSaveable(repo.fullName) { mutableStateOf(true) }
    var pullsPage by rememberSaveable(repo.fullName) { mutableIntStateOf(1) }; var pullsHasMore by rememberSaveable(repo.fullName) { mutableStateOf(true) }
    val filesListState = rememberSaveable(repo.fullName, "files", saver = LazyListState.Saver) { LazyListState(0, 0) }
    val commitsListState = rememberSaveable(repo.fullName, "commits", saver = LazyListState.Saver) { LazyListState(0, 0) }
    val issuesListState = rememberSaveable(repo.fullName, "issues", saver = LazyListState.Saver) { LazyListState(0, 0) }
    val pullsListState = rememberSaveable(repo.fullName, "pulls", saver = LazyListState.Saver) { LazyListState(0, 0) }

    LaunchedEffect(initialTarget) {
        val target = initialTarget ?: return@LaunchedEffect
        when (target.subjectType) {
            "PullRequest" -> {
                selectedTab = RepoTab.PULLS
                target.number?.let { selectedPullNumber = it }
            }
            "Issue" -> {
                selectedTab = RepoTab.ISSUES
                target.number?.let {
                    selectedIssue = GHIssue(
                        number = it,
                        title = "",
                        state = "",
                        author = "",
                        createdAt = "",
                        comments = 0,
                        isPR = false
                    )
                }
            }
            "Release" -> selectedTab = RepoTab.RELEASES
            "Discussion" -> showDiscussions = true
            else -> {
                selectedTab = if (target.number != null) RepoTab.ISSUES else RepoTab.FILES
            }
        }
        onInitialTargetConsumed()
    }

    fun handleRepoBack() {
        when {
            showUpload -> showUpload = false
            showCreateFile -> showCreateFile = false
            showCreateBranch -> showCreateBranch = false
            showCreateIssue -> showCreateIssue = false
            showCreatePR -> showCreatePR = false
            showBranchPicker -> showBranchPicker = false
            showDispatch -> showDispatch = false
            deleteTarget != null -> deleteTarget = null
            editingFile != null -> {
                editingFile = null
                fileContent = null
            }
            openedFile != null -> {
                openedFile = null
                fileContent = null
            }
            selectedIssue != null -> selectedIssue = null
            selectedCommitSha != null -> selectedCommitSha = null
            selectedPRNumber != null -> selectedPRNumber = null
            selectedPullNumber != null -> selectedPullNumber = null
            selectedRunId != null -> selectedRunId = null
            showRepoSettings -> showRepoSettings = false
            showBranchProtection -> showBranchProtection = false
            showCollaborators -> showCollaborators = false
            showTeams -> showTeams = false
            showCompare -> showCompare = false
            showWebhooks -> showWebhooks = false
            showDiscussions -> showDiscussions = false
            showRulesets -> showRulesets = false
            showSecurity -> showSecurity = false
            currentPath.isNotBlank() && selectedTab == RepoTab.FILES -> currentPath = currentPath.substringBeforeLast("/", "")
            else -> onBack()
        }
    }
    BackHandler(onBack = ::handleRepoBack)

    LaunchedEffect(Unit) { isStarred = GitHubManager.isStarred(context, repo.owner, repo.name); branches = GitHubManager.getBranches(context, repo.owner, repo.name) }
    LaunchedEffect(selectedTab, currentPath, selectedBranch, readmeReloadNonce) { loading = true; when (selectedTab) {
        RepoTab.FILES -> {
            val rootItems = GitHubManager.getRepoContents(context, repo.owner, repo.name, currentPath, selectedBranch)
            contents = rootItems
            childCache[currentPath] = rootItems
        }
        RepoTab.COMMITS -> { commitsPage = 1; val r = GitHubManager.getCommits(context, repo.owner, repo.name, 1); commits = r; commitsHasMore = r.size >= 30 }
        RepoTab.ISSUES -> { issuesPage = 1; val r = GitHubManager.getIssues(context, repo.owner, repo.name, page = 1); issues = r; issuesHasMore = r.size >= 30 }
        RepoTab.PULLS -> { pullsPage = 1; val r = GitHubManager.getPullRequests(context, repo.owner, repo.name, page = 1); pulls = r; pullsHasMore = r.size >= 30 }
        RepoTab.RELEASES -> releases = GitHubManager.getReleases(context, repo.owner, repo.name)
        RepoTab.ACTIONS, RepoTab.BUILDS, RepoTab.HISTORY -> { workflowRuns = GitHubManager.getWorkflowRuns(context, repo.owner, repo.name); workflows = GitHubManager.getWorkflows(context, repo.owner, repo.name) }
        RepoTab.PROJECTS -> { /* loaded inside ProjectsTab */ }
        RepoTab.README -> {
            readmeError = null
            readme = null
            readmeBlocks = null
            languages = emptyMap()
            contributors = emptyList()
            val loadStart = System.currentTimeMillis()
            val loadResult = runCatching {
                withTimeout(README_TOTAL_TIMEOUT_MS) readmeLoad@{
                    Log.d(README_RENDER_TAG, "fetch start ${repo.owner}/${repo.name}")
                    val fetched = withTimeout(README_FETCH_TIMEOUT_MS) {
                        fetchReadmeForRender(context, repo.owner, repo.name)
                    }
                    val markdownBytes = fetched.markdown.toByteArray().size
                    Log.d(README_RENDER_TAG, "fetch complete, size=$markdownBytes bytes ${repo.owner}/${repo.name}")
                    if (fetched.markdown.isBlank()) {
                        readme = ""
                        readmeBlocks = emptyList()
                        return@readmeLoad
                    }
                    if (markdownBytes > README_MAX_RENDER_BYTES) {
                        readme = fetched.markdown
                        readmeBlocks = emptyList()
                        readmeError = "README is too large to render safely (${ghFmtSize(markdownBytes.toLong())})."
                        Log.w(README_RENDER_TAG, "parse skipped large README ${repo.owner}/${repo.name} bytes=$markdownBytes")
                        return@readmeLoad
                    }
                    Log.d(README_RENDER_TAG, "parse start ${repo.owner}/${repo.name}")
                    val parsed = withContext(Dispatchers.Default) { parseReadmeBlocks(fetched.markdown, repo, fetched.path) }
                    Log.d(README_RENDER_TAG, "parse complete, blocks=${parsed.size} ${repo.owner}/${repo.name}")
                    readme = fetched.markdown
                    readmeBlocks = parsed
                }
            }
            val loadMs = System.currentTimeMillis() - loadStart
            loadResult.onFailure { throwable ->
                readme = null
                readmeBlocks = emptyList()
                readmeError = "README load timed out or failed"
                languages = emptyMap()
                contributors = emptyList()
                Log.e(README_RENDER_TAG, "README guarded load failed ${repo.owner}/${repo.name} ${loadMs}ms", throwable)
            }.onSuccess {
                Log.d(README_RENDER_TAG, "README guarded load complete ${repo.owner}/${repo.name} ${loadMs}ms")
                if (!readme.isNullOrBlank() && readmeError == null) {
                    scope.launch {
                        languages = withTimeoutOrNull(3_000L) {
                            withContext(Dispatchers.IO) { GitHubManager.getLanguages(context, repo.owner, repo.name) }
                        }.orEmpty()
                    }
                    scope.launch {
                        contributors = withTimeoutOrNull(3_000L) {
                            withContext(Dispatchers.IO) { GitHubManager.getContributors(context, repo.owner, repo.name) }
                        }.orEmpty()
                    }
                }
            }
        }
        RepoTab.CODE_SEARCH -> { /* searches on demand */ }
    }; loading = false }

    if (selectedIssue != null) { IssueDetailScreen(repo, selectedIssue!!.number) { selectedIssue = null }; return }
    if (selectedCommitSha != null) { 
        CommitDiffScreen(repo.owner, repo.name, selectedCommitSha!!) { selectedCommitSha = null }; 
        return 
    }
    if (selectedPRNumber != null) {
        PullRequestDiffScreen(
            repoOwner = repo.owner,
            repoName = repo.name,
            pullNumber = selectedPRNumber!!,
            onBack = { selectedPRNumber = null }
        )
        return
    }
    if (selectedPullNumber != null) {
        PullRequestDetailScreen(
            repo = repo,
            pullNumber = selectedPullNumber!!,
            onBack = { selectedPullNumber = null },
            onOpenFiles = { selectedPRNumber = it }
        )
        return
    }
    if (selectedRunId != null) {
        // B — wire onOpenAiAgent through so the run-detail screen can
        // ask the AI Agent to look at a failed run. Composable receiver
        // is responsible for opening the agent on a prefilled prompt.
        WorkflowRunDetailScreen(
            repo = repo,
            runId = selectedRunId!!,
            onSuggestFix = onOpenAiAgent?.let { open ->
                { prompt -> open(repo.fullName, selectedBranch, prompt) }
            },
            onBack = { selectedRunId = null },
        )
        return
    }
    if (showRepoSettings) { RepoSettingsScreen(repoOwner = repo.owner, repoName = repo.name, onBack = { showRepoSettings = false }, onBranchProtection = { showRepoSettings = false; showBranchProtection = true }, onCollaborators = { showRepoSettings = false; showCollaborators = true }, onTeams = { showRepoSettings = false; showTeams = true }, onWebhooks = { showRepoSettings = false; showWebhooks = true }, onDiscussions = { showRepoSettings = false; showDiscussions = true }, onRulesets = { showRepoSettings = false; showRulesets = true }, onSecurity = { showRepoSettings = false; showSecurity = true }) ; return }
    if (showBranchProtection) { BranchProtectionScreen(repoOwner = repo.owner, repoName = repo.name, branches = branches, onBack = { showBranchProtection = false }) ; return }
    if (showCollaborators) { CollaboratorsScreen(repoOwner = repo.owner, repoName = repo.name) { showCollaborators = false }; return }
    if (showTeams) { RepoTeamsScreen(repoOwner = repo.owner, repoName = repo.name) { showTeams = false }; return }
    if (showCompare) { CompareCommitsScreen(repoOwner = repo.owner, repoName = repo.name, initialBase = selectedBranch) { showCompare = false }; return }
    if (showWebhooks) { WebhooksScreen(repoOwner = repo.owner, repoName = repo.name) { showWebhooks = false }; return }
    if (showDiscussions) { DiscussionsScreen(repoOwner = repo.owner, repoName = repo.name, canWrite = repo.canWrite()) { showDiscussions = false }; return }
    if (showRulesets) { RulesetsScreen(repoOwner = repo.owner, repoName = repo.name) { showRulesets = false }; return }
    if (showSecurity) { SecurityScreen(repoOwner = repo.owner, repoName = repo.name) { showSecurity = false }; return }
    
    // File editor screen
    val safeEditingFile = editingFile
    val safeFileContent = fileContent
    if (safeEditingFile != null && safeFileContent != null) {
        com.glassfiles.ui.screens.CodeEditorScreen(
            repoOwner = repo.owner,
            repoName = repo.name,
            file = safeEditingFile,
            branch = selectedBranch,
            initialContent = safeFileContent,
            onBack = { 
                editingFile = null
                fileContent = null
                scope.launch { contents = GitHubManager.getRepoContents(context, repo.owner, repo.name, currentPath, selectedBranch) }
            },
            onAskAi = onOpenAiAgent?.let { open ->
                { customPrompt ->
                    val prompt = customPrompt ?: "Look at the file `${safeEditingFile.path}` on branch `$selectedBranch` and explain what it does."
                    open(repo.fullName, selectedBranch, prompt)
                }
            }
        )
        return
    }
    if (safeEditingFile != null) {
        LaunchedEffect(safeEditingFile.path, selectedBranch) {
            fileContent = GitHubManager.getFileContent(context, repo.owner, repo.name, safeEditingFile.path, selectedBranch)
        }
        AiModuleSurface {
            val loadingPalette = AiModuleTheme.colors
            Column(Modifier.fillMaxSize().background(loadingPalette.background)) {
                AiModulePageBar(
                    title = "> ${safeEditingFile.name}",
                    subtitle = "loading editor…",
                    onBack = {
                        editingFile = null
                        fileContent = null
                    },
                )
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = loadingPalette.accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
        return
    }
    
    // Releases screen
    if (selectedTab == RepoTab.RELEASES) {
        com.glassfiles.ui.screens.ReleasesScreen(
            repoOwner = repo.owner,
            repoName = repo.name,
            defaultBranch = repo.defaultBranch,
            canWrite = repo.canWrite(),
            onBack = { selectedTab = RepoTab.FILES },
            onReleaseClick = { /* optional */ }
        )
        return
    }
    
    val safeOpenedFile = openedFile
    if (safeFileContent != null && safeOpenedFile != null) {
        val ext = safeOpenedFile.name.substringAfterLast(".", "").lowercase()
        val isImage = ext in listOf("png", "jpg", "jpeg", "gif", "webp", "svg", "ico")
        val isMd = ext in listOf("md", "markdown")
        val cachedLines = remember(safeFileContent) { safeFileContent.lines() }
        AiModuleSurface {
        val viewerPalette = AiModuleTheme.colors
        Column(Modifier.fillMaxSize().background(viewerPalette.background)) {
            AiModulePageBar(
                title = "> ${safeOpenedFile.name}",
                subtitle = if (ext.isNotBlank()) "${cachedLines.size} lines · ${safeFileContent.length} chars · ${ext.uppercase()}" else "${cachedLines.size} lines · ${safeFileContent.length} chars",
                onBack = {
                    fileContent = null
                    openedFile = null
                },
                trailing = {
                    AiModuleGlyphAction(
                        glyph = GhGlyphs.COPY,
                        onClick = {
                            val clip = android.content.ClipData.newPlainText("code", safeFileContent)
                            (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
                            Toast.makeText(context, Strings.done, Toast.LENGTH_SHORT).show()
                        },
                        tint = viewerPalette.textSecondary,
                        fontSize = 12.sp,
                        contentDescription = "copy",
                    )
                    AiModuleGlyphAction(
                        glyph = GhGlyphs.DOWNLOAD,
                        onClick = {
                            scope.launch {
                                val dest = File(
                                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                    "GlassFiles_Git/${safeOpenedFile.name}"
                                )
                                val ok = GitHubManager.downloadFile(
                                    context, repo.owner, repo.name, safeOpenedFile.path, dest, selectedBranch
                                )
                                Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                            }
                        },
                        tint = viewerPalette.textSecondary,
                        fontSize = 12.sp,
                        contentDescription = "download",
                    )
                    AiModuleGlyphAction(
                        glyph = GhGlyphs.EDIT,
                        onClick = { editingFile = safeOpenedFile },
                        tint = viewerPalette.accent,
                        contentDescription = "edit",
                    )
                },
            )
            if (isImage) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Image preview not available\nUse Download to save",
                        fontSize = 12.sp,
                        fontFamily = JetBrainsMono,
                        color = viewerPalette.textMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            } else if (isMd) {
                LazyColumn(Modifier.fillMaxSize().padding(12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(cachedLines.size) { idx -> MarkdownLine(cachedLines[idx]) }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(start = 4.dp, end = 4.dp, top = 4.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(cachedLines.size) { idx ->
                        Row(Modifier.fillMaxWidth()) {
                            Text("${idx + 1}".padStart(4), fontSize = 11.sp, fontFamily = JetBrainsMono, color = viewerPalette.textMuted, modifier = Modifier.padding(end = 10.dp))
                            Text(highlightLine(cachedLines[idx], ext), fontSize = 12.sp, fontFamily = JetBrainsMono, lineHeight = 16.sp)
                        }
                    }
                }
            }
        }
        }; return
    }
    val filteredContents = remember(contents, repoQuery) {
        if (repoQuery.isBlank()) contents else contents.filter { it.name.contains(repoQuery, ignoreCase = true) || it.path.contains(repoQuery, ignoreCase = true) }
    }
    val filteredCommits = remember(commits, repoQuery) {
        if (repoQuery.isBlank()) commits else commits.filter {
            it.message.contains(repoQuery, ignoreCase = true) || it.author.contains(repoQuery, ignoreCase = true) || it.sha.contains(repoQuery, ignoreCase = true)
        }
    }
    val filteredIssues = remember(issues, repoQuery) {
        if (repoQuery.isBlank()) issues else issues.filter {
            it.title.contains(repoQuery, ignoreCase = true) || it.author.contains(repoQuery, ignoreCase = true) || it.number.toString().contains(repoQuery)
        }
    }
    val filteredPulls = remember(pulls, repoQuery) {
        if (repoQuery.isBlank()) pulls else pulls.filter {
            it.title.contains(repoQuery, ignoreCase = true) || it.author.contains(repoQuery, ignoreCase = true) || it.number.toString().contains(repoQuery)
        }
    }


    val canWrite = repo.canWrite()
    val canAdmin = repo.canAdmin()

    AiModuleSurface {
    val palette = AiModuleTheme.colors
    Column(Modifier.fillMaxSize().background(palette.background)) {
        AiModulePageBar(
            title = "> ${repo.name}",
            subtitle = if (currentPath.isNotBlank()) "${repo.fullName} \u00B7 $currentPath" else repo.fullName,
            onBack = ::handleRepoBack,
            trailing = {
                if (onOpenAiAgent != null) {
                    AiModuleGlyphAction(
                        glyph = GhGlyphs.AI,
                        onClick = { onOpenAiAgent(repo.fullName, selectedBranch, null) },
                        tint = palette.accent,
                        contentDescription = "ai agent",
                    )
                }
                AiModuleGlyphAction(
                    glyph = if (isStarred) GhGlyphs.STAR_ON else GhGlyphs.STAR_OFF,
                    onClick = {
                        scope.launch {
                            if (isStarred) GitHubManager.unstarRepo(context, repo.owner, repo.name)
                            else GitHubManager.starRepo(context, repo.owner, repo.name)
                            isStarred = !isStarred
                        }
                    },
                    tint = if (isStarred) palette.warning else palette.textSecondary,
                    contentDescription = "star",
                )
                if (canAdmin) {
                    AiModuleGlyphAction(
                        glyph = GhGlyphs.SETTINGS,
                        onClick = { showRepoSettings = true },
                        tint = palette.textSecondary,
                        contentDescription = "settings",
                    )
                }
                AiModuleGlyphAction(
                    glyph = GhGlyphs.COMPARE,
                    onClick = { showCompare = true },
                    tint = palette.accent,
                    contentDescription = "compare",
                )
                AiModuleGlyphAction(
                    glyph = GhGlyphs.FORK,
                    onClick = {
                        scope.launch {
                            val ok = GitHubManager.forkRepo(context, repo.owner, repo.name)
                            Toast.makeText(context, if (ok) Strings.ghForked else Strings.error, Toast.LENGTH_SHORT).show()
                        }
                    },
                    tint = palette.accent,
                    contentDescription = "fork",
                )
                AiModuleGlyphAction(
                    glyph = GhGlyphs.DOWNLOAD,
                    onClick = {
                        cloneProgress = "Starting..."
                        scope.launch {
                            val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GlassFiles_Git")
                            val ok = GitHubManager.cloneRepo(context, repo.owner, repo.name, dest) { cloneProgress = it }
                            Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                            cloneProgress = null
                        }
                    },
                    tint = palette.accent,
                    fontSize = 12.sp,
                    contentDescription = "clone",
                )
                if (onMinimize != null) {
                    AiModuleGlyphAction(
                        glyph = GhGlyphs.PIP,
                        onClick = onMinimize,
                        tint = palette.textSecondary,
                        contentDescription = "minimize",
                    )
                }
                if (onClose != null) {
                    AiModuleGlyphAction(
                        glyph = GhGlyphs.CLOSE,
                        onClick = onClose,
                        tint = palette.error,
                        contentDescription = "close",
                    )
                }
            },
        )
        if (!canWrite && repo.permissions != null) {
            Row(
                Modifier.fillMaxWidth()
                    .background(palette.surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "\u26A0  read-only",
                    color = palette.warning,
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                )
            }
        }
        if (cloneProgress != null) {
            Row(
                Modifier.fillMaxWidth()
                    .background(palette.surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "\u23F3  cloning: ${cloneProgress!!}",
                    color = palette.warning,
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                )
            }
        }
        // Branch + actions
        Row(
            Modifier.fillMaxWidth()
                .background(palette.surface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(palette.accent.copy(alpha = 0.10f))
                    .border(1.dp, palette.accent.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .clickable { showBranchPicker = true }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "\u2442 ${selectedBranch} \u25BE",
                    color = palette.accent,
                    fontFamily = JetBrainsMono,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            when (selectedTab) {
                RepoTab.FILES -> if (canWrite) {
                    AiModulePillButton(label = "+ file", onClick = { showCreateFile = true })
                    AiModulePillButton(label = "upload \u2191", onClick = { showUpload = true })
                }
                RepoTab.ISSUES -> if (canWrite || repo.permissions == null) {
                    AiModulePillButton(label = "+ issue", onClick = { showCreateIssue = true })
                }
                RepoTab.PULLS -> if (canWrite || repo.permissions == null) {
                    AiModulePillButton(label = "+ pr", onClick = { showCreatePR = true })
                }
                RepoTab.ACTIONS -> if (canWrite) {
                    AiModulePillButton(label = "run \u25B6", onClick = { showDispatch = true })
                }
                else -> {}
            }
        }
        // Tabs
        Row(
            Modifier.fillMaxWidth()
                .background(palette.surface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RepoTab.entries.forEach { tab ->
                val sel = selectedTab == tab
                val label = when (tab) {
                    RepoTab.FILES -> "files"
                    RepoTab.COMMITS -> "commits"
                    RepoTab.ISSUES -> "issues"
                    RepoTab.PULLS -> "pulls"
                    RepoTab.RELEASES -> "releases"
                    RepoTab.ACTIONS -> "actions"
                    RepoTab.BUILDS -> "builds"
                    RepoTab.HISTORY -> "history"
                    RepoTab.PROJECTS -> "projects"
                    RepoTab.README -> "readme"
                    RepoTab.CODE_SEARCH -> "search"
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (sel) palette.accent.copy(alpha = 0.12f) else Color.Transparent)
                        .border(1.dp, if (sel) palette.accent.copy(alpha = 0.55f) else palette.border, RoundedCornerShape(4.dp))
                        .clickable { selectedTab = tab; repoQuery = "" }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Text(
                        label,
                        color = if (sel) palette.accent else palette.textSecondary,
                        fontFamily = JetBrainsMono,
                        fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        if (selectedTab in listOf(RepoTab.FILES, RepoTab.COMMITS, RepoTab.ISSUES, RepoTab.PULLS)) {
            val palette = AiModuleTheme.colors
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "> ",
                        color = palette.textMuted,
                        fontSize = 13.sp,
                        fontFamily = JetBrainsMono,
                    )
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (repoQuery.isEmpty()) {
                            Text(
                                when (selectedTab) {
                                    RepoTab.FILES -> "filter files"
                                    RepoTab.COMMITS -> "filter commits"
                                    RepoTab.ISSUES -> "filter issues"
                                    RepoTab.PULLS -> "filter pull requests"
                                    else -> ""
                                },
                                color = palette.textMuted,
                                fontSize = 13.sp,
                                fontFamily = JetBrainsMono,
                            )
                        }
                        BasicTextField(
                            value = repoQuery,
                            onValueChange = { repoQuery = it },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = palette.textPrimary,
                                fontSize = 13.sp,
                                fontFamily = JetBrainsMono,
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.accent),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (repoQuery.isNotBlank()) {
                        Text(
                            "[x]",
                            color = palette.textMuted,
                            fontSize = 12.sp,
                            fontFamily = JetBrainsMono,
                            modifier = Modifier.clickable { repoQuery = "" }.padding(start = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                AiModuleHairline()
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant.copy(alpha = 0.10f)))
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp) }
        else when (selectedTab) {
            RepoTab.FILES -> FilesTab(
                rootContents = filteredContents,
                childCache = childCache,
                expandedPaths = expandedPaths,
                loadingPaths = loadingPaths,
                listState = filesListState,
                canWrite = canWrite,
                onToggleDir = { item ->
                    if (item.path in expandedPaths) {
                        expandedPaths = expandedPaths - item.path
                    } else {
                        expandedPaths = expandedPaths + item.path
                        if (item.path !in childCache && item.path !in loadingPaths) {
                            loadingPaths = loadingPaths + item.path
                            scope.launch {
                                val children = GitHubManager.getRepoContents(context, repo.owner, repo.name, item.path, selectedBranch)
                                childCache[item.path] = children
                                loadingPaths = loadingPaths - item.path
                            }
                        }
                    }
                },
                onOpenDir = { currentPath = it.path },
                onFileClick = { scope.launch { openedFile = it; fileContent = GitHubManager.getFileContent(context, repo.owner, repo.name, it.path, selectedBranch) } },
                onEdit = { openedFile = null; fileContent = null; editingFile = it },
                onDelete = { deleteTarget = it },
                onDownload = { scope.launch { val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GlassFiles_Git/${it.name}"); val ok = GitHubManager.downloadFile(context, repo.owner, repo.name, it.path, dest, selectedBranch); Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show() } },
                onCopyPath = { item ->
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("path", item.path))
                    Toast.makeText(context, Strings.done, Toast.LENGTH_SHORT).show()
                },
            )
            RepoTab.COMMITS -> CommitsTab(filteredCommits, commitsHasMore, { scope.launch { commitsPage++; val r = GitHubManager.getCommits(context, repo.owner, repo.name, commitsPage); if (r.size < 30) commitsHasMore = false; commits = commits + r } }, listState = commitsListState) { selectedCommitSha = it.sha }
            RepoTab.ISSUES -> IssuesTab(filteredIssues, issuesHasMore, { scope.launch { issuesPage++; val r = GitHubManager.getIssues(context, repo.owner, repo.name, page = issuesPage); if (r.size < 30) issuesHasMore = false; issues = issues + r } }, listState = issuesListState) { selectedIssue = it }
            RepoTab.PULLS -> PullsTab(filteredPulls, repo, { scope.launch { pulls = GitHubManager.getPullRequests(context, repo.owner, repo.name) } }, listState = pullsListState, onOpenDetail = { selectedPullNumber = it.number }) { prNumber -> selectedPRNumber = prNumber }
            RepoTab.RELEASES -> ReleasesTab(releases, repo)
            RepoTab.ACTIONS -> ActionsTab(workflowRuns, repo) { selectedRunId = it.id }
            RepoTab.BUILDS -> BuildsScreen(
                repo = repo,
                branches = branches,
                workflows = workflows,
                selectedBranch = selectedBranch
            ) { runId ->
                selectedTab = RepoTab.ACTIONS
                if (runId != null) selectedRunId = runId
                scope.launch {
                    workflowRuns = GitHubManager.getWorkflowRuns(context, repo.owner, repo.name)
                    workflows = GitHubManager.getWorkflows(context, repo.owner, repo.name)
                }
            }
            RepoTab.HISTORY -> ActionsHistoryTab(workflowRuns, repo) { selectedRunId = it.id }
            RepoTab.PROJECTS -> ProjectsTab(repo)
            RepoTab.README -> ReadmeTab(readme, readmeBlocks, readmeError, languages, contributors, repo) { readmeReloadNonce++ }
            RepoTab.CODE_SEARCH -> CodeSearchTab(repo)
        }
    }
    if (showUpload) UploadDialog(repo, currentPath, selectedBranch, { showUpload = false }) { showUpload = false; scope.launch { contents = GitHubManager.getRepoContents(context, repo.owner, repo.name, currentPath, selectedBranch) } }
    if (showCreateFile) CreateFileDialog(repo, currentPath, selectedBranch, { showCreateFile = false }) { showCreateFile = false; scope.launch { contents = GitHubManager.getRepoContents(context, repo.owner, repo.name, currentPath, selectedBranch) } }
    if (showCreateBranch) CreateBranchDialog(repo, branches, { showCreateBranch = false }) { showCreateBranch = false; scope.launch { branches = GitHubManager.getBranches(context, repo.owner, repo.name) } }
    if (showCreateIssue) CreateIssueDialog(repo, { showCreateIssue = false }) { showCreateIssue = false; scope.launch { issues = GitHubManager.getIssues(context, repo.owner, repo.name) } }
    if (showCreatePR) CreatePRDialog(repo, branches, { showCreatePR = false }) { showCreatePR = false; scope.launch { pulls = GitHubManager.getPullRequests(context, repo.owner, repo.name) } }
    if (deleteTarget != null) DeleteFileDialog(repo, deleteTarget!!, selectedBranch, { deleteTarget = null }) { deleteTarget = null; scope.launch { contents = GitHubManager.getRepoContents(context, repo.owner, repo.name, currentPath, selectedBranch) } }
    if (showBranchPicker) BranchPickerDialog(branches, selectedBranch, canWrite, { selectedBranch = it; showBranchPicker = false }, { showBranchPicker = false }) { showBranchPicker = false; showCreateBranch = true }
    if (showDispatch && workflows.isNotEmpty()) DispatchWorkflowDialog(repo, workflows, branches, { showDispatch = false }) { showDispatch = false; scope.launch { workflowRuns = GitHubManager.getWorkflowRuns(context, repo.owner, repo.name) } }
    }
}

private data class FilesTreeRow(
    val key: String,
    val item: GHContent?,
    val depth: Int,
    val parentLines: List<Boolean>,
    val isLastInParent: Boolean,
    val isLoading: Boolean,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FilesTab(
    rootContents: List<GHContent>,
    childCache: Map<String, List<GHContent>>,
    expandedPaths: Set<String>,
    loadingPaths: Set<String>,
    listState: LazyListState,
    canWrite: Boolean = true,
    onToggleDir: (GHContent) -> Unit,
    onOpenDir: (GHContent) -> Unit,
    onFileClick: (GHContent) -> Unit,
    onEdit: (GHContent) -> Unit,
    onDelete: (GHContent) -> Unit,
    onDownload: (GHContent) -> Unit,
    onCopyPath: (GHContent) -> Unit,
) {
    val palette = AiModuleTheme.colors
    val rowFontSize = 14.sp
    val sizeFontSize = 12.sp
    var menuFor by remember { mutableStateOf<String?>(null) }
    var expandedFile by remember { mutableStateOf<String?>(null) }

    val visibleNodes = remember(rootContents, childCache.toMap(), expandedPaths, loadingPaths) {
        val out = mutableListOf<FilesTreeRow>()
        fun build(items: List<GHContent>, depth: Int, parentLines: List<Boolean>) {
            val sorted = items.sortedWith(compareBy({ it.type != "dir" }, { it.name.lowercase() }))
            sorted.forEachIndexed { i, item ->
                val isLast = i == sorted.lastIndex
                out.add(FilesTreeRow(
                    key = "${depth}|${item.path}",
                    item = item,
                    depth = depth,
                    parentLines = parentLines,
                    isLastInParent = isLast,
                    isLoading = false,
                ))
                if (item.type == "dir" && item.path in expandedPaths) {
                    val nextLines = parentLines + !isLast
                    val children = childCache[item.path]
                    if (children == null) {
                        out.add(FilesTreeRow(
                            key = "${item.path}::loading",
                            item = null,
                            depth = depth + 1,
                            parentLines = nextLines,
                            isLastInParent = true,
                            isLoading = true,
                        ))
                    } else {
                        build(children, depth + 1, nextLines)
                    }
                }
            }
        }
        build(rootContents, 0, emptyList())
        out.toList()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        items(visibleNodes, key = { it.key }) { row ->
            val prefix = buildString {
                row.parentLines.forEach { hasMore -> append(if (hasMore) "\u2502  " else "   ") }
                append(if (row.isLastInParent) "\u2514\u2500 " else "\u251C\u2500 ")
            }
            if (row.isLoading) {
                Text(
                    "${prefix}\u2026 loading",
                    fontFamily = JetBrainsMono,
                    fontSize = rowFontSize,
                    lineHeight = rowFontSize,
                    color = palette.textMuted,
                )
                return@items
            }
            val item = row.item ?: return@items
            val isDir = item.type == "dir"
            val isExpanded = isDir && item.path in expandedPaths
            val toggleGlyph = when {
                isDir && isExpanded -> "\u25BE "
                isDir -> "\u25B8 "
                else -> "  "
            }
            val isHidden = item.name.startsWith(".")
            val nameColor = when {
                isDir -> palette.accent
                isHidden -> palette.textSecondary
                else -> palette.textPrimary
            }
            val displayName = if (isDir) "${item.name}/" else item.name
            Box(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (isDir) onToggleDir(item)
                                else expandedFile = if (expandedFile == item.path) null else item.path
                            },
                            onLongClick = {
                                if (isDir) menuFor = item.path
                                else expandedFile = item.path
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        prefix,
                        fontFamily = JetBrainsMono,
                        fontSize = rowFontSize,
                        lineHeight = rowFontSize,
                        color = palette.textMuted,
                    )
                    Text(
                        toggleGlyph,
                        fontFamily = JetBrainsMono,
                        fontSize = rowFontSize,
                        lineHeight = rowFontSize,
                        color = if (isDir) palette.accent else palette.textMuted,
                    )
                    Text(
                        displayName,
                        fontFamily = JetBrainsMono,
                        fontSize = rowFontSize,
                        lineHeight = rowFontSize,
                        color = nameColor,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!isDir && item.size > 0) {
                        Text(
                            ghFmtSize(item.size),
                            fontFamily = JetBrainsMono,
                            fontSize = sizeFontSize,
                            lineHeight = rowFontSize,
                            color = palette.textMuted,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    } else if (isDir && isExpanded) {
                        val count = childCache[item.path]?.size
                        if (count != null) {
                            Text(
                                "($count)",
                                fontFamily = JetBrainsMono,
                                fontSize = sizeFontSize,
                                lineHeight = rowFontSize,
                                color = palette.textMuted,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
                if (isDir && menuFor == item.path) {
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = { menuFor = null },
                        modifier = Modifier.background(palette.surface),
                    ) {
                        DropdownMenuItem(
                            text = { Text("> open in screen", color = palette.textPrimary, fontFamily = JetBrainsMono, fontSize = 13.sp) },
                            onClick = { menuFor = null; onOpenDir(item) },
                        )
                        DropdownMenuItem(
                            text = { Text("> copy path", color = palette.textPrimary, fontFamily = JetBrainsMono, fontSize = 13.sp) },
                            onClick = { menuFor = null; onCopyPath(item) },
                        )
                    }
                }
            }
            AnimatedVisibility(expandedFile == item.path && !isDir) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = (12 + (row.parentLines.size + 1) * 18).dp,
                            top = 4.dp,
                            bottom = 6.dp,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Chip(Icons.Rounded.Visibility, "view") { onFileClick(item) }
                    if (canWrite) Chip(Icons.Rounded.Edit, Strings.ghEditFile.lowercase()) { onEdit(item) }
                    Chip(Icons.Rounded.Download, Strings.ghDownloadFile.lowercase()) { onDownload(item) }
                    if (canWrite) Chip(Icons.Rounded.Delete, Strings.ghDeleteFile.lowercase(), palette.error) { onDelete(item) }
                }
            }
        }
    }
}

@Composable internal fun Chip(icon: ImageVector, label: String, tint: Color? = null, onClick: () -> Unit) { val chipTint = tint ?: AiModuleTheme.colors.accent; Row(Modifier.clip(RoundedCornerShape(6.dp)).background(chipTint.copy(0.08f)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) { Icon(icon, null, Modifier.size(12.dp), tint = chipTint); Text(label, fontSize = 10.sp, color = chipTint, fontWeight = FontWeight.Medium, fontFamily = JetBrainsMono) } }

private fun fileIcon(name: String): ImageVector = when (name.substringAfterLast(".", "").lowercase()) {
    "kt", "java", "js", "ts", "tsx", "jsx", "py", "rb", "go", "rs", "swift", "c", "cpp", "h", "html", "css", "json", "xml", "yml", "yaml" -> Icons.Rounded.Code
    "md", "markdown", "txt" -> Icons.Rounded.Article
    "png", "jpg", "jpeg", "gif", "webp", "svg" -> Icons.Rounded.Image
    "zip", "apk", "jar", "tar", "gz" -> Icons.Rounded.Archive
    else -> Icons.Rounded.InsertDriveFile
}

@Composable
private fun fileTint(name: String): Color = when (name.substringAfterLast(".", "").lowercase()) {
    "kt" -> Color(0xFFA97BFF)
    "java" -> Color(0xFFB07219)
    "js", "jsx" -> Color(0xFFF1E05A)
    "ts", "tsx" -> Color(0xFF3178C6)
    "md", "markdown" -> MaterialTheme.colorScheme.primary
    "png", "jpg", "jpeg", "gif", "webp", "svg" -> MaterialTheme.colorScheme.primary
    else -> TextSecondary
}

@Composable
internal fun CommitsTab(commits: List<GHCommit>, hasMore: Boolean, onLoadMore: () -> Unit, listState: LazyListState, onClick: (GHCommit) -> Unit) { LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 16.dp)) { items(commits) { c ->
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp).ghGlassCard(14.dp).clickable { onClick(c) }.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        if (c.avatarUrl.isNotBlank()) AsyncImage(c.avatarUrl, c.author, Modifier.size(34.dp).clip(CircleShape))
        else Box(Modifier.size(34.dp).clip(CircleShape).background(colors.primary.copy(0.12f)), contentAlignment = Alignment.Center) { Text(c.sha.take(2).uppercase(), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = colors.primary, letterSpacing = 0.6.sp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(c.message.lines().firstOrNull().orEmpty(), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Text(c.author.ifBlank { "unknown" }, fontSize = 11.sp, color = colors.primary, fontWeight = FontWeight.Medium)
                Text(c.sha.take(7), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextTertiary, letterSpacing = 0.5.sp)
                Text(c.date.take(10), fontSize = 11.sp, color = TextTertiary)
            }
        }
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = TextTertiary)
    }
}; if (hasMore) item { Box(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF21262D)).clickable { onLoadMore() }.padding(12.dp), contentAlignment = Alignment.Center) { Text("Load more", color = Blue, fontSize = 14.sp, fontWeight = FontWeight.Medium) } } } }

@Composable
internal fun IssuesTab(issues: List<GHIssue>, hasMore: Boolean, onLoadMore: () -> Unit, listState: LazyListState, onClick: (GHIssue) -> Unit) { LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 16.dp)) { items(issues) { issue ->
    val colors = MaterialTheme.colorScheme
    val stateColor = if (issue.state == "open") GitHubSuccessGreen else GitHubErrorRed
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp).height(IntrinsicSize.Min).ghGlassCard(14.dp).clickable { onClick(issue) }) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(stateColor))
        Row(Modifier.weight(1f).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(stateColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(stateColor))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(issue.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.onSurface, maxLines = 2, lineHeight = 18.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("#${issue.number}", fontSize = 11.sp, color = colors.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                    Text(issue.author, fontSize = 11.sp, color = colors.onSurfaceVariant, fontWeight = FontWeight.Medium)
                    Text(if (issue.isPR) "PR" else issue.state.uppercase(), fontSize = 10.sp, color = stateColor, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
                    if (issue.comments > 0) Text("${formatGitHubNumber(issue.comments)} comments", fontSize = 11.sp, color = colors.onSurfaceVariant)
                }
            }
            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
        }
    }
}; if (hasMore) item { Box(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).clickable { onLoadMore() }.padding(12.dp), contentAlignment = Alignment.Center) { Text("Load more", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium) } } } }

@Composable
internal fun PullsTab(
    pulls: List<GHPullRequest>,
    repo: GHRepo,
    onRefresh: () -> Unit,
    listState: LazyListState,
    onOpenDetail: (GHPullRequest) -> Unit = {},
    onFilesClick: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reviewTarget by remember { mutableStateOf<GHPullRequest?>(null) }
    var checkRunTarget by remember { mutableStateOf<GHPullRequest?>(null) }

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
        items(pulls) { pr ->
            val colors = MaterialTheme.colorScheme
            val prColor = pullStateColor(pr)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp).height(IntrinsicSize.Min).ghGlassCard(14.dp).clickable { onOpenDetail(pr) }) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(prColor))
                Column(Modifier.weight(1f).padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(prColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.CallMerge, null, Modifier.size(16.dp), tint = prColor)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(pr.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.onSurface, lineHeight = 18.sp, maxLines = 2)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                                Text("#${pr.number}", fontSize = 11.sp, color = colors.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                                Text("${pr.head} → ${pr.base}", fontSize = 11.sp, color = colors.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                                Text(pr.author, fontSize = 11.sp, color = colors.onSurfaceVariant)
                                Text(if (pr.merged) "MERGED" else pr.state.uppercase(), fontSize = 10.sp, color = prColor, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
                                if (pr.draft) Text("DRAFT", fontSize = 10.sp, color = colors.onSurfaceVariant, letterSpacing = 0.6.sp)
                                if (pr.reviewComments > 0) Text("${formatGitHubNumber(pr.reviewComments)} review comments", fontSize = 11.sp, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                    if (pr.body.isNotBlank()) {
                        Text(
                            pr.body.replace("\n", " ").take(140),
                            fontSize = 11.sp,
                            color = colors.onSurfaceVariant,
                            maxLines = 2,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(top = 8.dp, start = 38.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 38.dp).horizontalScroll(rememberScrollState())) {
                        Chip(Icons.Rounded.Visibility, "Details") { onOpenDetail(pr) }
                        Chip(Icons.Rounded.Article, "Files") { onFilesClick(pr.number) }
                        Chip(Icons.Rounded.RateReview, "Review") { reviewTarget = pr }
                        Chip(Icons.Rounded.FactCheck, "Checks") { checkRunTarget = pr }
                        if (pr.state == "open" && !pr.merged && !pr.draft) {
                            Chip(Icons.Rounded.CallMerge, Strings.ghMerge, GitHubSuccessGreen) {
                                scope.launch {
                                    val ok = GitHubManager.mergePullRequest(context, repo.owner, repo.name, pr.number)
                                    Toast.makeText(context, if (ok) Strings.ghMerged else Strings.error, Toast.LENGTH_SHORT).show()
                                    if (ok) onRefresh()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (reviewTarget != null) {
        PullReviewDialog(
            repo = repo,
            pr = reviewTarget!!,
            onDismiss = { reviewTarget = null },
            onDone = {
                reviewTarget = null
                onRefresh()
            }
        )
    }

    if (checkRunTarget != null) {
        CheckRunsScreen(
            repoOwner = repo.owner,
            repoName = repo.name,
            ref = checkRunTarget!!.headSha.ifBlank { checkRunTarget!!.head },
            onBack = { checkRunTarget = null }
        )
    }
}

@Composable
private fun PullRequestDetailScreen(
    repo: GHRepo,
    pullNumber: Int,
    onBack: () -> Unit,
    onOpenFiles: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pr by remember { mutableStateOf<GHPullRequest?>(null) }
    var files by remember { mutableStateOf<List<GHPullFile>>(emptyList()) }
    var comments by remember { mutableStateOf<List<GHReviewComment>>(emptyList()) }
    var checks by remember { mutableStateOf<List<GHCheckRun>>(emptyList()) }
    var reviews by remember { mutableStateOf<List<GHPullReview>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showReview by remember { mutableStateOf(false) }
    var showChecks by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showReviewers by remember { mutableStateOf(false) }
    var showReviews by remember { mutableStateOf(false) }
    var showMerge by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    // One-shot AI summary state. Populated by the chip in the action
    // row below; nulled out when the dialog is dismissed.
    var aiSummary by remember { mutableStateOf<String?>(null) }
    var aiSummaryLoading by remember { mutableStateOf(false) }
    var aiSummaryError by remember { mutableStateOf<String?>(null) }
    var aiSummaryShown by remember { mutableStateOf(false) }

    suspend fun refreshPull() {
        loading = true
        val detail = GitHubManager.getPullRequestDetail(context, repo.owner, repo.name, pullNumber)
        pr = detail
        files = GitHubManager.getPullRequestFiles(context, repo.owner, repo.name, pullNumber)
        comments = GitHubManager.getPullRequestReviewComments(context, repo.owner, repo.name, pullNumber)
        reviews = GitHubManager.getPullRequestReviews(context, repo.owner, repo.name, pullNumber)
        checks = detail?.let { GitHubManager.getPullRequestCheckRuns(context, repo.owner, repo.name, it.headSha.ifBlank { it.head }) }.orEmpty()
        loading = false
    }

    LaunchedEffect(pullNumber) { refreshPull() }

    val current = pr
    val currentHtmlUrl = current?.htmlUrl.orEmpty()
    if (showChecks && current != null) {
        CheckRunsScreen(
            repoOwner = repo.owner,
            repoName = repo.name,
            ref = current.headSha.ifBlank { current.head },
            onBack = { showChecks = false }
        )
        return
    }

    AiModuleSurface {
    val prPalette = AiModuleTheme.colors
    Column(Modifier.fillMaxSize().background(prPalette.background)) {
        AiModulePageBar(
            title = "> pr #$pullNumber",
            subtitle = repo.name,
            onBack = onBack,
            trailing = {
                AiModuleGlyphAction(
                    glyph = GhGlyphs.REFRESH,
                    onClick = { scope.launch { refreshPull() } },
                    tint = prPalette.accent,
                    contentDescription = "refresh",
                )
                if (currentHtmlUrl.isNotBlank()) {
                    AiModuleGlyphAction(
                        glyph = GhGlyphs.OPEN_NEW,
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentHtmlUrl)))
                            } catch (_: Exception) {
                                Toast.makeText(context, Strings.error, Toast.LENGTH_SHORT).show()
                            }
                        },
                        tint = prPalette.accent,
                        contentDescription = "open in browser",
                    )
                }
            }
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
            return@Column
        }

        if (current == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Failed to load pull request", color = TextTertiary, fontSize = 14.sp)
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.fillMaxWidth().ghGlassCard(16.dp).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.CallMerge, null, Modifier.size(22.dp), tint = pullStateColor(current))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(current.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary, lineHeight = 20.sp)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                PullBadge(pullStateLabel(current), pullStateColor(current))
                                if (current.draft) PullBadge("Draft", TextTertiary)
                                PullBadge("${current.head} -> ${current.base}", Blue)
                            }
                            Text("Opened by ${current.author}", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                    if (current.body.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(current.body, fontSize = 12.sp, color = TextSecondary, maxLines = 8, overflow = TextOverflow.Ellipsis)
                    }
                    if (current.requestedReviewers.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            current.requestedReviewers.forEach { reviewer -> PullBadge("review: $reviewer", TextSecondary) }
                        }
                    }
                }
            }

            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PullMetric("${current.commits.takeIf { it > 0 } ?: "?"}", "commits", Blue)
                    PullMetric("${current.changedFiles.takeIf { it > 0 } ?: files.size}", "files", TextSecondary)
                    PullMetric("+${current.additions.takeIf { it > 0 } ?: files.sumOf { f -> f.additions }}", "added", Color(0xFF34C759))
                    PullMetric("-${current.deletions.takeIf { it > 0 } ?: files.sumOf { f -> f.deletions }}", "deleted", Color(0xFFFF3B30))
                    PullMetric("${comments.size}", "review comments", TextSecondary)
                    PullMetric("${reviews.size}", "reviews", TextSecondary)
                }
            }

            item {
                PullMergeabilityCard(current, checks)
            }

            item {
                val canWrite = repo.canWrite()
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(
                        Icons.Rounded.AutoAwesome,
                        if (aiSummaryLoading) Strings.aiSummaryLoading else Strings.aiSummary,
                        Blue,
                    ) {
                        if (aiSummaryLoading) return@Chip
                        aiSummaryShown = true
                        aiSummaryError = null
                        if (aiSummary == null) {
                            aiSummaryLoading = true
                            scope.launch {
                                try {
                                    aiSummary = generatePullRequestSummary(
                                        context = context,
                                        pr = current,
                                        files = files,
                                    )
                                } catch (e: Exception) {
                                    aiSummaryError = e.message ?: e.javaClass.simpleName
                                } finally {
                                    aiSummaryLoading = false
                                }
                            }
                        }
                    }
                    if (canWrite) Chip(Icons.Rounded.Edit, "Edit", TextSecondary) { showEdit = true }
                    Chip(Icons.Rounded.Article, "Files", Blue) { onOpenFiles(pullNumber) }
                    if (canWrite) Chip(Icons.Rounded.RateReview, "Review", Blue) { showReview = true }
                    if (canWrite) Chip(Icons.Rounded.Group, "Reviewers", Blue) { showReviewers = true }
                    Chip(Icons.Rounded.History, "Reviews", TextSecondary) { showReviews = true }
                    Chip(Icons.Rounded.FactCheck, "Checks", Blue) { showChecks = true }
                    if (canWrite && current.state == "open" && !current.merged && !current.draft) {
                        Chip(Icons.Rounded.CallMerge, if (merging) "Merging..." else Strings.ghMerge, Color(0xFF34C759)) {
                            if (!merging) showMerge = true
                        }
                    }
                }
            }

            item {
                Text("Changed files", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }
            if (files.isEmpty()) {
                item { Text("No files loaded", fontSize = 13.sp, color = TextTertiary) }
            } else {
                items(files.take(12)) { file ->
                    PullFileSummaryRow(file)
                }
                if (files.size > 12) {
                    item {
                        Text("+${files.size - 12} more files", fontSize = 11.sp, color = TextTertiary)
                    }
                }
            }
        }
    }
    }

    if (showReview && current != null) {
        PullReviewDialog(
            repo = repo,
            pr = current,
            onDismiss = { showReview = false },
            onDone = {
                showReview = false
                scope.launch { refreshPull() }
            }
        )
    }
    if (showEdit && current != null) {
        PullEditDialog(
            repo = repo,
            pr = current,
            onDismiss = { showEdit = false },
            onDone = {
                showEdit = false
                scope.launch { refreshPull() }
            }
        )
    }
    if (showReviewers && current != null) {
        PullReviewersDialog(
            repo = repo,
            pr = current,
            onDismiss = { showReviewers = false },
            onDone = {
                showReviewers = false
                scope.launch { refreshPull() }
            }
        )
    }
    if (showReviews && current != null) {
        PullReviewHistoryDialog(
            repo = repo,
            pr = current,
            reviews = reviews,
            onDismiss = { showReviews = false },
            onChanged = { scope.launch { refreshPull() } }
        )
    }
    if (showMerge && current != null) {
        PullMergeDialog(
            pr = current,
            merging = merging,
            onDismiss = { showMerge = false },
            onMerge = { method, title, message ->
                if (!merging) {
                    merging = true
                    scope.launch {
                        val ok = GitHubManager.mergePullRequest(context, repo.owner, repo.name, pullNumber, message = message, method = method, title = title)
                        merging = false
                        showMerge = false
                        Toast.makeText(context, if (ok) Strings.ghMerged else Strings.error, Toast.LENGTH_SHORT).show()
                        if (ok) refreshPull()
                    }
                }
            }
        )
    }
    if (aiSummaryShown) {
        AiPullSummaryDialog(
            loading = aiSummaryLoading,
            summary = aiSummary,
            error = aiSummaryError,
            onRegenerate = {
                if (current == null) return@AiPullSummaryDialog
                aiSummaryLoading = true
                aiSummary = null
                aiSummaryError = null
                scope.launch {
                    try {
                        aiSummary = generatePullRequestSummary(
                            context = context,
                            pr = current,
                            files = files,
                        )
                    } catch (e: Exception) {
                        aiSummaryError = e.message ?: e.javaClass.simpleName
                    } finally {
                        aiSummaryLoading = false
                    }
                }
            },
            onDismiss = { aiSummaryShown = false },
        )
    }
}

/**
 * One-shot prompt builder for the "AI summary" chip on the PR detail
 * screen. Sends the PR's title / description / head & base / commit
 * count / changed-file list (with patches truncated for long diffs)
 * to the picked model and asks for a concise three-bullet TL;DR.
 */
private suspend fun generatePullRequestSummary(
    context: Context,
    pr: GHPullRequest,
    files: List<GHPullFile>,
): String {
    val payload = buildString {
        appendLine("Title: ${pr.title}")
        appendLine("State: ${pr.state}${if (pr.draft) " (draft)" else ""}${if (pr.merged) " (merged)" else ""}")
        appendLine("Author: ${pr.author}")
        appendLine("Branches: ${pr.head} -> ${pr.base}")
        appendLine("Stats: ${pr.commits} commits, +${pr.additions}/-${pr.deletions}, ${pr.changedFiles} files")
        if (pr.body.isNotBlank()) {
            appendLine()
            appendLine("Description:")
            appendLine(pr.body.take(2000))
        }
        if (files.isNotEmpty()) {
            appendLine()
            appendLine("Changed files (${files.size}):")
            // 12 files * 600 char patch = ~7k tokens of context max.
            // Beyond that the model can't keep it all in mind anyway.
            files.take(12).forEach { f ->
                append("- ${f.filename}  (+${f.additions} -${f.deletions})")
                if (f.patch.isNotBlank()) {
                    appendLine()
                    appendLine(f.patch.take(600))
                } else {
                    appendLine()
                }
            }
            if (files.size > 12) appendLine("[…${files.size - 12} more files omitted]")
        }
    }
    val systemPrompt =
        "You are a code-review assistant. Given the metadata and diff of a GitHub pull request, " +
        "produce a concise summary that helps a reviewer decide whether to approve. " +
        "Use 3-5 short bullet points. First bullet is the one-line intent. " +
        "Mention notable risks or test gaps if visible from the diff. Plain text, no markdown headers."
    return com.glassfiles.data.ai.AiOneShot.complete(
        context = context,
        systemPrompt = systemPrompt,
        userPrompt = payload,
    )
}

@Composable
private fun AiPullSummaryDialog(
    loading: Boolean,
    summary: String?,
    error: String?,
    onRegenerate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp), tint = colors.primary)
                Spacer(Modifier.width(8.dp))
                Text(Strings.aiSummary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Box(
                Modifier
                    .heightIn(min = 80.dp, max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when {
                    loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(Strings.aiSummaryLoading, fontSize = 13.sp, color = colors.onSurfaceVariant)
                    }
                    error != null -> Text(
                        error,
                        fontSize = 13.sp,
                        color = colors.error,
                    )
                    summary != null -> Text(
                        summary,
                        fontSize = 13.sp,
                        color = colors.onSurface,
                        lineHeight = 18.sp,
                    )
                    else -> Text(Strings.aiSummaryEmpty, fontSize = 13.sp, color = colors.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(Strings.close) }
        },
        dismissButton = {
            TextButton(onClick = onRegenerate, enabled = !loading) {
                Text(Strings.aiSummaryRegenerate)
            }
        },
    )
}

@Composable
private fun PullBadge(text: String, color: Color) {
    Text(
        text,
        fontSize = 10.sp,
        color = color,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(color.copy(0.1f)).padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

@Composable
private fun PullMetric(value: String, label: String, color: Color) {
    Row(
        Modifier.ghGlassCard(10.dp).padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(value, fontSize = 15.sp, color = color, fontWeight = FontWeight.Light, fontFamily = FontFamily.Monospace)
        Text(label.uppercase(), fontSize = 9.sp, color = TextSecondary, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp)
    }
}

@Composable
private fun PullMergeabilityCard(pr: GHPullRequest, checks: List<GHCheckRun>) {
    val failedChecks = checks.count { it.conclusion in listOf("failure", "cancelled", "timed_out", "action_required") }
    val activeChecks = checks.count { it.status != "completed" }
    val successChecks = checks.count { it.conclusion == "success" }
    val mergeColor = pullMergeColor(pr)
    Column(Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Verified, null, Modifier.size(18.dp), tint = mergeColor)
            Text(pullMergeText(pr), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PullBadge("$successChecks successful", Color(0xFF34C759))
            if (activeChecks > 0) PullBadge("$activeChecks active", Blue)
            if (failedChecks > 0) PullBadge("$failedChecks failed", Color(0xFFFF3B30))
            if (checks.isEmpty()) PullBadge("No checks", TextTertiary)
        }
        if (pr.mergeableState.isNotBlank()) {
            Text("Merge state: ${pr.mergeableState}", fontSize = 11.sp, color = TextTertiary)
        }
    }
}

@Composable
private fun PullFileSummaryRow(file: GHPullFile) {
    val color = when (file.status) {
        "added" -> Color(0xFF34C759)
        "removed" -> Color(0xFFFF3B30)
        "renamed" -> Color(0xFF5856D6)
        else -> Color(0xFFFF9500)
    }
    Row(
        Modifier.fillMaxWidth().ghGlassCard(12.dp).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(file.filename, fontSize = 12.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("+${file.additions}", fontSize = 11.sp, color = Color(0xFF34C759))
        Text("-${file.deletions}", fontSize = 11.sp, color = Color(0xFFFF3B30))
    }
}

private fun pullStateLabel(pr: GHPullRequest): String = when {
    pr.merged -> "Merged"
    pr.state == "closed" -> "Closed"
    pr.state == "open" -> "Open"
    else -> pr.state.replaceFirstChar { it.uppercase() }
}

@Composable
private fun pullStateColor(pr: GHPullRequest): Color = when {
    pr.draft -> MaterialTheme.colorScheme.outline
    pr.merged -> GitHubMergedPurple
    pr.state == "open" -> GitHubSuccessGreen
    else -> GitHubErrorRed
}

@Composable
private fun pullMergeColor(pr: GHPullRequest): Color = when {
    pr.draft -> MaterialTheme.colorScheme.onSurfaceVariant
    pr.mergeable == true && pr.mergeableState in listOf("clean", "has_hooks", "unstable") -> GitHubSuccessGreen
    pr.mergeable == false || pr.mergeableState in listOf("dirty", "blocked") -> GitHubErrorRed
    else -> MaterialTheme.colorScheme.primary
}

private fun pullMergeText(pr: GHPullRequest): String = when {
    pr.draft -> "Draft pull request cannot be merged yet"
    pr.merged -> "Pull request has been merged"
    pr.state == "closed" -> "Pull request is closed"
    pr.mergeable == true && pr.mergeableState == "clean" -> "Ready to merge"
    pr.mergeable == true && pr.mergeableState == "unstable" -> "Mergeable, but checks need attention"
    pr.mergeableState == "blocked" -> "Blocked by required checks or reviews"
    pr.mergeable == false || pr.mergeableState == "dirty" -> "Cannot merge cleanly"
    else -> "Mergeability is being calculated"
}

@Composable
internal fun ReleasesTab(releases: List<GHRelease>, repo: GHRepo) { val context = LocalContext.current; val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) { items(releases) { r -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp).ghGlassCard(14.dp).padding(14.dp)) {
        val colors = MaterialTheme.colorScheme
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Rounded.NewReleases, null, Modifier.size(20.dp), tint = if (r.prerelease) GitHubWarningAmber() else GitHubSuccessGreen); Text(r.name.ifBlank { r.tag }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface); if (r.prerelease) Text(Strings.ghPrerelease, fontSize = 10.sp, color = GitHubWarningAmber(), modifier = Modifier.background(GitHubWarningAmber().copy(0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) }
        Text(r.tag, fontSize = 12.sp, color = colors.onSurfaceVariant, fontFamily = FontFamily.Monospace)
        if (r.body.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            GitHubMarkdownDocument(r.body, repo)
        }
        if (r.assets.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                r.assets.forEach { a -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.surfaceVariant).clickable { scope.launch { val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GlassFiles_Git/${a.name}"); GitHubManager.downloadFile(context, repo.owner, repo.name, a.downloadUrl, dest) } }.padding(9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(releaseAssetIcon(a.name), null, Modifier.size(24.dp), tint = colors.primary.copy(alpha = 0.72f)); Column(Modifier.weight(1f)) { Text(a.name, fontSize = 12.sp, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${ghFmtSize(a.size)} · ${formatGitHubNumber(a.downloadCount)} downloads", fontSize = 10.sp, color = colors.onSurfaceVariant) }; Icon(Icons.Rounded.Download, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant) } }
            }
        }
    } } }
}

private data class ReadmeFetchResult(val markdown: String, val path: String)

private suspend fun fetchReadmeForRender(context: Context, owner: String, repo: String): ReadmeFetchResult = withContext(Dispatchers.IO) {
    val encodedOwner = owner.encodeGithubPathPart()
    val encodedRepo = repo.encodeGithubPathPart()
    val url = "https://api.github.com/repos/$encodedOwner/$encodedRepo/readme"
    val token = GitHubManager.getToken(context)
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "GlassFiles")
        setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
        connectTimeout = README_FETCH_TIMEOUT_MS.toInt()
        readTimeout = README_FETCH_TIMEOUT_MS.toInt()
    }
    try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            Log.w(README_RENDER_TAG, "fetch HTTP $code $owner/$repo body=${body.take(160)}")
            return@withContext ReadmeFetchResult("", "")
        }
        val json = JSONObject(body)
        val content = json.optString("content", "")
        val path = json.optString("path", "")
        val markdown = if (content.isBlank()) {
            ""
        } else {
            String(android.util.Base64.decode(content.replace("\n", ""), android.util.Base64.DEFAULT))
        }
        ReadmeFetchResult(markdown, path)
    } finally {
        connection.disconnect()
    }
}

private fun String.encodeGithubPathPart(): String =
    URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")

@Composable
private fun ReadmeTab(readme: String?, blocks: List<ReadmeRenderBlock>?, error: String?, languages: Map<String, Long>, contributors: List<GHContributor>, repo: GHRepo, onRetry: () -> Unit) {
    val readmeImageLoader = rememberReadmeImageLoader(LocalContext.current)
    val colors = MaterialTheme.colorScheme
    val total = languages.values.sum().toFloat().coerceAtLeast(1f)
    var rawView by remember(readme) { mutableStateOf(false) }
    var visibleCount by remember(readme) { mutableIntStateOf(250) }
    var renderCompleteLogged by remember(readme, blocks?.size ?: -1) { mutableStateOf(false) }
    val safeBlocks = blocks.orEmpty()
    val shownBlocks = safeBlocks.take(visibleCount)

    if (!readme.isNullOrBlank() && error == null && !rawView) {
        LaunchedEffect(readme, safeBlocks.size) {
            Log.d(README_RENDER_TAG, "render start ${repo.owner}/${repo.name} blocks=${safeBlocks.size}")
        }
        SideEffect {
            if (!renderCompleteLogged) {
                Log.d(README_RENDER_TAG, "render complete ${repo.owner}/${repo.name} blocks=${safeBlocks.size}")
                renderCompleteLogged = true
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        if (languages.isNotEmpty()) item { Text(Strings.ghLanguages, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onSurface); Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) { languages.forEach { (l, b) -> Box(Modifier.weight(b / total).fillMaxHeight().background(langColor(l))) } }; Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) { languages.forEach { (l, b) -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Box(Modifier.size(8.dp).clip(CircleShape).background(langColor(l))); Text("$l ${"%.1f".format(b / total * 100)}%", fontSize = 11.sp, color = colors.onSurfaceVariant) } } }; Spacer(Modifier.height(16.dp)) }
        if (contributors.isNotEmpty()) item { Text(Strings.ghContributors, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onSurface); Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { contributors.forEach { c -> Column(horizontalAlignment = Alignment.CenterHorizontally) { AsyncImage(c.avatarUrl, c.login, Modifier.size(36.dp).clip(CircleShape)); Text(c.login, fontSize = 10.sp, color = colors.onSurfaceVariant, maxLines = 1); Text(formatGitHubNumber(c.contributions), fontSize = 9.sp, color = colors.onSurfaceVariant) } } }; Spacer(Modifier.height(16.dp)) }
        item {
            Text(Strings.ghReadme, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            Spacer(Modifier.height(8.dp))
        }
        when {
            error != null -> item {
                ReadmeErrorCard(error, readme.orEmpty(), repo, onRetry = onRetry)
            }
            readme.isNullOrBlank() -> item {
                Text(Strings.ghNoReadme, fontSize = 14.sp, color = colors.onSurfaceVariant)
            }
            rawView -> item {
                Box(Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(14.dp)) { ReadmeRawBlock(readme.orEmpty()) }
            }
            shownBlocks.isEmpty() -> item {
                ReadmeErrorCard("README has no renderable markdown blocks.", readme.orEmpty(), repo, onViewRaw = { rawView = true })
            }
            else -> {
                item(key = "readme_doc_top_${repo.owner}_${repo.name}") {
                    Spacer(Modifier.height(6.dp))
                }
                items(shownBlocks, key = { it.stableId }) { block ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 4.dp)
                    ) {
                        ReadmeBlockView(block, readmeImageLoader)
                    }
                }
                item(key = "readme_doc_bottom_${repo.owner}_${repo.name}_${shownBlocks.size}") {
                    Spacer(Modifier.height(10.dp))
                }
                if (visibleCount < safeBlocks.size) {
                    item {
                        TextButton(onClick = { visibleCount += 250 }) {
                            Text("Expand more README content (${safeBlocks.size - visibleCount} hidden)", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GitHubMarkdownDocument(
    markdown: String,
    repo: GHRepo,
    readmePath: String = "",
    modifier: Modifier = Modifier,
    maxBlocks: Int? = null
) {
    val imageLoader = rememberReadmeImageLoader(LocalContext.current)
    var blocks by remember(markdown, repo.owner, repo.name, repo.defaultBranch, readmePath) { mutableStateOf<List<ReadmeRenderBlock>?>(null) }
    LaunchedEffect(markdown, repo.owner, repo.name, repo.defaultBranch, readmePath) {
        blocks = withContext(Dispatchers.Default) { parseReadmeBlocks(markdown, repo, readmePath) }
    }
    val safeBlocks = blocks
    if (safeBlocks == null) {
        Text("Rendering markdown...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            safeBlocks.let { if (maxBlocks == null) it else it.take(maxBlocks) }.forEach { block ->
                ReadmeBlockView(block, imageLoader)
            }
        }
    }
}

@Composable
internal fun ReadmeBlockView(block: ReadmeRenderBlock, imageLoader: ImageLoader) {
    when (block) {
        is ReadmeRenderBlock.Heading -> ReadmeHeading(block)
        is ReadmeRenderBlock.Paragraph -> ReadmeText(block.text)
        is ReadmeRenderBlock.Bullet -> ReadmeBullet(block.text, block.ordered, block.checked, block.level)
        is ReadmeRenderBlock.Quote -> Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(3.dp).heightIn(min = 20.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)))
            ReadmeText(block.text, modifier = Modifier.weight(1f))
        }
        is ReadmeRenderBlock.Rule -> Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(0.8.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)))
        is ReadmeRenderBlock.Image -> ReadmeImage(block, imageLoader)
        is ReadmeRenderBlock.ImageRow -> ReadmeImageRow(block.images, imageLoader)
        is ReadmeRenderBlock.Code -> ReadmeCodeBlock(block)
        is ReadmeRenderBlock.Table -> ReadmeTable(block.rows)
        is ReadmeRenderBlock.Link -> ReadmeLinkCard(block.text, block.url)
    }
}

@Composable
private fun ReadmeHeading(block: ReadmeRenderBlock.Heading) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (block.anchor.isNotBlank()) {
                Icon(Icons.Outlined.Link, null, Modifier.size(14.dp), tint = colors.onSurfaceVariant.copy(alpha = 0.4f))
            }
            val size = when (block.level) {
                1 -> 22.sp
                2 -> 18.sp
                else -> 16.sp
            }
            Text(
                readmeInlineAnnotated(block.text),
                fontSize = size,
                fontWeight = if (block.level <= 2) FontWeight.Bold else FontWeight.SemiBold,
                color = colors.onSurface,
                lineHeight = (size.value + 5).sp,
                modifier = Modifier.weight(1f)
            )
        }
        if (block.level == 2) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.outlineVariant.copy(alpha = 0.3f)))
        }
    }
}

@Composable
private fun ReadmeErrorCard(message: String, raw: String, repo: GHRepo, onViewRaw: (() -> Unit)? = null, onRetry: (() -> Unit)? = null) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(message, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        Text("README rendering was stopped to keep the app responsive.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            if (onRetry != null) Chip(Icons.Rounded.Refresh, "Retry", MaterialTheme.colorScheme.primary, onRetry)
            if (raw.isNotBlank() && onViewRaw != null) Chip(Icons.Rounded.Article, "View raw", MaterialTheme.colorScheme.primary, onViewRaw)
            Chip(Icons.Rounded.OpenInNew, "Open in browser", MaterialTheme.colorScheme.primary) { context.openReadmeUrl(readmeBrowserUrl(repo)) }
        }
    }
}

@Composable
private fun ReadmeRawBlock(markdown: String) {
    val preview = remember(markdown) { markdown.lineSequence().take(500).joinToString("\n") { it.take(README_MAX_LINE_CHARS) } }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp)) {
        Text(
            preview,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 15.sp
        )
        if (markdown.lines().size > 500) {
            Spacer(Modifier.height(8.dp))
            Text("Raw preview truncated to first 500 lines.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadmeText(text: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val segments = remember(text) { readmeInlineSegments(text) }
    if (segments.size == 1 && segments.first() is ReadmeInlineSegment.Text) {
        val annotated = readmeInlineAnnotated(text)
        ClickableText(
            text = annotated,
            modifier = modifier,
            style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 20.sp),
            onClick = { offset ->
                annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.item?.let { context.openReadmeUrl(it) }
            }
        )
    } else {
        FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            segments.forEach { segment ->
                when (segment) {
                    is ReadmeInlineSegment.Code -> {
                        Text(
                            segment.text,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    is ReadmeInlineSegment.Text -> {
                        val annotated = readmeInlineAnnotated(segment.text)
                        ClickableText(
                            text = annotated,
                            style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 20.sp),
                            onClick = { offset ->
                                annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.item?.let { context.openReadmeUrl(it) }
                            }
                        )
                    }
                }
            }
        }
    }
}

private sealed class ReadmeInlineSegment {
    data class Text(val text: String) : ReadmeInlineSegment()
    data class Code(val text: String) : ReadmeInlineSegment()
}

private fun readmeInlineSegments(text: String): List<ReadmeInlineSegment> {
    val segments = mutableListOf<ReadmeInlineSegment>()
    var index = 0
    while (index < text.length) {
        val start = text.indexOf('`', index)
        if (start < 0) {
            text.substring(index).takeIf { it.isNotBlank() }?.let { segments += ReadmeInlineSegment.Text(it) }
            break
        }
        text.substring(index, start).takeIf { it.isNotBlank() }?.let { segments += ReadmeInlineSegment.Text(it) }
        val end = text.indexOf('`', start + 1)
        if (end < 0) {
            text.substring(start).takeIf { it.isNotBlank() }?.let { segments += ReadmeInlineSegment.Text(it) }
            break
        }
        text.substring(start + 1, end).takeIf { it.isNotBlank() }?.let { segments += ReadmeInlineSegment.Code(it) }
        index = end + 1
    }
    return segments.ifEmpty { listOf(ReadmeInlineSegment.Text(text)) }
}

@Composable
private fun ReadmeBullet(text: String, ordered: Boolean = false, checked: Boolean? = null, level: Int = 0) {
    Row(Modifier.fillMaxWidth().padding(start = (level * 16).dp, top = 1.dp, bottom = 1.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val marker = when (checked) {
            true -> "✓"
            false -> "□"
            null -> if (ordered) "1." else "•"
        }
        Text(marker, fontSize = 13.sp, color = if (checked == true) GitHubSuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 14.dp))
        ReadmeText(text, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun rememberReadmeImageLoader(context: Context): ImageLoader {
    val appContext = context.applicationContext
    return remember(appContext) {
        ImageLoader.Builder(appContext)
            .okHttpClient(
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .header("User-Agent", README_IMAGE_USER_AGENT)
                                .build()
                        )
                    }
                    .build()
            )
            .components { add(SvgDecoder.Factory()) }
            .build()
    }
}

@Composable
private fun ReadmeImage(block: ReadmeRenderBlock.Image, imageLoader: ImageLoader) {
    if (block.inline) {
        InlineReadmeImage(block, imageLoader)
        return
    }
    val context = LocalContext.current
    val placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
    var failed by remember(block.url) { mutableStateOf(false) }
    var loaded by remember(block.url) { mutableStateOf(false) }
    val animatedGif = remember(block.url) { block.url.substringBefore('?').endsWith(".gif", ignoreCase = true) }
    LaunchedEffect(block.url) {
        failed = false
        loaded = false
        delay(README_IMAGE_TIMEOUT_MS)
        if (!loaded && !animatedGif) {
            Log.w(README_RENDER_TAG, "image timeout ${block.url}")
            failed = true
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (animatedGif) {
            ReadmeLinkCard(block.alt.ifBlank { "Animated image skipped" }, block.url)
        } else if (failed) {
            ReadmeImageUnavailable(block.alt)
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(block.url)
                    .size(2048, 2048)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(false)
                    .build(),
                contentDescription = block.alt,
                imageLoader = imageLoader,
                placeholder = placeholder,
                error = placeholder,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(block.aspectRatio.coerceIn(0.5f, 3f))
                    .heightIn(min = 200.dp, max = 360.dp)
                    .clip(RoundedCornerShape(6.dp)),
                onSuccess = { loaded = true },
                onError = {
                    loaded = true
                    failed = true
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadmeImageRow(images: List<ReadmeRenderBlock.Image>, imageLoader: ImageLoader) {
    FlowRow(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        images.forEach { InlineReadmeImage(it.copy(inline = true), imageLoader) }
    }
}

@Composable
private fun InlineReadmeImage(block: ReadmeRenderBlock.Image, imageLoader: ImageLoader) {
    val context = LocalContext.current
    var failed by remember(block.url) { mutableStateOf(false) }
    val height = (block.heightHintDp ?: 24).coerceIn(16, 56).dp
    if (failed) {
        ReadmeImageUnavailable(block.alt)
        return
    }
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(block.url)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .build(),
        contentDescription = block.alt,
        imageLoader = imageLoader,
        contentScale = ContentScale.Fit,
        modifier = Modifier.height(height).widthIn(min = 16.dp, max = 220.dp).clip(RoundedCornerShape(3.dp)),
        onError = { failed = true }
    )
}

@Composable
private fun ReadmeImageUnavailable(alt: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Rounded.BrokenImage, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
        Text(
            alt.ifBlank { "image unavailable" },
            fontSize = 12.sp,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ReadmeCodeBlock(block: ReadmeRenderBlock.Code) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var expanded by remember(block.code) { mutableStateOf(false) }
    val lines = remember(block.code, expanded) {
        val allLines = block.code.lines()
        if (expanded || allLines.size <= README_MAX_CODE_LINES) allLines else allLines.take(120)
    }
    val totalLines = remember(block.code) { block.code.lines().size }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${block.language.ifBlank { "code" }} · $totalLines lines", fontSize = 11.sp, color = colors.onSurfaceVariant, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            IconButton(modifier = Modifier.size(30.dp), onClick = {
                val clip = android.content.ClipData.newPlainText("readme-code", block.code)
                (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
                Toast.makeText(context, Strings.done, Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(15.dp), tint = colors.onSurfaceVariant)
            }
        }
        Column(Modifier.horizontalScroll(rememberScrollState()).padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            lines.forEach { line ->
                Text(line.take(README_MAX_LINE_CHARS).ifEmpty { " " }, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface, lineHeight = 17.sp)
            }
            if (!expanded && totalLines > README_MAX_CODE_LINES) TextButton(onClick = { expanded = true }) { Text("Expand large code block", color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun ReadmeTable(rows: List<List<String>>) {
    if (rows.isEmpty()) return
    var expanded by remember(rows) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val visibleRows = if (expanded || rows.size <= README_MAX_TABLE_ROWS) rows else rows.take(README_MAX_TABLE_ROWS)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).clip(RoundedCornerShape(8.dp)).border(0.5.dp, colors.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(8.dp))) {
        Column {
            visibleRows.forEachIndexed { rowIndex, row ->
                Row(Modifier.background(if (rowIndex == 0) colors.surfaceVariant else colors.surface)) {
                    row.forEachIndexed { cellIndex, cell ->
                        Box(
                            Modifier
                                .widthIn(min = 96.dp, max = 210.dp)
                                .then(if (cellIndex != 0) Modifier.border(0.5.dp, colors.outlineVariant.copy(alpha = 0.25f)) else Modifier)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                readmeInlineAnnotated(cell),
                                fontSize = 12.sp,
                                color = colors.onSurface,
                                lineHeight = 17.sp,
                                fontWeight = if (rowIndex == 0) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
                if (rowIndex != visibleRows.lastIndex) Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.outlineVariant.copy(alpha = 0.35f)))
            }
            if (!expanded && rows.size > README_MAX_TABLE_ROWS) {
                TextButton(onClick = { expanded = true }) {
                    Text("Expand large table (${rows.size - README_MAX_TABLE_ROWS} rows hidden)", color = colors.primary)
                }
            }
        }
    }
}

@Composable
private fun ReadmeLinkCard(text: String, url: String) {
    val context = LocalContext.current
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { context.openReadmeUrl(url) }.padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Rounded.OpenInNew, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text(text.ifBlank { url }, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun readmeInlineAnnotated(text: String): AnnotatedString {
    val colors = MaterialTheme.colorScheme
    return androidx.compose.ui.text.buildAnnotatedString {
        var i = 0
        val clean = stripReadmeHtml(text)
        while (i < clean.length) {
            when {
                clean.startsWith("**", i) -> {
                    val end = clean.indexOf("**", i + 2)
                    if (end > i) {
                        pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = colors.onSurface))
                        append(clean.substring(i + 2, end))
                        pop()
                        i = end + 2
                    } else append(clean[i++])
                }
                clean[i] == '*' -> {
                    val end = clean.indexOf('*', i + 1)
                    if (end > i) {
                        pushStyle(androidx.compose.ui.text.SpanStyle(fontStyle = FontStyle.Italic, color = colors.onSurface))
                        append(clean.substring(i + 1, end))
                        pop()
                        i = end + 1
                    } else append(clean[i++])
                }
                clean[i] == '`' -> {
                    val end = clean.indexOf('`', i + 1)
                    if (end > i) {
                        pushStyle(androidx.compose.ui.text.SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surfaceVariant, color = colors.onSurface))
                        append(clean.substring(i + 1, end))
                        pop()
                        i = end + 1
                    } else append(clean[i++])
                }
                clean[i] == '[' -> {
                    val closeBracket = clean.indexOf(']', i)
                    val openParen = if (closeBracket > 0 && closeBracket + 1 < clean.length && clean[closeBracket + 1] == '(') closeBracket + 1 else -1
                    val closeParen = if (openParen > 0) clean.indexOf(')', openParen) else -1
                    if (closeParen > 0) {
                        val label = clean.substring(i + 1, closeBracket)
                        val url = clean.substring(openParen + 1, closeParen).substringBefore(' ').trim()
                        pushStringAnnotation("URL", url)
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = colors.primary, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline))
                        append(label)
                        pop()
                        pop()
                        i = closeParen + 1
                    } else append(clean[i++])
                }
                README_PLAIN_URL_REGEX.find(clean, i)?.range?.first == i -> {
                    val rawUrl = README_PLAIN_URL_REGEX.find(clean, i)!!.value
                    val url = rawUrl.trimEnd('.', ',', ';', ':')
                    pushStringAnnotation("URL", url)
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = colors.primary, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline))
                    append(url)
                    pop()
                    pop()
                    val trailing = rawUrl.drop(url.length)
                    if (trailing.isNotEmpty()) append(trailing)
                    i += rawUrl.length
                }
                else -> append(clean[i++])
            }
        }
    }
}

internal sealed class ReadmeRenderBlock {
    abstract val stableId: String
    data class Heading(val level: Int, val text: String, val anchor: String = "", override val stableId: String = "") : ReadmeRenderBlock()
    data class Paragraph(val text: String, override val stableId: String = "") : ReadmeRenderBlock()
    data class Bullet(val text: String, val ordered: Boolean, val checked: Boolean? = null, val level: Int = 0, override val stableId: String = "") : ReadmeRenderBlock()
    data class Quote(val text: String, override val stableId: String = "") : ReadmeRenderBlock()
    data class Rule(override val stableId: String = "") : ReadmeRenderBlock()
    data class Image(val url: String, val alt: String, val aspectRatio: Float = README_DEFAULT_IMAGE_ASPECT_RATIO, val inline: Boolean = false, val heightHintDp: Int? = null, override val stableId: String = "") : ReadmeRenderBlock()
    data class ImageRow(val images: List<Image>, override val stableId: String = "") : ReadmeRenderBlock()
    data class Code(val language: String, val code: String, override val stableId: String = "") : ReadmeRenderBlock()
    data class Table(val rows: List<List<String>>, override val stableId: String = "") : ReadmeRenderBlock()
    data class Link(val text: String, val url: String, override val stableId: String = "") : ReadmeRenderBlock()
}

internal suspend fun parseReadmeBlocks(markdown: String, repo: GHRepo, readmePath: String = ""): List<ReadmeRenderBlock> {
    val blocks = mutableListOf<ReadmeRenderBlock>()
    val lines = markdown.replace("\r\n", "\n").lines()
    var i = 0
    var guard = 0
    while (i < lines.size) {
        if (guard++ > lines.size + 1_000) {
            throw IllegalStateException("README parser made no forward progress near line $i")
        }
        if (guard % 100 == 0) yield()
        val rawLine = lines[i]
        val line = rawLine.trim()
        when {
            line.isBlank() -> i++
            line.startsWith("```") || line.startsWith("~~~") -> {
                val fence = line.take(3)
                val language = line.drop(3).trim().take(24)
                val code = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith(fence)) {
                    code += lines[i].trimEnd().take(README_MAX_LINE_CHARS)
                    i++
                }
                if (i < lines.size) i++
                blocks += ReadmeRenderBlock.Code(language, code.joinToString("\n"))
            }
            readmeMarkdownImageLinkBlocks(line, repo, readmePath).isNotEmpty() -> {
                readmeTextWithoutImages(line).takeIf { it.isNotBlank() }?.let { blocks += ReadmeRenderBlock.Paragraph(stripReadmeHtml(it)) }
                blocks += readmeMarkdownImageLinkBlocks(line, repo, readmePath)
                i++
            }
            readmeMarkdownImages(line, repo, readmePath).isNotEmpty() -> {
                val images = readmeMarkdownImages(line, repo, readmePath)
                readmeTextWithoutImages(line).takeIf { it.isNotBlank() }?.let { blocks += ReadmeRenderBlock.Paragraph(stripReadmeHtml(it)) }
                blocks += readmeImageBlocks(images)
                i++
            }
            readmeHtmlImageLinkBlocks(line, repo, readmePath).isNotEmpty() -> {
                readmeTextWithoutImages(line).takeIf { it.isNotBlank() }?.let { blocks += ReadmeRenderBlock.Paragraph(stripReadmeHtml(it)) }
                blocks += readmeHtmlImageLinkBlocks(line, repo, readmePath)
                i++
            }
            readmeHtmlImages(line, repo, readmePath).isNotEmpty() -> {
                val images = readmeHtmlImages(line, repo, readmePath)
                readmeTextWithoutImages(line).takeIf { it.isNotBlank() }?.let { blocks += ReadmeRenderBlock.Paragraph(stripReadmeHtml(it)) }
                blocks += readmeImageBlocks(images)
                i++
            }
            line.equals("</a>", ignoreCase = true) || line.startsWith("<br", ignoreCase = true) -> i++
            line.startsWith("<a ", ignoreCase = true) -> {
                readmeHtmlLink(line)?.let { blocks += ReadmeRenderBlock.Link(it.first, it.second) }
                i++
            }
            line.startsWith("#") -> {
                val level = line.takeWhile { it == '#' }.length.coerceIn(1, 4)
                val text = line.drop(level).trim()
                if (text.isNotBlank()) {
                    val cleanText = stripReadmeHtml(text)
                    blocks += ReadmeRenderBlock.Heading(level, cleanText, readmeHeadingAnchor(cleanText))
                }
                i++
            }
            line.startsWith(">") -> {
                blocks += ReadmeRenderBlock.Quote(stripReadmeHtml(line.removePrefix(">").trim()))
                i++
            }
            line == "---" || line == "***" || line == "___" -> {
                blocks += ReadmeRenderBlock.Rule()
                i++
            }
            readmeLooksLikeTable(lines, i) -> {
                val rows = mutableListOf<List<String>>()
                rows += readmeTableCells(lines[i])
                i += 2
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    rows += readmeTableCells(lines[i])
                    i++
                }
                blocks += ReadmeRenderBlock.Table(rows)
            }
            line.startsWith("- [ ]", ignoreCase = true) || line.startsWith("* [ ]", ignoreCase = true) -> {
                blocks += ReadmeRenderBlock.Bullet(stripReadmeHtml(line.drop(5).trim()), ordered = false, checked = false, level = readmeListLevel(rawLine))
                i++
            }
            line.startsWith("- [x]", ignoreCase = true) || line.startsWith("* [x]", ignoreCase = true) -> {
                blocks += ReadmeRenderBlock.Bullet(stripReadmeHtml(line.drop(5).trim()), ordered = false, checked = true, level = readmeListLevel(rawLine))
                i++
            }
            line.startsWith("- ") || line.startsWith("* ") -> {
                blocks += ReadmeRenderBlock.Bullet(stripReadmeHtml(line.drop(2).trim()), ordered = false, level = readmeListLevel(rawLine))
                i++
            }
            Regex("^\\d+[.)]\\s+.*").matches(line) -> {
                blocks += ReadmeRenderBlock.Bullet(stripReadmeHtml(line.replaceFirst(Regex("^\\d+[.)]\\s+"), "")), ordered = true, level = readmeListLevel(rawLine))
                i++
            }
            readmeStandaloneMarkdownLink(line) != null -> {
                val link = readmeStandaloneMarkdownLink(line)!!
                blocks += ReadmeRenderBlock.Link(link.first, readmeResolveUrl(link.second, repo, readmePath))
                i++
            }
            else -> {
                val paragraph = mutableListOf<String>()
                val paragraphStart = i
                while (i < lines.size) {
                    val current = lines[i].trim()
                    if (current.isBlank() || current.startsWith("#") || current.startsWith("```") || current.startsWith("~~~") ||
                        current.startsWith("- ") || current.startsWith("* ") || current.startsWith(">") || current.startsWith("|") ||
                        readmeMarkdownImages(current, repo, readmePath).isNotEmpty() || readmeHtmlImages(current, repo, readmePath).isNotEmpty()
                    ) break
                    paragraph += stripReadmeHtml(current)
                    i++
                }
                if (i == paragraphStart) {
                    stripReadmeHtml(line).takeIf { it.isNotBlank() }?.let { blocks += ReadmeRenderBlock.Paragraph(it) }
                    i++
                } else {
                    paragraph.joinToString(" ").trim().takeIf { it.isNotBlank() }?.let { blocks += ReadmeRenderBlock.Paragraph(it) }
                }
            }
        }
    }
    return blocks.withStableReadmeIds()
}

private fun List<ReadmeRenderBlock>.withStableReadmeIds(): List<ReadmeRenderBlock> =
    mapIndexed { index, block ->
        val type = block.readmeBlockType()
        val stableId = "${index}_${type}_${block.readmeKeyContent().hashCode()}"
        when (block) {
            is ReadmeRenderBlock.Heading -> block.copy(stableId = stableId)
            is ReadmeRenderBlock.Paragraph -> block.copy(stableId = stableId)
            is ReadmeRenderBlock.Bullet -> block.copy(stableId = stableId)
            is ReadmeRenderBlock.Quote -> block.copy(stableId = stableId)
            is ReadmeRenderBlock.Rule -> block.copy(stableId = stableId)
            is ReadmeRenderBlock.Image -> block.copy(stableId = stableId)
            is ReadmeRenderBlock.ImageRow -> block.copy(stableId = stableId)
            is ReadmeRenderBlock.Code -> block.copy(stableId = stableId)
            is ReadmeRenderBlock.Table -> block.copy(stableId = stableId)
            is ReadmeRenderBlock.Link -> block.copy(stableId = stableId)
        }
    }

private fun ReadmeRenderBlock.readmeBlockType(): String = when (this) {
    is ReadmeRenderBlock.Heading -> "heading"
    is ReadmeRenderBlock.Paragraph -> "paragraph"
    is ReadmeRenderBlock.Bullet -> "bullet"
    is ReadmeRenderBlock.Quote -> "quote"
    is ReadmeRenderBlock.Rule -> "rule"
    is ReadmeRenderBlock.Image -> "image"
    is ReadmeRenderBlock.ImageRow -> "image_row"
    is ReadmeRenderBlock.Code -> "code"
    is ReadmeRenderBlock.Table -> "table"
    is ReadmeRenderBlock.Link -> "link"
}

private fun ReadmeRenderBlock.readmeKeyContent(): String = when (this) {
    is ReadmeRenderBlock.Heading -> "$level|$text|$anchor"
    is ReadmeRenderBlock.Paragraph -> text
    is ReadmeRenderBlock.Bullet -> "$ordered|$checked|$level|$text"
    is ReadmeRenderBlock.Quote -> text
    is ReadmeRenderBlock.Rule -> "rule"
    is ReadmeRenderBlock.Image -> "$url|$alt|$aspectRatio|$inline|$heightHintDp"
    is ReadmeRenderBlock.ImageRow -> images.joinToString("|") { it.readmeKeyContent() }
    is ReadmeRenderBlock.Code -> "$language|$code"
    is ReadmeRenderBlock.Table -> rows.joinToString("|") { it.joinToString("\u001F") }
    is ReadmeRenderBlock.Link -> "$text|$url"
}

private fun readmeMarkdownImages(line: String, repo: GHRepo, readmePath: String): List<ReadmeRenderBlock.Image> {
    val regex = Regex("!\\[([^]]*)]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)")
    val matches = regex.findAll(line).toList()
    val hasInlineText = readmeTextWithoutImages(line).isNotBlank()
    return matches.mapNotNull { match ->
        val raw = match.groupValues.getOrNull(2).orEmpty()
        val alt = match.groupValues.getOrNull(1).orEmpty()
        val url = readmeResolveUrl(raw, repo, readmePath)
        if (raw.isBlank()) null else ReadmeRenderBlock.Image(
            url = url,
            alt = alt,
            inline = hasInlineText || matches.size > 1 || readmeIsInlineImage(url, alt, null),
            heightHintDp = if (readmeIsBadgeImage(url, alt)) 20 else null
        )
    }.toList()
}

private fun readmeMarkdownImageLinkBlocks(line: String, repo: GHRepo, readmePath: String): List<ReadmeRenderBlock> {
    val regex = Regex("\\[!\\[([^]]*)]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)")
    val matches = regex.findAll(line).toList()
    if (matches.isEmpty()) return emptyList()

    val blocks = mutableListOf<ReadmeRenderBlock>()
    val inlineImages = mutableListOf<ReadmeRenderBlock.Image>()
    fun flushInlineImages() {
        if (inlineImages.isNotEmpty()) {
            blocks += readmeImageBlocks(inlineImages.toList())
            inlineImages.clear()
        }
    }

    matches.forEach { match ->
        val alt = match.groupValues.getOrNull(1).orEmpty()
        val rawImageUrl = match.groupValues.getOrNull(2).orEmpty()
        val rawTargetUrl = match.groupValues.getOrNull(3).orEmpty()
        val imageUrl = readmeResolveUrl(rawImageUrl, repo, readmePath)
        if (rawImageUrl.isNotBlank() && readmeIsBadgeImage(imageUrl, alt)) {
            inlineImages += ReadmeRenderBlock.Image(
                url = imageUrl,
                alt = alt,
                inline = true,
                heightHintDp = 20
            )
        } else {
            flushInlineImages()
            if (rawTargetUrl.isNotBlank()) {
                blocks += ReadmeRenderBlock.Link(alt.ifBlank { "Open" }, readmeResolveUrl(rawTargetUrl, repo, readmePath))
            }
        }
    }
    flushInlineImages()
    return blocks
}

private fun readmeHtmlImageLinkBlocks(line: String, repo: GHRepo, readmePath: String): List<ReadmeRenderBlock> {
    val regex = Regex("<a\\b[^>]*href=[\"'][^\"']+[\"'][^>]*>.*?<img\\b[^>]*>.*?</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    val matches = regex.findAll(line).toList()
    if (matches.isEmpty()) return emptyList()

    val blocks = mutableListOf<ReadmeRenderBlock>()
    val inlineImages = mutableListOf<ReadmeRenderBlock.Image>()
    fun flushInlineImages() {
        if (inlineImages.isNotEmpty()) {
            blocks += readmeImageBlocks(inlineImages.toList())
            inlineImages.clear()
        }
    }

    matches.forEach { match ->
        val anchorTag = match.value
        val imageTag = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE).find(anchorTag)?.value.orEmpty()
        val href = readmeHtmlAttr(anchorTag, "href")
        val rawImageUrl = readmeHtmlAttr(imageTag, "src")
        val alt = readmeHtmlAttr(imageTag, "alt")
        val heightHint = readmeHtmlImageHeightDp(imageTag)
        val imageUrl = readmeResolveUrl(rawImageUrl, repo, readmePath)
        if (rawImageUrl.isNotBlank() && readmeIsBadgeImage(imageUrl, alt)) {
            inlineImages += ReadmeRenderBlock.Image(
                url = imageUrl,
                alt = alt,
                inline = true,
                heightHintDp = heightHint ?: if (readmeIsBadgeImage(imageUrl, alt)) 20 else null
            )
        } else {
            flushInlineImages()
            if (href.isNotBlank()) {
                blocks += ReadmeRenderBlock.Link(alt.ifBlank { href }, readmeResolveUrl(href, repo, readmePath))
            }
        }
    }
    flushInlineImages()
    return blocks
}

private fun readmeHtmlImages(line: String, repo: GHRepo, readmePath: String): List<ReadmeRenderBlock.Image> {
    val regex = Regex("<img\\b[^>]*src=[\"']([^\"']+)[\"'][^>]*>", RegexOption.IGNORE_CASE)
    val matches = regex.findAll(line).toList()
    val hasInlineText = readmeTextWithoutImages(line).isNotBlank()
    return matches.mapNotNull { match ->
        val raw = match.groupValues.getOrNull(1).orEmpty()
        val tag = match.value
        val alt = readmeHtmlAttr(tag, "alt")
        val heightHint = readmeHtmlImageHeightDp(tag)
        val url = readmeResolveUrl(raw, repo, readmePath)
        if (raw.isBlank()) null else ReadmeRenderBlock.Image(
            url = url,
            alt = alt,
            aspectRatio = readmeHtmlImageAspectRatio(tag),
            inline = hasInlineText || matches.size > 1 || readmeIsInlineImage(url, alt, heightHint),
            heightHintDp = heightHint
        )
    }.toList()
}

private fun readmeImageBlocks(images: List<ReadmeRenderBlock.Image>): List<ReadmeRenderBlock> =
    if (images.isEmpty()) {
        emptyList()
    } else if (images.size > 1 || images.all { it.inline }) {
        listOf(ReadmeRenderBlock.ImageRow(images.map { it.copy(inline = true) }))
    } else {
        images
    }

private fun readmeTextWithoutImages(line: String): String =
    line.replace(Regex("\\[!\\[[^]]*]\\([^)]+\\)]\\([^)]+\\)"), "")
        .replace(Regex("!\\[([^]]*)]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)"), "")
        .replace(Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("</?a\\b[^>]*>", RegexOption.IGNORE_CASE), "")
        .trim()

private fun readmeIsInlineImage(url: String, alt: String, heightHintDp: Int?): Boolean =
    (heightHintDp != null && heightHintDp < 64) || readmeIsBadgeImage(url, alt)

private fun readmeIsBadgeImage(url: String, alt: String): Boolean {
    val lowerUrl = url.lowercase()
    val lowerAlt = alt.lowercase()
    return "shields.io" in lowerUrl ||
        "img.shields.io" in lowerUrl ||
        "badge.fury.io" in lowerUrl ||
        "badgen.net" in lowerUrl ||
        "badge" in lowerUrl ||
        "badge" in lowerAlt ||
        "license" in lowerAlt
}

private fun readmeHtmlImageAspectRatio(tag: String): Float {
    val width = readmeHtmlAttr(tag, "width").filter { it.isDigit() }.toFloatOrNull()
    val height = readmeHtmlAttr(tag, "height").filter { it.isDigit() }.toFloatOrNull()
    return if (width != null && height != null && width > 0f && height > 0f) {
        width / height
    } else {
        README_DEFAULT_IMAGE_ASPECT_RATIO
    }
}

private fun readmeHtmlImageHeightDp(tag: String): Int? =
    readmeHtmlAttr(tag, "height").filter { it.isDigit() }.toIntOrNull()

private fun readmeListLevel(rawLine: String): Int =
    (rawLine.takeWhile { it == ' ' }.length / 2).coerceIn(0, 6)

private fun readmeHeadingAnchor(text: String): String =
    text.lowercase()
        .replace(Regex("[^a-z0-9\\s-]"), "")
        .trim()
        .replace(Regex("\\s+"), "-")

private fun readmeHtmlLink(line: String): Pair<String, String>? {
    val href = readmeHtmlAttr(line, "href")
    if (href.isBlank()) return null
    val label = Regex(">([^<]+)<", RegexOption.IGNORE_CASE).find(line)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    return label.ifBlank { href } to href
}

private fun readmeHtmlAttr(line: String, attr: String): String =
    Regex("$attr=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(line)?.groupValues?.getOrNull(1).orEmpty()

private fun readmeStandaloneMarkdownLink(line: String): Pair<String, String>? {
    val match = Regex("^\\[([^]]+)]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)$").find(line) ?: return null
    return match.groupValues[1] to match.groupValues[2]
}

private fun readmeLooksLikeTable(lines: List<String>, index: Int): Boolean {
    if (index + 1 >= lines.size) return false
    val header = lines[index].trim()
    val divider = lines[index + 1].trim()
    return header.startsWith("|") && header.endsWith("|") && divider.matches(Regex("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?$"))
}

private fun readmeTableCells(line: String): List<String> =
    line.trim().trim('|').split('|').map { stripReadmeHtml(it.trim()) }

private fun stripReadmeHtml(text: String): String =
    text.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</?p[^>]*>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("</?div[^>]*>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("</?span[^>]*>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("</?strong[^>]*>", RegexOption.IGNORE_CASE), "**")
        .replace(Regex("</?b[^>]*>", RegexOption.IGNORE_CASE), "**")
        .replace(Regex("</?em[^>]*>", RegexOption.IGNORE_CASE), "*")
        .replace(Regex("</?i[^>]*>", RegexOption.IGNORE_CASE), "*")
        .replace(Regex("<a\\b[^>]*href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", RegexOption.IGNORE_CASE), "[$2]($1)")
        .replace(Regex("<[^>]+>"), "")
        .trim()
        .let { readmeSafeText(it) }

private fun readmeSafeText(text: String): String =
    text.lineSequence().joinToString("\n") { line ->
        if (line.length <= README_MAX_LINE_CHARS) line else line.take(README_MAX_LINE_CHARS) + "…"
    }

private fun readmeResolveUrl(raw: String, repo: GHRepo, readmePath: String = ""): String {
    val url = readmeCleanImageUrl(raw)
    if (url.startsWith("http://") || url.startsWith("https://")) return url
    if (url.startsWith("//")) return "https:$url"
    if (url.startsWith("#")) return url
    val (path, suffix) = readmeSplitPathSuffix(url)
    val baseDir = readmePath.substringBeforeLast('/', missingDelimiterValue = "")
    val joinedPath = if (path.startsWith("/")) {
        path.trimStart('/')
    } else {
        listOf(baseDir, path.removePrefix("./")).filter { it.isNotBlank() }.joinToString("/")
    }
    val normalizedPath = readmeNormalizePath(joinedPath)
    return "https://raw.githubusercontent.com/${repo.owner}/${repo.name}/${repo.defaultBranch.ifBlank { "main" }}/$normalizedPath$suffix"
}

private fun readmeCleanImageUrl(raw: String): String =
    raw.trim()
        .removeSurrounding("<", ">")
        .replace("&amp;", "&")
        .replace("&#38;", "&")

private fun readmeSplitPathSuffix(url: String): Pair<String, String> {
    val suffixStart = listOf(url.indexOf('?'), url.indexOf('#')).filter { it >= 0 }.minOrNull() ?: return url to ""
    return url.take(suffixStart) to url.drop(suffixStart)
}

private fun readmeNormalizePath(path: String): String {
    val segments = mutableListOf<String>()
    path.split('/').forEach { segment ->
        when (segment) {
            "", "." -> Unit
            ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
            else -> segments += segment
        }
    }
    return segments.joinToString("/")
}

private fun readmeBrowserUrl(repo: GHRepo): String =
    "https://github.com/${repo.owner}/${repo.name}#readme"

private fun android.content.Context.openReadmeUrl(url: String) {
    if (url.isBlank() || url.startsWith("#")) return
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        Toast.makeText(this, Strings.error, Toast.LENGTH_SHORT).show()
    }
}

@Composable
internal fun IssueDetailScreen(repo: GHRepo, issueNumber: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<GHIssueDetail?>(null) }
    var comments by remember { mutableStateOf<List<GHComment>>(emptyList()) }
    var issueReactions by remember { mutableStateOf<List<GHReaction>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var newComment by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var previewComment by remember { mutableStateOf(false) }
    var showMetaDialog by remember { mutableStateOf(false) }
    var showReactions by remember { mutableStateOf(false) }
    var showTimeline by remember { mutableStateOf(false) }
    var commentReactionTarget by remember { mutableStateOf<GHComment?>(null) }
    var editingComment by remember { mutableStateOf<GHComment?>(null) }
    var deleteCommentTarget by remember { mutableStateOf<GHComment?>(null) }
    var showLockDialog by remember { mutableStateOf(false) }

    suspend fun refreshIssueDetail(showLoader: Boolean = false) {
        if (showLoader) loading = true
        detail = GitHubManager.getIssueDetail(context, repo.owner, repo.name, issueNumber)
        comments = GitHubManager.getIssueComments(context, repo.owner, repo.name, issueNumber)
        issueReactions = GitHubManager.getIssueReactions(context, repo.owner, repo.name, issueNumber)
        loading = false
    }

    LaunchedEffect(issueNumber) {
        refreshIssueDetail(showLoader = true)
    }

    AiModuleSurface {
    val issuePalette = AiModuleTheme.colors
    Column(Modifier.fillMaxSize().background(issuePalette.background)) {
        AiModulePageBar(
            title = "> issue #$issueNumber",
            subtitle = detail?.title,
            onBack = onBack,
            trailing = {
                if (detail != null) {
                    AiModuleGlyphAction(
                        glyph = GhGlyphs.REACT,
                        onClick = { showReactions = true },
                        tint = issuePalette.accent,
                        fontSize = 12.sp,
                        contentDescription = "reactions",
                    )
                    AiModuleGlyphAction(
                        glyph = GhGlyphs.TIMELINE,
                        onClick = { showTimeline = true },
                        tint = issuePalette.accent,
                        contentDescription = "timeline",
                    )
                    AiModuleGlyphAction(
                        glyph = GhGlyphs.TUNE,
                        onClick = { showMetaDialog = true },
                        tint = issuePalette.accent,
                        contentDescription = "metadata",
                    )
                    if (repo.canWrite()) {
                        AiModuleGlyphAction(
                            glyph = if (detail!!.locked) GhGlyphs.UNLOCK else GhGlyphs.LOCK,
                            onClick = { showLockDialog = true },
                            tint = if (detail!!.locked) GitHubSuccessGreen else issuePalette.warning,
                            contentDescription = if (detail!!.locked) "unlock" else "lock",
                        )
                        val isOpen = detail!!.state == "open"
                        AiModuleGlyphAction(
                            glyph = if (isOpen) GhGlyphs.CLOSE else GhGlyphs.REFRESH,
                            onClick = {
                                scope.launch {
                                    val ok = if (isOpen) {
                                        GitHubManager.closeIssue(context, repo.owner, repo.name, issueNumber)
                                    } else {
                                        GitHubManager.reopenIssue(context, repo.owner, repo.name, issueNumber)
                                    }
                                    if (ok) refreshIssueDetail()
                                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                }
                            },
                            tint = if (isOpen) GitHubErrorRed else GitHubSuccessGreen,
                            contentDescription = if (isOpen) "close issue" else "reopen issue",
                        )
                    }
                }
            },
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                detail?.let { issue ->
                    item {
                        IssueHeaderCard(
                            detail = issue,
                            reactions = issueReactions,
                            onMeta = { showMetaDialog = true },
                            onReactions = { showReactions = true },
                            onTimeline = { showTimeline = true }
                        )
                    }
                    item {
                        IssueMetaCard(issue) { showMetaDialog = true }
                    }
                    item {
                        IssueBodyCard(issue.body)
                    }
                    item {
                        Text("${comments.size} comments", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                }
                if (comments.isEmpty()) item {
                    Box(Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(16.dp)) {
                        Text(Strings.ghNoComments, fontSize = 13.sp, color = TextTertiary)
                    }
                }
                items(comments) { c ->
                    IssueCommentCard(
                        comment = c,
                        onReactions = { commentReactionTarget = c },
                        onEdit = { editingComment = c },
                        onDelete = { deleteCommentTarget = c }
                    )
                }
            }
            Column(
                Modifier.fillMaxWidth().background(SurfaceWhite).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { previewComment = false }) {
                        Text("Write", fontSize = 12.sp, color = if (!previewComment) Blue else TextSecondary)
                    }
                    TextButton(onClick = { previewComment = true }) {
                        Text("Preview", fontSize = 12.sp, color = if (previewComment) Blue else TextSecondary)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        if (newComment.isBlank() || sending) return@IconButton
                        sending = true
                        scope.launch {
                            val ok = GitHubManager.addComment(context, repo.owner, repo.name, issueNumber, newComment)
                            if (ok) {
                                comments = GitHubManager.getIssueComments(context, repo.owner, repo.name, issueNumber)
                                newComment = ""
                                previewComment = false
                            }
                            sending = false
                            Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        }
                    }, enabled = !sending && newComment.isNotBlank()) {
                        Icon(Icons.Rounded.Send, null, Modifier.size(20.dp), tint = if (sending || newComment.isBlank()) TextTertiary else Blue)
                    }
                }
                if (previewComment) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 82.dp, max = 180.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceLight).padding(12.dp).verticalScroll(rememberScrollState())) {
                        if (newComment.isBlank()) Text(Strings.ghAddComment, color = TextTertiary, fontSize = 14.sp)
                        else IssueMarkdownBlock(newComment)
                    }
                } else {
                    Box(Modifier.fillMaxWidth().heightIn(min = 82.dp, max = 180.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceLight).padding(horizontal = 12.dp, vertical = 10.dp)) {
                        if (newComment.isEmpty()) Text(Strings.ghAddComment, color = TextTertiary, fontSize = 14.sp)
                        BasicTextField(
                            newComment,
                            { newComment = it },
                            textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 14.sp, lineHeight = 19.sp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
    }

    if (showMetaDialog && detail != null) {
        IssueMetaDialog(
            repo = repo,
            detail = detail!!,
            onDismiss = { showMetaDialog = false },
            onDone = {
                showMetaDialog = false
                scope.launch { refreshIssueDetail() }
            }
        )
    }

    if (showReactions) {
        IssueReactionsDialog(
            repo = repo,
            issueNumber = issueNumber,
            onDismiss = { showReactions = false },
            onChanged = { scope.launch { issueReactions = GitHubManager.getIssueReactions(context, repo.owner, repo.name, issueNumber) } }
        )
    }

    commentReactionTarget?.let { comment ->
        IssueCommentReactionsDialog(
            repo = repo,
            comment = comment,
            onDismiss = { commentReactionTarget = null }
        )
    }

    if (showTimeline) {
        IssueTimelineDialog(
            repo = repo,
            issueNumber = issueNumber,
            onDismiss = { showTimeline = false }
        )
    }

    if (showLockDialog && detail != null) {
        IssueLockDialog(
            repo = repo,
            detail = detail!!,
            onDismiss = { showLockDialog = false },
            onDone = {
                showLockDialog = false
                scope.launch { refreshIssueDetail() }
            }
        )
    }

    editingComment?.let { comment ->
        IssueCommentEditDialog(
            repo = repo,
            comment = comment,
            onDismiss = { editingComment = null },
            onDone = {
                editingComment = null
                scope.launch { comments = GitHubManager.getIssueComments(context, repo.owner, repo.name, issueNumber) }
            }
        )
    }

    deleteCommentTarget?.let { comment ->
        AlertDialog(
            onDismissRequest = { deleteCommentTarget = null },
            containerColor = SurfaceWhite,
            title = { Text("Delete comment", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text(comment.body.lineSequence().firstOrNull().orEmpty().take(160), color = TextSecondary, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val ok = GitHubManager.deleteIssueComment(context, repo.owner, repo.name, comment.id)
                        Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        if (ok) comments = GitHubManager.getIssueComments(context, repo.owner, repo.name, issueNumber)
                        deleteCommentTarget = null
                    }
                }) { Text("Delete", color = Color(0xFFFF3B30)) }
            },
            dismissButton = { TextButton(onClick = { deleteCommentTarget = null }) { Text(Strings.cancel, color = TextSecondary) } }
        )
    }
}


@Composable
private fun IssueHeaderCard(
    detail: GHIssueDetail,
    reactions: List<GHReaction>,
    onMeta: () -> Unit,
    onReactions: () -> Unit,
    onTimeline: () -> Unit
) {
    Column(Modifier.fillMaxWidth().ghGlassCard(16.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            AsyncImage(detail.avatarUrl, null, Modifier.size(34.dp).clip(CircleShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(detail.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary, lineHeight = 20.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PullBadge(if (detail.state == "open") Strings.ghOpen else Strings.ghClosed, if (detail.state == "open") Color(0xFF34C759) else Color(0xFF8E8E93))
                    Text(detail.author, fontSize = 11.sp, color = Blue, fontWeight = FontWeight.Medium)
                    Text(detail.createdAt.take(10), fontSize = 11.sp, color = TextTertiary)
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(Icons.Rounded.Label, "Meta", Blue, onMeta)
            Chip(Icons.Rounded.EmojiEmotions, "Reactions ${reactions.size}", Blue, onReactions)
            Chip(Icons.Rounded.Timeline, "Timeline", Blue, onTimeline)
            if (detail.locked) PullBadge("Locked ${detail.activeLockReason}".trim(), Color(0xFFFF9500))
        }
        if (reactions.isNotEmpty()) {
            IssueReactionSummary(reactions)
        }
    }
}

@Composable
private fun IssueMetaCard(detail: GHIssueDetail, onEdit: () -> Unit) {
    Column(Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Metadata", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
            TextButton(onClick = onEdit) { Text("Edit", fontSize = 12.sp, color = Blue) }
        }
        if (detail.labels.isEmpty() && detail.assignee.isBlank() && detail.milestoneTitle.isBlank()) {
            Text("No labels, assignee, or milestone", fontSize = 12.sp, color = TextTertiary)
            return@Column
        }
        if (detail.labels.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                detail.labels.forEach { label ->
                    Text(label, fontSize = 11.sp, color = Blue, modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(Blue.copy(0.1f)).padding(horizontal = 7.dp, vertical = 3.dp))
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (detail.assignee.isNotBlank()) PullBadge("Assignee: ${detail.assignee}", TextSecondary)
            if (detail.milestoneTitle.isNotBlank()) PullBadge("Milestone: ${detail.milestoneTitle}", TextSecondary)
        }
    }
}

@Composable
private fun IssueBodyCard(body: String) {
    Column(Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Description", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        if (body.isBlank()) {
            Text("No description provided.", fontSize = 13.sp, color = TextTertiary)
        } else {
            IssueMarkdownBlock(body)
        }
    }
}

@Composable
private fun IssueCommentCard(comment: GHComment, onReactions: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        AsyncImage(comment.avatarUrl, null, Modifier.size(28.dp).clip(CircleShape))
        Column(Modifier.weight(1f).ghGlassCard(14.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(comment.author, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Blue)
                Text(comment.createdAt.take(10), fontSize = 10.sp, color = TextTertiary)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onReactions, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.EmojiEmotions, null, Modifier.size(16.dp), tint = TextSecondary)
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp), tint = TextSecondary)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Delete, null, Modifier.size(16.dp), tint = Color(0xFFFF3B30))
                }
            }
            IssueMarkdownBlock(comment.body)
        }
    }
}

@Composable
private fun IssueReactionSummary(reactions: List<GHReaction>) {
    val emojiMap = issueEmojiMap()
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        reactions.groupBy { it.content }.forEach { (content, items) ->
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).background(SurfaceLight).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(emojiMap[content] ?: content, fontSize = 14.sp)
                Text("${items.size}", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun IssueMarkdownBlock(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        text.lines().forEach { raw ->
            val line = raw.trimEnd()
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("### ") -> Text(trimmed.removePrefix("### "), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.padding(top = 4.dp))
                trimmed.startsWith("## ") -> Text(trimmed.removePrefix("## "), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(top = 5.dp))
                trimmed.startsWith("# ") -> Text(trimmed.removePrefix("# "), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(top = 6.dp))
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> Row {
                    Text("• ", fontSize = 13.sp, color = TextSecondary)
                    Text(trimmed.drop(2), fontSize = 13.sp, color = TextPrimary, lineHeight = 18.sp)
                }
                trimmed.startsWith("> ") -> Row(Modifier.padding(vertical = 2.dp)) {
                    Box(Modifier.width(3.dp).height(18.dp).background(SeparatorColor))
                    Text(trimmed.drop(2), fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(start = 8.dp))
                }
                trimmed.startsWith("```") -> Box(Modifier.fillMaxWidth().height(1.dp).background(SeparatorColor))
                trimmed.isBlank() -> Spacer(Modifier.height(5.dp))
                else -> Text(trimmed, fontSize = 13.sp, color = TextPrimary, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun IssueLockDialog(repo: GHRepo, detail: GHIssueDetail, onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reason by remember(detail.number) { mutableStateOf(detail.activeLockReason.ifBlank { "resolved" }) }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text(if (detail.locked) "Unlock issue" else "Lock issue", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(detail.title, fontSize = 13.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!detail.locked) {
                    Text("Reason", fontSize = 12.sp, color = TextSecondary)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("resolved", "off-topic", "too heated", "spam").forEach { value ->
                            val selected = reason == value
                            Box(
                                Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) Blue.copy(0.15f) else SurfaceLight)
                                    .clickable { reason = value }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(value, fontSize = 12.sp, color = if (selected) Blue else TextSecondary)
                            }
                        }
                    }
                } else if (detail.activeLockReason.isNotBlank()) {
                    PullBadge("Reason: ${detail.activeLockReason}", Color(0xFFFF9500))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        val ok = if (detail.locked) {
                            GitHubManager.unlockIssue(context, repo.owner, repo.name, detail.number)
                        } else {
                            GitHubManager.lockIssue(context, repo.owner, repo.name, detail.number, reason)
                        }
                        saving = false
                        Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        if (ok) onDone()
                    }
                }
            ) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(14.dp), color = Blue, strokeWidth = 2.dp)
                } else {
                    Text(if (detail.locked) "Unlock" else "Lock", color = if (detail.locked) Color(0xFF34C759) else Color(0xFFFF9500))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }
    )
}

@Composable
private fun IssueCommentEditDialog(repo: GHRepo, comment: GHComment, onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var body by remember(comment.id) { mutableStateOf(comment.body) }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Edit comment", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Comment") },
                minLines = 6,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = !saving && body.isNotBlank(),
                onClick = {
                    saving = true
                    scope.launch {
                        val ok = GitHubManager.updateIssueComment(context, repo.owner, repo.name, comment.id, body)
                        saving = false
                        Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        if (ok) onDone()
                    }
                }
            ) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(14.dp), color = Blue, strokeWidth = 2.dp)
                } else {
                    Text("Save", color = Blue)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }
    )
}

private fun issueEmojiMap(): Map<String, String> = mapOf(
    "+1" to "👍",
    "-1" to "👎",
    "laugh" to "😄",
    "confused" to "😕",
    "heart" to "❤️",
    "hooray" to "🎉",
    "eyes" to "👀",
    "rocket" to "🚀"
)

@Composable
private fun IssueMetaDialog(repo: GHRepo, detail: GHIssueDetail, onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var labels by remember { mutableStateOf<List<GHLabel>>(emptyList()) }
    var milestones by remember { mutableStateOf<List<GHMilestone>>(emptyList()) }
    var assignees by remember { mutableStateOf<List<GHUserLite>>(emptyList()) }
    val selectedLabels = remember(detail.number) { mutableStateListOf<String>().apply { addAll(detail.labels) } }
    var selectedAssignee by remember(detail.number) { mutableStateOf(detail.assignee) }
    var selectedMilestone by remember(detail.number) { mutableStateOf(detail.milestoneTitle) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(detail.number) {
        loading = true
        labels = GitHubManager.getLabels(context, repo.owner, repo.name)
        milestones = GitHubManager.getMilestones(context, repo.owner, repo.name)
        assignees = GitHubManager.getAssignees(context, repo.owner, repo.name)
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Issue metadata", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            if (loading) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Blue, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Labels", fontSize = 12.sp, color = TextSecondary)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        labels.forEach { label ->
                            val selected = label.name in selectedLabels
                            Box(
                                Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) Blue.copy(0.15f) else SurfaceLight)
                                    .clickable {
                                        if (selected) selectedLabels.remove(label.name) else selectedLabels.add(label.name)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(label.name, fontSize = 12.sp, color = if (selected) Blue else TextSecondary)
                            }
                        }
                    }

                    Text("Assignee", fontSize = 12.sp, color = TextSecondary)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if (selectedAssignee.isBlank()) Blue.copy(0.15f) else SurfaceLight).clickable { selectedAssignee = "" }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text("None", fontSize = 12.sp, color = if (selectedAssignee.isBlank()) Blue else TextSecondary)
                        }
                        assignees.forEach { user ->
                            val selected = selectedAssignee == user.login
                            Box(
                                Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) Blue.copy(0.15f) else SurfaceLight)
                                    .clickable { selectedAssignee = user.login }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(user.login, fontSize = 12.sp, color = if (selected) Blue else TextSecondary)
                            }
                        }
                    }

                    Text("Milestone", fontSize = 12.sp, color = TextSecondary)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if (selectedMilestone.isBlank()) Blue.copy(0.15f) else SurfaceLight).clickable { selectedMilestone = "" }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text("None", fontSize = 12.sp, color = if (selectedMilestone.isBlank()) Blue else TextSecondary)
                        }
                        milestones.forEach { milestone ->
                            val selected = selectedMilestone == milestone.title
                            Box(
                                Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) Blue.copy(0.15f) else SurfaceLight)
                                    .clickable { selectedMilestone = milestone.title }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(milestone.title, fontSize = 12.sp, color = if (selected) Blue else TextSecondary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (saving) return@TextButton
                    saving = true
                    scope.launch {
                        val milestoneNumber = milestones.firstOrNull { it.title == selectedMilestone }?.number
                        val ok = GitHubManager.updateIssueMeta(
                            context = context,
                            owner = repo.owner,
                            repo = repo.name,
                            issueNumber = detail.number,
                            labels = selectedLabels.toList(),
                            assignees = if (selectedAssignee.isBlank()) emptyList() else listOf(selectedAssignee),
                            milestoneNumber = milestoneNumber,
                            clearMilestone = selectedMilestone.isBlank()
                        )
                        Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        saving = false
                        if (ok) onDone()
                    }
                },
                enabled = !loading && !saving
            ) { Text("Save", color = Blue) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }
    )
}

@Composable
private fun PullEditDialog(repo: GHRepo, pr: GHPullRequest, onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember(pr.number) { mutableStateOf(pr.title) }
    var body by remember(pr.number) { mutableStateOf(pr.body) }
    var base by remember(pr.number) { mutableStateOf(pr.base) }
    var state by remember(pr.number) { mutableStateOf(pr.state) }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Edit PR #${pr.number}", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(body, { body = it }, label = { Text("Body") }, minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(base, { base = it.trim() }, label = { Text("Base branch") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("open" to "Open", "closed" to "Closed").forEach { (value, label) ->
                        val selected = state == value
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .background(if (selected) Blue.copy(0.15f) else SurfaceLight)
                                .clickable { state = value }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(label, fontSize = 12.sp, color = if (selected) Blue else TextSecondary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && title.isNotBlank() && base.isNotBlank(),
                onClick = {
                    if (saving) return@TextButton
                    saving = true
                    scope.launch {
                        val ok = GitHubManager.updatePullRequest(
                            context = context,
                            owner = repo.owner,
                            repo = repo.name,
                            number = pr.number,
                            title = title.trim(),
                            body = body,
                            base = base.trim(),
                            state = state
                        )
                        saving = false
                        Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        if (ok) onDone()
                    }
                }
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(14.dp), color = Blue, strokeWidth = 2.dp)
                else Text("Save", color = Blue)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }
    )
}

@Composable
private fun PullReviewersDialog(repo: GHRepo, pr: GHPullRequest, onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reviewersRaw by remember { mutableStateOf(pr.requestedReviewers.joinToString(",")) }
    var saving by remember { mutableStateOf(false) }
    val reviewers = reviewersRaw.split(",").map { it.trim().removePrefix("@") }.filter { it.isNotBlank() }.distinct()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Reviewers #${pr.number}", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (pr.requestedReviewers.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pr.requestedReviewers.forEach { reviewer -> PullBadge(reviewer, TextSecondary) }
                    }
                } else {
                    Text("No requested reviewers", fontSize = 12.sp, color = TextTertiary)
                }
                OutlinedTextField(
                    value = reviewersRaw,
                    onValueChange = { reviewersRaw = it },
                    label = { Text("Usernames, comma-separated") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Request adds reviewers; remove removes the typed usernames.", fontSize = 11.sp, color = TextTertiary)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = !saving && reviewers.isNotEmpty(),
                    onClick = {
                        saving = true
                        scope.launch {
                            val ok = GitHubManager.removePullRequestReviewers(context, repo.owner, repo.name, pr.number, reviewers)
                            saving = false
                            Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                            if (ok) onDone()
                        }
                    }
                ) { Text("Remove", color = Color(0xFFFF3B30)) }
                TextButton(
                    enabled = !saving && reviewers.isNotEmpty(),
                    onClick = {
                        saving = true
                        scope.launch {
                            val ok = GitHubManager.requestPullRequestReviewers(context, repo.owner, repo.name, pr.number, reviewers)
                            saving = false
                            Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                            if (ok) onDone()
                        }
                    }
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(14.dp), color = Blue, strokeWidth = 2.dp)
                    else Text("Request", color = Blue)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }
    )
}

@Composable
private fun PullReviewHistoryDialog(
    repo: GHRepo,
    pr: GHPullRequest,
    reviews: List<GHPullReview>,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedReview by remember { mutableStateOf<GHPullReview?>(null) }
    var editReview by remember { mutableStateOf<GHPullReview?>(null) }
    var deleteReview by remember { mutableStateOf<GHPullReview?>(null) }
    var actionInFlight by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Review history", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            if (reviews.isEmpty()) {
                Text("No reviews yet", fontSize = 13.sp, color = TextTertiary)
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(reviews) { review ->
                        val canMutate = review.state.equals("PENDING", ignoreCase = true)
                        Column(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceLight)
                                .clickable {
                                    scope.launch {
                                        selectedReview = GitHubManager.getPullRequestReview(context, repo.owner, repo.name, pr.number, review.id) ?: review
                                    }
                                }
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PullBadge(review.state.ifBlank { "review" }, reviewStateColor(review.state))
                                Text(review.user.ifBlank { "GitHub" }, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                if (review.submittedAt.isNotBlank()) Text(review.submittedAt.take(10), fontSize = 10.sp, color = TextTertiary)
                                if (review.htmlUrl.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            try {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(review.htmlUrl)))
                                            } catch (_: Exception) {
                                                Toast.makeText(context, Strings.error, Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Rounded.OpenInNew, null, Modifier.size(16.dp), tint = TextSecondary)
                                    }
                                }
                                if (canMutate) {
                                    IconButton(onClick = { editReview = review }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp), tint = Blue)
                                    }
                                    IconButton(onClick = { deleteReview = review }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Rounded.Delete, null, Modifier.size(16.dp), tint = Color(0xFFFF3B30))
                                    }
                                }
                            }
                            if (review.body.isNotBlank()) Text(review.body, fontSize = 11.sp, color = TextSecondary, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            if (review.commitId.length >= 7) Text(review.commitId.take(7), fontSize = 10.sp, color = TextTertiary)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Blue) } }
    )

    selectedReview?.let { review ->
        PullReviewDetailDialog(review = review, onDismiss = { selectedReview = null })
    }

    editReview?.let { review ->
        PullReviewEditDialog(
            review = review,
            saving = actionInFlight,
            onDismiss = { if (!actionInFlight) editReview = null },
            onSave = { body ->
                actionInFlight = true
                scope.launch {
                    val updated = GitHubManager.updatePullRequestReview(context, repo.owner, repo.name, pr.number, review.id, body)
                    actionInFlight = false
                    Toast.makeText(context, if (updated != null) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                    if (updated != null) {
                        editReview = null
                        selectedReview = updated
                        onChanged()
                    }
                }
            }
        )
    }

    deleteReview?.let { review ->
        AlertDialog(
            onDismissRequest = { if (!actionInFlight) deleteReview = null },
            containerColor = SurfaceWhite,
            title = { Text("Delete pending review?", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text("Delete review #${review.id}?", fontSize = 13.sp, color = TextSecondary) },
            confirmButton = {
                TextButton(
                    enabled = !actionInFlight,
                    onClick = {
                        actionInFlight = true
                        scope.launch {
                            val ok = GitHubManager.deletePullRequestReview(context, repo.owner, repo.name, pr.number, review.id)
                            actionInFlight = false
                            Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                            if (ok) {
                                deleteReview = null
                                selectedReview = null
                                onChanged()
                            }
                        }
                    }
                ) { Text("Delete", color = Color(0xFFFF3B30)) }
            },
            dismissButton = { TextButton(enabled = !actionInFlight, onClick = { deleteReview = null }) { Text(Strings.cancel, color = TextSecondary) } }
        )
    }
}

@Composable
private fun PullReviewDetailDialog(review: GHPullReview, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Review #${review.id}", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PullBadge(review.state.ifBlank { "review" }, reviewStateColor(review.state))
                    if (review.commitId.length >= 7) PullBadge(review.commitId.take(7), TextSecondary)
                }
                PullReviewDetailLine("Reviewer", review.user.ifBlank { "GitHub" })
                PullReviewDetailLine("Submitted", review.submittedAt.take(19).replace('T', ' '))
                PullReviewDetailLine("Commit", review.commitId)
                if (review.body.isNotBlank()) {
                    Text(review.body, fontSize = 13.sp, color = TextPrimary, lineHeight = 18.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Blue) } }
    )
}

@Composable
private fun PullReviewEditDialog(
    review: GHPullReview,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var body by remember(review.id) { mutableStateOf(review.body) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Edit pending review", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PullBadge(review.state.ifBlank { "review" }, reviewStateColor(review.state))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Review body") },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    maxLines = 8
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = { onSave(body) }) {
                if (saving) CircularProgressIndicator(Modifier.size(14.dp), color = Blue, strokeWidth = 2.dp)
                else Text("Save", color = Blue)
            }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }
    )
}

@Composable
private fun PullReviewDetailLine(label: String, value: String) {
    if (value.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 11.sp, color = TextTertiary, fontWeight = FontWeight.Medium)
        Text(value, fontSize = 13.sp, color = TextPrimary)
    }
}

@Composable
private fun PullMergeDialog(
    pr: GHPullRequest,
    merging: Boolean,
    onDismiss: () -> Unit,
    onMerge: (method: String, title: String, message: String) -> Unit
) {
    var method by remember { mutableStateOf("merge") }
    var title by remember { mutableStateOf("${pr.title} (#${pr.number})") }
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!merging) onDismiss() },
        containerColor = SurfaceWhite,
        title = { Text("Merge PR #${pr.number}", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("merge" to "Merge", "squash" to "Squash", "rebase" to "Rebase").forEach { (value, label) ->
                        val selected = method == value
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .background(if (selected) Blue.copy(0.15f) else SurfaceLight)
                                .clickable { method = value }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(label, fontSize = 12.sp, color = if (selected) Blue else TextSecondary)
                        }
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("Commit title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(message, { message = it }, label = { Text("Commit message") }, minLines = 3, maxLines = 5, modifier = Modifier.fillMaxWidth())
                Text("${pr.head} -> ${pr.base}", fontSize = 11.sp, color = TextTertiary)
            }
        },
        confirmButton = {
            TextButton(enabled = !merging && title.isNotBlank(), onClick = { onMerge(method, title, message) }) {
                if (merging) CircularProgressIndicator(Modifier.size(14.dp), color = Color(0xFF34C759), strokeWidth = 2.dp)
                else Text(Strings.ghMerge, color = Color(0xFF34C759))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !merging) { Text(Strings.cancel, color = TextSecondary) } }
    )
}

private fun reviewStateColor(state: String): Color = when (state.lowercase()) {
    "approved" -> Color(0xFF34C759)
    "changes_requested" -> Color(0xFFFF3B30)
    "commented" -> Blue
    "dismissed" -> TextTertiary
    else -> TextSecondary
}

@Composable
private fun PullReviewDialog(repo: GHRepo, pr: GHPullRequest, onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var body by remember { mutableStateOf("") }
    var event by remember { mutableStateOf("COMMENT") }
    var sending by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Review #${pr.number}", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("COMMENT" to "Comment", "APPROVE" to "Approve", "REQUEST_CHANGES" to "Request changes").forEach { (value, label) ->
                        val selected = event == value
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .background(if (selected) Blue.copy(0.15f) else SurfaceLight)
                                .clickable { event = value }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(label, fontSize = 12.sp, color = if (selected) Blue else TextSecondary)
                        }
                    }
                }
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Review message") },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    maxLines = 8
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (sending) return@TextButton
                sending = true
                scope.launch {
                    val ok = GitHubManager.submitPullRequestReview(context, repo.owner, repo.name, pr.number, event, body)
                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                    sending = false
                    if (ok) onDone()
                }
            }) { Text("Submit", color = Blue) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }
    )
}

@Composable
private fun PullFilesDialog(repo: GHRepo, pr: GHPullRequest, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var files by remember { mutableStateOf<List<GHPullFile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(pr.number) {
        loading = true
        files = GitHubManager.getPullRequestFiles(context, repo.owner, repo.name, pr.number)
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Files #${pr.number}", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            if (loading) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Blue, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(files) { file ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SurfaceLight).padding(10.dp)
                        ) {
                            Text(file.filename, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                            Text("${file.status}  +${file.additions}  -${file.deletions}", fontSize = 10.sp, color = TextSecondary)
                            if (file.patch.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Box(Modifier.fillMaxWidth().background(Color(0xFF1E1E22), RoundedCornerShape(6.dp)).padding(8.dp)) {
                                    Text(file.patch.take(1200), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFD4D4D4))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Blue) } }
    )
}

@Composable
internal fun CommitDiffScreen(repo: GHRepo, sha: String, onBack: () -> Unit) { val context = LocalContext.current; var detail by remember { mutableStateOf<GHCommitDetail?>(null) }; var loading by remember { mutableStateOf(true) }
    LaunchedEffect(sha) { detail = GitHubManager.getCommitDiff(context, repo.owner, repo.name, sha); loading = false }
    AiModuleSurface {
    val commitPalette = AiModuleTheme.colors
    Column(Modifier.fillMaxSize().background(commitPalette.background)) {
        AiModulePageBar(
            title = "> ${sha.take(7)}",
            subtitle = detail?.message?.lines()?.firstOrNull(),
            onBack = onBack,
        )
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = commitPalette.accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
        else if (detail != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("+${detail!!.totalAdditions}", fontSize = 12.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, color = GitHubSuccessGreen)
                Text("-${detail!!.totalDeletions}", fontSize = 12.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, color = GitHubErrorRed)
                Text("${detail!!.files.size} files", fontSize = 12.sp, fontFamily = JetBrainsMono, color = commitPalette.textSecondary)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(commitPalette.border))
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(detail!!.files) { f ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val statusGlyph = when (f.status) { "added" -> "+"; "removed" -> "-"; "renamed" -> "~"; else -> "M" }
                            val statusColor = when (f.status) { "added" -> GitHubSuccessGreen; "removed" -> GitHubErrorRed; else -> commitPalette.warning }
                            Text(statusGlyph, color = statusColor, fontFamily = JetBrainsMono, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(12.dp))
                            Text(f.filename, fontSize = 12.sp, fontFamily = JetBrainsMono, color = commitPalette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("+${f.additions}", fontSize = 11.sp, fontFamily = JetBrainsMono, color = GitHubSuccessGreen)
                            Text("-${f.deletions}", fontSize = 11.sp, fontFamily = JetBrainsMono, color = GitHubErrorRed)
                        }
                        if (f.patch.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Box(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, commitPalette.border, RoundedCornerShape(6.dp))
                                    .background(commitPalette.surface)
                                    .horizontalScroll(rememberScrollState())
                                    .padding(8.dp),
                            ) {
                                Text(
                                    f.patch.take(2000),
                                    fontSize = 11.sp,
                                    fontFamily = JetBrainsMono,
                                    color = commitPalette.textPrimary,
                                    lineHeight = 14.sp,
                                )
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(commitPalette.border))
                }
            }
        }
    }
    }
}

// ═══════════════════════════════════
// Issue Reactions Dialog
// ═══════════════════════════════════

@Composable
private fun IssueReactionsDialog(repo: GHRepo, issueNumber: Int, onDismiss: () -> Unit, onChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reactions by remember { mutableStateOf<List<GHReaction>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(issueNumber) {
        reactions = GitHubManager.getIssueReactions(context, repo.owner, repo.name, issueNumber)
        loading = false
    }

    val emojiMap = issueEmojiMap()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Reactions", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column {
                // Add reaction row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    emojiMap.forEach { (key, emoji) ->
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(SurfaceLight)
                                .clickable {
                                    scope.launch {
                                        val ok = GitHubManager.addIssueReaction(context, repo.owner, repo.name, issueNumber, key)
                                        reactions = GitHubManager.getIssueReactions(context, repo.owner, repo.name, issueNumber)
                                        if (ok) onChanged()
                                    }
                                }
                                .padding(8.dp)
                        ) {
                            Text(emoji, fontSize = 20.sp)
                        }
                    }
                }

                if (loading) {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Blue, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else if (reactions.isEmpty()) {
                    Text("No reactions yet", fontSize = 13.sp, color = TextTertiary)
                } else {
                    // Group by reaction type
                    val grouped = reactions.groupBy { it.content }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        grouped.forEach { (content, items) ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(emojiMap[content] ?: content, fontSize = 16.sp)
                                Text("${items.size}", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                                Text(items.joinToString(", ") { it.user }, fontSize = 11.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Blue) } }
    )
}

@Composable
private fun IssueCommentReactionsDialog(repo: GHRepo, comment: GHComment, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reactions by remember { mutableStateOf<List<GHReaction>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val emojiMap = issueEmojiMap()

    LaunchedEffect(comment.id) {
        reactions = GitHubManager.getIssueCommentReactions(context, repo.owner, repo.name, comment.id)
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Comment reactions", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(comment.body.lineSequence().firstOrNull().orEmpty().take(120), fontSize = 12.sp, color = TextSecondary, maxLines = 2)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    emojiMap.forEach { (key, emoji) ->
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(SurfaceLight)
                                .clickable {
                                    scope.launch {
                                        GitHubManager.addIssueCommentReaction(context, repo.owner, repo.name, comment.id, key)
                                        reactions = GitHubManager.getIssueCommentReactions(context, repo.owner, repo.name, comment.id)
                                    }
                                }
                                .padding(8.dp)
                        ) {
                            Text(emoji, fontSize = 20.sp)
                        }
                    }
                }
                if (loading) {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Blue, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else if (reactions.isEmpty()) {
                    Text("No reactions yet", fontSize = 13.sp, color = TextTertiary)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        reactions.groupBy { it.content }.forEach { (content, items) ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(emojiMap[content] ?: content, fontSize = 16.sp)
                                Text("${items.size}", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                                Text(items.joinToString(", ") { it.user }, fontSize = 11.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Blue) } }
    )
}

// ═══════════════════════════════════
// Issue Timeline Dialog
// ═══════════════════════════════════

@Composable
private fun IssueTimelineDialog(repo: GHRepo, issueNumber: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var events by remember { mutableStateOf<List<GHTimelineEvent>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(issueNumber) {
        events = GitHubManager.getIssueTimeline(context, repo.owner, repo.name, issueNumber)
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Timeline", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            if (loading) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Blue, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else if (events.isEmpty()) {
                Text("No timeline events", fontSize = 13.sp, color = TextTertiary)
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(events) { event ->
                        val eventText = when (event.event) {
                            "labeled" -> "added label \"${event.label}\""
                            "unlabeled" -> "removed label \"${event.label}\""
                            "milestoned" -> "added to milestone \"${event.milestone}\""
                            "demilestoned" -> "removed from milestone \"${event.milestone}\""
                            "assigned" -> "assigned to ${event.assignee}"
                            "unassigned" -> "unassigned ${event.assignee}"
                            "closed" -> "closed this"
                            "reopened" -> "reopened this"
                            "cross-referenced" -> "referenced this"
                            "commented" -> "commented"
                            "committed" -> "committed"
                            "reviewed" -> "reviewed"
                            else -> event.event
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Blue))
                            Column {
                                Text("${event.actor} $eventText", fontSize = 12.sp, color = TextPrimary)
                                Text(event.createdAt.take(10), fontSize = 10.sp, color = TextTertiary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Blue) } }
    )
}
