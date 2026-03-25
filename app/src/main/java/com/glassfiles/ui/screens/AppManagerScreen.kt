package com.glassfiles.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.glassfiles.data.Strings
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class AppItem(
    val name: String, val packageName: String, val versionName: String,
    val versionCode: Long, val apkPath: String, val apkSize: Long,
    val isSystem: Boolean, val installed: Long, val updated: Long,
    val targetSdk: Int, val minSdk: Int, val icon: android.graphics.drawable.Drawable?
)

@Composable
fun AppManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    var sortMode by remember { mutableIntStateOf(0) } // 0=name, 1=size, 2=date
    var selectedApp by remember { mutableStateOf<AppItem?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            apps = loadApps(context)
            loading = false
        }
    }

    val filtered = remember(apps, query, showSystem, sortMode) {
        var list = if (showSystem) apps else apps.filter { !it.isSystem }
        if (query.isNotBlank()) list = list.filter {
            it.name.contains(query, true) || it.packageName.contains(query, true)
        }
        when (sortMode) {
            1 -> list.sortedByDescending { it.apkSize }
            2 -> list.sortedByDescending { it.updated }
            else -> list.sortedBy { it.name.lowercase() }
        }
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        // Top bar
        Row(Modifier.fillMaxWidth().background(SurfaceWhite).padding(top = 44.dp, start = 4.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = Blue) }
            Text(Strings.appManager, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 20.sp, modifier = Modifier.weight(1f))
            Text("${filtered.size}", fontSize = 14.sp, color = TextSecondary, modifier = Modifier.padding(end = 8.dp))
        }

        // Search + filters
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(SurfaceWhite).padding(horizontal = 12.dp, vertical = 10.dp)) {
                if (query.isEmpty()) Text(Strings.searchApps, color = TextTertiary, fontSize = 14.sp)
                androidx.compose.foundation.text.BasicTextField(query, { query = it }, textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 14.sp),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }

        // Filter chips
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChipSmall(if (showSystem) Strings.systemApps else Strings.userApps, true) { showSystem = !showSystem }
            FilterChipSmall(Strings.sortByName, sortMode == 0) { sortMode = 0 }
            FilterChipSmall(Strings.sortBySize, sortMode == 1) { sortMode = 1 }
            FilterChipSmall(Strings.sortByDate, sortMode == 2) { sortMode = 2 }
        }

        Spacer(Modifier.height(4.dp))

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue, modifier = Modifier.size(32.dp))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    AppRow(app, onClick = { selectedApp = app }, onExtract = {
                        scope.launch {
                            val result = extractApk(context, app)
                            Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            }
        }
    }

    // App detail dialog
    if (selectedApp != null) {
        AppDetailDialog(selectedApp!!, onDismiss = { selectedApp = null }, onExtract = {
            scope.launch {
                val result = extractApk(context, selectedApp!!)
                Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
            }
        })
    }
}

@Composable
private fun FilterChipSmall(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(8.dp))
        .background(if (selected) Blue.copy(0.15f) else SurfaceWhite)
        .border(1.dp, if (selected) Blue.copy(0.4f) else SeparatorColor, RoundedCornerShape(8.dp))
        .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(label, fontSize = 12.sp, color = if (selected) Blue else TextSecondary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun AppRow(app: AppItem, onClick: () -> Unit, onExtract: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Icon
        if (app.icon != null) {
            val bmp = remember(app.packageName) { app.icon.toBitmap(128, 128).asImageBitmap() }
            Image(bmp, app.name, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
        } else {
            Box(Modifier.size(44.dp).background(Blue.copy(0.1f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Android, null, Modifier.size(24.dp), tint = Blue)
            }
        }
        // Info
        Column(Modifier.weight(1f)) {
            Text(app.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.packageName, fontSize = 11.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // Size
        Column(horizontalAlignment = Alignment.End) {
            Text(fmtSize(app.apkSize), fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
            if (app.isSystem) Text("SYS", fontSize = 9.sp, color = Color(0xFFFF9800), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AppDetailDialog(app: AppItem, onDismiss: () -> Unit, onExtract: () -> Unit) {
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
    var showPerms by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        modifier = Modifier.heightIn(max = 480.dp),
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (app.icon != null) {
                        val bmp = remember(app.packageName) { app.icon.toBitmap(128, 128).asImageBitmap() }
                        Image(bmp, app.name, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(app.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("v${app.versionName} • ${fmtSize(app.apkSize)}", fontSize = 12.sp, color = TextSecondary)
                    }
                }
                Spacer(Modifier.height(10.dp))
                // Action buttons — always visible
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionBtn(Icons.Rounded.Download, Strings.extractApk, Blue, Modifier.weight(1f)) { onExtract() }
                    ActionBtn(Icons.Rounded.OpenInNew, Strings.openApp, Color(0xFF4CAF50), Modifier.weight(1f)) {
                        context.packageManager.getLaunchIntentForPackage(app.packageName)?.let { context.startActivity(it) }
                    }
                    ActionBtn(Icons.Rounded.Settings, Strings.appInfo, Color(0xFFFF9800), Modifier.weight(1f)) {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
                    }
                }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DetailRow(Strings.packageName, app.packageName)
                DetailRow("SDK", "${app.minSdk} → ${app.targetSdk}")
                DetailRow(Strings.installedDate, sdf.format(Date(app.installed)))
                DetailRow(Strings.updatedDate, sdf.format(Date(app.updated)))

                // Components
                val (acts, svcs, rcvs) = remember(app.packageName) {
                    try {
                        val pi = context.packageManager.getPackageInfo(app.packageName,
                            PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS)
                        Triple(pi.activities?.size ?: 0, pi.services?.size ?: 0, pi.receivers?.size ?: 0)
                    } catch (_: Exception) { Triple(0, 0, 0) }
                }
                if (acts + svcs + rcvs > 0) {
                    DetailRow("A/S/R", "$acts / $svcs / $rcvs")
                }

                // Permissions — collapsed by default
                val perms = remember(app.packageName) {
                    try { context.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions?.toList() } catch (_: Exception) { null }
                }
                if (perms != null && perms.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().clickable { showPerms = !showPerms }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("${Strings.permissions} (${perms.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Blue, modifier = Modifier.weight(1f))
                        Icon(if (showPerms) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.size(16.dp), tint = Blue)
                    }
                    if (showPerms) {
                        perms.forEach { p ->
                            Text("• ${p.substringAfterLast(".")}", fontSize = 10.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(Strings.close, color = Blue) } }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Text(value, fontSize = 13.sp, color = TextPrimary, fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f, false).padding(start = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ActionBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(0.1f)).clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(20.dp), tint = color)
        Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

internal fun loadApps(context: Context): List<AppItem> {
    val pm = context.packageManager
    val packages = pm.getInstalledPackages(0)
    return packages.mapNotNull { pi ->
        try {
            val ai = pi.applicationInfo ?: return@mapNotNull null
            val name = pm.getApplicationLabel(ai).toString()
            val apkFile = File(ai.sourceDir)
            AppItem(
                name = name, packageName = pi.packageName,
                versionName = pi.versionName ?: "?",
                versionCode = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong(),
                apkPath = ai.sourceDir, apkSize = apkFile.length(),
                isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                installed = pi.firstInstallTime, updated = pi.lastUpdateTime,
                targetSdk = ai.targetSdkVersion,
                minSdk = if (Build.VERSION.SDK_INT >= 24) ai.minSdkVersion else 0,
                icon = try { pm.getApplicationIcon(ai) } catch (_: Exception) { null }
            )
        } catch (_: Exception) { null }
    }
}

private suspend fun extractApk(context: Context, app: AppItem): String {
    return withContext(Dispatchers.IO) {
        try {
            val src = File(app.apkPath)
            val destDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "GlassFiles_APK")
            destDir.mkdirs()
            val destFile = File(destDir, "${app.name}_v${app.versionName}.apk")
            src.copyTo(destFile, overwrite = true)
            "${Strings.apkExtracted}: ${destFile.name}"
        } catch (e: Exception) {
            "${Strings.error}: ${e.message}"
        }
    }
}

private fun fmtSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024L * 1024 -> "%.1f KB".format(b / 1024.0)
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / (1024.0 * 1024))
    else -> "%.2f GB".format(b / (1024.0 * 1024 * 1024))
}
