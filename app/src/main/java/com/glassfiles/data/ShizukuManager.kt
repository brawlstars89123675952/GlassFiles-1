package com.glassfiles.data

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method

object ShizukuManager {

    private const val TAG = "ShizukuMgr"
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
    // Shell execution — reflection with fallbacks
    // ═══════════════════════════════════

    private var cachedMethod: Method? = null

    private fun getNewProcessMethod(): Method? {
        if (cachedMethod != null) return cachedMethod
        return try {
            val m = rikka.shizuku.Shizuku::class.java.getDeclaredMethod(
                "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
            )
            m.isAccessible = true
            cachedMethod = m
            m
        } catch (e: Exception) {
            Log.e(TAG, "Method lookup failed: ${e.message}")
            // Fallback: try all declared methods
            try {
                val m = rikka.shizuku.Shizuku::class.java.declaredMethods.firstOrNull { it.name == "newProcess" && it.parameterCount == 3 }
                m?.isAccessible = true
                cachedMethod = m
                m
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback lookup failed: ${e2.message}")
                null
            }
        }
    }

    private suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            val method = getNewProcessMethod()
            if (method == null) return@withContext ShellResult(false, "", "Shizuku newProcess method not found")

            val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            process.destroy()
            Log.d(TAG, "exec[$command] exit=$exitCode stdout=${stdout.take(100)}")
            ShellResult(exitCode == 0, stdout.trim(), stderr.trim())
        } catch (e: java.lang.reflect.InvocationTargetException) {
            val cause = e.cause ?: e
            Log.e(TAG, "exec invoke error[$command]: ${cause.message}")
            ShellResult(false, "", "Invoke: ${cause.message}")
        } catch (e: Exception) {
            Log.e(TAG, "exec error[$command]: ${e.message}")
            ShellResult(false, "", "Error: ${e.message}")
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

    /** List directory with improved parsing and error feedback */
    suspend fun listRestrictedDir(path: String): List<ShizukuFileItem> = withContext(Dispatchers.IO) {
        // Try ls -la first
        var r = exec("ls -la \"$path\" 2>&1")
        if (!r.success && r.stdout.isBlank()) {
            // Fallback: simple ls
            r = exec("ls -1 \"$path\" 2>&1")
            if (!r.success) {
                Log.e(TAG, "listDir failed: ${r.stderr}")
                return@withContext emptyList()
            }
            // Simple parsing: one name per line
            return@withContext r.stdout.lines().filter { it.isNotBlank() && it != "." && it != ".." }.map { name ->
                val isDir = exec("test -d \"$path/$name\" && echo D || echo F").stdout.trim() == "D"
                ShizukuFileItem(name.trim(), "$path/${name.trim()}", isDir, 0L)
            }
        }

        val items = r.stdout.lines().mapNotNull { line -> parseListLine(line, path) }
        if (items.isEmpty() && r.stdout.isNotBlank()) {
            Log.w(TAG, "Parse returned 0 from: ${r.stdout.take(200)}")
        }
        items
    }

    /** Get last listing error for UI display */
    suspend fun getLastError(path: String): String {
        val r = exec("ls -la \"$path\" 2>&1")
        return if (r.success) "" else r.stderr.ifBlank { r.stdout }
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
    // Automation — tap, swipe, keyevents
    // ═══════════════════════════════════

    /** Simulate screen tap at coordinates */
    suspend fun tap(x: Int, y: Int): Boolean = exec("input tap $x $y").success

    /** Simulate swipe gesture */
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 300): Boolean =
        exec("input swipe $x1 $y1 $x2 $y2 $durationMs").success

    /** Simulate long press */
    suspend fun longPress(x: Int, y: Int, durationMs: Int = 1000): Boolean =
        exec("input swipe $x $y $x $y $durationMs").success

    /** Simulate key event (e.g. KEYCODE_BACK=4, HOME=3, POWER=26, VOLUME_UP=24, VOLUME_DOWN=25) */
    suspend fun keyEvent(keyCode: Int): Boolean = exec("input keyevent $keyCode").success

    /** Simulate text input */
    suspend fun inputText(text: String): Boolean {
        val escaped = text.replace(" ", "%s").replace("\"", "\\\"")
        return exec("input text \"$escaped\"").success
    }

    /** Press Home button */
    suspend fun pressHome(): Boolean = keyEvent(3)
    /** Press Back button */
    suspend fun pressBack(): Boolean = keyEvent(4)
    /** Press Power button */
    suspend fun pressPower(): Boolean = keyEvent(26)
    /** Press Recent Apps */
    suspend fun pressRecents(): Boolean = keyEvent(187)
    /** Volume Up */
    suspend fun volumeUp(): Boolean = keyEvent(24)
    /** Volume Down */
    suspend fun volumeDown(): Boolean = keyEvent(25)
    /** Media Play/Pause */
    suspend fun mediaPlayPause(): Boolean = keyEvent(85)
    /** Media Next */
    suspend fun mediaNext(): Boolean = keyEvent(87)
    /** Media Previous */
    suspend fun mediaPrevious(): Boolean = keyEvent(88)
    /** Toggle mute */
    suspend fun mute(): Boolean = keyEvent(164)
    /** Open camera */
    suspend fun openCamera(): Boolean = keyEvent(27)
    /** Take screenshot via key combo */
    suspend fun screenshotKey(): Boolean = exec("input keyevent 120").success

    // ═══════════════════════════════════
    // Automation — display & brightness
    // ═══════════════════════════════════

    /** Get current brightness (0-255) */
    suspend fun getBrightness(): Int {
        val r = exec("settings get system screen_brightness")
        return r.stdout.trim().toIntOrNull() ?: -1
    }

    /** Set brightness (0-255), bypasses system limits */
    suspend fun setBrightness(value: Int): Boolean {
        val v = value.coerceIn(0, 255)
        exec("settings put system screen_brightness_mode 0") // manual mode
        return exec("settings put system screen_brightness $v").success
    }

    /** Toggle auto brightness */
    suspend fun setAutoBrightness(on: Boolean): Boolean =
        exec("settings put system screen_brightness_mode ${if (on) 1 else 0}").success

    /** Get animation scale */
    suspend fun getAnimationScale(): String {
        val w = exec("settings get global window_animation_scale").stdout.trim()
        val t = exec("settings get global transition_animation_scale").stdout.trim()
        val a = exec("settings get global animator_duration_scale").stdout.trim()
        return "window=$w transition=$t animator=$a"
    }

    /** Set all animation scales (0.0 = off, 0.5 = fast, 1.0 = normal, 2.0 = slow) */
    suspend fun setAnimationScale(scale: Float): Boolean {
        val s = scale.toString()
        val r1 = exec("settings put global window_animation_scale $s")
        val r2 = exec("settings put global transition_animation_scale $s")
        val r3 = exec("settings put global animator_duration_scale $s")
        return r1.success && r2.success && r3.success
    }

    /** Get screen timeout in ms */
    suspend fun getScreenTimeout(): Long {
        val r = exec("settings get system screen_off_timeout")
        return r.stdout.trim().toLongOrNull() ?: -1L
    }

    /** Set screen timeout in ms */
    suspend fun setScreenTimeout(ms: Long): Boolean =
        exec("settings put system screen_off_timeout $ms").success

    /** Force dark mode for specific app */
    suspend fun forceDarkMode(packageName: String, enable: Boolean): Boolean {
        // Uses cmd overlay or appcompat
        return if (enable) {
            exec("cmd uimode night yes 2>/dev/null; settings put secure ui_night_mode 2").success
        } else {
            exec("cmd uimode night no 2>/dev/null; settings put secure ui_night_mode 1").success
        }
    }

    /** Set global dark mode */
    suspend fun setDarkMode(on: Boolean): Boolean =
        exec("cmd uimode night ${if (on) "yes" else "no"}").success

    /** Get stay-on-while-charging setting */
    suspend fun getStayOnWhileCharging(): Boolean {
        val r = exec("settings get global stay_on_while_plugged_in")
        return r.stdout.trim() != "0"
    }

    /** Keep screen on while charging (0=off, 3=usb+ac, 7=all) */
    suspend fun setStayOnWhileCharging(mode: Int): Boolean =
        exec("settings put global stay_on_while_plugged_in $mode").success

    // ═══════════════════════════════════
    // Automation — connectivity
    // ═══════════════════════════════════

    /** Toggle mobile data */
    suspend fun setMobileData(on: Boolean): Boolean =
        exec("svc data ${if (on) "enable" else "disable"}").success

    /** Toggle airplane mode */
    suspend fun setAirplaneMode(on: Boolean): Boolean {
        exec("settings put global airplane_mode_on ${if (on) 1 else 0}")
        return exec("am broadcast -a android.intent.action.AIRPLANE_MODE --ez state $on").success
    }

    /** Toggle NFC */
    suspend fun setNfc(on: Boolean): Boolean =
        exec("svc nfc ${if (on) "enable" else "disable"}").success

    /** Toggle GPS/Location */
    suspend fun setLocationMode(mode: Int): Boolean =
        exec("settings put secure location_mode $mode").success // 0=off, 3=high accuracy

    /** Get current connected Wi-Fi SSID */
    suspend fun getCurrentSsid(): String {
        val r = exec("dumpsys wifi | grep 'mWifiInfo' | head -1")
        val match = Regex("SSID: ([^,]+)").find(r.stdout)
        return match?.groupValues?.get(1)?.trim() ?: ""
    }

    /** Get IP address */
    suspend fun getIpAddress(): String {
        val r = exec("ip addr show wlan0 | grep 'inet ' | awk '{print \$2}' | cut -d/ -f1")
        return r.stdout.trim()
    }

    /** Get MAC address */
    suspend fun getMacAddress(): String {
        val r = exec("cat /sys/class/net/wlan0/address 2>/dev/null")
        return r.stdout.trim()
    }

    // ═══════════════════════════════════
    // Automation — sound
    // ═══════════════════════════════════

    /** Get current volume (stream 3 = music) */
    suspend fun getVolume(stream: Int = 3): Int {
        val r = exec("cmd media_session volume --get --stream $stream 2>/dev/null || dumpsys audio | grep 'STREAM_MUSIC' -A5 | grep 'Current' | head -1")
        return Regex("(\\d+)").find(r.stdout)?.value?.toIntOrNull() ?: -1
    }

    /** Set volume for stream */
    suspend fun setVolume(value: Int, stream: Int = 3): Boolean =
        exec("cmd media_session volume --set $value --stream $stream 2>/dev/null || media volume --set $value --stream $stream").success

    /** Set Do Not Disturb mode (0=off, 1=priority, 2=none/total silence, 3=alarms only) */
    suspend fun setDndMode(mode: Int): Boolean =
        exec("cmd notification set_dnd $mode 2>/dev/null || settings put global zen_mode $mode").success

    /** Disable system sounds */
    suspend fun setSystemSounds(on: Boolean): Boolean {
        exec("settings put system sound_effects_enabled ${if (on) 1 else 0}")
        return exec("settings put system haptic_feedback_enabled ${if (on) 1 else 0}").success
    }

    // ═══════════════════════════════════
    // Automation — open/launch
    // ═══════════════════════════════════

    /** Launch any app by package */
    suspend fun launchApp(packageName: String): Boolean {
        val r = exec("monkey -p $packageName -c android.intent.category.LAUNCHER 1 2>/dev/null")
        return r.success || exec("am start -n $(cmd package resolve-activity --brief $packageName | tail -1) 2>/dev/null").success
    }

    /** Open URL in browser */
    suspend fun openUrl(url: String): Boolean =
        exec("am start -a android.intent.action.VIEW -d \"$url\"").success

    /** Send intent broadcast */
    suspend fun sendBroadcast(action: String, extras: String = ""): Boolean =
        exec("am broadcast -a $action $extras").success

    /** Start activity */
    suspend fun startActivity(component: String): Boolean =
        exec("am start -n $component").success

    // ═══════════════════════════════════
    // Automation — clipboard
    // ═══════════════════════════════════

    /** Get clipboard content */
    suspend fun getClipboard(): String {
        val r = exec("cmd clipboard get-text 2>/dev/null || service call clipboard 2 2>/dev/null")
        return r.stdout.trim()
    }

    /** Set clipboard content */
    suspend fun setClipboard(text: String): Boolean =
        exec("cmd clipboard set-text \"$text\" 2>/dev/null || am broadcast -a clipper.set -e text \"$text\"").success

    // ═══════════════════════════════════
    // Privacy & Security
    // ═══════════════════════════════════

    /** Hide app from launcher (disable main activity) */
    suspend fun hideApp(pkg: String): Boolean =
        exec("pm disable --user 0 $pkg/.$(cmd package resolve-activity --brief $pkg 2>/dev/null | tail -1 | sed 's|.*/||') 2>/dev/null || pm hide $pkg").success

    /** Unhide app */
    suspend fun unhideApp(pkg: String): Boolean =
        exec("pm enable --user 0 $pkg 2>/dev/null || pm unhide $pkg").success

    /** Block camera access for app via appops */
    suspend fun blockCamera(pkg: String): Boolean =
        exec("appops set $pkg CAMERA deny").success

    /** Allow camera access for app */
    suspend fun allowCamera(pkg: String): Boolean =
        exec("appops set $pkg CAMERA allow").success

    /** Block microphone access for app */
    suspend fun blockMicrophone(pkg: String): Boolean =
        exec("appops set $pkg RECORD_AUDIO deny").success

    /** Allow microphone access */
    suspend fun allowMicrophone(pkg: String): Boolean =
        exec("appops set $pkg RECORD_AUDIO allow").success

    /** Block location access for app */
    suspend fun blockLocation(pkg: String): Boolean {
        exec("appops set $pkg COARSE_LOCATION deny")
        return exec("appops set $pkg FINE_LOCATION deny").success
    }

    /** Allow location access for app */
    suspend fun allowLocation(pkg: String): Boolean {
        exec("appops set $pkg COARSE_LOCATION allow")
        return exec("appops set $pkg FINE_LOCATION allow").success
    }

    /** Get app ops (permissions usage) for package */
    suspend fun getAppOps(pkg: String): String {
        val r = exec("appops get $pkg")
        return if (r.success) r.stdout else ""
    }

    /** Get recent camera usage */
    suspend fun getRecentCameraUsage(): List<AppOpsEntry> = parseAppOps("CAMERA")

    /** Get recent microphone usage */
    suspend fun getRecentMicUsage(): List<AppOpsEntry> = parseAppOps("RECORD_AUDIO")

    /** Get recent location usage */
    suspend fun getRecentLocationUsage(): List<AppOpsEntry> = parseAppOps("FINE_LOCATION")

    /** Get recent clipboard reads */
    suspend fun getRecentClipboardReads(): List<AppOpsEntry> = parseAppOps("READ_CLIPBOARD")

    private suspend fun parseAppOps(op: String): List<AppOpsEntry> = withContext(Dispatchers.IO) {
        val r = exec("dumpsys appops 2>/dev/null | grep -B2 '$op' | grep 'Package\\|$op'")
        if (!r.success) return@withContext emptyList()
        val entries = mutableListOf<AppOpsEntry>()
        var currentPkg = ""
        r.stdout.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("Package:")) {
                currentPkg = trimmed.removePrefix("Package:").trim().substringBefore(" ")
            } else if (trimmed.contains(op) && currentPkg.isNotBlank()) {
                val mode = when {
                    trimmed.contains("allow") -> "allow"
                    trimmed.contains("deny") -> "deny"
                    trimmed.contains("ignore") -> "ignore"
                    else -> "default"
                }
                val timeMatch = Regex("time=([^;]+)").find(trimmed)
                entries.add(AppOpsEntry(currentPkg, op, mode, timeMatch?.groupValues?.get(1) ?: ""))
            }
        }
        entries
    }

    /** Revoke all dangerous permissions from app */
    suspend fun revokeAllPermissions(pkg: String): String {
        val r = exec("dumpsys package $pkg | grep 'granted=true' | awk '{print \$1}' | sed 's/://g'")
        if (!r.success) return ""
        val perms = r.stdout.lines().filter { it.startsWith("android.permission.") }
        var revoked = 0
        perms.forEach { perm ->
            if (exec("pm revoke $pkg $perm 2>/dev/null").success) revoked++
        }
        return "$revoked/${perms.size}"
    }

    /** Get list of granted permissions for app */
    suspend fun getGrantedPermissions(pkg: String): List<String> {
        val r = exec("dumpsys package $pkg | grep 'granted=true' | awk '{print \$1}' | sed 's/://g'")
        return if (r.success) r.stdout.lines().filter { it.isNotBlank() } else emptyList()
    }

    // ═══════════════════════════════════
    // Network
    // ═══════════════════════════════════

    /** Get active network connections */
    suspend fun getNetstat(): String {
        val r = exec("netstat -tunp 2>/dev/null || cat /proc/net/tcp /proc/net/tcp6 2>/dev/null")
        return if (r.success) r.stdout else ""
    }

    /** Block internet for specific app using iptables */
    suspend fun blockInternet(uid: Int): Boolean {
        val r1 = exec("iptables -A OUTPUT -m owner --uid-owner $uid -j DROP 2>/dev/null")
        val r2 = exec("ip6tables -A OUTPUT -m owner --uid-owner $uid -j DROP 2>/dev/null")
        return r1.success || r2.success
    }

    /** Unblock internet for specific app */
    suspend fun unblockInternet(uid: Int): Boolean {
        val r1 = exec("iptables -D OUTPUT -m owner --uid-owner $uid -j DROP 2>/dev/null")
        val r2 = exec("ip6tables -D OUTPUT -m owner --uid-owner $uid -j DROP 2>/dev/null")
        return r1.success || r2.success
    }

    /** Get UID for package */
    suspend fun getAppUid(pkg: String): Int {
        val r = exec("dumpsys package $pkg | grep userId= | head -1")
        return Regex("userId=(\\d+)").find(r.stdout)?.groupValues?.get(1)?.toIntOrNull() ?: -1
    }

    /** Get current iptables rules */
    suspend fun getIptablesRules(): String {
        val r = exec("iptables -L OUTPUT -n -v 2>/dev/null")
        return if (r.success) r.stdout else ""
    }

    /** Flush all custom iptables rules */
    suspend fun flushIptables(): Boolean {
        exec("iptables -F OUTPUT 2>/dev/null")
        return exec("ip6tables -F OUTPUT 2>/dev/null").success
    }

    /** Set DNS servers */
    suspend fun setDns(dns1: String, dns2: String = ""): Boolean {
        exec("setprop net.dns1 $dns1")
        if (dns2.isNotBlank()) exec("setprop net.dns2 $dns2")
        return exec("ndc resolver setnetdns wlan0 '' $dns1 ${dns2.ifBlank { "" }} 2>/dev/null").success
    }

    /** Get current DNS */
    suspend fun getDns(): String {
        val r = exec("getprop net.dns1 && getprop net.dns2")
        return r.stdout.trim()
    }

    /** Get all network interfaces info */
    suspend fun getNetworkInfo(): String {
        val r = exec("ip addr show 2>/dev/null || ifconfig 2>/dev/null")
        return if (r.success) r.stdout else ""
    }

    /** Ping host */
    suspend fun ping(host: String, count: Int = 4): String {
        val r = exec("ping -c $count -W 2 $host 2>&1")
        return r.stdout
    }

    /** Get routing table */
    suspend fun getRoutes(): String {
        val r = exec("ip route show 2>/dev/null || route -n 2>/dev/null")
        return if (r.success) r.stdout else ""
    }

    // ═══════════════════════════════════
    // Battery & Performance
    // ═══════════════════════════════════

    /** Force Doze mode for specific app */
    suspend fun forceDoze(pkg: String): Boolean =
        exec("dumpsys deviceidle force-idle 2>/dev/null; am set-inactive $pkg true").success

    /** Remove app from Doze whitelist */
    suspend fun removeDozeWhitelist(pkg: String): Boolean =
        exec("dumpsys deviceidle whitelist -$pkg 2>/dev/null").success

    /** Add app to Doze whitelist */
    suspend fun addDozeWhitelist(pkg: String): Boolean =
        exec("dumpsys deviceidle whitelist +$pkg 2>/dev/null").success

    /** Get Doze whitelist */
    suspend fun getDozeWhitelist(): String {
        val r = exec("dumpsys deviceidle whitelist 2>/dev/null")
        return if (r.success) r.stdout else ""
    }

    /** Kill all background processes */
    suspend fun killAllBackground(): Int {
        val r = exec("am kill-all 2>/dev/null")
        return if (r.success) 1 else 0
    }

    /** Kill background process by package */
    suspend fun killBackground(pkg: String): Boolean =
        exec("am kill $pkg 2>/dev/null").success

    /** Get detailed battery stats per app (top consumers) */
    suspend fun getBatteryConsumers(): String {
        val r = exec("dumpsys batterystats --charged 2>/dev/null | grep 'Uid\\|Estimated' | head -40")
        return if (r.success) r.stdout else ""
    }

    /** Get battery temperature */
    suspend fun getBatteryTemp(): String {
        val r = exec("dumpsys battery | grep temperature")
        val temp = Regex("(\\d+)").find(r.stdout)?.value?.toIntOrNull()
        return if (temp != null) "${temp / 10.0}°C" else "?"
    }

    /** Get battery health */
    suspend fun getBatteryHealth(): String {
        val r = exec("dumpsys battery | grep health")
        return when {
            r.stdout.contains("2") -> "Good"
            r.stdout.contains("3") -> "Overheat"
            r.stdout.contains("4") -> "Dead"
            r.stdout.contains("5") -> "Over voltage"
            r.stdout.contains("6") -> "Unspecified failure"
            r.stdout.contains("7") -> "Cold"
            else -> r.stdout.trim()
        }
    }

    /** Get battery cycle count (if supported) */
    suspend fun getBatteryCycles(): String {
        val r = exec("cat /sys/class/power_supply/battery/cycle_count 2>/dev/null || echo N/A")
        return r.stdout.trim()
    }

    /** Set max charging level (if kernel supports) */
    suspend fun setChargeLimit(percent: Int): Boolean {
        // Different paths for different devices
        val paths = listOf(
            "/sys/class/power_supply/battery/charge_control_limit",
            "/sys/class/power_supply/battery/charging_enabled",
            "/sys/devices/platform/battery/charge_limit"
        )
        for (path in paths) {
            if (exec("test -f $path && echo Y").stdout.contains("Y")) {
                return exec("echo $percent > $path").success
            }
        }
        return false
    }

    /** Get wakelock stats */
    suspend fun getWakelocks(): String {
        val r = exec("dumpsys power | grep 'Wake Locks' -A 20 2>/dev/null")
        return if (r.success) r.stdout else ""
    }

    // ═══════════════════════════════════
    // Extra File Operations
    // ═══════════════════════════════════

    /** Clear Dalvik/ART cache */
    suspend fun clearDalvikCache(): Boolean =
        exec("pm art clear-app-profiles 2>/dev/null; rm -rf /data/dalvik-cache/* 2>/dev/null").success

    /** Force media scanner rescan */
    suspend fun rescanMedia(): Boolean =
        exec("am broadcast -a android.intent.action.MEDIA_MOUNTED -d file:///storage/emulated/0 2>/dev/null || am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///storage/emulated/0").success

    /** Get saved Wi-Fi passwords (Android <10) */
    suspend fun getWifiPasswords(): String {
        val r = exec("cat /data/misc/wifi/wpa_supplicant.conf 2>/dev/null || cat /data/misc/apexdata/com.android.wifi/WifiConfigStore.xml 2>/dev/null")
        return if (r.success) r.stdout else "Access denied or not available"
    }

    /** Get total internal storage usage */
    suspend fun getStorageUsage(): String {
        val r = exec("df -h /data 2>/dev/null")
        return if (r.success) r.stdout else ""
    }

    /** Get top 10 largest files on device */
    suspend fun getLargestFiles(): String {
        val r = exec("find /storage/emulated/0 -type f -printf '%s %p\\n' 2>/dev/null | sort -rn | head -10")
        return if (r.success) r.stdout else ""
    }

    /** Empty all app caches at once */
    suspend fun clearAllCaches(): Boolean =
        exec("pm trim-caches 999999999999 2>/dev/null").success

    // ═══════════════════════════════════
    // Data models
    // ═══════════════════════════════════

    data class AppOpsEntry(val pkg: String, val op: String, val mode: String, val lastAccess: String)

    // ═══════════════════════════════════
    // Helpers
    // ═══════════════════════════════════

    private fun parseListLine(line: String, parentPath: String): ShizukuFileItem? {
        val trimmed = line.trim()
        if (trimmed.isBlank() || trimmed.startsWith("total")) return null
        val parts = trimmed.split("\\s+".toRegex())
        if (parts.size < 7) return null

        val perms = parts[0]
        val isDir = perms.startsWith("d") || perms.startsWith("l")
        val size = parts.getOrNull(4)?.toLongOrNull() ?: 0L

        // Name can start at index 7 or 8 depending on ls format
        // Try to find the name by looking for date patterns
        val nameStartIdx = findNameIndex(parts)
        if (nameStartIdx < 0 || nameStartIdx >= parts.size) return null

        val name = parts.drop(nameStartIdx).joinToString(" ")
            .let { if (it.contains(" -> ")) it.substringBefore(" -> ") else it } // handle symlinks
        if (name.isBlank() || name == "." || name == "..") return null

        return ShizukuFileItem(name, "$parentPath/$name", isDir, size)
    }

    /** Find where filename starts in ls -la output by detecting date/time columns */
    private fun findNameIndex(parts: List<String>): Int {
        // Common ls -la formats:
        // drwxrwx--x 3 system ext_data_rw 4096 2024-01-15 12:30 dirname
        // drwxrwx--x 3 system ext_data_rw 4096 Jan 15 12:30 dirname
        for (i in 5 until parts.size) {
            // Check for time pattern HH:MM or HH:MM:SS
            if (parts[i].matches(Regex("\\d{1,2}:\\d{2}(:\\d{2})?"))) {
                return i + 1
            }
            // Check for year pattern (4 digits alone)
            if (parts[i].matches(Regex("\\d{4}")) && i > 5) {
                return i + 1
            }
        }
        // Fallback: assume index 7 (standard format)
        return if (parts.size > 7) 7 else -1
    }

    data class ShellResult(val success: Boolean, val stdout: String, val stderr: String)
    data class ShizukuFileItem(val name: String, val path: String, val isDirectory: Boolean, val size: Long)
    data class ProcessInfo(val pid: Int, val memKb: Long, val name: String)
}
