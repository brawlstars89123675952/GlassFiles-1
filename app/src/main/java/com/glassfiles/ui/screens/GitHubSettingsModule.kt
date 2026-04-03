package com.glassfiles.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.glassfiles.data.github.GHAccountProfile
import com.glassfiles.data.github.GHEmailAddress
import com.glassfiles.data.github.GHGpgKeyItem
import com.glassfiles.data.github.GHKeyItem
import com.glassfiles.data.github.GHNotification
import com.glassfiles.data.github.GHOrg
import com.glassfiles.data.github.GHRateLimit
import com.glassfiles.data.github.GHRepo
import com.glassfiles.data.github.GHTokenScopes
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.theme.Blue
import com.glassfiles.ui.theme.SeparatorColor
import com.glassfiles.ui.theme.SurfaceLight
import com.glassfiles.ui.theme.SurfaceWhite
import com.glassfiles.ui.theme.TextPrimary
import com.glassfiles.ui.theme.TextSecondary
import com.glassfiles.ui.theme.TextTertiary
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.ColumnScope

private enum class NativeSettingsTab {
    PROFILE, EMAILS, NOTIFICATIONS, KEYS, ORGANIZATIONS, REPOSITORIES, DEVELOPER, UNSUPPORTED
}

private enum class KeyTab { SSH, SSH_SIGNING, GPG }

@Composable
internal fun GitHubSettingsScreen(onBack: () -> Unit, onLogout: () -> Unit, onClose: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(NativeSettingsTab.PROFILE) }
    var loading by remember { mutableStateOf(false) }

    var profile by remember { mutableStateOf<GHAccountProfile?>(null) }
    var profileName by remember { mutableStateOf("") }
    var profileBio by remember { mutableStateOf("") }
    var profileCompany by remember { mutableStateOf("") }
    var profileLocation by remember { mutableStateOf("") }
    var profileBlog by remember { mutableStateOf("") }
    var profileTwitter by remember { mutableStateOf("") }
    var profileHireable by remember { mutableStateOf(false) }

    var emails by remember { mutableStateOf<List<GHEmailAddress>>(emptyList()) }
    var newEmail by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf("private") }

    var notifications by remember { mutableStateOf<List<GHNotification>>(emptyList()) }
    var includeReadNotifications by remember { mutableStateOf(false) }

    var keyTab by remember { mutableStateOf(KeyTab.SSH) }
    var sshKeys by remember { mutableStateOf<List<GHKeyItem>>(emptyList()) }
    var sshSigningKeys by remember { mutableStateOf<List<GHKeyItem>>(emptyList()) }
    var gpgKeys by remember { mutableStateOf<List<GHGpgKeyItem>>(emptyList()) }
    var keyTitle by remember { mutableStateOf("") }
    var keyBody by remember { mutableStateOf("") }

    var orgs by remember { mutableStateOf<List<GHOrg>>(emptyList()) }
    var repos by remember { mutableStateOf<List<GHRepo>>(emptyList()) }
    var pinnedRepoNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var recentRepoNames by remember { mutableStateOf<List<String>>(emptyList()) }

    var tokenScopes by remember { mutableStateOf<GHTokenScopes?>(null) }
    var rateLimit by remember { mutableStateOf<GHRateLimit?>(null) }
    var actionLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var newToken by remember { mutableStateOf("") }

    suspend fun loadProfile() {
        loading = true
        val p = GitHubManager.getAuthenticatedUserProfile(context)
        profile = p
        profileName = p?.name ?: ""
        profileBio = p?.bio ?: ""
        profileCompany = p?.company ?: ""
        profileLocation = p?.location ?: ""
        profileBlog = p?.blog ?: ""
        profileTwitter = p?.twitterUsername ?: ""
        profileHireable = p?.hireable == true
        loading = false
    }

    suspend fun loadEmails() {
        loading = true
        emails = GitHubManager.getUserEmails(context)
        visibility = emails.firstOrNull { it.primary }?.visibility ?: "private"
        loading = false
    }

    suspend fun loadNotifications() {
        loading = true
        notifications = GitHubManager.getNotifications(context, all = includeReadNotifications)
        loading = false
    }

    suspend fun loadKeys() {
        loading = true
        sshKeys = GitHubManager.getGitSshKeys(context)
        sshSigningKeys = GitHubManager.getSshSigningKeysNative(context)
        gpgKeys = GitHubManager.getGpgKeysNative(context)
        loading = false
    }

    suspend fun loadOrgs() {
        loading = true
        orgs = GitHubManager.getOrganizations(context)
        loading = false
    }

    suspend fun loadRepos() {
        loading = true
        repos = GitHubManager.getRepos(context, 1)
        pinnedRepoNames = GitHubManager.getPinnedRepoNames(context)
        recentRepoNames = GitHubManager.getRecentRepoNames(context)
        loading = false
    }

    suspend fun loadDeveloper() {
        loading = true
        tokenScopes = GitHubManager.getTokenScopes(context)
        rateLimit = GitHubManager.getRateLimit(context)
        actionLogs = GitHubManager.getActionLogs(context)
        loading = false
    }

    LaunchedEffect(selectedTab, includeReadNotifications, keyTab) {
        when (selectedTab) {
            NativeSettingsTab.PROFILE -> loadProfile()
            NativeSettingsTab.EMAILS -> loadEmails()
            NativeSettingsTab.NOTIFICATIONS -> loadNotifications()
            NativeSettingsTab.KEYS -> loadKeys()
            NativeSettingsTab.ORGANIZATIONS -> loadOrgs()
            NativeSettingsTab.REPOSITORIES -> loadRepos()
            NativeSettingsTab.DEVELOPER -> loadDeveloper()
            NativeSettingsTab.UNSUPPORTED -> loading = false
        }
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar("GitHub Settings", onBack = onBack, onClose = onClose)

        profile?.let { p ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(14.dp)).background(SurfaceWhite).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(p.avatarUrl, p.login, Modifier.size(52.dp).clip(CircleShape))
                Column(Modifier.weight(1f)) {
                    Text(p.name.ifBlank { p.login }, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("@${p.login}", color = TextSecondary, fontSize = 13.sp)
                    if (p.email.isNotBlank()) Text(p.email, color = TextTertiary, fontSize = 11.sp)
                }
                IconButton(onClick = {
                    scope.launch {
                        when (selectedTab) {
                            NativeSettingsTab.PROFILE -> loadProfile()
                            NativeSettingsTab.EMAILS -> loadEmails()
                            NativeSettingsTab.NOTIFICATIONS -> loadNotifications()
                            NativeSettingsTab.KEYS -> loadKeys()
                            NativeSettingsTab.ORGANIZATIONS -> loadOrgs()
                            NativeSettingsTab.REPOSITORIES -> loadRepos()
                            NativeSettingsTab.DEVELOPER -> loadDeveloper()
                            NativeSettingsTab.UNSUPPORTED -> Unit
                        }
                    }
                }) {
                    if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = Blue, strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Refresh, null, tint = Blue)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NativeTabChip("Profile", selectedTab == NativeSettingsTab.PROFILE) { selectedTab = NativeSettingsTab.PROFILE }
            NativeTabChip("Emails", selectedTab == NativeSettingsTab.EMAILS) { selectedTab = NativeSettingsTab.EMAILS }
            NativeTabChip("Notifications", selectedTab == NativeSettingsTab.NOTIFICATIONS) { selectedTab = NativeSettingsTab.NOTIFICATIONS }
            NativeTabChip("Keys", selectedTab == NativeSettingsTab.KEYS) { selectedTab = NativeSettingsTab.KEYS }
            NativeTabChip("Organizations", selectedTab == NativeSettingsTab.ORGANIZATIONS) { selectedTab = NativeSettingsTab.ORGANIZATIONS }
            NativeTabChip("Repositories", selectedTab == NativeSettingsTab.REPOSITORIES) { selectedTab = NativeSettingsTab.REPOSITORIES }
            NativeTabChip("Developer", selectedTab == NativeSettingsTab.DEVELOPER) { selectedTab = NativeSettingsTab.DEVELOPER }
            NativeTabChip("Unsupported", selectedTab == NativeSettingsTab.UNSUPPORTED) { selectedTab = NativeSettingsTab.UNSUPPORTED }
        }

        Spacer(Modifier.height(10.dp))

        when (selectedTab) {
            NativeSettingsTab.PROFILE -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                item {
                    NativeSection("Profile") {
                        NativeField(label = "Name", value = profileName, onValueChange = { profileName = it })
                        NativeField(label = "Bio", value = profileBio, onValueChange = { profileBio = it }, singleLine = false, minLines = 3)
                        NativeField(label = "Company", value = profileCompany, onValueChange = { profileCompany = it })
                        NativeField(label = "Location", value = profileLocation, onValueChange = { profileLocation = it })
                        NativeField(label = "Blog", value = profileBlog, onValueChange = { profileBlog = it })
                        NativeField(label = "Twitter username", value = profileTwitter, onValueChange = { profileTwitter = it })
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Switch(checked = profileHireable, onCheckedChange = { profileHireable = it })
                            Text("Available for hire", color = TextPrimary, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            scope.launch {
                                val updated = GitHubManager.updateAuthenticatedUserProfile(
                                    context,
                                    profileName,
                                    profileBio,
                                    profileCompany,
                                    profileLocation,
                                    profileBlog,
                                    profileTwitter,
                                    profileHireable
                                )
                                if (updated != null) {
                                    profile = updated
                                    Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Update failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }, shape = RoundedCornerShape(10.dp)) {
                            Icon(Icons.Rounded.Save, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Save profile")
                        }
                    }
                }
            }

            NativeSettingsTab.EMAILS -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                item {
                    NativeSection("Emails") {
                        Text("Primary visibility", color = TextSecondary, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NativeTabChip("Private", visibility == "private") { visibility = "private" }
                            NativeTabChip("Public", visibility == "public") { visibility = "public" }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            scope.launch {
                                val ok = GitHubManager.setPrimaryEmailVisibility(context, visibility)
                                Toast.makeText(context, if (ok) "Visibility updated" else "Update failed", Toast.LENGTH_SHORT).show()
                                loadEmails()
                            }
                        }, shape = RoundedCornerShape(10.dp)) { Text("Apply visibility") }
                        Spacer(Modifier.height(12.dp))
                        NativeField(label = "Add email", value = newEmail, onValueChange = { newEmail = it })
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            scope.launch {
                                val ok = GitHubManager.addUserEmail(context, newEmail)
                                Toast.makeText(context, if (ok) "Email added" else "Add failed", Toast.LENGTH_SHORT).show()
                                if (ok) newEmail = ""
                                loadEmails()
                            }
                        }, shape = RoundedCornerShape(10.dp)) { Text("Add email") }
                    }
                }
                items(emails) { email ->
                    NativeItemCard(
                        icon = Icons.Rounded.Email,
                        title = email.email,
                        subtitle = listOf(
                            if (email.primary) "Primary" else null,
                            if (email.verified) "Verified" else "Unverified",
                            email.visibility ?: "private"
                        ).filterNotNull().joinToString(" • "),
                        actionLabel = if (email.primary) null else "Delete",
                        onAction = if (email.primary) null else {
                            {
                                scope.launch {
                                    val ok = GitHubManager.deleteUserEmail(context, email.email)
                                    Toast.makeText(context, if (ok) "Email deleted" else "Delete failed", Toast.LENGTH_SHORT).show()
                                    loadEmails()
                                }
                            }
                        }
                    )
                }
            }

            NativeSettingsTab.NOTIFICATIONS -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                item {
                    NativeSection("Notifications") {
                        Text("GitHub notifications API works best with classic PAT tokens.", color = TextTertiary, fontSize = 11.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = includeReadNotifications, onCheckedChange = { includeReadNotifications = it })
                            Text(if (includeReadNotifications) "Include read threads" else "Unread only", color = TextPrimary, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            scope.launch {
                                val ok = GitHubManager.markAllNotificationsRead(context)
                                Toast.makeText(context, if (ok) "All marked read" else "Action failed", Toast.LENGTH_SHORT).show()
                                loadNotifications()
                            }
                        }, shape = RoundedCornerShape(10.dp)) { Text("Mark all read") }
                    }
                }
                items(notifications) { n ->
                    NativeItemCard(
                        icon = Icons.Rounded.Notifications,
                        title = n.title.ifBlank { "Untitled thread" },
                        subtitle = listOf(n.repoName, n.type, n.reason, n.updatedAt.take(19).replace("T", " ")).filter { it.isNotBlank() }.joinToString(" • "),
                        badge = if (n.unread) "UNREAD" else null,
                        actionLabel = if (n.unread) "Read" else null,
                        onAction = if (n.unread) {
                            {
                                scope.launch {
                                    val ok = GitHubManager.markNotificationRead(context, n.id)
                                    Toast.makeText(context, if (ok) "Marked read" else "Action failed", Toast.LENGTH_SHORT).show()
                                    loadNotifications()
                                }
                            }
                        } else null
                    )
                }
            }

            NativeSettingsTab.KEYS -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                item {
                    NativeSection("Keys") {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NativeTabChip("SSH", keyTab == KeyTab.SSH) { keyTab = KeyTab.SSH }
                            NativeTabChip("SSH signing", keyTab == KeyTab.SSH_SIGNING) { keyTab = KeyTab.SSH_SIGNING }
                            NativeTabChip("GPG", keyTab == KeyTab.GPG) { keyTab = KeyTab.GPG }
                        }
                        Spacer(Modifier.height(12.dp))
                        NativeField(label = if (keyTab == KeyTab.GPG) "Name" else "Title", value = keyTitle, onValueChange = { keyTitle = it })
                        NativeField(
                            label = if (keyTab == KeyTab.GPG) "ASCII-armored public key" else "Public key",
                            value = keyBody,
                            onValueChange = { keyBody = it },
                            singleLine = false,
                            minLines = 4
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            scope.launch {
                                val ok = when (keyTab) {
                                    KeyTab.SSH -> GitHubManager.addGitSshKey(context, keyTitle, keyBody)
                                    KeyTab.SSH_SIGNING -> GitHubManager.addSshSigningKeyNative(context, keyTitle, keyBody)
                                    KeyTab.GPG -> GitHubManager.addGpgKeyNative(context, keyTitle, keyBody)
                                }
                                Toast.makeText(context, if (ok) "Key added" else "Add failed", Toast.LENGTH_SHORT).show()
                                if (ok) {
                                    keyTitle = ""
                                    keyBody = ""
                                    loadKeys()
                                }
                            }
                        }, shape = RoundedCornerShape(10.dp)) { Text("Add key") }
                    }
                }
                when (keyTab) {
                    KeyTab.SSH -> items(sshKeys) { key ->
                        NativeItemCard(
                            icon = Icons.Rounded.Key,
                            title = key.title.ifBlank { "SSH key" },
                            subtitle = key.createdAt.take(10),
                            body = key.key,
                            actionLabel = "Delete",
                            onAction = {
                                scope.launch {
                                    val ok = GitHubManager.deleteGitSshKey(context, key.id)
                                    Toast.makeText(context, if (ok) "Key deleted" else "Delete failed", Toast.LENGTH_SHORT).show()
                                    loadKeys()
                                }
                            }
                        )
                    }
                    KeyTab.SSH_SIGNING -> items(sshSigningKeys) { key ->
                        NativeItemCard(
                            icon = Icons.Rounded.Security,
                            title = key.title.ifBlank { "SSH signing key" },
                            subtitle = key.createdAt.take(10),
                            body = key.key,
                            actionLabel = "Delete",
                            onAction = {
                                scope.launch {
                                    val ok = GitHubManager.deleteSshSigningKeyNative(context, key.id)
                                    Toast.makeText(context, if (ok) "Key deleted" else "Delete failed", Toast.LENGTH_SHORT).show()
                                    loadKeys()
                                }
                            }
                        )
                    }
                    KeyTab.GPG -> items(gpgKeys) { key ->
                        NativeItemCard(
                            icon = Icons.Rounded.Lock,
                            title = key.name.ifBlank { key.keyId.ifBlank { "GPG key" } },
                            subtitle = listOf(key.keyId, key.createdAt.take(10)).filter { it.isNotBlank() }.joinToString(" • "),
                            body = if (key.emails.isEmpty()) key.publicKey.take(140) else key.emails.joinToString(", "),
                            actionLabel = "Delete",
                            onAction = {
                                scope.launch {
                                    val ok = GitHubManager.deleteGpgKeyNative(context, key.id)
                                    Toast.makeText(context, if (ok) "Key deleted" else "Delete failed", Toast.LENGTH_SHORT).show()
                                    loadKeys()
                                }
                            }
                        )
                    }
                }
            }

            NativeSettingsTab.ORGANIZATIONS -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                item {
                    NativeSection("Organizations") {
                        Text("Organizations available to the active account.", color = TextSecondary, fontSize = 12.sp)
                    }
                }
                items(orgs) { org ->
                    NativeItemCard(
                        icon = Icons.Rounded.Business,
                        title = org.login,
                        subtitle = org.description.ifBlank { "No description" }
                    )
                }
            }

            NativeSettingsTab.REPOSITORIES -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                item {
                    NativeSection("Pinned repositories") {
                        if (pinnedRepoNames.isEmpty()) Text("No pinned repositories", color = TextTertiary, fontSize = 12.sp)
                        else pinnedRepoNames.forEach { name ->
                            NativeMiniRow(name, actionLabel = "Unpin") {
                                GitHubManager.removePinnedRepo(context, name)
                                pinnedRepoNames = GitHubManager.getPinnedRepoNames(context)
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(10.dp))
                    NativeSection("Recent repositories") {
                        if (recentRepoNames.isEmpty()) Text("No recent repositories", color = TextTertiary, fontSize = 12.sp)
                        else recentRepoNames.forEach { name ->
                            NativeMiniRow(name, actionLabel = "Forget") {
                                GitHubManager.removeRecentRepo(context, name)
                                recentRepoNames = GitHubManager.getRecentRepoNames(context)
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(10.dp))
                    NativeSection("My repositories") {
                        Text("Tap pin to keep a repository in settings.", color = TextSecondary, fontSize = 12.sp)
                    }
                }
                items(repos) { repo ->
                    NativeItemCard(
                        icon = Icons.Rounded.Storage,
                        title = repo.fullName,
                        subtitle = listOf(repo.language, repo.updatedAt.take(10)).filter { it.isNotBlank() }.joinToString(" • "),
                        body = repo.description,
                        actionLabel = if (GitHubManager.isRepoPinned(context, repo.fullName)) "Unpin" else "Pin",
                        onAction = {
                            GitHubManager.togglePinnedRepo(context, repo.fullName)
                            pinnedRepoNames = GitHubManager.getPinnedRepoNames(context)
                        }
                    )
                }
            }

            NativeSettingsTab.DEVELOPER -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                item {
                    NativeSection("Developer settings") {
                        Button(onClick = { showTokenDialog = true }, shape = RoundedCornerShape(10.dp)) {
                            Icon(Icons.Rounded.Key, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Change token")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Token scopes", color = TextSecondary, fontSize = 12.sp)
                        Text(tokenScopes?.scopes?.joinToString(", ").takeUnless { it.isNullOrBlank() } ?: "Unknown / not loaded", color = TextPrimary, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        Text("Rate limit", color = TextSecondary, fontSize = 12.sp)
                        Text(rateLimit?.let { "${it.remaining}/${it.limit} remaining" } ?: "Unknown", color = TextPrimary, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        Text("Last API error", color = TextSecondary, fontSize = 12.sp)
                        Text(GitHubManager.getLastApiError(context).ifBlank { "None" }, color = TextPrimary, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(10.dp))
                        Text("Last HTTP code", color = TextSecondary, fontSize = 12.sp)
                        Text(GitHubManager.getLastHttpCode(context).toString(), color = TextPrimary, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                GitHubManager.clearDiagnostics(context)
                                scope.launch { loadDeveloper() }
                            }, shape = RoundedCornerShape(10.dp)) { Text("Clear diagnostics") }
                            Button(onClick = {
                                GitHubManager.clearActionLogs(context)
                                scope.launch { loadDeveloper() }
                            }, shape = RoundedCornerShape(10.dp)) { Text("Clear logs") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onLogout, shape = RoundedCornerShape(10.dp)) { Text("Sign out") }
                    }
                }
                item {
                    Spacer(Modifier.height(10.dp))
                    NativeSection("Stored action log") {
                        if (actionLogs.isEmpty()) Text("No action log entries", color = TextTertiary, fontSize = 12.sp)
                    }
                }
                items(actionLogs.take(40)) { line ->
                    val parts = line.split("|", limit = 2)
                    val stamp = parts.getOrNull(0)?.toLongOrNull()
                    val msg = parts.getOrNull(1) ?: line
                    NativeItemCard(
                        icon = Icons.Rounded.Check,
                        title = msg,
                        subtitle = stamp?.let { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: "",
                        small = true
                    )
                }
            }

            NativeSettingsTab.UNSUPPORTED -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                item {
                    NativeSection("Not exposed cleanly by GitHub API") {
                        Text("These web GitHub settings are not implemented natively here because GitHub does not expose the same level of public account-management API for them.", color = TextSecondary, fontSize = 12.sp)
                    }
                }
                items(listOf(
                    "Password and authentication",
                    "Sessions",
                    "Billing and plans",
                    "Copilot account preferences",
                    "Pages account-level settings",
                    "Saved replies",
                    "Full browser Security pages"
                )) { name ->
                    NativeItemCard(icon = Icons.Rounded.Lock, title = name, subtitle = "Not supported natively", small = true)
                }
            }
        }
    }

    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    if (newToken.isNotBlank()) {
                        GitHubManager.saveToken(context, newToken)
                        scope.launch {
                            loadProfile()
                            loadDeveloper()
                        }
                        Toast.makeText(context, "Token updated", Toast.LENGTH_SHORT).show()
                        showTokenDialog = false
                        newToken = ""
                    }
                }) { Text("Save", color = Blue) }
            },
            dismissButton = { TextButton(onClick = { showTokenDialog = false }) { Text("Cancel", color = TextSecondary) } },
            title = { Text("Change token") },
            text = {
                OutlinedTextField(
                    value = newToken,
                    onValueChange = { newToken = it },
                    label = { Text("Personal access token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}

@Composable
private fun NativeTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(9.dp))
            .background(if (selected) Blue.copy(alpha = 0.14f) else SurfaceWhite)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(label, color = if (selected) Blue else TextSecondary, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun NativeSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceWhite).padding(14.dp)) {
        Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun NativeField(label: String, value: String, onValueChange: (String) -> Unit = {}, singleLine: Boolean = true, minLines: Int = 1) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun NativeItemCard(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    body: String = "",
    badge: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    small: Boolean = false
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, null, Modifier.size(if (small) 18.dp else 20.dp), tint = Blue)
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = TextPrimary, fontSize = if (small) 13.sp else 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (badge != null) {
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Blue.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(badge, color = Blue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (subtitle.isNotBlank()) Text(subtitle, color = TextSecondary, fontSize = 11.sp)
            if (body.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(body, color = TextTertiary, fontSize = 11.sp, maxLines = 5, overflow = TextOverflow.Ellipsis, fontFamily = if (body.contains("ssh-") || body.contains("BEGIN PGP")) FontFamily.Monospace else null)
            }
        }
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                color = if (actionLabel.contains("Delete") || actionLabel.contains("Forget") || actionLabel.contains("Unpin")) Color(0xFFFF3B30) else Blue,
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onAction)
            )
        }
    }
}

@Composable
private fun NativeMiniRow(title: String, actionLabel: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(actionLabel, color = Color(0xFFFF3B30), fontSize = 12.sp, modifier = Modifier.clickable(onClick = onAction))
    }
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(SeparatorColor))
}