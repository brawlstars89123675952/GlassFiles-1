package com.glassfiles.data.ai.workspace

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.glassfiles.data.ai.AiAgentMemoryStore
import com.glassfiles.data.ai.agent.LineDiff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.UUID

enum class WorkspaceState { ACTIVE, PENDING_REVIEW, COMMITTED, DISCARDED }
enum class WorkspaceFileOperation { CREATE, MODIFY, DELETE, RENAME }

data class WorkspaceRecord(
    val id: String,
    val taskId: String,
    val chatId: String,
    val repoFullName: String,
    val baseCommitSha: String,
    val state: WorkspaceState,
    val createdAt: Long,
    val updatedAt: Long,
    val committedAt: Long? = null,
    val commitSha: String? = null,
    val title: String? = null,
)

data class WorkspaceFileRecord(
    val id: Long = 0,
    val workspaceId: String,
    val path: String,
    val operation: WorkspaceFileOperation,
    val originalContentHash: String? = null,
    val newContent: String? = null,
    val newPath: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

data class WorkspaceDiff(
    val workspaceId: String,
    val changes: List<FileChange>,
) {
    val filesChanged: Int get() = changes.size
    val additions: Int get() = changes.sumOf { it.additions }
    val deletions: Int get() = changes.sumOf { it.deletions }
}

data class FileChange(
    val path: String,
    val operation: WorkspaceFileOperation,
    val originalContent: String?,
    val newContent: String?,
    val newPath: String? = null,
    val unifiedDiff: String,
    val additions: Int,
    val deletions: Int,
)

class WorkspaceConflictException(message: String) : RuntimeException(message)
class WorkspaceCommitUnavailableException(message: String) : RuntimeException(message)

interface VirtualFileSystem {
    suspend fun read(path: String): String
    suspend fun write(path: String, content: String)
    suspend fun delete(path: String)
    suspend fun rename(oldPath: String, newPath: String)
    suspend fun exists(path: String): Boolean
    suspend fun list(directory: String): List<String>
    suspend fun diff(): WorkspaceDiff
    suspend fun commit(message: String): String
    suspend fun discard()
}

interface WorkspaceCommitter {
    suspend fun commit(
        workspace: WorkspaceRecord,
        changes: List<WorkspaceFileRecord>,
        message: String,
        applyChanges: suspend () -> Unit,
    ): String
}

interface WorkspaceBackingFileSystem {
    suspend fun read(path: String): String
    suspend fun readOrNull(path: String): String?
    suspend fun write(path: String, content: String)
    suspend fun delete(path: String)
    suspend fun exists(path: String): Boolean
    suspend fun list(directory: String): List<String>
    fun normalize(path: String): String
}

class RealFileSystem(private val root: File) : WorkspaceBackingFileSystem {
    private val canonicalRoot: File = root.apply { mkdirs() }.canonicalFile

    override suspend fun read(path: String): String = withContext(Dispatchers.IO) {
        val file = resolve(path)
        if (!file.isFile) throw FileNotFoundException(path)
        file.readText()
    }

    override suspend fun readOrNull(path: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val file = resolve(path)
            if (file.isFile) file.readText() else null
        }.getOrNull()
    }

    override suspend fun write(path: String, content: String) = withContext(Dispatchers.IO) {
        val file = resolve(path)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    override suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        val file = resolve(path)
        if (file.exists() && !file.deleteRecursively()) {
            throw IllegalStateException("Failed to delete $path")
        }
    }

    override suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) {
        resolve(path).exists()
    }

    override suspend fun list(directory: String): List<String> = withContext(Dispatchers.IO) {
        val dir = resolve(directory.ifBlank { "." })
        if (!dir.exists()) return@withContext emptyList()
        if (!dir.isDirectory) throw IllegalArgumentException("$directory is not a directory")
        dir.listFiles()
            ?.map { it.relativeTo(canonicalRoot).path.replace('\\', '/') }
            ?.sorted()
            ?: emptyList()
    }

    override fun normalize(path: String): String =
        resolve(path).relativeTo(canonicalRoot).path.replace('\\', '/')

    private fun resolve(path: String): File {
        if (path.isBlank()) return canonicalRoot
        val raw = File(path)
        require(!raw.isAbsolute) { "Absolute paths are not allowed: $path" }
        require(path.split('/', '\\').none { it == ".." }) { "Parent traversal is not allowed: $path" }
        val candidate = File(canonicalRoot, path).canonicalFile
        val rootPath = canonicalRoot.path
        val candidatePath = candidate.path
        require(candidatePath == rootPath || candidatePath.startsWith(rootPath + File.separator)) {
            "Path escapes repository root: $path"
        }
        return candidate
    }
}

class WorkspaceFileSystem(
    private val workspaceId: String,
    private val realFs: WorkspaceBackingFileSystem,
    private val db: WorkspaceDatabase,
    private val committer: WorkspaceCommitter? = null,
) : VirtualFileSystem {
    private val mutex = Mutex()

    override suspend fun read(path: String): String = withWorkspaceLock {
        val clean = realFs.normalize(path)
        val wsFile = db.getFile(workspaceId, clean)
        when (wsFile?.operation) {
            WorkspaceFileOperation.CREATE,
            WorkspaceFileOperation.MODIFY -> wsFile.newContent ?: ""
            WorkspaceFileOperation.DELETE -> throw FileNotFoundException(clean)
            WorkspaceFileOperation.RENAME -> realFs.read(wsFile.newPath ?: clean)
            null -> realFs.read(clean)
        }
    }

    override suspend fun write(path: String, content: String) = withWorkspaceLock {
        val clean = realFs.normalize(path)
        val now = System.currentTimeMillis()
        val existing = db.getFile(workspaceId, clean)
        val existsInRealFs = realFs.exists(clean)
        val operation = when {
            existing?.operation == WorkspaceFileOperation.CREATE -> WorkspaceFileOperation.CREATE
            existsInRealFs -> WorkspaceFileOperation.MODIFY
            else -> WorkspaceFileOperation.CREATE
        }
        val originalHash = existing?.originalContentHash ?: if (existsInRealFs) sha256(realFs.read(clean)) else null
        db.upsertFile(
            WorkspaceFileRecord(
                id = existing?.id ?: 0,
                workspaceId = workspaceId,
                path = clean,
                operation = operation,
                originalContentHash = originalHash,
                newContent = content,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun delete(path: String) = withWorkspaceLock {
        val clean = realFs.normalize(path)
        val existing = db.getFile(workspaceId, clean)
        if (existing?.operation == WorkspaceFileOperation.CREATE) {
            db.deleteFile(workspaceId, clean)
            return@withWorkspaceLock
        }
        if (!realFs.exists(clean)) {
            throw FileNotFoundException(clean)
        }
        val now = System.currentTimeMillis()
        db.upsertFile(
            WorkspaceFileRecord(
                id = existing?.id ?: 0,
                workspaceId = workspaceId,
                path = clean,
                operation = WorkspaceFileOperation.DELETE,
                originalContentHash = existing?.originalContentHash ?: sha256(realFs.read(clean)),
                newContent = null,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun rename(oldPath: String, newPath: String) = withWorkspaceLock {
        val cleanOldPath = realFs.normalize(oldPath)
        val cleanNewPath = realFs.normalize(newPath)
        val content = when (val existing = db.getFile(workspaceId, cleanOldPath)) {
            null -> realFs.read(cleanOldPath)
            else -> when (existing.operation) {
                WorkspaceFileOperation.CREATE,
                WorkspaceFileOperation.MODIFY -> existing.newContent.orEmpty()
                WorkspaceFileOperation.DELETE -> throw FileNotFoundException(cleanOldPath)
                WorkspaceFileOperation.RENAME -> existing.newContent ?: realFs.read(cleanOldPath)
            }
        }
        if (db.getFile(workspaceId, cleanOldPath)?.operation == WorkspaceFileOperation.CREATE) {
            db.deleteFile(workspaceId, cleanOldPath)
        } else {
            val now = System.currentTimeMillis()
            val oldExisting = db.getFile(workspaceId, cleanOldPath)
            db.upsertFile(
                WorkspaceFileRecord(
                    id = oldExisting?.id ?: 0,
                    workspaceId = workspaceId,
                    path = cleanOldPath,
                    operation = WorkspaceFileOperation.DELETE,
                    originalContentHash = oldExisting?.originalContentHash ?: sha256(realFs.read(cleanOldPath)),
                    createdAt = oldExisting?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
        }
        val newExisting = db.getFile(workspaceId, cleanNewPath)
        val existsInRealFs = realFs.exists(cleanNewPath)
        val operation = if (existsInRealFs) WorkspaceFileOperation.MODIFY else WorkspaceFileOperation.CREATE
        val now = System.currentTimeMillis()
        db.upsertFile(
            WorkspaceFileRecord(
                id = newExisting?.id ?: 0,
                workspaceId = workspaceId,
                path = cleanNewPath,
                operation = operation,
                originalContentHash = newExisting?.originalContentHash ?: if (existsInRealFs) sha256(realFs.read(cleanNewPath)) else null,
                newContent = content,
                createdAt = newExisting?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun exists(path: String): Boolean = withWorkspaceLock {
        val clean = realFs.normalize(path)
        when (db.getFile(workspaceId, clean)?.operation) {
            WorkspaceFileOperation.CREATE,
            WorkspaceFileOperation.MODIFY,
            WorkspaceFileOperation.RENAME -> true
            WorkspaceFileOperation.DELETE -> false
            null -> realFs.exists(clean)
        }
    }

    override suspend fun list(directory: String): List<String> = withWorkspaceLock {
        val cleanDir = realFs.normalize(directory.ifBlank { "." }).removeSuffix("/")
        val files = realFs.list(cleanDir).toMutableSet()
        db.getFilesInDirectory(workspaceId, cleanDir).forEach { wsFile ->
            when (wsFile.operation) {
                WorkspaceFileOperation.CREATE -> files += wsFile.path
                WorkspaceFileOperation.DELETE -> files -= wsFile.path
                WorkspaceFileOperation.MODIFY,
                WorkspaceFileOperation.RENAME -> Unit
            }
        }
        files.sorted()
    }

    override suspend fun diff(): WorkspaceDiff = withWorkspaceLock {
        val files = db.getAllFiles(workspaceId)
        WorkspaceDiff(
            workspaceId = workspaceId,
            changes = files.map { wsFile ->
                val oldText = when (wsFile.operation) {
                    WorkspaceFileOperation.CREATE -> null
                    WorkspaceFileOperation.MODIFY,
                    WorkspaceFileOperation.DELETE,
                    WorkspaceFileOperation.RENAME -> realFs.readOrNull(wsFile.path)
                }
                val newText = when (wsFile.operation) {
                    WorkspaceFileOperation.DELETE -> null
                    else -> wsFile.newContent
                }
                val diffLines = LineDiff.diff(oldText.orEmpty(), newText.orEmpty())
                val stats = LineDiff.stats(diffLines)
                FileChange(
                    path = wsFile.path,
                    operation = wsFile.operation,
                    originalContent = oldText,
                    newContent = newText,
                    newPath = wsFile.newPath,
                    unifiedDiff = unifiedDiff(wsFile.path, oldText.orEmpty(), newText.orEmpty()),
                    additions = stats.added,
                    deletions = stats.removed,
                )
            },
        )
    }

    override suspend fun commit(message: String): String = withWorkspaceLock {
        val workspace = db.getWorkspace(workspaceId) ?: throw IllegalStateException("Workspace not found: $workspaceId")
        val files = db.getAllFiles(workspaceId)
        val commit = committer ?: throw WorkspaceCommitUnavailableException("Workspace committer is not wired yet")
        val sha = commit.commit(workspace, files, message) {
            applyToRealFileSystem(files)
        }
        db.markCommitted(workspaceId, sha)
        sha
    }

    override suspend fun discard(): Unit = withWorkspaceLock {
        db.deleteWorkspace(workspaceId)
    }

    private suspend fun applyToRealFileSystem(files: List<WorkspaceFileRecord>) {
        files.forEach { wsFile ->
            when (wsFile.operation) {
                WorkspaceFileOperation.CREATE,
                WorkspaceFileOperation.MODIFY,
                WorkspaceFileOperation.RENAME -> realFs.write(wsFile.path, wsFile.newContent.orEmpty())
                WorkspaceFileOperation.DELETE -> realFs.delete(wsFile.path)
            }
        }
    }

    private suspend fun <T> withWorkspaceLock(block: suspend () -> T): T {
        mutex.lock()
        return try {
            block()
        } finally {
            mutex.unlock()
        }
    }
}

class WorkspaceDatabase(context: Context) {
    private val helper = Db(context.applicationContext)

    suspend fun createWorkspace(
        taskId: String,
        chatId: String,
        repoFullName: String,
        baseCommitSha: String,
        title: String? = null,
    ): WorkspaceRecord = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val workspace = WorkspaceRecord(
            id = "ws_${UUID.randomUUID()}",
            taskId = taskId,
            chatId = chatId,
            repoFullName = repoFullName,
            baseCommitSha = baseCommitSha,
            state = WorkspaceState.ACTIVE,
            createdAt = now,
            updatedAt = now,
            title = title,
        )
        helper.writableDatabase.insert("workspaces", null, workspaceValues(workspace))
        workspace
    }

    suspend fun getWorkspace(id: String): WorkspaceRecord? = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT id, task_id, chat_id, repo_full_name, base_commit_sha, state, created_at, updated_at, committed_at, commit_sha, title FROM workspaces WHERE id = ?",
            arrayOf(id),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toWorkspace() else null
        }
    }

    suspend fun listWorkspaces(state: WorkspaceState? = null): List<WorkspaceRecord> = withContext(Dispatchers.IO) {
        val (sql, args) = if (state == null) {
            "SELECT id, task_id, chat_id, repo_full_name, base_commit_sha, state, created_at, updated_at, committed_at, commit_sha, title FROM workspaces ORDER BY updated_at DESC" to emptyArray<String>()
        } else {
            "SELECT id, task_id, chat_id, repo_full_name, base_commit_sha, state, created_at, updated_at, committed_at, commit_sha, title FROM workspaces WHERE state = ? ORDER BY updated_at DESC" to arrayOf(state.name)
        }
        helper.readableDatabase.rawQuery(sql, args).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toWorkspace())
            }
        }
    }

    suspend fun getFile(workspaceId: String, path: String): WorkspaceFileRecord? = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            """
                SELECT id, workspace_id, path, operation, original_content_hash, new_content, new_path, created_at, updated_at
                FROM workspace_files
                WHERE workspace_id = ? AND path = ?
            """.trimIndent(),
            arrayOf(workspaceId, path),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toWorkspaceFile() else null
        }
    }

    suspend fun getAllFiles(workspaceId: String): List<WorkspaceFileRecord> = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            """
                SELECT id, workspace_id, path, operation, original_content_hash, new_content, new_path, created_at, updated_at
                FROM workspace_files
                WHERE workspace_id = ?
                ORDER BY path ASC
            """.trimIndent(),
            arrayOf(workspaceId),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toWorkspaceFile())
            }
        }
    }

    suspend fun getFilesInDirectory(workspaceId: String, directory: String): List<WorkspaceFileRecord> = withContext(Dispatchers.IO) {
        val normalized = directory.trim('/').takeIf { it != "." }.orEmpty()
        val prefix = if (normalized.isBlank()) "" else "$normalized/"
        helper.readableDatabase.rawQuery(
            """
                SELECT id, workspace_id, path, operation, original_content_hash, new_content, new_path, created_at, updated_at
                FROM workspace_files
                WHERE workspace_id = ? AND path LIKE ?
                ORDER BY path ASC
            """.trimIndent(),
            arrayOf(workspaceId, "$prefix%"),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toWorkspaceFile())
            }
        }
    }

    suspend fun upsertFile(file: WorkspaceFileRecord) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.insertWithOnConflict("workspace_files", null, workspaceFileValues(file), SQLiteDatabase.CONFLICT_REPLACE)
        touchWorkspace(db, file.workspaceId)
    }

    suspend fun deleteFile(workspaceId: String, path: String) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.delete("workspace_files", "workspace_id = ? AND path = ?", arrayOf(workspaceId, path))
        touchWorkspace(db, workspaceId)
    }

    suspend fun markPendingReview(workspaceId: String) = updateState(workspaceId, WorkspaceState.PENDING_REVIEW)

    suspend fun markCommitted(workspaceId: String, commitSha: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("state", WorkspaceState.COMMITTED.name)
            put("updated_at", now)
            put("committed_at", now)
            put("commit_sha", commitSha)
        }
        helper.writableDatabase.update("workspaces", values, "id = ?", arrayOf(workspaceId))
    }

    suspend fun markDiscarded(workspaceId: String) = updateState(workspaceId, WorkspaceState.DISCARDED)

    suspend fun deleteWorkspace(workspaceId: String) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.delete("workspace_files", "workspace_id = ?", arrayOf(workspaceId))
        db.delete("workspaces", "id = ?", arrayOf(workspaceId))
    }

    private suspend fun updateState(workspaceId: String, state: WorkspaceState) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("state", state.name)
            put("updated_at", System.currentTimeMillis())
        }
        helper.writableDatabase.update("workspaces", values, "id = ?", arrayOf(workspaceId))
    }

    private fun workspaceValues(workspace: WorkspaceRecord): ContentValues =
        ContentValues().apply {
            put("id", workspace.id)
            put("task_id", workspace.taskId)
            put("chat_id", workspace.chatId)
            put("repo_full_name", workspace.repoFullName)
            put("base_commit_sha", workspace.baseCommitSha)
            put("state", workspace.state.name)
            put("created_at", workspace.createdAt)
            put("updated_at", workspace.updatedAt)
            workspace.committedAt?.let { put("committed_at", it) } ?: putNull("committed_at")
            workspace.commitSha?.let { put("commit_sha", it) } ?: putNull("commit_sha")
            workspace.title?.let { put("title", it) } ?: putNull("title")
        }

    private fun workspaceFileValues(file: WorkspaceFileRecord): ContentValues =
        ContentValues().apply {
            if (file.id > 0) put("id", file.id)
            put("workspace_id", file.workspaceId)
            put("path", file.path)
            put("operation", file.operation.name.lowercase())
            file.originalContentHash?.let { put("original_content_hash", it) } ?: putNull("original_content_hash")
            file.newContent?.let { put("new_content", it) } ?: putNull("new_content")
            file.newPath?.let { put("new_path", it) } ?: putNull("new_path")
            put("created_at", file.createdAt)
            put("updated_at", file.updatedAt)
        }

    private fun touchWorkspace(db: SQLiteDatabase, workspaceId: String) {
        val values = ContentValues().apply { put("updated_at", System.currentTimeMillis()) }
        db.update("workspaces", values, "id = ?", arrayOf(workspaceId))
    }

    private class Db(context: Context) : SQLiteOpenHelper(
        context,
        File(AiAgentMemoryStore.memoryRootDir(context).apply { mkdirs() }, DB_NAME).absolutePath,
        null,
        DB_VERSION,
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            createWorkspaceTables(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 3) createWorkspaceTables(db)
        }

        override fun onOpen(db: SQLiteDatabase) {
            super.onOpen(db)
            if (!db.isReadOnly) {
                db.execSQL("PRAGMA foreign_keys=ON")
                createWorkspaceTables(db)
            }
        }
    }

    companion object {
        private const val DB_NAME = "memory_index.db"
        private const val DB_VERSION = 3

        fun createWorkspaceTables(db: SQLiteDatabase) {
            db.execSQL(
                """
                    CREATE TABLE IF NOT EXISTS workspaces (
                        id TEXT PRIMARY KEY,
                        task_id TEXT NOT NULL,
                        chat_id TEXT NOT NULL,
                        repo_full_name TEXT NOT NULL,
                        base_commit_sha TEXT NOT NULL,
                        state TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        committed_at INTEGER,
                        commit_sha TEXT,
                        title TEXT
                    )
                """.trimIndent(),
            )
            db.execSQL(
                """
                    CREATE TABLE IF NOT EXISTS workspace_files (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        workspace_id TEXT NOT NULL,
                        path TEXT NOT NULL,
                        operation TEXT NOT NULL,
                        original_content_hash TEXT,
                        new_content TEXT,
                        new_path TEXT,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE,
                        UNIQUE(workspace_id, path)
                    )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_ws_files ON workspace_files(workspace_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_ws_state ON workspaces(state)")
        }
    }
}

private fun android.database.Cursor.toWorkspace(): WorkspaceRecord =
    WorkspaceRecord(
        id = getString(0),
        taskId = getString(1),
        chatId = getString(2),
        repoFullName = getString(3),
        baseCommitSha = getString(4),
        state = runCatching { WorkspaceState.valueOf(getString(5)) }.getOrDefault(WorkspaceState.ACTIVE),
        createdAt = getLong(6),
        updatedAt = getLong(7),
        committedAt = nullableLong(8),
        commitSha = nullableString(9),
        title = nullableString(10),
    )

private fun android.database.Cursor.toWorkspaceFile(): WorkspaceFileRecord =
    WorkspaceFileRecord(
        id = getLong(0),
        workspaceId = getString(1),
        path = getString(2),
        operation = runCatching { WorkspaceFileOperation.valueOf(getString(3).uppercase()) }
            .getOrDefault(WorkspaceFileOperation.MODIFY),
        originalContentHash = nullableString(4),
        newContent = nullableString(5),
        newPath = nullableString(6),
        createdAt = getLong(7),
        updatedAt = getLong(8),
    )

private fun android.database.Cursor.nullableString(index: Int): String? =
    if (isNull(index)) null else getString(index)

private fun android.database.Cursor.nullableLong(index: Int): Long? =
    if (isNull(index)) null else getLong(index)

private fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private fun unifiedDiff(path: String, oldText: String, newText: String): String {
    val lines = LineDiff.diff(oldText, newText)
    return buildString {
        appendLine("--- a/$path")
        appendLine("+++ b/$path")
        lines.forEach { line ->
            when (line) {
                is LineDiff.Line.Same -> appendLine(" ${line.text}")
                is LineDiff.Line.Del -> appendLine("-${line.text}")
                is LineDiff.Line.Add -> appendLine("+${line.text}")
            }
        }
    }
}
