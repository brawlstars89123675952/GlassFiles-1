package com.glassfiles.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.ui.components.AiModuleAlertDialog
import com.glassfiles.ui.components.AiModuleHairline
import com.glassfiles.ui.components.AiModuleIcon as Icon
import com.glassfiles.ui.components.AiModuleIconButton as IconButton
import com.glassfiles.ui.components.AiModulePageBar
import com.glassfiles.ui.components.AiModuleSpinner
import com.glassfiles.ui.components.AiModuleText as Text
import com.glassfiles.ui.components.AiModuleTextAction
import com.glassfiles.ui.components.AiModuleTextField
import com.glassfiles.data.github.GHAsset
import com.glassfiles.data.github.GHRepo
import com.glassfiles.data.github.GHRelease
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ReleasesScreen(
    repoOwner: String,
    repoName: String,
    defaultBranch: String = "main",
    canWrite: Boolean = true,
    onBack: () -> Unit,
    onReleaseClick: (GHRelease) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var releases by remember { mutableStateOf<List<GHRelease>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showCreate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        releases = GitHubManager.getReleases(context, repoOwner, repoName)
        loading = false
    }

    GitHubScreenFrame(
        title = "> releases",
        subtitle = repoName,
        onBack = { if (showCreate) showCreate = false else onBack() },
        trailing = if (canWrite) {
                {
                    GitHubTopBarAction(
                        glyph = GhGlyphs.PLUS,
                        onClick = { showCreate = true },
                        tint = AiModuleTheme.colors.accent,
                        contentDescription = "create release",
                    )
                }
            } else null,
    ) {

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AiModuleSpinner(label = "loading releases")
            }
            return@GitHubScreenFrame
        }

        if (releases.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.NewReleases,
                title = "No releases",
                subtitle = "Create your first release"
            )
            return@GitHubScreenFrame
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            items(releases) { release ->
                ReleaseCard(release, repoOwner, repoName, defaultBranch, releases, canWrite) { updatedReleases ->
                    releases = updatedReleases
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showCreate) {
        CreateReleaseDialog(
            repoOwner = repoOwner,
            repoName = repoName,
            onDismiss = { showCreate = false },
            onCreated = { newRelease ->
                releases = listOf(newRelease) + releases
                showCreate = false
            }
        )
    }
}

@Composable
private fun ReleaseCard(
    release: GHRelease,
    repoOwner: String,
    repoName: String,
    defaultBranch: String,
    releases: List<GHRelease>,
    canWrite: Boolean,
    onReleasesUpdate: (List<GHRelease>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var deletingAsset by remember { mutableStateOf<GHAsset?>(null) }
    var uploadingAsset by remember { mutableStateOf(false) }
    val colors = AiModuleTheme.colors
    val releaseRepo = remember(repoOwner, repoName, defaultBranch) {
        GHRepo(
            name = repoName,
            fullName = "$repoOwner/$repoName",
            description = "",
            language = "",
            stars = 0,
            forks = 0,
            isPrivate = false,
            isFork = false,
            defaultBranch = defaultBranch.ifBlank { "main" },
            updatedAt = "",
            owner = repoOwner
        )
    }
    val assetPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        if (release.id == 0L) {
            Toast.makeText(context, "Release id is missing", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            uploadingAsset = true
            val file = cachePickedReleaseAsset(context, uri)
            val uploaded = file?.let { GitHubManager.uploadReleaseAssetDetailed(context, repoOwner, repoName, release.id, it) }
            uploadingAsset = false
            if (uploaded != null) {
                onReleasesUpdate(releases.map {
                    if (it.tag == release.tag) it.copy(assets = it.assets + uploaded) else it
                })
                Toast.makeText(context, "${Strings.done}: ${uploaded.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, Strings.error, Toast.LENGTH_SHORT).show()
            }
        }
    }
    BackHandler(enabled = showEdit || showDelete || deletingAsset != null) {
        when {
            deletingAsset != null -> deletingAsset = null
            showEdit -> showEdit = false
            showDelete -> showDelete = false
        }
    }

    Column(
        Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(if (release.prerelease) GitHubWarningAmber() else GitHubSuccessGreen))
            Column(Modifier.weight(1f)) {
                Text(release.name.ifBlank { release.tag }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text(release.tag, fontSize = 12.sp, color = colors.textMuted)
            }
            if (release.draft) {
                Text("Draft", fontSize = 10.sp, color = colors.textMuted, modifier = Modifier.background(colors.background, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
            }
            if (release.prerelease) {
                Text("Pre", fontSize = 10.sp, color = GitHubWarningAmber(), modifier = Modifier.background(GitHubWarningAmber().copy(0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
            }
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    null,
                    Modifier.size(20.dp),
                    tint = colors.textMuted
                )
            }
        }

        if (release.createdAt.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(formatDate(release.createdAt), fontSize = 11.sp, color = colors.textMuted)
        }

        if (release.body.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            GitHubMarkdownDocument(release.body, releaseRepo, maxBlocks = if (expanded) null else 3)
        }

        if (expanded) {
            Spacer(Modifier.height(12.dp))
            if (release.assets.isNotEmpty()) {
                Text("Assets", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    release.assets.forEach { asset ->
                        AssetRow(
                            asset = asset,
                            onDownload = {
                                scope.launch {
                                    val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GlassFiles_Git/${asset.name}")
                                    val ok = GitHubManager.downloadReleaseAsset(context, asset, dest)
                                    Toast.makeText(context, if (ok) "${Strings.done}: ${dest.name}" else Strings.error, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDelete = if (canWrite && asset.id > 0L) { { deletingAsset = asset } } else null
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (canWrite && release.draft) {
                    GitHubTerminalButton(
                        label = "publish",
                        onClick = {
                            scope.launch {
                                val published = GitHubManager.publishRelease(context, repoOwner, repoName, release)
                                if (published != null) {
                                    onReleasesUpdate(releases.map { if (it.tag == release.tag) published else it })
                                    Toast.makeText(context, "Published", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, Strings.error, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        color = GitHubSuccessGreen,
                    )
                }
                if (canWrite) {
                    ReleaseActionButton(
                        icon = Icons.Rounded.UploadFile,
                        label = "Asset",
                        tint = AiModuleTheme.colors.textSecondary,
                        enabled = !uploadingAsset && release.id > 0L,
                        loading = uploadingAsset,
                        onClick = { assetPicker.launch("*/*") }
                    )
                }
                if (release.htmlUrl.isNotBlank()) {
                    ReleaseActionButton(Icons.Rounded.OpenInNew, "Open", AiModuleTheme.colors.textSecondary) { openGitHubUrl(context, release.htmlUrl) }
                }
                if (canWrite) {
                    ReleaseActionButton(Icons.Rounded.Edit, "Edit", AiModuleTheme.colors.textSecondary) { showEdit = true }
                    ReleaseActionButton(Icons.Rounded.Delete, "Delete", AiModuleTheme.colors.error) { showDelete = true }
                }
            }
        }
    }

    if (showEdit) {
        EditReleaseDialog(
            release = release,
            repoOwner = repoOwner,
            repoName = repoName,
            onDismiss = { showEdit = false },
            onUpdated = { updated ->
                onReleasesUpdate(releases.map { if (it.tag == updated.tag) updated else it })
                showEdit = false
            }
        )
    }

    if (showDelete) {
        AiModuleAlertDialog(
            onDismissRequest = { showDelete = false },
            title = "delete release",
            content = {
                Text(
                    "Are you sure you want to delete ${release.tag}? This cannot be undone.",
                    color = AiModuleTheme.colors.textPrimary,
                    fontSize = 13.sp,
                    fontFamily = JetBrainsMono,
                )
            },
            confirmButton = {
                AiModuleTextAction(
                    label = "delete",
                    tint = AiModuleTheme.colors.error,
                    onClick = {
                        scope.launch {
                            val success = GitHubManager.deleteRelease(context, repoOwner, repoName, release.tag)
                            if (success) {
                                onReleasesUpdate(releases.filter { it.tag != release.tag })
                                Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to delete", Toast.LENGTH_SHORT).show()
                            }
                            showDelete = false
                        }
                    },
                )
            },
            dismissButton = {
                AiModuleTextAction(label = "cancel", onClick = { showDelete = false }, tint = AiModuleTheme.colors.textSecondary)
            }
        )
    }

    val targetAsset = deletingAsset
    if (targetAsset != null) {
        AiModuleAlertDialog(
            onDismissRequest = { deletingAsset = null },
            title = "delete asset",
            content = {
                Text(
                    "Delete ${targetAsset.name} from this release?",
                    color = AiModuleTheme.colors.textPrimary,
                    fontSize = 13.sp,
                    fontFamily = JetBrainsMono,
                )
            },
            confirmButton = {
                AiModuleTextAction(
                    label = "delete",
                    tint = AiModuleTheme.colors.error,
                    onClick = {
                        scope.launch {
                            val ok = GitHubManager.deleteReleaseAsset(context, repoOwner, repoName, targetAsset.id)
                            Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                            if (ok) {
                                onReleasesUpdate(releases.map {
                                    if (it.tag == release.tag) it.copy(assets = it.assets.filterNot { a -> a.id == targetAsset.id }) else it
                                })
                            }
                            deletingAsset = null
                        }
                    },
                )
            },
            dismissButton = {
                AiModuleTextAction(label = "cancel", onClick = { deletingAsset = null }, tint = AiModuleTheme.colors.textSecondary)
            }
        )
    }
}

@Composable
private fun AssetRow(asset: GHAsset, onDownload: () -> Unit, onDelete: (() -> Unit)?) {
    val colors = AiModuleTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(colors.background).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(releaseAssetIcon(asset.name), null, Modifier.size(24.dp), tint = colors.accent.copy(alpha = 0.72f))
        Column(Modifier.weight(1f)) {
            Text(asset.name, fontSize = 12.sp, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(releaseAssetKind(asset.name), fontSize = 10.sp, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatBytes(asset.size), fontSize = 11.sp, color = colors.textMuted)
        Text("${formatGitHubNumber(asset.downloadCount)} downloads", fontSize = 11.sp, color = colors.textMuted)
        IconButton(onClick = onDownload, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Rounded.Download, null, Modifier.size(16.dp), tint = colors.textSecondary)
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Rounded.Delete, null, Modifier.size(16.dp), tint = colors.error)
            }
        }
    }
}

@Composable
private fun ReleaseActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(GitHubControlRadius))
            .border(1.dp, if (enabled) tint else tint.copy(alpha = 0.35f), RoundedCornerShape(GitHubControlRadius))
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (loading) AiModuleSpinner()
            else Icon(icon, null, Modifier.size(14.dp), tint = if (enabled) tint else tint.copy(alpha = 0.35f))
            Text(label.lowercase(Locale.US), color = if (enabled) tint else tint.copy(alpha = 0.35f), fontSize = 12.sp, fontFamily = JetBrainsMono)
        }
    }
}

@Composable
private fun CreateReleaseDialog(
    repoOwner: String,
    repoName: String,
    onDismiss: () -> Unit,
    onCreated: (GHRelease) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tag by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf(true) }
    var prerelease by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }

    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = "create release",
        content = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                AiModuleTextField(tag, { tag = it }, label = "Tag version *", singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                AiModuleTextField(name, { name = it }, label = "Release title", singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                AiModuleTextField(body, { body = it }, label = "Description", modifier = Modifier.fillMaxWidth().height(120.dp), minLines = 4, maxLines = 5)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    AiModuleTextAction(
                        label = if (generating) "generating" else "generate changelog",
                        enabled = !generating,
                        onClick = {
                            generating = true
                            scope.launch {
                                val commits = GitHubManager.getCommits(context, repoOwner, repoName).take(20)
                                body = commits.joinToString("\n") { "- ${it.message.lineSequence().firstOrNull().orEmpty()} (${it.sha})" }
                                generating = false
                            }
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                GitHubTerminalCheckbox("draft", draft, onToggle = { draft = !draft })
                GitHubTerminalCheckbox("pre-release", prerelease, onToggle = { prerelease = !prerelease })
            }
        },
        confirmButton = {
            AiModuleTextAction(
                label = if (loading) "creating" else "create",
                onClick = {
                    if (tag.isBlank()) {
                        Toast.makeText(context, "Tag is required", Toast.LENGTH_SHORT).show()
                        return@AiModuleTextAction
                    }
                    loading = true
                    scope.launch {
                        val release = GitHubManager.createReleaseDetailed(context, repoOwner, repoName, tag, name, body, prerelease, draft)
                        loading = false
                        if (release != null) {
                            onCreated(release)
                            Toast.makeText(context, "Created", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }
                },
                enabled = !loading
            )
        },
        dismissButton = {
            AiModuleTextAction(label = "cancel", onClick = onDismiss, tint = AiModuleTheme.colors.textSecondary)
        }
    )
}

@Composable
private fun EditReleaseDialog(
    release: GHRelease,
    repoOwner: String,
    repoName: String,
    onDismiss: () -> Unit,
    onUpdated: (GHRelease) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(release.name) }
    var body by remember { mutableStateOf(release.body) }
    var draft by remember { mutableStateOf(release.draft) }
    var prerelease by remember { mutableStateOf(release.prerelease) }
    var loading by remember { mutableStateOf(false) }

    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = "edit release",
        content = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                AiModuleTextField(name, { name = it }, label = "Release title", singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                AiModuleTextField(body, { body = it }, label = "Description", modifier = Modifier.fillMaxWidth().height(120.dp), minLines = 4, maxLines = 5)
                Spacer(Modifier.height(8.dp))
                GitHubTerminalCheckbox("draft", draft, onToggle = { draft = !draft })
                GitHubTerminalCheckbox("pre-release", prerelease, onToggle = { prerelease = !prerelease })
            }
        },
        confirmButton = {
            AiModuleTextAction(
                label = if (loading) "saving" else "save",
                onClick = {
                    loading = true
                    scope.launch {
                        val updated = GitHubManager.updateReleaseDetailed(
                            context = context,
                            owner = repoOwner,
                            repo = repoName,
                            tag = release.tag,
                            name = name,
                            body = body,
                            prerelease = prerelease,
                            draft = draft,
                            releaseId = release.id
                        )
                        loading = false
                        if (updated != null) {
                            onUpdated(updated)
                            Toast.makeText(context, "Updated", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }
                },
                enabled = !loading
            )
        },
        dismissButton = {
            AiModuleTextAction(label = "cancel", onClick = onDismiss, tint = AiModuleTheme.colors.textSecondary)
        }
    )
}

private fun formatDate(iso: String): String {
    return try {
        val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val output = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        output.format(input.parse(iso) ?: Date())
    } catch (_: Exception) { iso }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}

private fun openGitHubUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    } catch (_: Exception) {
        Toast.makeText(context, Strings.error, Toast.LENGTH_SHORT).show()
    }
}

private fun cachePickedReleaseAsset(context: Context, uri: Uri): File? {
    return try {
        val name = context.queryDisplayName(uri).sanitizeReleaseAssetName()
        val file = File(context.cacheDir, "release-upload/${System.currentTimeMillis()}-$name")
        file.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        file
    } catch (_: Exception) {
        null
    }
}

private fun Context.queryDisplayName(uri: Uri): String {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) {
            val name = cursor.getString(index)
            if (!name.isNullOrBlank()) return name
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "release-asset.bin"
}

private fun String.sanitizeReleaseAssetName(): String =
    replace(Regex("""[\\/:*?"<>|]+"""), "-").trim().ifBlank { "release-asset.bin" }
