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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.glassfiles.data.Strings
import com.glassfiles.data.github.*
import com.glassfiles.ui.components.AiModuleText as Text
import com.glassfiles.ui.components.AiModuleGlyphAction
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
    val colors = AiModuleTheme.colors
    return this
        .clip(shape)
        .background(color = colors.surface, shape = shape)
        .border(1.dp, colors.border.copy(alpha = 0.65f), shape)
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

@Composable
internal fun GitHubScreenFrame(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AiModuleSurface {
        Column(Modifier.fillMaxSize().background(AiModuleTheme.colors.background)) {
            GitHubPageBar(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                trailing = trailing,
            )
            Box(Modifier.fillMaxWidth().weight(1f)) {
                content()
            }
            if (bottomBar != null) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
                    bottomBar()
                }
            }
        }
    }
}

@Composable
internal fun GitHubPageBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = AiModuleTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.background)
            .statusBarsPadding()
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GitHubTopBarAction(
                glyph = GhGlyphs.BACK,
                onClick = onBack,
                tint = colors.textPrimary,
                contentDescription = "back",
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                color = colors.textPrimary,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                fontSize = AiModuleTheme.type.topBarTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            if (trailing != null) {
                Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
            }
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = colors.textMuted,
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 46.dp, top = 1.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

@Composable
internal fun GitHubTopBarAction(
    glyph: String,
    onClick: () -> Unit,
    tint: Color = AiModuleTheme.colors.textSecondary,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    AiModuleGlyphAction(
        glyph = glyph,
        onClick = onClick,
        tint = tint,
        enabled = enabled,
        contentDescription = contentDescription,
    )
}

@Composable
internal fun GitHubTopBarTextAction(
    label: String,
    onClick: () -> Unit,
    tint: Color = AiModuleTheme.colors.textSecondary,
    enabled: Boolean = true,
) {
    Box(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) tint else tint.copy(alpha = 0.45f),
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
        )
    }
}

@Composable
internal fun GitHubTerminalButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AiModuleTheme.colors.textSecondary,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(2.dp))
            .border(1.dp, if (enabled) color else color.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) color else color.copy(alpha = 0.35f),
            fontFamily = JetBrainsMono,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
internal fun GitHubTerminalTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = AiModuleTheme.colors
    GitHubTerminalButton(
        label = label.lowercase(Locale.US),
        onClick = onClick,
        color = if (selected) palette.accent else palette.textMuted,
    )
}

@Composable
internal fun GitHubTerminalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    minHeight: androidx.compose.ui.unit.Dp = 44.dp,
    singleLine: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
) {
    val palette = AiModuleTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .border(1.dp, palette.textMuted)
            .background(palette.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (value.isEmpty() && placeholder.isNotBlank()) {
            Text(
                placeholder,
                color = palette.textMuted,
                fontFamily = JetBrainsMono,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = palette.textPrimary,
                fontFamily = JetBrainsMono,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
            singleLine = singleLine,
            maxLines = maxLines,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun GitHubTerminalCheckbox(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean = true,
) {
    val palette = AiModuleTheme.colors
    val color = when {
        !enabled -> palette.textMuted.copy(alpha = 0.5f)
        checked -> GitHubSuccessGreen
        else -> palette.textMuted
    }
    Row(
        Modifier
            .let { if (enabled) it.clickable(onClick = onToggle) else it }
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (checked) "[✓]" else "[ ]", color = color, fontFamily = JetBrainsMono, fontSize = 13.sp)
        Text(label.lowercase(Locale.US), color = color, fontFamily = JetBrainsMono, fontSize = 13.sp)
    }
}

@Composable
internal fun GitHubTerminalModal(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = AiModuleTheme.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .background(palette.background)
                .border(1.dp, palette.accent)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                title,
                color = palette.accent,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            content()
        }
    }
}

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
