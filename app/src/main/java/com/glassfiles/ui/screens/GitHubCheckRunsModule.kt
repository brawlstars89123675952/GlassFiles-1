package com.glassfiles.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.glassfiles.data.github.GHCheckRun
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.components.AiModuleHairline
import com.glassfiles.ui.components.AiModuleSpinner
import com.glassfiles.ui.components.AiModuleText as Text
import com.glassfiles.ui.components.aiModuleStatusBadge
import com.glassfiles.ui.components.aiModuleStatusLabel
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.ui.theme.JetBrainsMono

@Composable
internal fun CheckRunsScreen(
    repoOwner: String,
    repoName: String,
    ref: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var checkRuns by remember { mutableStateOf<List<GHCheckRun>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(repoOwner, repoName, ref) {
        checkRuns = GitHubManager.getPullRequestCheckRuns(context, repoOwner, repoName, ref)
        loading = false
    }

    GitHubScreenFrame(
        title = "> check runs",
        onBack = onBack,
        subtitle = "$repoOwner/$repoName · $ref",
    ) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AiModuleSpinner(label = "loading checks…")
            }
            checkRuns.isEmpty() -> GitHubMonoEmpty(
                title = "no check runs",
                subtitle = "no CI activity reported for this ref",
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
            ) {
                items(checkRuns) { run ->
                    CheckRunRow(run)
                    AiModuleHairline()
                }
            }
        }
    }
}

@Composable
private fun CheckRunRow(run: GHCheckRun) {
    val palette = AiModuleTheme.colors
    val badge = aiModuleStatusBadge(run.status, run.conclusion, palette)
    val label = aiModuleStatusLabel(run.status, run.conclusion)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = badge.glyph,
                color = badge.color,
                fontFamily = JetBrainsMono,
                fontSize = 14.sp,
                modifier = Modifier.width(18.dp),
            )
            Text(
                text = run.name,
                color = palette.textPrimary,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                color = badge.color,
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
            )
        }
        if (run.outputTitle.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = run.outputTitle,
                color = palette.textSecondary,
                fontFamily = JetBrainsMono,
                fontSize = 12.sp,
                lineHeight = 1.3.em,
                modifier = Modifier.padding(start = 18.dp),
            )
        }
        if (run.outputSummary.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = run.outputSummary,
                color = palette.textMuted,
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                lineHeight = 1.3.em,
                maxLines = 3,
                modifier = Modifier.padding(start = 18.dp),
            )
        }
    }
}
