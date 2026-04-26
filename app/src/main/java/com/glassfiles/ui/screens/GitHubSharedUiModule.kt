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
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.text.font.FontStyle
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
import java.util.Locale

// Compact mode — propagates through all sub-screens automatically

internal val GitHubSuccessGreen = Color(0xFF34C759)
internal val GitHubErrorRed = Color(0xFFFF3B30)
internal val GitHubMergedPurple = Color(0xFF6F42C1)

@Composable internal fun StatBox(label: String, value: String, modifier: Modifier) { Column(modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface); Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable
internal fun Modifier.ghGlassCard(radius: androidx.compose.ui.unit.Dp = 16.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    val colors = MaterialTheme.colorScheme
    return this
        .clip(shape)
        .background(color = colors.surface, shape = shape)
        .border(1.dp, colors.outlineVariant.copy(alpha = 0.08f), shape)
}

@Composable
internal fun RepoCard(repo: GHRepo, onClick: () -> Unit, modifier: Modifier = Modifier, showStats: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val icon = when {
        repo.isArchived -> Icons.Outlined.Archive
        repo.isPrivate -> Icons.Outlined.Lock
        repo.isFork -> Icons.Outlined.CallSplit
        repo.isTemplate -> Icons.Outlined.Description
        else -> Icons.Outlined.Folder
    }
    val iconTint = when {
        repo.isArchived -> colors.error
        repo.isPrivate -> colors.tertiary
        repo.isFork -> colors.onSurfaceVariant
        repo.isTemplate -> colors.secondary
        else -> colors.primary
    }
    val iconBackground = when {
        repo.isArchived -> colors.errorContainer.copy(alpha = 0.55f)
        repo.isPrivate -> colors.tertiaryContainer.copy(alpha = 0.55f)
        repo.isFork -> colors.surfaceVariant
        repo.isTemplate -> colors.secondaryContainer.copy(alpha = 0.45f)
        else -> colors.primaryContainer.copy(alpha = 0.45f)
    }
    Row(
        modifier
            .fillMaxWidth()
            .ghGlassCard(14.dp)
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(iconBackground), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(24.dp), tint = iconTint)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(repo.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                repo.description.ifBlank { "No description" },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                fontStyle = if (repo.description.isBlank()) FontStyle.Italic else FontStyle.Normal,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (repo.language.isBlank()) colors.outline else langColor(repo.language)))
                    Text(repo.language.ifBlank { "Unknown" }, fontSize = 11.sp, color = colors.onSurfaceVariant, fontWeight = FontWeight.Medium)
                }
                if (showStats && repo.stars > 0) Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Star, null, Modifier.size(13.dp), tint = colors.onSurfaceVariant); Text(formatGitHubNumber(repo.stars), fontSize = 11.sp, color = colors.onSurfaceVariant, fontFamily = FontFamily.Monospace) }
                if (showStats && repo.forks > 0) Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.CallSplit, null, Modifier.size(13.dp), tint = colors.onSurfaceVariant); Text(formatGitHubNumber(repo.forks), fontSize = 11.sp, color = colors.onSurfaceVariant, fontFamily = FontFamily.Monospace) }
                if (repo.isFork) Text("fork", fontSize = 10.sp, color = colors.onSurfaceVariant)
            }
        }
    }
}

internal fun langColor(lang: String): Color = when (lang.lowercase()) { "kotlin" -> Color(0xFFA97BFF); "java" -> Color(0xFFB07219); "python" -> Color(0xFF3572A5); "javascript" -> Color(0xFFF1E05A); "typescript" -> Color(0xFF3178C6); "c" -> Color(0xFF555555); "c++" -> Color(0xFFF34B7D); "swift" -> Color(0xFFFFAC45); "go" -> Color(0xFF00ADD8); "rust" -> Color(0xFFDEA584); "dart" -> Color(0xFF00B4AB); "ruby" -> Color(0xFF701516); "php" -> Color(0xFF4F5D95); "c#" -> Color(0xFF178600); "shell" -> Color(0xFF89E051); "html" -> Color(0xFFE34C26); "css" -> Color(0xFF563D7C); "vue" -> Color(0xFF41B883); else -> Color(0xFF8E8E93) }

internal fun ghFmtSize(b: Long): String = when { b < 1024 -> "$b B"; b < 1024L * 1024 -> "%.1f KB".format(b / 1024.0); else -> "%.1f MB".format(b / (1024.0 * 1024)) }

internal fun formatGitHubNumber(n: Int): String = when {
    n < 1_000 -> n.toString()
    n < 1_000_000 -> formatCompactDecimal(n / 1_000.0, "k")
    else -> formatCompactDecimal(n / 1_000_000.0, "M")
}

private fun formatCompactDecimal(value: Double, suffix: String): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) "${rounded.toInt()}$suffix" else String.format(Locale.US, "%.1f%s", rounded, suffix)
}

@Composable
internal fun GitHubWarningAmber(): Color = Color(0xFFFF9500)

internal fun releaseAssetKind(name: String): String {
    val lower = name.lowercase(Locale.US)
    return when {
        lower.endsWith(".apk") -> "Android APK"
        lower.endsWith(".aab") -> "Android App Bundle"
        lower.contains("linux") || lower.endsWith(".deb") || lower.endsWith(".rpm") || lower.endsWith(".appimage") -> "Linux package"
        lower.endsWith(".dmg") || lower.contains("mac") || lower.contains("darwin") -> "macOS build"
        lower.endsWith(".exe") || lower.endsWith(".msi") || lower.contains("win") -> "Windows build"
        lower.endsWith(".zip") || lower.endsWith(".tar.gz") || lower.endsWith(".tar") || lower.endsWith(".tgz") || lower.endsWith(".7z") -> "Archive"
        lower.endsWith(".sha256") || lower.endsWith(".sig") || lower.endsWith(".asc") -> "Checksum / signature"
        else -> "Release asset"
    }
}

internal fun releaseAssetIcon(name: String): ImageVector {
    val lower = name.lowercase(Locale.US)
    return when {
        lower.endsWith(".apk") || lower.endsWith(".aab") -> Icons.Outlined.Android
        lower.contains("linux") || lower.endsWith(".deb") || lower.endsWith(".rpm") || lower.endsWith(".appimage") -> Icons.Outlined.Inventory2
        lower.endsWith(".dmg") || lower.contains("mac") || lower.contains("darwin") -> Icons.Outlined.DesktopMac
        lower.endsWith(".exe") || lower.endsWith(".msi") || lower.contains("win") -> Icons.Outlined.DesktopWindows
        lower.endsWith(".zip") || lower.endsWith(".tar.gz") || lower.endsWith(".tar") || lower.endsWith(".tgz") || lower.endsWith(".7z") -> Icons.Outlined.Archive
        lower.endsWith(".sha256") || lower.endsWith(".sig") || lower.endsWith(".asc") -> Icons.Outlined.Verified
        else -> Icons.Outlined.InsertDriveFile
    }
}

// ═══════════════════════════════════
// GitHub Actions Tab
// ═══════════════════════════════════
