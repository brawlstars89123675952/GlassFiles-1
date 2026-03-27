package com.glassfiles.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.*
import com.glassfiles.data.ai.GeminiKeyStore
import com.glassfiles.ui.theme.*

@Composable
fun SettingsScreen(settings: AppSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        // Top bar
        Row(Modifier.fillMaxWidth().background(SurfaceWhite).padding(top = 44.dp, start = 4.dp, end = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = Blue) }
            Text(Strings.settings, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 20.sp)
        }

        Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // ═══════════════════════════════════
            // Внешний вид
            // ═══════════════════════════════════
            SettingsSection(Strings.appearance, Icons.Rounded.Palette) {
                SettingsLabel(Strings.theme)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppThemeMode.entries.forEach { mode ->
                        val selected = settings.themeMode == mode
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Blue.copy(0.15f) else Color.Transparent)
                            .border(1.dp, if (selected) Blue.copy(0.4f) else SeparatorColor, RoundedCornerShape(10.dp))
                            .clickable { settings.changeTheme(mode) }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center) {
                            Text(mode.label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) Blue else TextSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                SettingsLabel(Strings.language)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        val selected = settings.appLanguage == lang
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Blue.copy(0.15f) else Color.Transparent)
                            .border(1.dp, if (selected) Blue.copy(0.4f) else SeparatorColor, RoundedCornerShape(10.dp))
                            .clickable { settings.changeLanguage(lang) }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center) {
                            Text("${lang.flag} ${lang.label}", fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) Blue else TextSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                SettingsLabel("${Strings.fileFontSize}: ${settings.fileFontSize}sp")
                Slider(settings.fileFontSize.toFloat(), { settings.changeFileFontSize(it.toInt()) },
                    valueRange = 12f..20f, steps = 7, colors = SliderDefaults.colors(thumbColor = Blue, activeTrackColor = Blue))
            }

            // ═══════════════════════════════════
            // Файловый менеджер
            // ═══════════════════════════════════
            SettingsSection(Strings.fileManager, Icons.Rounded.Folder) {
                SettingsToggle(Strings.showHiddenFiles, "Show files starting with dot",
                    settings.showHiddenFiles) { settings.changeShowHidden(it) }

                SettingsToggle(Strings.confirmDelete, "Ask before deleting",
                    settings.confirmDelete) { settings.changeConfirmDelete(it) }

                HorizontalDivider(color = SeparatorColor)

                SettingsLabel(Strings.defaultView)
                SettingsChips(DefaultView.entries.map { it.label }, DefaultView.entries.indexOf(settings.defaultView)) {
                    settings.changeDefaultView(DefaultView.entries[it])
                }

                SettingsLabel(Strings.defaultSort)
                SettingsChips(DefaultSort.entries.map { it.label }, DefaultSort.entries.indexOf(settings.defaultSort)) {
                    settings.changeDefaultSort(DefaultSort.entries[it])
                }

                SettingsLabel("Start folder")
                SettingsChips(StartFolder.entries.map { it.label }, StartFolder.entries.indexOf(settings.startFolder)) {
                    settings.changeStartFolder(StartFolder.entries[it])
                }
            }

            // ═══════════════════════════════════
            // AI Чат
            // ═══════════════════════════════════
            SettingsSection("AI Чат", Icons.Rounded.AutoAwesome) {
                var geminiKey by remember { mutableStateOf(GeminiKeyStore.getKey(context)) }
                var orKey by remember { mutableStateOf(GeminiKeyStore.getOpenRouterKey(context)) }
                var proxyUrl by remember { mutableStateOf(GeminiKeyStore.getProxy(context)) }

                SettingsLabel("Gemini API Key")
                SettingsTextField(geminiKey, "AIzaSy...") {
                    geminiKey = it; GeminiKeyStore.saveKey(context, it)
                }

                SettingsLabel("OpenRouter API Key")
                SettingsTextField(orKey, "sk-or-v1-...") {
                    orKey = it; GeminiKeyStore.saveOpenRouterKey(context, it)
                }

                SettingsLabel("Proxy URL (Gemini)")
                SettingsTextField(proxyUrl, "https://proxy.example.com/v1beta/models") {
                    proxyUrl = it; GeminiKeyStore.saveProxy(context, it)
                }

                HorizontalDivider(color = SeparatorColor)

                SettingsLabel("AI system prompt")
                OutlinedTextField(settings.aiSystemPrompt, { settings.changeAiSystemPrompt(it) },
                    placeholder = { Text("AI instructions (optional)", color = TextTertiary, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                    maxLines = 4, shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Blue, unfocusedBorderColor = SeparatorColor,
                        cursorColor = Blue
                    ))

                SettingsLabel("AI response language")
                SettingsChips(listOf("Русский", "English", "Auto"), listOf("Русский", "English", "Auto").indexOf(settings.aiLanguage)) {
                    settings.changeAiLanguage(listOf("Русский", "English", "Auto")[it])
                }
            }

            // ═══════════════════════════════════
            // О приложении
            // ═══════════════════════════════════
            SettingsSection(Strings.aboutApp, Icons.Rounded.Info) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(Strings.version, color = TextPrimary, fontSize = 15.sp)
                    Text("2.0-pre1", color = TextSecondary, fontSize = 15.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Developer", color = TextPrimary, fontSize = 15.sp)
                    Text("stanislavdev987", color = TextSecondary, fontSize = 15.sp)
                }

                HorizontalDivider(color = SeparatorColor)

                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(Red.copy(0.08f)).clickable {
                        settings.resetAll()
                        Toast.makeText(context, Strings.resetAll, Toast.LENGTH_SHORT).show()
                    }.padding(14.dp), contentAlignment = Alignment.Center) {
                    Text(Strings.resetAll, color = Red, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

// ═══════════════════════════════════
// Компоненты
// ═══════════════════════════════════

@Composable
private fun SettingsSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceWhite).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(20.dp), tint = Blue)
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextPrimary)
        }
        content()
    }
}

@Composable
private fun SettingsLabel(text: String) {
    Text(text, fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
}

@Composable
private fun SettingsToggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = TextSecondary)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Blue, checkedThumbColor = Color.White))
    }
}

@Composable
private fun SettingsChips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label ->
            val sel = i == selected
            Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                .background(if (sel) Blue.copy(0.12f) else SurfaceLight)
                .border(1.dp, if (sel) Blue.copy(0.3f) else Color.Transparent, RoundedCornerShape(8.dp))
                .clickable { onSelect(i) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center) {
                Text(label, fontSize = 12.sp, color = if (sel) Blue else TextSecondary,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun SettingsTextField(value: String, placeholder: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, placeholder = { Text(placeholder, color = TextTertiary, fontSize = 13.sp) },
        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
            focusedBorderColor = Blue, unfocusedBorderColor = SeparatorColor,
            cursorColor = Blue
        ), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp))
}
