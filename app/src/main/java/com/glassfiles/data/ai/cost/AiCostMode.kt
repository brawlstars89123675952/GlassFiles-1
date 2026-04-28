package com.glassfiles.data.ai.cost

/**
 * User-selectable cost / quality trade-off for the AI Agent.
 *
 *  - [Eco]      — minimal context, cheap models, strict file/tool caps.
 *                 Designed to keep API spend tiny and make the agent
 *                 ask before any heavy operation.
 *  - [Balanced] — the default. Reasonable caps for everyday repo
 *                 questions; warns before clearly large operations.
 *  - [MaxQuality] — biggest allowed context, more iterations, deep
 *                 repository analysis. Suitable for "audit the whole
 *                 module" tasks. Always shows a warning before sending.
 *
 * The enum itself only carries identity. Concrete numeric caps live
 * on [AiAgentLimits], looked up via [AiCostPolicy.limitsFor].
 */
enum class AiCostMode {
    Eco,
    Balanced,
    MaxQuality;

    companion object {
        /** Default for new users — matches the spec. */
        val DEFAULT = Balanced

        /** Parses [name] tolerantly; returns [DEFAULT] on garbage input. */
        fun parse(name: String?): AiCostMode = when (name?.lowercase()) {
            "eco" -> Eco
            "balanced" -> Balanced
            "max", "maxquality", "max_quality" -> MaxQuality
            else -> DEFAULT
        }
    }
}
