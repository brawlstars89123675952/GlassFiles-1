package com.glassfiles.security

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.util.Log
import com.glassfiles.BuildConfig
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

object SecurityManager {

    private const val TAG = "GF_SM"

    // Store signature hash on first run, verify on subsequent runs
    private const val PREFS = "gf_sec"
    private const val KEY_SIG = "s_h"
    private const val KEY_DEX = "d_h"

    data class SecurityResult(
        val signatureValid: Boolean = true,
        val integrityValid: Boolean = true,
        val debuggerDetected: Boolean = false,
        val hookDetected: Boolean = false,
        val tampered: Boolean = false
    ) {
        val isSecure: Boolean get() = signatureValid && integrityValid && !debuggerDetected && !hookDetected && !tampered
    }

    /**
     * Run all security checks.
     * Call in onCreate BEFORE showing any content.
     */
    fun performChecks(context: Context): SecurityResult {
        return try {
            val sigValid = verifySignature(context)
            val intValid = verifyIntegrity(context)
            val debugger = isDebuggerAttached()
            val hooks = detectHooks()
            val tampered = isAppTampered(context)

            // Native checks
            var nativeThreats = 0
            try { nativeThreats = NativeSecurity.nativeSecurityCheck() } catch (_: Exception) {}

            val result = SecurityResult(
                signatureValid = sigValid,
                integrityValid = intValid,
                debuggerDetected = debugger || (nativeThreats and 0x01 != 0),
                hookDetected = hooks || (nativeThreats and 0x02 != 0) || (nativeThreats and 0x04 != 0),
                tampered = tampered
            )

            if (!result.isSecure) {
                Log.w(TAG, "Security: sig=$sigValid int=$intValid dbg=$debugger hook=$hooks tamper=$tampered native=$nativeThreats")
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Security check error: ${e.message}")
            SecurityResult() // Default = secure (don't crash on check failure)
        }
    }

    // ═══════════════════════════════════
    // Signature verification
    // ═══════════════════════════════════

    @SuppressLint("PackageManagerGetSignatures")
    private fun verifySignature(context: Context): Boolean {
        return try {
            val sig = getSignatureHash(context)
            if (sig.isBlank()) return false

            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val stored = prefs.getString(KEY_SIG, null)

            if (stored == null) {
                // First run — store the hash
                prefs.edit().putString(KEY_SIG, sig).apply()
                // Also try native verification
                val sigBytes = getSignatureBytes(context)
                if (sigBytes != null) {
                    try { NativeSecurity.nativeVerifySignature(sigBytes) } catch (_: Exception) {}
                }
                true
            } else {
                // Subsequent runs — compare
                stored == sig
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sig check: ${e.message}")
            true // Don't block on error
        }
    }

    @SuppressLint("PackageManagerGetSignatures")
    private fun getSignatureHash(context: Context): String {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners ?: return ""
        } else {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info.signatures ?: return ""
        }

        if (signatures.isEmpty()) return ""

        val md = MessageDigest.getInstance("SHA-256")
        md.update(signatures[0].toByteArray())
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    @SuppressLint("PackageManagerGetSignatures")
    private fun getSignatureBytes(context: Context): ByteArray? {
        return try {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures
            }
            signatures?.firstOrNull()?.toByteArray()
        } catch (_: Exception) { null }
    }

    // ═══════════════════════════════════
    // DEX integrity check
    // ═══════════════════════════════════

    private fun verifyIntegrity(context: Context): Boolean {
        return try {
            val apkPath = context.applicationInfo.sourceDir
            val dexHash = computeDexHash(apkPath)
            if (dexHash.isBlank()) return true

            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val stored = prefs.getString(KEY_DEX, null)

            if (stored == null) {
                prefs.edit().putString(KEY_DEX, dexHash).apply()
                true
            } else {
                stored == dexHash
            }
        } catch (e: Exception) {
            Log.e(TAG, "Integrity: ${e.message}")
            true
        }
    }

    private fun computeDexHash(apkPath: String): String {
        return try {
            val zipFile = ZipFile(apkPath)
            val entry = zipFile.getEntry("classes.dex") ?: return ""
            val md = MessageDigest.getInstance("SHA-256")
            zipFile.getInputStream(entry).use { stream ->
                val buf = ByteArray(8192)
                var n: Int
                while (stream.read(buf).also { n = it } != -1) {
                    md.update(buf, 0, n)
                }
            }
            zipFile.close()
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "DEX hash: ${e.message}")
            ""
        }
    }

    // ═══════════════════════════════════
    // Debugger detection
    // ═══════════════════════════════════

    private fun isDebuggerAttached(): Boolean {
        if (Debug.isDebuggerConnected()) return true
        if (Debug.waitingForDebugger()) return true

        // Check TracerPid
        try {
            val status = File("/proc/self/status").readText()
            val tracer = Regex("TracerPid:\\s+(\\d+)").find(status)
            if (tracer != null && tracer.groupValues[1] != "0") return true
        } catch (_: Exception) {}

        return false
    }

    // ═══════════════════════════════════
    // Hook detection
    // ═══════════════════════════════════

    private fun detectHooks(): Boolean {
        // Check for Xposed
        try {
            Class.forName("de.robv.android.xposed.XposedBridge")
            return true
        } catch (_: ClassNotFoundException) {}

        // Check for EdXposed/LSPosed
        try {
            Class.forName("org.lsposed.lspd.core.Main")
            return true
        } catch (_: ClassNotFoundException) {}

        // Check for Substrate/Cydia
        try {
            Class.forName("com.saurik.substrate.MS")
            return true
        } catch (_: ClassNotFoundException) {}

        // Check for Frida gadget in loaded libraries
        try {
            val maps = File("/proc/self/maps").readText()
            if (maps.contains("frida") || maps.contains("gadget")) return true
        } catch (_: Exception) {}

        // Native hook check
        try {
            if (!NativeSecurity.nativeCheckHooks()) return true
        } catch (_: Exception) {}

        return false
    }

    // ═══════════════════════════════════
    // Tamper detection
    // ═══════════════════════════════════

    private fun isAppTampered(context: Context): Boolean {
        // Check installer — if not from Play Store or direct install, might be modified
        // (Skip this check since app is distributed via GitHub)

        // Check if app is debuggable (should be false in release)
        val appInfo = context.applicationInfo
        if (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            // Debuggable in release = tampered
            if (!BuildConfig.DEBUG) return true
        }

        // Check APK path — should be in /data/app
        val apkPath = appInfo.sourceDir
        if (!apkPath.startsWith("/data/app")) {
            // APK in unusual location
            Log.w(TAG, "APK path: $apkPath")
        }

        // Native integrity check
        try {
            if (!NativeSecurity.nativeCheckIntegrity(apkPath)) return true
        } catch (_: Exception) {}

        return false
    }

    /**
     * Reset stored hashes — call after app update
     */
    fun resetHashes(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
