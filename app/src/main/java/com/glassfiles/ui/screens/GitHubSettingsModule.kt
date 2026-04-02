package com.glassfiles.ui.screens

import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.glassfiles.data.Strings
import com.glassfiles.data.github.*
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

// Compact mode — propagates through all sub-screens automatically

@Composable
private fun GitHubSettingsScreen(onBack: () -> Unit, onLogout: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf(GitHubManager.getCachedUser(context)) }
    val token = remember { GitHubManager.getToken(context) }
    var showChangeToken by remember { mutableStateOf(false) }
    var newToken by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar(Strings.ghSettings, onBack = onBack)

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            // Account section
            item {
                Text(Strings.ghAccount, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceWhite)) {
                    // User info
                    if (user != null) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(user!!.avatarUrl, user!!.login, Modifier.size(48.dp).clip(CircleShape))
                            Column(Modifier.weight(1f)) {
                                Text(user!!.name.ifBlank { user!!.login }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("@${user!!.login}", fontSize = 13.sp, color = TextSecondary)
                            }
                        }
                        Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(0.5.dp).background(SeparatorColor))
                    }
                    // Token
                    SettingsRow(Icons.Rounded.Key, Strings.ghToken, Strings.ghTokenHidden) { showChangeToken = true }
                    Box(Modifier.fillMaxWidth().padding(start = 52.dp).height(0.5.dp).background(SeparatorColor))
                    // Logout
                    SettingsRow(Icons.Rounded.Logout, Strings.ghSignIn.let { if (user != null) "Выйти / Sign out" else it }, color = Color(0xFFFF3B30)) { onLogout() }
                }
            }

            // Storage section
            item {
                Spacer(Modifier.height(16.dp))
                Text(Strings.ghClonePath, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceWhite)) {
                    SettingsRow(Icons.Rounded.Folder, "Downloads/GlassFiles_Git", Strings.ghClonePath)
                    Box(Modifier.fillMaxWidth().padding(start = 52.dp).height(0.5.dp).background(SeparatorColor))
                    SettingsRow(Icons.Rounded.DeleteSweep, Strings.ghClearCache) {
                        context.getSharedPreferences("github_prefs", android.content.Context.MODE_PRIVATE).edit().remove("user_json").apply()
                        Toast.makeText(context, Strings.ghCacheClearedMsg, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // About section
            item {
                Spacer(Modifier.height(16.dp))
                Text(Strings.ghAbout, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceWhite)) {
                    SettingsRow(Icons.Rounded.Info, Strings.ghAboutDesc)
                    Box(Modifier.fillMaxWidth().padding(start = 52.dp).height(0.5.dp).background(SeparatorColor))
                    SettingsRow(Icons.Rounded.Code, Strings.ghVersion, "1.0")
                }
            }

            // Features list
            item {
                Spacer(Modifier.height(16.dp))
                Text(Strings.tools, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceWhite)) {
                    FeatureRow("Repos", "Create, browse, star, fork, clone")
                    FeatureRow("Files", "View, edit, upload, delete, download")
                    FeatureRow("Issues & PR", "Create, comment, close, merge")
                    FeatureRow("Actions", "Workflows, runs, artifacts, dispatch")
                    FeatureRow("Releases", "Browse, download assets")
                    FeatureRow("Gists", "Create, view, delete")
                    FeatureRow("Notifications", "View, mark read")
                    FeatureRow("Code Search", "Search inside repos")
                    FeatureRow("Profiles", "View profiles, follow/unfollow")
                    FeatureRow("Organizations", "Browse org repos")
                    FeatureRow("Syntax Highlight", "Code viewer with colors")
                }
            }
        }
    }

    // Change token dialog
    if (showChangeToken) {
        AlertDialog(
            onDismissRequest = { showChangeToken = false }, containerColor = SurfaceWhite,
            title = { Text(Strings.ghChangeToken, fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(Strings.ghTokenHint, fontSize = 11.sp, color = TextTertiary)
                    OutlinedTextField(newToken, { newToken = it }, label = { Text("Personal Access Token") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = {
                if (newToken.isNotBlank()) {
                    GitHubManager.saveToken(context, newToken)
                    scope.launch { user = GitHubManager.getUser(context) }
                    showChangeToken = false; newToken = ""
                }
            }) { Text(Strings.done, color = Blue) } },
            dismissButton = { TextButton(onClick = { showChangeToken = false }) { Text(Strings.cancel, color = TextSecondary) } }
        )
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String? = null, color: Color = TextPrimary, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (color == TextPrimary) TextSecondary else color)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = color)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = TextTertiary)
        }
        if (onClick != null) Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = TextTertiary)
    }
}

@Composable
private fun FeatureRow(title: String, desc: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = Color(0xFF34C759))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(desc, fontSize = 11.sp, color = TextTertiary)
        }
    }
    Box(Modifier.fillMaxWidth().padding(start = 42.dp).height(0.5.dp).background(SeparatorColor))
}
