package com.example.securenotes.repositories

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import com.example.securenotes.database.DatabaseContract
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.models.ConnectedThought
import com.example.securenotes.models.CreateConnectionResult
import com.example.securenotes.models.IdeaStage
import com.example.securenotes.models.IdeaStageHistory
import com.example.securenotes.models.Note
import com.example.securenotes.models.ThoughtConnection
import com.example.securenotes.security.EncryptionManager

class NoteRepository(
    private val dbHelper: DatabaseHelper,
    private val encryption: EncryptionManager = EncryptionManager()
) {
    fun createNote(userId: Long, title: String, content: String, pinned: Boolean = false): Long {
        requireOwner(userId)
        val hasExistingNotes = dbHelper.readableDatabase.query(
            DatabaseContract.NoteTable.TABLE_NAME,
            arrayOf(DatabaseContract.NoteTable.COLUMN_ID),
            "${DatabaseContract.NoteTable.COLUMN_USER_ID} = ?",
            arrayOf(userId.toString()),
            null,
            null,
            null,
            "1"
        ).use { it.moveToFirst() }
        val encrypt: (String) -> EncryptionManager.EncryptedValue =
            if (hasExistingNotes) ({ value -> encryption.encryptWithExistingKey(userId, value) })
            else ({ value -> encryption.encrypt(userId, value) })
        val titleValue = encrypt(title)
        val contentValue = encrypt(content)
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(DatabaseContract.NoteTable.COLUMN_USER_ID, userId)
            put(DatabaseContract.NoteTable.COLUMN_TITLE, titleValue.ciphertext)
            put(DatabaseContract.NoteTable.COLUMN_TITLE_IV, titleValue.iv)
            put(DatabaseContract.NoteTable.COLUMN_CONTENT, contentValue.ciphertext)
            put(DatabaseContract.NoteTable.COLUMN_CONTENT_IV, contentValue.iv)
            put(DatabaseContract.NoteTable.COLUMN_CREATED_AT, now)
            put(DatabaseContract.NoteTable.COLUMN_UPDATED_AT, now)
            put(DatabaseContract.NoteTable.COLUMN_IS_PINNED, if (pinned) 1 else 0)
            put(DatabaseContract.NoteTable.COLUMN_IS_IDEA, 1)
            put(DatabaseContract.NoteTable.COLUMN_IDEA_STAGE, IdeaStage.CAPTURED.value)
        }
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val noteId = db.insert(DatabaseContract.NoteTable.TABLE_NAME, null, values)
            if (noteId > 0) {
                val historyValues = ContentValues().apply {
                    put(DatabaseContract.IdeaStageHistoryTable.COLUMN_OWNER_ID, userId)
                    put(DatabaseContract.IdeaStageHistoryTable.COLUMN_NOTE_ID, noteId)
                    put(DatabaseContract.IdeaStageHistoryTable.COLUMN_FROM_STAGE, IdeaStage.CAPTURED.value)
                    put(DatabaseContract.IdeaStageHistoryTable.COLUMN_TO_STAGE, IdeaStage.CAPTURED.value)
                    put(DatabaseContract.IdeaStageHistoryTable.COLUMN_CHANGED_AT, now)
                }
                db.insert(DatabaseContract.IdeaStageHistoryTable.TABLE_NAME, null, historyValues)
            }
            db.setTransactionSuccessful()
            return noteId
        } finally {
            db.endTransaction()
        }
    }

    fun updateNote(userId: Long, noteId: Long, title: String, content: String, pinned: Boolean): Boolean {
        requireOwner(userId)
        if (getNote(userId, noteId) == null) return false
        val encryptedTitle = encryption.encryptWithExistingKey(userId, title)
        val encryptedContent = encryption.encryptWithExistingKey(userId, content)
        val values = ContentValues().apply {
            put(DatabaseContract.NoteTable.COLUMN_TITLE, encryptedTitle.ciphertext)
            put(DatabaseContract.NoteTable.COLUMN_TITLE_IV, encryptedTitle.iv)
            put(DatabaseContract.NoteTable.COLUMN_CONTENT, encryptedContent.ciphertext)
            put(DatabaseContract.NoteTable.COLUMN_CONTENT_IV, encryptedContent.iv)
            put(DatabaseContract.NoteTable.COLUMN_UPDATED_AT, System.currentTimeMillis())
            put(DatabaseContract.NoteTable.COLUMN_IS_PINNED, if (pinned) 1 else 0)
        }
        return dbHelper.writableDatabase.update(DatabaseContract.NoteTable.TABLE_NAME, values,
            "${DatabaseContract.NoteTable.COLUMN_ID} = ? AND ${DatabaseContract.NoteTable.COLUMN_USER_ID} = ?",
            arrayOf(noteId.toString(), userId.toString())) == 1
    }

    fun updateIdeaStage(userId: Long, noteId: Long, newStage: IdeaStage): Boolean {
        requireOwner(userId)
        val currentNote = getNote(userId, noteId) ?: return false
        if (currentNote.ideaStage == newStage) return true
        val now = System.currentTimeMillis()
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put(DatabaseContract.NoteTable.COLUMN_IDEA_STAGE, newStage.value)
                put(DatabaseContract.NoteTable.COLUMN_UPDATED_AT, now)
            }
            val updated = db.update(
                DatabaseContract.NoteTable.TABLE_NAME,
                values,
                "${DatabaseContract.NoteTable.COLUMN_ID} = ? AND ${DatabaseContract.NoteTable.COLUMN_USER_ID} = ?",
                arrayOf(noteId.toString(), userId.toString())
            ) == 1
            if (updated) {
                val historyValues = ContentValues().apply {
                    put(DatabaseContract.IdeaStageHistoryTable.COLUMN_OWNER_ID, userId)
                    put(DatabaseContract.IdeaStageHistoryTable.COLUMN_NOTE_ID, noteId)
                    put(DatabaseContract.IdeaStageHistoryTable.COLUMN_FROM_STAGE, currentNote.ideaStage.value)
                    put(DatabaseContract.IdeaStageHistoryTable.COLUMN_TO_STAGE, newStage.value)
                    put(DatabaseContract.IdeaStageHistoryTable.COLUMN_CHANGED_AT, now)
                }
                db.insert(DatabaseContract.IdeaStageHistoryTable.TABLE_NAME, null, historyValues)
                db.setTransactionSuccessful()
            }
            return updated
        } finally {
            db.endTransaction()
        }
    }

    fun getIdeaStageHistory(userId: Long, noteId: Long): List<IdeaStageHistory> {
        requireOwner(userId)
        val historyTable = DatabaseContract.IdeaStageHistoryTable
        val result = mutableListOf<IdeaStageHistory>()
        dbHelper.readableDatabase.query(
            historyTable.TABLE_NAME,
            arrayOf(
                historyTable.COLUMN_ID,
                historyTable.COLUMN_OWNER_ID,
                historyTable.COLUMN_NOTE_ID,
                historyTable.COLUMN_FROM_STAGE,
                historyTable.COLUMN_TO_STAGE,
                historyTable.COLUMN_CHANGED_AT
            ),
            "${historyTable.COLUMN_OWNER_ID} = ? AND ${historyTable.COLUMN_NOTE_ID} = ?",
            arrayOf(userId.toString(), noteId.toString()),
            null,
            null,
            "${historyTable.COLUMN_CHANGED_AT} ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException()
                result += IdeaStageHistory(
                    id = cursor.getLong(0),
                    ownerUserId = cursor.getLong(1),
                    noteId = cursor.getLong(2),
                    fromStage = IdeaStage.fromInt(cursor.getInt(3)),
                    toStage = IdeaStage.fromInt(cursor.getInt(4)),
                    changedAt = cursor.getLong(5)
                )
            }
        }
        return result
    }

    fun getNotesByStage(userId: Long, stage: IdeaStage): List<Note> {
        requireOwner(userId)
        return queryNotes(
            userId,
            "${DatabaseContract.NoteTable.COLUMN_USER_ID} = ? AND ${DatabaseContract.NoteTable.COLUMN_IDEA_STAGE} = ?",
            arrayOf(userId.toString(), stage.value.toString()),
            "${DatabaseContract.NoteTable.COLUMN_UPDATED_AT} DESC"
        )
    }

    fun getIdeaStageCounts(userId: Long): Map<IdeaStage, Int> {
        requireOwner(userId)
        val counts = mutableMapOf(
            IdeaStage.CAPTURED to 0,
            IdeaStage.EXPLORING to 0,
            IdeaStage.DEVELOPING to 0,
            IdeaStage.COMPLETED to 0
        )
        val sql = "SELECT ${DatabaseContract.NoteTable.COLUMN_IDEA_STAGE}, COUNT(*) " +
            "FROM ${DatabaseContract.NoteTable.TABLE_NAME} " +
            "WHERE ${DatabaseContract.NoteTable.COLUMN_USER_ID} = ? " +
            "GROUP BY ${DatabaseContract.NoteTable.COLUMN_IDEA_STAGE}"
        dbHelper.readableDatabase.rawQuery(sql, arrayOf(userId.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                val stage = IdeaStage.fromInt(cursor.getInt(0))
                val count = cursor.getInt(1)
                counts[stage] = count
            }
        }
        return counts
    }

    fun getNote(userId: Long, noteId: Long): Note? =
        queryNotes(
            userId,
            "${DatabaseContract.NoteTable.COLUMN_ID} = ? AND ${DatabaseContract.NoteTable.COLUMN_USER_ID} = ?",
            arrayOf(noteId.toString(), userId.toString()),
            null
        ).firstOrNull()

    fun touchNoteOpened(userId: Long, noteId: Long): Boolean {
        requireOwner(userId)
        val values = ContentValues().apply {
            put(DatabaseContract.NoteTable.COLUMN_LAST_OPENED_AT, System.currentTimeMillis())
        }
        return dbHelper.writableDatabase.update(
            DatabaseContract.NoteTable.TABLE_NAME,
            values,
            "${DatabaseContract.NoteTable.COLUMN_ID} = ? AND ${DatabaseContract.NoteTable.COLUMN_USER_ID} = ?",
            arrayOf(noteId.toString(), userId.toString())
        ) == 1
    }

    fun getNotes(
        userId: Long,
        search: String? = null,
        pinned: Boolean? = null,
        stage: IdeaStage? = null,
        sortAscending: Boolean = false,
        limit: Int? = null
    ): List<Note> {
        requireOwner(userId)
        val where = mutableListOf("${DatabaseContract.NoteTable.COLUMN_USER_ID} = ?")
        val args = mutableListOf(userId.toString())
        if (pinned != null) {
            where += "${DatabaseContract.NoteTable.COLUMN_IS_PINNED} = ?"
            args += if (pinned) "1" else "0"
        }
        if (stage != null) {
            where += "${DatabaseContract.NoteTable.COLUMN_IDEA_STAGE} = ?"
            args += stage.value.toString()
        }
        val notes = queryNotes(
            userId,
            where.joinToString(" AND "),
            args.toTypedArray(),
            "${DatabaseContract.NoteTable.COLUMN_IS_PINNED} DESC, ${DatabaseContract.NoteTable.COLUMN_UPDATED_AT} ${if (sortAscending) "ASC" else "DESC"}",
            limit?.coerceAtLeast(0)?.toString()
        )
        return if (search.isNullOrBlank()) notes else notes.filter {
            it.title.contains(search, true) || it.content.contains(search, true)
        }
    }

    fun getRecentNotes(userId: Long, limit: Int = 5): List<Note> =
        getNotes(userId, limit = limit)

    fun countNotes(userId: Long): Int {
        requireOwner(userId)
        val cursor = dbHelper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${DatabaseContract.NoteTable.TABLE_NAME} WHERE ${DatabaseContract.NoteTable.COLUMN_USER_ID} = ?",
            arrayOf(userId.toString())
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun setPinned(userId: Long, noteId: Long, pinned: Boolean): Boolean {
        requireOwner(userId)
        val values = ContentValues().apply {
            put(DatabaseContract.NoteTable.COLUMN_IS_PINNED, if (pinned) 1 else 0)
            put(DatabaseContract.NoteTable.COLUMN_UPDATED_AT, System.currentTimeMillis())
        }
        return dbHelper.writableDatabase.update(DatabaseContract.NoteTable.TABLE_NAME, values,
            "${DatabaseContract.NoteTable.COLUMN_ID} = ? AND ${DatabaseContract.NoteTable.COLUMN_USER_ID} = ?",
            arrayOf(noteId.toString(), userId.toString())) == 1
    }

    fun connectNotes(userId: Long, firstNoteId: Long, secondNoteId: Long): CreateConnectionResult {
        requireOwner(userId)
        if (firstNoteId <= 0 || secondNoteId <= 0) return CreateConnectionResult.NOTE_NOT_AVAILABLE
        if (firstNoteId == secondNoteId) return CreateConnectionResult.SAME_NOTE

        val sourceId = minOf(firstNoteId, secondNoteId)
        val targetId = maxOf(firstNoteId, secondNoteId)
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val ownedCount = db.rawQuery(
                "SELECT COUNT(*) FROM ${DatabaseContract.NoteTable.TABLE_NAME} " +
                    "WHERE ${DatabaseContract.NoteTable.COLUMN_USER_ID} = ? AND " +
                    "${DatabaseContract.NoteTable.COLUMN_ID} IN (?, ?)",
                arrayOf(userId.toString(), sourceId.toString(), targetId.toString())
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
            if (ownedCount != 2) return CreateConnectionResult.NOTE_NOT_AVAILABLE

            if (isConnected(db, userId, sourceId, targetId)) {
                return CreateConnectionResult.ALREADY_CONNECTED
            }

            val values = ContentValues().apply {
                put(DatabaseContract.NoteConnectionTable.COLUMN_OWNER_ID, userId)
                put(DatabaseContract.NoteConnectionTable.COLUMN_SOURCE_NOTE_ID, sourceId)
                put(DatabaseContract.NoteConnectionTable.COLUMN_TARGET_NOTE_ID, targetId)
                put(DatabaseContract.NoteConnectionTable.COLUMN_CREATED_AT, System.currentTimeMillis())
            }
            try {
                db.insertOrThrow(DatabaseContract.NoteConnectionTable.TABLE_NAME, null, values)
            } catch (_: SQLiteConstraintException) {
                return if (isConnected(db, userId, sourceId, targetId)) {
                    CreateConnectionResult.ALREADY_CONNECTED
                } else {
                    CreateConnectionResult.NOTE_NOT_AVAILABLE
                }
            }
            db.setTransactionSuccessful()
            return CreateConnectionResult.CREATED
        } finally {
            db.endTransaction()
        }
    }

    fun isConnected(userId: Long, firstNoteId: Long, secondNoteId: Long): Boolean {
        requireOwner(userId)
        if (firstNoteId <= 0 || secondNoteId <= 0 || firstNoteId == secondNoteId) return false
        return isConnected(dbHelper.readableDatabase, userId, minOf(firstNoteId, secondNoteId), maxOf(firstNoteId, secondNoteId))
    }

    fun removeConnection(userId: Long, firstNoteId: Long, secondNoteId: Long): Boolean {
        requireOwner(userId)
        if (firstNoteId <= 0 || secondNoteId <= 0 || firstNoteId == secondNoteId) return false
        val sourceId = minOf(firstNoteId, secondNoteId)
        val targetId = maxOf(firstNoteId, secondNoteId)
        return dbHelper.writableDatabase.delete(
            DatabaseContract.NoteConnectionTable.TABLE_NAME,
            "${DatabaseContract.NoteConnectionTable.COLUMN_OWNER_ID} = ? AND " +
                "${DatabaseContract.NoteConnectionTable.COLUMN_SOURCE_NOTE_ID} = ? AND " +
                "${DatabaseContract.NoteConnectionTable.COLUMN_TARGET_NOTE_ID} = ?",
            arrayOf(userId.toString(), sourceId.toString(), targetId.toString())
        ) == 1
    }

    fun getConnectedNotes(userId: Long, noteId: Long): List<ConnectedThought> {
        requireOwner(userId)
        if (noteId <= 0) return emptyList()
        val connection = DatabaseContract.NoteConnectionTable
        val note = DatabaseContract.NoteTable
        val otherNoteId = "CASE WHEN c.${connection.COLUMN_SOURCE_NOTE_ID} = ? " +
            "THEN c.${connection.COLUMN_TARGET_NOTE_ID} ELSE c.${connection.COLUMN_SOURCE_NOTE_ID} END"
        val selection = "c.${connection.COLUMN_OWNER_ID} = ? AND " +
            "(c.${connection.COLUMN_SOURCE_NOTE_ID} = ? OR c.${connection.COLUMN_TARGET_NOTE_ID} = ?)"
        val args = arrayOf(noteId.toString(), userId.toString(), noteId.toString(), noteId.toString())
        val projection = noteProjection().map { "n.$it" }.toTypedArray() + "c.${connection.COLUMN_CREATED_AT}"
        val result = mutableListOf<ConnectedThought>()
        dbHelper.readableDatabase.rawQuery(
            "SELECT ${projection.joinToString()} FROM ${connection.TABLE_NAME} c " +
                "JOIN ${note.TABLE_NAME} n ON n.${note.COLUMN_USER_ID} = c.${connection.COLUMN_OWNER_ID} " +
                "AND n.${note.COLUMN_ID} = $otherNoteId " +
                "WHERE $selection ORDER BY c.${connection.COLUMN_CREATED_AT} DESC, " +
                "n.${note.COLUMN_UPDATED_AT} DESC",
            args
        ).use { cursor ->
            while (cursor.moveToNext()) {
                if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException()
                result += ConnectedThought(cursorToNote(cursor, userId), cursor.getLong(12))
            }
        }
        return result
    }

    fun getConnectionCandidates(userId: Long, currentNoteId: Long): List<Note> {
        requireOwner(userId)
        if (currentNoteId <= 0) return emptyList()
        val notes = DatabaseContract.NoteTable
        val connections = DatabaseContract.NoteConnectionTable
        val projection = noteProjection().joinToString { "n.$it" }
        val sql = "SELECT $projection FROM ${notes.TABLE_NAME} n " +
            "WHERE n.${notes.COLUMN_USER_ID} = ? AND n.${notes.COLUMN_ID} != ? AND NOT EXISTS (" +
            "SELECT 1 FROM ${connections.TABLE_NAME} c WHERE c.${connections.COLUMN_OWNER_ID} = ? AND " +
            "((c.${connections.COLUMN_SOURCE_NOTE_ID} = ? AND c.${connections.COLUMN_TARGET_NOTE_ID} = n.${notes.COLUMN_ID}) " +
            "OR (c.${connections.COLUMN_TARGET_NOTE_ID} = ? AND c.${connections.COLUMN_SOURCE_NOTE_ID} = n.${notes.COLUMN_ID}))) " +
            "ORDER BY n.${notes.COLUMN_UPDATED_AT} DESC"
        val args = arrayOf(
            userId.toString(),
            currentNoteId.toString(),
            userId.toString(),
            currentNoteId.toString(),
            currentNoteId.toString()
        )
        val result = mutableListOf<Note>()
        dbHelper.readableDatabase.rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) {
                if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException()
                result += cursorToNote(cursor, userId)
            }
        }
        return result
    }

    fun getRecentConnections(userId: Long, limit: Int? = 3): List<ThoughtConnection> {
        requireOwner(userId)
        val connection = DatabaseContract.NoteConnectionTable
        val note = DatabaseContract.NoteTable
        val firstProjection = noteProjection().map { "first_note.$it" }
        val secondProjection = noteProjection().map { "second_note.$it" }
        val sql = "SELECT ${(firstProjection + secondProjection + "c.${connection.COLUMN_CREATED_AT}").joinToString()} " +
            "FROM ${connection.TABLE_NAME} c " +
            "JOIN ${note.TABLE_NAME} first_note ON first_note.${note.COLUMN_ID} = c.${connection.COLUMN_SOURCE_NOTE_ID} " +
            "AND first_note.${note.COLUMN_USER_ID} = c.${connection.COLUMN_OWNER_ID} " +
            "JOIN ${note.TABLE_NAME} second_note ON second_note.${note.COLUMN_ID} = c.${connection.COLUMN_TARGET_NOTE_ID} " +
            "AND second_note.${note.COLUMN_USER_ID} = c.${connection.COLUMN_OWNER_ID} " +
            "WHERE c.${connection.COLUMN_OWNER_ID} = ? ORDER BY c.${connection.COLUMN_CREATED_AT} DESC" +
            if (limit != null) " LIMIT ?" else ""
        val args = if (limit == null) arrayOf(userId.toString())
        else arrayOf(userId.toString(), limit.coerceAtLeast(0).toString())
        val result = mutableListOf<ThoughtConnection>()
        dbHelper.readableDatabase.rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) {
                if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException()
                result += ThoughtConnection(
                    first = cursorToNote(cursor, userId, 0),
                    second = cursorToNote(cursor, userId, 12),
                    createdAt = cursor.getLong(24)
                )
            }
        }
        return result
    }

    private fun isConnected(db: android.database.sqlite.SQLiteDatabase, userId: Long, sourceId: Long, targetId: Long): Boolean {
        val table = DatabaseContract.NoteConnectionTable
        return db.query(
            table.TABLE_NAME,
            arrayOf(table.COLUMN_SOURCE_NOTE_ID),
            "${table.COLUMN_OWNER_ID} = ? AND ${table.COLUMN_SOURCE_NOTE_ID} = ? AND " +
                "${table.COLUMN_TARGET_NOTE_ID} = ?",
            arrayOf(userId.toString(), sourceId.toString(), targetId.toString()),
            null,
            null,
            null,
            "1"
        ).use { it.moveToFirst() }
    }

    fun deleteNote(userId: Long, noteId: Long): Boolean {
        requireOwner(userId)
        return dbHelper.writableDatabase.delete(DatabaseContract.NoteTable.TABLE_NAME,
            "${DatabaseContract.NoteTable.COLUMN_ID} = ? AND ${DatabaseContract.NoteTable.COLUMN_USER_ID} = ?",
            arrayOf(noteId.toString(), userId.toString())) == 1
    }

    fun restoreNote(note: Note): Boolean {
        requireOwner(note.userId)
        val titleValue = encryption.encrypt(note.userId, note.title)
        val contentValue = encryption.encrypt(note.userId, note.content)
        val values = ContentValues().apply {
            put(DatabaseContract.NoteTable.COLUMN_ID, note.id)
            put(DatabaseContract.NoteTable.COLUMN_USER_ID, note.userId)
            put(DatabaseContract.NoteTable.COLUMN_TITLE, titleValue.ciphertext)
            put(DatabaseContract.NoteTable.COLUMN_TITLE_IV, titleValue.iv)
            put(DatabaseContract.NoteTable.COLUMN_CONTENT, contentValue.ciphertext)
            put(DatabaseContract.NoteTable.COLUMN_CONTENT_IV, contentValue.iv)
            put(DatabaseContract.NoteTable.COLUMN_CREATED_AT, note.createdAt)
            put(DatabaseContract.NoteTable.COLUMN_UPDATED_AT, note.updatedAt)
            put(DatabaseContract.NoteTable.COLUMN_IS_PINNED, if (note.isPinned) 1 else 0)
            put(DatabaseContract.NoteTable.COLUMN_IS_IDEA, if (note.isIdea) 1 else 0)
            put(DatabaseContract.NoteTable.COLUMN_IDEA_STAGE, note.ideaStage.value)
        }
        return dbHelper.writableDatabase.insert(DatabaseContract.NoteTable.TABLE_NAME, null, values) > 0
    }

    private fun queryNotes(
        userId: Long,
        selection: String,
        args: Array<String>,
        order: String?,
        limit: String? = null
    ): List<Note> {
        requireOwner(userId)
        val cols = noteProjection()
        val result = mutableListOf<Note>()
        dbHelper.writableDatabase.query(
            DatabaseContract.NoteTable.TABLE_NAME,
            cols,
            selection,
            args,
            null,
            null,
            order,
            limit
        ).use { cursor ->
            while (cursor.moveToNext()) {
                if (Thread.currentThread().isInterrupted) {
                    throw java.util.concurrent.CancellationException("Note loading was cancelled.")
                }
                result += cursorToNote(cursor, userId)
            }
        }
        return result
    }

    private fun noteProjection(): Array<String> = arrayOf(
        DatabaseContract.NoteTable.COLUMN_ID,
        DatabaseContract.NoteTable.COLUMN_USER_ID,
        DatabaseContract.NoteTable.COLUMN_TITLE,
        DatabaseContract.NoteTable.COLUMN_TITLE_IV,
        DatabaseContract.NoteTable.COLUMN_CONTENT,
        DatabaseContract.NoteTable.COLUMN_CONTENT_IV,
        DatabaseContract.NoteTable.COLUMN_CREATED_AT,
        DatabaseContract.NoteTable.COLUMN_UPDATED_AT,
        DatabaseContract.NoteTable.COLUMN_IS_PINNED,
        DatabaseContract.NoteTable.COLUMN_IS_IDEA,
        DatabaseContract.NoteTable.COLUMN_IDEA_STAGE,
        DatabaseContract.NoteTable.COLUMN_LAST_OPENED_AT
    )

    private fun cursorToNote(cursor: Cursor, ownerId: Long, offset: Int = 0): Note {
        val title = encryption.decrypt(ownerId, cursor.getString(offset + 2), cursor.getString(offset + 3))
        val content = encryption.decrypt(ownerId, cursor.getString(offset + 4), cursor.getString(offset + 5))
        val isIdea = cursor.getInt(offset + 9) == 1
        val ideaStageInt = cursor.getInt(offset + 10)
        val lastOpened = if (cursor.isNull(offset + 11)) null else cursor.getLong(offset + 11)

        return Note(
            id = cursor.getLong(offset),
            userId = cursor.getLong(offset + 1),
            title = title,
            content = content,
            iv = cursor.getString(offset + 5),
            createdAt = cursor.getLong(offset + 6),
            updatedAt = cursor.getLong(offset + 7),
            isPinned = cursor.getInt(offset + 8) == 1,
            titleIv = cursor.getString(offset + 3),
            isIdea = isIdea,
            ideaStage = IdeaStage.fromInt(ideaStageInt),
            lastOpenedAt = lastOpened
        )
    }

    private fun requireOwner(userId: Long) = require(userId > 0) { "Sign in to access notes." }
}
