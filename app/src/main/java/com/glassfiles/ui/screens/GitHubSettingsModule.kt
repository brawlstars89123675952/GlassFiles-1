package com.glassfiles.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Web
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.glassfiles.data.Strings
import com.glassfiles.data.github.GHBlockedUser
import com.glassfiles.data.github.GHEmailEntry
import com.glassfiles.data.github.GHFollowerUser
import com.glassfiles.data.github.GHInteractionLimit
import com.glassfiles.data.github.GHNotificationThread
import com.glassfiles.data.github.GHRepo
import com.glassfiles.data.github.GHSocialAccount
import com.glassfiles.data.github.GHUserKey
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.theme.Blue
import com.glassfiles.ui.theme.SeparatorColor
import com.glassfiles.ui.theme.SurfaceLight
import com.glassfiles.ui.theme.SurfaceWhite
import com.glassfiles.ui.theme.TextPrimary
import com.glassfiles.ui.theme.TextSecondary
import com.glassfiles.ui.theme.TextTertiary
import kotlinx.coroutines.launch

private enum class ApiSettingsTab {
    PROFILE,
    EMAILS,
    NOTIFICATIONS,
    KEYS,
    SOCIAL,
    PEOPLE,
    BLOCKED,
    INTERACTION_LIMIT,
    ORGANIZATIONS,
    REPOSITORIES,
    DEVELOPER
}

private enum class KeyTab { SSH, SSH_SIGNING, GPG }

@Composable
internal fun GitHubSettingsScreen(onBack: () -> Unit, onLogout: () -> Unit, onClose: (() -> Unit)? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var user by remember { mutableStateOf(GitHubManager.getCachedUser(context)) }
    var selectedTab by remember { mutableStateOf(ApiSettingsTab.PROFILE) }

    var profileName by remember { mutableStateOf(user?.name ?: "") }
    var profileBio by remember { mutableStateOf(user?.bio ?: "") }
    var profileCompany by remember { mutableStateOf(user?.company ?: "") }
    var profileLocation by remember { mutableStateOf(user?.location ?: "") }
    var profileBlog by remember { mutableStateOf(user?.blog ?: "") }
    var profileTwitter by remember { mutableStateOf(user?.twitterUsername ?: "") }

    var emails by remember { mutableStateOf<List<GHEmailEntry>>(emptyList()) }
    var newEmail by remember { mutableStateOf("") }
    var emailVisibility by remember { mutableStateOf("private") }

    var notifications by remember { mutableStateOf<List<GHNotificationThread>>(emptyList()) }
    var notificationsOnlyUnread by remember { mutableStateOf(true) }

    var keyTab by remember { mutableStateOf(KeyTab.SSH) }
    var sshKeys by remember { mutableStateOf<List<GHUserKey>>(emptyList()) }
    var sshSigningKeys by remember { mutableStateOf<List<GHUserKey>>(emptyList()) }
    var gpgKeys by remember { mutableStateOf<List<GHUserKey>>(emptyList()) }
    var keyTitle by remember { mutableStateOf("") }
    var keyBody by remember { mutableStateOf("") }

    var socialAccounts by remember { mutableStateOf<List<GHSocialAccount>>(emptyList()) }
    var newSocial by remember { mutableStateOf("") }

    var followers by remember { mutableStateOf<List<GHFollowerUser>>(emptyList()) }
    var following by remember { mutableStateOf<List<GHFollowerUser>>(emptyList()) }

    var blockedUsers by remember { mutableStateOf<List<GHBlockedUser>>(emptyList()) }
    var blockTarget by remember { mutableStateOf("") }

    var interactionLimit by remember { mutableStateOf<GHInteractionLimit?>(null) }

    var orgs by remember { mutableStateOf<List<String>>(emptyList()) }
    var starredRepos by remember { mutableStateOf<List<GHRepo>>(emptyList()) }

    var tokenScopes by remember { mutableStateOf<List<String>>(emptyList()) }
    var rateLimitSummary by remember { mutableStateOf("") }
    var diagnosticsText by remember { mutableStateOf("") }
    var actionLog = remember { mutableStateListOf<String>() }

    var showChangeToken by remember { mutableStateOf(false) }
    var newToken by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    fun addLog(entry: String) {
        actionLog.add(0, "${System.currentTimeMillis()}: $entry")
        if (actionLog.size > 100) actionLog.removeLast()
    }

    suspend fun refreshProfile() {
        user = GitHubManager.getUser(context)
        profileName = user?.name ?: ""
        profileBio = user?.bio ?: ""
        profileCompany = user?.company ?: ""
        profileLocation = user?.location ?: ""
        profileBlog = user?.blog ?: ""
        profileTwitter = user?.twitterUsername ?: ""
    }

    suspend fun refreshTab(tab: ApiSettingsTab = selectedTab) {
        loading = true
        when (tab) {
            ApiSettingsTab.PROFILE -> refreshProfile()
            ApiSettingsTab.EMAILS -> emails = GitHubManager.getEmails(context)
            ApiSettingsTab.NOTIFICATIONS -> notifications = GitHubManager.getNotifications(context, onlyUnread = notificationsOnlyUnread)
            ApiSettingsTab.KEYS -> {
                sshKeys = GitHubManager.getSshKeys(context)
                sshSigningKeys = GitHubManager.getSshSigningKeys(context)
                gpgKeys = GitHubManager.getGpgKeys(context)
            }
            ApiSettingsTab.SOCIAL -> socialAccounts = GitHubManager.getSocialAccounts(context)
            ApiSettingsTab.PEOPLE -> {
                followers = GitHubManager.getFollowers(context)
                following = GitHubManager.getFollowing(context)
            }
            ApiSettingsTab.BLOCKED -> blockedUsers = GitHubManager.getBlockedUsers(context)
            ApiSettingsTab.INTERACTION_LIMIT -> interactionLimit = GitHubManager.getInteractionLimit(context)
            ApiSettingsTab.ORGANIZATIONS -> orgs = GitHubManager.getOrganizations(context)
            ApiSettingsTab.REPOSITORIES -> starredRepos = GitHubManager.getStarredRepositories(context)
            ApiSettingsTab.DEVELOPER -> {
                tokenScopes = GitHubManager.getTokenScopes(context)
                rateLimitSummary = GitHubManager.getRateLimitSummary(context)
                diagnosticsText = GitHubManager.getDiagnosticsSummary(context)
            }
        }
        loading = false
    }

    LaunchedEffect(selectedTab) {
        refreshTab(selectedTab)
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar(
            "GitHub Settings",
            subtitle = user?.name?.takeIf { it.isNotBlank() } ?: user?.login ?: "GitHub",
            onBack = onBack,
            onClose = onClose
        )

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceWhite).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AsyncImage(
                    model = user?.avatarUrl,
                    contentDescription = user?.login,
                    modifier = Modifier.size(50.dp).clip(CircleShape)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        user?.name?.takeIf { it.isNotBlank() } ?: user?.login ?: "GitHub",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "@${user?.login ?: "unknown"}",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = Blue, strokeWidth = 2.dp)
                else IconButton(onClick = { scope.launch { refreshTab(selectedTab) } }) {
                    Icon(Icons.Rounded.Refresh, null, tint = Blue)
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 0.dp)
        ) {
            item {
                NativeSection("Account") {
                    NativeSettingsRow("Profile", "Name, bio, company, location, links", selectedTab == ApiSettingsTab.PROFILE) { selectedTab = ApiSettingsTab.PROFILE }
                    NativeDivider()
                    NativeSettingsRow("Emails", "Primary, public visibility, add and delete", selectedTab == ApiSettingsTab.EMAILS) { selectedTab = ApiSettingsTab.EMAILS }
                    NativeDivider()
                    NativeSettingsRow("Notifications", "Threads, unread filter, mark read", selectedTab == ApiSettingsTab.NOTIFICATIONS) { selectedTab = ApiSettingsTab.NOTIFICATIONS }
                    NativeDivider()
                    NativeSettingsRow("Keys", "SSH, SSH signing, GPG", selectedTab == ApiSettingsTab.KEYS) { selectedTab = ApiSettingsTab.KEYS }
                    NativeDivider()
                    NativeSettingsRow("Social accounts", "Linked social profiles", selectedTab == ApiSettingsTab.SOCIAL) { selectedTab = ApiSettingsTab.SOCIAL }
                }
                Spacer(Modifier.height(10.dp))
            }

            item {
                NativeSection("People") {
                    NativeSettingsRow("Followers / Following", "Follow, unfollow, inspect people", selectedTab == ApiSettingsTab.PEOPLE) { selectedTab = ApiSettingsTab.PEOPLE }
                    NativeDivider()
                    NativeSettingsRow("Blocked users", "Block and unblock users", selectedTab == ApiSettingsTab.BLOCKED) { selectedTab = ApiSettingsTab.BLOCKED }
                    NativeDivider()
                    NativeSettingsRow("Interaction limits", "Temporary public interaction limits", selectedTab == ApiSettingsTab.INTERACTION_LIMIT) { selectedTab = ApiSettingsTab.INTERACTION_LIMIT }
                }
                Spacer(Modifier.height(10.dp))
            }

            item {
                NativeSection("Workspace") {
                    NativeSettingsRow("Organizations", "Your organizations", selectedTab == ApiSettingsTab.ORGANIZATIONS) { selectedTab = ApiSettingsTab.ORGANIZATIONS }
                    NativeDivider()
                    NativeSettingsRow("Repositories", "Starred, pinned, recent", selectedTab == ApiSettingsTab.REPOSITORIES) { selectedTab = ApiSettingsTab.REPOSITORIES }
                }
                Spacer(Modifier.height(10.dp))
            }

            item {
                NativeSection("Developer") {
                    NativeSettingsRow("Developer settings", "Scopes, rate limit, diagnostics, token", selectedTab == ApiSettingsTab.DEVELOPER) { selectedTab = ApiSettingsTab.DEVELOPER }
                }
                Spacer(Modifier.height(10.dp))
            }

            item {
                when (selectedTab) {
                    ApiSettingsTab.PROFILE -> NativeSection("Profile") {
                        NativeField(label = "Name", value = profileName, onValueChange = { profileName = it })
                        NativeField(label = "Bio", value = profileBio, onValueChange = { profileBio = it }, singleLine = false, minLines = 3)
                        NativeField(label = "Company", value = profileCompany, onValueChange = { profileCompany = it })
                        NativeField(label = "Location", value = profileLocation, onValueChange = { profileLocation = it })
                        NativeField(label = "Blog", value = profileBlog, onValueChange = { profileBlog = it })
                        NativeField(label = "Twitter username", value = profileTwitter, onValueChange = { profileTwitter = it })
                        NativeActionRow(Icons.Rounded.Check, "Save profile") {
                            scope.launch {
                                val ok = GitHubManager.updateProfile(
                                    context,
                                    name = profileName,
                                    bio = profileBio,
                                    company = profileCompany,
                                    location = profileLocation,
                                    blog = profileBlog,
                                    twitter = profileTwitter
                                )
                                addLog("Profile updated: $ok")
                                Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                refreshProfile()
                            }
                        }
                    }

                    ApiSettingsTab.EMAILS -> NativeSection("Emails") {
                        emails.forEachIndexed { index, email ->
                            NativeEmailRow(email) {
                                scope.launch {
                                    val ok = GitHubManager.deleteEmail(context, email.email)
                                    addLog("Delete email ${email.email}: $ok")
                                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                    refreshTab(ApiSettingsTab.EMAILS)
                                }
                            }
                            if (index != emails.lastIndex) NativeDivider()
                        }
                        if (emails.isNotEmpty()) NativeDivider()
                        NativeField(label = "Add email", value = newEmail, onValueChange = { newEmail = it })
                        NativeActionRow(Icons.Rounded.Add, "Add email") {
                            scope.launch {
                                val ok = GitHubManager.addEmail(context, newEmail)
                                addLog("Add email $newEmail: $ok")
                                Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                if (ok) newEmail = ""
                                refreshTab(ApiSettingsTab.EMAILS)
                            }
                        }
                        NativeDivider()
                        NativeVisibilityToggle(emailVisibility) { visibility ->
                            scope.launch {
                                val ok = GitHubManager.setPrimaryEmailVisibility(context, visibility)
                                emailVisibility = visibility
                                addLog("Set email visibility $visibility: $ok")
                                Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    ApiSettingsTab.NOTIFICATIONS -> NativeSection("Notifications") {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Unread only", fontSize = 14.sp, color = TextPrimary)
                            Switch(
                                checked = notificationsOnlyUnread,
                                onCheckedChange = {
                                    notificationsOnlyUnread = it
                                    scope.launch { refreshTab(ApiSettingsTab.NOTIFICATIONS) }
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = Blue)
                            )
                        }
                        NativeActionRow(Icons.Rounded.Check, "Mark all read") {
                            scope.launch {
                                val ok = GitHubManager.markAllNotificationsRead(context)
                                addLog("Mark all notifications read: $ok")
                                Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                refreshTab(ApiSettingsTab.NOTIFICATIONS)
                            }
                        }
                        if (notifications.isNotEmpty()) NativeDivider()
                        notifications.forEachIndexed { index, item ->
                            NativeNotificationRow(item) {
                                scope.launch {
                                    val ok = GitHubManager.markThreadRead(context, item.id)
                                    addLog("Mark thread ${item.id} read: $ok")
                                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                    refreshTab(ApiSettingsTab.NOTIFICATIONS)
                                }
                            }
                            if (index != notifications.lastIndex) NativeDivider()
                        }
                        if (notifications.isEmpty()) {
                            Text("No notifications", color = TextTertiary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }

                    ApiSettingsTab.KEYS -> NativeSection("Keys") {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            NativeTabChip("SSH", keyTab == KeyTab.SSH) { keyTab = KeyTab.SSH }
                            NativeTabChip("SSH signing", keyTab == KeyTab.SSH_SIGNING) { keyTab = KeyTab.SSH_SIGNING }
                            NativeTabChip("GPG", keyTab == KeyTab.GPG) { keyTab = KeyTab.GPG }
                        }
                        Spacer(Modifier.height(8.dp))
                        val currentKeys = when (keyTab) {
                            KeyTab.SSH -> sshKeys
                            KeyTab.SSH_SIGNING -> sshSigningKeys
                            KeyTab.GPG -> gpgKeys
                        }
                        currentKeys.forEachIndexed { index, key ->
                            NativeKeyRow(key) {
                                scope.launch {
                                    val ok = when (keyTab) {
                                        KeyTab.SSH -> GitHubManager.deleteSshKey(context, key.id)
                                        KeyTab.SSH_SIGNING -> GitHubManager.deleteSshSigningKey(context, key.id)
                                        KeyTab.GPG -> GitHubManager.deleteGpgKey(context, key.id)
                                    }
                                    addLog("Delete key ${key.id}: $ok")
                                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                    refreshTab(ApiSettingsTab.KEYS)
                                }
                            }
                            if (index != currentKeys.lastIndex) NativeDivider()
                        }
                        if (currentKeys.isNotEmpty()) NativeDivider()
                        NativeField(label = if (keyTab == KeyTab.GPG) "Name" else "Title", value = keyTitle, onValueChange = { keyTitle = it })
                        NativeField(
                            label = if (keyTab == KeyTab.GPG) "ASCII-armored public key" else "Public key",
                            value = keyBody,
                            onValueChange = { keyBody = it },
                            singleLine = false,
                            minLines = 4
                        )
                        NativeActionRow(Icons.Rounded.Add, "Add key") {
                            scope.launch {
                                val ok = when (keyTab) {
                                    KeyTab.SSH -> GitHubManager.addSshKey(context, keyTitle, keyBody)
                                    KeyTab.SSH_SIGNING -> GitHubManager.addSshSigningKey(context, keyTitle, keyBody)
                                    KeyTab.GPG -> GitHubManager.addGpgKey(context, keyBody, keyTitle)
                                }
                                addLog("Add key: $ok")
                                Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                if (ok) { keyTitle = ""; keyBody = "" }
                                refreshTab(ApiSettingsTab.KEYS)
                            }
                        }
                    }

                    ApiSettingsTab.SOCIAL -> NativeSection("Social accounts") {
                        socialAccounts.forEachIndexed { index, account ->
                            NativeSocialRow(account) {
                                scope.launch {
                                    val ok = GitHubManager.deleteSocialAccount(context, account.url)
                                    addLog("Delete social ${account.url}: $ok")
                                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                    refreshTab(ApiSettingsTab.SOCIAL)
                                }
                            }
                            if (index != socialAccounts.lastIndex) NativeDivider()
                        }
                        if (socialAccounts.isNotEmpty()) NativeDivider()
                        NativeField(label = "Add social URL", value = newSocial, onValueChange = { newSocial = it })
                        NativeActionRow(Icons.Rounded.Add, "Add social account") {
                            scope.launch {
                                val ok = GitHubManager.addSocialAccount(context, newSocial)
                                addLog("Add social $newSocial: $ok")
                                Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                if (ok) newSocial = ""
                                refreshTab(ApiSettingsTab.SOCIAL)
                            }
                        }
                    }

                    ApiSettingsTab.PEOPLE -> NativeSection("People") {
                        Text("Followers", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
                        followers.forEachIndexed { index, person ->
                            NativePeopleRow(person, following = false) {
                                scope.launch {
                                    val ok = GitHubManager.followUser(context, person.login)
                                    addLog("Follow ${person.login}: $ok")
                                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                    refreshTab(ApiSettingsTab.PEOPLE)
                                }
                            }
                            if (index != followers.lastIndex) NativeDivider()
                        }
                        if (followers.isNotEmpty()) Spacer(Modifier.height(10.dp))
                        Text("Following", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
                        following.forEachIndexed { index, person ->
                            NativePeopleRow(person, following = true) {
                                scope.launch {
                                    val ok = GitHubManager.unfollowUser(context, person.login)
                                    addLog("Unfollow ${person.login}: $ok")
                                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                    refreshTab(ApiSettingsTab.PEOPLE)
                                }
                            }
                            if (index != following.lastIndex) NativeDivider()
                        }
                    }

                    ApiSettingsTab.BLOCKED -> NativeSection("Blocked users") {
                        blockedUsers.forEachIndexed { index, blocked ->
                            NativeBlockedUserRow(blocked) {
                                scope.launch {
                                    val ok = GitHubManager.unblockUser(context, blocked.login)
                                    addLog("Unblock ${blocked.login}: $ok")
                                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                    refreshTab(ApiSettingsTab.BLOCKED)
                                }
                            }
                            if (index != blockedUsers.lastIndex) NativeDivider()
                        }
                        if (blockedUsers.isNotEmpty()) NativeDivider()
                        NativeField(label = "Block user", value = blockTarget, onValueChange = { blockTarget = it })
                        NativeActionRow(Icons.Rounded.Block, "Block user") {
                            scope.launch {
                                val ok = GitHubManager.blockUser(context, blockTarget)
                                addLog("Block $blockTarget: $ok")
                                Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                if (ok) blockTarget = ""
                                refreshTab(ApiSettingsTab.BLOCKED)
                            }
                        }
                    }

                    ApiSettingsTab.INTERACTION_LIMIT -> NativeSection("Interaction limits") {
                        val current = interactionLimit
                        Text(
                            current?.let { "Current: ${it.limit} for ${it.expiry ?: "custom duration"}" } ?: "No active interaction limit",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        NativeActionRow(Icons.Rounded.Warning, "Enable existing users for 24h") {
                            scope.launch {
                                val ok = GitHubManager.setInteractionLimit(context, "existing_users", "one_day")
                                addLog("Set interaction limit existing_users: $ok")
                                Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                refreshTab(ApiSettingsTab.INTERACTION_LIMIT)
                            }
                        }
                        NativeActionRow(Icons.Rounded.Warning, "Enable contributors only for 24h") {
                            scope.launch {
                                val ok = GitHubManager.setInteractionLimit(context, "contributors_only", "one_day")
                                addLog("Set interaction limit contributors_only: $ok")
                                Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                refreshTab(ApiSettingsTab.INTERACTION_LIMIT)
                            }
                        }
                        NativeActionRow(Icons.Rounded.Warning, "Enable collaborators only for 24h") {
                            scope.launch {
                                val ok = GitHubManager.setInteractionLimit(context, "collaborators_only", "one_day")
                                addLog("Set interaction limit collaborators_only: $ok")
                                Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                refreshTab(ApiSettingsTab.INTERACTION_LIMIT)
                            }
                        }
                        NativeActionRow(Icons.Rounded.Close, "Remove interaction limit") {
                            scope.launch {
                                val ok = GitHubManager.removeInteractionLimit(context)
                                addLog("Remove interaction limit: $ok")
                                Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                refreshTab(ApiSettingsTab.INTERACTION_LIMIT)
                            }
                        }
                    }

                    ApiSettingsTab.ORGANIZATIONS -> NativeSection("Organizations") {
                        orgs.forEachIndexed { index, org ->
                            NativeTextRow(org, "Organization")
                            if (index != orgs.lastIndex) NativeDivider()
                        }
                        if (orgs.isEmpty()) {
                            Text("No organizations", color = TextTertiary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }

                    ApiSettingsTab.REPOSITORIES -> NativeSection("Repositories") {
                        starredRepos.forEachIndexed { index, repo ->
                            NativeRepoRow(repo,
                                onOpen = {
                                    addLog("Open repo ${repo.owner}/${repo.name}")
                                },
                                onUnstar = {
                                    scope.launch {
                                        val ok = GitHubManager.unstarRepo(context, repo.owner, repo.name)
                                        addLog("Unstar ${repo.owner}/${repo.name}: $ok")
                                        Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                        refreshTab(ApiSettingsTab.REPOSITORIES)
                                    }
                                },
                                onPin = {
                                    GitHubManager.pinRepository(context, repo)
                                    addLog("Pin ${repo.owner}/${repo.name}")
                                    Toast.makeText(context, Strings.done, Toast.LENGTH_SHORT).show()
                                }
                            )
                            if (index != starredRepos.lastIndex) NativeDivider()
                        }
                        if (starredRepos.isEmpty()) {
                            Text("No starred repositories", color = TextTertiary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }

                    ApiSettingsTab.DEVELOPER -> NativeSection("Developer settings") {
                        Text("Scopes", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        if (tokenScopes.isEmpty()) {
                            Text("No scopes detected", color = TextTertiary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
                        } else {
                            tokenScopes.forEachIndexed { index, scopeName ->
                                NativeTextRow(scopeName, "scope")
                                if (index != tokenScopes.lastIndex) NativeDivider()
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Rate limit", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(rateLimitSummary.ifBlank { "Unknown" }, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Diagnostics", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(diagnosticsText.ifBlank { "No diagnostics" }, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
                        NativeDivider()
                        NativeActionRow(Icons.Rounded.Key, "Change token") { showChangeToken = true }
                        NativeActionRow(Icons.Rounded.Delete, "Clear diagnostics") {
                            GitHubManager.clearDiagnostics(context)
                            diagnosticsText = GitHubManager.getDiagnosticsSummary(context)
                            actionLog.clear()
                            Toast.makeText(context, Strings.done, Toast.LENGTH_SHORT).show()
                        }
                        NativeActionRow(Icons.Rounded.Logout, "Sign out", tint = Color(0xFFFF3B30)) {
                            GitHubManager.logout(context)
                            onLogout()
                        }
                        if (actionLog.isNotEmpty()) {
                            NativeDivider()
                            Text("Action log", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 6.dp))
                            actionLog.take(30).forEachIndexed { index, item ->
                                Text(item, color = TextPrimary, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
                                if (index != actionLog.take(30).lastIndex) NativeDivider()
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }

    if (showChangeToken) {
        AlertDialog(
            onDismissRequest = { showChangeToken = false },
            containerColor = SurfaceWhite,
            title = { Text("Change token", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(Strings.ghTokenHint, fontSize = 11.sp, color = TextTertiary)
                    OutlinedTextField(
                        value = newToken,
                        onValueChange = { newToken = it },
                        label = { Text("Personal Access Token") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newToken.isNotBlank()) {
                        GitHubManager.saveToken(context, newToken)
                        addLog("Token updated")
                        showChangeToken = false
                        newToken = ""
                        scope.launch { refreshTab(ApiSettingsTab.DEVELOPER) }
                    }
                }) { Text(Strings.done, color = Blue) }
            },
            dismissButton = {
                TextButton(onClick = { showChangeToken = false }) { Text(Strings.cancel, color = TextSecondary) }
            }
        )
    }
}

@Composable
private fun NativeSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceWhite).padding(14.dp)
    ) {
        Text(title, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun NativeDivider() {
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(SeparatorColor).padding(vertical = 0.dp))
}

@Composable
private fun NativeSettingsRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (selected) Blue.copy(alpha = 0.08f) else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Rounded.Settings, null, tint = if (selected) Blue else TextSecondary, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (selected) Blue else TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NativeField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit = {},
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = TextPrimary),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Blue,
            unfocusedBorderColor = SeparatorColor,
            focusedLabelColor = Blue,
            unfocusedLabelColor = TextTertiary,
            cursorColor = Blue
        )
    )
}

@Composable
private fun NativeActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, tint: Color = Blue, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Text(title, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun NativeEmailRow(email: GHEmailEntry, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(email.email, color = TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val tags = buildList {
                if (email.primary) add("primary")
                if (email.verified) add("verified")
                add(email.visibility.ifBlank { "private" })
            }.joinToString(" • ")
            Text(tags, color = TextTertiary, fontSize = 11.sp)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, null, tint = Color(0xFFFF3B30), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun NativeNotificationRow(item: GHNotificationThread, onMarkRead: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Notifications, null, tint = if (item.unread) Blue else TextTertiary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.subjectTitle, color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${item.repositoryFullName} • ${item.reason}", color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.unread) {
            TextButton(onClick = onMarkRead) { Text("Read", color = Blue, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun NativeVisibilityToggle(current: String, onSet: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NativeTabChip("private", current == "private") { onSet("private") }
        NativeTabChip("public", current == "public") { onSet("public") }
    }
}

@Composable
private fun NativeTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) Blue.copy(alpha = 0.14f) else SurfaceLight).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (selected) Blue else TextSecondary, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun NativeKeyRow(key: GHUserKey, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Key, null, tint = Blue, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(key.title.ifBlank { key.name.ifBlank { "Key ${key.id}" } }, color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(key.createdAt.take(10), color = TextTertiary, fontSize = 11.sp)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, null, tint = Color(0xFFFF3B30), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun NativeSocialRow(account: GHSocialAccount, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Public, null, tint = Blue, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(account.provider.ifBlank { "Social account" }, color = TextPrimary, fontSize = 13.sp)
            Text(account.url, color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, null, tint = Color(0xFFFF3B30), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun NativePeopleRow(person: GHFollowerUser, following: Boolean, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = person.avatarUrl, contentDescription = person.login, modifier = Modifier.size(28.dp).clip(CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(person.login, color = TextPrimary, fontSize = 13.sp)
            Text(if (following) "Following" else "Follower", color = TextTertiary, fontSize = 11.sp)
        }
        TextButton(onClick = onAction) {
            Text(if (following) "Unfollow" else "Follow", color = Blue, fontSize = 12.sp)
        }
    }
}

@Composable
private fun NativeBlockedUserRow(user: GHBlockedUser, onUnblock: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = user.avatarUrl, contentDescription = user.login, modifier = Modifier.size(28.dp).clip(CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(user.login, color = TextPrimary, fontSize = 13.sp)
            Text("Blocked user", color = TextTertiary, fontSize = 11.sp)
        }
        TextButton(onClick = onUnblock) { Text("Unblock", color = Color(0xFFFF3B30), fontSize = 12.sp) }
    }
}

@Composable
private fun NativeRepoRow(repo: GHRepo, onOpen: () -> Unit, onUnstar: () -> Unit, onPin: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Description, null, tint = Blue, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${repo.owner}/${repo.name}", color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(repo.description.takeIf { it.isNotBlank() } ?: "No description", color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onOpen) { Text("Open", color = Blue, fontSize = 12.sp) }
            TextButton(onClick = onPin) { Text("Pin", color = Blue, fontSize = 12.sp) }
            TextButton(onClick = onUnstar) { Text("Unstar", color = Color(0xFFFF3B30), fontSize = 12.sp) }
        }
    }
}

@Composable
private fun NativeTextRow(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, color = TextPrimary, fontSize = 13.sp)
        Text(subtitle, color = TextTertiary, fontSize = 11.sp)
    }
}