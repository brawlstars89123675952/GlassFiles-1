Implement GitHub Notifications for GlassFiles

═══════════════════════════════════════════════════════════════
GOAL
═══════════════════════════════════════════════════════════════

Add a notifications system that polls GitHub /notifications API in 
background and shows Android system notifications. Users can 
configure what types they want, quiet hours, polling frequency, 
and per-repo filtering.

This is NEW functionality — no existing notifications system to 
replace. Build it from scratch in clean modular files.

═══════════════════════════════════════════════════════════════
SCOPE — files to create/modify
═══════════════════════════════════════════════════════════════

CREATE these new files:
- app/src/main/java/com/glassfiles/notifications/NotificationsWorker.kt
- app/src/main/java/com/glassfiles/notifications/NotificationsManager.kt
- app/src/main/java/com/glassfiles/notifications/NotificationsPreferences.kt
- app/src/main/java/com/glassfiles/notifications/NotificationChannels.kt
- app/src/main/java/com/glassfiles/notifications/NotificationFilter.kt
- app/src/main/java/com/glassfiles/ui/screens/NotificationSettingsScreen.kt

MODIFY these existing files:
- app/src/main/java/com/glassfiles/GlassFilesApplication.kt 
  (register channels + start worker)
- app/src/main/AndroidManifest.xml 
  (add POST_NOTIFICATIONS permission)
- app/build.gradle (or build.gradle.kts) 
  (add WorkManager dependency if not present)
- The existing Settings screen — add an entry "Notifications" 
  that opens NotificationSettingsScreen

DO NOT modify:
- GitHubManager.kt — add new methods to it ONLY for notifications API 
  (listNotifications, markThreadRead). All other methods left alone.
- Any other GitHub UI module file
- Theme.kt

═══════════════════════════════════════════════════════════════
ARCHITECTURE
═══════════════════════════════════════════════════════════════

Layer 1 — GitHub API (in GitHubManager.kt)

Add 3 methods:

```kotlin
suspend fun listNotifications(
    context: Context,
    all: Boolean = false,
    participating: Boolean = false,
    since: String? = null
): List<GHNotification>

suspend fun markThreadRead(context: Context, threadId: String): Boolean

suspend fun markAllNotificationsRead(context: Context): Boolean

Endpoints:
GET /notifications?all={all}&participating={participating}&since={since}
PATCH /notifications/threads/{thread_id}
PUT /notifications
Add data class GHNotification with fields:

data class GHNotification(
    val id: String,
    val unread: Boolean,
    val reason: String,        // ci_activity, mention, review_requested, etc.
    val updatedAt: String,
    val lastReadAt: String?,
    val subjectTitle: String,
    val subjectType: String,   // PullRequest, Issue, Discussion, Release, etc.
    val subjectUrl: String,    // API url
    val repositoryFullName: String,  // owner/repo
    val repositoryUrl: String,
    val htmlUrl: String        // resolved web URL for opening in app
)

Parse JSON response carefully — some fields can be null.
Layer 2 — NotificationsPreferences.kt
Use SharedPreferences ("glassfiles_notifications") to store:
master_enabled: Boolean (default true)
poll_interval_minutes: Int (15, 30, or 60; default 15)
quiet_hours_enabled: Boolean (default false)
quiet_hours_start: Int (hour 0-23, default 23)
quiet_hours_end: Int (hour 0-23, default 7)
last_seen_notification_ids: Set (max 200 entries, FIFO)
last_poll_at: Long (timestamp ms)
repo_filter_mode: String ("all", "owned", "watching", "custom")
custom_repo_list: Set (owner/repo entries)
type__enabled: Boolean (one per reason, all default true)
reasons: ci_activity, mention, review_requested, comment,
state_change, assign, author, security_alert, subscribed,
team_mention, manual, invitation
Provide companion object with constants for keys and a clean API:
object NotificationsPreferences {
    fun isEnabled(context: Context): Boolean
    fun setEnabled(context: Context, enabled: Boolean)
    fun isTypeEnabled(context: Context, reason: String): Boolean
    fun setTypeEnabled(context: Context, reason: String, enabled: Boolean)
    fun getPollIntervalMinutes(context: Context): Int
    fun setPollIntervalMinutes(context: Context, minutes: Int)
    fun isInQuietHours(context: Context, now: Calendar = Calendar.getInstance()): Boolean
    // ... etc
    fun markSeen(context: Context, notificationId: String)
    fun isSeen(context: Context, notificationId: String): Boolean
}
Layer 3 — NotificationChannels.kt
Define 7 channels with stable IDs:
object NotificationChannels {
    const val CHANNEL_CI = "gh_ci"
    const val CHANNEL_MENTIONS = "gh_mentions"
    const val CHANNEL_REVIEWS = "gh_reviews"
    const val CHANNEL_ISSUES_PRS = "gh_issues_prs"
    const val CHANNEL_SECURITY = "gh_security"
    const val CHANNEL_ACTIVITY = "gh_activity"
    const val CHANNEL_SUBSCRIPTIONS = "gh_subscriptions"
    
    fun registerAll(context: Context) {
        // Create NotificationChannel for each on Android O+
        // CI, Mentions, Reviews, Security: IMPORTANCE_HIGH (with sound)
        // Issues/PRs: IMPORTANCE_DEFAULT
        // Activity, Subscriptions: IMPORTANCE_LOW (silent)
    }
    
    fun channelForReason(reason: String): String = when (reason) {
        "ci_activity" -> CHANNEL_CI
        "mention", "team_mention" -> CHANNEL_MENTIONS
        "review_requested" -> CHANNEL_REVIEWS
        "comment", "state_change", "assign", "author" -> CHANNEL_ISSUES_PRS
        "security_alert" -> CHANNEL_SECURITY
        "subscribed", "manual" -> CHANNEL_SUBSCRIPTIONS
        else -> CHANNEL_ACTIVITY
    }
}

User can enable/disable individual channels via system Settings →
Apps → GlassFiles → Notifications. We provide channel structure,
Android handles the toggles natively.
Layer 4 — NotificationFilter.kt
Pure logic, no I/O. Decides if a given notification should be shown:
object NotificationFilter {
    fun shouldShow(
        notification: GHNotification,
        prefs: NotificationsPreferences.Snapshot,  // captured at start of poll
        now: Calendar = Calendar.getInstance()
    ): Boolean {
        // 1. Master toggle
        if (!prefs.masterEnabled) return false
        
        // 2. Quiet hours
        if (prefs.quietHoursEnabled && isInQuietHours(prefs, now)) return false
        
        // 3. Type filter (per-reason toggle)
        if (!prefs.typeEnabled(notification.reason)) return false
        
        // 4. Repo filter
        when (prefs.repoFilterMode) {
            "owned" -> if (!isOwnedRepo(notification, prefs.userLogin)) return false
            "watching" -> // requires separate API call, fallback to "all"
            "custom" -> if (notification.repositoryFullName !in prefs.customRepos) return false
            "all" -> {} // pass
        }
        
        // 5. Already seen?
        if (notification.id in prefs.seenIds) return false
        
        return true
    }
}

Layer 5 — NotificationsManager.kt
Builds and shows Android notifications:

object NotificationsManager {
    fun show(context: Context, notification: GHNotification) {
        val channel = NotificationChannels.channelForReason(notification.reason)
        val title = formatTitle(notification)
        val body = formatBody(notification)
        val contentIntent = buildOpenIntent(context, notification)
        
        val notif = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification_glassfiles)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_check,
                "Mark as read",
                buildMarkReadIntent(context, notification.id)
            )
            .build()
        
        NotificationManagerCompat.from(context).notify(
            notification.id.hashCode(),
            notif
        )
    }
    
    private fun formatTitle(n: GHNotification): String = when (n.reason) {
        "ci_activity" -> when {
            "failed" in n.subjectTitle.lowercase() -> "❌ Build failed"
            "success" in n.subjectTitle.lowercase() -> "✅ Build succeeded"
            else -> "Build update: ${n.repositoryFullName}"
        }
        "mention", "team_mention" -> "Mentioned in ${n.repositoryFullName}"
        "review_requested" -> "Review requested"
        "security_alert" -> "🔒 Security alert"
        "comment" -> "New comment"
        "assign" -> "Assigned to you"
        "state_change" -> "Status changed"
        else -> n.subjectTitle
    }
    
    private fun formatBody(n: GHNotification): String =
        "${n.subjectTitle}\n${n.repositoryFullName}"
}

Layer 6 — NotificationsWorker.kt
CoroutineWorker triggered every N minutes:

class NotificationsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        if (!NotificationsPreferences.isEnabled(applicationContext)) {
            return Result.success()
        }
        
        val since = NotificationsPreferences.getLastPollAt(applicationContext)
            .takeIf { it > 0 }
            ?.let { iso8601(it) }
        
        val notifications = try {
            GitHubManager.listNotifications(applicationContext, since = since)
        } catch (e: Exception) {
            Log.e("NotifsWorker", "Failed to fetch", e)
            return Result.retry()
        }
        
        val prefs = NotificationsPreferences.snapshot(applicationContext)
        
        var shown = 0
        for (n in notifications) {
            if (!NotificationFilter.shouldShow(n, prefs)) continue
            try {
                NotificationsManager.show(applicationContext, n)
                NotificationsPreferences.markSeen(applicationContext, n.id)
                shown++
            } catch (e: Exception) {
                Log.e("NotifsWorker", "Failed to show ${n.id}", e)
            }
        }
        
        NotificationsPreferences.setLastPollAt(
            applicationContext, 
            System.currentTimeMillis()
        )
        Log.d("NotifsWorker", "Polled ${notifications.size}, shown $shown")
        
        return Result.success()
    }
    
    companion object {
        const val WORK_NAME = "github_notifications_polling"
        
        fun enqueue(context: Context) {
            val intervalMinutes = 
                NotificationsPreferences.getPollIntervalMinutes(context).toLong()
            
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
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

Layer 7 — UI: NotificationSettingsScreen.kt
Compose screen accessible from Settings. Sections:
Master toggle: "Enable notifications"
When OFF, all other options are disabled (alpha 0.5)
Polling frequency
Radio: Every 15 / 30 / 60 minutes
Quiet hours
Toggle + time pickers (start, end)
What to notify about (категории)
List of switches by reason:
🔴 CI failures and successes (ci_activity)
💬 Mentions (mention, team_mention)
👀 Review requests (review_requested)
📝 Comments and state changes (comment, state_change, assign)
🔒 Security alerts (security_alert)
📌 Subscribed repos (subscribed, manual)
📦 Releases (release)
Repository filter
Radio: All repos / Only my repos / Custom list
If Custom — button "Manage list" opens screen with add/remove repo
System settings link
Button: "Open Android notification settings"
→ opens Intent.ACTION_APP_NOTIFICATION_SETTINGS so user can disable
specific channels at OS level
Battery optimization warning
Show if not whitelisted (PowerManager.isIgnoringBatteryOptimizations)
Button: "Disable battery optimization"
On Xiaomi/Huawei/Honor also show:
"On Honor, additionally allow autostart in system Settings →
Apps → GlassFiles → Background activity"
Use the existing GHTopBar style from the project for consistency.
Follow existing Compose patterns in NotificationsScreen or settings
screens you find in the codebase.
═══════════════════════════════════════════════════════════════
APPLICATION INTEGRATION
═══════════════════════════════════════════════════════════════
In GlassFilesApplication.onCreate(), AFTER existing crash logger:

NotificationChannels.registerAll(this)

if (NotificationsPreferences.isEnabled(this) && hasGitHubToken()) {
    NotificationsWorker.enqueue(this)
}

Where hasGitHubToken() = GitHubManager.getToken(this).isNotBlank()
(use whatever method exists, look in GitHubManager).
═══════════════════════════════════════════════════════════════
PERMISSIONS
═══════════════════════════════════════════════════════════════
In AndroidManifest.xml, add inside  alongside other
 tags:
�

(POST_NOTIFICATIONS required on Android 13+. RECEIVE_BOOT_COMPLETED
to re-register WorkManager after device reboot — WorkManager handles
it automatically with this permission.)
When NotificationSettingsScreen opens for the first time and
master_enabled is true, request POST_NOTIFICATIONS at runtime if
on Android 13+ via ActivityResultContracts.RequestPermission.
═══════════════════════════════════════════════════════════════
DEPENDENCIES
═══════════════════════════════════════════════════════════════
In app/build.gradle (or build.gradle.kts), add to dependencies if
not already present:
implementation "androidx.work:work-runtime-ktx:2.9.0"
═══════════════════════════════════════════════════════════════
INTENT HANDLING (when user taps notification)
═══════════════════════════════════════════════════════════════
Tap on notification → opens MainActivity with extras:
notification_action: "open_thread"
thread_id: 
subject_url: 
html_url: 
subject_type: PullRequest/Issue/Discussion/Release
In MainActivity.onNewIntent / onCreate, check these extras and
navigate to the appropriate screen:
PullRequest → PR detail
Issue → Issue detail
Discussion → Discussion view (if exists, else open htmlUrl in browser)
Release → Release detail
Other → fallback: open htmlUrl in external browser
For "Mark as read" action button — use BroadcastReceiver that:
Calls GitHubManager.markThreadRead(threadId)
Cancels the Android notification
═══════════════════════════════════════════════════════════════
EDGE CASES TO HANDLE
═══════════════════════════════════════════════════════════════
No GitHub token (user logged out) — worker should noop, not crash
No internet — Result.retry() with backoff
Rate limit hit (403 with x-ratelimit-remaining=0) — log and
Result.success() (don't retry, wait for next interval)
notifications.size == 0 — normal, just update last_poll_at
Old notification IDs accumulate — limit seenIds to last 200 (FIFO)
User changes interval setting → cancel old work, enqueue new
App force-stopped — WorkManager re-schedules on next launch via
Application.onCreate()
Android 13+ runtime permission for POST_NOTIFICATIONS denied →
show explainer in settings, don't try to show notifications
═══════════════════════════════════════════════════════════════
TESTING & VERIFICATION
═══════════════════════════════════════════════════════════════
After implementation, manually verify:
Settings → Notifications opens the new screen
Toggling master enable starts/cancels the worker
Changing interval re-enqueues with new interval
Toggling individual reason types properly filters
Quiet hours filter works (test by setting current hour as quiet)
Open the app's notification settings via system button
Trigger a real notification:
Push a commit to your own repo to trigger ci_activity
Wait up to 15 minutes (or manually trigger via
"Test notification" debug button if added)
Notification appears with right title and channel
Tap notification → opens correct screen in app
"Mark as read" action → notification dismissed AND thread marked
read on GitHub
═══════════════════════════════════════════════════════════════
HONOR / XIAOMI / OPPO COMPATIBILITY
═══════════════════════════════════════════════════════════════
These OEMs aggressively kill background processes. Add to
NotificationSettingsScreen:
A persistent info card (only visible on first open or until dismissed):
"Some manufacturers (Xiaomi, Huawei, Honor, Oppo, Vivo) restrict
background apps. To get reliable notifications:
Allow autostart for GlassFiles in system Settings
Disable battery optimization (button below)
Lock the app in recents (swipe down on app card)
[Show me how] [Dismiss]"
The "Show me how" can link to a brief explainer screen with steps
for the most common OEMs (Xiaomi, Honor, Huawei).
═══════════════════════════════════════════════════════════════
NICE TO HAVE (skip if running out of time)
═══════════════════════════════════════════════════════════════
Group similar notifications: if 5 ci_activity from same repo within
10 min, show as "5 build updates in repo X" with summary
Notification grouping via setGroup() and group summary notification
Test-fire button in NotificationSettings: "Send test notification
now" (helpful for debugging permissions and channels)
Localization for notification titles (RU + EN)
═══════════════════════════════════════════════════════════════
CONSTRAINTS
═══════════════════════════════════════════════════════════════
Use ONLY HttpURLConnection + org.json (no OkHttp, no Retrofit,
no Moshi) — match existing project style
Use ONLY existing project color palette and theme tokens
Use Compose Material3 + existing components (GHTopBar etc)
All strings should go through Strings.kt or string resources, not
hardcoded
Both Light and Dark theme must look correct
All work on background threads — no network on main thread
No external dependencies beyond WorkManager
Minimum supported Android: same as existing project (check minSdk)
═══════════════════════════════════════════════════════════════
REPORT
═══════════════════════════════════════════════════════════════
After implementation, list:
All files created (full paths)
All files modified (full paths) and what changed in each
Any GitHub API endpoints used
Build command used to verify compilation (./gradlew assembleDebug)
Any issues encountered and how resolved
Any features deferred and why
