package com.glassfiles.data.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.glassfiles.data.ArchiveHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile

data class AiPreparedAttachment(
    val name: String,
    val mimeType: String,
    val extension: String,
    val tempPath: String,
    val isArchive: Boolean,
    val promptContent: String,
    val previewContent: String?,
    val summary: String,
)

object AiAttachmentProcessor {
    private const val MAX_TEXT_CHARS = 18_000
    private const val MAX_PREVIEW_CHARS = 2_400
    private const val MAX_ARCHIVE_FILES = 80
    private const val MAX_ARCHIVE_FILE_CHARS = 6_000

    private val textExtensions = setOf(
        "txt", "md", "markdown", "kt", "kts", "java", "dart", "gradle",
        "xml", "json", "yaml", "yml", "toml", "properties", "ini", "conf",
        "html", "css", "js", "jsx", "ts", "tsx", "py", "sh", "bash", "zsh",
        "go", "rs", "c", "cpp", "h", "hpp", "cs", "swift", "sql", "log",
        "diff", "patch", "gitignore", "dockerfile",
    )
    private val archiveExtensions = setOf("zip", "jar", "aar", "7z", "tar", "gz", "tgz", "rar")

    suspend fun prepare(context: Context, uri: Uri): AiPreparedAttachment = withContext(Dispatchers.IO) {
        val name = displayName(context, uri).ifBlank { "attachment" }
        val mime = context.contentResolver.getType(uri).orEmpty()
        val ext = extensionFor(name)
        val tempFile = copyToTemp(context, uri, name)
        if (ext in archiveExtensions || mime.contains("zip", true) || mime.contains("archive", true)) {
            prepareArchive(tempFile, name, mime, ext)
        } else {
            prepareTextLike(tempFile, name, mime, ext)
        }
    }

    fun isImage(mimeType: String, name: String): Boolean {
        val ext = extensionFor(name)
        return mimeType.startsWith("image/") || ext in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
    }

    fun extensionFor(name: String): String =
        name.substringAfterLast('.', "").lowercase(Locale.US)

    private fun prepareTextLike(file: File, name: String, mime: String, ext: String): AiPreparedAttachment {
        val readable = ext in textExtensions || mime.startsWith("text/") || mime.contains("json", true) || mime.contains("xml", true)
        val text = if (readable) {
            runCatching { file.readText(Charsets.UTF_8) }.getOrElse { file.readBytes().decodeToString() }
        } else {
            "[binary file omitted: $name, ${formatBytes(file.length())}]"
        }
        val clipped = text.take(MAX_TEXT_CHARS).let {
            if (text.length > MAX_TEXT_CHARS) "$it\n...[truncated ${text.length - MAX_TEXT_CHARS} chars]" else it
        }
        val prompt = "Attached file: $name (${formatBytes(file.length())})\n```$ext\n$clipped\n```"
        return AiPreparedAttachment(
            name = name,
            mimeType = mime,
            extension = ext,
            tempPath = file.absolutePath,
            isArchive = false,
            promptContent = prompt,
            previewContent = if (readable) clipped.take(MAX_PREVIEW_CHARS) else null,
            summary = if (readable) "$name · ${formatBytes(file.length())}" else "$name · binary · ${formatBytes(file.length())}",
        )
    }

    private suspend fun prepareArchive(file: File, name: String, mime: String, ext: String): AiPreparedAttachment {
        if (ext in setOf("zip", "jar", "aar") || mime.contains("zip", true)) {
            prepareZipArchive(file, name, mime, ext)?.let { return it }
        }
        val outDir = ArchiveHelper.decompress(file)
        if (outDir == null) {
            val prompt = "Attached archive: $name (${formatBytes(file.length())}). Extraction is not available for .$ext in this build."
            return AiPreparedAttachment(
                name = name,
                mimeType = mime,
                extension = ext,
                tempPath = file.absolutePath,
                isArchive = true,
                promptContent = prompt,
                previewContent = null,
                summary = "$name · archive · extraction unavailable",
            )
        }
        val files = outDir.walkTopDown()
            .filter { it.isFile }
            .take(MAX_ARCHIVE_FILES + 1)
            .toList()
        val shown = files.take(MAX_ARCHIVE_FILES)
        val more = (files.size - shown.size).coerceAtLeast(0)
        val sections = shown.map { child ->
            val rel = child.relativeTo(outDir).path.replace('\\', '/')
            val childExt = extensionFor(child.name)
            if (childExt in textExtensions && child.length() <= 700_000) {
                val body = runCatching { child.readText(Charsets.UTF_8) }
                    .getOrElse { "[read error: ${it.message ?: it.javaClass.simpleName}]" }
                    .take(MAX_ARCHIVE_FILE_CHARS)
                "### $rel\n```$childExt\n$body\n```"
            } else {
                "### $rel\n[binary or unsupported: ${formatBytes(child.length())}]"
            }
        }
        val listing = shown.joinToString("\n") { "- ${it.relativeTo(outDir).path.replace('\\', '/')} (${formatBytes(it.length())})" }
        val prompt = buildString {
            appendLine("Attached archive: $name (${formatBytes(file.length())})")
            appendLine("Extracted files:")
            appendLine(listing)
            if (more > 0) appendLine("- ... $more more file(s)")
            appendLine()
            append(sections.joinToString("\n\n"))
        }.take(MAX_TEXT_CHARS)
        return AiPreparedAttachment(
            name = name,
            mimeType = mime,
            extension = ext,
            tempPath = file.absolutePath,
            isArchive = true,
            promptContent = prompt,
            previewContent = null,
            summary = "$name · archive · ${shown.size}${if (more > 0) "+" else ""} file(s)",
        )
    }

    private fun prepareZipArchive(file: File, name: String, mime: String, ext: String): AiPreparedAttachment? =
        runCatching {
            ZipFile(file).use { zip ->
                val entries = zip.entries().asSequence()
                    .filter { !it.isDirectory }
                    .take(MAX_ARCHIVE_FILES + 1)
                    .toList()
                val shown = entries.take(MAX_ARCHIVE_FILES)
                val more = (entries.size - shown.size).coerceAtLeast(0)
                val listing = shown.joinToString("\n") { "- ${it.name} (${formatBytes(it.size.coerceAtLeast(0L))})" }
                val sections = shown.map { entry ->
                    val childExt = extensionFor(entry.name)
                    val size = entry.size.coerceAtLeast(0L)
                    if (childExt in textExtensions && size <= 700_000) {
                        val body = zip.getInputStream(entry).use { input ->
                            runCatching { input.readBytes().decodeToString() }
                                .getOrElse { "[read error: ${it.message ?: it.javaClass.simpleName}]" }
                                .take(MAX_ARCHIVE_FILE_CHARS)
                        }
                        "### ${entry.name}\n```$childExt\n$body\n```"
                    } else {
                        "### ${entry.name}\n[binary or unsupported: ${formatBytes(size)}]"
                    }
                }
                val prompt = buildString {
                    appendLine("Attached archive: $name (${formatBytes(file.length())})")
                    appendLine("Extracted files:")
                    if (listing.isNotBlank()) appendLine(listing) else appendLine("- (empty archive)")
                    if (more > 0) appendLine("- ... $more more file(s)")
                    appendLine()
                    append(sections.joinToString("\n\n"))
                }.take(MAX_TEXT_CHARS)
                AiPreparedAttachment(
                    name = name,
                    mimeType = mime,
                    extension = ext,
                    tempPath = file.absolutePath,
                    isArchive = true,
                    promptContent = prompt,
                    previewContent = null,
                    summary = "$name · archive · ${shown.size}${if (more > 0) "+" else ""} file(s)",
                )
            }
        }.getOrNull()

    private fun copyToTemp(context: Context, uri: Uri, name: String): File {
        val root = File(context.cacheDir, "ai_attachments").apply { mkdirs() }
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "attachment" }
        val file = File(root, "${System.currentTimeMillis()}_$safeName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to open attachment")
        return file
    }

    private fun displayName(context: Context, uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()
    }

    private fun formatBytes(bytes: Long): String =
        when {
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
            bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
}
