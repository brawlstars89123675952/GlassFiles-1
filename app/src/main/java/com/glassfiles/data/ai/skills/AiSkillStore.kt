package com.glassfiles.data.ai.skills

import android.content.Context
import android.net.Uri
import com.glassfiles.data.ai.agent.AgentToolRegistry
import com.glassfiles.data.ai.agent.AgentTools
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

private const val TAR_BLOCK_SIZE = 512

private enum class SkillFormat {
    ZIP,
    TAR_GZ,
    UNKNOWN,
}

private object SkillArchiveDetector {
    fun detectFormat(file: File): SkillFormat {
        val header = ByteArray(4)
        val read = file.inputStream().use { it.read(header) }
        return when {
            read >= 4 &&
                header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() &&
                header[3] == 0x04.toByte() -> SkillFormat.ZIP

            read >= 2 &&
                header[0] == 0x1F.toByte() &&
                header[1] == 0x8B.toByte() -> SkillFormat.TAR_GZ

            else -> SkillFormat.UNKNOWN
        }
    }
}

object AiSkillStore {
    private const val INDEX_FILE = "index.json"
    private const val STANDARD_SKILL_FILE = "SKILL.md"
    private val defaultStandardTools = listOf(
        "list_dir",
        "read_file",
        "read_file_range",
        "search_repo",
        "edit_file",
        "write_file",
        "file_picker_current_context",
        "local_list_dir",
        "local_read_file",
        "local_write_file",
        "local_append_file",
        "local_mkdir",
        "local_read_file_range",
        "local_search_files",
        "local_search_text",
        "local_stat",
        "local_replace_in_file",
        "local_apply_patch",
        "local_copy",
        "local_move",
        "local_rename",
        "local_diff_files",
        "local_diff_text",
        "local_preview_patch",
        "local_apply_batch_patch",
        "archive_list",
        "archive_read_file",
        "archive_extract",
        "archive_create",
        "archive_test",
        "web_fetch",
        "web_search",
        "github_read_public_file",
        "github_list_public_dir",
        "skill_read",
        "artifact_write",
        "artifact_update",
        "todo_write",
        "todo_update",
    )
    private val dangerousTools: Set<String>
        get() = AgentToolRegistry.all
            .filter { it.dangerous }
            .map { it.name }
            .toSet() + setOf("root", "shizuku", "network_upload")
    private val suspiciousPatterns = listOf(
        "ignore previous instructions",
        "do not tell the user",
        "steal",
        "exfiltrate",
        "api key",
        "rm -rf",
        "curl | sh",
        "wget | sh",
        "chmod 777",
        "su",
        "shizuku",
    )
    private val toolAliases = mapOf(
        "read" to listOf("local_read_file", "local_read_file_range", "local_stat"),
        "ls" to listOf("local_list_dir"),
        "list" to listOf("local_list_dir"),
        "glob" to listOf("local_search_files", "local_list_dir"),
        "grep" to listOf("local_search_text"),
        "edit" to listOf("local_replace_in_file"),
        "multiedit" to listOf("local_apply_batch_patch"),
        "write" to listOf("local_write_file", "local_mkdir"),
        "bash" to listOf("terminal_run"),
        "shell" to listOf("terminal_run"),
        "webfetch" to listOf("web_fetch"),
        "web_fetch" to listOf("web_fetch"),
        "websearch" to listOf("web_search"),
        "web_search" to listOf("web_search"),
    )

    fun skillsRoot(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "skills").apply { mkdirs() }

    fun packsRoot(context: Context): File =
        File(skillsRoot(context), "packs").apply { mkdirs() }

    fun prepareImport(context: Context, uri: Uri): AiSkillImportPreview {
        val tempZip = File(context.cacheDir, "skill_import_${System.currentTimeMillis()}.gskill")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempZip).use { output -> input.copyTo(output) }
        } ?: error("Unable to open skill pack")
        return prepareImport(context, tempZip)
    }

    fun prepareImport(context: Context, source: File): AiSkillImportPreview {
        val format = SkillArchiveDetector.detectFormat(source)
        require(format != SkillFormat.UNKNOWN) {
            "Unsupported skill archive format. Expected ZIP/.gskill or tar.gz"
        }
        val tempDir = File(context.cacheDir, "skill_import_${System.currentTimeMillis()}").canonicalFile
        tempDir.mkdirs()
        extractArchiveSafe(source, tempDir, format)
        val manifestFile = File(tempDir, "manifest.json")
        return if (manifestFile.isFile) {
            prepareManifestImport(tempDir, manifestFile)
        } else {
            prepareStandardSkillImport(source, tempDir)
        }
    }

    private fun prepareManifestImport(tempDir: File, manifestFile: File): AiSkillImportPreview {
        val manifest = JSONObject(manifestFile.readText())
        val warnings = linkedSetOf<String>()
        val requestedTools = normalizeToolList(
            manifest.optStringArray("tools"),
            strict = true,
            warnings = warnings,
            sourceName = "manifest.json",
        )
        require(requestedTools.isNotEmpty()) { "manifest tools must not be empty" }

        val manifestRisk = AiSkillRisk.parse(manifest.optString("risk", "low"))
        val effectivePackRisk = escalateRisk(manifestRisk, requestedTools)
        if (effectivePackRisk.ordinal > manifestRisk.ordinal) {
            warnings += "risk escalated to ${effectivePackRisk.name.lowercase(Locale.US)} because dangerous tools were requested"
        }
        if (manifest.optString("author", "").equals("community", ignoreCase = true)) {
            warnings += "community/untrusted source"
        }
        warnings += scanSuspicious("manifest.json", manifestFile.readText())

        val packId = manifest.requireCleanId("id")
        val pack = AiSkillPack(
            id = packId,
            name = manifest.optString("name", packId).ifBlank { packId },
            version = manifest.optString("version").ifBlank { error("manifest version is required") },
            author = manifest.optString("author").takeIf { it.isNotBlank() },
            description = manifest.optString("description").takeIf { it.isNotBlank() },
            source = manifest.optString("source").takeIf { it.isNotBlank() },
            risk = effectivePackRisk,
            permissions = manifest.optStringArray("permissions"),
            tools = requestedTools,
            minAppVersion = manifest.optInt("minAppVersion").takeIf { it > 0 },
            installedAt = System.currentTimeMillis(),
            enabled = true,
            trusted = false,
        )
        manifest
            .put("tools", JSONArray(requestedTools))
            .put("risk", effectivePackRisk.name.lowercase(Locale.US))
        manifestFile.writeText(manifest.toString(2))

        val skillsDir = File(tempDir, "skills").canonicalFile
        require(skillsDir.isDirectory) { "skills/ directory is required" }
        val skills = skillsDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".skill.md", ignoreCase = true) }
            .map { file ->
                warnings += scanSuspicious(file.name, file.readText())
                parseSkillFile(file, pack, strictTools = true, standardMode = false, warnings = warnings)
            }
            .toList()
        require(skills.isNotEmpty()) { "At least one .skill.md file is required" }
        skills.forEach { skill ->
            require(skill.triggers.isNotEmpty()) { "${skill.id}: triggers are required" }
            require(skill.instructions.isNotBlank()) { "${skill.id}: instructions are required" }
            validateKnownTools(skill.tools)
            val missing = skill.tools.filterNot { it in pack.tools }
            require(missing.isEmpty()) { "${skill.id}: tools not declared in manifest: ${missing.joinToString()}" }
        }
        return AiSkillImportPreview(tempDir.absolutePath, pack, skills, warnings.toList())
    }

    private fun prepareStandardSkillImport(source: File, tempDir: File): AiSkillImportPreview {
        val warnings = linkedSetOf<String>()
        val skillFiles = findStandardSkillFiles(tempDir)
        val portableInstructionFiles = if (skillFiles.isEmpty()) findPortableInstructionFiles(tempDir) else emptyList()
        require(skillFiles.isNotEmpty() || portableInstructionFiles.isNotEmpty()) {
            "manifest.json, SKILL.md, .cursor/rules, .clinerules, AGENTS.md, CONVENTIONS.md, or .prompt.md is required"
        }

        val packId = source.nameWithoutExtension.toSkillId()
        val provisionalPack = AiSkillPack(
            id = packId,
            name = packId,
            version = "1.0.0",
            author = null,
            description = "Imported Agent Skills pack",
            source = null,
            risk = AiSkillRisk.LOW,
            permissions = emptyList(),
            tools = emptyList(),
            minAppVersion = null,
            installedAt = System.currentTimeMillis(),
            enabled = true,
            trusted = false,
        )
        val parsedSkills = skillFiles.map { file ->
            warnings += scanSuspicious(file.relativeTo(tempDir).path, file.readText())
            parseSkillFile(file, provisionalPack, strictTools = false, standardMode = true, warnings = warnings)
        }.ifEmpty {
            portableInstructionFiles.map { file ->
                warnings += scanSuspicious(file.relativeTo(tempDir).path, file.readText())
                parsePortableInstructionFile(file, tempDir, provisionalPack, warnings)
            }
        }
        val packTools = parsedSkills.flatMap { it.tools }.distinct().ifEmpty { defaultStandardTools }
        validateKnownTools(packTools)
        val effectivePackRisk = escalateRisk(AiSkillRisk.LOW, packTools)
        if (effectivePackRisk != AiSkillRisk.LOW) {
            warnings += "risk escalated to ${effectivePackRisk.name.lowercase(Locale.US)} because dangerous tools were requested"
        }
        warnings += if (skillFiles.isNotEmpty()) {
            "standard Agent Skill format detected: manifest.json was generated from SKILL.md metadata"
        } else {
            "rules/prompts format detected: manifest.json was generated from portable instruction files"
        }

        val first = parsedSkills.first()
        val pack = provisionalPack.copy(
            name = if (parsedSkills.size == 1) first.name else packId,
            description = if (parsedSkills.size == 1) first.description else "Imported Agent Skills pack (${parsedSkills.size} skills)",
            risk = effectivePackRisk,
            tools = packTools,
        )
        val skills = parsedSkills.map { skill ->
            val tools = skill.tools.ifEmpty { defaultStandardTools }
            skill.copy(
                risk = escalateRisk(skill.risk, tools),
                tools = tools,
            )
        }
        val sourceFiles = if (skillFiles.isNotEmpty()) skillFiles else portableInstructionFiles
        writeGeneratedManifest(tempDir, pack, sourceFiles.zip(skills))
        return AiSkillImportPreview(tempDir.absolutePath, pack, skills, warnings.toList())
    }

    fun commitImport(context: Context, preview: AiSkillImportPreview): AiSkillPack {
        val src = File(preview.tempDirPath).canonicalFile
        require(src.isDirectory) { "Import temp directory is missing" }
        val dest = File(packsRoot(context), preview.pack.id).canonicalFile
        require(dest.path.startsWith(packsRoot(context).canonicalPath + File.separator)) {
            "Invalid pack id"
        }
        if (dest.exists()) dest.deleteRecursively()
        src.copyRecursively(dest, overwrite = true)
        updateIndex(context) { index ->
            val packs = index.optJSONObject("packs") ?: JSONObject().also { index.put("packs", it) }
            packs.put(
                preview.pack.id,
                JSONObject()
                    .put("enabled", true)
                    .put("trusted", false)
                    .put("installedAt", preview.pack.installedAt),
            )
            val skills = index.optJSONObject("skills") ?: JSONObject().also { index.put("skills", it) }
            preview.skills.forEach { skill ->
                skills.put("${preview.pack.id}/${skill.id}", JSONObject().put("enabled", true))
            }
        }
        return preview.pack
    }

    fun listPacks(context: Context): List<AiSkillPack> {
        val index = readIndex(context)
        val packsMeta = index.optJSONObject("packs") ?: JSONObject()
        return packsRoot(context).listFiles().orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { dir ->
                runCatching {
                    val manifest = JSONObject(File(dir, "manifest.json").readText())
                    val id = manifest.requireCleanId("id")
                    val meta = packsMeta.optJSONObject(id) ?: JSONObject()
                    val tools = manifest.optStringArray("tools")
                    AiSkillPack(
                        id = id,
                        name = manifest.optString("name", id).ifBlank { id },
                        version = manifest.optString("version", ""),
                        author = manifest.optString("author").takeIf { it.isNotBlank() },
                        description = manifest.optString("description").takeIf { it.isNotBlank() },
                        source = manifest.optString("source").takeIf { it.isNotBlank() },
                        risk = escalateRisk(AiSkillRisk.parse(manifest.optString("risk", "low")), tools),
                        permissions = manifest.optStringArray("permissions"),
                        tools = tools,
                        minAppVersion = manifest.optInt("minAppVersion").takeIf { it > 0 },
                        installedAt = meta.optLong("installedAt", dir.lastModified()),
                        enabled = meta.optBoolean("enabled", true),
                        trusted = meta.optBoolean("trusted", false),
                    )
                }.getOrNull()
            }
            .sortedBy { it.name.lowercase(Locale.US) }
    }

    fun listSkills(context: Context): List<AiSkill> {
        val index = readIndex(context)
        val skillMeta = index.optJSONObject("skills") ?: JSONObject()
        return listPacks(context).flatMap { pack ->
            val dir = File(packsRoot(context), pack.id)
            File(dir, "skills").walkTopDown()
                .filter { it.isFile && it.name.endsWith(".skill.md", ignoreCase = true) }
                .mapNotNull { file ->
                    runCatching {
                        val parsed = parseSkillFile(file, pack)
                        val key = "${pack.id}/${parsed.id}"
                        parsed.copy(enabled = pack.enabled && skillMeta.optJSONObject(key)?.optBoolean("enabled", true) != false)
                    }.getOrNull()
                }
                .toList()
        }
    }

    fun readSkill(context: Context, skillId: String): AiSkill? =
        listSkills(context).firstOrNull { it.id == skillId || "${it.packId}/${it.id}" == skillId }

    fun listPackFiles(context: Context, packId: String): List<String> {
        val packDir = File(packsRoot(context), packId).canonicalFile
        if (!packDir.isDirectory) return emptyList()
        return packDir.walkTopDown()
            .filter { it.isFile }
            .mapNotNull { file ->
                runCatching { file.relativeTo(packDir).path.replace('\\', '/') }.getOrNull()
            }
            .filterNot { it == INDEX_FILE }
            .sorted()
            .toList()
    }

    fun readPackFile(context: Context, skillId: String, path: String, maxChars: Int): String {
        val skill = readSkill(context, skillId) ?: throw IllegalArgumentException("skill not found: $skillId")
        val packDir = File(packsRoot(context), skill.packId).canonicalFile
        val safePath = path.trim().replace('\\', '/')
        require(safePath.isNotBlank() && !safePath.startsWith("/") && !safePath.contains("..")) {
            "Invalid skill resource path"
        }
        val file = File(packDir, safePath).canonicalFile
        require(file.path.startsWith(packDir.path + File.separator)) { "Invalid skill resource path" }
        require(file.isFile) { "skill resource not found: $safePath" }
        val bytes = file.readBytes()
        if (bytes.any { it == 0.toByte() }) {
            return "binary skill resource: $safePath (${bytes.size} bytes)"
        }
        val cap = maxChars.coerceIn(1_000, 80_000)
        val text = bytes.toString(Charsets.UTF_8)
        return text.take(cap) + if (text.length > cap) "\n[truncated: ${text.length - cap} chars omitted]" else ""
    }

    fun setSkillEnabled(context: Context, packId: String, skillId: String, enabled: Boolean) {
        updateIndex(context) { index ->
            val skills = index.optJSONObject("skills") ?: JSONObject().also { index.put("skills", it) }
            skills.put("$packId/$skillId", JSONObject().put("enabled", enabled))
        }
    }

    fun setPackEnabled(context: Context, packId: String, enabled: Boolean) {
        updateIndex(context) { index ->
            val packs = index.optJSONObject("packs") ?: JSONObject().also { index.put("packs", it) }
            val meta = packs.optJSONObject(packId) ?: JSONObject()
            packs.put(packId, meta.put("enabled", enabled))
        }
    }

    fun deletePack(context: Context, packId: String) {
        File(packsRoot(context), packId).deleteRecursively()
        updateIndex(context) { index ->
            index.optJSONObject("packs")?.remove(packId)
            val skills = index.optJSONObject("skills") ?: return@updateIndex
            val keys = skills.keys().asSequence().toList()
            keys.filter { it.startsWith("$packId/") }.forEach { skills.remove(it) }
        }
    }

    fun allowedToolsForSkill(context: Context, skill: AiSkill): Set<String> {
        val pack = listPacks(context).firstOrNull { it.id == skill.packId }
        val allowDangerous = AiSkillPrefs.getAllowUntrustedDangerousTools(context)
        return (skill.tools + "skill_read")
            .filter { AgentToolRegistry.isKnown(it) }
            .filter { tool -> pack?.trusted == true || allowDangerous || !isDangerousTool(tool) }
            .toSet()
    }

    fun catalogPrompt(context: Context, maxChars: Int = 4_000): String {
        if (!AiSkillPrefs.getEnableSkills(context)) return ""
        val skills = listSkills(context).filter { it.enabled }
        if (skills.isEmpty()) return ""
        val selected = AiSkillPrefs.getSelectedSkillId(context)
        val body = buildString {
            appendLine("Installed AI skills catalog:")
            appendLine("Use this catalog to decide whether a skill is relevant. If the user manually selects a skill in settings, that skill is injected separately and takes priority. The user can also invoke a skill by typing /skill-id or @skill-id.")
            selected?.let { appendLine("Currently selected skill: $it") }
            skills.take(40).forEach { skill ->
                appendLine()
                appendLine("- id: ${skill.packId}/${skill.id}")
                appendLine("  name: ${skill.name}")
                skill.description?.takeIf { it.isNotBlank() }?.let { appendLine("  description: ${it.take(280)}") }
                if (skill.triggers.isNotEmpty()) appendLine("  triggers: ${skill.triggers.take(8).joinToString(", ")}")
                appendLine("  risk: ${skill.risk.name.lowercase(Locale.US)}")
                appendLine("  tools: ${skill.tools.take(14).joinToString(", ")}")
            }
            if (skills.size > 40) appendLine("\n... ${skills.size - 40} more installed skills omitted from catalog")
            appendLine()
            appendLine("To inspect a selected skill or bundled references, call skill_read with skill_id and optional path.")
        }
        return body.take(maxChars)
    }

    fun promptFor(skill: AiSkill, allowedTools: Set<String>): String = buildString {
        appendLine("Active skill:")
        appendLine("Name: ${skill.name}")
        appendLine("Pack: ${skill.packId}/${skill.id}")
        appendLine("Risk: ${skill.risk.name.lowercase(Locale.US)}")
        appendLine("Allowed tools:")
        allowedTools.sorted().forEach { appendLine("- $it") }
        appendLine("Bundled references/assets can be inspected with skill_read using skill_id '${skill.packId}/${skill.id}' and a relative path.")
        appendLine()
        appendLine("Skill instructions:")
        appendLine(skill.instructions)
    }

    fun isDangerousTool(toolName: String): Boolean =
        AgentToolRegistry.isDangerous(toolName) || toolName in dangerousTools

    private fun parseSkillFile(
        file: File,
        pack: AiSkillPack,
        strictTools: Boolean = true,
        standardMode: Boolean = false,
        warnings: MutableSet<String> = linkedSetOf(),
    ): AiSkill {
        val raw = file.readText()
        require(raw.startsWith("---")) { "${file.name}: YAML frontmatter is required" }
        val end = raw.indexOf("\n---", startIndex = 3)
        require(end > 0) { "${file.name}: closing frontmatter marker is required" }
        val yaml = raw.substring(3, end).trim()
        val instructions = raw.substring(end + 4).trim()
        val scalar = parseYamlScalars(yaml)
        val lists = parseYamlLists(yaml)
        val rawName = scalar["name"]?.takeIf { it.isNotBlank() }
        val id = scalar["id"]?.cleanId()
            ?: rawName?.toSkillId()
            ?: file.parentFile?.name?.toSkillId()
            ?: error("${file.name}: id or name is required")
        val rawTools = lists["tools"].orEmpty()
            .ifEmpty { lists["allowed-tools"].orEmpty() }
            .ifEmpty { scalar["tools"]?.splitToolScalar().orEmpty() }
            .ifEmpty { scalar["allowed-tools"]?.splitToolScalar().orEmpty() }
        val normalizedTools = normalizeToolList(
            rawTools,
            strict = strictTools,
            warnings = warnings,
            sourceName = file.name,
        )
        val description = scalar["description"]?.takeIf { it.isNotBlank() }
        if (standardMode) {
            if (rawName.isNullOrBlank()) warnings += "${file.name}: standard SKILL.md should define name"
            if (description.isNullOrBlank()) warnings += "${file.name}: standard SKILL.md should define description"
            if (rawName != null && rawName.length > 64) warnings += "${file.name}: name is longer than 64 characters"
            if (description != null && description.length > 1024) warnings += "${file.name}: description is longer than 1024 characters"
            if ((rawName.orEmpty() + description.orEmpty()).contains("<") || (rawName.orEmpty() + description.orEmpty()).contains(">")) {
                warnings += "${file.name}: name/description contains XML-like characters"
            }
        }
        return AiSkill(
            id = id,
            packId = pack.id,
            name = rawName ?: id,
            description = description,
            category = scalar["category"]?.takeIf { it.isNotBlank() } ?: "general",
            risk = escalateRisk(AiSkillRisk.parse(scalar["risk"]), normalizedTools),
            triggers = lists["triggers"].orEmpty(),
            tools = normalizedTools.ifEmpty { pack.tools },
            permissions = lists["permissions"].orEmpty().ifEmpty { pack.permissions },
            instructions = instructions,
            enabled = true,
        )
    }

    private fun findStandardSkillFiles(root: File): List<File> =
        root.walkTopDown()
            .filter { it.isFile && it.name.equals(STANDARD_SKILL_FILE, ignoreCase = true) }
            .sortedBy { it.relativeTo(root).path }
            .toList()

    private fun findPortableInstructionFiles(root: File): List<File> =
        root.walkTopDown()
            .filter { it.isFile }
            .filter { file ->
                val rel = file.relativeTo(root).path.replace('\\', '/')
                (rel.startsWith(".cursor/rules/") && file.extension.equals("mdc", ignoreCase = true)) ||
                    (rel.startsWith(".clinerules/") && file.extension.lowercase(Locale.US) in setOf("md", "txt")) ||
                    file.name.equals("AGENTS.md", ignoreCase = true) ||
                    file.name.equals("CONVENTIONS.md", ignoreCase = true) ||
                    file.name.endsWith(".prompt.md", ignoreCase = true)
            }
            .sortedBy { it.relativeTo(root).path }
            .toList()

    private fun parsePortableInstructionFile(
        file: File,
        root: File,
        pack: AiSkillPack,
        warnings: MutableSet<String>,
    ): AiSkill {
        val raw = file.readText()
        val (frontmatter, body) = splitOptionalFrontmatter(raw)
        val scalar = parseYamlScalars(frontmatter)
        val lists = parseYamlLists(frontmatter)
        val rel = file.relativeTo(root).path.replace('\\', '/')
        val id = (scalar["name"] ?: file.nameWithoutExtension).toSkillId()
        val globs = lists["globs"].orEmpty().ifEmpty { lists["paths"].orEmpty() }
        if (globs.isNotEmpty()) {
            warnings += "$rel: path/glob scope imported as description context; runtime path auto-attach is not implemented yet"
        }
        val description = scalar["description"]
            ?.takeIf { it.isNotBlank() }
            ?: when {
                rel.startsWith(".cursor/rules/") -> "Cursor rule imported from $rel"
                rel.startsWith(".clinerules/") -> "Cline rule imported from $rel"
                file.name.equals("AGENTS.md", ignoreCase = true) -> "AGENTS.md instructions imported from $rel"
                file.name.equals("CONVENTIONS.md", ignoreCase = true) -> "Aider-style conventions imported from $rel"
                file.name.endsWith(".prompt.md", ignoreCase = true) -> "Continue prompt imported from $rel"
                else -> "Imported instructions from $rel"
            }
        val instructions = buildString {
            appendLine("Source: $rel")
            if (globs.isNotEmpty()) appendLine("Original path scope: ${globs.joinToString(", ")}")
            appendLine()
            append(body.trim().ifBlank { raw.trim() })
        }.trim()
        return AiSkill(
            id = id,
            packId = pack.id,
            name = scalar["name"]?.takeIf { it.isNotBlank() } ?: id,
            description = description,
            category = "rules",
            risk = AiSkillRisk.LOW,
            triggers = listOf(id, id.replace('-', ' '), file.nameWithoutExtension.replace('-', ' ')).distinct(),
            tools = defaultStandardTools,
            permissions = emptyList(),
            instructions = instructions,
            enabled = true,
        )
    }

    private fun splitOptionalFrontmatter(raw: String): Pair<String, String> {
        if (!raw.startsWith("---")) return "" to raw
        val end = raw.indexOf("\n---", startIndex = 3)
        if (end <= 0) return "" to raw
        return raw.substring(3, end).trim() to raw.substring(end + 4).trim()
    }

    private fun writeGeneratedManifest(dir: File, pack: AiSkillPack, skillFiles: List<Pair<File, AiSkill>>) {
        File(dir, "manifest.json").writeText(
            JSONObject()
                .put("id", pack.id)
                .put("name", pack.name)
                .put("version", pack.version)
                .put("description", pack.description)
                .put("risk", pack.risk.name.lowercase(Locale.US))
                .put("permissions", JSONArray(pack.permissions))
                .put("tools", JSONArray(pack.tools))
                .put("minAppVersion", pack.minAppVersion ?: 1)
                .toString(2),
        )
        val skillsDir = File(dir, "skills").apply { mkdirs() }
        skillFiles.forEach { (_, skill) ->
            File(skillsDir, "${skill.id}.skill.md").writeText(renderSkillFile(skill))
        }
    }

    private fun renderSkillFile(skill: AiSkill): String = buildString {
        appendLine("---")
        appendLine("id: ${skill.id}")
        appendLine("name: ${skill.name}")
        skill.description?.takeIf { it.isNotBlank() }?.let { appendLine("description: ${it.replace('\n', ' ')}") }
        appendLine("category: ${skill.category}")
        appendLine("risk: ${skill.risk.name.lowercase(Locale.US)}")
        appendYamlList("triggers", skill.triggers)
        appendYamlList("tools", skill.tools)
        appendYamlList("permissions", skill.permissions)
        appendLine("---")
        appendLine()
        append(skill.instructions)
    }

    private fun StringBuilder.appendYamlList(key: String, values: List<String>) {
        if (values.isEmpty()) return
        appendLine("$key:")
        values.distinct().forEach { appendLine("  - ${it.replace('\n', ' ')}") }
    }

    private fun extractArchiveSafe(source: File, dest: File, format: SkillFormat) {
        when (format) {
            SkillFormat.ZIP -> unzipSafe(source, dest)
            SkillFormat.TAR_GZ -> untarGzSafe(source, dest)
            SkillFormat.UNKNOWN -> error("Unsupported skill archive format")
        }
    }

    private fun unzipSafe(source: File, dest: File) {
        ZipInputStream(FileInputStream(source)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                val out = safeArchiveOutputFile(dest, name, "zip")
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun untarGzSafe(source: File, dest: File) {
        GZIPInputStream(FileInputStream(source)).use { input ->
            val header = ByteArray(TAR_BLOCK_SIZE)
            while (readTarBlock(input, header)) {
                if (header.all { it == 0.toByte() }) return
                val name = tarString(header, 0, 100)
                val prefix = tarString(header, 345, 155)
                val entryName = listOf(prefix, name).filter { it.isNotBlank() }.joinToString("/")
                if (entryName.isBlank()) return
                val size = tarOctal(header, 124, 12)
                val type = header[156].toInt().toChar()
                val out = safeArchiveOutputFile(dest, entryName, "tar")
                when (type) {
                    '5' -> out.mkdirs()
                    '0', '\u0000' -> {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { output -> copyTarEntry(input, output, size) }
                    }
                    else -> skipTarBytes(input, size)
                }
                val padding = (TAR_BLOCK_SIZE - (size % TAR_BLOCK_SIZE)) % TAR_BLOCK_SIZE
                if (padding > 0) skipTarBytes(input, padding)
            }
        }
    }

    private fun safeArchiveOutputFile(dest: File, entryName: String, archiveName: String): File {
        val name = entryName.replace('\\', '/')
        require(!name.startsWith("/") && !name.contains("..")) {
            "Blocked unsafe $archiveName entry: $name"
        }
        val out = File(dest, name).canonicalFile
        require(out.path == dest.path || out.path.startsWith(dest.path + File.separator)) {
            "Blocked path traversal entry: $name"
        }
        return out
    }

    private fun readTarBlock(input: InputStream, block: ByteArray): Boolean {
        var offset = 0
        while (offset < block.size) {
            val read = input.read(block, offset, block.size - offset)
            if (read == -1) {
                if (offset == 0) return false
                error("Unexpected end of tar header")
            }
            offset += read
        }
        return true
    }

    private fun copyTarEntry(input: InputStream, output: FileOutputStream, size: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var remaining = size
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read == -1) error("Unexpected end of tar entry")
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun skipTarBytes(input: InputStream, bytes: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var remaining = bytes
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read == -1) error("Unexpected end of tar archive")
                remaining -= read
            }
        }
    }

    private fun tarString(header: ByteArray, offset: Int, length: Int): String {
        val end = (offset until offset + length)
            .firstOrNull { header[it] == 0.toByte() }
            ?: (offset + length)
        return header.copyOfRange(offset, end).toString(Charsets.UTF_8).trim()
    }

    private fun tarOctal(header: ByteArray, offset: Int, length: Int): Long {
        val value = tarString(header, offset, length).trim { it <= ' ' || it == '\u0000' }
        return value.ifBlank { "0" }.toLong(8)
    }

    private fun validateKnownTools(tools: List<String>) {
        val unknown = tools.filterNot { AgentToolRegistry.isKnown(it) }
        require(unknown.isEmpty()) { "Unknown requested tools: ${unknown.joinToString()}" }
    }

    private fun normalizeToolList(
        tools: List<String>,
        strict: Boolean,
        warnings: MutableSet<String>,
        sourceName: String,
    ): List<String> {
        val normalized = linkedSetOf<String>()
        val unknown = mutableListOf<String>()
        tools.forEach { tool ->
            val clean = tool.trim().substringBefore("(").trim()
            if (clean.isBlank()) return@forEach
            val direct = AgentTools.byName(clean)?.name
            when {
                direct != null -> normalized += direct
                toolAliases[clean.lowercase(Locale.US)] != null -> normalized += toolAliases.getValue(clean.lowercase(Locale.US))
                else -> unknown += tool
            }
        }
        if (unknown.isNotEmpty()) {
            if (strict) {
                error("Unknown requested tools: ${unknown.joinToString()}")
            } else {
                warnings += "$sourceName: ignored unsupported tools: ${unknown.joinToString()}"
            }
        }
        return normalized.filter { AgentToolRegistry.isKnown(it) }
    }

    private fun escalateRisk(risk: AiSkillRisk, tools: List<String>): AiSkillRisk =
        if (tools.any { AgentToolRegistry.isDangerous(it) || it in dangerousTools }) {
            if (risk.ordinal < AiSkillRisk.HIGH.ordinal) AiSkillRisk.HIGH else risk
        } else risk

    private fun scanSuspicious(name: String, text: String): List<String> {
        val lower = text.lowercase(Locale.US)
        return suspiciousPatterns.filter { it in lower }.map { "$name contains suspicious phrase: $it" }
    }

    private fun readIndex(context: Context): JSONObject {
        val file = File(skillsRoot(context), INDEX_FILE)
        return runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
    }

    private fun updateIndex(context: Context, block: (JSONObject) -> Unit) {
        val index = readIndex(context)
        block(index)
        File(skillsRoot(context), INDEX_FILE).writeText(index.toString(2))
    }

    private fun JSONObject.optStringArray(key: String): List<String> {
        val arr = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length())
            .mapNotNull { arr.optString(it).trim().takeIf { value -> value.isNotBlank() } }
            .distinct()
    }

    private fun JSONObject.requireCleanId(key: String): String =
        optString(key).cleanId() ?: error("$key is required")

    private fun String.cleanId(): String? {
        val clean = trim()
        if (clean.isBlank()) return null
        require(Regex("^[A-Za-z0-9_.-]+$").matches(clean)) { "Invalid id: $clean" }
        return clean
    }

    private fun String.toSkillId(): String {
        val slug = trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9_.-]+"), "-")
            .trim('-', '.', '_')
            .take(64)
            .trim('-', '.', '_')
        return slug.ifBlank { "skill-${System.currentTimeMillis()}" }
    }

    private fun String.splitToolScalar(): List<String> =
        if (contains("(")) {
            split(',', '\n')
                .map { it.trim().trim('"', '\'') }
                .filter { it.isNotBlank() }
        } else {
            split(Regex("[,\\s]+"))
                .map { it.trim().trim('"', '\'') }
                .filter { it.isNotBlank() }
        }

    private fun parseYamlScalars(yaml: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        yaml.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("-") || !line.contains(":")) return@forEach
            val key = line.substringBefore(":").trim()
            val value = line.substringAfter(":").trim().trim('"', '\'')
            if (value.isNotBlank() && !value.startsWith("[") && value != "|" && value != ">") {
                out[key] = value
            }
        }
        return out
    }

    private fun parseYamlLists(yaml: String): Map<String, List<String>> {
        val out = linkedMapOf<String, MutableList<String>>()
        var current: String? = null
        yaml.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.isBlank() -> {}
                line.contains(":") && !line.startsWith("-") -> {
                    val key = line.substringBefore(":").trim()
                    val tail = line.substringAfter(":").trim()
                    current = key
                    if (tail.startsWith("[") && tail.endsWith("]")) {
                        out.getOrPut(key) { mutableListOf() } += tail
                            .trim('[', ']')
                            .split(',')
                            .map { it.trim().trim('"', '\'') }
                            .filter { it.isNotBlank() }
                    }
                }
                line.startsWith("-") && current != null -> {
                    out.getOrPut(current!!) { mutableListOf() } += line.removePrefix("-").trim().trim('"', '\'')
                }
            }
        }
        return out
    }
}
