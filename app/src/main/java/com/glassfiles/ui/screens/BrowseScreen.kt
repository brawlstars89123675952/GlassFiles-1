package com.glassfiles.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.glassfiles.data.*
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.components.*
import com.glassfiles.ui.theme.*

@Composable
fun BrowseScreen(
    driveSignedIn: Boolean = false,
    onFolderClick: (FileItem) -> Unit = {},
    onLocationClick: (StorageLocation) -> Unit = {},
    onDriveSignIn: () -> Unit = {},
    onDriveOpen: () -> Unit = {},
    onSearch: () -> Unit = {},
    onTrash: () -> Unit = {},
    onStorage: () -> Unit = {},
    onDuplicates: () -> Unit = {},
    onQrScan: () -> Unit = {},
    onOcr: () -> Unit = {},
    onDeviceInfo: () -> Unit = {},
    onAppManager: () -> Unit = {},
    onBookmarks: () -> Unit = {},
    onDiff: () -> Unit = {},
    onNotes: () -> Unit = {},
    onContentSearch: () -> Unit = {},
    onShizuku: () -> Unit = {},
    onFtp: () -> Unit = {},
    onDualPane: () -> Unit = {},
    onTheme: () -> Unit = {},
    onGitHub: () -> Unit = {},
    onSettings: () -> Unit = {},
    onTagClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var favoritesExpanded by remember { mutableStateOf(true) }
    var locationsExpanded by remember { mutableStateOf(true) }
    var toolsExpanded by remember { mutableStateOf(true) }
    var tagsExpanded by remember { mutableStateOf(false) }
    val favorites = remember { FileManager.getFavorites() }
    val locations = remember { FileManager.getStorageLocations() }
    val ghUser = remember { GitHubManager.getCachedUser(context) }

    LazyColumn(modifier.fillMaxSize().background(SurfaceLight), contentPadding = PaddingValues(bottom = 100.dp)) {
        item { Text(Strings.browse, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, color = TextPrimary,
            modifier = Modifier.padding(top = 60.dp, start = 16.dp, end = 16.dp, bottom = 4.dp)) }
        // Storage warning
        item {
            val stat = android.os.StatFs(android.os.Environment.getExternalStorageDirectory().absolutePath)
            val freeGb = stat.availableBytes / (1024.0 * 1024 * 1024)
            if (freeGb < 2.0) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    .background(Color(0x22FF3B30), RoundedCornerShape(10.dp)).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Warning, null, Modifier.size(20.dp), tint = Red)
                        Text("${Strings.lowStorage}: ${"%.1f".format(freeGb)} GB ${Strings.freeSpace}", color = Red, fontSize = 13.sp)
                    }
                }
            }
        }
        // Search
        item { Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp))
            .background(if (ThemeState.isDark) Color(0xFF2C2C2E) else Color(0xFFE9E9EB)).clickable { onSearch() }.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = TextTertiary)
                Text(Strings.searchOnDevice, fontSize = 15.sp, color = TextTertiary) } } }
        // Favorites
        item { CHeader(Strings.favorites, favoritesExpanded) { favoritesExpanded = !favoritesExpanded } }
        item { AnimatedVisibility(favoritesExpanded, enter = expandVertically(tween(300)) + fadeIn(tween(200)), exit = shrinkVertically(tween(250)) + fadeOut(tween(150))) {
            GlassCard(Modifier.padding(horizontal = 16.dp), cornerRadius = 12.dp) {
                favorites.forEachIndexed { i, fav -> LRow(if (fav.name.contains(Strings.downloads, true)) Icons.Rounded.Download else Icons.Rounded.Folder, fav.name, Blue, count = fav.itemCount) { onFolderClick(fav) }; if (i < favorites.lastIndex) Dv() }
                if (favorites.isEmpty()) Text(Strings.noFavorites, color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
            } } }
        // Locations
        item { CHeader(Strings.locations, locationsExpanded) { locationsExpanded = !locationsExpanded } }
        item { AnimatedVisibility(locationsExpanded, enter = expandVertically(tween(300)) + fadeIn(tween(200)), exit = shrinkVertically(tween(250)) + fadeOut(tween(150))) {
            GlassCard(Modifier.padding(horizontal = 16.dp), cornerRadius = 12.dp) {
                locations.forEachIndexed { _, loc ->
                    val icon = when (loc.icon) { "phone_android" -> Icons.Rounded.PhoneAndroid; "download" -> Icons.Rounded.Download; "photo" -> Icons.Rounded.Photo; "music" -> Icons.Rounded.MusicNote; else -> Icons.Rounded.Folder }
                    LRow(icon, loc.name, loc.color) { onLocationClick(loc) }; Dv() }
                if (driveSignedIn) LRow(Icons.Rounded.CloudQueue, "Google Drive", Color(0xFF4285F4), iconBgColor = Color(0xFFE8F0FE)) { onDriveOpen() }
                else LRow(Icons.Rounded.CloudOff, "Google Drive", Color(0xFF999999), subtitle = Strings.tapToSignIn) { onDriveSignIn() }
                Dv()
                // Trash with size
                val trashSize = remember {
                    val trashDir = java.io.File(android.os.Environment.getExternalStorageDirectory(), ".glass_trash")
                    if (trashDir.exists()) trashDir.walkTopDown().filter { it.isFile && it.name != "metadata.json" }.sumOf { it.length() } else 0L
                }
                val trashSizeText = remember(trashSize) { if (trashSize > 0) fmtTrashSize(trashSize) else null }
                LRow(Icons.Rounded.DeleteOutline, Strings.trash, Red, subtitle = trashSizeText) { onTrash() }
            } } }
        // GitHub card — prominent at top
        item {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(16.dp))
                .background(if (ThemeState.isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF))
                .clickable { onGitHub() }.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (ghUser != null && ghUser.avatarUrl.isNotBlank()) {
                        AsyncImage(ghUser.avatarUrl, ghUser.login, Modifier.size(52.dp).clip(CircleShape))
                    } else {
                        Box(Modifier.size(52.dp).clip(CircleShape).background(TextPrimary), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Code, null, Modifier.size(26.dp), tint = SurfaceLight)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (ghUser != null) ghUser.name.ifBlank { "@${ghUser.login}" } else "GitHub",
                            fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (ghUser != null) "${ghUser.publicRepos + ghUser.privateRepos} ${Strings.ghRepos.lowercase()}"
                            else Strings.ghSignIn,
                            fontSize = 13.sp, color = TextSecondary)
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, Modifier.size(14.dp), tint = TextTertiary)
                }
            }
        }
        // Tools
        item { CHeader(Strings.tools, toolsExpanded) { toolsExpanded = !toolsExpanded } }
        item { AnimatedVisibility(toolsExpanded, enter = expandVertically(tween(300)) + fadeIn(tween(200)), exit = shrinkVertically(tween(250)) + fadeOut(tween(150))) {
            GlassCard(Modifier.padding(horizontal = 16.dp), cornerRadius = 12.dp) {
                LRow(Icons.Rounded.PieChart, Strings.storageAnalysis, Color(0xFF00BCD4), subtitle = Strings.whatTakesSpace) { onStorage() }
                Dv(); LRow(Icons.Rounded.FileCopy, Strings.duplicates, Color(0xFFFF9800), subtitle = Strings.findDuplicates) { onDuplicates() }
                Dv(); LRow(Icons.Rounded.QrCode2, Strings.qrScanner, Color(0xFF9C27B0), subtitle = Strings.scanQrCode) { onQrScan() }
                Dv(); LRow(Icons.Rounded.DocumentScanner, Strings.recognizeText, Color(0xFF4CAF50), subtitle = Strings.ocrSubtitle) { onOcr() }
                Dv(); LRow(Icons.Rounded.PhoneAndroid, Strings.deviceInfo, Color(0xFF607D8B), subtitle = Strings.deviceInfoSubtitle) { onDeviceInfo() }
                Dv(); LRow(Icons.Rounded.Apps, Strings.appManager, Color(0xFF2196F3), subtitle = Strings.appManagerSub) { onAppManager() }
                Dv(); LRow(Icons.Rounded.Bookmark, Strings.bookmarks, Color(0xFFE91E63), subtitle = Strings.bookmarksSub) { onBookmarks() }
                Dv(); LRow(Icons.Rounded.Compare, Strings.fileDiff, Color(0xFF795548), subtitle = Strings.fileDiffSub) { onDiff() }
                Dv(); LRow(Icons.Rounded.StickyNote2, Strings.quickNotes, Color(0xFFFFC107), subtitle = Strings.quickNotesSub) { onNotes() }
                Dv(); LRow(Icons.Rounded.ContentPasteSearch, Strings.contentSearch, Color(0xFF009688), subtitle = Strings.contentSearchSub) { onContentSearch() }
                Dv(); LRow(Icons.Rounded.Security, Strings.shizuku, Color(0xFF673AB7), subtitle = Strings.shizukuSub) { onShizuku() }
                Dv(); LRow(Icons.Rounded.Cloud, Strings.ftpClient, Color(0xFF00BCD4), subtitle = Strings.ftpClientSub) { onFtp() }
                Dv(); LRow(Icons.Rounded.ViewColumn, Strings.dualPane, Color(0xFF607D8B), subtitle = Strings.dualPaneSub) { onDualPane() }
                Dv(); LRow(Icons.Rounded.Palette, Strings.themeCustomize, Color(0xFFE91E63), subtitle = Strings.themeCustomizeSub) { onTheme() }
                Dv(); LRow(Icons.Rounded.Settings, Strings.settings, Color(0xFF8E8E93), subtitle = Strings.settingsSub) { onSettings() }
            } } }
        // Tags
        item { CHeader(Strings.tags, tagsExpanded) { tagsExpanded = !tagsExpanded } }
        item { AnimatedVisibility(tagsExpanded, enter = expandVertically(tween(300)) + fadeIn(tween(200)), exit = shrinkVertically(tween(250)) + fadeOut(tween(150))) {
            GlassCard(Modifier.padding(horizontal = 16.dp), cornerRadius = 12.dp) {
                listOf(Strings.tagRed to Red, Strings.tagOrange to Orange, Strings.tagYellow to Yellow, Strings.tagGreen to Green, Strings.tagBlue to Blue, Strings.tagPurple to Purple).forEachIndexed { i, (n, c) ->
                    TRow(n, c) { onTagClick(n) }; if (i < 5) Dv() }
            } } }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable private fun CHeader(title: String, expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 0f else -90f, tween(250), label = "chev")
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(24.dp).rotate(rotation), tint = Blue) } }

@Composable private fun LRow(icon: ImageVector, label: String, iconTint: Color, iconBgColor: Color = iconTint.copy(0.1f),
    count: Int = -1, subtitle: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(30.dp).background(iconBgColor, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(18.dp), tint = iconTint) }
        Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary); if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = TextSecondary) }
        if (count >= 0) Text("$count", fontSize = 14.sp, color = TextSecondary)
        Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, Modifier.size(14.dp), tint = TextTertiary) } }

@Composable private fun TRow(name: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(14.dp).background(color, CircleShape))
        Text(name, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, Modifier.size(14.dp), tint = TextTertiary) } }

@Composable private fun Dv() { Box(Modifier.fillMaxWidth().padding(start = 58.dp).height(0.5.dp).background(SeparatorColor)) }

private fun fmtTrashSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024L * 1024 -> "%.1f KB".format(b / 1024.0)
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / (1024.0 * 1024))
    else -> "%.2f GB".format(b / (1024.0 * 1024 * 1024))
}
