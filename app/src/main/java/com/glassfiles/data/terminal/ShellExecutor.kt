package com.glassfiles.data.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.widget.Toast
import com.glassfiles.notifications.AppNotifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Locale

data class ShellResult(val output: String, val error: String, val exitCode: Int)

class ShellExecutor(
    initialDir: String = "/storage/emulated/0",
    private val termuxEnv: TermuxBootstrap? = null,
    private val context: Context? = null
) {
    var currentDir: String = initialDir
        private set

    private val environment = mutableMapOf<String, String>()
    private val _history = mutableListOf<String>()
    val history: List<String> get() = _history

    private val canRunLinux: Boolean get() =
        termuxEnv?.isInstalled() == true && termuxEnv.hasProot()

    var onOpenFile: ((String) -> Unit)? = null
    var onStreamLine: ((String, Boolean) -> Unit)? = null
    private var tts: TextToSpeech? = null
    private var currentProcess: Process? = null

    init {
        environment["HOME"] = "/storage/emulated/0"
        environment["PATH"] = "/system/bin:/system/xbin"
        environment["TERM"] = "xterm-256color"
        environment["PWD"] = initialDir
        termuxEnv?.let {
            environment["PROOT_TMP_DIR"] = it.tmpDir.absolutePath
        }
    }

    fun refreshEnvironment() {}
    fun cancelCurrentProcess() { currentProcess?.destroyForcibly(); currentProcess = null }

    suspend fun executeStreaming(command: String): ShellResult {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return ShellResult("", "", 0)
        _history.add(trimmed)

        val builtin = handleBuiltin(trimmed)
        if (builtin != null) return builtin

        return withContext(Dispatchers.IO) {
            try {
                val args: Array<String> =
                    if (canRunLinux) {
                        val prootCmd = termuxEnv!!.buildProotCommand(trimmed)
                        arrayOf("/system/bin/sh", "-c", prootCmd)
                    } else {
                        arrayOf("/system/bin/sh", "-c", trimmed)
                    }

                val pb = ProcessBuilder(*args)
                pb.directory(File(currentDir))
                pb.redirectErrorStream(false)
                pb.environment().clear()
                pb.environment().putAll(environment)

                val process = pb.start()
                currentProcess = process

                val t1 = Thread {
                    try {
                        BufferedReader(InputStreamReader(process.inputStream)).forEachLine {
                            onStreamLine?.invoke(it, false)
                        }
                    } catch (_: Exception) {}
                }
                val t2 = Thread {
                    try {
                        BufferedReader(InputStreamReader(process.errorStream)).forEachLine {
                            onStreamLine?.invoke(it, true)
                        }
                    } catch (_: Exception) {}
                }
                t1.start(); t2.start()

                val exit = process.waitFor()
                t1.join(3000); t2.join(3000)
                currentProcess = null
                ShellResult("", "", exit)
            } catch (e: Exception) {
                currentProcess = null
                ShellResult("", "Error: ${e.message}", 1)
            }
        }
    }

    suspend fun execute(command: String): ShellResult = executeStreaming(command)

    fun tabComplete(input: String): List<String> {
        val parts = input.trimEnd().split(" ")
        if (parts.size <= 1) {
            val p = parts[0]
            val cmds = listOf(
                "cd","pwd","export","clear","help","exit","history","clip-copy","clip-paste","notify","open","vibrate","battery","tts-speak","sensor-list",
                "apt","apt-get","python3","pip","git","node","npm","ssh","wget","curl","vim","nano","cat","ls","cp","mv","rm","mkdir","touch","chmod","find","grep","echo","head","tail","wc"
            )
            return cmds.filter { it.startsWith(p, true) }.sorted()
        }
        val lp = parts.last()
        val dir: File
        val pfx: String
        if (lp.contains("/")) {
            val pp = lp.substringBeforeLast("/")
            pfx = lp.substringAfterLast("/")
            dir = if (pp.startsWith("/")) File(pp) else File(currentDir, pp)
        } else {
            pfx = lp
            dir = File(currentDir)
        }
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { !it.name.startsWith(".") && it.name.startsWith(pfx, true) }
            ?.map { if (it.isDirectory) "${it.name}/" else it.name }
            ?.sorted()
            ?: emptyList()
    }

    fun applyCompletion(input: String, completion: String): String {
        val parts = input.trimEnd().split(" ")
        return if (parts.size <= 1) "$completion " else {
            val lp = parts.last()
            val bp = if (lp.contains("/")) lp.substringBeforeLast("/") + "/" else ""
            (parts.dropLast(1) + "$bp$completion").joinToString(" ") + if (!completion.endsWith("/")) " " else ""
        }
    }

    private fun handleBuiltin(command: String): ShellResult? {
        val parts = command.split(" ", limit = 2)
        val cmd = parts[0]
        val args = parts.getOrNull(1)?.trim() ?: ""

        return when (cmd) {
            "cd" -> {
                val home = environment["HOME"] ?: "/storage/emulated/0"
                val target = when {
                    args.isEmpty() || args == "~" -> home
                    args.startsWith("/") -> args
                    args == ".." -> File(currentDir).parent ?: currentDir
                    args == "-" -> environment["OLDPWD"] ?: currentDir
                    args.startsWith("~/") -> home + args.substring(1)
                    else -> "$currentDir/$args"
                }
                val dir = File(target)
                if (dir.exists() && dir.isDirectory) {
                    environment["OLDPWD"] = currentDir
                    currentDir = dir.canonicalPath
                    environment["PWD"] = currentDir
                    ShellResult("", "", 0)
                } else ShellResult("", "cd: No such directory", 1)
            }
            "export" -> if (args.contains("=")) {
                val (k, v) = args.split("=", limit = 2)
                environment[k] = v
                ShellResult("", "", 0)
            } else ShellResult(environment.entries.joinToString("\n") { "${it.key}=${it.value}" }, "", 0)
            "pwd" -> ShellResult(currentDir, "", 0)
            "clear" -> ShellResult("\u000C", "", 0)
            "exit" -> ShellResult("EXIT", "", 0)
            "history" -> ShellResult(_history.mapIndexed { i, c -> "  ${i + 1}  $c" }.joinToString("\n"), "", 0)
            "clip-copy" -> {
                if (context == null) return ShellResult("", "No context", 1)
                if (args.isEmpty()) return ShellResult("", "clip-copy <text>", 1)
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("t", args))
                ShellResult("📋 Copied", "", 0)
            }
            "clip-paste" -> {
                if (context == null) return ShellResult("", "No context", 1)
                ShellResult(
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .primaryClip?.getItemAt(0)?.text?.toString() ?: "",
                    "",
                    0
                )
            }
            "notify" -> {
                if (context == null) return ShellResult("", "No context", 1)
                if (args.isEmpty()) return ShellResult("", "notify <msg>", 1)
                Toast.makeText(context, args, Toast.LENGTH_LONG).show()
                AppNotifications.notifyTerminal(context, args)
                ShellResult("✅", "", 0)
            }
            "open" -> {
                if (args.isEmpty()) return ShellResult("", "open <file>", 1)
                val f = if (args.startsWith("/")) File(args) else File(currentDir, args)
                if (!f.exists()) return ShellResult("", "Not found", 1)
                onOpenFile?.invoke(f.absolutePath)
                ShellResult("Opening: ${f.name}", "", 0)
            }
            "vibrate" -> {
                if (context == null) return ShellResult("", "No context", 1)
                val ms = args.toLongOrNull() ?: 200L
                val v = if (Build.VERSION.SDK_INT >= 31)
                    (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                else
                    @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
                if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                else @Suppress("DEPRECATION") v.vibrate(ms)
                ShellResult("📳 ${ms}ms", "", 0)
            }
            "battery" -> {
                if (context == null) return ShellResult("", "No context", 1)
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                ShellResult("🔋 ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%${if (bm.isCharging) " ⚡" else ""}", "", 0)
            }
            "tts-speak" -> {
                if (context == null) return ShellResult("", "No context", 1)
                if (args.isEmpty()) return ShellResult("", "tts-speak <text>", 1)
                if (tts == null) {
                    tts = TextToSpeech(context) { s ->
                        if (s == TextToSpeech.SUCCESS) {
                            tts?.language = Locale("ru")
                            tts?.speak(args, TextToSpeech.QUEUE_FLUSH, null, "t")
                        }
                    }
                } else tts?.speak(args, TextToSpeech.QUEUE_FLUSH, null, "t")
                ShellResult("🔊 $args", "", 0)
            }
            "sensor-list" -> {
                if (context == null) return ShellResult("", "No context", 1)
                ShellResult(
                    (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager)
                        .getSensorList(Sensor.TYPE_ALL)
                        .joinToString("\n") { "  ${it.type}: ${it.name}" },
                    "",
                    0
                )
            }
            "help" -> ShellResult("""
                |Glass Files Terminal v2.0
                |${if (canRunLinux) "✅ Ubuntu Linux (proot)" else "❌ Linux не установлен"}
                |──────────────────────────
                |cd, pwd, ls, cat, grep, find
                |clip-copy/paste, notify, open
                |battery, vibrate, tts-speak, sensor-list
                |${if (canRunLinux) "bun, git, python3, apt install" else "Install Linux via ⚙️"}
                |history, export, clear, exit
            """.trimMargin(), "", 0)
            else -> null
        }
    }

    fun getPrompt(): String {
        val dir = currentDir.replace("/storage/emulated/0", "~")
        return "${if (canRunLinux) "ubuntu" else "sh"}:$dir \$ "
    }
}
