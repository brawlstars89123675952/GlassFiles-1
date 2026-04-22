package com.glassfiles.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class DiffLine {
    data class Same(val lineNum1: Int, val lineNum2: Int, val text: String) : DiffLine()
    data class Added(val lineNum: Int, val text: String) : DiffLine()
    data class Removed(val lineNum: Int, val text: String) : DiffLine()
}

@Composable
fun DiffScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var file1Path by remember { mutableStateOf("") }
    var file2Path by remember { mutableStateOf("") }
    var diffLines by remember { mutableStateOf<List<DiffLine>>(emptyList()) }
    var compared by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf(Triple(0, 0, 0)) } // added, removed, same

    val pickFile1 = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u ->
            try {
                val input = context.contentResolver.openInputStream(u)
                val tmpFile = File(context.cacheDir, "diff_file1_${System.currentTimeMillis()}.txt")
                input?.use { inp -> tmpFile.outputStream().use { out -> inp.copyTo(out) } }
                file1Path = tmpFile.absolutePath
            } catch (e: Exception) { Toast.makeText(context, "${Strings.error}: ${e.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    val pickFile2 = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u ->
            try {
                val input = context.contentResolver.openInputStream(u)
                val tmpFile = File(context.cacheDir, "diff_file2_${System.currentTimeMillis()}.txt")
                input?.use { inp -> tmpFile.outputStream().use { out -> inp.copyTo(out) } }
                file2Path = tmpFile.absolutePath
            } catch (e: Exception) { Toast.makeText(context, "${Strings.error}: ${e.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().background(SurfaceWhite).padding(top = 44.dp, start = 4.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = Blue) }
            Text(Strings.fileDiff, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 20.sp)
        }

        // File selectors
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // File 1
            FileSelector(
                label = Strings.file1,
                path = file1Path,
                onClick = { pickFile1.launch(arrayOf("text/*", "application/json", "application/xml")) }
            )
            // File 2
            FileSelector(
                label = Strings.file2,
                path = file2Path,
                onClick = { pickFile2.launch(arrayOf("text/*", "application/json", "application/xml")) }
            )

            // Compare button
            Button(
                onClick = {
                    if (file1Path.isNotBlank() && file2Path.isNotBlank()) {
                        loading = true; compared = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { computeDiff(File(file1Path), File(file2Path)) }
                            diffLines = result
                            stats = Triple(
                                result.count { it is DiffLine.Added },
                                result.count { it is DiffLine.Removed },
                                result.count { it is DiffLine.Same }
                            )
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
                enabled = file1Path.isNotBlank() && file2Path.isNotBlank() && !loading
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else {
                    Icon(Icons.Rounded.Compare, null, Modifier.size(18.dp), tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text(Strings.compareFiles, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }

            // Stats
            if (compared && !loading) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatChip("+${stats.first}", Color(0xFF4CAF50), "${Strings.added}")
                    StatChip("-${stats.second}", Color(0xFFF44336), "${Strings.removed}")
                    if (diffLines.isEmpty() || (stats.first == 0 && stats.second == 0)) {
                        Text(Strings.identical, fontSize = 13.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // Diff output
        if (compared && !loading) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                itemsIndexed(diffLines) { _, line ->
                    DiffLineRow(line)
                }
            }
        }
    }
}

@Composable
private fun FileSelector(label: String, path: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceWhite)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Rounded.InsertDriveFile, null, Modifier.size(20.dp), tint = Blue)
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, color = TextSecondary)
            if (path.isNotBlank()) {
                Text(File(path).name, fontSize = 14.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Text(if (label == Strings.file1) Strings.selectFile1 else Strings.selectFile2,
                    fontSize = 14.sp, color = TextTertiary)
            }
        }
        Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp), tint = TextTertiary)
    }
}

@Composable
private fun StatChip(text: String, color: Color, label: String) {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(0.1f)).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = FontFamily.Monospace)
        Text(label, fontSize = 11.sp, color = color.copy(0.7f))
    }
}

@Composable
private fun DiffLineRow(line: DiffLine) {
    val (bgColor, prefix, textColor, text, lineNum) = when (line) {
        is DiffLine.Added -> DiffRowData(Color(0xFF4CAF50).copy(0.08f), "+", Color(0xFF2E7D32), line.text, "${line.lineNum}")
        is DiffLine.Removed -> DiffRowData(Color(0xFFF44336).copy(0.08f), "-", Color(0xFFC62828), line.text, "${line.lineNum}")
        is DiffLine.Same -> DiffRowData(Color.Transparent, " ", TextSecondary, line.text, "${line.lineNum1}")
        else -> DiffRowData(Color.Transparent, " ", TextSecondary, "", "")
    }

    Row(
        Modifier.fillMaxWidth().background(bgColor).padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(lineNum, fontSize = 11.sp, color = TextTertiary, fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(32.dp))
        Text(prefix, fontSize = 12.sp, color = textColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(14.dp))
        Text(text, fontSize = 12.sp, color = textColor, fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f))
    }
}

private data class DiffRowData(val bg: Color, val prefix: String, val textColor: Color, val text: String, val lineNum: String)

// ═══════════════════════════════════
// Simple LCS-based diff algorithm
// ═══════════════════════════════════

private fun computeDiff(file1: File, file2: File): List<DiffLine> {
    val lines1 = try { file1.readLines() } catch (_: Exception) { emptyList() }
    val lines2 = try { file2.readLines() } catch (_: Exception) { emptyList() }

    val n = lines1.size; val m = lines2.size
    // LCS table
    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in 1..n) for (j in 1..m) {
        dp[i][j] = if (lines1[i - 1] == lines2[j - 1]) dp[i - 1][j - 1] + 1
        else maxOf(dp[i - 1][j], dp[i][j - 1])
    }

    // Backtrack
    val result = mutableListOf<DiffLine>()
    var i = n; var j = m
    while (i > 0 || j > 0) {
        when {
            i > 0 && j > 0 && lines1[i - 1] == lines2[j - 1] -> {
                result.add(0, DiffLine.Same(i, j, lines1[i - 1]))
                i--; j--
            }
            j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j]) -> {
                result.add(0, DiffLine.Added(j, lines2[j - 1]))
                j--
            }
            else -> {
                result.add(0, DiffLine.Removed(i, lines1[i - 1]))
                i--
            }
        }
    }
    return result
}
