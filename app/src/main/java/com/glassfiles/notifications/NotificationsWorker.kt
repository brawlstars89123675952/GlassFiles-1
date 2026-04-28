package com.glassfiles.notifications

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.glassfiles.data.github.GitHubManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class NotificationsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext

        if (!NotificationsPreferences.isEnabled(ctx)) {
            Log.d(TAG, "Master toggle off — skipping poll")
            return Result.success()
        }
        val token = GitHubManager.getToken(ctx)
        if (token.isBlank()) {
            Log.d(TAG, "No GitHub token — skipping poll")
            return Result.success()
        }

        val sinceIso = NotificationsPreferences.getLastPollAt(ctx)
            .takeIf { it > 0 }
            ?.let { formatIso8601(it) }

        val notifications = try {
            GitHubManager.listNotifications(ctx, all = false, participating = false, since = sinceIso)
        } catch (e: Exception) {
            Log.e(TAG, "Fetch failed", e)
            return Result.retry()
        }

        // Cache login for the "owned repos" filter, if missing.
        if (NotificationsPreferences.getUserLogin(ctx).isBlank()) {
            try {
                val login = GitHubManager.getCachedUser(ctx)?.login
                    ?: GitHubManager.getUser(ctx)?.login
                if (!login.isNullOrBlank()) NotificationsPreferences.setUserLogin(ctx, login)
            } catch (_: Throwable) { /* best-effort */ }
        }

        val snapshot = NotificationsPreferences.snapshot(ctx)

        var shown = 0
        for (n in notifications) {
            if (!NotificationFilter.shouldShow(n, snapshot)) continue
            try {
                if (NotificationsManager.show(ctx, n)) {
                    NotificationsPreferences.markSeen(ctx, n.id)
                    shown++
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to show notification ${n.id}", e)
            }
        }

        NotificationsPreferences.setLastPollAt(ctx, System.currentTimeMillis())
        Log.d(TAG, "Polled ${notifications.size}, shown $shown")
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "github_notifications_polling"
        private const val TAG = "GhNotifsWorker"

        fun enqueue(context: Context) {
            val intervalMinutes = NotificationsPreferences
                .getPollIntervalMinutes(context).toLong()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<NotificationsWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            } catch (_: Throwable) {}
        }

        private fun formatIso8601(ts: Long): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(Date(ts))
        }
    }
}
