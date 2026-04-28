package com.glassfiles

import android.app.Application
import android.os.Environment
import android.os.StatFs
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.notifications.AppNotifications
import com.glassfiles.notifications.NotificationChannels
import com.glassfiles.notifications.NotificationsPreferences
import com.glassfiles.notifications.NotificationsWorker
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GlassFilesApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        initNotifications()
        notifyPendingCrashLog()
        notifyLowStorageIfNeeded()
    }

    private fun initNotifications() {
        try {
            NotificationChannels.registerAll(this)
            if (NotificationsPreferences.isEnabled(this) &&
                GitHubManager.getToken(this).isNotBlank()
            ) {
                NotificationsWorker.enqueue(this)
            }
        } catch (_: Throwable) {
            // Don't let notification init crash the app.
        }
    }

    private fun notifyPendingCrashLog() {
        try {
            val logFile = File(filesDir, "crashes.log")
            if (!logFile.exists()) return
            val prefs = getSharedPreferences("glassfiles_app_notifications", MODE_PRIVATE)
            val lastNotified = prefs.getLong("last_notified_crash_log", 0L)
            val modified = logFile.lastModified()
            if (modified > lastNotified) {
                AppNotifications.notifyCrashLog(this, logFile.absolutePath)
                prefs.edit().putLong("last_notified_crash_log", modified).apply()
            }
        } catch (_: Throwable) {
        }
    }

    private fun notifyLowStorageIfNeeded() {
        try {
            val stat = StatFs(Environment.getDataDirectory().absolutePath)
            val free = stat.availableBytes
            val total = stat.totalBytes
            if (total <= 0L) return
            val low = free < 1L * 1024L * 1024L * 1024L || free.toDouble() / total.toDouble() < 0.10
            if (!low) return

            val prefs = getSharedPreferences("glassfiles_app_notifications", MODE_PRIVATE)
            val bucket = System.currentTimeMillis() / (12 * 60 * 60 * 1000L)
            if (prefs.getLong("last_low_storage_bucket", -1L) == bucket) return
            AppNotifications.notifyStorageWarning(this, free, total)
            prefs.edit().putLong("last_low_storage_bucket", bucket).apply()
        } catch (_: Throwable) {
        }
    }

    private fun installCrashLogger() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stamp = SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss",
                    Locale.US
                ).format(Date())
                val log = buildString {
                    append("\n")
                    append("================================================================\n")
                    append("CRASH at ").append(stamp).append("\n")
                    append("================================================================\n")
                    append("Thread: ").append(thread.name).append("\n")
                    append("Type: ").append(throwable::class.java.name).append("\n")
                    append("Message: ").append(throwable.message ?: "(no message)").append("\n\n")
                    append(throwable.stackTraceToString())
                    append("\n")
                }
                getExternalFilesDir(null)?.let { dir ->
                    File(dir, "crashes.log").appendText(log)
                }
                File(filesDir, "crashes.log").appendText(log)
            } catch (_: Throwable) {
                // Не дать самому логгеру упасть
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}
