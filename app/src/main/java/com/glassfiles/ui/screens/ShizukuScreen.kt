package com.glassfiles.ui.screens

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.glassfiles.data.ShizukuManager
import com.glassfiles.data.Strings
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ShTab { FILES, APPS, SYSTEM, LOGS, AUTO }

@Composable
private fun shTabLabel(tab: ShTab): String = when (tab) {
    ShTab.FILES -> Strings.tools
    ShTab.APPS -> Strings.appManager
    ShTab.SYSTEM -> Strings.shSystem
    ShTab.LOGS -> Strings.shLogs
    ShTab.AUTO -> Strings.shAuto
}

@Composable
fun ShizukuScreen(onBack: () -> Unit, onBrowseRestricted: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isInstalled by remember { mutableStateOf(ShizukuManager.isShizukuInstalled(context)) }
    var isRunning by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(ShTab.FILES) }

    LaunchedEffect(Unit) {
        isRunning = ShizukuManager.isShizukuRunning()
        hasPermission = ShizukuManager.hasShizukuPermission()
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        Row(Modifier.fillMaxWidth().background(SurfaceWhite).padding(top = 44.dp, start = 4.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = Blue) }
            Text(Strings.shizuku, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 20.sp, modifier = Modifier.weight(1f))
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp))
                .background(if (hasPermission) Color(0xFF34C759) else if (isRunning) Color(0xFFFF9F0A) else Color(0xFFFF3B30)))
        }

        if (!hasPermission) {
            Box(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.Warning, null, Modifier.size(22.dp), tint = Color(0xFFFF9F0A))
                        Text(when {
                            !isInstalled -> Strings.shizukuNotInstalled
                            !isRunning -> Strings.shizukuNotRunning
                            else -> Strings.shizukuNoPermission
                        }, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    }
                    if (isRunning) {
                        Button(onClick = {
                            ShizukuManager.requestPermission(100)
                            scope.launch { kotlinx.coroutines.delay(1000); hasPermission = ShizukuManager.hasShizukuPermission() }
                        }, colors = ButtonDefaults.buttonColors(containerColor = Blue), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(Strings.shizukuRequestPerm, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            return@Column
        }

        Row(Modifier.fillMaxWidth().background(SurfaceWhite).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShTab.entries.forEach { tab ->
                val sel = selectedTab == tab
                val label = shTabLabel(tab)
                Box(Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (sel) Blue.copy(0.12f) else Color.Transparent)
                    .border(1.dp, if (sel) Blue.copy(0.3f) else SeparatorColor, RoundedCornerShape(8.dp))
                    .clickable { selectedTab = tab }
                    .padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Text(label, fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, color = if (sel) Blue else TextSecondary)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(SeparatorColor))

        when (selectedTab) {
            ShTab.FILES -> FilesContent(context, scope, onBrowseRestricted)
            ShTab.APPS -> AppsContent(context, scope)
            ShTab.SYSTEM -> SystemContent(context, scope)
            ShTab.LOGS -> LogsContent(context, scope)
            ShTab.AUTO -> AutoContent(context, scope)
        }
    }
}

// ═══ Files ═══

@Composable
private fun FilesContent(context: Context, scope: kotlinx.coroutines.CoroutineScope, onBrowse: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Label(Strings.shRestrictedDirs) }
        item { Card(Icons.Rounded.Folder, Strings.androidData, "/storage/emulated/0/Android/data", Color(0xFF007AFF)) { onBrowse("shizuku:///storage/emulated/0/Android/data") } }
        item { Card(Icons.Rounded.Folder, Strings.androidObb, "/storage/emulated/0/Android/obb", Color(0xFF5856D6)) { onBrowse("shizuku:///storage/emulated/0/Android/obb") } }
        item { Label(Strings.shSystemDirs) }
        item { Card(Icons.Rounded.Storage, "/data/local/tmp", Strings.shTempFiles, Color(0xFF8E8E93)) { onBrowse("shizuku:///data/local/tmp") } }
        item { Card(Icons.Rounded.SettingsApplications, "/system/app", Strings.shSystemApps, Color(0xFFFF9F0A)) { onBrowse("shizuku:///system/app") } }
        item { Card(Icons.Rounded.FolderSpecial, "/data/data", Strings.shAppData, Color(0xFFF44336)) { onBrowse("shizuku:///data/data") } }
        item { Label(Strings.shFileTools) }
        item {
            var show by remember { mutableStateOf(false) }
            Card(Icons.Rounded.Lock, Strings.shChangePerms, Strings.shChmodSub, Color(0xFF8E8E93)) { show = true }
            if (show) ChmodDialog(scope) { show = false }
        }
        item {
            var show by remember { mutableStateOf(false) }
            Card(Icons.Rounded.Link, Strings.shSymlink, Strings.shCreateSymlink, Color(0xFF30D158)) { show = true }
            if (show) SymlinkDialog(context, scope) { show = false }
        }
        item { Card(Icons.Rounded.ListAlt, Strings.shMountPoints, Strings.shCopyMount, Color(0xFF636366)) {
            scope.launch { val m = ShizukuManager.getMounts()
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("mounts", m))
                tst(context, true, "${Strings.shCopied} (${m.lines().size})") }
        }}
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ═══ Apps ═══

@Composable
private fun AppsContent(context: Context, scope: kotlinx.coroutines.CoroutineScope) {
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { apps = loadApps(context); loading = false } }
    val filtered = remember(apps, query) {
        val list = apps.filter { !it.isSystem }
        if (query.isNotBlank()) list.filter { it.name.contains(query, true) || it.packageName.contains(query, true) }
        else list.sortedBy { it.name.lowercase() }
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceWhite).padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (query.isEmpty()) Text(Strings.searchApps, color = TextTertiary, fontSize = 14.sp)
            BasicTextField(query, { query = it }, textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 14.sp), singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp) }
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
            items(filtered, key = { it.packageName }) { app -> AppRow(app, context, scope) }
        }
    }
}

@Composable
private fun AppRow(app: AppItem, context: Context, scope: kotlinx.coroutines.CoroutineScope) {
    var expanded by remember { mutableStateOf(false) }
    var isFrozen by remember { mutableStateOf(false) }
    var dataSize by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(app.packageName) { isFrozen = ShizukuManager.isAppFrozen(app.packageName) }

    Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (app.icon != null) { val bmp = remember(app.packageName) { app.icon.toBitmap(96, 96).asImageBitmap() }; Image(bmp, app.name, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))) }
            else Box(Modifier.size(40.dp).background(Blue.copy(0.08f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Android, null, Modifier.size(22.dp), tint = Blue) }
            Column(Modifier.weight(1f)) {
                Text(app.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(app.packageName, fontSize = 11.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                    if (dataSize != null) Text(fmtSz(dataSize!!), fontSize = 11.sp, color = Blue, fontWeight = FontWeight.Medium)
                }
            }
            if (isFrozen) Box(Modifier.background(Color(0xFF007AFF).copy(0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(Strings.frozen, fontSize = 10.sp, color = Color(0xFF007AFF), fontWeight = FontWeight.SemiBold) }
        }
        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(Strings.forceStop, Color(0xFFFF3B30), Modifier.weight(1f)) { scope.launch { tst(context, ShizukuManager.forceStop(app.packageName), Strings.forceStopped) } }
                    Chip(if (isFrozen) Strings.unfreezeApp else Strings.freezeApp, Color(0xFF007AFF), Modifier.weight(1f)) {
                        scope.launch { val ok = if (isFrozen) ShizukuManager.unfreezeApp(app.packageName) else ShizukuManager.freezeApp(app.packageName)
                            if (ok) isFrozen = !isFrozen; tst(context, ok, if (isFrozen) Strings.frozen else Strings.unfrozen) } }
                    Chip(Strings.clearCacheDone, Color(0xFF34C759), Modifier.weight(1f)) { scope.launch { tst(context, ShizukuManager.clearCache(app.packageName), Strings.clearCacheDone) } }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(Strings.shClearAll, Color(0xFFFF9F0A), Modifier.weight(1f)) { scope.launch { tst(context, ShizukuManager.clearAllData(app.packageName), Strings.shDataCleared) } }
                    Chip(Strings.delete, Color(0xFFFF3B30), Modifier.weight(1f)) { scope.launch { tst(context, ShizukuManager.uninstallApp(app.packageName), Strings.shRemoved) } }
                    Chip(Strings.shSize, Color(0xFF8E8E93), Modifier.weight(1f)) { scope.launch { dataSize = ShizukuManager.getAppDataSize(app.packageName) } }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(Strings.shRestrictBg, Color(0xFF636366), Modifier.weight(1f)) { scope.launch { tst(context, ShizukuManager.restrictBackground(app.packageName, true), Strings.shBgRestricted) } }
                    Chip(Strings.shAllowBg, Color(0xFF30D158), Modifier.weight(1f)) { scope.launch { tst(context, ShizukuManager.restrictBackground(app.packageName, false), Strings.shBgAllowed) } }
                    Chip(Strings.shBackup, Color(0xFF5856D6), Modifier.weight(1f)) {
                        scope.launch { val dir = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GlassFiles_Backup"); dir.mkdirs()
                            val p = "${dir.absolutePath}/${app.packageName}_data.tar.gz"; tst(context, ShizukuManager.backupAppData(app.packageName, p), Strings.shBackupSaved) } }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(start = 68.dp).height(0.5.dp).background(SeparatorColor))
    }
}

// ═══ System ═══

@Composable
private fun SystemContent(context: Context, scope: kotlinx.coroutines.CoroutineScope) {
    var dpi by remember { mutableStateOf("...") }; var res by remember { mutableStateOf("...") }; var battery by remember { mutableStateOf("") }
    var procs by remember { mutableStateOf<List<ShizukuManager.ProcessInfo>>(emptyList()) }; var showDpi by remember { mutableStateOf(false) }; var showRes by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { dpi = ShizukuManager.getScreenDpi().let { if (it > 0) it.toString() else "?" }; res = ShizukuManager.getScreenResolution().ifBlank { "?" }; battery = ShizukuManager.getBatteryStats() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Label(Strings.shDisplay) }
        item { Card(Icons.Rounded.Smartphone, "DPI: $dpi", Strings.shChangeDpi, Color(0xFF007AFF)) { showDpi = true } }
        item { Card(Icons.Rounded.AspectRatio, res, Strings.shChangeRes, Color(0xFF5856D6)) { showRes = true } }
        item { Card(Icons.Rounded.Refresh, Strings.shResetDisplay, Strings.shResetDisplaySub, Color(0xFF8E8E93)) {
            scope.launch { ShizukuManager.resetScreenDpi(); ShizukuManager.resetScreenResolution(); dpi = ShizukuManager.getScreenDpi().toString(); res = ShizukuManager.getScreenResolution(); tst(context, true, Strings.shReset) } } }
        item { Label(Strings.shScreenCapture) }
        item { Card(Icons.Rounded.Screenshot, Strings.shScreenshot, Strings.shSaveScreenshot, Color(0xFF34C759)) {
            scope.launch { val p = "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)}/scr_${System.currentTimeMillis()}.png"; tst(context, ShizukuManager.takeScreenshot(p), Strings.shScreenshotSaved) } } }
        item { Card(Icons.Rounded.Videocam, Strings.shScreenRecord, Strings.shScreenRecordSub, Color(0xFFFF2D55)) {
            scope.launch { val p = "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)}/rec_${System.currentTimeMillis()}.mp4"; tst(context, ShizukuManager.startScreenRecord(p, 30), Strings.shRecordStarted) } } }
        item { Card(Icons.Rounded.StopCircle, Strings.shStopRecord, Strings.shStopRecordSub, Color(0xFF636366)) { scope.launch { ShizukuManager.stopScreenRecord(); tst(context, true, Strings.shRecordStopped) } } }
        item { Label(Strings.shConnection) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(Icons.Rounded.Wifi, Strings.shWifiOn, "", Color(0xFF007AFF), Modifier.weight(1f)) { scope.launch { ShizukuManager.setWifi(true); tst(context, true, "ON") } }
            Card(Icons.Rounded.WifiOff, Strings.shWifiOff, "", Color(0xFFFF3B30), Modifier.weight(1f)) { scope.launch { ShizukuManager.setWifi(false); tst(context, true, "OFF") } }
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(Icons.Rounded.Bluetooth, Strings.shBtOn, "", Color(0xFF007AFF), Modifier.weight(1f)) { scope.launch { ShizukuManager.setBluetooth(true); tst(context, true, "ON") } }
            Card(Icons.Rounded.BluetoothDisabled, Strings.shBtOff, "", Color(0xFFFF3B30), Modifier.weight(1f)) { scope.launch { ShizukuManager.setBluetooth(false); tst(context, true, "OFF") } }
        } }
        item { Label(Strings.shBattery) }
        item { if (battery.isNotBlank()) Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(12.dp)) {
            Text(battery.take(500), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextPrimary, lineHeight = 16.sp) } }
        item { Label(Strings.shProcesses) }
        item { Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Blue).clickable { scope.launch { procs = ShizukuManager.getRunningProcesses() } }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(Strings.shLoadProcesses, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) } }
        if (procs.isNotEmpty()) { item { Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth()) { Text("PID", fontSize = 10.sp, color = TextTertiary, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp)); Text("RAM", fontSize = 10.sp, color = TextTertiary, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp)); Text(Strings.shProcess, fontSize = 10.sp, color = TextTertiary, fontWeight = FontWeight.Bold) }
                procs.take(25).forEach { p -> Row(Modifier.fillMaxWidth()) { Text("${p.pid}", fontSize = 11.sp, color = TextSecondary, fontFamily = FontFamily.Monospace, modifier = Modifier.width(48.dp)); Text(fmtSz(p.memKb * 1024), fontSize = 11.sp, color = Blue, fontFamily = FontFamily.Monospace, modifier = Modifier.width(60.dp)); Text(p.name, fontSize = 11.sp, color = TextPrimary, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
            } } } }
        item { Label(Strings.shReboot) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(Icons.Rounded.RestartAlt, "Reboot", "", Color(0xFFFF3B30), Modifier.weight(1f)) { scope.launch { ShizukuManager.reboot() } }
            Card(Icons.Rounded.PhoneAndroid, "Recovery", "", Color(0xFFFF9F0A), Modifier.weight(1f)) { scope.launch { ShizukuManager.reboot("recovery") } }
            Card(Icons.Rounded.DeveloperMode, "Bootloader", "", Color(0xFF636366), Modifier.weight(1f)) { scope.launch { ShizukuManager.reboot("bootloader") } }
        } }
        item { Spacer(Modifier.height(80.dp)) }
    }
    if (showDpi) DpiDialog(context, scope, dpi, { showDpi = false }) { dpi = it }
    if (showRes) ResDialog(context, scope, { showRes = false }) { res = it }
}

// ═══ Logs ═══

@Composable
private fun LogsContent(context: Context, scope: kotlinx.coroutines.CoroutineScope) {
    var log by remember { mutableStateOf("") }; var filter by remember { mutableStateOf("") }; var loading by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(SurfaceWhite).padding(horizontal = 12.dp, vertical = 10.dp)) {
                if (filter.isEmpty()) Text(Strings.shFilter, color = TextTertiary, fontSize = 14.sp)
                BasicTextField(filter, { filter = it }, textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 14.sp, fontFamily = FontFamily.Monospace), singleLine = true, modifier = Modifier.fillMaxWidth()) }
            IconButton(onClick = { loading = true; scope.launch { log = ShizukuManager.getLogcat(300, filter); loading = false } }) { Icon(Icons.Rounded.Refresh, null, Modifier.size(22.dp), tint = Blue) }
            IconButton(onClick = { scope.launch { ShizukuManager.clearLogcat(); log = ""; tst(context, true, Strings.shLogsCleared) } }) { Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(22.dp), tint = Red) }
        }
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp) }
        else if (log.isBlank()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Terminal, null, Modifier.size(40.dp), tint = TextTertiary); Spacer(Modifier.height(8.dp)); Text(Strings.shPressRefresh, color = TextSecondary, fontSize = 14.sp) } }
        else Box(Modifier.fillMaxSize().padding(8.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1C1C1E)).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
            Text(log, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFE5E5EA), lineHeight = 14.sp, modifier = Modifier.padding(10.dp)) }
    }
}

// ═══ Automation ═══

@Composable
private fun AutoContent(context: Context, scope: kotlinx.coroutines.CoroutineScope) {
    var brightness by remember { mutableStateOf(-1) }; var animScale by remember { mutableStateOf("") }; var screenTimeout by remember { mutableStateOf(-1L) }
    var ssid by remember { mutableStateOf("") }; var ip by remember { mutableStateOf("") }; var mac by remember { mutableStateOf("") }
    var tapX by remember { mutableStateOf("540") }; var tapY by remember { mutableStateOf("960") }; var textToType by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { brightness = ShizukuManager.getBrightness(); animScale = ShizukuManager.getAnimationScale(); screenTimeout = ShizukuManager.getScreenTimeout(); ssid = ShizukuManager.getCurrentSsid(); ip = ShizukuManager.getIpAddress(); mac = ShizukuManager.getMacAddress() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Label(Strings.shTouchSim) }
        item { Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp)) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(tapX, { tapX = it }, label = { Text("X") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(tapY, { tapY = it }, label = { Text("Y") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(Strings.shTap, Color(0xFF007AFF), Modifier.weight(1f)) { scope.launch { tst(context, ShizukuManager.tap(tapX.toIntOrNull() ?: 540, tapY.toIntOrNull() ?: 960), Strings.shTap) } }
                Chip(Strings.shLongTap, Color(0xFF5856D6), Modifier.weight(1f)) { scope.launch { tst(context, ShizukuManager.longPress(tapX.toIntOrNull() ?: 540, tapY.toIntOrNull() ?: 960), Strings.shLongTap) } } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(Strings.shSwipeUp, Color(0xFF34C759), Modifier.weight(1f)) { scope.launch { ShizukuManager.swipe(540, 1600, 540, 400, 300) } }
                Chip(Strings.shSwipeDown, Color(0xFF34C759), Modifier.weight(1f)) { scope.launch { ShizukuManager.swipe(540, 400, 540, 1600, 300) } } }
        } } }

        item { Label(Strings.shTextInput) }
        item { Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp)) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(textToType, { textToType = it }, label = { Text(Strings.shTextToType) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Blue).clickable { if (textToType.isNotBlank()) scope.launch { tst(context, ShizukuManager.inputText(textToType), Strings.shTyped) } }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(Strings.shType, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        } } }

        item { Label(Strings.shControlButtons) }
        item { Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp)) { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(Strings.shBack, Color(0xFF8E8E93), Modifier.weight(1f)) { scope.launch { ShizukuManager.pressBack() } }
                Chip(Strings.shHome, Color(0xFF007AFF), Modifier.weight(1f)) { scope.launch { ShizukuManager.pressHome() } }
                Chip(Strings.shRecent, Color(0xFF5856D6), Modifier.weight(1f)) { scope.launch { ShizukuManager.pressRecents() } } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(Strings.shVolumeUp, Color(0xFF34C759), Modifier.weight(1f)) { scope.launch { ShizukuManager.volumeUp() } }
                Chip(Strings.shVolumeDown, Color(0xFFFF9F0A), Modifier.weight(1f)) { scope.launch { ShizukuManager.volumeDown() } }
                Chip(Strings.shMute, Color(0xFFFF3B30), Modifier.weight(1f)) { scope.launch { ShizukuManager.mute() } } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Play/Pause", Color(0xFF007AFF), Modifier.weight(1f)) { scope.launch { ShizukuManager.mediaPlayPause() } }
                Chip(Strings.shForward, Color(0xFF636366), Modifier.weight(1f)) { scope.launch { ShizukuManager.mediaNext() } }
                Chip(Strings.shBack, Color(0xFF636366), Modifier.weight(1f)) { scope.launch { ShizukuManager.mediaPrevious() } } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(Strings.shPower, Color(0xFFFF3B30), Modifier.weight(1f)) { scope.launch { ShizukuManager.pressPower() } }
                Chip(Strings.shCamera, Color(0xFF34C759), Modifier.weight(1f)) { scope.launch { ShizukuManager.openCamera() } }
                Chip(Strings.shScreenshot, Color(0xFF5856D6), Modifier.weight(1f)) { scope.launch { ShizukuManager.screenshotKey() } } }
        } } }

        item { Label("${Strings.shBrightness}: $brightness") }
        item { Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp)) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Slider(value = brightness.coerceIn(0, 255).toFloat(), onValueChange = { brightness = it.toInt() }, onValueChangeFinished = { scope.launch { ShizukuManager.setBrightness(brightness) } },
                valueRange = 0f..255f, modifier = Modifier.fillMaxWidth(), colors = SliderDefaults.colors(thumbColor = Blue, activeTrackColor = Blue))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(Strings.shMin, Color(0xFF636366), Modifier.weight(1f)) { scope.launch { brightness = 1; ShizukuManager.setBrightness(1) } }
                Chip(Strings.shMedium, Color(0xFFFF9F0A), Modifier.weight(1f)) { scope.launch { brightness = 128; ShizukuManager.setBrightness(128) } }
                Chip(Strings.shMax, Color(0xFFFF3B30), Modifier.weight(1f)) { scope.launch { brightness = 255; ShizukuManager.setBrightness(255) } }
                Chip(Strings.shAutoBrightness, Color(0xFF007AFF), Modifier.weight(1f)) { scope.launch { ShizukuManager.setAutoBrightness(true); tst(context, true, Strings.shAutoB) } } }
        } } }

        item { Label("${Strings.shAnimations}: $animScale") }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(Strings.shOff, Color(0xFFFF3B30), Modifier.weight(1f)) { scope.launch { ShizukuManager.setAnimationScale(0f); animScale = ShizukuManager.getAnimationScale() } }
            Chip("0.5x", Color(0xFFFF9F0A), Modifier.weight(1f)) { scope.launch { ShizukuManager.setAnimationScale(0.5f); animScale = ShizukuManager.getAnimationScale() } }
            Chip("1x", Color(0xFF34C759), Modifier.weight(1f)) { scope.launch { ShizukuManager.setAnimationScale(1f); animScale = ShizukuManager.getAnimationScale() } }
            Chip("2x", Color(0xFF5856D6), Modifier.weight(1f)) { scope.launch { ShizukuManager.setAnimationScale(2f); animScale = ShizukuManager.getAnimationScale() } }
        } }

        item { Label("${Strings.shScreenTimeout}: ${if (screenTimeout > 0) "${screenTimeout / 1000}s" else "?"}") }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("15s", Color(0xFF8E8E93), Modifier.weight(1f)) { scope.launch { ShizukuManager.setScreenTimeout(15000); screenTimeout = 15000 } }
            Chip("1m", Color(0xFF636366), Modifier.weight(1f)) { scope.launch { ShizukuManager.setScreenTimeout(60000); screenTimeout = 60000 } }
            Chip("5m", Color(0xFF007AFF), Modifier.weight(1f)) { scope.launch { ShizukuManager.setScreenTimeout(300000); screenTimeout = 300000 } }
            Chip("30m", Color(0xFF5856D6), Modifier.weight(1f)) { scope.launch { ShizukuManager.setScreenTimeout(1800000); screenTimeout = 1800000 } }
        } }

        item { Label(Strings.shConnection) }
        item { Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp)) { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(Strings.shDataOn, Color(0xFF34C759), Modifier.weight(1f)) { scope.launch { ShizukuManager.setMobileData(true) } }
                Chip(Strings.shDataOff, Color(0xFFFF3B30), Modifier.weight(1f)) { scope.launch { ShizukuManager.setMobileData(false) } } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(Strings.shAirplaneOn, Color(0xFFFF9F0A), Modifier.weight(1f)) { scope.launch { ShizukuManager.setAirplaneMode(true) } }
                Chip(Strings.shAirplaneOff, Color(0xFF007AFF), Modifier.weight(1f)) { scope.launch { ShizukuManager.setAirplaneMode(false) } } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(Strings.shGpsOn, Color(0xFF34C759), Modifier.weight(1f)) { scope.launch { ShizukuManager.setLocationMode(3) } }
                Chip(Strings.shGpsOff, Color(0xFFFF3B30), Modifier.weight(1f)) { scope.launch { ShizukuManager.setLocationMode(0) } } }
            if (ssid.isNotBlank()) InfoRow("Wi-Fi", ssid)
            if (ip.isNotBlank()) InfoRow("IP", ip)
            if (mac.isNotBlank()) InfoRow("MAC", mac)
        } } }

        item { Label(Strings.shSound) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(Strings.shSilence, Color(0xFFFF3B30), Modifier.weight(1f)) { scope.launch { ShizukuManager.setDndMode(2) } }
            Chip(Strings.shAlarmsOnly, Color(0xFFFF9F0A), Modifier.weight(1f)) { scope.launch { ShizukuManager.setDndMode(3) } }
            Chip(Strings.shSoundOn, Color(0xFF34C759), Modifier.weight(1f)) { scope.launch { ShizukuManager.setDndMode(0) } }
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(Strings.shSysSoundsOff, Color(0xFF636366), Modifier.weight(1f)) { scope.launch { ShizukuManager.setSystemSounds(false) } }
            Chip(Strings.shSysSoundsOn, Color(0xFF007AFF), Modifier.weight(1f)) { scope.launch { ShizukuManager.setSystemSounds(true) } }
        } }

        item { Label(Strings.shDisplayMode) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(Strings.shDarkTheme, Color(0xFF636366), Modifier.weight(1f)) { scope.launch { ShizukuManager.setDarkMode(true) } }
            Chip(Strings.shLightTheme, Color(0xFFFF9F0A), Modifier.weight(1f)) { scope.launch { ShizukuManager.setDarkMode(false) } }
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(Strings.shStayOnCharging, Color(0xFF007AFF), Modifier.weight(1f)) { scope.launch { ShizukuManager.setStayOnWhileCharging(7) } }
            Chip(Strings.shNormalTimeout, Color(0xFF8E8E93), Modifier.weight(1f)) { scope.launch { ShizukuManager.setStayOnWhileCharging(0) } }
        } }

        item { Label(Strings.shQuickLaunch) }
        item { var url by remember { mutableStateOf("") }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp)) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(url, { url = it }, label = { Text(Strings.shUrlToOpen) }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Blue).clickable { if (url.isNotBlank()) scope.launch { tst(context, ShizukuManager.openUrl(url), Strings.shOpened) } }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(Strings.shOpenUrl, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            } } }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = TextSecondary); Text(value, fontSize = 12.sp, color = TextPrimary, fontFamily = FontFamily.Monospace) }
}

// ═══ Dialogs ═══

@Composable
private fun DpiDialog(context: Context, scope: kotlinx.coroutines.CoroutineScope, cur: String, onDismiss: () -> Unit, onApplied: (String) -> Unit) {
    var v by remember { mutableStateOf(cur) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceWhite,
        title = { Text(Strings.shChangeDpiTitle, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${Strings.shCurrent}: $cur", fontSize = 13.sp, color = TextSecondary)
            OutlinedTextField(v, { v = it }, label = { Text("DPI") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("320", "360", "400", "420", "480", "560").forEach { d ->
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if (v == d) Blue.copy(0.12f) else SurfaceLight).clickable { v = d }.padding(horizontal = 8.dp, vertical = 5.dp)) { Text(d, fontSize = 12.sp, color = if (v == d) Blue else TextSecondary) } } }
        } },
        confirmButton = { TextButton(onClick = { scope.launch { ShizukuManager.setScreenDpi(v.toIntOrNull() ?: 420); onApplied(v) }; onDismiss() }) { Text(Strings.ok, color = Blue) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } })
}

@Composable
private fun ResDialog(context: Context, scope: kotlinx.coroutines.CoroutineScope, onDismiss: () -> Unit, onApplied: (String) -> Unit) {
    var w by remember { mutableStateOf("1080") }; var h by remember { mutableStateOf("2400") }
    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceWhite,
        title = { Text(Strings.shResolution, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(w, { w = it }, label = { Text("W") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Text("×", fontSize = 20.sp, color = TextSecondary, modifier = Modifier.align(Alignment.CenterVertically))
            OutlinedTextField(h, { h = it }, label = { Text("H") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        } },
        confirmButton = { TextButton(onClick = { val ww = w.toIntOrNull() ?: 1080; val hh = h.toIntOrNull() ?: 2400; scope.launch { ShizukuManager.setScreenResolution(ww, hh); onApplied("${ww}x${hh}") }; onDismiss() }) { Text(Strings.ok, color = Blue) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } })
}

@Composable
private fun ChmodDialog(scope: kotlinx.coroutines.CoroutineScope, onDismiss: () -> Unit) {
    var path by remember { mutableStateOf("") }; var mode by remember { mutableStateOf("755") }; val ctx = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceWhite,
        title = { Text(Strings.shChangePerms, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(path, { path = it }, label = { Text(Strings.shFilePath) }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp))
            OutlinedTextField(mode, { mode = it }, label = { Text(Strings.shPermissions) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("644", "755", "777", "600").forEach { m ->
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if (mode == m) Blue.copy(0.12f) else SurfaceLight).clickable { mode = m }.padding(horizontal = 10.dp, vertical = 5.dp)) { Text(m, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = if (mode == m) Blue else TextSecondary) } } }
        } },
        confirmButton = { TextButton(onClick = { if (path.isNotBlank()) scope.launch { tst(ctx, ShizukuManager.chmod(path, mode), "chmod $mode") }; onDismiss() }) { Text(Strings.ok, color = Blue) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } })
}

@Composable
private fun SymlinkDialog(context: Context, scope: kotlinx.coroutines.CoroutineScope, onDismiss: () -> Unit) {
    var target by remember { mutableStateOf("") }; var link by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceWhite,
        title = { Text(Strings.shSymlink, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(target, { target = it }, label = { Text(Strings.shTarget) }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp))
            OutlinedTextField(link, { link = it }, label = { Text(Strings.shLinkPath) }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp))
        } },
        confirmButton = { TextButton(onClick = { if (target.isNotBlank() && link.isNotBlank()) scope.launch { tst(context, ShizukuManager.symlink(target, link), Strings.shCreated) }; onDismiss() }) { Text(Strings.create, color = Blue) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } })
}

// ═══ Shared UI ═══

@Composable
private fun Label(text: String) { Text(text, fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)) }

@Composable
private fun Card(icon: ImageVector, title: String, subtitle: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(34.dp).background(color.copy(0.1f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(18.dp), tint = color) }
        Column(Modifier.weight(1f)) { Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 11.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
}

@Composable
private fun Chip(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(0.08f)).clickable(onClick = onClick).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium, maxLines = 1) }
}

private fun tst(ctx: Context, ok: Boolean, msg: String) { Toast.makeText(ctx, if (ok) msg else Strings.error, Toast.LENGTH_SHORT).show() }
private fun fmtSz(b: Long): String = when { b < 1024 -> "$b B"; b < 1024L * 1024 -> "%.1f KB".format(b / 1024.0); b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / (1024.0 * 1024)); else -> "%.2f GB".format(b / (1024.0 * 1024 * 1024)) }
