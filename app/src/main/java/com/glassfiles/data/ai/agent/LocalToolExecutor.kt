package com.glassfiles.data.ai.agent

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Base64
import android.util.Log
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.glassfiles.data.ArchiveHelper
import com.glassfiles.data.TrashManager
import com.glassfiles.data.ai.AiPreparedAttachment
import com.glassfiles.data.ai.skills.AiSkillStore
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

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
    companion object {
        private const val TAG = "LocalToolExecutor"

        fun ensureSessionWorkspace(context: Context, sessionId: String): File {
            val primary = sessionWorkspaceRoot(context, sessionId)
            if (primary.ensureDirectory()) return primary

            Log.e(TAG, "Unable to create AI agent workspace: ${primary.absolutePath}")
            val fallback = fallbackSessionWorkspaceRoot(context, sessionId)
            if (fallback.ensureDirectory()) {
                Log.e(TAG, "Using fallback AI agent workspace: ${fallback.absolutePath}")
                return fallback
            }

            Log.e(TAG, "Unable to create fallback AI agent workspace: ${fallback.absolutePath}")
            throw IllegalStateException("terminal_run: unable to create working directory")
        }

        private fun sessionWorkspaceRoot(context: Context, sessionId: String): File {
            return File(context.filesDir, "ai_agent_local/${safeSessionId(sessionId)}").canonicalFile
        }

        private fun fallbackSessionWorkspaceRoot(context: Context, sessionId: String): File {
            return File(context.cacheDir, "ai_agent_local/${safeSessionId(sessionId)}").canonicalFile
        }

        private fun safeSessionId(sessionId: String): String {
            return sessionId.replace(Regex("[^A-Za-z0-9_.-]"), "_").ifBlank { "default" }
        }

        private fun File.ensureDirectory(): Boolean {
            return runCatching {
                if (exists()) isDirectory else mkdirs()
            }.getOrDefault(false)
        }
    }

    suspend fun execute(context: Context, call: AiToolCall): AiToolResult {
        val args = runCatching { JSONObject(call.argsJson) }.getOrElse { JSONObject() }
        return try {
            val output = withContext(Dispatchers.IO) {
                when (call.name) {
                    AgentTools.TOOL_SEARCH.name -> AgentToolRegistry.searchText(
                        query = args.getString("query"),
                        domain = args.optString("domain").takeIf { it.isNotBlank() },
                        includeDeferred = args.optBoolean("include_deferred", false),
                        limit = args.optInt("limit", 12),
                    )
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
                    AgentTools.LOCAL_SEARCH_FILES.name -> searchFiles(
                        context,
                        args.optString("path", ""),
                        args.getString("query"),
                        args.optInt("max_results", 80),
                    )
                    AgentTools.LOCAL_SEARCH_TEXT.name -> searchText(
                        context,
                        args.optString("path", ""),
                        args.getString("query"),
                        args.optInt("max_results", 80),
                    )
                    AgentTools.LOCAL_READ_FILE_RANGE.name -> readFileRange(
                        context,
                        args.getString("path"),
                        args.getInt("start_line"),
                        args.getInt("end_line"),
                    )
                    AgentTools.LOCAL_HASH_FILE.name -> hashFile(
                        context,
                        args.getString("path"),
                        args.optString("algorithm", "SHA-256"),
                    )
                    AgentTools.LOCAL_FIND_DUPLICATES.name -> findDuplicates(
                        context,
                        args.optString("path", ""),
                        args.optInt("max_results", 50),
                    )
                    AgentTools.LOCAL_GET_MIME.name -> getMime(context, args.getString("path"))
                    AgentTools.LOCAL_GET_METADATA.name -> getMetadata(context, args.getString("path"))
                    AgentTools.LOCAL_CREATE_TEMP_FILE.name -> createTempFile(
                        context,
                        args.optString("prefix", "agent_"),
                        args.optString("suffix", ".tmp"),
                        args.optString("content", ""),
                    )
                    AgentTools.LOCAL_DIFF_FILES.name -> diffFiles(
                        context,
                        args.getString("left_path"),
                        args.getString("right_path"),
                    )
                    AgentTools.LOCAL_DIFF_TEXT.name -> diffText(
                        args.getString("left_text"),
                        args.getString("right_text"),
                    )
                    AgentTools.LOCAL_PREVIEW_PATCH.name -> previewPatch(args.getString("patch"))
                    AgentTools.LOCAL_APPLY_BATCH_PATCH.name -> applyBatchPatch(
                        context,
                        args.optJSONArray("patches") ?: JSONArray(),
                    )
                    AgentTools.LOCAL_REVERT_FILE.name -> revertFile(context, args.getString("path"))
                    AgentTools.LOCAL_TRASH_LIST.name -> trashList(context)
                    AgentTools.LOCAL_TRASH_RESTORE.name -> trashRestore(
                        context,
                        args.optString("trash_path", ""),
                        args.optString("original_path", ""),
                    )
                    AgentTools.LOCAL_TRASH_EMPTY.name -> trashEmpty(context)
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
                    AgentTools.ARCHIVE_ADD_ENTRIES.name -> archiveAddEntries(
                        context,
                        args.getString("path"),
                        args.optJSONArray("source_paths") ?: JSONArray(),
                        args.optString("entry_prefix", ""),
                    )
                    AgentTools.ARCHIVE_DELETE_ENTRIES.name -> archiveDeleteEntries(
                        context,
                        args.getString("path"),
                        args.optJSONArray("entries") ?: JSONArray(),
                    )
                    AgentTools.ARCHIVE_UPDATE_ENTRY.name -> archiveUpdateEntry(
                        context,
                        args.getString("path"),
                        args.getString("entry"),
                        args.getString("content"),
                    )
                    AgentTools.ARCHIVE_LIST_NESTED.name -> archiveListNested(
                        context,
                        args.getString("path"),
                        args.optInt("max_entries", 200),
                    )
                    AgentTools.ARCHIVE_EXTRACT_NESTED.name -> archiveExtractNested(
                        context,
                        args.getString("path"),
                        args.optString("destination", ""),
                    )
                    AgentTools.APK_INSPECT.name -> apkInspect(context, args.getString("path"))
                    AgentTools.IMAGE_OCR.name -> imageOcr(context, args.getString("path"))
                    AgentTools.QR_SCAN_IMAGE.name -> qrScanImage(context, args.getString("path"))
                    AgentTools.EXIF_READ.name -> exifRead(context, args.getString("path"))
                    AgentTools.EXIF_REMOVE.name -> exifRemove(context, args.getString("path"))
                    AgentTools.PDF_EXTRACT_TEXT.name -> pdfExtractText(
                        context,
                        args.getString("path"),
                        args.optInt("max_chars", 12_000),
                    )
                    AgentTools.MEDIA_GET_INFO.name -> mediaGetInfo(context, args.getString("path"))
                    AgentTools.STORAGE_ANALYZE.name -> storageAnalyze(
                        context,
                        args.optString("path", ""),
                        args.optInt("max_entries", 40),
                    )
                    AgentTools.TERMINAL_RUN.name -> terminalRun(
                        context,
                        args.getString("command"),
                        args.optJSONArray("args"),
                        args.optInt("timeout_ms", 10_000),
                    )
                    AgentTools.WEB_FETCH.name -> webFetch(
                        args.getString("url"),
                        args.optInt("maxChars", 8_000),
                    )
                    AgentTools.WEB_SEARCH.name -> webSearch(
                        args.getString("query"),
                        args.optInt("limit", 5),
                    )
                    AgentTools.GITHUB_READ_PUBLIC_FILE.name -> githubReadPublicFile(
                        args.getString("owner"),
                        args.getString("repo"),
                        args.getString("path"),
                        args.optString("ref", ""),
                        args.optInt("max_chars", 12_000),
                    )
                    AgentTools.GITHUB_LIST_PUBLIC_DIR.name -> githubListPublicDir(
                        args.getString("owner"),
                        args.getString("repo"),
                        args.optString("path", ""),
                        args.optString("ref", ""),
                    )
                    AgentTools.SKILL_IMPORT.name -> skillImportPreview(context, args.getString("path"))
                    AgentTools.SKILL_LIST.name -> skillList(context)
                    AgentTools.SKILL_READ.name -> skillRead(
                        context,
                        args.getString("skill_id"),
                        args.optString("path"),
                        args.optInt("max_chars", 20_000),
                    )
                    AgentTools.SKILL_ENABLE.name -> skillEnable(
                        context,
                        args.getString("skill_id"),
                        args.getBoolean("enabled"),
                    )
                    AgentTools.SKILL_DELETE.name -> skillDelete(context, args.getString("pack_id"))
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
        backupBeforeChange(context, file)
        file.writeText(content)
        return "Wrote ${displayPath(context, file)} (${formatBytes(file.length())}, ${content.lineCount()} lines)."
    }

    private fun appendFile(context: Context, path: String, content: String): String {
        val file = resolve(context, path, mustExist = false)
        file.parentFile?.mkdirs()
        backupBeforeChange(context, file)
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
        backupBeforeChange(context, file)
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
                    backupBeforeChange(context, file)
                    file.writeText(body.joinToString("\n"))
                    changed += "added $path"
                    continue
                }
                line.startsWith("*** Delete File: ") -> {
                    val path = line.removePrefix("*** Delete File: ").trim()
                    val file = resolve(context, path, mustExist = true)
                    backupBeforeChange(context, file)
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
                    backupBeforeChange(context, file)
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
        downloadsTargetFor(destination, src.name)?.let { target ->
            if (src.isDirectory) {
                throw IllegalArgumentException("local_copy: export to Downloads supports files only; archive the directory first")
            }
            val exported = exportFileToDownloads(context, src, target, overwrite)
            return "Copied ${displayPath(context, src)} -> $exported."
        }
        val dst = destinationFor(context, destination, src)
        if (dst.exists() && !overwrite) throw IllegalArgumentException("local_copy: destination exists: ${dst.absolutePath}")
        if (src.isDirectory) src.copyRecursively(dst, overwrite) else {
            dst.parentFile?.mkdirs()
            backupBeforeChange(context, dst)
            src.copyTo(dst, overwrite)
        }
        return "Copied ${displayPath(context, src)} -> ${displayPath(context, dst)}."
    }

    private fun move(context: Context, source: String, destination: String, overwrite: Boolean): String {
        val src = resolve(context, source, mustExist = true)
        downloadsTargetFor(destination, src.name)?.let { target ->
            if (src.isDirectory) {
                throw IllegalArgumentException("local_move: export to Downloads supports files only; archive the directory first")
            }
            val exported = exportFileToDownloads(context, src, target, overwrite)
            backupBeforeChange(context, src)
            if (!src.delete()) throw IllegalStateException("local_move: exported to Downloads but failed to delete source")
            return "Moved ${displayPath(context, src)} -> $exported."
        }
        val dst = destinationFor(context, destination, src)
        if (dst.exists()) {
            if (!overwrite) throw IllegalArgumentException("local_move: destination exists: ${dst.absolutePath}")
            backupBeforeChange(context, dst)
            dst.deleteRecursively()
        }
        dst.parentFile?.mkdirs()
        backupBeforeChange(context, src)
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
        backupBeforeChange(context, src)
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
        backupBeforeChange(context, file)
        val ok = file.deleteRecursively()
        if (!ok) throw IllegalStateException("local_delete: delete failed")
        return "Deleted permanently: ${displayPath(context, file)}."
    }

    private fun searchFiles(context: Context, path: String, query: String, maxResults: Int): String {
        val root = resolve(context, path.ifBlank { "." }, mustExist = true)
        if (!root.isDirectory) throw IllegalArgumentException("local_search_files: path is not a directory")
        val needle = query.trim()
        if (needle.isBlank()) throw IllegalArgumentException("local_search_files: query must not be blank")
        val cap = if (maxResults <= 0) 80 else maxResults.coerceIn(1, 1_000)
        val matches = root.walkTopDown()
            .filter { it.name.contains(needle, ignoreCase = true) || it.path.contains(needle, ignoreCase = true) }
            .take(cap + 1)
            .toList()
        return buildString {
            appendLine("\$ local_search_files \"$needle\" in ${displayPath(context, root)}")
            matches.take(cap).forEach { file ->
                appendLine("${if (file.isDirectory) "[dir] " else "      "}${file.relativeTo(root).path.replace('\\', '/')}")
            }
            if (matches.size > cap) appendLine("[truncated: more matches omitted]")
            if (matches.isEmpty()) appendLine("No matches.")
        }.trimEnd()
    }

    private fun searchText(context: Context, path: String, query: String, maxResults: Int): String {
        val root = resolve(context, path.ifBlank { "." }, mustExist = true)
        val needle = query.trim()
        if (needle.isBlank()) throw IllegalArgumentException("local_search_text: query must not be blank")
        val cap = if (maxResults <= 0) 80 else maxResults.coerceIn(1, 500)
        val files = if (root.isFile) sequenceOf(root) else root.walkTopDown().filter { it.isFile }
        val hits = mutableListOf<String>()
        files.forEach { file ->
            if (hits.size > cap || file.length() > 2_000_000) return@forEach
            runCatching {
                file.useLines(Charsets.UTF_8) { lines ->
                    lines.forEachIndexed { index, line ->
                        if (line.contains(needle, ignoreCase = true)) {
                            val rel = runCatching { file.relativeTo(if (root.isDirectory) root else root.parentFile).path }.getOrDefault(file.name)
                            hits += "$rel:${index + 1}: ${line.trim().take(220)}"
                        }
                        if (hits.size > cap) return@useLines
                    }
                }
            }
        }
        return if (hits.isEmpty()) "No matches." else hits.take(cap).joinToString("\n") +
            if (hits.size > cap) "\n[truncated: more matches omitted]" else ""
    }

    private fun readFileRange(context: Context, path: String, startLine: Int, endLine: Int): String {
        if (startLine < 1 || endLine < startLine) throw IllegalArgumentException("invalid line range")
        val file = resolve(context, path, mustExist = true)
        val lines = file.readLines(Charsets.UTF_8)
        val from = (startLine - 1).coerceIn(0, lines.size)
        val to = endLine.coerceIn(from, lines.size)
        if (from >= lines.size) return "(file has ${lines.size} line(s); requested range is past the end)"
        val width = to.toString().length
        return lines.subList(from, to).mapIndexed { i, line ->
            "${(from + i + 1).toString().padStart(width)}: $line"
        }.joinToString("\n")
    }

    private fun hashFile(context: Context, path: String, algorithm: String): String {
        val file = resolve(context, path, mustExist = true)
        if (!file.isFile) throw IllegalArgumentException("local_hash_file: not a file")
        val algo = when (algorithm.replace("-", "").lowercase(Locale.US)) {
            "md5" -> "MD5"
            "sha1" -> "SHA-1"
            "sha512" -> "SHA-512"
            else -> "SHA-256"
        }
        val digest = MessageDigest.getInstance(algo)
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            var read = input.read(buffer)
            while (read > 0) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return "$algo ${displayPath(context, file)}\n${digest.digest().joinToString("") { "%02x".format(it) }}"
    }

    private fun findDuplicates(context: Context, path: String, maxResults: Int): String {
        val root = resolve(context, path.ifBlank { "." }, mustExist = true)
        val cap = if (maxResults <= 0) 50 else maxResults.coerceIn(1, 500)
        val files = (if (root.isFile) sequenceOf(root) else root.walkTopDown().filter { it.isFile }).toList()
        val candidates = files.groupBy { it.length() }.filterKeys { it > 0 }.values.filter { it.size > 1 }
        val duplicates = mutableListOf<List<File>>()
        candidates.forEach { group ->
            group.groupBy { sha256(it) }.values.filter { it.size > 1 }.forEach { duplicates += it }
        }
        if (duplicates.isEmpty()) return "No duplicates found."
        return buildString {
            duplicates.take(cap).forEachIndexed { index, group ->
                appendLine("duplicate group ${index + 1}: ${formatBytes(group.first().length())}")
                group.forEach { appendLine("  ${displayPath(context, it)}") }
            }
            if (duplicates.size > cap) appendLine("[truncated: ${duplicates.size - cap} groups omitted]")
        }.trimEnd()
    }

    private fun getMime(context: Context, path: String): String {
        val file = resolve(context, path, mustExist = true)
        return "${displayPath(context, file)}\n${mimeFor(file)}"
    }

    private fun getMetadata(context: Context, path: String): String {
        val file = resolve(context, path, mustExist = true)
        return buildString {
            appendLine(stat(context, path))
            appendLine("mime: ${mimeFor(file)}")
            if (file.isFile) appendLine("sha256: ${sha256(file)}")
            if (file.isFile) {
                BitmapFactory.Options().also { options ->
                    options.inJustDecodeBounds = true
                    BitmapFactory.decodeFile(file.absolutePath, options)
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        appendLine("image: ${options.outWidth}x${options.outHeight}")
                    }
                }
            }
        }.trimEnd()
    }

    private fun createTempFile(context: Context, prefix: String, suffix: String, content: String): String {
        val file = File.createTempFile(
            prefix.ifBlank { "agent_" },
            suffix.ifBlank { ".tmp" },
            workspaceRoot(context).apply { mkdirs() },
        )
        file.writeText(content)
        return "Created temp file ${displayPath(context, file)} (${formatBytes(file.length())})."
    }

    private fun diffFiles(context: Context, leftPath: String, rightPath: String): String {
        val left = resolve(context, leftPath, mustExist = true).readText(Charsets.UTF_8)
        val right = resolve(context, rightPath, mustExist = true).readText(Charsets.UTF_8)
        return renderDiff(left, right)
    }

    private fun diffText(left: String, right: String): String =
        renderDiff(left, right)

    private fun previewPatch(patch: String): String =
        parsePatchSummary(patch).ifEmpty { listOf("Patch has no recognized file changes.") }.joinToString("\n")

    private fun applyBatchPatch(context: Context, patches: JSONArray): String {
        if (patches.length() == 0) throw IllegalArgumentException("local_apply_batch_patch: patches is empty")
        val results = mutableListOf<String>()
        for (i in 0 until patches.length()) {
            results += "patch ${i + 1}:\n${applyPatch(context, patches.getString(i))}"
        }
        return results.joinToString("\n\n")
    }

    private fun revertFile(context: Context, path: String): String {
        val target = resolve(context, path, mustExist = false)
        val backup = latestBackup(context, target)
            ?: throw IllegalArgumentException("local_revert_file: no backup found for ${displayPath(context, target)}")
        target.parentFile?.mkdirs()
        backup.copyTo(target, overwrite = true)
        return "Reverted ${displayPath(context, target)} from ${backup.name}."
    }

    private fun trashList(context: Context): String {
        val items = TrashManager(context).getTrashItems()
        if (items.isEmpty()) return "(trash empty)"
        return items.joinToString("\n") {
            "${it.trashPath}  ${if (it.isDirectory) "[dir]" else "[file]"}  ${formatBytes(it.size)}  ${it.originalPath}"
        }
    }

    private fun trashRestore(context: Context, trashPath: String, originalPath: String): String {
        val manager = TrashManager(context)
        val item = manager.getTrashItems().firstOrNull {
            (trashPath.isNotBlank() && it.trashPath == trashPath) ||
                (originalPath.isNotBlank() && it.originalPath == originalPath)
        } ?: throw IllegalArgumentException("local_trash_restore: item not found")
        val ok = kotlinx.coroutines.runBlocking { manager.restore(item) }
        if (!ok) throw IllegalStateException("local_trash_restore: restore failed")
        return "Restored ${item.originalPath}."
    }

    private fun trashEmpty(context: Context): String {
        kotlinx.coroutines.runBlocking { TrashManager(context).emptyTrash() }
        return "Trash emptied."
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
        val downloadsTarget = downloadsTargetFor(destination, sourceName = null)
        val dest = if (downloadsTarget != null) {
            File(workspaceRoot(context), downloadsTarget.displayName).canonicalFile.also { ensureAllowed(context, it) }
        } else {
            resolve(context, destination, mustExist = false)
        }
        dest.parentFile?.mkdirs()
        val archiveFormat = archiveFormatFor(
            if (downloadsTarget != null) File(downloadsTarget.displayName) else dest,
            format,
        )
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
        val exported = downloadsTarget?.let { exportFileToDownloads(context, dest, it, overwrite = true) }
        return if (exported != null) {
            "Created archive ${displayPath(context, dest)} (${formatBytes(dest.length())}) and exported to $exported."
        } else {
            "Created archive ${displayPath(context, dest)} (${formatBytes(dest.length())})."
        }
    }

    private data class DownloadsTarget(
        val displayName: String,
        val relativePath: String,
    ) {
        val label: String
            get() = relativePath.trimEnd('/') + "/" + displayName
    }

    private fun downloadsTargetFor(destination: String, sourceName: String?): DownloadsTarget? {
        val raw = destination.trim().replace('\\', '/')
        if (raw.isBlank()) return null
        val normalized = raw.trimEnd('/')
        val roots = listOf(
            runCatching { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath.replace('\\', '/') }
                .getOrDefault("/storage/emulated/0/Download"),
            "/storage/emulated/0/Download",
            "/storage/emulated/0/Downloads",
            "/sdcard/Download",
            "/sdcard/Downloads",
        ).distinct()
        val root = roots.firstOrNull { candidate ->
            normalized == candidate || normalized.startsWith("$candidate/")
        } ?: return null
        val remainder = normalized.removePrefix(root).trim('/')
        val targetPath = if (remainder.isBlank() || raw.endsWith("/")) {
            sourceName?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Downloads destination must include a file name")
        } else {
            remainder
        }
        val safeParts = targetPath.split('/').filter { it.isNotBlank() }
        require(safeParts.isNotEmpty() && safeParts.none { it == "." || it == ".." }) {
            "Invalid Downloads destination: $destination"
        }
        val displayName = safeDownloadName(safeParts.last())
        val subdir = safeParts.dropLast(1).joinToString("/")
        val relativePath = if (subdir.isBlank()) {
            Environment.DIRECTORY_DOWNLOADS
        } else {
            Environment.DIRECTORY_DOWNLOADS + "/" + subdir
        }
        return DownloadsTarget(displayName = displayName, relativePath = relativePath)
    }

    private fun exportFileToDownloads(
        context: Context,
        source: File,
        target: DownloadsTarget,
        overwrite: Boolean,
    ): String {
        if (!source.isFile) throw IllegalArgumentException("Downloads export source is not a file: ${source.absolutePath}")
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportFileToDownloadsMediaStore(context, source, target, overwrite)
            } else {
                exportFileToDownloadsLegacy(source, target, overwrite)
            }
        } catch (e: SecurityException) {
            throw IllegalStateException("Downloads export permission denied: ${e.message ?: e.javaClass.simpleName}")
        } catch (e: Exception) {
            throw IllegalStateException("Downloads export failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun exportFileToDownloadsMediaStore(
        context: Context,
        source: File,
        target: DownloadsTarget,
        overwrite: Boolean,
    ): String {
        val resolver = context.contentResolver
        if (overwrite) {
            runCatching {
                resolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                    arrayOf(target.displayName, target.relativePath.trimEnd('/') + "/"),
                )
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, target.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeForFileName(target.displayName))
            put(MediaStore.MediaColumns.RELATIVE_PATH, target.relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore insert returned null")
        resolver.openOutputStream(uri)?.use { output ->
            FileInputStream(source).use { input -> input.copyTo(output) }
        } ?: throw IllegalStateException("MediaStore openOutputStream returned null")
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return target.label
    }

    private fun exportFileToDownloadsLegacy(
        source: File,
        target: DownloadsTarget,
        overwrite: Boolean,
    ): String {
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val subdir = target.relativePath.removePrefix(Environment.DIRECTORY_DOWNLOADS).trim('/')
        val dir = if (subdir.isBlank()) root else File(root, subdir)
        dir.mkdirs()
        val dst = File(dir, target.displayName).canonicalFile
        if (dst.exists() && !overwrite) throw IllegalArgumentException("destination exists: ${dst.absolutePath}")
        source.copyTo(dst, overwrite = overwrite)
        return dst.absolutePath
    }

    private fun safeDownloadName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .trim()
            .trim('.')
            .ifBlank { "agent_file" }

    private fun mimeForFileName(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
        return when (ext) {
            "zip", "gskill", "aar" -> "application/zip"
            "jar" -> "application/java-archive"
            "7z" -> "application/x-7z-compressed"
            "tar" -> "application/x-tar"
            "gz", "tgz" -> "application/gzip"
            "rar" -> "application/vnd.rar"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "md", "markdown" -> "text/markdown"
            "txt", "kt", "java", "dart", "py", "js", "ts", "html", "css" -> "text/plain"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
    }

    private fun archiveTest(context: Context, path: String): String {
        val archive = resolve(context, path, mustExist = true)
        ensureSupportedArchive(archive)
        val entries = ArchiveHelper.listContents(archive)
        return "Archive OK: ${displayPath(context, archive)} (${entries.size} entries)."
    }

    private fun archiveAddEntries(context: Context, path: String, sourcePaths: JSONArray, entryPrefix: String): String {
        if (sourcePaths.length() == 0) throw IllegalArgumentException("archive_add_entries: source_paths must not be empty")
        val archive = resolveZipArchive(context, path)
        val entries = readZipBytes(archive).toMutableMap()
        val prefix = entryPrefix.trim('/').takeIf { it.isNotBlank() }?.plus("/").orEmpty()
        for (i in 0 until sourcePaths.length()) {
            val src = resolve(context, sourcePaths.getString(i), mustExist = true)
            val files = if (src.isDirectory) src.walkTopDown().filter { it.isFile }.toList() else listOf(src)
            files.forEach { file ->
                val rel = if (src.isDirectory) file.relativeTo(src).path else file.name
                entries[prefix + rel.replace('\\', '/')] = file.readBytes()
            }
        }
        backupBeforeChange(context, archive)
        writeZipBytes(archive, entries)
        return "Updated ${displayPath(context, archive)} with ${sourcePaths.length()} source path(s)."
    }

    private fun archiveDeleteEntries(context: Context, path: String, entriesToDelete: JSONArray): String {
        if (entriesToDelete.length() == 0) throw IllegalArgumentException("archive_delete_entries: entries must not be empty")
        val archive = resolveZipArchive(context, path)
        val entries = readZipBytes(archive).toMutableMap()
        var removed = 0
        for (i in 0 until entriesToDelete.length()) {
            if (entries.remove(entriesToDelete.getString(i).trimStart('/')) != null) removed += 1
        }
        backupBeforeChange(context, archive)
        writeZipBytes(archive, entries)
        return "Deleted $removed entr${if (removed == 1) "y" else "ies"} from ${displayPath(context, archive)}."
    }

    private fun archiveUpdateEntry(context: Context, path: String, entry: String, content: String): String {
        val archive = resolveZipArchive(context, path)
        val cleanEntry = entry.trim().trimStart('/')
        if (cleanEntry.isBlank() || cleanEntry.endsWith('/')) throw IllegalArgumentException("archive_update_entry: invalid entry")
        val entries = readZipBytes(archive).toMutableMap()
        entries[cleanEntry] = content.toByteArray(Charsets.UTF_8)
        backupBeforeChange(context, archive)
        writeZipBytes(archive, entries)
        return "Updated $cleanEntry in ${displayPath(context, archive)} (${content.length} chars)."
    }

    private fun archiveListNested(context: Context, path: String, maxEntries: Int): String {
        val archive = materializeNestedArchive(context, path)
        return archiveList(context, archive.absolutePath, maxEntries)
    }

    private fun archiveExtractNested(context: Context, path: String, destination: String): String {
        val archive = materializeNestedArchive(context, path)
        val finalDestination = destination.ifBlank { "nested_extract_${System.currentTimeMillis()}" }
        return archiveExtract(context, archive.absolutePath, finalDestination)
    }

    private fun apkInspect(context: Context, path: String): String {
        val file = resolve(context, path, mustExist = true)
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_PERMISSIONS)
            ?: throw IllegalArgumentException("apk_inspect: unable to parse APK")
        val permissions = info.requestedPermissions?.toList().orEmpty()
        return buildString {
            appendLine("package: ${info.packageName}")
            appendLine("versionName: ${info.versionName.orEmpty()}")
            appendLine("versionCode: ${if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()}")
            appendLine("permissions: ${permissions.size}")
            permissions.forEach { appendLine("  $it") }
        }.trimEnd()
    }

    private suspend fun imageOcr(context: Context, path: String): String {
        val file = resolve(context, path, mustExist = true)
        val image = InputImage.fromFilePath(context, Uri.fromFile(file))
        val result = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image).await()
        return result.text.ifBlank { "(no text detected)" }
    }

    private suspend fun qrScanImage(context: Context, path: String): String {
        val file = resolve(context, path, mustExist = true)
        val image = InputImage.fromFilePath(context, Uri.fromFile(file))
        val codes = BarcodeScanning.getClient().process(image).await()
        if (codes.isEmpty()) return "(no QR/barcodes detected)"
        return codes.joinToString("\n") { code ->
            "${code.format}: ${code.rawValue.orEmpty()}"
        }
    }

    private fun exifRead(context: Context, path: String): String {
        val file = resolve(context, path, mustExist = true)
        val exif = ExifInterface(file.absolutePath)
        val tags = listOf(
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_IMAGE_WIDTH,
            ExifInterface.TAG_IMAGE_LENGTH,
            ExifInterface.TAG_FOCAL_LENGTH,
        )
        val lines = tags.mapNotNull { tag -> exif.getAttribute(tag)?.let { "$tag: $it" } }
        return if (lines.isEmpty()) "(no common EXIF tags found)" else lines.joinToString("\n")
    }

    private fun exifRemove(context: Context, path: String): String {
        val file = resolve(context, path, mustExist = true)
        backupBeforeChange(context, file)
        val exif = ExifInterface(file.absolutePath)
        val tags = listOf(
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
        )
        tags.forEach { exif.setAttribute(it, null) }
        exif.saveAttributes()
        return "Removed common EXIF tags from ${displayPath(context, file)}."
    }

    private fun pdfExtractText(context: Context, path: String, maxChars: Int): String {
        val file = resolve(context, path, mustExist = true)
        val cap = if (maxChars <= 0) 12_000 else maxChars.coerceIn(1_000, 60_000)
        val raw = file.readBytes().toString(Charsets.ISO_8859_1)
        val text = Regex("""\(([^()]{1,500})\)""").findAll(raw)
            .map { unescapePdfString(it.groupValues[1]) }
            .filter { it.any { ch -> ch.isLetterOrDigit() } }
            .joinToString("\n")
            .ifBlank { "(no embedded text found; scanned PDFs may need OCR)" }
        return text.take(cap) + if (text.length > cap) "\n[truncated: ${text.length - cap} chars omitted]" else ""
    }

    private fun mediaGetInfo(context: Context, path: String): String {
        val file = resolve(context, path, mustExist = true)
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val keys = listOf(
                "duration_ms" to MediaMetadataRetriever.METADATA_KEY_DURATION,
                "mime" to MediaMetadataRetriever.METADATA_KEY_MIMETYPE,
                "width" to MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH,
                "height" to MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT,
                "bitrate" to MediaMetadataRetriever.METADATA_KEY_BITRATE,
                "title" to MediaMetadataRetriever.METADATA_KEY_TITLE,
                "artist" to MediaMetadataRetriever.METADATA_KEY_ARTIST,
            )
            buildString {
                appendLine("path: ${displayPath(context, file)}")
                keys.forEach { (label, key) ->
                    retriever.extractMetadata(key)?.takeIf { it.isNotBlank() }?.let { appendLine("$label: $it") }
                }
            }.trimEnd()
        } finally {
            retriever.release()
        }
    }

    private fun storageAnalyze(context: Context, path: String, maxEntries: Int): String {
        val root = resolve(context, path.ifBlank { "." }, mustExist = true)
        val files = (if (root.isFile) sequenceOf(root) else root.walkTopDown().filter { it.isFile }).toList()
        val cap = if (maxEntries <= 0) 40 else maxEntries.coerceIn(1, 200)
        val byDir = files.groupBy { it.parentFile ?: root }.mapValues { it.value.sumOf { file -> file.length() } }
            .toList().sortedByDescending { it.second }
        return buildString {
            appendLine("root: ${displayPath(context, root)}")
            appendLine("files: ${files.size}")
            appendLine("size: ${formatBytes(files.sumOf { file -> file.length() })}")
            appendLine()
            appendLine("largest files:")
            files.sortedByDescending { it.length() }.take(cap).forEach {
                appendLine("  ${formatBytes(it.length()).padStart(9)}  ${displayPath(context, it)}")
            }
            appendLine()
            appendLine("largest directories:")
            byDir.take(cap).forEach { (dir, size) ->
                appendLine("  ${formatBytes(size).padStart(9)}  ${displayPath(context, dir)}")
            }
        }.trimEnd()
    }

    private fun terminalRun(context: Context, command: String, args: JSONArray?, timeoutMs: Int): String {
        val rawCommand = command.trim()
        if (rawCommand.isBlank()) throw IllegalArgumentException("terminal_run: command must not be blank")
        val argList = buildList {
            if (args != null) {
                for (i in 0 until args.length()) add(args.getString(i))
            }
        }
        val commandLine = if (argList.isEmpty()) {
            rawCommand
        } else {
            rawCommand + argList.joinToString(separator = " ", prefix = " ") { shellQuote(it) }
        }
        val cmd = if (argList.isEmpty() || rawCommand.needsShell()) {
            listOf("/system/bin/sh", "-c", commandLine)
        } else {
            buildList {
                add(rawCommand)
                addAll(argList)
            }
        }
        val timeout = timeoutMs.coerceIn(1_000, 30_000).toLong()
        val workDir = ensureWorkspaceRoot(context)
        val stdoutFile = File.createTempFile("terminal_stdout_", ".log", workDir)
        val stderrFile = File.createTempFile("terminal_stderr_", ".log", workDir)
        val process = ProcessBuilder(cmd)
            .directory(workDir)
            .redirectOutput(stdoutFile)
            .redirectError(stderrFile)
            .start()
        try {
            val finished = process.waitFor(timeout, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                throw IllegalStateException("terminal_run: timed out after ${timeout}ms")
            }
            val stdout = stdoutFile.bufferedReader().use { readLimited(it, 8_000) }
            val stderr = stderrFile.bufferedReader().use { readLimited(it, 4_000) }
            return buildString {
                appendLine("$ $commandLine")
                appendLine("cwd: ${workDir.absolutePath}")
                appendLine("exit: ${process.exitValue()}")
                if (stdout.isNotBlank()) {
                    appendLine()
                    appendLine("stdout:")
                    appendLine(stdout.trimEnd())
                }
                if (stderr.isNotBlank()) {
                    appendLine()
                    appendLine("stderr:")
                    appendLine(stderr.trimEnd())
                }
            }.trimEnd()
        } finally {
            stdoutFile.delete()
            stderrFile.delete()
        }
    }

    private fun webFetch(url: String, maxChars: Int): String {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) throw IllegalArgumentException("web_fetch: url must not be blank")
        val parsed = URL(cleanUrl)
        val protocol = parsed.protocol.lowercase(Locale.US)
        if (protocol != "http" && protocol != "https") throw IllegalArgumentException("web_fetch: only http and https URLs are allowed")
        if (isBlockedWebHost(parsed.host)) throw IllegalArgumentException("web_fetch: local/private network hosts are not allowed")
        val cap = if (maxChars <= 0) 8_000 else maxChars.coerceIn(1_000, 20_000)
        val response = httpGet(cleanUrl, cap + 20_000)
        val readable = if (response.contentType.contains("html", ignoreCase = true)) htmlToText(response.text) else response.text
        return buildString {
            appendLine("$ web_fetch $cleanUrl")
            if (response.finalUrl != cleanUrl) appendLine("final url: ${response.finalUrl}")
            appendLine()
            append(readable.trim().take(cap).ifBlank { "(empty response)" })
        }
    }

    private fun webSearch(query: String, limit: Int): String {
        val needle = query.trim()
        if (needle.isBlank()) throw IllegalArgumentException("web_search: query must not be blank")
        val maxResults = if (limit <= 0) 5 else limit.coerceIn(1, 10)
        val response = httpGet("https://duckduckgo.com/html/?q=${URLEncoder.encode(needle, "UTF-8")}", 160_000)
        val results = parseDuckDuckGoResults(response.text).take(maxResults)
        if (results.isEmpty()) return "No results."
        return buildString {
            appendLine("$ web_search \"$needle\"")
            results.forEachIndexed { index, result ->
                appendLine()
                appendLine("${index + 1}. ${result.title}")
                appendLine(result.url)
                if (result.snippet.isNotBlank()) appendLine(result.snippet)
            }
        }.trimEnd()
    }

    private fun githubReadPublicFile(owner: String, repo: String, path: String, ref: String, maxChars: Int): String {
        val url = githubContentsUrl(owner, repo, path, ref)
        val json = JSONObject(httpGet(url, 80_000).text)
        val download = json.optString("download_url")
        if (download.isBlank()) throw IllegalArgumentException("github_read_public_file: not a file or no download_url")
        val cap = if (maxChars <= 0) 12_000 else maxChars.coerceIn(1_000, 60_000)
        val text = httpGet(download, cap + 1_000).text
        return text.take(cap) + if (text.length > cap) "\n[truncated: ${text.length - cap} chars omitted]" else ""
    }

    private fun githubListPublicDir(owner: String, repo: String, path: String, ref: String): String {
        val json = JSONArray(httpGet(githubContentsUrl(owner, repo, path, ref), 80_000).text)
        if (json.length() == 0) return "(empty directory)"
        return buildString {
            for (i in 0 until json.length()) {
                val item = json.getJSONObject(i)
                appendLine("${if (item.optString("type") == "dir") "[dir] " else "      "}${item.optString("path")} ${formatBytes(item.optLong("size", 0))}")
            }
        }.trimEnd()
    }

    private fun skillImportPreview(context: Context, path: String): String {
        val file = resolve(context, path, mustExist = true)
        val preview = AiSkillStore.prepareImport(context, file)
        return buildString {
            appendLine("IMPORT SKILL PACK")
            appendLine("name: ${preview.pack.name}")
            appendLine("version: ${preview.pack.version}")
            appendLine("author: ${preview.pack.author.orEmpty()}")
            appendLine("risk: ${preview.pack.risk.name.lowercase(Locale.US)}")
            appendLine("skills: ${preview.skills.size}")
            appendLine("tools: ${preview.pack.tools.joinToString(", ")}")
            if (preview.warnings.isNotEmpty()) {
                appendLine()
                appendLine("WARNINGS")
                preview.warnings.forEach { appendLine("! $it") }
            }
            appendLine()
            appendLine("Validated only. Install from AI Agent settings > skills so the user can confirm import.")
        }.trimEnd()
    }

    private fun skillList(context: Context): String {
        val packs = AiSkillStore.listPacks(context)
        val skills = AiSkillStore.listSkills(context)
        if (packs.isEmpty()) return "(no installed skills)"
        return buildString {
            packs.forEach { pack ->
                val packSkills = skills.filter { it.packId == pack.id }
                appendLine("${if (pack.enabled) "[✓]" else "[ ]"} ${pack.name}  ${pack.risk.name.lowercase(Locale.US)}  ${packSkills.size} skills")
                appendLine("    ${pack.author ?: "unknown"} · ${if (pack.trusted) "trusted" else "untrusted"} · ${pack.id}")
                packSkills.forEach { skill ->
                    appendLine("    - ${if (skill.enabled) "[✓]" else "[ ]"} ${skill.id}: ${skill.name}")
                }
            }
        }.trimEnd()
    }

    private fun skillRead(context: Context, skillId: String, path: String = "", maxChars: Int = 20_000): String {
        if (path.isNotBlank()) {
            return AiSkillStore.readPackFile(context, skillId, path, maxChars)
        }
        val skill = AiSkillStore.readSkill(context, skillId) ?: throw IllegalArgumentException("skill not found: $skillId")
        val files = AiSkillStore.listPackFiles(context, skill.packId)
        return buildString {
            appendLine("skill: ${skill.id}")
            appendLine("pack: ${skill.packId}")
            appendLine("name: ${skill.name}")
            appendLine("risk: ${skill.risk.name.lowercase(Locale.US)}")
            appendLine("category: ${skill.category}")
            appendLine("triggers: ${skill.triggers.joinToString(", ")}")
            appendLine("tools: ${skill.tools.joinToString(", ")}")
            if (files.isNotEmpty()) {
                appendLine()
                appendLine("bundled files:")
                files.take(80).forEach { appendLine("- $it") }
                if (files.size > 80) appendLine("- ... ${files.size - 80} more")
                appendLine("read a bundled text file with skill_read { skill_id: \"${skill.packId}/${skill.id}\", path: \"references/example.md\" }")
            }
            appendLine()
            appendLine(skill.instructions)
        }.trimEnd()
    }

    private fun skillEnable(context: Context, skillId: String, enabled: Boolean): String {
        val skill = AiSkillStore.readSkill(context, skillId) ?: throw IllegalArgumentException("skill not found: $skillId")
        AiSkillStore.setSkillEnabled(context, skill.packId, skill.id, enabled)
        return "${if (enabled) "Enabled" else "Disabled"} skill ${skill.packId}/${skill.id}."
    }

    private fun skillDelete(context: Context, packId: String): String {
        AiSkillStore.deletePack(context, packId)
        return "Deleted skill pack $packId."
    }

    private fun resolveZipArchive(context: Context, path: String): File {
        val archive = resolve(context, path, mustExist = true)
        if (ArchiveHelper.detectFormat(archive) != ArchiveHelper.ArchiveFormat.ZIP) {
            throw IllegalArgumentException("ZIP archive required for this operation")
        }
        return archive
    }

    private fun readZipBytes(archive: File): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipFile(archive).use { zip ->
            val enumeration = zip.entries()
            while (enumeration.hasMoreElements()) {
                val entry = enumeration.nextElement()
                if (!entry.isDirectory) {
                    zip.getInputStream(entry).use { entries[entry.name] = it.readBytes() }
                }
            }
        }
        return entries
    }

    private fun writeZipBytes(archive: File, entries: Map<String, ByteArray>) {
        val temp = File(archive.parentFile, "${archive.name}.tmp_${System.currentTimeMillis()}")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(temp))).use { output ->
            entries.toSortedMap().forEach { (name, bytes) ->
                output.putNextEntry(ZipEntry(name.trimStart('/')))
                output.write(bytes)
                output.closeEntry()
            }
        }
        if (archive.exists() && !archive.delete()) throw IllegalStateException("unable to replace archive")
        if (!temp.renameTo(archive)) {
            temp.copyTo(archive, overwrite = true)
            temp.delete()
        }
    }

    private fun materializeNestedArchive(context: Context, path: String): File {
        val parts = path.split('!').map { it.trim().trimStart('/') }.filter { it.isNotBlank() }
        if (parts.size < 2) throw IllegalArgumentException("nested archive path must look like outer.zip!inner.zip")
        var current = resolve(context, parts.first(), mustExist = true)
        for (i in 1 until parts.size) {
            if (ArchiveHelper.detectFormat(current) != ArchiveHelper.ArchiveFormat.ZIP) {
                throw IllegalArgumentException("nested archive traversal currently supports ZIP containers only")
            }
            val entryName = parts[i]
            val bytes = ZipFile(current).use { zip ->
                val entry = zip.getEntry(entryName) ?: throw IllegalArgumentException("nested entry not found: $entryName")
                zip.getInputStream(entry).use { it.readBytes() }
            }
            val out = File(context.cacheDir, "nested_${System.currentTimeMillis()}_${i}_${File(entryName).name}").canonicalFile
            out.parentFile?.mkdirs()
            out.writeBytes(bytes)
            current = out
        }
        ensureSupportedArchive(current)
        return current
    }

    private fun renderDiff(left: String, right: String): String {
        val lines = LineDiff.compact(LineDiff.diff(left, right), contextLines = 3)
        if (lines.all { it is LineDiff.Line.Same }) return "(no differences)"
        return lines.joinToString("\n") { line ->
            when (line) {
                null -> "@@"
                is LineDiff.Line.Add -> "+ ${line.text}"
                is LineDiff.Line.Del -> "- ${line.text}"
                is LineDiff.Line.Same -> "  ${line.text}"
            }
        }
    }

    private fun parsePatchSummary(patch: String): List<String> {
        val lines = patch.replace("\r\n", "\n").lines()
        if (lines.firstOrNull()?.trim() != "*** Begin Patch" || lines.lastOrNull()?.trim() != "*** End Patch") {
            throw IllegalArgumentException("local_preview_patch: patch must start with *** Begin Patch and end with *** End Patch")
        }
        return lines.mapNotNull { line ->
            when {
                line.startsWith("*** Add File: ") -> "create: ${line.removePrefix("*** Add File: ").trim()}"
                line.startsWith("*** Delete File: ") -> "delete: ${line.removePrefix("*** Delete File: ").trim()}"
                line.startsWith("*** Update File: ") -> "modify: ${line.removePrefix("*** Update File: ").trim()}"
                else -> null
            }
        }
    }

    private fun backupBeforeChange(context: Context, file: File) {
        if (!file.exists() || !file.isFile) return
        val dir = backupDirFor(context, file).apply { mkdirs() }
        val backup = File(dir, "${System.currentTimeMillis()}__${file.name}")
        file.copyTo(backup, overwrite = true)
    }

    private fun latestBackup(context: Context, file: File): File? =
        backupDirFor(context, file)
            .listFiles()
            ?.filter { it.isFile }
            ?.maxByOrNull { it.name.substringBefore("__").toLongOrNull() ?: it.lastModified() }

    private fun backupDirFor(context: Context, file: File): File {
        val key = sha256Hex(file.canonicalPath.toByteArray(Charsets.UTF_8)).take(32)
        return File(workspaceRoot(context), ".backups/$key").canonicalFile
    }

    private fun sha256(file: File): String =
        hashBytes(file, "SHA-256")

    private fun hashBytes(file: File, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            var read = input.read(buffer)
            while (read > 0) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun mimeFor(file: File): String {
        val byExtension = file.extension.takeIf { it.isNotBlank() }?.let { ext ->
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase(Locale.US))
        }
        return byExtension
            ?: java.net.URLConnection.guessContentTypeFromName(file.name)
            ?: if (file.isDirectory) "inode/directory" else "application/octet-stream"
    }

    private fun unescapePdfString(value: String): String =
        value.replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")

    private data class WebResponse(
        val text: String,
        val contentType: String,
        val finalUrl: String,
    )

    private data class WebSearchResult(
        val title: String,
        val url: String,
        val snippet: String,
    )

    private fun httpGet(url: String, maxChars: Int): WebResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "GlassFiles-AIAgent/1.0")
            setRequestProperty("Accept", "text/html,text/plain,application/json;q=0.9,*/*;q=0.5")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream ?: connection.inputStream else connection.inputStream
            val charset = charsetFromContentType(connection.contentType)
            val text = stream.bufferedReader(charset).use { readLimited(it, maxChars) }
            if (code !in 200..299) throw IllegalStateException("HTTP $code from $url")
            WebResponse(text, connection.contentType.orEmpty(), connection.url.toString())
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(reader: Reader, maxChars: Int): String {
        val out = StringBuilder()
        val buffer = CharArray(4096)
        while (out.length < maxChars) {
            val read = reader.read(buffer, 0, minOf(buffer.size, maxChars - out.length))
            if (read < 0) break
            out.append(buffer, 0, read)
        }
        return out.toString()
    }

    private fun String.needsShell(): Boolean =
        any { it.isWhitespace() || it in setOf(';', '&', '|', '<', '>', '*', '?', '$', '(', ')', '`', '"', '\'') }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"

    private fun charsetFromContentType(contentType: String?): Charset {
        val charset = contentType
            ?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim()
            ?.trim('"')
        return runCatching { if (charset.isNullOrBlank()) Charsets.UTF_8 else Charset.forName(charset) }
            .getOrDefault(Charsets.UTF_8)
    }

    private fun parseDuckDuckGoResults(html: String): List<WebSearchResult> {
        val anchorRegex = Regex(
            """<a[^>]+class="result__a"[^>]+href="([^"]+)"[^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val snippetRegex = Regex(
            """<a[^>]+class="result__snippet"[^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        return anchorRegex.findAll(html).mapNotNull { match ->
            val title = htmlToInlineText(match.groupValues[2]).ifBlank { return@mapNotNull null }
            val href = cleanSearchUrl(match.groupValues[1])
            if (href.isBlank()) return@mapNotNull null
            val windowEnd = minOf(html.length, match.range.last + 2_000)
            val window = html.substring(match.range.last + 1, windowEnd)
            WebSearchResult(
                title = title.take(160),
                url = href,
                snippet = snippetRegex.find(window)?.groupValues?.getOrNull(1)?.let { htmlToInlineText(it) }.orEmpty().take(260),
            )
        }.distinctBy { it.url }.toList()
    }

    private fun cleanSearchUrl(raw: String): String {
        val decoded = htmlDecode(raw)
        val absolute = when {
            decoded.startsWith("//") -> "https:$decoded"
            decoded.startsWith("/") -> "https://duckduckgo.com$decoded"
            else -> decoded
        }
        val uddg = absolute.substringAfter("uddg=", missingDelimiterValue = "")
            .substringBefore('&')
            .takeIf { it.isNotBlank() }
        return runCatching {
            if (uddg != null) URLDecoder.decode(uddg, "UTF-8") else absolute
        }.getOrDefault(absolute)
    }

    private fun htmlToText(html: String): String {
        val withoutScripts = html
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|li|h[1-6]|tr|section|article)>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
        return htmlDecode(withoutScripts)
            .lineSequence()
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun htmlToInlineText(html: String): String =
        htmlDecode(html.replace(Regex("<[^>]+>"), " "))
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun htmlDecode(text: String): String =
        text.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")

    private fun isBlockedWebHost(host: String?): Boolean {
        val h = host.orEmpty().trim().lowercase(Locale.US).removePrefix("[").removeSuffix("]")
        if (h.isBlank()) return true
        if (h == "localhost" || h.endsWith(".localhost")) return true
        if (h == "::1" || h == "0.0.0.0") return true
        if (h.startsWith("127.") || h.startsWith("10.") || h.startsWith("192.168.") || h.startsWith("169.254.")) return true
        return Regex("""^172\.(1[6-9]|2[0-9]|3[0-1])\.""").containsMatchIn(h)
    }

    private fun githubContentsUrl(owner: String, repo: String, path: String, ref: String): String {
        val cleanPath = path.trim().trim('/')
            .split('/')
            .filter { it.isNotBlank() }
            .joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val refQuery = ref.trim().takeIf { it.isNotBlank() }?.let { "?ref=${URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
        return "https://api.github.com/repos/${owner.trim()}/${repo.trim()}/contents/$cleanPath$refQuery"
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
            fallbackWorkspaceRoot(context),
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
        return sessionWorkspaceRoot(context, sessionId)
    }

    private fun fallbackWorkspaceRoot(context: Context): File {
        return fallbackSessionWorkspaceRoot(context, sessionId)
    }

    private fun ensureWorkspaceRoot(context: Context): File {
        return ensureSessionWorkspace(context, sessionId)
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
