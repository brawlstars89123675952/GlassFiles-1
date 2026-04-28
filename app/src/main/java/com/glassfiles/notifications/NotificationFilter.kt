package com.glassfiles.notifications

import com.glassfiles.data.github.GHNotification
import java.util.Calendar

/**
 * Pure decision: should this notification be shown to the user, given the
 * current preferences snapshot?
 *
 * No I/O, no SharedPreferences access — caller passes a snapshot captured
 * at the start of a poll batch.
 */
object NotificationFilter {

    fun shouldShow(
        notification: GHNotification,
        prefs: NotificationsPreferences.Snapshot,
        now: Calendar = Calendar.getInstance()
    ): Boolean {
        // 1. Master toggle
        if (!prefs.masterEnabled) return false

        // 2. Quiet hours
        if (prefs.quietHoursEnabled) {
            val hour = now.get(Calendar.HOUR_OF_DAY)
            if (NotificationsPreferences.hourInRange(hour, prefs.quietStart, prefs.quietEnd)) {
                return false
            }
        }

        // 3. Per-reason toggle
        if (!prefs.typeEnabled(notification.reason)) return false

        // 4. Repo filter
        when (prefs.repoFilterMode) {
            NotificationsPreferences.REPO_MODE_OWNED -> {
                val owner = notification.repoName.substringBefore('/', "")
                if (owner.isBlank() || prefs.userLogin.isBlank()) return false
                if (!owner.equals(prefs.userLogin, ignoreCase = true)) return false
            }
            NotificationsPreferences.REPO_MODE_CUSTOM -> {
                if (notification.repoName !in prefs.customRepos) return false
            }
            NotificationsPreferences.REPO_MODE_WATCHING -> {
                // Watching list is determined by GitHub itself for /notifications
                // (only repos the user is subscribed to send notifications),
                // so this is effectively the same as "all".
            }
            // REPO_MODE_ALL — no filtering
        }

        // 5. Already shown locally?
        if (notification.id in prefs.seenIds) return false

        return true
    }
}
