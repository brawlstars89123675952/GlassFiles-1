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
import com.glassfiles.ui.components.aiModuleRepoBadge
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// Compact mode — propagates through all sub-screens automatically

internal val GitHubSuccessGreen = Color(0xFF34C759)
internal val GitHubErrorRed = Color(0xFFFF3B30)
internal val GitHubMergedPurple = Color(0xFF6F42C1)

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
    val palette = AiModuleTheme.colors
    val badge = aiModuleRepoBadge(repo.isArchived, repo.isPrivate, repo.isFork, repo.isTemplate, palette)
    val ago = remember(repo.updatedAt) { repoUpdatedAgoMono(repo.updatedAt) }
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            badge.glyph,
            color = badge.color,
            fontFamily = JetBrainsMono,
            fontSize = 13.sp,
            modifier = Modifier.width(14.dp),
        )
        Text(
            repo.name,
            color = palette.textPrimary,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = repo.language.ifBlank { "—" }.lowercase(Locale.US),
            color = if (repo.language.isBlank()) palette.textMuted else palette.textSecondary,
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.width(72.dp),
            overflow = TextOverflow.Ellipsis,
        )
        if (showStats || repo.stars > 0) {
            Text(
                text = if (repo.stars > 0) "\u2605${formatGitHubNumber(repo.stars)}" else "  —",
                color = if (repo.stars > 0) palette.warning else palette.textMuted,
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                modifier = Modifier.width(48.dp),
            )
        }
        Text(
            text = ago,
            color = palette.textMuted,
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            modifier = Modifier.width(40.dp),
        )
    }
}

private val REPO_AGO_FMT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

private fun repoUpdatedAgoMono(iso: String): String {
    if (iso.isBlank()) return "—"
    val ms = runCatching { REPO_AGO_FMT.parse(iso)?.time }.getOrNull() ?: return "—"
    val diff = (System.currentTimeMillis() - ms).coerceAtLeast(0L)
    val sec = diff / 1000
    return when {
        sec < 60 -> "${sec}s"
        sec < 3600 -> "${sec / 60}m"
        sec < 86400 -> "${sec / 3600}h"
        sec < 604800 -> "${sec / 86400}d"
        sec < 2592000 -> "${sec / 604800}w"
        sec < 31536000 -> "${sec / 2592000}mo"
        else -> "${sec / 31536000}y"
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

/**
 * Terminal-style empty placeholder for GitHub screens. Replaces the
 * earlier glass-card spinner / muted text combo with a mono `<icon>
 * title / subtitle` block that matches the AI module aesthetic
 * (no card chrome, just two centered lines on the page background).
 */
@Composable
internal fun GitHubMonoEmpty(
    title: String,
    subtitle: String? = null,
    leadingGlyph: String = "·",
) {
    val palette = AiModuleTheme.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            Text(
                text = leadingGlyph,
                color = palette.textMuted,
                fontFamily = JetBrainsMono,
                fontSize = 22.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = title,
                color = palette.textSecondary,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = palette.textMuted,
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ═══════════════════════════════════
// GitHub Actions Tab
// ═══════════════════════════════════
