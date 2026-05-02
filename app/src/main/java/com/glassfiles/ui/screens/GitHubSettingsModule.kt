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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.glassfiles.data.Strings
import androidx.compose.material.icons.rounded.ChevronRight
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.ui.theme.JetBrainsMono
import com.glassfiles.data.github.GHBlockedEntry
import com.glassfiles.data.github.GHEmailEntry
import com.glassfiles.data.github.GHFollowerEntry
import com.glassfiles.data.github.GHInteractionLimitEntry
import com.glassfiles.data.github.GHNotification
import com.glassfiles.data.github.GHOrg
import com.glassfiles.data.github.GHRepo
import com.glassfiles.data.github.GHSocialAccountEntry
import com.glassfiles.data.github.GHUser
import com.glassfiles.data.github.GHUserKeyEntry
import com.glassfiles.data.github.GHUserProfile
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.components.AiModulePageBar
import com.glassfiles.ui.components.AiModuleHairline
import com.glassfiles.ui.components.AiModuleAlertDialog
import com.glassfiles.ui.components.AiModuleIcon as Icon
import com.glassfiles.ui.components.AiModuleSpinner
import com.glassfiles.ui.components.AiModuleText as Text
import com.glassfiles.ui.components.AiModuleTextAction
import kotlinx.coroutines.launch

private enum class SettingsSection(val title: String, val subtitle: String) {
    PROFILE("Profile", "Name, bio, company, location"),
    EMAILS("Emails", "Primary email and visibility"),
    NOTIFICATIONS("Notifications", "Unread filter and mark read"),
    KEYS("Keys", "SSH, SSH signing and GPG"),
    SOCIAL("Social accounts", "Linked social profiles"),
    PEOPLE("People", "Followers and following"),
    BLOCKED("Blocked users", "Block and unblock users"),
    INTERACTION("Interaction limits", "Temporary public interaction limits"),
    ORGANIZATIONS("Organizations", "Your organizations"),
    REPOSITORIES("Repositories", "Starred repositories"),
    DEVELOPER("Developer", "Token and cache")
}

private enum class KeyMode { SSH, SSH_SIGNING, GPG }

@Composable
internal fun GitHubSettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onClose: (() -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var user by remember { mutableStateOf<GHUser?>(GitHubManager.getCachedUser(context)) }
    var currentSection by remember { mutableStateOf<SettingsSection?>(null) }
    var loading by remember { mutableStateOf(false) }

    var profile by remember { mutableStateOf<GHUserProfile?>(null) }
    var profileName by remember { mutableStateOf("") }
    var profileBio by remember { mutableStateOf("") }
    var profileCompany by remember { mutableStateOf("") }
    var profileLocation by remember { mutableStateOf("") }
    var profileBlog by remember { mutableStateOf("") }

    var emails by remember { mutableStateOf<List<GHEmailEntry>>(emptyList()) }
    var newEmail by remember { mutableStateOf("") }
    var emailVisibility by remember { mutableStateOf("private") }

    var notifications by remember { mutableStateOf<List<GHNotification>>(emptyList()) }
    var notificationsUnreadOnly by remember { mutableStateOf(true) }

    var keyMode by remember { mutableStateOf(KeyMode.SSH) }
    var sshKeys by remember { mutableStateOf<List<GHUserKeyEntry>>(emptyList()) }
    var sshSigningKeys by remember { mutableStateOf<List<GHUserKeyEntry>>(emptyList()) }
    var gpgKeys by remember { mutableStateOf<List<GHUserKeyEntry>>(emptyList()) }
    var keyTitle by remember { mutableStateOf("") }
    var keyValue by remember { mutableStateOf("") }

    var socialAccounts by remember { mutableStateOf<List<GHSocialAccountEntry>>(emptyList()) }
    var newSocialUrl by remember { mutableStateOf("") }

    var followers by remember { mutableStateOf<List<GHFollowerEntry>>(emptyList()) }
    var following by remember { mutableStateOf<List<GHFollowerEntry>>(emptyList()) }

    var blockedUsers by remember { mutableStateOf<List<GHBlockedEntry>>(emptyList()) }
    var blockUsername by remember { mutableStateOf("") }

    var interactionLimit by remember { mutableStateOf<GHInteractionLimitEntry?>(null) }
    var organizations by remember { mutableStateOf<List<GHOrg>>(emptyList()) }
    var starredRepos by remember { mutableStateOf<List<GHRepo>>(emptyList()) }
    var rateLimitSummary by remember { mutableStateOf("Unavailable") }
    var showChangeToken by remember { mutableStateOf(false) }
    var newToken by remember { mutableStateOf("") }
    val actionLog = remember { mutableStateListOf<String>() }

    fun addLog(line: String) {
        actionLog.add(0, line)
        while (actionLog.size > 25) actionLog.removeLast()
    }

    suspend fun refreshSection(section: SettingsSection?) {
        loading = true
        when (section) {
            null -> user = GitHubManager.getUser(context) ?: GitHubManager.getCachedUser(context)
            SettingsSection.PROFILE -> {
                user = GitHubManager.getUser(context) ?: GitHubManager.getCachedUser(context)
                profile = GitHubManager.getCurrentUserProfile(context)
                profileName = profile?.name ?: user?.name.orEmpty()
                profileBio = profile?.bio.orEmpty()
                profileCompany = profile?.company.orEmpty()
                profileLocation = profile?.location.orEmpty()
                profileBlog = profile?.blog.orEmpty()
            }
            SettingsSection.EMAILS -> {
                emails = GitHubManager.getEmailEntries(context)
                emailVisibility = emails.firstOrNull { it.primary }?.visibility?.ifBlank { "private" } ?: "private"
            }
            SettingsSection.NOTIFICATIONS -> notifications = GitHubManager.getNotifications(context, all = !notificationsUnreadOnly)
            SettingsSection.KEYS -> {
                sshKeys = GitHubManager.getSshKeysNative(context)
                sshSigningKeys = GitHubManager.getSshSigningKeysNative(context)
                gpgKeys = GitHubManager.getGpgKeysNative(context)
            }
            SettingsSection.SOCIAL -> socialAccounts = GitHubManager.getSocialAccountsNative(context)
            SettingsSection.PEOPLE -> {
                followers = GitHubManager.getFollowersNative(context)
                following = GitHubManager.getFollowingNative(context)
            }
            SettingsSection.BLOCKED -> blockedUsers = GitHubManager.getBlockedUsersNative(context)
            SettingsSection.INTERACTION -> interactionLimit = GitHubManager.getInteractionLimitNative(context)
            SettingsSection.ORGANIZATIONS -> organizations = GitHubManager.getOrganizations(context)
            SettingsSection.REPOSITORIES -> starredRepos = GitHubManager.getStarredRepos(context)
            SettingsSection.DEVELOPER -> rateLimitSummary = GitHubManager.getRateLimitSummaryNative(context)
        }
        loading = false
    }

    LaunchedEffect(currentSection) { refreshSection(currentSection) }

    GitHubScreenFrame(
        title = "> ${(currentSection?.title ?: "settings").lowercase()}",
        subtitle = currentSection?.let { user?.name?.takeIf { n -> n.isNotBlank() } ?: user?.login },
        onBack = { if (currentSection == null) onBack() else currentSection = null },
        trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (loading) {
                        AiModuleSpinner()
                    } else {
                        GitHubTopBarAction(
                            glyph = GhGlyphs.REFRESH,
                            onClick = { scope.launch { refreshSection(currentSection) } },
                            tint = AiModuleTheme.colors.accent,
                            contentDescription = "refresh settings",
                        )
                    }
                    if (onClose != null) {
                        GitHubTopBarAction(
                            glyph = GhGlyphs.CLOSE,
                            onClick = onClose,
                            tint = AiModuleTheme.colors.error,
                            contentDescription = "close",
                        )
                    }
                }
        },
    ) {

        if (currentSection == null) {
            HomeSettingsMenu(user = user, onOpen = { currentSection = it })
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { HomeUserHeader(user) }
                item {
                    when (currentSection) {
                        SettingsSection.PROFILE -> SectionCard("Profile") {
                            CompactField("Name", profileName) { profileName = it }
                            CompactField("Bio", profileBio, singleLine = false, minLines = 3) { profileBio = it }
                            CompactField("Company", profileCompany) { profileCompany = it }
                            CompactField("Location", profileLocation) { profileLocation = it }
                            CompactField("Blog", profileBlog) { profileBlog = it }
                            ActionRow(Icons.Rounded.Check, "Save profile") {
                                scope.launch {
                                    val ok = GitHubManager.updateCurrentUserProfile(context, profileName, profileBio, profileCompany, profileLocation, profileBlog)
                                    addLog("Profile updated: $ok")
                                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                    refreshSection(SettingsSection.PROFILE)
                                }
                            }
                        }
                        SettingsSection.EMAILS -> SectionCard("Emails") {
                            VisibilityChooser(emailVisibility) { emailVisibility = it }
                            ActionRow(Icons.Rounded.Check, "Apply visibility") {
                                scope.launch {
                                    val ok = GitHubManager.setEmailVisibility(context, emailVisibility)
                                    addLog("Email visibility: $ok")
                                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                    refreshSection(SettingsSection.EMAILS)
                                }
                            }
                            CompactField("Add email", newEmail) { newEmail = it }
                            ActionRow(Icons.Rounded.Add, "Add email") {
                                scope.launch {
                                    val ok = GitHubManager.addEmailAddress(context, newEmail)
                                    addLog("Add email: $ok")
                                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                    if (ok) newEmail = ""
                                    refreshSection(SettingsSection.EMAILS)
                                }
                            }
                        }
                        SettingsSection.NOTIFICATIONS -> SectionCard("Notifications") {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        notificationsUnreadOnly = !notificationsUnreadOnly
                                        scope.launch { refreshSection(SettingsSection.NOTIFICATIONS) }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (notificationsUnreadOnly) "[x]" else "[ ]",
                                    color = AiModuleTheme.colors.accent,
                                    fontSize = 13.sp,
                                    fontFamily = JetBrainsMono,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "unread only",
                                    color = AiModuleTheme.colors.textPrimary,
                                    fontSize = 13.sp,
                                    fontFamily = JetBrainsMono,
                                )
                            }
                            ActionRow(Icons.Rounded.Check, "Mark all read") {
                                scope.launch {
                                    val ok = GitHubManager.markAllNotificationsRead(context)
                                    addLog("Mark all read: $ok")
                                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                    refreshSection(SettingsSection.NOTIFICATIONS)
                                }
                            }
                        }
                        SettingsSection.KEYS -> SectionCard("Keys") {
                            KeyModeRow(keyMode) { keyMode = it }
                            CompactField(if (keyMode == KeyMode.GPG) "Name" else "Title", keyTitle) { keyTitle = it }
                            CompactField(if (keyMode == KeyMode.GPG) "ASCII-armored key" else "Public key", keyValue, singleLine = false, minLines = 4) { keyValue = it }
                            ActionRow(Icons.Rounded.Add, "Add key") {
                                scope.launch {
                                    val ok = when (keyMode) {
                                        KeyMode.SSH -> GitHubManager.addSshKeyNative(context, keyTitle, keyValue)
                                        KeyMode.SSH_SIGNING -> GitHubManager.addSshSigningKeyNative(context, keyTitle, keyValue)
                                        KeyMode.GPG -> GitHubManager.addGpgKeyNative(context, keyValue)
                                    }
                                    addLog("Add key: $ok")
                                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                    if (ok) {
                                        keyTitle = ""
                                        keyValue = ""
                                    }
                                    refreshSection(SettingsSection.KEYS)
                                }
                            }
                        }
                        SettingsSection.SOCIAL -> SectionCard("Social accounts") {
                            CompactField("Add social URL", newSocialUrl) { newSocialUrl = it }
                            ActionRow(Icons.Rounded.Add, "Add social account") {
                                scope.launch {
                                    val ok = GitHubManager.addSocialAccountNative(context, newSocialUrl)
                                    addLog("Add social account: $ok")
                                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                    if (ok) newSocialUrl = ""
                                    refreshSection(SettingsSection.SOCIAL)
                                }
                            }
                        }
                        SettingsSection.PEOPLE -> SectionCard("People") {
                            Text("Followers", color = AiModuleTheme.colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            if (followers.isEmpty()) Text("No followers", color = AiModuleTheme.colors.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                            followers.forEach { person ->
                                CompactPersonRow(person.login, person.avatarUrl, "Follow") {
                                    scope.launch {
                                        val ok = GitHubManager.followUser(context, person.login)
                                        addLog("Follow ${person.login}: $ok")
                                        Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Following", color = AiModuleTheme.colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            if (following.isEmpty()) Text("Not following anyone", color = AiModuleTheme.colors.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                            following.forEach { person ->
                                CompactPersonRow(person.login, person.avatarUrl, "Unfollow") {
                                    scope.launch {
                                        val ok = GitHubManager.unfollowUser(context, person.login)
                                        addLog("Unfollow ${person.login}: $ok")
                                        Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                        refreshSection(SettingsSection.PEOPLE)
                                    }
                                }
                            }
                        }
                        SettingsSection.BLOCKED -> SectionCard("Blocked users") {
                            CompactField("Block username", blockUsername) { blockUsername = it }
                            ActionRow(Icons.Rounded.Block, "Block user") {
                                scope.launch {
                                    val ok = GitHubManager.blockUserNative(context, blockUsername)
                                    addLog("Block ${blockUsername}: $ok")
                                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                    if (ok) blockUsername = ""
                                    refreshSection(SettingsSection.BLOCKED)
                                }
                            }
                        }
                        SettingsSection.INTERACTION -> SectionCard("Interaction limits") {
                            Text(interactionLimit?.let { "Current: ${it.limit}${it.expiry?.let { exp -> " • $exp" } ?: ""}" } ?: "No active interaction limit", color = AiModuleTheme.colors.textSecondary, fontSize = 12.sp)
                            ActionRow(Icons.Rounded.Warning, "Existing users for 24h") {
                                scope.launch {
                                    val ok = GitHubManager.setInteractionLimitNative(context, "existing_users", "one_day")
                                    addLog("Set interaction limit: $ok")
                                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                    refreshSection(SettingsSection.INTERACTION)
                                }
                            }
                            ActionRow(Icons.Rounded.Warning, "Contributors only for 24h") {
                                scope.launch {
                                    val ok = GitHubManager.setInteractionLimitNative(context, "contributors_only", "one_day")
                                    addLog("Set interaction limit: $ok")
                                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                    refreshSection(SettingsSection.INTERACTION)
                                }
                            }
                            ActionRow(Icons.Rounded.Warning, "Collaborators only for 24h") {
                                scope.launch {
                                    val ok = GitHubManager.setInteractionLimitNative(context, "collaborators_only", "one_day")
                                    addLog("Set interaction limit: $ok")
                                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                    refreshSection(SettingsSection.INTERACTION)
                                }
                            }
                            ActionRow(Icons.Rounded.Delete, "Remove interaction limit", tint = AiModuleTheme.colors.error) {
                                scope.launch {
                                    val ok = GitHubManager.removeInteractionLimitNative(context)
                                    addLog("Remove interaction limit: $ok")
                                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                    refreshSection(SettingsSection.INTERACTION)
                                }
                            }
                        }
                        SettingsSection.ORGANIZATIONS -> SectionCard("Organizations") {
                            if (organizations.isEmpty()) Text("No organizations", color = AiModuleTheme.colors.textMuted, fontSize = 12.sp)
                            organizations.forEach { org -> CompactOrgRow(org) }
                        }
                        SettingsSection.REPOSITORIES -> SectionCard("Repositories") {
                            if (starredRepos.isEmpty()) Text("No starred repositories", color = AiModuleTheme.colors.textMuted, fontSize = 12.sp)
                            starredRepos.forEach { repo ->
                                CompactRepoRow(repo) {
                                    scope.launch {
                                        val ok = GitHubManager.unstarRepo(context, repo.owner, repo.name)
                                        addLog("Unstar ${repo.fullName}: $ok")
                                        Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                        refreshSection(SettingsSection.REPOSITORIES)
                                    }
                                }
                            }
                        }
                        SettingsSection.DEVELOPER -> SectionCard("Developer") {
                            InfoLine("Token", maskToken(GitHubManager.getToken(context)))
                            InfoLine("Rate limit", rateLimitSummary)
                            ActionRow(Icons.Rounded.Key, "Change token") { showChangeToken = true }
                            ActionRow(Icons.Rounded.Delete, "Clear GitHub cache") {
                                GitHubManager.clearGitHubUserCache(context)
                                addLog("Cleared GitHub cache")
                                Toast.makeText(context, Strings.done, Toast.LENGTH_SHORT).show()
                            }
                            ActionRow(Icons.Rounded.Logout, "Sign out", tint = AiModuleTheme.colors.error) {
                                GitHubManager.logout(context)
                                onLogout()
                            }
                            if (actionLog.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "// recent actions",
                                    color = AiModuleTheme.colors.textMuted,
                                    fontSize = 11.sp,
                                    fontFamily = JetBrainsMono,
                                    fontWeight = FontWeight.Medium,
                                )
                                Spacer(Modifier.height(4.dp))
                                actionLog.forEach { line ->
                                    Text(
                                        line,
                                        color = AiModuleTheme.colors.textMuted,
                                        fontSize = 11.sp,
                                        fontFamily = JetBrainsMono,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                    
                        null -> Unit
                    }
                }

                when (currentSection) {
                    SettingsSection.EMAILS -> items(emails) { email ->
                        CompactCard {
                            EmailRow(email) {
                                scope.launch {
                                    val ok = GitHubManager.deleteEmailAddress(context, email.email)
                                    addLog("Delete email ${email.email}: $ok")
                                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                    refreshSection(SettingsSection.EMAILS)
                                }
                            }
                        }
                    }
                    SettingsSection.NOTIFICATIONS -> items(notifications) { item ->
                        CompactCard {
                            NotificationRow(item) {
                                scope.launch {
                                    val ok = GitHubManager.markNotificationRead(context, item.id)
                                    addLog("Mark thread ${item.id}: $ok")
                                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                    refreshSection(SettingsSection.NOTIFICATIONS)
                                }
                            }
                        }
                    }
                    SettingsSection.KEYS -> {
                        val currentKeys = when (keyMode) {
                            KeyMode.SSH -> sshKeys
                            KeyMode.SSH_SIGNING -> sshSigningKeys
                            KeyMode.GPG -> gpgKeys
                        }
                        items(currentKeys) { key ->
                            CompactCard {
                                KeyRow(key) {
                                    scope.launch {
                                        val ok = when (keyMode) {
                                            KeyMode.SSH -> GitHubManager.deleteSshKeyNative(context, key.id)
                                            KeyMode.SSH_SIGNING -> GitHubManager.deleteSshSigningKeyNative(context, key.id)
                                            KeyMode.GPG -> GitHubManager.deleteGpgKeyNative(context, key.id)
                                        }
                                        addLog("Delete key ${key.id}: $ok")
                                        Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                        refreshSection(SettingsSection.KEYS)
                                    }
                                }
                            }
                        }
                    }
                    SettingsSection.SOCIAL -> items(socialAccounts) { acc ->
                        CompactCard {
                            SocialRow(acc) {
                                scope.launch {
                                    val ok = GitHubManager.deleteSocialAccountNative(context, acc.url)
                                    addLog("Delete social ${acc.url}: $ok")
                                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                    refreshSection(SettingsSection.SOCIAL)
                                }
                            }
                        }
                    }
                    SettingsSection.BLOCKED -> items(blockedUsers) { blocked ->
                        CompactCard {
                            BlockedRow(blocked) {
                                scope.launch {
                                    val ok = GitHubManager.unblockUserNative(context, blocked.login)
                                    addLog("Unblock ${blocked.login}: $ok")
                                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                    refreshSection(SettingsSection.BLOCKED)
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    if (showChangeToken) {
        AiModuleAlertDialog(
            onDismissRequest = { showChangeToken = false },
            title = "change token",
            content = { CompactField("Personal access token", newToken, password = true) { newToken = it } },
            confirmButton = {
                AiModuleTextAction(label = Strings.done.lowercase(), onClick = {
                    GitHubManager.saveToken(context, newToken.trim())
                    addLog("Token updated")
                    newToken = ""
                    showChangeToken = false
                    scope.launch { refreshSection(SettingsSection.DEVELOPER) }
                })
            },
            dismissButton = {
                AiModuleTextAction(label = Strings.cancel.lowercase(), onClick = { showChangeToken = false }, tint = AiModuleTheme.colors.textSecondary)
            },
        )
    }
}

@Composable
private fun HomeSettingsMenu(user: GHUser?, onOpen: (SettingsSection) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item { HomeUserHeader(user) }
        item { TerminalSectionHeader("account") }
        item { MenuRow(Icons.Rounded.Person, SettingsSection.PROFILE, onOpen); AiModuleHairline() }
        item { MenuRow(Icons.Rounded.Email, SettingsSection.EMAILS, onOpen); AiModuleHairline() }
        item { MenuRow(Icons.Rounded.Notifications, SettingsSection.NOTIFICATIONS, onOpen); AiModuleHairline() }
        item { MenuRow(Icons.Rounded.Key, SettingsSection.KEYS, onOpen); AiModuleHairline() }
        item { MenuRow(Icons.Rounded.Public, SettingsSection.SOCIAL, onOpen); AiModuleHairline() }
        item { TerminalSectionHeader("people") }
        item { MenuRow(Icons.Rounded.Group, SettingsSection.PEOPLE, onOpen); AiModuleHairline() }
        item { MenuRow(Icons.Rounded.Block, SettingsSection.BLOCKED, onOpen); AiModuleHairline() }
        item { MenuRow(Icons.Rounded.Warning, SettingsSection.INTERACTION, onOpen); AiModuleHairline() }
        item { TerminalSectionHeader("workspace") }
        item { MenuRow(Icons.Rounded.Business, SettingsSection.ORGANIZATIONS, onOpen); AiModuleHairline() }
        item { MenuRow(Icons.Rounded.Description, SettingsSection.REPOSITORIES, onOpen); AiModuleHairline() }
        item { TerminalSectionHeader("developer") }
        item { MenuRow(Icons.Rounded.Code, SettingsSection.DEVELOPER, onOpen); AiModuleHairline() }
    }
}

@Composable
private fun HomeUserHeader(user: GHUser?) {
    val colors = AiModuleTheme.colors
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = user?.avatarUrl,
                contentDescription = user?.login,
                modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.surfaceElevated)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    user?.name?.takeIf { it.isNotBlank() } ?: user?.login ?: "github",
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "@${user?.login ?: "unknown"}",
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    fontFamily = JetBrainsMono
                )
            }
        }
        AiModuleHairline()
    }
}

@Composable
private fun TerminalSectionHeader(title: String) {
    Text(
        "// $title",
        color = AiModuleTheme.colors.textMuted,
        fontSize = 11.sp,
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(horizontal = 4.dp).padding(top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun CompactCard(content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        content()
        Spacer(Modifier.height(8.dp))
        AiModuleHairline()
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            "// ${title.lowercase()}",
            color = AiModuleTheme.colors.textMuted,
            fontSize = 11.sp,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, color = AiModuleTheme.colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, section: SettingsSection, onOpen: (SettingsSection) -> Unit) {
    val colors = AiModuleTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(section) }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                section.title.lowercase(),
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
            )
            Text(
                section.subtitle,
                color = colors.textMuted,
                fontSize = 11.sp,
                fontFamily = JetBrainsMono,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = colors.textSecondary)
    }
}

@Composable
private fun MenuDivider() {
    AiModuleHairline()
}

@Composable
private fun CompactField(
    label: String,
    value: String,
    singleLine: Boolean = true,
    minLines: Int = 1,
    password: Boolean = false,
    onValueChange: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            "> ${label.lowercase()}",
            color = AiModuleTheme.colors.textMuted,
            fontSize = 11.sp,
            fontFamily = JetBrainsMono,
        )
        Spacer(Modifier.height(4.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = AiModuleTheme.colors.textPrimary,
                fontSize = 13.sp,
                fontFamily = JetBrainsMono,
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(AiModuleTheme.colors.accent),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        AiModuleHairline()
    }
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, tint: Color = AiModuleTheme.colors.accent, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Text(
            "> ${title.lowercase()}",
            color = tint,
            fontSize = 13.sp,
            fontFamily = JetBrainsMono,
        )
    }
}

@Composable
private fun VisibilityChooser(current: String, onSet: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VisibilityChip("private", current == "private") { onSet("private") }
        VisibilityChip("public", current == "public") { onSet("public") }
    }
}

@Composable
private fun VisibilityChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Text(
            if (selected) "[${label.lowercase()}]" else " ${label.lowercase()} ",
            color = if (selected) AiModuleTheme.colors.accent else AiModuleTheme.colors.textMuted,
            fontSize = 12.sp,
            fontFamily = JetBrainsMono,
        )
    }
}

@Composable
private fun KeyModeRow(mode: KeyMode, onSet: (KeyMode) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VisibilityChip("SSH", mode == KeyMode.SSH) { onSet(KeyMode.SSH) }
        VisibilityChip("SSH signing", mode == KeyMode.SSH_SIGNING) { onSet(KeyMode.SSH_SIGNING) }
        VisibilityChip("GPG", mode == KeyMode.GPG) { onSet(KeyMode.GPG) }
    }
}

@Composable
private fun EmailRow(email: GHEmailEntry, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Email, null, tint = AiModuleTheme.colors.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(email.email, color = AiModuleTheme.colors.textPrimary, fontSize = 13.sp, fontFamily = JetBrainsMono, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val tags = buildList {
                if (email.primary) add("primary")
                if (email.verified) add("verified")
                add(email.visibility.ifBlank { "private" })
            }.joinToString(" • ")
            Text(tags, color = AiModuleTheme.colors.textMuted, fontSize = 11.sp, fontFamily = JetBrainsMono)
        }
        AiModuleTextAction(label = "delete", onClick = onDelete, tint = AiModuleTheme.colors.error)
    }
}

@Composable
private fun NotificationRow(item: GHNotification, onMarkRead: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.Notifications, null, tint = if (item.unread) AiModuleTheme.colors.accent else AiModuleTheme.colors.textMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = AiModuleTheme.colors.textPrimary, fontSize = 13.sp, fontFamily = JetBrainsMono, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${item.repoName} • ${item.reason}", color = AiModuleTheme.colors.textMuted, fontSize = 11.sp, fontFamily = JetBrainsMono, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.unread) AiModuleTextAction(label = "read", onClick = onMarkRead)
    }
}

@Composable
private fun KeyRow(key: GHUserKeyEntry, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Key, null, tint = AiModuleTheme.colors.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(key.title.ifBlank { "Key ${key.id}" }, color = AiModuleTheme.colors.textPrimary, fontSize = 13.sp, fontFamily = JetBrainsMono, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${key.kind} • ${key.createdAt.take(10)}", color = AiModuleTheme.colors.textMuted, fontSize = 11.sp, fontFamily = JetBrainsMono)
        }
        AiModuleTextAction(label = "delete", onClick = onDelete, tint = AiModuleTheme.colors.error)
    }
}

@Composable
private fun SocialRow(acc: GHSocialAccountEntry, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Public, null, tint = AiModuleTheme.colors.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(acc.provider.ifBlank { "Social account" }, color = AiModuleTheme.colors.textPrimary, fontSize = 13.sp, fontFamily = JetBrainsMono)
            Text(acc.url, color = AiModuleTheme.colors.textMuted, fontSize = 11.sp, fontFamily = JetBrainsMono, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AiModuleTextAction(label = "delete", onClick = onDelete, tint = AiModuleTheme.colors.error)
    }
}

@Composable
private fun CompactPersonRow(login: String, avatarUrl: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = avatarUrl, contentDescription = login, modifier = Modifier.size(24.dp).clip(CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(login, color = AiModuleTheme.colors.textPrimary, fontSize = 13.sp, fontFamily = JetBrainsMono, modifier = Modifier.weight(1f))
        AiModuleTextAction(label = action.lowercase(), onClick = onAction)
    }
}

@Composable
private fun BlockedRow(entry: GHBlockedEntry, onUnblock: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = entry.avatarUrl, contentDescription = entry.login, modifier = Modifier.size(24.dp).clip(CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(entry.login, color = AiModuleTheme.colors.textPrimary, fontSize = 13.sp, fontFamily = JetBrainsMono, modifier = Modifier.weight(1f))
        AiModuleTextAction(label = "unblock", onClick = onUnblock, tint = AiModuleTheme.colors.error)
    }
}

@Composable
private fun CompactOrgRow(org: GHOrg) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = org.avatarUrl, contentDescription = org.login, modifier = Modifier.size(24.dp).clip(CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(org.login, color = AiModuleTheme.colors.textPrimary, fontSize = 13.sp, fontFamily = JetBrainsMono)
            if (org.description.isNotBlank()) Text(org.description, color = AiModuleTheme.colors.textMuted, fontSize = 11.sp, fontFamily = JetBrainsMono, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CompactRepoRow(repo: GHRepo, onUnstar: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Description, null, tint = AiModuleTheme.colors.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(repo.fullName, color = AiModuleTheme.colors.textPrimary, fontSize = 13.sp, fontFamily = JetBrainsMono, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(repo.language.takeIf { it.isNotBlank() }, repo.updatedAt.takeIf { it.isNotBlank() }?.take(10)).joinToString(" • ")
            if (sub.isNotBlank()) Text(sub, color = AiModuleTheme.colors.textMuted, fontSize = 11.sp, fontFamily = JetBrainsMono)
        }
        AiModuleTextAction(label = "unstar", onClick = onUnstar, tint = AiModuleTheme.colors.error)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("> ${label.lowercase()}", color = AiModuleTheme.colors.textMuted, fontSize = 11.sp, fontFamily = JetBrainsMono)
        Text(value, color = AiModuleTheme.colors.textPrimary, fontSize = 13.sp, fontFamily = JetBrainsMono)
    }
}

private fun maskToken(token: String): String {
    if (token.isBlank()) return "Not set"
    return if (token.length <= 8) "••••••••" else token.take(4) + "••••••••" + token.takeLast(4)
}
