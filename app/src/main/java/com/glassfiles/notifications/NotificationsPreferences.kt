package com.glassfiles.notifications

import android.content.Context
import java.util.Calendar

/**
 * Preferences storage for the GitHub notifications subsystem.
 *
 * Stored in SharedPreferences "glassfiles_notifications".
 * Public API takes Context and is safe to call from any thread.
 */
object NotificationsPreferences {

    const val PREFS = "glassfiles_notifications"

    // ── Keys ──────────────────────────────────────────────────────────
    const val KEY_MASTER = "master_enabled"
    const val KEY_INTERVAL = "poll_interval_minutes"
    const val KEY_QUIET_ON = "quiet_hours_enabled"
    const val KEY_QUIET_START = "quiet_hours_start"
    const val KEY_QUIET_END = "quiet_hours_end"
    const val KEY_SEEN_IDS = "last_seen_notification_ids"
    const val KEY_LAST_POLL = "last_poll_at"
    const val KEY_REPO_FILTER_MODE = "repo_filter_mode"
    const val KEY_CUSTOM_REPOS = "custom_repo_list"
    const val KEY_USER_LOGIN = "user_login_cached"
    const val KEY_OEM_DISMISSED = "oem_warning_dismissed"
    const val KEY_TYPE_PREFIX = "type_"

    // ── Constants ─────────────────────────────────────────────────────
    const val MAX_SEEN_IDS = 200
    const val DEFAULT_INTERVAL = 15
    val ALLOWED_INTERVALS = intArrayOf(15, 30, 60)

    /** Reasons we can filter on. Order matters for UI. */
    val ALL_REASONS = listOf(
        "ci_activity",
        "mention",
        "team_mention",
        "review_requested",
        "comment",
        "state_change",
        "assign",
        "author",
        "security_alert",
        "subscribed",
        "manual",
        "invitation",
        "release"
    )

    // Repo filter modes
    const val REPO_MODE_ALL = "all"
    const val REPO_MODE_OWNED = "owned"
    const val REPO_MODE_WATCHING = "watching"
    const val REPO_MODE_CUSTOM = "custom"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── Master toggle ─────────────────────────────────────────────────
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_MASTER, true)
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MASTER, enabled).apply()
    }

    // ── Interval ──────────────────────────────────────────────────────
    fun getPollIntervalMinutes(context: Context): Int {
        val v = prefs(context).getInt(KEY_INTERVAL, DEFAULT_INTERVAL)
        return if (v in ALLOWED_INTERVALS) v else DEFAULT_INTERVAL
    }
    fun setPollIntervalMinutes(context: Context, minutes: Int) {
        val v = if (minutes in ALLOWED_INTERVALS) minutes else DEFAULT_INTERVAL
        prefs(context).edit().putInt(KEY_INTERVAL, v).apply()
    }

    // ── Quiet hours ───────────────────────────────────────────────────
    fun isQuietHoursEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_QUIET_ON, false)
    fun setQuietHoursEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_QUIET_ON, enabled).apply()
    }
    fun getQuietHoursStart(context: Context): Int =
        prefs(context).getInt(KEY_QUIET_START, 23).coerceIn(0, 23)
    fun getQuietHoursEnd(context: Context): Int =
        prefs(context).getInt(KEY_QUIET_END, 7).coerceIn(0, 23)
    fun setQuietHours(context: Context, startHour: Int, endHour: Int) {
        prefs(context).edit()
            .putInt(KEY_QUIET_START, startHour.coerceIn(0, 23))
            .putInt(KEY_QUIET_END, endHour.coerceIn(0, 23))
            .apply()
    }
    fun isInQuietHours(context: Context, now: Calendar = Calendar.getInstance()): Boolean {
        if (!isQuietHoursEnabled(context)) return false
        val start = getQuietHoursStart(context)
        val end = getQuietHoursEnd(context)
        val hour = now.get(Calendar.HOUR_OF_DAY)
        return hourInRange(hour, start, end)
    }

    /** Inclusive of start, exclusive of end. Handles wrap-around (e.g. 23..7). */
    internal fun hourInRange(hour: Int, start: Int, end: Int): Boolean {
        if (start == end) return false // 0-length window
        return if (start < end) hour in start until end
        else hour >= start || hour < end
    }

    // ── Per-reason toggles ────────────────────────────────────────────
    fun isTypeEnabled(context: Context, reason: String): Boolean =
        prefs(context).getBoolean(KEY_TYPE_PREFIX + reason, true)
    fun setTypeEnabled(context: Context, reason: String, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TYPE_PREFIX + reason, enabled).apply()
    }

    // ── Repo filter ───────────────────────────────────────────────────
    fun getRepoFilterMode(context: Context): String =
        prefs(context).getString(KEY_REPO_FILTER_MODE, REPO_MODE_ALL) ?: REPO_MODE_ALL
    fun setRepoFilterMode(context: Context, mode: String) {
        val v = if (mode in setOf(REPO_MODE_ALL, REPO_MODE_OWNED, REPO_MODE_WATCHING, REPO_MODE_CUSTOM))
            mode else REPO_MODE_ALL
        prefs(context).edit().putString(KEY_REPO_FILTER_MODE, v).apply()
    }
    fun getCustomRepos(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_CUSTOM_REPOS, emptySet())?.toSet() ?: emptySet()
    fun setCustomRepos(context: Context, repos: Set<String>) {
        prefs(context).edit().putStringSet(KEY_CUSTOM_REPOS, repos).apply()
    }
    fun addCustomRepo(context: Context, fullName: String) {
        val s = getCustomRepos(context).toMutableSet()
        s.add(fullName.trim())
        setCustomRepos(context, s)
    }
    fun removeCustomRepo(context: Context, fullName: String) {
        val s = getCustomRepos(context).toMutableSet()
        s.remove(fullName)
        setCustomRepos(context, s)
    }

    // ── User login (cached) ───────────────────────────────────────────
    fun getUserLogin(context: Context): String =
        prefs(context).getString(KEY_USER_LOGIN, "") ?: ""
    fun setUserLogin(context: Context, login: String) {
        prefs(context).edit().putString(KEY_USER_LOGIN, login).apply()
    }

    // ── Last poll ─────────────────────────────────────────────────────
    fun getLastPollAt(context: Context): Long =
        prefs(context).getLong(KEY_LAST_POLL, 0L)
    fun setLastPollAt(context: Context, ts: Long) {
        prefs(context).edit().putLong(KEY_LAST_POLL, ts).apply()
    }

    // ── Seen IDs (FIFO, capped) ───────────────────────────────────────
    /**
     * Stored as a single newline-separated string (not StringSet) to preserve
     * insertion order for FIFO eviction. SharedPreferences StringSet has no
     * order guarantee.
     */
    fun isSeen(context: Context, notificationId: String): Boolean =
        notificationId in seenList(context)

    fun markSeen(context: Context, notificationId: String) {
        val list = seenList(context).toMutableList()
        if (notificationId in list) return
        list.add(notificationId)
        while (list.size > MAX_SEEN_IDS) list.removeAt(0)
        prefs(context).edit().putString(KEY_SEEN_IDS, list.joinToString("\n")).apply()
    }

    fun seenList(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_SEEN_IDS, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split('\n').filter { it.isNotEmpty() }
    }

    fun clearSeen(context: Context) {
        prefs(context).edit().remove(KEY_SEEN_IDS).apply()
    }

    // ── OEM warning dismissal ─────────────────────────────────────────
    fun isOemWarningDismissed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OEM_DISMISSED, false)
    fun setOemWarningDismissed(context: Context, dismissed: Boolean) {
        prefs(context).edit().putBoolean(KEY_OEM_DISMISSED, dismissed).apply()
    }

    // ── Snapshot for stateless filtering ──────────────────────────────
    /**
     * Captures all relevant prefs at the start of a poll, so filtering is
     * deterministic and doesn't hit SharedPreferences for every notification.
     */
    fun snapshot(context: Context): Snapshot = Snapshot(
        masterEnabled = isEnabled(context),
        quietHoursEnabled = isQuietHoursEnabled(context),
        quietStart = getQuietHoursStart(context),
        quietEnd = getQuietHoursEnd(context),
        typeEnabled = ALL_REASONS.associateWith { isTypeEnabled(context, it) },
        repoFilterMode = getRepoFilterMode(context),
        customRepos = getCustomRepos(context),
        userLogin = getUserLogin(context),
        seenIds = seenList(context).toSet()
    )

    data class Snapshot(
        val masterEnabled: Boolean,
        val quietHoursEnabled: Boolean,
        val quietStart: Int,
        val quietEnd: Int,
        val typeEnabled: Map<String, Boolean>,
        val repoFilterMode: String,
        val customRepos: Set<String>,
        val userLogin: String,
        val seenIds: Set<String>
    ) {
        fun typeEnabled(reason: String): Boolean = typeEnabled[reason] ?: true
    }
}
