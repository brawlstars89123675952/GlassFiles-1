package com.glassfiles.ui.screens

import android.widget.Toast
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.data.github.GHAsset
import com.glassfiles.data.github.GHRelease
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ReleasesScreen(
    repoOwner: String,
    repoName: String,
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

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar(
            title = "Releases",
            subtitle = repoName,
            onBack = onBack,
            actions = {
                IconButton(onClick = { showCreate = true }) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(22.dp), tint = Blue)
                }
            }
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue)
            }
            return@Column
        }

        if (releases.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.NewReleases,
                title = "No releases",
                subtitle = "Create your first release"
            )
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            items(releases) { release ->
                ReleaseCard(release, repoOwner, repoName) { updatedReleases ->
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
    onReleasesUpdate: (List<GHRelease>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(SurfaceWhite).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(if (release.prerelease) Color(0xFFFF9500) else Color(0xFF34C759)))
            Column(Modifier.weight(1f)) {
                Text(release.name.ifBlank { release.tag }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(release.tag, fontSize = 12.sp, color = TextSecondary)
            }
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    null,
                    Modifier.size(20.dp),
                    tint = TextSecondary
                )
            }
        }

        if (release.createdAt.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(formatDate(release.createdAt), fontSize = 11.sp, color = TextTertiary)
        }

        if (release.body.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(release.body, fontSize = 13.sp, color = TextSecondary, maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
        }

        if (expanded) {
            Spacer(Modifier.height(12.dp))
            if (release.assets.isNotEmpty()) {
                Text("Assets", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                release.assets.forEach { asset ->
                    AssetRow(asset)
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showEdit = true }) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp), tint = Blue)
                    Spacer(Modifier.width(4.dp))
                    Text("Edit", color = Blue, fontSize = 12.sp)
                }
                TextButton(onClick = { showDelete = true }) {
                    Icon(Icons.Rounded.Delete, null, Modifier.size(16.dp), tint = Color(0xFFFF3B30))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete", color = Color(0xFFFF3B30), fontSize = 12.sp)
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
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete Release") },
            text = { Text("Are you sure you want to delete ${release.tag}? This cannot be undone.") },
            confirmButton = {
                TextButton(
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
                    }
                ) {
                    Text("Delete", color = Color(0xFFFF3B30))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AssetRow(asset: GHAsset) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(SurfaceLight).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Rounded.Attachment, null, Modifier.size(16.dp), tint = TextSecondary)
        Text(asset.name, modifier = Modifier.weight(1f), fontSize = 12.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(formatBytes(asset.size), fontSize = 11.sp, color = TextTertiary)
        Text("${asset.downloadCount}↓", fontSize = 11.sp, color = Blue)
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
    var prerelease by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Release") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(tag, { tag = it }, label = { Text("Tag version *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(name, { name = it }, label = { Text("Release title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(body, { body = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth().height(120.dp), maxLines = 5)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(prerelease, { prerelease = it })
                    Text("Pre-release", fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (tag.isBlank()) {
                        Toast.makeText(context, "Tag is required", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    loading = true
                    scope.launch {
                        val success = GitHubManager.createRelease(context, repoOwner, repoName, tag, name, body, prerelease)
                        loading = false
                        if (success) {
                            val newRelease = GHRelease(tag, name, body, prerelease, SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()), emptyList())
                            onCreated(newRelease)
                            Toast.makeText(context, "Created", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }
                },
                enabled = !loading
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
    var prerelease by remember { mutableStateOf(release.prerelease) }
    var loading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Release") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Release title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(body, { body = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth().height(120.dp), maxLines = 5)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(prerelease, { prerelease = it })
                    Text("Pre-release", fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    loading = true
                    scope.launch {
                        val success = GitHubManager.updateRelease(context, repoOwner, repoName, release.tag, name, body, prerelease)
                        loading = false
                        if (success) {
                            onUpdated(release.copy(name = name, body = body, prerelease = prerelease))
                            Toast.makeText(context, "Updated", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }
                },
                enabled = !loading
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
