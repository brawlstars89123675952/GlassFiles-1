package com.glassfiles.data.ai.skills

import android.content.Context

object AiSkillPrefs {
    private const val PREFS = "ai_skill_prefs"
    private const val KEY_ENABLE_SKILLS = "enable_skills"
    private const val KEY_AUTO_SUGGEST = "auto_suggest_matching_skill"
    private const val KEY_ALLOW_UNTRUSTED_DANGEROUS = "allow_untrusted_dangerous_tools"
    private const val KEY_SELECTED_SKILL = "selected_skill"
    private const val KEY_AUTO_DETECT = "auto_detect_when_to_use"
    private const val KEY_AUTO_DETECT_MODEL = "auto_detect_model"
    private const val KEY_AUTO_DETECT_MAX = "auto_detect_max"

    const val DEFAULT_AUTO_DETECT_MODEL = "claude-haiku-4-5"
    const val DEFAULT_AUTO_DETECT_MAX = 3

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getEnableSkills(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLE_SKILLS, true)

    fun setEnableSkills(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLE_SKILLS, enabled).apply()
    }

    fun getAutoSuggest(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_SUGGEST, true)

    fun setAutoSuggest(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_SUGGEST, enabled).apply()
    }

    fun getAutoDetectWhenToUse(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_DETECT, true)

    fun setAutoDetectWhenToUse(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_DETECT, enabled).apply()
    }

    fun getAutoDetectModel(context: Context): String =
        prefs(context).getString(KEY_AUTO_DETECT_MODEL, DEFAULT_AUTO_DETECT_MODEL)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_AUTO_DETECT_MODEL

    fun setAutoDetectModel(context: Context, modelId: String) {
        prefs(context).edit().putString(KEY_AUTO_DETECT_MODEL, modelId.trim()).apply()
    }

    fun getAutoDetectMax(context: Context): Int =
        prefs(context).getInt(KEY_AUTO_DETECT_MAX, DEFAULT_AUTO_DETECT_MAX)
            .coerceIn(1, 5)

    fun setAutoDetectMax(context: Context, max: Int) {
        prefs(context).edit().putInt(KEY_AUTO_DETECT_MAX, max.coerceIn(1, 5)).apply()
    }

    fun getAllowUntrustedDangerousTools(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ALLOW_UNTRUSTED_DANGEROUS, false)

    fun setAllowUntrustedDangerousTools(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ALLOW_UNTRUSTED_DANGEROUS, enabled).apply()
    }

    fun getSelectedSkillId(context: Context): String? =
        prefs(context).getString(KEY_SELECTED_SKILL, null)?.takeIf { it.isNotBlank() }

    fun setSelectedSkillId(context: Context, skillId: String?) {
        prefs(context).edit().apply {
            if (skillId.isNullOrBlank()) remove(KEY_SELECTED_SKILL) else putString(KEY_SELECTED_SKILL, skillId)
        }.apply()
    }
}
