package com.glassfiles.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.data.github.GHCommitDetail
import com.glassfiles.data.github.GHDiffFile
import com.glassfiles.data.github.GHPullFile
import com.glassfiles.data.github.GHReviewComment
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun DiffViewerScreen(
    title: String,
    subtitle: String? = null,
    files: List<GHDiffFile>,
    totalAdditions: Int,
    totalDeletions: Int,
    repoOwner: String? = null,
    repoName: String? = null,
    pullNumber: Int? = null,
    comments: List<GHReviewComment> = emptyList(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedFile by remember { mutableStateOf<GHDiffFile?>(null) }
    var viewMode by remember { mutableStateOf(DiffViewMode.UNIFIED) }
    var showComments by remember { mutableStateOf(false) }

    if (selectedFile != null && !showComments) {
        FileDiffScreen(
            file = selectedFile!!,
            viewMode = viewMode,
            repoOwner = repoOwner,
            repoName = repoName,
            pullNumber = pullNumber,
            comments = comments.filter { it.path == selectedFile!!.filename },
            onBack = { selectedFile = null },
            onViewModeChange = { viewMode = it }
        )
        return
    }

    if (showComments && pullNumber != null) {
        PRReviewCommentsScreen(
            repoOwner = repoOwner!!,
            repoName = repoName!!,
            pullNumber = pullNumber,
            comments = comments,
            onBack = { showComments = false }
        )
        return
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar(
            title = title,
            subtitle = subtitle,
            onBack = onBack,
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("+$totalAdditions", color = Color(0xFF34C759), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text("-$totalDeletions", color = Color(0xFFFF3B30), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                if (pullNumber != null) {
                    IconButton(onClick = { showComments = true }) {
                        Icon(Icons.Rounded.Comment, null, Modifier.size(20.dp), tint = Blue)
                    }
                }
            }
        )

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            items(files) { file ->
                val fileComments = comments.filter { it.path == file.filename }
                DiffFileCard(file, files.indexOf(file) + 1, files.size, fileComments.size) { selectedFile = file }
                if (file != files.lastOrNull()) Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun DiffFileCard(file: GHDiffFile, index: Int, total: Int, commentCount: Int = 0, onClick: () -> Unit) {
    val statusColor = when (file.status) {
        "added" -> Color(0xFF34C759)
        "removed" -> Color(0xFFFF3B30)
        "modified" -> Color(0xFFFF9500)
        "renamed" -> Color(0xFF5856D6)
        else -> TextSecondary
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(SurfaceWhite).clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(statusColor))
            Text(file.filename, modifier = Modifier.weight(1f), fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text("${index}/$total", fontSize = 11.sp, color = TextTertiary)
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(file.status.replaceFirstChar { it.uppercase() }, fontSize = 12.sp, color = statusColor, fontWeight = FontWeight.Medium)
            Text("+${file.additions}", fontSize = 12.sp, color = Color(0xFF34C759))
            Text("-${file.deletions}", fontSize = 12.sp, color = Color(0xFFFF3B30))
            if (commentCount > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Comment, null, Modifier.size(12.dp), tint = Blue)
                    Text("$commentCount", fontSize = 12.sp, color = Blue, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

enum class DiffViewMode { UNIFIED, SPLIT }

@Composable
private fun FileDiffScreen(
    file: GHDiffFile,
    viewMode: DiffViewMode,
    repoOwner: String? = null,
    repoName: String? = null,
    pullNumber: Int? = null,
    comments: List<GHReviewComment> = emptyList(),
    onBack: () -> Unit,
    onViewModeChange: (DiffViewMode) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lines = remember(file.patch) { parseDiffLines(file.patch) }
    var showCommentDialog by remember { mutableStateOf(false) }
    var commentLine by remember { mutableStateOf<Int?>(null) }
    var commentPath by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar(
            title = file.filename.substringAfterLast("/"),
            subtitle = "${file.status} +${file.additions} -${file.deletions}",
            onBack = onBack,
            actions = {
                IconButton(onClick = {
                    onViewModeChange(if (viewMode == DiffViewMode.UNIFIED) DiffViewMode.SPLIT else DiffViewMode.UNIFIED)
                }) {
                    Icon(
                        if (viewMode == DiffViewMode.UNIFIED) Icons.Rounded.ViewColumn else Icons.Rounded.ViewAgenda,
                        null,
                        Modifier.size(20.dp),
                        tint = Blue
                    )
                }
            }
        )

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(lines) { line ->
                val lineNum = when (line) {
                    is PatchDiffLine.Added -> line.lineNum
                    is PatchDiffLine.Context -> line.newLineNum
                    else -> 0
                }
                val lineComments = if (lineNum > 0) comments.filter { it.line == lineNum } else emptyList()

                Column {
                    DiffLineItem(
                        line = line,
                        viewMode = viewMode,
                        onAddComment = if (pullNumber != null && lineNum > 0) {
                            {
                                commentLine = lineNum
                                commentPath = file.filename
                                showCommentDialog = true
                            }
                        } else null
                    )

                    // Show comments for this line
                    if (lineComments.isNotEmpty()) {
                        lineComments.forEach { comment ->
                            CommentBubble(comment)
                        }
                    }
                }
            }
        }
    }

    // Add comment dialog
    if (showCommentDialog && pullNumber != null) {
        var commentBody by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCommentDialog = false },
            containerColor = SurfaceWhite,
            title = { Text("Add comment on line $commentLine", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = commentBody,
                    onValueChange = { commentBody = it },
                    label = { Text("Comment") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    maxLines = 6
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val ok = GitHubManager.createPullRequestReviewComment(
                                context, repoOwner!!, repoName!!, pullNumber,
                                commentBody, commentPath, commentLine!!
                            )
                            Toast.makeText(context, if (ok) "Comment added" else "Failed", Toast.LENGTH_SHORT).show()
                            if (ok) {
                                showCommentDialog = false
                                commentBody = ""
                            }
                        }
                    }
                ) {
                    Text("Comment", color = Blue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCommentDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

sealed class PatchDiffLine {
    data class Header(val text: String) : PatchDiffLine()
    data class Added(val text: String, val lineNum: Int) : PatchDiffLine()
    data class Removed(val text: String, val lineNum: Int) : PatchDiffLine()
    data class Context(val text: String, val oldLineNum: Int, val newLineNum: Int) : PatchDiffLine()
    data class NoNewline(val text: String) : PatchDiffLine()
}

private fun parseDiffLines(patch: String): List<PatchDiffLine> {
    val result = mutableListOf<PatchDiffLine>()
    val lines = patch.lines()
    var oldLine = 0
    var newLine = 0
    var inHunk = false

    for (line in lines) {
        when {
            line.startsWith("@@") -> {
                inHunk = true
                val match = Regex("@@ -(\\d+).*?\\+(\\d+)").find(line)
                oldLine = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
                newLine = match?.groupValues?.get(2)?.toIntOrNull() ?: 0
                result.add(PatchDiffLine.Header(line))
            }
            line.startsWith("+") -> {
                if (inHunk) {
                    result.add(PatchDiffLine.Added(line.drop(1), newLine))
                    newLine++
                }
            }
            line.startsWith("-") -> {
                if (inHunk) {
                    result.add(PatchDiffLine.Removed(line.drop(1), oldLine))
                    oldLine++
                }
            }
            line.startsWith(" ") -> {
                if (inHunk) {
                    result.add(PatchDiffLine.Context(line.drop(1), oldLine, newLine))
                    oldLine++
                    newLine++
                }
            }
            line.startsWith("\\") -> result.add(PatchDiffLine.NoNewline(line))
            else -> result.add(PatchDiffLine.Context(line, oldLine, newLine))
        }
    }
    return result
}

@Composable
private fun DiffLineItem(line: PatchDiffLine, viewMode: DiffViewMode, onAddComment: (() -> Unit)? = null) {
    when (line) {
        is PatchDiffLine.Header -> {
            Box(Modifier.fillMaxWidth().background(Color(0xFF2C2C2E)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(line.text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF8E8E93))
            }
        }
        is PatchDiffLine.Added -> {
            Row(
                Modifier.fillMaxWidth().background(Color(0x0D34C759))
                    .clickable(enabled = onAddComment != null) { onAddComment?.invoke() }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("+${line.lineNum}", modifier = Modifier.width(40.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF34C759))
                Text(line.text, modifier = Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextPrimary)
                if (onAddComment != null) {
                    Icon(Icons.Rounded.AddComment, null, Modifier.size(14.dp), tint = Blue.copy(0.5f))
                }
            }
        }
        is PatchDiffLine.Removed -> {
            Row(Modifier.fillMaxWidth().background(Color(0x0DFF3B30)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text("-${line.lineNum}", modifier = Modifier.width(40.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFFFF3B30))
                Text(line.text, modifier = Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextPrimary)
            }
        }
        is PatchDiffLine.Context -> {
            Row(
                Modifier.fillMaxWidth()
                    .clickable(enabled = onAddComment != null) { onAddComment?.invoke() }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${line.oldLineNum}", modifier = Modifier.width(40.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF6E7681))
                Text(line.text, modifier = Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextSecondary)
                if (onAddComment != null) {
                    Icon(Icons.Rounded.AddComment, null, Modifier.size(14.dp), tint = Blue.copy(0.3f))
                }
            }
        }
        is PatchDiffLine.NoNewline -> {
            Text(line.text, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFFFF9500))
        }
    }
}

@Composable
private fun CommentBubble(comment: GHReviewComment) {
    Column(
        Modifier.fillMaxWidth().padding(start = 48.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
            .clip(RoundedCornerShape(8.dp)).background(SurfaceWhite)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(comment.author, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Blue)
            Text(comment.createdAt.take(10), fontSize = 10.sp, color = TextTertiary)
        }
        Spacer(Modifier.height(4.dp))
        Text(comment.body, fontSize = 13.sp, color = TextPrimary, lineHeight = 18.sp)
    }
}

@Composable
fun PRReviewCommentsScreen(
    repoOwner: String,
    repoName: String,
    pullNumber: Int,
    comments: List<GHReviewComment>,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar(
            title = "Review Comments",
            subtitle = "#$pullNumber · ${comments.size} comments",
            onBack = onBack
        )

        if (comments.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No review comments yet", fontSize = 14.sp, color = TextTertiary)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(comments) { comment ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(comment.path.substringAfterLast("/"), fontSize = 12.sp, color = Blue, fontWeight = FontWeight.Medium)
                            Text("Line ${comment.line}", fontSize = 11.sp, color = TextTertiary)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(comment.author, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(comment.createdAt.take(10), fontSize = 11.sp, color = TextTertiary)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(comment.body, fontSize = 13.sp, color = TextPrimary, lineHeight = 18.sp)
                    }
                }
            }
        }
    }
}

// PR Diff Screen wrapper
@Composable
fun PullRequestDiffScreen(
    repoOwner: String,
    repoName: String,
    pullNumber: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<GHPullFile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var prTitle by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        files = GitHubManager.getPullRequestFiles(context, repoOwner, repoName, pullNumber)
        val pr = GitHubManager.getPullRequests(context, repoOwner, repoName).find { it.number == pullNumber }
        prTitle = pr?.title ?: "PR #$pullNumber"
        loading = false
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Blue)
        }
        return
    }

    val diffFiles = remember(files) {
        files.map { GHDiffFile(it.filename, it.status, it.additions, it.deletions, it.patch) }
    }
    val totalAdditions = files.sumOf { it.additions }
    val totalDeletions = files.sumOf { it.deletions }

    // Load review comments
    var comments by remember { mutableStateOf<List<GHReviewComment>>(emptyList()) }
    LaunchedEffect(Unit) {
        comments = GitHubManager.getPullRequestReviewComments(context, repoOwner, repoName, pullNumber)
    }

    DiffViewerScreen(
        title = prTitle,
        subtitle = "#$pullNumber",
        files = diffFiles,
        totalAdditions = totalAdditions,
        totalDeletions = totalDeletions,
        repoOwner = repoOwner,
        repoName = repoName,
        pullNumber = pullNumber,
        comments = comments,
        onBack = onBack
    )
}

// Commit Diff Screen wrapper
@Composable
fun CommitDiffScreen(
    repoOwner: String,
    repoName: String,
    sha: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<GHCommitDetail?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        detail = GitHubManager.getCommitDiff(context, repoOwner, repoName, sha)
        loading = false
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Blue)
        }
        return
    }

    if (detail == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Failed to load diff", color = TextTertiary)
        }
        return
    }

    DiffViewerScreen(
        title = detail!!.sha.take(7),
        subtitle = detail!!.message.lines().firstOrNull() ?: "",
        files = detail!!.files,
        totalAdditions = detail!!.totalAdditions,
        totalDeletions = detail!!.totalDeletions,
        onBack = onBack
    )
}
