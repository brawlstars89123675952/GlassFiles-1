package com.glassfiles.data.ai.agent

import android.content.Context
import android.os.Environment
import android.util.Base64
import com.glassfiles.data.ArchiveHelper
import com.glassfiles.data.TrashManager
import com.glassfiles.data.ai.AiPreparedAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

/**
 * Executes app-local file and archive tools for the AI agent.
 *
 * Relative paths are rooted in a per-session workspace under app private
 * storage. Absolute paths are accepted only when they are inside app-owned
 * roots, or inside shared external storage when [allowExternalPaths] is true
 * (repo-agent mode, where write/destructive calls pass through approval).
 */
class LocalToolExecutor(
    private val sessionId: String,
    private val repoFullName: String? = null,
    private val branch: String? = null,
    private val currentAttachment: AiPreparedAttachment? = null,
    private val allowExternalPaths: Boolean = false,
) {
    suspend fun execute(context: Context, call: AiToolCall): AiToolResult {
        val args = runCatching { JSONObject(call.argsJson) }.getOrElse { JSONObject() }
        return try {
            val output = withContext(Dispatchers.IO) {
                when (call.name) {
                    AgentTools.FILE_PICKER_CURRENT_CONTEXT.name -> currentContext(context)
                    AgentTools.LOCAL_LIST_DIR.name -> listDir(
                        context,
                        args.optString("path", ""),
                        args.optBoolean("recursive", false),
                        args.optInt("max_entries", 120),
                    )
                    AgentTools.LOCAL_READ_FILE.name -> readFile(
                        context,
                        args.getString("path"),
                        args.optInt("max_chars", 12_000),
                        args.optBoolean("base64", false),
                    )
                    AgentTools.LOCAL_WRITE_FILE.name -> writeFile(
                        context,
                        args.getString("path"),
                        args.getString("content"),
                    )
                    AgentTools.LOCAL_APPEND_FILE.name -> appendFile(
                        context,
                        args.getString("path"),
                        args.getString("content"),
                    )
                    AgentTools.LOCAL_MKDIR.name -> mkdir(context, args.getString("path"))
                    AgentTools.LOCAL_STAT.name -> stat(context, args.getString("path"))
                    AgentTools.LOCAL_REPLACE_IN_FILE.name -> replaceInFile(
                        context,
                        args.getString("path"),
                        args.getString("old_string"),
                        args.getString("new_string"),
                        args.optBoolean("replace_all", false),
                    )
                    AgentTools.LOCAL_APPLY_PATCH.name -> applyPatch(context, args.getString("patch"))
                    AgentTools.LOCAL_COPY.name -> copy(
                        context,
                        args.getString("source"),
                        args.getString("destination"),
                        args.optBoolean("overwrite", false),
                    )
                    AgentTools.LOCAL_MOVE.name -> move(
                        context,
                        args.getString("source"),
                        args.getString("destination"),
                        args.optBoolean("overwrite", false),
                    )
                    AgentTools.LOCAL_RENAME.name -> rename(
                        context,
                        args.getString("path"),
                        args.getString("new_name"),
                    )
                    AgentTools.LOCAL_DELETE_TO_TRASH.name -> deleteToTrash(context, args.getString("path"))
                    AgentTools.LOCAL_DELETE.name -> delete(context, args.getString("path"))
                    AgentTools.ARCHIVE_LIST.name -> archiveList(
                        context,
                        args.getString("path"),
                        args.optInt("max_entries", 200),
                    )
                    AgentTools.ARCHIVE_READ_FILE.name -> archiveReadFile(
                        context,
                        args.getString("path"),
                        args.getString("entry"),
                        args.optInt("max_chars", 12_000),
                    )
                    AgentTools.ARCHIVE_EXTRACT.name -> archiveExtract(
                        context,
                        args.getString("path"),
                        args.optString("destination", ""),
                    )
                    AgentTools.ARCHIVE_CREATE.name -> archiveCreate(
                        context,
                        args.optJSONArray("source_paths"),
                        args.getString("destination"),
                        args.optString("format", ""),
                    )
                    AgentTools.ARCHIVE_TEST.name -> archiveTest(context, args.getString("path"))
                    else -> "Unknown local tool: ${call.name}"
                }
            }
            AiToolResult(callId = call.id, name = call.name, output = capped(output))
        } catch (e: Exception) {
            AiToolResult(
                callId = call.id,
                name = call.name,
                output = "Error: ${e.message ?: e.javaClass.simpleName}",
                isError = true,
            )
        }
    }

    private fun currentContext(context: Context): String {
        val workspace = workspaceRoot(context).apply { mkdirs() }
        return buildString {
            appendLine("local workspace: ${workspace.absolutePath}")
            appendLine("relative paths resolve inside this workspace")
            appendLine("external absolute paths: ${if (allowExternalPaths) "enabled with approval" else "disabled in chat-only mode"}")
            repoFullName?.takeIf { it.isNotBlank() }?.let {
                appendLine("repository context: $it${branch?.takeIf { b -> b.isNotBlank() }?.let { b -> "@$b" }.orEmpty()}")
            }
            currentAttachment?.let { file ->
                appendLine()
                appendLine("current attached file:")
                appendLine("  name: ${file.name}")
                appendLine("  path: ${file.tempPath}")
                appendLine("  mime: ${file.mimeType.ifBlank { "(unknown)" }}")
                appendLine("  extension: ${file.extension.ifBlank { "(none)" }}")
                appendLine("  archive: ${file.isArchive}")
                appendLine("  summary: ${file.summary}")
            }
            val entries = workspace.listFiles()?.sortedBy { it.name.lowercase(Locale.US) }.orEmpty()
            appendLine()
            appendLine("workspace entries: ${entries.size}")
            entries.take(40).forEach { appendLine("  ${if (it.isDirectory) "[dir] " else "      "}${it.name}") }
        }.trimEnd()
    }

    private fun listDir(context: Context, path: String, recursive: Boolean, maxEntries: Int): String {
        val dir = resolve(context, path.ifBlank { "." }, mustExist = true)
        if (!dir.isDirectory) throw IllegalArgumentException("local_list_dir: not a directory: ${dir.absolutePath}")
        val cap = if (maxEntries <= 0) 120 else maxEntries.coerceIn(1, 1_000)
        val files = if (recursive) dir.walkTopDown().drop(1) else dir.listFiles().orEmpty().asSequence()
        val listed = files.take(cap + 1).toList()
        val shown = listed.take(cap)
        return buildString {
            appendLine("\$ local_list_dir ${displayPath(context, dir)}")
            if (shown.isEmpty()) {
                appendLine("(empty directory)")
            } else {
                shown.forEach { file ->
                    val tag = if (file.isDirectory) "[dir] " else "      "
                    val rel = runCatching { file.relativeTo(dir).path }.getOrDefault(file.name).replace('\\', '/')
                    appendLine("$tag$rel${if (file.isFile) "  ${formatBytes(file.length())}" else ""}")
                }
            }
            if (listed.size > cap) appendLine("[truncated: more entries omitted]")
        }.trimEnd()
    }

    private fun readFile(context: Context, path: String, maxChars: Int, base64: Boolean): String {
        val file = resolve(context, path, mustExist = true)
        if (!file.isFile) throw IllegalArgumentException("local_read_file: not a file: ${file.absolutePath}")
        val cap = if (maxChars <= 0) 12_000 else maxChars.coerceIn(1_000, 60_000)
        return if (base64) {
            val encoded = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            header("local_read_file", context, file) + encoded.take(cap)
        } else {
            val text = runCatching { file.readText(Charsets.UTF_8) }
                .getOrElse { "[binary or non-UTF8 file: ${formatBytes(file.length())}; retry with base64=true]" }
            header("local_read_file", context, file) + text.take(cap).let {
                if (text.length > cap) "$it\n[truncated: ${text.length - cap} chars omitted]" else it
            }
        }
    }

    private fun writeFile(context: Context, path: String, content: String): String {
        val file = resolve(context, path, mustExist = false)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return "Wrote ${displayPath(context, file)} (${formatBytes(file.length())}, ${content.lineCount()} lines)."
    }

    private fun appendFile(context: Context, path: String, content: String): String {
        val file = resolve(context, path, mustExist = false)
        file.parentFile?.mkdirs()
        file.appendText(content)
        return "Appended ${content.length} chars to ${displayPath(context, file)} (${formatBytes(file.length())})."
    }

    private fun mkdir(context: Context, path: String): String {
        val dir = resolve(context, path, mustExist = false)
        if (dir.exists() && !dir.isDirectory) throw IllegalArgumentException("local_mkdir: path exists and is not a directory")
        dir.mkdirs()
        return "Created directory ${displayPath(context, dir)}."
    }

    private fun stat(context: Context, path: String): String {
        val file = resolve(context, path, mustExist = true)
        val modified = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(file.lastModified()))
        return buildString {
            appendLine("path: ${displayPath(context, file)}")
            appendLine("absolute: ${file.absolutePath}")
            appendLine("type: ${if (file.isDirectory) "directory" else "file"}")
            appendLine("size: ${formatBytes(if (file.isDirectory) directorySize(file) else file.length())}")
            appendLine("modified: $modified")
            appendLine("readable: ${file.canRead()}")
            appendLine("writable: ${file.canWrite()}")
        }.trimEnd()
    }

    private fun replaceInFile(
        context: Context,
        path: String,
        oldString: String,
        newString: String,
        replaceAll: Boolean,
    ): String {
        if (oldString.isEmpty()) throw IllegalArgumentException("local_replace_in_file: old_string must not be empty")
        val file = resolve(context, path, mustExist = true)
        val current = file.readText(Charsets.UTF_8)
        val count = countOccurrences(current, oldString)
        if (count == 0) throw IllegalArgumentException("local_replace_in_file: old_string not found")
        if (!replaceAll && count > 1) {
            throw IllegalArgumentException("local_replace_in_file: old_string appears $count times; add more context or set replace_all=true")
        }
        val updated = if (replaceAll) current.replace(oldString, newString) else current.replaceFirst(oldString, newString)
        file.writeText(updated)
        return "Updated ${displayPath(context, file)} ($count match${if (count == 1) "" else "es"}${if (replaceAll) "" else ", first only"})."
    }

    private fun applyPatch(context: Context, patch: String): String {
        val lines = patch.replace("\r\n", "\n").lines()
        if (lines.firstOrNull()?.trim() != "*** Begin Patch" || lines.lastOrNull()?.trim() != "*** End Patch") {
            throw IllegalArgumentException("local_apply_patch: patch must start with *** Begin Patch and end with *** End Patch")
        }
        val changed = mutableListOf<String>()
        var i = 1
        while (i < lines.lastIndex) {
            val line = lines[i]
            when {
                line.startsWith("*** Add File: ") -> {
                    val path = line.removePrefix("*** Add File: ").trim()
                    val body = mutableListOf<String>()
                    i += 1
                    while (i < lines.lastIndex && !lines[i].startsWith("*** ")) {
                        body += lines[i].removePrefix("+")
                        i += 1
                    }
                    val file = resolve(context, path, mustExist = false)
                    file.parentFile?.mkdirs()
                    file.writeText(body.joinToString("\n"))
                    changed += "added $path"
                    continue
                }
                line.startsWith("*** Delete File: ") -> {
                    val path = line.removePrefix("*** Delete File: ").trim()
                    val file = resolve(context, path, mustExist = true)
                    file.deleteRecursively()
                    changed += "deleted $path"
                }
                line.startsWith("*** Update File: ") -> {
                    val path = line.removePrefix("*** Update File: ").trim()
                    val hunks = mutableListOf<Pair<String, String>>()
                    i += 1
                    while (i < lines.lastIndex && !lines[i].startsWith("*** ")) {
                        if (lines[i].startsWith("@@")) i += 1
                        val oldBlock = StringBuilder()
                        val newBlock = StringBuilder()
                        while (i < lines.lastIndex && !lines[i].startsWith("@@") && !lines[i].startsWith("*** ")) {
                            val h = lines[i]
                            when {
                                h.startsWith("-") -> oldBlock.appendLine(h.drop(1))
                                h.startsWith("+") -> newBlock.appendLine(h.drop(1))
                                h.startsWith(" ") -> {
                                    oldBlock.appendLine(h.drop(1))
                                    newBlock.appendLine(h.drop(1))
                                }
                            }
                            i += 1
                        }
                        if (oldBlock.isNotEmpty() || newBlock.isNotEmpty()) {
                            hunks += oldBlock.toString().trimEnd('\n') to newBlock.toString().trimEnd('\n')
                        }
                    }
                    val file = resolve(context, path, mustExist = true)
                    var text = file.readText(Charsets.UTF_8)
                    hunks.forEach { (oldBlock, newBlock) ->
                        if (oldBlock.isEmpty()) {
                            text += newBlock
                        } else if (text.contains(oldBlock)) {
                            text = text.replaceFirst(oldBlock, newBlock)
                        } else {
                            throw IllegalArgumentException("local_apply_patch: hunk not found in $path")
                        }
                    }
                    file.writeText(text)
                    changed += "updated $path"
                    continue
                }
                line.isBlank() -> {}
                else -> throw IllegalArgumentException("local_apply_patch: unsupported line: $line")
            }
            i += 1
        }
        return if (changed.isEmpty()) "Patch applied with no file changes." else "Patch applied:\n${changed.joinToString("\n")}"
    }

    private fun copy(context: Context, source: String, destination: String, overwrite: Boolean): String {
        val src = resolve(context, source, mustExist = true)
        val dst = destinationFor(context, destination, src)
        if (dst.exists() && !overwrite) throw IllegalArgumentException("local_copy: destination exists: ${dst.absolutePath}")
        if (src.isDirectory) src.copyRecursively(dst, overwrite) else {
            dst.parentFile?.mkdirs()
            src.copyTo(dst, overwrite)
        }
        return "Copied ${displayPath(context, src)} -> ${displayPath(context, dst)}."
    }

    private fun move(context: Context, source: String, destination: String, overwrite: Boolean): String {
        val src = resolve(context, source, mustExist = true)
        val dst = destinationFor(context, destination, src)
        if (dst.exists()) {
            if (!overwrite) throw IllegalArgumentException("local_move: destination exists: ${dst.absolutePath}")
            dst.deleteRecursively()
        }
        dst.parentFile?.mkdirs()
        if (!src.renameTo(dst)) {
            if (src.isDirectory) src.copyRecursively(dst, true) else src.copyTo(dst, true)
            src.deleteRecursively()
        }
        return "Moved ${displayPath(context, src)} -> ${displayPath(context, dst)}."
    }

    private fun rename(context: Context, path: String, newName: String): String {
        if (newName.contains('/') || newName.contains('\\')) throw IllegalArgumentException("local_rename: new_name must not contain path separators")
        val src = resolve(context, path, mustExist = true)
        val dst = File(src.parentFile, newName).canonicalFile
        ensureAllowed(context, dst)
        if (dst.exists()) throw IllegalArgumentException("local_rename: destination exists")
        if (!src.renameTo(dst)) throw IllegalStateException("local_rename: rename failed")
        return "Renamed ${displayPath(context, src)} -> ${displayPath(context, dst)}."
    }

    private fun deleteToTrash(context: Context, path: String): String {
        val file = resolve(context, path, mustExist = true)
        val ok = kotlinx.coroutines.runBlocking { TrashManager(context).moveToTrash(file) }
        if (!ok) throw IllegalStateException("local_delete_to_trash: move to trash failed")
        return "Moved to trash: ${displayPath(context, file)}."
    }

    private fun delete(context: Context, path: String): String {
        val file = resolve(context, path, mustExist = true)
        val ok = file.deleteRecursively()
        if (!ok) throw IllegalStateException("local_delete: delete failed")
        return "Deleted permanently: ${displayPath(context, file)}."
    }

    private fun archiveList(context: Context, path: String, maxEntries: Int): String {
        val archive = resolve(context, path, mustExist = true)
        ensureSupportedArchive(archive)
        val entries = ArchiveHelper.listContents(archive)
        val cap = if (maxEntries <= 0) 200 else maxEntries.coerceIn(1, 2_000)
        return buildString {
            appendLine("\$ archive_list ${displayPath(context, archive)}")
            appendLine("entries: ${entries.size}")
            entries.take(cap).forEach { appendLine(it) }
            if (entries.size > cap) appendLine("[truncated: ${entries.size - cap} entries omitted]")
        }.trimEnd()
    }

    private fun archiveReadFile(context: Context, path: String, entry: String, maxChars: Int): String {
        val archive = resolve(context, path, mustExist = true)
        ensureSupportedArchive(archive)
        val cleanEntry = entry.trim().trimStart('/')
        val cap = if (maxChars <= 0) 12_000 else maxChars.coerceIn(1_000, 60_000)
        val text = when (ArchiveHelper.detectFormat(archive)) {
            ArchiveHelper.ArchiveFormat.ZIP -> readZipEntry(archive, cleanEntry)
            ArchiveHelper.ArchiveFormat.TAR -> readTarEntry(archive, cleanEntry, gzip = false)
            ArchiveHelper.ArchiveFormat.TAR_GZ -> readTarEntry(archive, cleanEntry, gzip = true)
            ArchiveHelper.ArchiveFormat.SEVEN_Z -> read7zEntry(archive, cleanEntry)
            null -> throw IllegalArgumentException("archive_read_file: unsupported archive format")
        }
        return "### $cleanEntry\n" + text.take(cap).let {
            if (text.length > cap) "$it\n[truncated: ${text.length - cap} chars omitted]" else it
        }
    }

    private fun archiveExtract(context: Context, path: String, destination: String): String {
        val archive = resolve(context, path, mustExist = true)
        ensureSupportedArchive(archive)
        val extracted = kotlinx.coroutines.runBlocking { ArchiveHelper.decompress(archive) }
            ?: throw IllegalStateException("archive_extract: extraction failed")
        val finalDir = if (destination.isBlank()) {
            extracted
        } else {
            val dst = resolve(context, destination, mustExist = false)
            if (dst.exists()) dst.deleteRecursively()
            extracted.copyRecursively(dst, overwrite = true)
            extracted.deleteRecursively()
            dst
        }
        return "Extracted ${displayPath(context, archive)} -> ${displayPath(context, finalDir)}."
    }

    private fun archiveCreate(
        context: Context,
        sourcePaths: org.json.JSONArray?,
        destination: String,
        format: String,
    ): String {
        val arr = sourcePaths ?: throw IllegalArgumentException("archive_create: source_paths is required")
        if (arr.length() == 0) throw IllegalArgumentException("archive_create: source_paths must not be empty")
        val dest = resolve(context, destination, mustExist = false)
        dest.parentFile?.mkdirs()
        val archiveFormat = archiveFormatFor(dest, format)
        val tempRoot = File(workspaceRoot(context), "archive_create_${System.currentTimeMillis()}").canonicalFile
        tempRoot.mkdirs()
        var created: File? = null
        try {
            for (i in 0 until arr.length()) {
                val src = resolve(context, arr.getString(i), mustExist = true)
                val child = File(tempRoot, src.name)
                if (src.isDirectory) src.copyRecursively(child, overwrite = true) else src.copyTo(child, overwrite = true)
            }
            created = kotlinx.coroutines.runBlocking { ArchiveHelper.compress(tempRoot, archiveFormat) }
                ?: throw IllegalStateException("archive_create: compression failed")
            if (dest.exists()) dest.deleteRecursively()
            created?.copyTo(dest, overwrite = true)
        } finally {
            tempRoot.deleteRecursively()
            created?.delete()
        }
        return "Created archive ${displayPath(context, dest)} (${formatBytes(dest.length())})."
    }

    private fun archiveTest(context: Context, path: String): String {
        val archive = resolve(context, path, mustExist = true)
        ensureSupportedArchive(archive)
        val entries = ArchiveHelper.listContents(archive)
        return "Archive OK: ${displayPath(context, archive)} (${entries.size} entries)."
    }

    private fun resolve(context: Context, rawPath: String, mustExist: Boolean): File {
        val raw = rawPath.trim().ifBlank { "." }
        if (raw.contains('\u0000')) throw IllegalArgumentException("path contains NUL")
        val file = if (File(raw).isAbsolute) File(raw) else File(workspaceRoot(context), raw)
        val canonical = file.canonicalFile
        ensureAllowed(context, canonical)
        if (mustExist && !canonical.exists()) throw IllegalArgumentException("path does not exist: ${canonical.absolutePath}")
        return canonical
    }

    private fun destinationFor(context: Context, rawDestination: String, source: File): File {
        val dst = resolve(context, rawDestination, mustExist = false)
        return if (dst.exists() && dst.isDirectory) File(dst, source.name).canonicalFile.also { ensureAllowed(context, it) } else dst
    }

    private fun ensureAllowed(context: Context, file: File) {
        val canonical = file.canonicalFile
        val roots = allowedRoots(context)
        if (roots.any { root -> canonical.path == root.path || canonical.path.startsWith(root.path + File.separator) }) return
        throw IllegalArgumentException("path is outside the current local tool context: ${canonical.absolutePath}")
    }

    private fun allowedRoots(context: Context): List<File> {
        val roots = mutableListOf(
            workspaceRoot(context),
            context.cacheDir,
        )
        if (allowExternalPaths) {
            roots += context.filesDir
            context.getExternalFilesDir(null)?.let { roots += it }
            runCatching { Environment.getExternalStorageDirectory() }.getOrNull()?.let { roots += it }
        }
        return roots.mapNotNull { runCatching { it.canonicalFile }.getOrNull() }
    }

    private fun workspaceRoot(context: Context): File {
        val safeSession = sessionId.replace(Regex("[^A-Za-z0-9_.-]"), "_").ifBlank { "default" }
        return File(context.filesDir, "ai_agent_local/$safeSession").canonicalFile
    }

    private fun header(tool: String, context: Context, file: File): String =
        "\$ $tool ${displayPath(context, file)}\n  -> ${formatBytes(file.length())}, ${file.readTextLineCount()} lines\n\n"

    private fun displayPath(context: Context, file: File): String {
        val workspace = workspaceRoot(context)
        return runCatching { file.relativeTo(workspace).path.replace('\\', '/') }
            .getOrNull()
            ?.takeIf { !it.startsWith("..") && it != "." }
            ?: file.absolutePath
    }

    private fun ensureSupportedArchive(file: File) {
        if (file.extension.equals("rar", ignoreCase = true)) {
            throw IllegalArgumentException("RAR archives are not supported by the bundled archive engine in this build")
        }
        if (ArchiveHelper.detectFormat(file) == null) {
            throw IllegalArgumentException("unsupported archive format: ${file.name}")
        }
    }

    private fun readZipEntry(file: File, entryName: String): String =
        ZipFile(file).use { zip ->
            val entry = zip.getEntry(entryName) ?: throw IllegalArgumentException("entry not found: $entryName")
            zip.getInputStream(entry).use { it.readBytes().decodeToString() }
        }

    private fun readTarEntry(file: File, entryName: String, gzip: Boolean): String {
        val input = if (gzip) GZIPInputStream(BufferedInputStream(FileInputStream(file))) else BufferedInputStream(FileInputStream(file))
        org.apache.commons.compress.archivers.tar.TarArchiveInputStream(input).use { tar ->
            var entry = tar.nextTarEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name == entryName) return tar.readBytes().decodeToString()
                entry = tar.nextTarEntry
            }
        }
        throw IllegalArgumentException("entry not found: $entryName")
    }

    private fun read7zEntry(file: File, entryName: String): String {
        org.apache.commons.compress.archivers.sevenz.SevenZFile(file).use { seven ->
            var entry = seven.nextEntry
            val buffer = ByteArray(8192)
            while (entry != null) {
                if (!entry.isDirectory && entry.name == entryName) {
                    val out = java.io.ByteArrayOutputStream()
                    var read = seven.read(buffer)
                    while (read > 0) {
                        out.write(buffer, 0, read)
                        read = seven.read(buffer)
                    }
                    return out.toByteArray().decodeToString()
                }
                entry = seven.nextEntry
            }
        }
        throw IllegalArgumentException("entry not found: $entryName")
    }

    private fun archiveFormatFor(destination: File, requested: String): ArchiveHelper.ArchiveFormat {
        val value = requested.trim().lowercase(Locale.US).ifBlank { destination.name.lowercase(Locale.US) }
        return when {
            value == "zip" || value.endsWith(".zip") -> ArchiveHelper.ArchiveFormat.ZIP
            value == "tar" || value.endsWith(".tar") -> ArchiveHelper.ArchiveFormat.TAR
            value == "tar.gz" || value == "tgz" || value.endsWith(".tar.gz") || value.endsWith(".tgz") ->
                ArchiveHelper.ArchiveFormat.TAR_GZ
            value == "7z" || value.endsWith(".7z") -> ArchiveHelper.ArchiveFormat.SEVEN_Z
            else -> throw IllegalArgumentException("archive_create: unsupported format: $requested")
        }
    }

    private fun directorySize(dir: File): Long =
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun countOccurrences(haystack: String, needle: String): Int {
        var count = 0
        var idx = haystack.indexOf(needle)
        while (idx >= 0) {
            count += 1
            idx = haystack.indexOf(needle, idx + needle.length)
        }
        return count
    }

    private fun capped(text: String, max: Int = 8_000): String =
        if (text.length <= max) text else text.take(max) + "\n\n[truncated: ${text.length - max} chars omitted]"

    private fun formatBytes(bytes: Long): String =
        when {
            bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
            bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }

    private fun String.lineCount(): Int =
        if (isEmpty()) 0 else count { it == '\n' } + 1

    private fun File.readTextLineCount(): Int =
        runCatching { useLines { lines -> lines.count() } }.getOrDefault(0)
}
