package com.glassfiles.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.github.GHCompareResult
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.launch

@Composable
internal fun CompareCommitsScreen(
    repoOwner: String,
    repoName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var baseBranch by remember { mutableStateOf("") }
    var headBranch by remember { mutableStateOf("") }
    var compareResult by remember { mutableStateOf<GHCompareResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var branches by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        branches = GitHubManager.getBranches(context, repoOwner, repoName)
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar(
            title = "Compare",
            subtitle = "$repoOwner/$repoName",
            onBack = onBack
        )

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Base branch selector
            Text("Base branch", fontSize = 12.sp, color = TextSecondary)
            BranchSelectorDropdown(
                branches = branches,
                selected = baseBranch,
                onSelect = { baseBranch = it },
                placeholder = "Select base branch"
            )

            // Head branch selector
            Text("Compare branch", fontSize = 12.sp, color = TextSecondary)
            BranchSelectorDropdown(
                branches = branches,
                selected = headBranch,
                onSelect = { headBranch = it },
                placeholder = "Select compare branch"
            )

            Button(
                onClick = {
                    if (baseBranch.isBlank() || headBranch.isBlank() || baseBranch == headBranch) return@Button
                    loading = true
                    scope.launch {
                        compareResult = GitHubManager.compareCommits(context, repoOwner, repoName, baseBranch, headBranch)
                        loading = false
                    }
                },
                enabled = baseBranch.isNotBlank() && headBranch.isNotBlank() && baseBranch != headBranch && !loading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Compare")
                }
            }
        }

        if (compareResult != null) {
            val result = compareResult!!
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${result.aheadBy} ahead", fontSize = 14.sp, color = Color(0xFF34C759), fontWeight = FontWeight.Medium)
                    Text("${result.behindBy} behind", fontSize = 14.sp, color = Color(0xFFFF3B30), fontWeight = FontWeight.Medium)
                    Text("${result.totalCommits} commits", fontSize = 13.sp, color = TextSecondary)
                }
                Spacer(Modifier.height(12.dp))

                if (result.files.isEmpty()) {
                    Text("No file changes", fontSize = 14.sp, color = TextTertiary)
                } else {
                    Text("${result.files.size} files changed", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(result.files) { file ->
                            val statusColor = when (file.status) {
                                "added" -> Color(0xFF34C759)
                                "removed" -> Color(0xFFFF3B30)
                                "modified" -> Color(0xFFFF9500)
                                "renamed" -> Color(0xFF5856D6)
                                else -> TextSecondary
                            }
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceWhite).padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(statusColor))
                                    Text(file.filename, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(file.status.replaceFirstChar { it.uppercase() }, fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.Medium)
                                    Text("+${file.additions}", fontSize = 11.sp, color = Color(0xFF34C759))
                                    Text("-${file.deletions}", fontSize = 11.sp, color = Color(0xFFFF3B30))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BranchSelectorDropdown(
    branches: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    placeholder: String
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(placeholder) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Blue,
                unfocusedBorderColor = SeparatorColor
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(SurfaceWhite)
        ) {
            branches.forEach { branch ->
                DropdownMenuItem(
                    text = { Text(branch, fontSize = 13.sp, color = TextPrimary) },
                    onClick = {
                        onSelect(branch)
                        expanded = false
                    }
                )
            }
        }
    }
}
