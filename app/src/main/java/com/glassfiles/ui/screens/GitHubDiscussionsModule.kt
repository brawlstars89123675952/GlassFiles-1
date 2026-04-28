package com.glassfiles.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.glassfiles.data.github.GHComment
import com.glassfiles.data.github.GHDiscussion
import com.glassfiles.data.github.GHDiscussionCategory
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.theme.Blue
import com.glassfiles.ui.theme.SurfaceLight
import com.glassfiles.ui.theme.SurfaceWhite
import com.glassfiles.ui.theme.TextPrimary
import com.glassfiles.ui.theme.TextSecondary
import com.glassfiles.ui.theme.TextTertiary
import kotlinx.coroutines.launch

@Composable
internal fun DiscussionsScreen(
    repoOwner: String,
    repoName: String,
    canWrite: Boolean = true,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var discussions by remember { mutableStateOf<List<GHDiscussion>>(emptyList()) }
    var categories by remember { mutableStateOf<List<GHDiscussionCategory>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var selectedDiscussion by remember { mutableStateOf<GHDiscussion?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    fun loadDiscussions() {
        loading = true
        scope.launch {
            categories = GitHubManager.getDiscussionCategories(context, repoOwner, repoName)
            discussions = GitHubManager.getDiscussions(context, repoOwner, repoName)
            loading = false
        }
    }

    LaunchedEffect(repoOwner, repoName) { loadDiscussions() }

    selectedDiscussion?.let { discussion ->
        DiscussionDetailScreen(
            repoOwner = repoOwner,
            repoName = repoName,
            initialDiscussion = discussion,
            categories = categories,
            onBack = { selectedDiscussion = null },
            onDeleted = {
                selectedDiscussion = null
                loadDiscussions()
            },
            onChanged = { updated ->
                selectedDiscussion = updated
                discussions = discussions.map { if (it.number == updated.number) updated else it }
            }
        )
        return
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar(
            title = "Discussions",
            subtitle = "$repoOwner/$repoName",
            onBack = onBack,
            actions = {
                if (canWrite) {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(22.dp), tint = Blue)
                    }
                }
            }
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        } else {
            val visibleDiscussions = discussions.filter { discussion ->
                val matchesQuery = query.isBlank() ||
                    discussion.title.contains(query, ignoreCase = true) ||
                    discussion.body.contains(query, ignoreCase = true) ||
                    discussion.author.contains(query, ignoreCase = true)
                val matchesCategory = selectedCategoryId == null || discussion.categoryId == selectedCategoryId
                matchesQuery && matchesCategory
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { DiscussionsSummaryCard(discussions, categories) }
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search discussions") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = TextSecondary) }
                    )
                }
                item {
                    DiscussionCategoryFilters(
                        categories = categories,
                        selectedCategoryId = selectedCategoryId,
                        onSelect = { selectedCategoryId = it }
                    )
                }
                items(visibleDiscussions) { discussion ->
                    DiscussionCard(discussion) { selectedDiscussion = discussion }
                }
                if (visibleDiscussions.isEmpty()) {
                    item { EmptyDiscussionsCard(if (discussions.isEmpty()) "No discussions yet" else "No matching discussions") }
                }
            }
        }
    }

    if (showCreateDialog) {
        DiscussionEditorDialog(
            title = "New Discussion",
            categories = categories,
            initialTitle = "",
            initialBody = "",
            initialCategoryId = categories.firstOrNull()?.id.orEmpty(),
            confirmLabel = "Create",
            onDismiss = { showCreateDialog = false },
            onSave = { title, body, categoryId ->
                scope.launch {
                    val ok = GitHubManager.createDiscussion(context, repoOwner, repoName, title, body, categoryId)
                    Toast.makeText(context, if (ok) "Discussion created" else "Failed", Toast.LENGTH_SHORT).show()
                    if (ok) {
                        showCreateDialog = false
                        loadDiscussions()
                    }
                }
            }
        )
    }
}

@Composable
private fun DiscussionsSummaryCard(discussions: List<GHDiscussion>, categories: List<GHDiscussionCategory>) {
    val answered = discussions.count { it.isAnswered }
    val closed = discussions.count { it.state == "closed" }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Forum, null, Modifier.size(18.dp), tint = Blue)
            Column(Modifier.weight(1f)) {
                Text("${discussions.size} discussions", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text("${categories.size} categories", fontSize = 11.sp, color = TextTertiary)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CountChip("Answered", answered, Color(0xFF34C759))
            CountChip("Closed", closed, TextSecondary)
            CountChip("Open", discussions.size - closed, Blue)
        }
    }
}

@Composable
private fun DiscussionCategoryFilters(
    categories: List<GHDiscussionCategory>,
    selectedCategoryId: String?,
    onSelect: (String?) -> Unit
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SelectChip("All", selectedCategoryId == null) { onSelect(null) }
        categories.forEach { category ->
            SelectChip("${category.emoji} ${category.name}".trim(), selectedCategoryId == category.id) {
                onSelect(category.id)
            }
        }
    }
}

@Composable
private fun DiscussionCard(discussion: GHDiscussion, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(if (discussion.isAnswerable) Icons.Rounded.QuestionAnswer else Icons.Rounded.Forum, null, Modifier.size(20.dp), tint = Blue)
            Text(discussion.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (discussion.locked) Icon(Icons.Rounded.Lock, null, Modifier.size(15.dp), tint = TextTertiary)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (discussion.categoryName.isNotBlank()) CountChip("${discussion.categoryEmoji} ${discussion.categoryName}".trim(), 0, Blue, showCount = false)
            if (discussion.isAnswered) CountChip("Answered", 0, Color(0xFF34C759), showCount = false)
            if (discussion.state == "closed") CountChip("Closed", 0, TextSecondary, showCount = false)
        }
        if (discussion.body.isNotBlank()) {
            Text(discussion.body, fontSize = 12.sp, color = TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(discussion.author.ifBlank { "Unknown" }, fontSize = 12.sp, color = Blue)
            Text(discussion.updatedAt.ifBlank { discussion.createdAt }.take(10), fontSize = 11.sp, color = TextTertiary)
            Text("${discussion.comments} comments", fontSize = 11.sp, color = TextSecondary)
            if (discussion.upvotes > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ThumbUp, null, Modifier.size(12.dp), tint = TextTertiary)
                    Text("${discussion.upvotes}", fontSize = 11.sp, color = TextTertiary)
                }
            }
        }
    }
}

@Composable
private fun DiscussionDetailScreen(
    repoOwner: String,
    repoName: String,
    initialDiscussion: GHDiscussion,
    categories: List<GHDiscussionCategory>,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onChanged: (GHDiscussion) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var discussion by remember(initialDiscussion.number) { mutableStateOf(initialDiscussion) }
    var comments by remember(initialDiscussion.number) { mutableStateOf<List<GHComment>>(emptyList()) }
    var loading by remember(initialDiscussion.number) { mutableStateOf(true) }
    var newComment by remember(initialDiscussion.number) { mutableStateOf("") }
    var actionInFlight by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    fun loadDetail() {
        loading = true
        scope.launch {
            GitHubManager.getDiscussionDetail(context, repoOwner, repoName, initialDiscussion.number)?.let {
                discussion = it
                onChanged(it)
            }
            comments = GitHubManager.getDiscussionComments(context, repoOwner, repoName, initialDiscussion.number)
            loading = false
        }
    }

    LaunchedEffect(initialDiscussion.number) { loadDetail() }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar(
            title = "#${discussion.number}",
            subtitle = discussion.title,
            onBack = onBack,
            actions = {
                if (discussion.htmlUrl.isNotBlank()) {
                    IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(discussion.htmlUrl))) }) {
                        Icon(Icons.Rounded.Language, null, Modifier.size(20.dp), tint = TextSecondary)
                    }
                }
                IconButton(onClick = { showEditDialog = true }) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(20.dp), tint = Blue)
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Rounded.Delete, null, Modifier.size(20.dp), tint = Color(0xFFFF3B30))
                }
            }
        )

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { DiscussionBodyCard(discussion) }
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newComment,
                        onValueChange = { newComment = it },
                        label = { Text("Add a comment") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(
                            enabled = !actionInFlight && newComment.isNotBlank() && discussion.id.isNotBlank(),
                            onClick = {
                                actionInFlight = true
                                scope.launch {
                                    val ok = GitHubManager.addDiscussionComment(context, discussion.id, newComment)
                                    Toast.makeText(context, if (ok) "Comment added" else "Failed", Toast.LENGTH_SHORT).show()
                                    actionInFlight = false
                                    if (ok) {
                                        newComment = ""
                                        loadDetail()
                                    }
                                }
                            }
                        ) {
                            Text("Comment")
                        }
                    }
                }
            }
            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Blue, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            } else {
                items(comments) { comment -> DiscussionCommentCard(comment) }
                if (comments.isEmpty()) {
                    item { EmptyDiscussionsCard("No comments yet") }
                }
            }
        }
    }

    if (showEditDialog) {
        DiscussionEditorDialog(
            title = "Edit Discussion",
            categories = categories,
            initialTitle = discussion.title,
            initialBody = discussion.body,
            initialCategoryId = discussion.categoryId,
            confirmLabel = "Save",
            onDismiss = { showEditDialog = false },
            onSave = { title, body, categoryId ->
                actionInFlight = true
                scope.launch {
                    val ok = GitHubManager.updateDiscussion(context, discussion.id, title, body, categoryId)
                    Toast.makeText(context, if (ok) "Discussion updated" else "Failed", Toast.LENGTH_SHORT).show()
                    actionInFlight = false
                    if (ok) {
                        showEditDialog = false
                        loadDetail()
                    }
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = SurfaceWhite,
            title = { Text("Delete Discussion?", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text("Delete #${discussion.number} and all replies?", fontSize = 14.sp, color = TextSecondary) },
            confirmButton = {
                TextButton(
                    enabled = !actionInFlight && discussion.id.isNotBlank(),
                    onClick = {
                        actionInFlight = true
                        scope.launch {
                            val ok = GitHubManager.deleteDiscussion(context, discussion.id)
                            Toast.makeText(context, if (ok) "Deleted" else "Failed", Toast.LENGTH_SHORT).show()
                            actionInFlight = false
                            showDeleteDialog = false
                            if (ok) onDeleted()
                        }
                    }
                ) {
                    Text("Delete", color = Color(0xFFFF3B30))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun DiscussionBodyCard(discussion: GHDiscussion) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (discussion.avatarUrl.isNotBlank()) {
                AsyncImage(discussion.avatarUrl, discussion.author, Modifier.size(34.dp).clip(CircleShape))
            } else {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Blue.copy(0.12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Forum, null, Modifier.size(18.dp), tint = Blue)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(discussion.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text("${discussion.author.ifBlank { "Unknown" }} - ${discussion.createdAt.take(10)}", fontSize = 11.sp, color = TextTertiary)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (discussion.categoryName.isNotBlank()) CountChip("${discussion.categoryEmoji} ${discussion.categoryName}".trim(), 0, Blue, showCount = false)
            CountChip("${discussion.comments} comments", 0, TextSecondary, showCount = false)
            if (discussion.upvotes > 0) CountChip("${discussion.upvotes} upvotes", 0, TextSecondary, showCount = false)
            if (discussion.isAnswered) CountChip("Answered", 0, Color(0xFF34C759), showCount = false)
            if (discussion.locked) CountChip("Locked", 0, Color(0xFFFF9500), showCount = false)
        }
        Text(discussion.body.ifBlank { "No description." }, fontSize = 13.sp, color = TextPrimary, lineHeight = 20.sp)
    }
}

@Composable
private fun DiscussionCommentCard(comment: GHComment) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceWhite).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (comment.avatarUrl.isNotBlank()) {
                AsyncImage(comment.avatarUrl, comment.author, Modifier.size(28.dp).clip(CircleShape))
            }
            Column {
                Text(comment.author.ifBlank { "Unknown" }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Blue)
                Text(comment.createdAt.take(10), fontSize = 10.sp, color = TextTertiary)
            }
        }
        Text(comment.body, fontSize = 13.sp, color = TextPrimary, lineHeight = 18.sp)
    }
}

@Composable
private fun DiscussionEditorDialog(
    title: String,
    categories: List<GHDiscussionCategory>,
    initialTitle: String,
    initialBody: String,
    initialCategoryId: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var draftTitle by remember(initialTitle) { mutableStateOf(initialTitle) }
    var draftBody by remember(initialBody) { mutableStateOf(initialBody) }
    var categoryId by remember(initialCategoryId, categories) { mutableStateOf(initialCategoryId.ifBlank { categories.firstOrNull()?.id.orEmpty() }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draftTitle,
                    onValueChange = { draftTitle = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draftBody,
                    onValueChange = { draftBody = it },
                    label = { Text("Body") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    maxLines = 8
                )
                Text("Category", fontSize = 12.sp, color = TextSecondary)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { category ->
                        SelectChip("${category.emoji} ${category.name}".trim(), categoryId == category.id) {
                            categoryId = category.id
                        }
                    }
                }
                if (categories.isEmpty()) {
                    Text("No discussion categories returned for this repository.", fontSize = 12.sp, color = TextTertiary)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = draftTitle.isNotBlank() && draftBody.isNotBlank() && categoryId.isNotBlank(),
                onClick = { onSave(draftTitle, draftBody, categoryId) }
            ) {
                Text(confirmLabel, color = Blue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) Blue.copy(0.15f) else SurfaceWhite)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(label, fontSize = 12.sp, color = if (selected) Blue else TextPrimary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun CountChip(label: String, count: Int, color: Color, showCount: Boolean = true) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(0.10f)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
        if (showCount) Text("$count", fontSize = 11.sp, color = color)
    }
}

@Composable
private fun EmptyDiscussionsCard(message: String) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, fontSize = 14.sp, color = TextTertiary)
    }
}
