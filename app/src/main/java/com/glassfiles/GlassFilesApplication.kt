package com.glassfiles

import android.app.Application
import com.glassfiles.data.github.GitHubManager
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
