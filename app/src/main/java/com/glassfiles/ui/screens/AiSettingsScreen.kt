package com.glassfiles.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.data.ai.AiSettingsStore
import com.glassfiles.data.ai.AiSyntaxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * AI-module settings: syntax theme picker, font sizes, auto-save toggle, and
 * a "clear cache" action that wipes the on-disk previews.
 *
 * Predictive-back gesture is honoured via [BackHandler].
 */
@Composable
fun AiSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onBack)

    var syntaxTheme by remember { mutableStateOf(AiSettingsStore.getSyntaxTheme(context)) }
    var codeFontSize by remember { mutableIntStateOf(AiSettingsStore.getCodeFontSize(context)) }
    var chatFontSize by remember { mutableIntStateOf(AiSettingsStore.getChatFontSize(context)) }
    var autoSave by remember { mutableStateOf(AiSettingsStore.isAutoSaveGallery(context)) }
    var streamScroll by remember { mutableStateOf(AiSettingsStore.isStreamAutoScroll(context)) }
    var cacheCleared by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(colors.surface).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = colors.onSurface)
            }
            Text(
                Strings.aiSettings,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ─── Syntax theme ─────────────────────────────────────────────
            item {
                SectionHeader(Strings.aiSettingsSyntaxTheme)
                Spacer(Modifier.height(4.dp))
                Text(
                    Strings.aiSettingsSyntaxThemeHint,
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                SectionHeader(Strings.aiSettingsCodeFontSize)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = codeFontSize.toFloat(),
                        onValueChange = {
                            codeFontSize = it.toInt().coerceIn(10, 18)
                            AiSettingsStore.setCodeFontSize(context, codeFontSize)
                        },
                        valueRange = 10f..18f,
                        steps = 7,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = colors.primary,
                            activeTrackColor = colors.primary,
                            inactiveTrackColor = colors.outlineVariant,
                        ),
                    )
                    Text(
                        "$codeFontSize sp",
                        modifier = Modifier.padding(start = 12.dp).width(48.dp),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.onSurface,
                    )
                }
            }
            item {
                SectionHeader(Strings.aiSettingsChatFontSize)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = chatFontSize.toFloat(),
                        onValueChange = {
                            chatFontSize = it.toInt().coerceIn(12, 20)
                            AiSettingsStore.setChatFontSize(context, chatFontSize)
                        },
                        valueRange = 12f..20f,
                        steps = 7,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = colors.primary,
                            activeTrackColor = colors.primary,
                            inactiveTrackColor = colors.outlineVariant,
                        ),
                    )
                    Text(
                        "$chatFontSize sp",
                        modifier = Modifier.padding(start = 12.dp).width(48.dp),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.onSurface,
                    )
                }
            }

            // ─── Toggles ──────────────────────────────────────────────────
            item {
                ToggleRow(
                    title = Strings.aiSettingsAutoSave,
                    subtitle = Strings.aiSettingsAutoSaveHint,
                    checked = autoSave,
                    onChange = {
                        autoSave = it
                        AiSettingsStore.setAutoSaveGallery(context, it)
                    },
                )
            }
            item {
                ToggleRow(
                    title = Strings.aiSettingsStreamScroll,
                    subtitle = null,
                    checked = streamScroll,
                    onChange = {
                        streamScroll = it
                        AiSettingsStore.setStreamAutoScroll(context, it)
                    },
                )
            }

            // ─── Cache ─────────────────────────────────────────────────────
            item {
                SectionHeader(Strings.aiSettingsClearCache)
                Spacer(Modifier.height(4.dp))
                Text(
                    Strings.aiSettingsClearCacheHint,
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.errorContainer.copy(alpha = 0.5f))
                        .clickable {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    listOf("ai_images", "ai_videos").forEach { dir ->
                                        File(context.cacheDir, dir).deleteRecursively()
                                    }
                                }
                                cacheCleared = true
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.DeleteSweep,
                        null,
                        Modifier.size(20.dp),
                        tint = colors.onErrorContainer,
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        if (cacheCleared) Strings.aiSettingsClearCacheDone
                        else Strings.aiSettingsClearCache,
                        color = colors.onErrorContainer,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    val colors = MaterialTheme.colorScheme
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = colors.onSurfaceVariant,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
private fun SyntaxThemeCard(
    theme: AiSyntaxTheme,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.bgColor)
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(20.dp).clip(CircleShape).background(
                if (selected) colors.primary else theme.headerColor,
            ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    null,
                    Modifier.size(14.dp),
                    tint = colors.onPrimary,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                theme.displayName,
                color = theme.plain,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("fun", color = theme.keyword, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text("\"hi\"", color = theme.string, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text("42", color = theme.number, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text("// note", color = theme.comment, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.5f))
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = colors.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    color = colors.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
                uncheckedThumbColor = colors.onSurfaceVariant,
                uncheckedTrackColor = colors.surfaceVariant,
            ),
        )
    }
}
