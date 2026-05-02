package com.glassfiles.data.ai

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

enum class AiAgentPermissionMode(
    val label: String,
    val description: String,
) {
    ASK("ask", "Ask before every tool action."),
    AUTO_READS("auto reads", "Read-only tools run automatically; edits still ask."),
    ACCEPT_EDITS("accept edits", "Reads, edits and file writes run automatically; commits still ask."),
    YOLO("yolo", "Most actions run automatically; destructive actions still require approval."),
    CUSTOM("custom", "Manual approval toggles are active."),
}

object AiAgentApprovalPrefs {
    const val WRITE_LIMIT_UNLIMITED = 0
    const val DEFAULT_WRITE_LIMIT = 50

    val DEFAULT_PROTECTED_PATHS: List<String> = listOf(
        "AgentTerminalTheme.kt",
        "AiAgentScreen.kt",
        "CodingScreen.kt",
        "GlassFilesApp.kt",
        "ai/terminal/**",
        "build.gradle",
        "*.gradle.kts",
        "AndroidManifest.xml",
        "keystore.*",
        "*.jks",
    )

    private const val PREFS = "ai_agent_approval_prefs"
    private const val KEY_AUTO_READS = "auto_reads"
    private const val KEY_AUTO_EDITS = "auto_edits"
    private const val KEY_AUTO_WRITES = "auto_writes"
    private const val KEY_AUTO_COMMITS = "auto_commits"
    private const val KEY_AUTO_DESTRUCTIVE = "auto_destructive"
    private const val KEY_YOLO_MODE = "yolo_mode"
    private const val KEY_SESSION_TRUST = "session_trust"
    private const val KEY_WRITE_LIMIT = "write_limit"
    private const val KEY_PROTECTED_PATHS = "protected_paths"
    private const val KEY_BACKGROUND_EXECUTION = "background_execution"
    private const val KEY_KEEP_CPU_AWAKE = "keep_cpu_awake"
    private const val KEY_NOTIFICATION_DENIED_NOTICE_SHOWN = "notification_denied_notice_shown"
    private const val KEY_WORKSPACE_MODE = "workspace_mode"
    private const val KEY_EXPAND_TOOL_CALLS = "expand_tool_calls"
    val yoloConfirmedKey = booleanPreferencesKey("yolo_mode_confirmed")

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getAutoApproveReads(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_READS, true)

    fun setAutoApproveReads(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_READS, enabled).apply()
    }

    fun getAutoApproveEdits(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_EDITS, false)

    fun setAutoApproveEdits(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_EDITS, enabled).apply()
    }

    fun getAutoApproveWrites(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_WRITES, false)

    fun setAutoApproveWrites(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_WRITES, enabled).apply()
    }

    fun getAutoApproveCommits(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_COMMITS, false)

    fun setAutoApproveCommits(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_COMMITS, enabled).apply()
    }

    fun getAutoApproveDestructive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_DESTRUCTIVE, false)

    fun setAutoApproveDestructive(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_DESTRUCTIVE, enabled).apply()
    }

    fun getPermissionMode(context: Context): AiAgentPermissionMode {
        val yolo = getYoloMode(context)
        val reads = getAutoApproveReads(context)
        val edits = getAutoApproveEdits(context)
        val writes = getAutoApproveWrites(context)
        val commits = getAutoApproveCommits(context)
        val destructive = getAutoApproveDestructive(context)
        val trust = getSessionTrust(context)
        return when {
            yolo && reads && edits && writes && commits && !destructive -> AiAgentPermissionMode.YOLO
            !yolo && reads && edits && writes && !commits && !destructive && !trust -> AiAgentPermissionMode.ACCEPT_EDITS
            !yolo && reads && !edits && !writes && !commits && !destructive && !trust -> AiAgentPermissionMode.AUTO_READS
            !yolo && !reads && !edits && !writes && !commits && !destructive && !trust -> AiAgentPermissionMode.ASK
            else -> AiAgentPermissionMode.CUSTOM
        }
    }

    fun applyPermissionMode(context: Context, mode: AiAgentPermissionMode) {
        if (mode == AiAgentPermissionMode.CUSTOM) return
        val editor = prefs(context).edit()
        when (mode) {
            AiAgentPermissionMode.ASK -> {
                editor
                    .putBoolean(KEY_AUTO_READS, false)
                    .putBoolean(KEY_AUTO_EDITS, false)
                    .putBoolean(KEY_AUTO_WRITES, false)
                    .putBoolean(KEY_AUTO_COMMITS, false)
                    .putBoolean(KEY_AUTO_DESTRUCTIVE, false)
                    .putBoolean(KEY_YOLO_MODE, false)
                    .putBoolean(KEY_SESSION_TRUST, false)
            }
            AiAgentPermissionMode.AUTO_READS -> {
                editor
                    .putBoolean(KEY_AUTO_READS, true)
                    .putBoolean(KEY_AUTO_EDITS, false)
                    .putBoolean(KEY_AUTO_WRITES, false)
                    .putBoolean(KEY_AUTO_COMMITS, false)
                    .putBoolean(KEY_AUTO_DESTRUCTIVE, false)
                    .putBoolean(KEY_YOLO_MODE, false)
                    .putBoolean(KEY_SESSION_TRUST, false)
            }
            AiAgentPermissionMode.ACCEPT_EDITS -> {
                editor
                    .putBoolean(KEY_AUTO_READS, true)
                    .putBoolean(KEY_AUTO_EDITS, true)
                    .putBoolean(KEY_AUTO_WRITES, true)
                    .putBoolean(KEY_AUTO_COMMITS, false)
                    .putBoolean(KEY_AUTO_DESTRUCTIVE, false)
                    .putBoolean(KEY_YOLO_MODE, false)
                    .putBoolean(KEY_SESSION_TRUST, false)
            }
            AiAgentPermissionMode.YOLO -> {
                editor
                    .putBoolean(KEY_AUTO_READS, true)
                    .putBoolean(KEY_AUTO_EDITS, true)
                    .putBoolean(KEY_AUTO_WRITES, true)
                    .putBoolean(KEY_AUTO_COMMITS, true)
                    .putBoolean(KEY_AUTO_DESTRUCTIVE, false)
                    .putBoolean(KEY_YOLO_MODE, true)
                    .putBoolean(KEY_SESSION_TRUST, false)
            }
            AiAgentPermissionMode.CUSTOM -> Unit
        }
        editor.apply()
    }

    fun getYoloMode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_YOLO_MODE, false)

    fun setYoloMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_YOLO_MODE, enabled).apply()
    }

    suspend fun isYoloModeConfirmed(context: Context): Boolean =
        context.aiAgentApprovalDataStore.data.first()[yoloConfirmedKey] ?: false

    suspend fun setYoloModeConfirmed(context: Context, confirmed: Boolean) {
        context.aiAgentApprovalDataStore.edit { prefs ->
            prefs[yoloConfirmedKey] = confirmed
        }
    }

    fun getSessionTrust(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SESSION_TRUST, false)

    fun setSessionTrust(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SESSION_TRUST, enabled).apply()
    }

    fun getWriteLimit(context: Context): Int =
        prefs(context).getInt(KEY_WRITE_LIMIT, DEFAULT_WRITE_LIMIT)

    fun setWriteLimit(context: Context, limit: Int) {
        prefs(context).edit().putInt(KEY_WRITE_LIMIT, limit.coerceAtLeast(0)).apply()
    }

    fun getBackgroundExecution(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BACKGROUND_EXECUTION, true)

    fun setBackgroundExecution(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BACKGROUND_EXECUTION, enabled).apply()
    }

    fun getKeepCpuAwake(context: Context): Boolean =
        prefs(context).getBoolean(KEY_KEEP_CPU_AWAKE, false)

    fun setKeepCpuAwake(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_KEEP_CPU_AWAKE, enabled).apply()
    }

    fun getWorkspaceMode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WORKSPACE_MODE, false)

    fun setWorkspaceMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_WORKSPACE_MODE, enabled).apply()
    }

    fun getExpandToolCalls(context: Context): Boolean =
        prefs(context).getBoolean(KEY_EXPAND_TOOL_CALLS, false)

    fun setExpandToolCalls(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_EXPAND_TOOL_CALLS, enabled).apply()
    }

    fun getNotificationDeniedNoticeShown(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NOTIFICATION_DENIED_NOTICE_SHOWN, false)

    fun setNotificationDeniedNoticeShown(context: Context, shown: Boolean) {
        prefs(context).edit().putBoolean(KEY_NOTIFICATION_DENIED_NOTICE_SHOWN, shown).apply()
    }

    fun getProtectedPaths(context: Context): List<String> =
        prefs(context).getString(KEY_PROTECTED_PATHS, null)
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toList()
            ?: DEFAULT_PROTECTED_PATHS

    fun setProtectedPaths(context: Context, text: String) {
        val cleaned = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
        prefs(context).edit().putString(KEY_PROTECTED_PATHS, cleaned).apply()
    }
}

private val Context.aiAgentApprovalDataStore by preferencesDataStore(name = "ai_agent_approval")
