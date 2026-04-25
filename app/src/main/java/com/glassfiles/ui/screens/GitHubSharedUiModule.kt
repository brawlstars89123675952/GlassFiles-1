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

@Composable internal fun StatBox(label: String, value: String, modifier: Modifier) { Column(modifier.clip(RoundedCornerShape(10.dp)).background(SurfaceWhite).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary); Text(label, fontSize = 11.sp, color = TextSecondary) } }

@Composable
internal fun Modifier.ghGlassCard(radius: androidx.compose.ui.unit.Dp = 16.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    val colors = MaterialTheme.colorScheme
    return this
        .background(color = colors.surface, shape = shape)
        .border(1.dp, colors.outlineVariant.copy(alpha = 0.08f), shape)
}

@Composable
internal fun RepoCard(repo: GHRepo, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).ghGlassCard().clickable(onClick = onClick).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(38.dp).background(colors.primary.copy(0.10f), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            Icon(if (repo.isPrivate) Icons.Rounded.Lock else Icons.Rounded.FolderOpen, null, Modifier.size(21.dp), tint = colors.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(repo.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (repo.description.isNotBlank()) Text(repo.description, fontSize = 12.sp, color = colors.onSurfaceVariant, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (repo.language.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(langColor(repo.language))); Text(repo.language, fontSize = 11.sp, color = colors.onSurfaceVariant, fontWeight = FontWeight.Medium) }
                if (repo.stars > 0) Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Star, null, Modifier.size(13.dp), tint = colors.onSurfaceVariant); Text("${repo.stars}", fontSize = 11.sp, color = colors.onSurfaceVariant, fontFamily = FontFamily.Monospace) }
                if (repo.forks > 0) Text("\u2491 ${repo.forks}", fontSize = 11.sp, color = colors.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                if (repo.isFork) Text("fork", fontSize = 10.sp, color = colors.onSurfaceVariant)
            }
        }
    }
}

internal fun langColor(lang: String): Color = when (lang.lowercase()) { "kotlin" -> Color(0xFFA97BFF); "java" -> Color(0xFFB07219); "python" -> Color(0xFF3572A5); "javascript" -> Color(0xFFF1E05A); "typescript" -> Color(0xFF3178C6); "c" -> Color(0xFF555555); "c++" -> Color(0xFFF34B7D); "swift" -> Color(0xFFFFAC45); "go" -> Color(0xFF00ADD8); "rust" -> Color(0xFFDEA584); "dart" -> Color(0xFF00B4AB); "ruby" -> Color(0xFF701516); "php" -> Color(0xFF4F5D95); "c#" -> Color(0xFF178600); "shell" -> Color(0xFF89E051); "html" -> Color(0xFFE34C26); "css" -> Color(0xFF563D7C); "vue" -> Color(0xFF41B883); else -> Color(0xFF8E8E93) }

internal fun ghFmtSize(b: Long): String = when { b < 1024 -> "$b B"; b < 1024L * 1024 -> "%.1f KB".format(b / 1024.0); else -> "%.1f MB".format(b / (1024.0 * 1024)) }

// ═══════════════════════════════════
// GitHub Actions Tab
// ═══════════════════════════════════
