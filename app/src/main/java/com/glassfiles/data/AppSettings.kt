package com.glassfiles.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppThemeMode {
    LIGHT,
    DARK,
    AMOLED,
    SYSTEM
    ; val label: String get() = when (this) { LIGHT -> Strings.themeLight; DARK -> Strings.themeDark; AMOLED -> "AMOLED"; SYSTEM -> Strings.themeSystem }
}

enum class AccentColor(val color: androidx.compose.ui.graphics.Color, val label: String) {
    BLUE(androidx.compose.ui.graphics.Color(0xFF007AFF), "Blue"),
    GREEN(androidx.compose.ui.graphics.Color(0xFF34C759), "Green"),
    ORANGE(androidx.compose.ui.graphics.Color(0xFFFF9500), "Orange"),
    RED(androidx.compose.ui.graphics.Color(0xFFFF3B30), "Red"),
    PURPLE(androidx.compose.ui.graphics.Color(0xFFAF52DE), "Purple"),
    TEAL(androidx.compose.ui.graphics.Color(0xFF5AC8FA), "Teal"),
    PINK(androidx.compose.ui.graphics.Color(0xFFFF2D55), "Pink"),
    INDIGO(androidx.compose.ui.graphics.Color(0xFF5856D6), "Indigo"),
    MINT(androidx.compose.ui.graphics.Color(0xFF00C7BE), "Mint"),
    YELLOW(androidx.compose.ui.graphics.Color(0xFFFFCC00), "Yellow")
}

enum class FolderIconStyle {
    DEFAULT, CIRCLE, GRADIENT, OUTLINED, FILLED
    ; val label: String get() = when (this) { DEFAULT -> "Default"; CIRCLE -> "Circle"; GRADIENT -> "Gradient"; OUTLINED -> "Outlined"; FILLED -> "Filled" }
}

enum class DefaultView {
    GRID,
    LIST; val label: String get() = when (this) { GRID -> Strings.grid; LIST -> Strings.list }
}

enum class DefaultSort {
    NAME,
    DATE,
    SIZE,
    TYPE; val label: String get() = when (this) { NAME -> Strings.sortName; DATE -> Strings.sortDate; SIZE -> Strings.sortSize; TYPE -> Strings.sortType }
}

enum class StartFolder {
    DOWNLOADS,
    ROOT,
    LAST; val label: String get() = when (this) { DOWNLOADS -> Strings.downloads; ROOT -> "Root"; LAST -> "Last" }
}

class AppSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    // ═══ Тема ═══
    var themeMode by mutableStateOf(
        try { AppThemeMode.valueOf(prefs.getString("theme_mode", "DARK") ?: "DARK") }
        catch (_: Exception) { AppThemeMode.LIGHT }
    )
        private set

    fun changeTheme(mode: AppThemeMode) {
        themeMode = mode
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    // ═══ Акцентный цвет ═══
    var accentColor by mutableStateOf(
        try { AccentColor.valueOf(prefs.getString("accent_color", "BLUE") ?: "BLUE") }
        catch (_: Exception) { AccentColor.BLUE }
    )
        private set

    fun changeAccentColor(color: AccentColor) {
        accentColor = color
        prefs.edit().putString("accent_color", color.name).apply()
    }

    // ═══ Стиль иконок папок ═══
    var folderIconStyle by mutableStateOf(
        try { FolderIconStyle.valueOf(prefs.getString("folder_icon_style", "DEFAULT") ?: "DEFAULT") }
        catch (_: Exception) { FolderIconStyle.DEFAULT }
    )
        private set

    fun changeFolderIconStyle(style: FolderIconStyle) {
        folderIconStyle = style
        prefs.edit().putString("folder_icon_style", style.name).apply()
    }

    // ═══ Файловый менеджер ═══
    var showHiddenFiles by mutableStateOf(prefs.getBoolean("show_hidden", false))
        private set

    var defaultView by mutableStateOf(
        try { DefaultView.valueOf(prefs.getString("default_view", "GRID") ?: "GRID") }
        catch (_: Exception) { DefaultView.GRID }
    )
        private set

    var defaultSort by mutableStateOf(
        try { DefaultSort.valueOf(prefs.getString("default_sort", "NAME") ?: "NAME") }
        catch (_: Exception) { DefaultSort.NAME }
    )
        private set

    var confirmDelete by mutableStateOf(prefs.getBoolean("confirm_delete", true))
        private set

    var startFolder by mutableStateOf(
        try { StartFolder.valueOf(prefs.getString("start_folder", "DOWNLOADS") ?: "DOWNLOADS") }
        catch (_: Exception) { StartFolder.DOWNLOADS }
    )
        private set

    var fileFontSize by mutableIntStateOf(prefs.getInt("file_font_size", 15))
        private set

    var folderStyle by mutableStateOf(
        try { FolderIconStyle.valueOf(prefs.getString("folder_style", "DEFAULT") ?: "DEFAULT") }
        catch (_: Exception) { FolderIconStyle.DEFAULT }
    )
        private set

    fun changeShowHidden(v: Boolean) { showHiddenFiles = v; prefs.edit().putBoolean("show_hidden", v).apply() }
    fun changeDefaultView(v: DefaultView) { defaultView = v; prefs.edit().putString("default_view", v.name).apply() }
    fun changeDefaultSort(v: DefaultSort) { defaultSort = v; prefs.edit().putString("default_sort", v.name).apply() }
    fun changeConfirmDelete(v: Boolean) { confirmDelete = v; prefs.edit().putBoolean("confirm_delete", v).apply() }
    fun changeStartFolder(v: StartFolder) { startFolder = v; prefs.edit().putString("start_folder", v.name).apply() }
    fun changeFileFontSize(v: Int) { fileFontSize = v.coerceIn(12, 20); prefs.edit().putInt("file_font_size", fileFontSize).apply() }
    fun changeFolderStyle(v: FolderIconStyle) { folderStyle = v; prefs.edit().putString("folder_style", v.name).apply() }

    // ═══ AI ═══
    var aiDefaultModel by mutableStateOf(prefs.getString("ai_default_model", "GEMINI_FLASH") ?: "GEMINI_FLASH")
        private set

    var aiSystemPrompt by mutableStateOf(prefs.getString("ai_system_prompt", "") ?: "")
        private set

    var aiLanguage by mutableStateOf(prefs.getString("ai_language", "Русский") ?: "Русский")
        private set

    fun changeAiDefaultModel(v: String) { aiDefaultModel = v; prefs.edit().putString("ai_default_model", v).apply() }
    fun changeAiSystemPrompt(v: String) { aiSystemPrompt = v; prefs.edit().putString("ai_system_prompt", v).apply() }
    fun changeAiLanguage(v: String) { aiLanguage = v; prefs.edit().putString("ai_language", v).apply() }

    // ═══ Язык / Language ═══
    var appLanguage by mutableStateOf(
        try { AppLanguage.valueOf(prefs.getString("app_language", "") ?: "") }
        catch (_: Exception) {
            // Auto-detect from system locale
            val sysLang = java.util.Locale.getDefault().language
            if (sysLang == "ru") AppLanguage.RUSSIAN else AppLanguage.ENGLISH
        }
    )
        private set

    val onboardingDone get() = prefs.getBoolean("onboarding_done", false)

    fun changeLanguage(v: AppLanguage) {
        appLanguage = v
        Strings.lang = v
        prefs.edit().putString("app_language", v.name).apply()
    }

    fun completeOnboarding() {
        prefs.edit().putBoolean("onboarding_done", true).apply()
    }

    init {
        // Sync language on startup
        Strings.lang = appLanguage
    }

    // ═══ Сброс ═══
    fun resetAll() {
        prefs.edit().clear().apply()
        themeMode = AppThemeMode.DARK
        showHiddenFiles = false
        defaultView = DefaultView.GRID
        defaultSort = DefaultSort.NAME
        confirmDelete = true
        startFolder = StartFolder.DOWNLOADS
        fileFontSize = 15
        folderStyle = FolderIconStyle.DEFAULT
        aiDefaultModel = "GEMINI_FLASH"
        aiSystemPrompt = ""
        aiLanguage = "Русский"
    }
}
