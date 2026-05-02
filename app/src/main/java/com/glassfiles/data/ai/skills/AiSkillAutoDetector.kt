package com.glassfiles.data.ai.skills

import android.content.Context
import com.glassfiles.data.ai.AiKeyStore
import com.glassfiles.data.ai.ModelPricing
import com.glassfiles.data.ai.models.AiMessage
import com.glassfiles.data.ai.models.AiProviderId
import com.glassfiles.data.ai.providers.AiProvider
import com.glassfiles.data.ai.providers.AiProviders
import com.glassfiles.data.ai.usage.AiUsageMode
import com.glassfiles.data.ai.usage.AiUsageRecord
import com.glassfiles.data.ai.usage.AiUsageStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class AiSkillAutoDetector(
    private val skillStore: AiSkillStore = AiSkillStore,
    private val provider: AiProvider? = null,
) {
    suspend fun detectRelevantSkills(
        context: Context,
        userQuery: String,
        maxSkills: Int = AiSkillPrefs.getAutoDetectMax(context),
        selectorModel: String = AiSkillPrefs.getAutoDetectModel(context),
    ): List<AiSkill> {
        if (!AiSkillPrefs.getEnableSkills(context) ||
            !AiSkillPrefs.getAutoSuggest(context) ||
            !AiSkillPrefs.getAutoDetectWhenToUse(context)
        ) {
            return emptyList()
        }
        val query = userQuery.trim()
        if (query.isBlank()) return emptyList()

        val packs = skillStore.listPacks(context).associateBy { it.id }
        val candidates = skillStore.listSkills(context)
            .filter { it.enabled }
            .mapNotNull { skill ->
                val pack = packs[skill.packId] ?: return@mapNotNull null
                val usage = skill.whenToUse?.takeIf { it.isNotBlank() }
                    ?: pack.whenToUse?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                Candidate(skill, pack, usage)
            }
        if (candidates.isEmpty()) return emptyList()

        val providerId = provider?.id ?: providerForModel(selectorModel)
        val apiKey = AiKeyStore.getKey(context, providerId)
        if (apiKey.isBlank()) return emptyList()
        val selectedProvider = provider ?: AiProviders.get(providerId)
        val cappedMax = maxSkills.coerceIn(1, 5)
        val skillsList = candidates.joinToString("\n") { candidate ->
            "${candidate.qualifiedId}: ${candidate.usage.replace('\n', ' ').take(420)}"
        }.take(12_000)
        val system = """
            You are a skill selector. Given a user query and available skills with usage descriptions, return skill IDs that apply.

            Rules:
            - Return JSON object only
            - Return {"skill_ids": []} if nothing matches
            - Maximum $cappedMax skills
            - Be selective; only include skills that CLEARLY apply
            - Do not include skills based on weak keyword overlap
        """.trimIndent()
        val user = """
            User query: $query

            Available skills:
            $skillsList

            Return JSON: {"skill_ids": ["..."]} or {"skill_ids": []}
        """.trimIndent()
        val messages = listOf(
            AiMessage("system", system),
            AiMessage("user", user),
        )
        val response = runCatching {
            selectedProvider.chat(
                context = context,
                modelId = selectorModel,
                messages = messages,
                apiKey = apiKey,
                onChunk = {},
            )
        }.getOrElse { return emptyList() }

        recordUsage(context, providerId, selectorModel, system + user, response)
        val ids = parseSkillIds(response).take(cappedMax)
        if (ids.isEmpty()) return emptyList()
        return ids.mapNotNull { id ->
            val normalized = id.lowercase(Locale.US)
            candidates.firstOrNull {
                it.qualifiedId.lowercase(Locale.US) == normalized ||
                    it.skill.id.lowercase(Locale.US) == normalized
            }?.skill
        }.distinctBy { "${it.packId}/${it.id}" }
    }

    suspend fun resolveActiveSkills(
        context: Context,
        userQuery: String,
        appContext: AppAgentContext,
        selectedSkill: AiSkill?,
    ): List<AiSkill> {
        if (selectedSkill != null) return listOf(selectedSkill)
        val triggerMatched = AiSkillRouter(skillStore).match(context, userQuery, appContext)?.skill
        val detected = detectRelevantSkills(context, userQuery)
        return (listOfNotNull(triggerMatched) + detected)
            .distinctBy { "${it.packId}/${it.id}" }
    }

    private fun parseSkillIds(response: String): List<String> {
        val json = response.extractJsonValue() ?: return emptyList()
        val arr = runCatching { JSONObject(json).optJSONArray("skill_ids") }.getOrNull()
            ?: runCatching { JSONArray(json) }.getOrNull()
            ?: return emptyList()
        return (0 until arr.length())
            .mapNotNull { arr.optString(it).trim().takeIf { value -> value.isNotBlank() } }
    }

    private fun recordUsage(
        context: Context,
        providerId: AiProviderId,
        modelId: String,
        input: String,
        output: String,
    ) {
        val inputChars = input.length
        val outputChars = output.length
        val cost = ModelPricing.rateFor(providerId.name, modelId)
            ?.let { ModelPricing.estimateCostUsd(it, inputChars, outputChars) }
        AiUsageStore.append(
            context,
            AiUsageRecord(
                providerId = providerId.name,
                modelId = modelId,
                mode = AiUsageMode.SKILL_AUTO_DETECTION,
                estimatedInputChars = inputChars,
                estimatedOutputChars = outputChars,
                costUsd = cost,
                estimated = true,
            ),
        )
    }

    private fun providerForModel(modelId: String): AiProviderId {
        val id = modelId.lowercase(Locale.US)
        return when {
            "/" in id -> AiProviderId.OPENROUTER
            id.startsWith("claude") -> AiProviderId.ANTHROPIC
            id.startsWith("gemini") -> AiProviderId.GOOGLE
            id.startsWith("grok") -> AiProviderId.XAI
            id.startsWith("moonshot") || id.startsWith("kimi") -> AiProviderId.MOONSHOT
            id.startsWith("qwen") -> AiProviderId.ALIBABA
            else -> AiProviderId.OPENAI
        }
    }

    private fun String.extractJsonValue(): String? {
        val trimmed = trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) return trimmed
        val start = indexOf('{')
        val end = lastIndexOf('}')
        if (start >= 0 && end > start) return substring(start, end + 1)
        val arrayStart = indexOf('[')
        val arrayEnd = lastIndexOf(']')
        return if (arrayStart >= 0 && arrayEnd > arrayStart) substring(arrayStart, arrayEnd + 1) else null
    }

    private data class Candidate(
        val skill: AiSkill,
        val pack: AiSkillPack,
        val usage: String,
    ) {
        val qualifiedId: String = "${pack.id}/${skill.id}"
    }
}
