package com.glassfiles.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.glassfiles.MainActivity
import com.glassfiles.R

object AppNotifications {
    const val EXTRA_ACTION = "app_notification_action"
    const val EXTRA_INBOX_ID = "app_notification_id"
    const val EXTRA_DESTINATION = "app_notification_destination"
    const val EXTRA_PATH = "app_notification_path"
    const val EXTRA_EXTRA = "app_notification_extra"
    const val ACTION_OPEN_TARGET = "open_app_notification_target"
    const val GROUP_KEY = "com.glassfiles.APP_NOTIFICATIONS"

    fun post(context: Context, event: AppNotificationEvent) {
        val item = AppNotificationInboxStore.add(context, event)
        if (!event.showSystem) return
        if (!NotificationsPreferences.isEnabled(context)) return
        if (!AppNotificationPreferences.isSourceEnabled(context, event.source)) return
        if (NotificationsPreferences.isInQuietHours(context) && !event.important) return
        if (!hasPostPermission(context)) return

        val builder = NotificationCompat.Builder(context, NotificationChannels.channelForAppSource(event.source))
            .setSmallIcon(R.drawable.ic_notification_glassfiles)
            .setContentTitle(event.title)
            .setContentText(event.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(event.body))
            .setContentIntent(buildOpenIntent(context, item))
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setPriority(if (event.important) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)

        try {
            NotificationManagerCompat.from(context).notify(item.id.hashCode(), builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun notifyFileOperation(context: Context, operation: String, success: Boolean, name: String, path: String = "") {
        post(
            context,
            AppNotificationEvent(
                source = AppNotificationPreferences.SOURCE_FILE_OPS,
                type = if (success) "file_op_success" else "file_op_failed",
                title = if (success) "$operation completed" else "$operation failed",
                body = name,
                externalId = "file:$operation:$name:${System.currentTimeMillis() / 10_000L}",
                target = AppNotificationTarget(
                    destination = if (path.isBlank()) AppNotificationTarget.DEST_HOME else AppNotificationTarget.DEST_PATH,
                    path = path
                ),
                important = !success
            )
        )
    }

    fun notifyStorageWarning(context: Context, freeBytes: Long, totalBytes: Long) {
        post(
            context,
            AppNotificationEvent(
                source = AppNotificationPreferences.SOURCE_STORAGE,
                type = "low_storage",
                title = "Storage is running low",
                body = "${formatBytes(freeBytes)} free of ${formatBytes(totalBytes)}",
                externalId = "low_storage:${System.currentTimeMillis() / (12 * 60 * 60 * 1000L)}",
                target = AppNotificationTarget(AppNotificationTarget.DEST_STORAGE),
                important = true
            )
        )
    }

    fun notifyCrashLog(context: Context, path: String) {
        post(
            context,
            AppNotificationEvent(
                source = AppNotificationPreferences.SOURCE_SECURITY,
                type = "crash_log",
                title = "Crash log captured",
                body = "GlassFiles saved a crash report",
                externalId = "crash:$path",
                target = AppNotificationTarget(AppNotificationTarget.DEST_PATH, path),
                important = true
            )
        )
    }

    fun notifyTerminal(context: Context, message: String) {
        post(
            context,
            AppNotificationEvent(
                source = AppNotificationPreferences.SOURCE_TERMINAL,
                type = "terminal_notify",
                title = "Terminal",
                body = message,
                externalId = "terminal:${message}:${System.currentTimeMillis() / 10_000L}",
                target = AppNotificationTarget(AppNotificationTarget.DEST_TERMINAL)
            )
        )
    }

    private fun buildOpenIntent(context: Context, item: AppNotificationInboxStore.InboxItem): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_TARGET
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ACTION, ACTION_OPEN_TARGET)
            putExtra(EXTRA_INBOX_ID, item.id)
            putExtra(EXTRA_DESTINATION, item.destination)
            putExtra(EXTRA_PATH, item.path)
            putExtra(EXTRA_EXTRA, item.extra)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getActivity(context, item.id.hashCode(), intent, flags)
    }

    private fun hasPostPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun formatBytes(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) String.format("%.1f GB", gb) else "${bytes / (1024L * 1024L)} MB"
    }
}
