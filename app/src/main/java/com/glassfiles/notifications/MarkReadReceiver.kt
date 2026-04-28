package com.glassfiles.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.glassfiles.data.github.GitHubManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the "Mark as read" action button on GitHub notifications.
 *
 * Cancels the local notification immediately, then asynchronously calls the
 * GitHub API to mark the thread read upstream. We don't block on the
 * network: the notification has already disappeared from the user's tray.
 */
class MarkReadReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION = "com.glassfiles.notifications.MARK_READ"
        const val EXTRA_THREAD_ID = "thread_id"
        private const val TAG = "GhMarkRead"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val threadId = intent.getStringExtra(EXTRA_THREAD_ID).orEmpty()
        if (threadId.isBlank()) return

        // Drop from tray right away.
        NotificationsManager.cancel(context, threadId)
        // Remember it locally so the worker doesn't re-show it.
        NotificationsPreferences.markSeen(context, threadId)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                GitHubManager.markThreadRead(context, threadId)
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to mark thread $threadId read upstream", e)
            } finally {
                pending.finish()
            }
        }
    }
}
