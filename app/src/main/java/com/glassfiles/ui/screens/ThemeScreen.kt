package com.glassfiles.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.*
import com.glassfiles.ui.theme.*

@Composable
fun ThemeScreen(settings: AppSettings, onBack: () -> Unit) {

    Column(Modifier.fillMaxSize().background(SurfaceLight).verticalScroll(rememberScrollState())) {
        // Top bar
        Row(Modifier.fillMaxWidth().background(SurfaceWhite).padding(top = 44.dp, start = 4.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = Blue) }
            Text(Strings.themeCustomize, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 20.sp)
        }

        Spacer(Modifier.height(16.dp))

        // ── Preview card ──
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).clip(RoundedCornerShape(20.dp))
            .background(SurfaceWhite).border(1.dp, SeparatorColor, RoundedCornerShape(20.dp)).padding(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(40.dp).background(Blue.copy(0.1f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Folder, null, Modifier.size(22.dp), tint = Blue)
                    }
                    Column {
                        Text(Strings.previewTitle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(Strings.previewSubtitle, fontSize = 12.sp, color = TextSecondary)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(8.dp)).background(Blue), contentAlignment = Alignment.Center) {
                        Text(Strings.previewButton, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(8.dp)).background(Blue.copy(0.1f)), contentAlignment = Alignment.Center) {
                        Text(Strings.cancel, color = Blue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Theme mode ──
        SectionTitle(Strings.themeMode)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppThemeMode.entries.forEach { mode ->
                val sel = settings.themeMode == mode
                val icon = when (mode) {
                    AppThemeMode.LIGHT -> Icons.Rounded.LightMode
                    AppThemeMode.DARK -> Icons.Rounded.DarkMode
                    AppThemeMode.AMOLED -> Icons.Rounded.Contrast
                    AppThemeMode.SYSTEM -> Icons.Rounded.SettingsBrightness
                }
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (sel) Blue.copy(0.1f) else SurfaceWhite)
                    .border(1.5.dp, if (sel) Blue else SeparatorColor, RoundedCornerShape(12.dp))
                    .clickable { settings.setTheme(mode) }
                    .padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(icon, null, Modifier.size(24.dp), tint = if (sel) Blue else TextSecondary)
                    Text(mode.label, fontSize = 11.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (sel) Blue else TextSecondary)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Accent color ──
        SectionTitle(Strings.accentColorLabel)
        LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(AccentColor.entries.toList()) { accent ->
                val sel = settings.accentColor == accent
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { settings.setAccentColor(accent) }) {
                    Box(Modifier.size(44.dp).clip(CircleShape)
                        .background(accent.color)
                        .then(if (sel) Modifier.border(3.dp, TextPrimary, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center) {
                        if (sel) Icon(Icons.Rounded.Check, null, Modifier.size(22.dp), tint = Color.White)
                    }
                    Text(accent.label, fontSize = 10.sp, color = if (sel) TextPrimary else TextSecondary, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Folder icon style ──
        SectionTitle(Strings.folderStyle)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FolderIconStyle.entries.forEach { style ->
                val sel = settings.folderIconStyle == style
                val icon = when (style) {
                    FolderIconStyle.DEFAULT -> Icons.Rounded.Folder
                    FolderIconStyle.ROUNDED -> Icons.Rounded.FolderOpen
                    FolderIconStyle.SHARP -> Icons.Rounded.FolderCopy
                    FolderIconStyle.MINIMAL -> Icons.Rounded.FolderSpecial
                }
                val shape = when (style) {
                    FolderIconStyle.DEFAULT -> RoundedCornerShape(10.dp)
                    FolderIconStyle.ROUNDED -> RoundedCornerShape(16.dp)
                    FolderIconStyle.SHARP -> RoundedCornerShape(4.dp)
                    FolderIconStyle.MINIMAL -> RoundedCornerShape(10.dp)
                }
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (sel) Blue.copy(0.1f) else SurfaceWhite)
                    .border(1.5.dp, if (sel) Blue else SeparatorColor, RoundedCornerShape(12.dp))
                    .clickable { settings.setFolderIconStyle(style) }
                    .padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(36.dp).background(Blue.copy(0.1f), shape), contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(22.dp), tint = Blue)
                    }
                    Text(style.label, fontSize = 11.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (sel) Blue else TextSecondary)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Default view ──
        SectionTitle(Strings.defaultViewLabel)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DefaultView.entries.forEach { view ->
                val sel = settings.defaultView == view
                val icon = if (view == DefaultView.GRID) Icons.Rounded.GridView else Icons.Rounded.ViewList
                Row(Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (sel) Blue.copy(0.1f) else SurfaceWhite)
                    .border(1.5.dp, if (sel) Blue else SeparatorColor, RoundedCornerShape(10.dp))
                    .clickable { settings.changeDefaultView(view) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, Modifier.size(20.dp), tint = if (sel) Blue else TextSecondary)
                    Text(view.label, fontSize = 13.sp, color = if (sel) Blue else TextSecondary, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Font size ──
        SectionTitle("${Strings.fontSize}: ${settings.fileFontSize}sp")
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("A", fontSize = 12.sp, color = TextSecondary)
            Slider(
                value = settings.fileFontSize.toFloat(),
                onValueChange = { settings.changeFileFontSize(it.toInt()) },
                valueRange = 12f..20f,
                steps = 7,
                colors = SliderDefaults.colors(thumbColor = Blue, activeTrackColor = Blue),
                modifier = Modifier.weight(1f)
            )
            Text("A", fontSize = 20.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}
