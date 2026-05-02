package com.glassfiles.data.ai.skills

import android.content.Context
import java.util.Locale

class AiSkillRouter(
    private val store: AiSkillStore = AiSkillStore,
) {
    fun match(context: Context, userMessage: String, appContext: AppAgentContext): AiSkillMatch? {
        if (!AiSkillPrefs.getEnableSkills(context) || !AiSkillPrefs.getAutoSuggest(context)) return null
        val lower = userMessage.lowercase(Locale.US)
        val candidates = store.listSkills(context).filter { it.enabled }
        val scored = candidates.mapNotNull { skill ->
            var best = 0f
            var reason = ""
            val skillName = skill.name.lowercase(Locale.US).replace("-", " ").trim()
            val skillId = skill.id.lowercase(Locale.US).replace("-", " ").trim()
            val manualTokens = setOf(
                "/${skill.id.lowercase(Locale.US)}",
                "@${skill.id.lowercase(Locale.US)}",
                "/${skill.packId.lowercase(Locale.US)}/${skill.id.lowercase(Locale.US)}",
                "@${skill.packId.lowercase(Locale.US)}/${skill.id.lowercase(Locale.US)}",
            )
            val firstToken = lower.substringBefore(' ').trim()
            when {
                firstToken in manualTokens -> {
                    best = 1f
                    reason = "manual invocation: $firstToken"
                }
                skillName.isNotBlank() && lower.contains(skillName) -> {
                    best = 0.85f
                    reason = "skill name: ${skill.name}"
                }
                skillId.isNotBlank() && lower.contains(skillId) -> {
                    best = 0.8f
                    reason = "skill id: ${skill.id}"
                }
            }
            skill.triggers.forEach { trigger ->
                val t = trigger.lowercase(Locale.US).trim()
                if (t.isBlank()) return@forEach
                when {
                    lower == t -> if (1f > best) {
                        best = 1f
                        reason = "exact trigger: $trigger"
                    }
                    lower.contains(t) -> if (0.8f > best) {
                        best = 0.8f
                        reason = "contains trigger: $trigger"
                    }
                }
            }
            if (best < 0.56f && skill.category.isNotBlank() && lower.contains(skill.category.lowercase(Locale.US))) {
                best = 0.56f
                reason = "category keyword: ${skill.category}"
            }
            if (best < 0.62f && !skill.description.isNullOrBlank()) {
                val descriptionTokens = skill.description
                    .lowercase(Locale.US)
                    .split(Regex("[^\\p{L}\\p{N}_-]+"))
                    .filter { it.length >= 4 }
                    .toSet()
                val messageTokens = lower
                    .split(Regex("[^\\p{L}\\p{N}_-]+"))
                    .filter { it.length >= 4 }
                    .toSet()
                val overlap = messageTokens.intersect(descriptionTokens).size
                if (overlap >= 2) {
                    best = 0.62f
                    reason = "description keyword overlap"
                }
            }
            if (appContext.chatOnly && skill.tools.any { it.startsWith("github_") }) {
                best -= 0.1f
            }
            if (best >= 0.55f) AiSkillMatch(skill, best, reason) else null
        }
        return scored.maxByOrNull { it.confidence }
    }
}
