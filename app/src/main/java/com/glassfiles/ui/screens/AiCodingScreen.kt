package com.glassfiles.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.data.ai.AiCapability
import com.glassfiles.data.ai.AiModelCatalog
import com.glassfiles.data.ai.AiProviderType

@Composable
fun AiCodingScreen(onBack: () -> Unit, onOpenChat: () -> Unit = {}) {
    val codingModels = remember {
        AiModelCatalog.allModels.filter {
            AiCapability.CODING in it.capabilities
        }.groupBy { it.providerType }
    }
    var prompt by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(Color(0xFF09090B))) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF111113)).padding(top = 48.dp, start = 6.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Color(0xFFE4E4E7))
            }
            Column(Modifier.weight(1f)) {
                Text(Strings.aiCodingTitle, color = Color(0xFFE4E4E7), fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text(Strings.aiCodingSubtitle, color = Color(0xFF71717A), fontSize = 12.sp)
            }
            IconButton(onClick = onOpenChat) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = Color(0xFF22C55E))
            }
            IconButton(onClick = {}) {
                Icon(Icons.Rounded.Settings, null, tint = Color(0xFF71717A))
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF111113)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(Strings.aiWorkspace, color = Color(0xFFE4E4E7), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(Strings.aiWorkspaceSubtitle, color = Color(0xFF71717A), fontSize = 13.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Strings.explainCodeAction, Strings.fixBugAction, Strings.generatePatchAction, Strings.writeScriptAction, Strings.refactorFileAction, Strings.createWorkflowAction).forEach { chip ->
                            Box(
                                Modifier.clip(RoundedCornerShape(9.dp)).background(Color(0xFF18181B)).clickable { prompt = chip }.padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Text(chip, color = Color(0xFFE4E4E7), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            codingModels.forEach { (provider, models) ->
                item {
                    Text(
                        when (provider) {
                            AiProviderType.GEMINI -> "Gemini Coding"
                            AiProviderType.QWEN -> "Qwen Coding"
                            AiProviderType.OPENAI -> "ChatGPT Coding"
                            AiProviderType.XAI -> "Grok Coding"
                            AiProviderType.KIMI -> "Kimi Coding"
                        },
                        color = Color(0xFF22C55E),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                items(models) { model ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF111113)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF18181B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Code, null, tint = Color(0xFF22C55E), modifier = Modifier.size(18.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(model.label, color = Color(0xFFE4E4E7), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(model.description, color = Color(0xFF71717A), fontSize = 11.sp)
                        }
                        if (AiCapability.FILES in model.capabilities) {
                            TinyBadge("Files")
                        }
                        if (AiCapability.REASONING in model.capabilities) {
                            TinyBadge("Reasoning")
                        }
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().background(Color(0xFF111113)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF18181B)).padding(horizontal = 14.dp, vertical = 12.dp),
                textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFFE4E4E7), fontSize = 15.sp),
                decorationBox = { inner ->
                    Box {
                        if (prompt.isBlank()) Text(Strings.describeCodingTask, color = Color(0xFF52525B), fontSize = 15.sp)
                        inner()
                    }
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF18181B)).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF22C55E)))
                        Text(Strings.agentStylePrompt, color = Color(0xFF71717A), fontSize = 12.sp)
                    }
                }
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF22C55E)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.Black)
                }
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF18181B)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Terminal, null, tint = Color(0xFF71717A))
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun TinyBadge(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF22C55E).copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 4.dp)
    ) {
        Text(text, color = Color(0xFF22C55E), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}
