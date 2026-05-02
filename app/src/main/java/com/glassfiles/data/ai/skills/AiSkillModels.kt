package com.glassfiles.data.ai.skills

data class AiSkillPack(
    val id: String,
    val name: String,
    val version: String,
    val author: String?,
    val description: String?,
    val whenToUse: String? = null,
    val source: String?,
    val risk: AiSkillRisk,
    val permissions: List<String>,
    val tools: List<String>,
    val minAppVersion: Int?,
    val installedAt: Long,
    val enabled: Boolean,
    val trusted: Boolean,
)

data class AiSkill(
    val id: String,
    val packId: String,
    val name: String,
    val description: String?,
    val whenToUse: String? = null,
    val category: String,
    val risk: AiSkillRisk,
    val triggers: List<String>,
    val tools: List<String>,
    val permissions: List<String>,
    val instructions: String,
    val enabled: Boolean,
)

enum class AiSkillRisk {
    READ_ONLY,
    LOW,
    MEDIUM,
    HIGH,
    DANGEROUS;

    companion object {
        fun parse(value: String?): AiSkillRisk =
            values().firstOrNull { it.name.equals(value.orEmpty().replace("-", "_"), ignoreCase = true) }
                ?: LOW
    }
}

data class AiSkillImportPreview(
    val tempDirPath: String,
    val pack: AiSkillPack,
    val skills: List<AiSkill>,
    val warnings: List<String>,
)

data class AppAgentContext(
    val repoFullName: String?,
    val chatOnly: Boolean,
)

data class AiSkillMatch(
    val skill: AiSkill,
    val confidence: Float,
    val reason: String,
)
