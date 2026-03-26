package com.glassfiles.data

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * ShizukuManager — executes privileged commands through Shizuku.
 * Uses direct Shizuku API (dev.rikka.shizuku:api).
 */
object ShizukuManager {

    private const val TAG = "ShizukuManager"
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    // ═══════════════════════════════════
    // Status
    // ═══════════════════════════════════

    fun isShizukuInstalled(context: Context): Boolean {
        return try { context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0); true }
        catch (_: PackageManager.NameNotFoundException) { false }
    }

    fun isShizukuRunning(): Boolean {
        return try { rikka.shizuku.Shizuku.pingBinder() }
        catch (e: Exception) { Log.w(TAG, "ping: ${e.message}"); false }
    }

    fun hasShizukuPermission(): Boolean {
        return try { rikka.shizuku.Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
        catch (e: Exception) { Log.w(TAG, "perm: ${e.message}"); false }
    }

    fun requestPermission(requestCode: Int) {
        try { rikka.shizuku.Shizuku.requestPermission(requestCode) }
        catch (e: Exception) { Log.e(TAG, "req: ${e.message}") }
    }

    // ═══════════════════════════════════
    // Shell execution — direct API
    // ═══════════════════════════════════

    private suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            val process = rikka.shizuku.Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText()
            val exitCode = process.waitFor()
            ShellResult(exitCode == 0, stdout.trim(), stderr.trim())
        } catch (e: Exception) {
            Log.e(TAG, "exec[$command]: ${e.message}")
            ShellResult(false, "", e.message ?: "Unknown error")
        }
    }

    // ═══════════════════════════════════
    // App management
    // ═══════════════════════════════════

    suspend fun forceStop(pkg: String): Boolean = exec("am force-stop $pkg").success
    suspend fun freezeApp(pkg: String): Boolean = exec("pm disable-user --user 0 $pkg").success
    suspend fun unfreezeApp(pkg: String): Boolean = exec("pm enable --user 0 $pkg").success

    suspend fun clearCache(pkg: String): Boolean {
        val r = exec("pm clear --cache-only $pkg")
        return if (r.success) true else exec("rm -rf /data/data/$pkg/cache/*").success
    }

    suspend fun silentInstall(apkPath: String): Boolean = exec("pm install -r -t \"$apkPath\"").success
    suspend fun uninstallApp(pkg: String): Boolean = exec("pm uninstall $pkg").success
    suspend fun downgradeInstall(apkPath: String): Boolean = exec("pm install -r -d -t \"$apkPath\"").success
    suspend fun clearAllData(pkg: String): Boolean = exec("pm clear $pkg").success

    suspend fun isAppFrozen(pkg: String): Boolean {
        val r = exec("pm list packages -d")
        return r.stdout.contains(pkg)
    }

    suspend fun getAppDataSize(pkg: String): Long {
        val r = exec("du -sb /data/data/$pkg 2>/dev/null | cut -f1")
        return r.stdout.trim().toLongOrNull() ?: 0L
    }

    suspend fun revokePermission(pkg: String, perm: String): Boolean = exec("pm revoke $pkg $perm").success
    suspend fun grantPermission(pkg: String, perm: String): Boolean = exec("pm grant $pkg $perm").success

    suspend fun restrictBackground(pkg: String, restrict: Boolean): Boolean {
        val op = if (restrict) "ignore" else "allow"
        return exec("appops set $pkg RUN_IN_BACKGROUND $op").success
    }

    suspend fun backupAppData(pkg: String, outputPath: String): Boolean =
        exec("tar -czf \"$outputPath\" -C /data/data/$pkg . 2>/dev/null").success

    // ═══════════════════════════════════
    // File operations
    // ═══════════════════════════════════

    suspend fun listRestrictedDir(path: String): List<ShizukuFileItem> = withContext(Dispatchers.IO) {
        val r = exec("ls -la \"$path\"")
        if (!r.success) return@withContext emptyList()
        r.stdout.lines().mapNotNull { parseListLine(it, path) }
    }

    suspend fun copyFromRestricted(src: String, dest: String): Boolean = exec("cp -r \"$src\" \"$dest\"").success
    suspend fun copyToRestricted(src: String, dest: String): Boolean = exec("cp -r \"$src\" \"$dest\"").success
    suspend fun deleteFromRestricted(path: String): Boolean = exec("rm -rf \"$path\"").success
    suspend fun renameInRestricted(old: String, new: String): Boolean = exec("mv \"$old\" \"$new\"").success
    suspend fun getDirectorySize(path: String): Long {
        val r = exec("du -sb \"$path\" 2>/dev/null | cut -f1")
        return r.stdout.trim().toLongOrNull() ?: 0L
    }

    suspend fun chmod(path: String, mode: String): Boolean = exec("chmod $mode \"$path\"").success
    suspend fun chown(path: String, owner: String): Boolean = exec("chown $owner \"$path\"").success
    suspend fun symlink(target: String, linkPath: String): Boolean = exec("ln -s \"$target\" \"$linkPath\"").success

    // ═══════════════════════════════════
    // System
    // ═══════════════════════════════════

    suspend fun takeScreenshot(out: String): Boolean = exec("screencap -p \"$out\"").success
    suspend fun startScreenRecord(out: String, sec: Int = 30): Boolean = exec("screenrecord --time-limit $sec \"$out\" &").success
    suspend fun stopScreenRecord(): Boolean = exec("pkill -INT screenrecord").success

    suspend fun getScreenDpi(): Int {
        val r = exec("wm density")
        return Regex("(\\d+)").findAll(r.stdout).lastOrNull()?.value?.toIntOrNull() ?: 0
    }
    suspend fun setScreenDpi(dpi: Int): Boolean = exec("wm density $dpi").success
    suspend fun resetScreenDpi(): Boolean = exec("wm density reset").success
    suspend fun getScreenResolution(): String = exec("wm size").let { if (it.success) it.stdout.trim() else "" }
    suspend fun setScreenResolution(w: Int, h: Int): Boolean = exec("wm size ${w}x${h}").success
    suspend fun resetScreenResolution(): Boolean = exec("wm size reset").success

    suspend fun setWifi(on: Boolean): Boolean = exec("svc wifi ${if (on) "enable" else "disable"}").success
    suspend fun setBluetooth(on: Boolean): Boolean = exec("svc bluetooth ${if (on) "enable" else "disable"}").success
    suspend fun reboot(mode: String = ""): Boolean = exec(if (mode.isBlank()) "reboot" else "reboot $mode").success

    suspend fun getLogcat(lines: Int = 200, filter: String = ""): String {
        val grep = if (filter.isNotBlank()) " | grep -i \"$filter\"" else ""
        val r = exec("logcat -d -t $lines$grep")
        return if (r.success) r.stdout else r.stderr
    }
    suspend fun clearLogcat(): Boolean = exec("logcat -c").success

    suspend fun getBatteryStats(): String = exec("dumpsys battery").let { if (it.success) it.stdout else "" }

    suspend fun getRunningProcesses(): List<ProcessInfo> = withContext(Dispatchers.IO) {
        val r = exec("ps -A -o PID,RSS,NAME 2>/dev/null || ps -A")
        if (!r.success) return@withContext emptyList()
        r.stdout.lines().drop(1).mapNotNull { line ->
            val p = line.trim().split("\\s+".toRegex())
            if (p.size >= 3) ProcessInfo(p[0].toIntOrNull() ?: 0, p[1].toLongOrNull() ?: 0L, p.drop(2).joinToString(" "))
            else null
        }.sortedByDescending { it.memKb }
    }

    suspend fun getMounts(): String = exec("mount").let { if (it.success) it.stdout else "" }

    // ═══════════════════════════════════
    // Helpers
    // ═══════════════════════════════════

    private fun parseListLine(line: String, parentPath: String): ShizukuFileItem? {
        val parts = line.trim().split("\\s+".toRegex())
        if (parts.size < 8) return null
        val perms = parts[0]; if (perms == "total") return null
        val isDir = perms.startsWith("d")
        val size = parts[4].toLongOrNull() ?: 0L
        val name = parts.drop(7).joinToString(" ")
        if (name == "." || name == "..") return null
        return ShizukuFileItem(name, "$parentPath/$name", isDir, size)
    }

    data class ShellResult(val success: Boolean, val stdout: String, val stderr: String)
    data class ShizukuFileItem(val name: String, val path: String, val isDirectory: Boolean, val size: Long)
    data class ProcessInfo(val pid: Int, val memKb: Long, val name: String)
}