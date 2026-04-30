package com.glassfiles.data.ai.cost

/**
 * Static lookup from [AiCostMode] to [AiAgentLimits]. The numbers are
 * the spec'd defaults — they are intentionally *not* user-tunable in
 * the first cut. If we later want a "Custom" mode we add a fourth
 * enum value plus a stored override blob in [AiCostModeStore].
 *
 *  - **Eco**: very small context. Suitable for a free / low-credit
 *             provider key. Most tasks finish in 5-15 tool calls.
 *  - **Balanced**: comfortable for everyday repo questions. Will not
 *             auto-read very large files but otherwise just works.
 *  - **MaxQuality**: deep repository analysis. Larger files allowed,
 *             more iterations, bigger diffs.
 *
 * Skiplists ([SKIP_FOLDERS], [SECRET_FILE_PATTERNS]) live here too
 * because they are mode-independent — even MaxQuality should not be
 * silently shipping `local.properties` to OpenAI.
 */
object AiCostPolicy {
    val ECO = AiAgentLimits(
        maxFilesPerTask = 10,
        maxFileSizeBytes = 80_000,
        maxTotalContextChars = 40_000,
        maxToolCalls = 15,
        maxLogLines = 500,
        maxDiffChars = 20_000,
        maxWriteProposals = 3,
    )

    val BALANCED = AiAgentLimits(
        maxFilesPerTask = 25,
        maxFileSizeBytes = 200_000,
        maxTotalContextChars = 100_000,
        maxToolCalls = 35,
        maxLogLines = 1500,
        maxDiffChars = 60_000,
        maxWriteProposals = 8,
    )

    /**
     * MaxQuality is the home for power-user / long-running tasks. The
     * `maxTotalContextChars` budget is sized at ~1M tokens (≈ 4M chars
     * at the 4-chars-per-token rule of thumb) so a long agent loop can
     * keep an entire repository's worth of read context in flight on
     * providers that natively expose 1M+ windows (GPT-4.1, Gemini 2.5
     * Pro). Models with smaller windows (Claude 200k, GPT-4o 128k) will
     * still 4xx earlier — the cap above is a *client-side* ceiling, not
     * a guarantee the model will accept the full payload.
     */
    val MAX_QUALITY = AiAgentLimits(
        maxFilesPerTask = 200,
        maxFileSizeBytes = 2_000_000,
        maxTotalContextChars = 4_000_000,
        maxToolCalls = 200,
        maxLogLines = 5000,
        maxDiffChars = 600_000,
        maxWriteProposals = 20,
    )

    fun limitsFor(mode: AiCostMode): AiAgentLimits = when (mode) {
        AiCostMode.Eco -> ECO
        AiCostMode.Balanced -> BALANCED
        AiCostMode.MaxQuality -> MAX_QUALITY
    }

    /**
     * Folders the agent must never traverse via `list_dir` /
     * `search_repo` / mass-read paths. Build / cache / IDE / vendor
     * trees: tons of bytes, ~zero useful information for a model.
     */
    val SKIP_FOLDERS: Set<String> = setOf(
        ".git",
        ".gradle",
        "build",
        "out",
        "node_modules",
        ".idea",
        ".acside",
        "cache",
        ".cache",
        "tmp",
        ".tmp",
        "dist",
        ".next",
        "target", // rust / java
    )

    /**
     * Filename patterns whose contents must NOT be sent to a provider
     * unless the user has explicitly approved this exact path. Match
     * is case-insensitive against the file's basename only — a path
     * like `src/.env.example` is matched purely by `.env.example`.
     */
    val SECRET_FILE_PATTERNS: List<Regex> = listOf(
        Regex("""^\.env(\..*)?$""", RegexOption.IGNORE_CASE),
        Regex("""^local\.properties$""", RegexOption.IGNORE_CASE),
        Regex("""^secrets?(\..*)?$""", RegexOption.IGNORE_CASE),
        Regex("""^credentials?(\..*)?$""", RegexOption.IGNORE_CASE),
        Regex(""".*\.keystore$""", RegexOption.IGNORE_CASE),
        Regex(""".*\.jks$""", RegexOption.IGNORE_CASE),
        Regex("""^google-services\.json$""", RegexOption.IGNORE_CASE),
        Regex("""^firebase-adminsdk.*\.json$""", RegexOption.IGNORE_CASE),
        Regex(""".*\.pem$""", RegexOption.IGNORE_CASE),
        Regex(""".*\.p12$""", RegexOption.IGNORE_CASE),
        Regex("""^id_rsa(\.pub)?$""", RegexOption.IGNORE_CASE),
    )

    /** Returns true when the given file path looks like a secret. */
    fun isSecretFile(path: String): Boolean {
        val name = path.substringAfterLast('/')
        return SECRET_FILE_PATTERNS.any { it.matches(name) }
    }

    /** Returns true when the given path lies inside any [SKIP_FOLDERS] directory. */
    fun isInSkipFolder(path: String): Boolean {
        val parts = path.trim('/').split('/')
        return parts.any { it in SKIP_FOLDERS }
    }
}
