package com.glassfiles.notifications

data class AppNotificationTarget(
    val destination: String,
    val path: String = "",
    val extra: String = ""
) {
    companion object {
        const val DEST_HOME = "home"
        const val DEST_STORAGE = "storage"
        const val DEST_SETTINGS = "settings"
        const val DEST_TERMINAL = "terminal"
        const val DEST_GITHUB = "github"
        const val DEST_PATH = "path"
        const val DEST_NOTIFICATIONS = "notifications"
    }
}
