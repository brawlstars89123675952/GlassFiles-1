package com.glassfiles.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Notification channels for the GitHub notifications subsystem.
 *
 * Channels are the user-facing toggles in system Settings → Apps → GlassFiles
 * → Notifications. We register a small set of categories grouped by intent
 * (CI is loud, subscriptions are silent, etc.). The user can override sound
 * and importance per channel from system settings.
 */
object NotificationChannels {

    const val CHANNEL_CI = "gh_ci"
    const val CHANNEL_MENTIONS = "gh_mentions"
    const val CHANNEL_REVIEWS = "gh_reviews"
    const val CHANNEL_ISSUES_PRS = "gh_issues_prs"
    const val CHANNEL_SECURITY = "gh_security"
    const val CHANNEL_ACTIVITY = "gh_activity"
    const val CHANNEL_SUBSCRIPTIONS = "gh_subscriptions"

    /** Group all GH channels under one Settings header. */
    const val GROUP_GITHUB = "gh_group"

    fun registerAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        try {
            nm.createNotificationChannelGroup(
                android.app.NotificationChannelGroup(GROUP_GITHUB, "GitHub")
            )
        } catch (_: Throwable) {}

        val channels = listOf(
            channel(CHANNEL_CI, "CI builds",
                "Workflow runs (success / failure) for repositories you watch.",
                NotificationManager.IMPORTANCE_HIGH),
            channel(CHANNEL_MENTIONS, "Mentions",
                "Direct @mentions and team mentions.",
                NotificationManager.IMPORTANCE_HIGH),
            channel(CHANNEL_REVIEWS, "Review requests",
                "Pull request reviews assigned to you.",
                NotificationManager.IMPORTANCE_HIGH),
            channel(CHANNEL_SECURITY, "Security alerts",
                "Vulnerability and security advisories.",
                NotificationManager.IMPORTANCE_HIGH),
            channel(CHANNEL_ISSUES_PRS, "Issues & pull requests",
                "Comments, state changes, and assignments on issues and PRs.",
                NotificationManager.IMPORTANCE_DEFAULT),
            channel(CHANNEL_ACTIVITY, "Other activity",
                "Authoring, invitations and miscellaneous activity.",
                NotificationManager.IMPORTANCE_LOW),
            channel(CHANNEL_SUBSCRIPTIONS, "Subscribed repos",
                "Activity from repositories you have manually subscribed to.",
                NotificationManager.IMPORTANCE_LOW)
        )

        channels.forEach { nm.createNotificationChannel(it) }
    }

    private fun channel(id: String, name: String, desc: String, importance: Int): NotificationChannel {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Unreachable, but compiler needs it.
            throw IllegalStateException("requires API 26+")
        }
        return NotificationChannel(id, name, importance).apply {
            description = desc
            group = GROUP_GITHUB
            // Disable channel-level badge for the silent ones to avoid noise.
            setShowBadge(importance >= NotificationManager.IMPORTANCE_DEFAULT)
        }
    }

    /** Map a GitHub `reason` to a channel id. */
    fun channelForReason(reason: String): String = when (reason) {
        "ci_activity" -> CHANNEL_CI
        "mention", "team_mention" -> CHANNEL_MENTIONS
        "review_requested" -> CHANNEL_REVIEWS
        "comment", "state_change", "assign", "author" -> CHANNEL_ISSUES_PRS
        "security_alert" -> CHANNEL_SECURITY
        "subscribed", "manual", "release" -> CHANNEL_SUBSCRIPTIONS
        else -> CHANNEL_ACTIVITY
    }
}
