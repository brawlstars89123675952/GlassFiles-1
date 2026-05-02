package com.glassfiles.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.data.ai.AiSettingsStore
import com.glassfiles.data.ai.AiSyntaxTheme
import com.glassfiles.ui.components.AiModuleCheckRow
import com.glassfiles.ui.components.AiModuleHairline
import com.glassfiles.ui.components.AiModulePillButton
import com.glassfiles.ui.components.AiModuleScreenScaffold
import com.glassfiles.ui.components.AiModuleSectionLabel
import com.glassfiles.ui.components.AiModuleText
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.ui.theme.JetBrainsMono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * AI-module settings: syntax theme picker, font sizes, auto-save toggle,
 * stream-scroll toggle, and a "clear cache" action that wipes the on-disk
 * previews. Rendered inside the AI terminal palette so it lines up with
 * the rest of the AI module.
 */
@Composable
fun AiSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onBack)

    var syntaxTheme by remember { mutableStateOf(AiSettingsStore.getSyntaxTheme(context)) }
    var codeFontSize by remember { mutableIntStateOf(AiSettingsStore.getCodeFontSize(context)) }
    var chatFontSize by remember { mutableIntStateOf(AiSettingsStore.getChatFontSize(context)) }
    var autoSave by remember { mutableStateOf(AiSettingsStore.isAutoSaveGallery(context)) }
    var streamScroll by remember { mutableStateOf(AiSettingsStore.isStreamAutoScroll(context)) }
    var cacheCleared by remember { mutableStateOf(false) }

    AiModuleScreenScaffold(
        title = Strings.aiSettings,
        onBack = onBack,
        subtitle = "ai.cfg",
    ) {
        val colors = AiModuleTheme.colors
        LazyColumn(
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ─── Syntax theme ─────────────────────────────────────────────
            item {
                Column {
                    SettingsSectionHeader(Strings.aiSettingsSyntaxTheme)
                    AiModuleText(
                        "// " + Strings.aiSettingsSyntaxThemeHint,
                        fontSize = 11.sp,
                        fontFamily = JetBrainsMono,
                        color = colors.textMuted,
                        lineHeight = 1.4.em,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
            item {
                Column(
                    Modifier.padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AiSyntaxTheme.values().forEach { theme ->
                        SyntaxThemeCard(
                            theme = theme,
                            selected = theme == syntaxTheme,
                            onSelect = {
                                syntaxTheme = theme
                                AiSettingsStore.setSyntaxTheme(context, theme)
                            },
                        )
                    }
                }
            }

            // ─── Font sizes ────────────────────────────────────────────────
            item {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    SettingsSectionHeader(Strings.aiSettingsCodeFontSize, withPadding = false)
                    TerminalStepper(
                        value = codeFontSize,
                        min = 10,
                        max = 18,
                        onChange = {
                            codeFontSize = it
                            AiSettingsStore.setCodeFontSize(context, codeFontSize)
                        },
                    )
                }
            }
            item {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    SettingsSectionHeader(Strings.aiSettingsChatFontSize, withPadding = false)
                    TerminalStepper(
                        value = chatFontSize,
                        min = 12,
                        max = 20,
                        onChange = {
                            chatFontSize = it
                            AiSettingsStore.setChatFontSize(context, chatFontSize)
                        },
                    )
                }
            }

            // ─── Toggles ──────────────────────────────────────────────────
            item {
                Column {
                    AiModuleCheckRow(
                        label = Strings.aiSettingsAutoSave,
                        description = Strings.aiSettingsAutoSaveHint,
                        checked = autoSave,
                        onToggle = {
                            autoSave = !autoSave
                            AiSettingsStore.setAutoSaveGallery(context, autoSave)
                        },
                    )
                    AiModuleCheckRow(
                        label = Strings.aiSettingsStreamScroll,
                        checked = streamScroll,
                        onToggle = {
                            streamScroll = !streamScroll
                            AiSettingsStore.setStreamAutoScroll(context, streamScroll)
                        },
                    )
                }
            }

            // ─── Cache ─────────────────────────────────────────────────────
            item {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    SettingsSectionHeader(Strings.aiSettingsClearCache, withPadding = false)
                    AiModuleText(
                        "// " + Strings.aiSettingsClearCacheHint,
                        fontSize = 11.sp,
                        fontFamily = JetBrainsMono,
                        color = colors.textMuted,
                        lineHeight = 1.4.em,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    AiModulePillButton(
                        label = if (cacheCleared) {
                            Strings.aiSettingsClearCacheDone.lowercase() + " ✓"
                        } else {
                            "rm -rf ai_images ai_videos"
                        },
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    listOf("ai_images", "ai_videos").forEach { dir ->
                                        File(context.cacheDir, dir).deleteRecursively()
                                    }
                                }
                                cacheCleared = true
                            }
                        },
                        destructive = true,
                        accent = false,
                        enabled = !cacheCleared,
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalStepper(
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    val colors = AiModuleTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StepperCell("-", enabled = value > min) {
            onChange((value - 1).coerceIn(min, max))
        }
        AiModuleText(
            "$value sp",
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
        )
        StepperCell("+", enabled = value < max) {
            onChange((value + 1).coerceIn(min, max))
        }
    }
}

@Composable
private fun StepperCell(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = AiModuleTheme.colors
    AiModuleText(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surface)
            .border(1.dp, if (enabled) colors.accent else colors.border, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = if (enabled) colors.accent else colors.textMuted,
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
    )
}

@Composable
private fun SettingsSectionHeader(text: String, withPadding: Boolean = true) {
    val colors = AiModuleTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = if (withPadding) 12.dp else 0.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AiModuleText(
            text = "> ",
            color = colors.accent,
            fontFamily = JetBrainsMono,
            fontSize = 12.sp,
        )
        AiModuleSectionLabel(text = text)
    }
    if (!withPadding) {
        AiModuleHairline(Modifier.padding(top = 2.dp))
    } else {
        AiModuleHairline(Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
    }
}

@Composable
private fun SyntaxThemeCard(
    theme: AiSyntaxTheme,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = AiModuleTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(theme.bgColor)
            .border(
                width = 1.dp,
                color = if (selected) colors.accent else colors.border,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AiModuleText(
            text = if (selected) "[✓]" else "[ ]",
            color = if (selected) colors.accent else theme.plain,
            fontFamily = JetBrainsMono,
            fontSize = 13.sp,
        )
        Column(Modifier.weight(1f)) {
            AiModuleText(
                theme.displayName,
                color = theme.plain,
                fontSize = 13.sp,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiModuleText("fun", color = theme.keyword, fontSize = 11.sp, fontFamily = JetBrainsMono)
                AiModuleText("\"hi\"", color = theme.string, fontSize = 11.sp, fontFamily = JetBrainsMono)
                AiModuleText("42", color = theme.number, fontSize = 11.sp, fontFamily = JetBrainsMono)
                AiModuleText("// note", color = theme.comment, fontSize = 11.sp, fontFamily = JetBrainsMono)
            }
        }
    }
}
