package com.glassfiles.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.glassfiles.MainActivity
import com.glassfiles.R
import com.glassfiles.data.github.GHNotification

/**
 * Builds and posts Android notifications for GitHub items.
 *
 * The actual scheduling/polling is in [NotificationsWorker]; this object is
 * just the "show me on the screen" layer.
 */
object NotificationsManager {

    /** Group key used so Android collapses many GH notifications. */
    const val GROUP_KEY = "com.glassfiles.GITHUB_NOTIFICATIONS"

    /** Extras carried by content intents (consumed by MainActivity). */
    const val EXTRA_ACTION = "notification_action"
    const val EXTRA_THREAD_ID = "thread_id"
    const val EXTRA_SUBJECT_URL = "subject_url"
    const val EXTRA_HTML_URL = "html_url"
    const val EXTRA_SUBJECT_TYPE = "subject_type"
    const val EXTRA_REPO = "repo_full_name"
    const val ACTION_OPEN_THREAD = "open_thread"

    fun show(context: Context, n: GHNotification): Boolean {
        val title = formatTitle(n)
        val body = formatBody(n)
        AppNotificationInboxStore.add(
            context,
            AppNotificationEvent(
                source = AppNotificationPreferences.SOURCE_GITHUB,
                type = n.reason.ifBlank { "github" },
                title = title,
                body = body,
                externalId = "github:${n.id}",
                target = AppNotificationTarget(AppNotificationTarget.DEST_GITHUB, n.repoName, n.subjectType),
                important = n.reason in setOf("mention", "team_mention", "review_requested", "security_alert", "ci_activity"),
                showSystem = false,
                createdAt = System.currentTimeMillis()
            )
        )

        if (!AppNotificationPreferences.isSourceEnabled(context, AppNotificationPreferences.SOURCE_GITHUB)) return false
        if (!hasPostPermission(context)) return false

        val channel = NotificationChannels.channelForReason(n.reason)

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(smallIconRes())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(buildOpenIntent(context, n))
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .addAction(
                R.drawable.ic_check,
                "Mark as read",
                buildMarkReadIntent(context, n.id)
            )

        try {
            NotificationManagerCompat.from(context).notify(notifIdFor(n.id), builder.build())
            return true
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied at runtime — silently noop.
        }
        return false
    }

    fun cancel(context: Context, notificationId: String) {
        try {
            NotificationManagerCompat.from(context).cancel(notifIdFor(notificationId))
        } catch (_: Throwable) {}
    }

    // ── Internal helpers ──────────────────────────────────────────────

    private fun notifIdFor(threadId: String): Int = threadId.hashCode()

    private fun smallIconRes(): Int = R.drawable.ic_notification_glassfiles

    private fun hasPostPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return try {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) { false }
    }

    private fun buildOpenIntent(context: Context, n: GHNotification): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_THREAD
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ACTION, ACTION_OPEN_THREAD)
            putExtra(EXTRA_THREAD_ID, n.id)
            putExtra(EXTRA_SUBJECT_URL, n.subjectUrl)
            putExtra(EXTRA_HTML_URL, n.htmlUrl)
            putExtra(EXTRA_SUBJECT_TYPE, n.subjectType)
            putExtra(EXTRA_REPO, n.repoName)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getActivity(context, notifIdFor(n.id), intent, flags)
    }

    private fun buildMarkReadIntent(context: Context, threadId: String): PendingIntent {
        val intent = Intent(context, MarkReadReceiver::class.java).apply {
            action = MarkReadReceiver.ACTION
            putExtra(MarkReadReceiver.EXTRA_THREAD_ID, threadId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getBroadcast(context, notifIdFor(threadId), intent, flags)
    }

    internal fun formatTitle(n: GHNotification): String {
        val t = n.title.lowercase()
        return when (n.reason) {
            "ci_activity" -> when {
                "fail" in t -> "Build failed"
                "success" in t || "passed" in t -> "Build succeeded"
                else -> "Build update — ${n.repoName}"
            }
            "mention", "team_mention" -> "Mentioned in ${n.repoName}"
            "review_requested" -> "Review requested"
            "security_alert" -> "Security alert"
            "comment" -> "New comment"
            "assign" -> "Assigned to you"
            "state_change" -> "Status changed"
            "author" -> "Activity on your thread"
            "invitation" -> "Repository invitation"
            "release" -> "New release"
            "subscribed", "manual" -> n.repoName.ifBlank { "GitHub activity" }
            else -> n.title.ifBlank { n.repoName.ifBlank { "GitHub" } }
        }
    }

    internal fun formatBody(n: GHNotification): String = buildString {
        if (n.title.isNotBlank()) append(n.title).append('\n')
        if (n.repoName.isNotBlank()) append(n.repoName)
        if (n.subjectType.isNotBlank()) {
            if (isNotEmpty()) append(" · ")
            append(n.subjectType)
        }
    }.trim().ifEmpty { "GitHub notification" }
}
