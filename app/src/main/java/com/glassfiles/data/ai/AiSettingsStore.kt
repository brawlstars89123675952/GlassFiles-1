package com.glassfiles.data.ai

import android.content.Context

/**
 * SharedPreferences-backed store for AI-module-only settings.
 *
 * Kept separate from the global appearance / theme settings so the user can
 * tune the AI experience (syntax theme, code font size, auto-save behaviour)
 * without affecting the rest of the app, and vice versa.
 */
object AiSettingsStore {
    private const val PREFS = "ai_settings"

    private const val K_SYNTAX_THEME = "syntax_theme"
    private const val K_CODE_FONT_SIZE = "code_font_size_sp"
    private const val K_CHAT_FONT_SIZE = "chat_font_size_sp"
    private const val K_AUTO_SAVE_GALLERY = "auto_save_gallery"
    private const val K_STREAM_SCROLL = "stream_auto_scroll"

    private const val DEFAULT_CODE_FONT_SIZE = 12
    private const val DEFAULT_CHAT_FONT_SIZE = 14

    fun getSyntaxTheme(context: Context): AiSyntaxTheme {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = prefs.getString(K_SYNTAX_THEME, null) ?: return AiSyntaxTheme.DEFAULT_DARK
        return runCatching { AiSyntaxTheme.valueOf(name) }.getOrDefault(AiSyntaxTheme.DEFAULT_DARK)
    }

    fun setSyntaxTheme(context: Context, theme: AiSyntaxTheme) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(K_SYNTAX_THEME, theme.name)
            .apply()
    }

    fun getCodeFontSize(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(K_CODE_FONT_SIZE, DEFAULT_CODE_FONT_SIZE)
            .coerceIn(10, 18)
    }

    fun setCodeFontSize(context: Context, sp: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(K_CODE_FONT_SIZE, sp.coerceIn(10, 18))
            .apply()
    }

    fun getChatFontSize(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(K_CHAT_FONT_SIZE, DEFAULT_CHAT_FONT_SIZE)
            .coerceIn(12, 20)
    }

    fun setChatFontSize(context: Context, sp: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(K_CHAT_FONT_SIZE, sp.coerceIn(12, 20))
            .apply()
    }

    fun isAutoSaveGallery(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(K_AUTO_SAVE_GALLERY, false)
    }

    fun setAutoSaveGallery(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(K_AUTO_SAVE_GALLERY, enabled)
            .apply()
    }

    fun isStreamAutoScroll(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(K_STREAM_SCROLL, true)
    }

    fun setStreamAutoScroll(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(K_STREAM_SCROLL, enabled)
            .apply()
    }
}
