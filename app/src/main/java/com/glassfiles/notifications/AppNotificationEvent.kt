package com.glassfiles.notifications

data class AppNotificationEvent(
    val source: String,
    val type: String,
    val title: String,
    val body: String,
    val externalId: String = "",
    val target: AppNotificationTarget = AppNotificationTarget(AppNotificationTarget.DEST_NOTIFICATIONS),
    val important: Boolean = false,
    val showSystem: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
