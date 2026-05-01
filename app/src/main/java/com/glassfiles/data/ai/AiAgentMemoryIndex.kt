package com.glassfiles.data.ai

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object AiAgentMemoryIndex {
    data class SearchResult(
        val repoFullName: String,
        val path: String,
        val kind: String,
        val title: String,
        val snippet: String,
        val updatedAt: Long,
    )

    data class Fact(
        val type: String,
        val text: String,
        val sourcePath: String,
        val updatedAt: Long,
    )

    fun rebuildRepo(context: Context, repoFullName: String) {
        if (repoFullName.isBlank()) return
        val db = Db(context).writableDatabase
        db.beginTransaction()
        try {
            clearRepo(db, repoFullName)
            val repoDir = AiAgentMemoryStore.repoMemoryDir(context, repoFullName)
            val docs = mutableListOf<IndexedDoc>()
            repoDir.walkTopDown()
                .filter { it.isFile && (it.extension == "md" || it.name == "full.json") }
                .forEach { file ->
                    docs += indexableDoc(repoFullName, repoDir, file)
                }
            AiAgentMemoryStore.globalPreferencesMemoryFile(context).takeIf { it.isFile }?.let { file ->
                docs += IndexedDoc(
                    repoFullName = repoFullName,
                    path = "preferences.md",
                    kind = "preference",
                    title = "User preferences",
                    body = file.readText(),
                    updatedAt = file.lastModified(),
                )
            }
            docs.forEach { doc ->
                insertDoc(db, doc)
                extractFacts(doc).forEach { fact ->
                    insertFact(db, doc.repoFullName, fact, doc.path, doc.updatedAt)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun search(context: Context, repoFullName: String, query: String, limit: Int = 20): List<SearchResult> {
        if (repoFullName.isBlank()) return emptyList()
        val ftsQuery = buildFtsQuery(query) ?: return emptyList()
        val db = Db(context).readableDatabase
        return runCatching {
            db.rawQuery(
                """
                    SELECT repo, path, kind, title,
                           snippet(memory_fts, '[', ']', ' ... ', -1, 18) AS snip,
                           (SELECT updated_at FROM documents WHERE documents.doc_id = memory_fts.doc_id) AS updated_at
                    FROM memory_fts
                    WHERE memory_fts MATCH ? AND repo = ?
                    LIMIT ?
                """.trimIndent(),
                arrayOf(ftsQuery, repoFullName, limit.toString()),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            SearchResult(
                                repoFullName = cursor.getString(0),
                                path = cursor.getString(1),
                                kind = cursor.getString(2),
                                title = cursor.getString(3),
                                snippet = cursor.getString(4).replace('\n', ' ').take(220),
                                updatedAt = cursor.getLong(5),
                            ),
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun facts(context: Context, repoFullName: String, limit: Int = 80): List<Fact> {
        if (repoFullName.isBlank()) return emptyList()
        val db = Db(context).readableDatabase
        return db.rawQuery(
            """
                SELECT type, text, source_path, updated_at
                FROM facts
                WHERE repo = ?
                ORDER BY updated_at DESC, type ASC
                LIMIT ?
            """.trimIndent(),
            arrayOf(repoFullName, limit.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Fact(
                            type = cursor.getString(0),
                            text = cursor.getString(1),
                            sourcePath = cursor.getString(2),
                            updatedAt = cursor.getLong(3),
                        ),
                    )
                }
            }
        }
    }

    fun markDirtyAndRebuild(context: Context, repoFullName: String) {
        runCatching { rebuildRepo(context, repoFullName) }
    }

    private fun clearRepo(db: SQLiteDatabase, repoFullName: String) {
        val ids = db.rawQuery("SELECT doc_id FROM documents WHERE repo = ?", arrayOf(repoFullName)).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        ids.forEach { id ->
            db.delete("memory_fts", "doc_id = ?", arrayOf(id))
        }
        db.delete("documents", "repo = ?", arrayOf(repoFullName))
        db.delete("facts", "repo = ?", arrayOf(repoFullName))
    }

    private fun insertDoc(db: SQLiteDatabase, doc: IndexedDoc) {
        db.execSQL(
            "INSERT OR REPLACE INTO documents(doc_id, repo, path, kind, title, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf(doc.id, doc.repoFullName, doc.path, doc.kind, doc.title, doc.updatedAt),
        )
        db.execSQL(
            "INSERT INTO memory_fts(doc_id, repo, path, kind, title, body) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf(doc.id, doc.repoFullName, doc.path, doc.kind, doc.title, doc.body),
        )
    }

    private fun insertFact(db: SQLiteDatabase, repoFullName: String, fact: ExtractedFact, sourcePath: String, updatedAt: Long) {
        db.execSQL(
            "INSERT OR IGNORE INTO facts(repo, type, text, source_path, updated_at) VALUES (?, ?, ?, ?, ?)",
            arrayOf(repoFullName, fact.type, fact.text, sourcePath, updatedAt),
        )
    }

    private fun indexableDoc(repoFullName: String, repoDir: File, file: File): IndexedDoc {
        val relative = file.relativeTo(repoDir).path.replace('\\', '/')
        if (file.name == "full.json") {
            val parsed = runCatching { JSONObject(file.readText()) }.getOrNull()
            val messages = parsed?.optJSONArray("messages") ?: JSONArray()
            val body = buildString {
                for (i in 0 until messages.length()) {
                    val msg = messages.optJSONObject(i) ?: continue
                    append(msg.optString("role"))
                    append(": ")
                    appendLine(msg.optString("content"))
                }
            }
            return IndexedDoc(
                repoFullName = repoFullName,
                path = relative,
                kind = "chat",
                title = parsed?.optString("title").orEmpty().ifBlank { file.parentFile?.name.orEmpty() },
                body = body,
                updatedAt = file.lastModified(),
            )
        }
        val kind = when (file.name) {
            "project.md" -> "project"
            "preferences.md" -> "preference"
            "decisions.md" -> "decision"
            "summary.md" -> "summary"
            else -> "memory"
        }
        return IndexedDoc(
            repoFullName = repoFullName,
            path = relative,
            kind = kind,
            title = file.name,
            body = file.readText(),
            updatedAt = file.lastModified(),
        )
    }

    private fun extractFacts(doc: IndexedDoc): List<ExtractedFact> {
        val out = mutableListOf<ExtractedFact>()
        val currentSection = StringBuilder()
        var section = ""
        fun flush() {
            val text = currentSection.toString()
            if (text.isBlank()) return
            val type = when {
                section.contains("preference", true) || doc.kind == "preference" -> "preference"
                section.contains("decision", true) || doc.kind == "decision" -> "decision"
                section.contains("known issue", true) -> "known_issue"
                section.contains("open task", true) -> "open_task"
                section.contains("convention", true) -> "convention"
                section.contains("architecture", true) -> "architecture"
                else -> doc.kind
            }
            text.lineSequence()
                .map { it.trim().removePrefix("- ").removePrefix("* ").trim() }
                .filter { it.length >= 8 && !it.startsWith("#") }
                .take(24)
                .forEach { out += ExtractedFact(type, it.take(240)) }
            currentSection.clear()
        }
        doc.body.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("#")) {
                flush()
                section = line.removePrefix("#").trim()
            } else {
                currentSection.appendLine(line)
            }
        }
        flush()
        return out.distinctBy { it.type to it.text.lowercase() }
    }

    private fun buildFtsQuery(query: String): String? {
        val tokens = Regex("[\\p{L}\\p{N}_]+")
            .findAll(query.lowercase())
            .map { it.value }
            .filter { it.length >= 2 }
            .take(8)
            .toList()
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "$it*" }
    }

    private data class IndexedDoc(
        val repoFullName: String,
        val path: String,
        val kind: String,
        val title: String,
        val body: String,
        val updatedAt: Long,
    ) {
        val id: String = "$repoFullName::$path"
    }

    private data class ExtractedFact(val type: String, val text: String)

    private class Db(context: Context) : SQLiteOpenHelper(
        context,
        File(AiAgentMemoryStore.memoryRootDir(context).apply { mkdirs() }, "memory_index.db").absolutePath,
        null,
        1,
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                    CREATE TABLE IF NOT EXISTS documents(
                        doc_id TEXT PRIMARY KEY,
                        repo TEXT NOT NULL,
                        path TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        title TEXT NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                """.trimIndent(),
            )
            db.execSQL(
                """
                    CREATE VIRTUAL TABLE IF NOT EXISTS memory_fts
                    USING fts4(doc_id, repo, path, kind, title, body)
                """.trimIndent(),
            )
            db.execSQL(
                """
                    CREATE TABLE IF NOT EXISTS facts(
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        repo TEXT NOT NULL,
                        type TEXT NOT NULL,
                        text TEXT NOT NULL,
                        source_path TEXT NOT NULL,
                        updated_at INTEGER NOT NULL,
                        UNIQUE(repo, type, text, source_path)
                    )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_documents_repo ON documents(repo)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_facts_repo ON facts(repo)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS documents")
            db.execSQL("DROP TABLE IF EXISTS memory_fts")
            db.execSQL("DROP TABLE IF EXISTS facts")
            onCreate(db)
        }
    }
}
