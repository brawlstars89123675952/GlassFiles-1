package com.glassfiles.notifications

import android.content.Context

object AppNotificationPreferences {
    const val SOURCE_GITHUB = "github"
    const val SOURCE_FILE_OPS = "file_ops"
    const val SOURCE_STORAGE = "storage"
    const val SOURCE_SECURITY = "security"
    const val SOURCE_TERMINAL = "terminal"
    const val SOURCE_DRIVE = "drive"
    const val SOURCE_SHIZUKU = "shizuku"
    const val SOURCE_SYSTEM = "system"

    val SOURCES = listOf(
        SOURCE_GITHUB,
        SOURCE_FILE_OPS,
        SOURCE_STORAGE,
        SOURCE_SECURITY,
        SOURCE_TERMINAL,
        SOURCE_DRIVE,
        SOURCE_SHIZUKU,
        SOURCE_SYSTEM
    )

    private const val PREFS = "glassfiles_app_notifications"
    private const val KEY_SOURCE_PREFIX = "source_enabled_"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isSourceEnabled(context: Context, source: String): Boolean =
        prefs(context).getBoolean(KEY_SOURCE_PREFIX + source, true)

    fun setSourceEnabled(context: Context, source: String, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SOURCE_PREFIX + source, enabled).apply()
    }

    fun displayName(source: String): String = when (source) {
        SOURCE_GITHUB -> "GitHub"
        SOURCE_FILE_OPS -> "File operations"
        SOURCE_STORAGE -> "Storage warnings"
        SOURCE_SECURITY -> "Security and crashes"
        SOURCE_TERMINAL -> "Terminal"
        SOURCE_DRIVE -> "Google Drive"
        SOURCE_SHIZUKU -> "Shizuku"
        SOURCE_SYSTEM -> "System"
        else -> source
    }
}
