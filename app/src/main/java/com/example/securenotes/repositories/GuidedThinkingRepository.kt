package com.example.securenotes.repositories

import android.content.ContentValues
import com.example.securenotes.database.DatabaseContract
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.models.ThinkingResponse
import com.example.securenotes.models.ThinkingSession
import com.example.securenotes.security.EncryptionManager

class GuidedThinkingRepository(
    private val dbHelper: DatabaseHelper,
    private val encryption: EncryptionManager = EncryptionManager(),
    private val noteRepository: NoteRepository = NoteRepository(dbHelper, encryption)
) {
    fun createSession(userId: Long, frameworkId: String, sourceNoteId: Long? = null): Long {
        requireOwner(userId)
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(DatabaseContract.ThinkingSessionTable.COLUMN_OWNER_ID, userId)
            if (sourceNoteId != null && sourceNoteId > 0) {
                put(DatabaseContract.ThinkingSessionTable.COLUMN_SOURCE_NOTE_ID, sourceNoteId)
            } else {
                putNull(DatabaseContract.ThinkingSessionTable.COLUMN_SOURCE_NOTE_ID)
            }
            put(DatabaseContract.ThinkingSessionTable.COLUMN_FRAMEWORK_ID, frameworkId)
            put(DatabaseContract.ThinkingSessionTable.COLUMN_STATUS, DatabaseContract.ThinkingSessionTable.STATUS_ACTIVE)
            put(DatabaseContract.ThinkingSessionTable.COLUMN_CREATED_AT, now)
            put(DatabaseContract.ThinkingSessionTable.COLUMN_UPDATED_AT, now)
        }
        return dbHelper.writableDatabase.insert(DatabaseContract.ThinkingSessionTable.TABLE_NAME, null, values)
    }

    fun getSession(userId: Long, sessionId: Long): ThinkingSession? {
        requireOwner(userId)
        val sessionsTable = DatabaseContract.ThinkingSessionTable
        var session: ThinkingSession? = null

        dbHelper.readableDatabase.query(
            sessionsTable.TABLE_NAME,
            arrayOf(
                sessionsTable.COLUMN_SESSION_ID,
                sessionsTable.COLUMN_OWNER_ID,
                sessionsTable.COLUMN_SOURCE_NOTE_ID,
                sessionsTable.COLUMN_FRAMEWORK_ID,
                sessionsTable.COLUMN_STATUS,
                sessionsTable.COLUMN_CREATED_AT,
                sessionsTable.COLUMN_UPDATED_AT,
                sessionsTable.COLUMN_COMPLETED_AT
            ),
            "${sessionsTable.COLUMN_SESSION_ID} = ? AND ${sessionsTable.COLUMN_OWNER_ID} = ?",
            arrayOf(sessionId.toString(), userId.toString()),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val sourceNoteId = if (cursor.isNull(2)) null else cursor.getLong(2)
                val completedAt = if (cursor.isNull(7)) null else cursor.getLong(7)
                session = ThinkingSession(
                    id = cursor.getLong(0),
                    ownerUserId = cursor.getLong(1),
                    sourceNoteId = sourceNoteId,
                    frameworkId = cursor.getString(3),
                    status = cursor.getInt(4),
                    createdAt = cursor.getLong(5),
                    updatedAt = cursor.getLong(6),
                    completedAt = completedAt,
                    responses = getResponses(userId, sessionId)
                )
            }
        }
        return session
    }

    fun getActiveSessions(userId: Long): List<ThinkingSession> {
        requireOwner(userId)
        return querySessions(
            userId,
            "${DatabaseContract.ThinkingSessionTable.COLUMN_OWNER_ID} = ? AND ${DatabaseContract.ThinkingSessionTable.COLUMN_STATUS} = ?",
            arrayOf(userId.toString(), DatabaseContract.ThinkingSessionTable.STATUS_ACTIVE.toString())
        )
    }

    fun getSessionHistory(userId: Long, limit: Int = 20): List<ThinkingSession> {
        requireOwner(userId)
        return querySessions(
            userId,
            "${DatabaseContract.ThinkingSessionTable.COLUMN_OWNER_ID} = ?",
            arrayOf(userId.toString()),
            limit.coerceAtLeast(0).toString()
        )
    }

    fun saveResponse(userId: Long, sessionId: Long, stepId: String, responseText: String): Boolean {
        requireOwner(userId)
        if (sessionId <= 0 || responseText.isBlank()) return false
        if (getSession(userId, sessionId) == null) return false

        val hasExistingKey = dbHelper.readableDatabase.query(
            DatabaseContract.NoteTable.TABLE_NAME,
            arrayOf(DatabaseContract.NoteTable.COLUMN_ID),
            "${DatabaseContract.NoteTable.COLUMN_USER_ID} = ?",
            arrayOf(userId.toString()),
            null, null, null, "1"
        ).use { it.moveToFirst() }

        val encrypted = if (hasExistingKey) {
            encryption.encryptWithExistingKey(userId, responseText)
        } else {
            encryption.encrypt(userId, responseText)
        }

        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(DatabaseContract.ThinkingResponseTable.COLUMN_SESSION_ID, sessionId)
            put(DatabaseContract.ThinkingResponseTable.COLUMN_OWNER_ID, userId)
            put(DatabaseContract.ThinkingResponseTable.COLUMN_STEP_ID, stepId)
            put(DatabaseContract.ThinkingResponseTable.COLUMN_ENCRYPTED_RESPONSE, encrypted.ciphertext)
            put(DatabaseContract.ThinkingResponseTable.COLUMN_RESPONSE_IV, encrypted.iv)
            put(DatabaseContract.ThinkingResponseTable.COLUMN_CREATED_AT, now)
            put(DatabaseContract.ThinkingResponseTable.COLUMN_UPDATED_AT, now)
        }

        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            // Delete existing response for this step if present
            db.delete(
                DatabaseContract.ThinkingResponseTable.TABLE_NAME,
                "${DatabaseContract.ThinkingResponseTable.COLUMN_SESSION_ID} = ? AND ${DatabaseContract.ThinkingResponseTable.COLUMN_STEP_ID} = ?",
                arrayOf(sessionId.toString(), stepId)
            )
            val inserted = db.insert(DatabaseContract.ThinkingResponseTable.TABLE_NAME, null, values) > 0

            val updateSessionValues = ContentValues().apply {
                put(DatabaseContract.ThinkingSessionTable.COLUMN_UPDATED_AT, now)
            }
            db.update(
                DatabaseContract.ThinkingSessionTable.TABLE_NAME,
                updateSessionValues,
                "${DatabaseContract.ThinkingSessionTable.COLUMN_SESSION_ID} = ? AND ${DatabaseContract.ThinkingSessionTable.COLUMN_OWNER_ID} = ?",
                arrayOf(sessionId.toString(), userId.toString())
            )
            db.setTransactionSuccessful()
            return inserted
        } finally {
            db.endTransaction()
        }
    }

    fun completeSession(userId: Long, sessionId: Long): Boolean {
        requireOwner(userId)
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(DatabaseContract.ThinkingSessionTable.COLUMN_STATUS, DatabaseContract.ThinkingSessionTable.STATUS_COMPLETED)
            put(DatabaseContract.ThinkingSessionTable.COLUMN_COMPLETED_AT, now)
            put(DatabaseContract.ThinkingSessionTable.COLUMN_UPDATED_AT, now)
        }
        return dbHelper.writableDatabase.update(
            DatabaseContract.ThinkingSessionTable.TABLE_NAME,
            values,
            "${DatabaseContract.ThinkingSessionTable.COLUMN_SESSION_ID} = ? AND ${DatabaseContract.ThinkingSessionTable.COLUMN_OWNER_ID} = ?",
            arrayOf(sessionId.toString(), userId.toString())
        ) == 1
    }

    fun deleteSession(userId: Long, sessionId: Long): Boolean {
        requireOwner(userId)
        return dbHelper.writableDatabase.delete(
            DatabaseContract.ThinkingSessionTable.TABLE_NAME,
            "${DatabaseContract.ThinkingSessionTable.COLUMN_SESSION_ID} = ? AND ${DatabaseContract.ThinkingSessionTable.COLUMN_OWNER_ID} = ?",
            arrayOf(sessionId.toString(), userId.toString())
        ) == 1
    }

    fun createNoteFromSession(userId: Long, sessionId: Long): Long {
        requireOwner(userId)
        val session = getSession(userId, sessionId) ?: return -1
        val framework = ThinkingTemplateRepository.getFramework(session.frameworkId) ?: return -1
        if (session.responses.isEmpty()) return -1

        val title = "Guided Reflection: ${framework.title}"
        val bodyBuilder = StringBuilder()
        bodyBuilder.append("Framework: ").append(framework.title).append("\n")
        if (session.sourceNoteId != null) {
            val sourceNote = noteRepository.getNote(userId, session.sourceNoteId)
            if (sourceNote != null) {
                bodyBuilder.append("Explored Note: ").append(sourceNote.title).append("\n")
            }
        }
        bodyBuilder.append("\n--- Reflection Outcomes ---\n\n")

        for (step in framework.steps) {
            val resp = session.responses.find { it.stepId == step.id }
            if (resp != null && resp.response.isNotBlank()) {
                bodyBuilder.append("• ").append(step.promptTitle).append("\n")
                bodyBuilder.append(resp.response).append("\n\n")
            }
        }

        val noteId = noteRepository.createNote(userId, title, bodyBuilder.toString().trim())
        if (noteId > 0 && session.sourceNoteId != null && session.sourceNoteId > 0) {
            noteRepository.connectNotes(userId, session.sourceNoteId, noteId)
        }
        return noteId
    }

    fun getResponses(userId: Long, sessionId: Long): List<ThinkingResponse> {
        requireOwner(userId)
        val responseTable = DatabaseContract.ThinkingResponseTable
        val responses = mutableListOf<ThinkingResponse>()

        dbHelper.readableDatabase.query(
            responseTable.TABLE_NAME,
            arrayOf(
                responseTable.COLUMN_RESPONSE_ID,
                responseTable.COLUMN_SESSION_ID,
                responseTable.COLUMN_OWNER_ID,
                responseTable.COLUMN_STEP_ID,
                responseTable.COLUMN_ENCRYPTED_RESPONSE,
                responseTable.COLUMN_RESPONSE_IV,
                responseTable.COLUMN_CREATED_AT,
                responseTable.COLUMN_UPDATED_AT
            ),
            "${responseTable.COLUMN_SESSION_ID} = ? AND ${responseTable.COLUMN_OWNER_ID} = ?",
            arrayOf(sessionId.toString(), userId.toString()),
            null,
            null,
            "${responseTable.COLUMN_CREATED_AT} ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val encryptedText = cursor.getString(4)
                val iv = cursor.getString(5)
                val plaintext = try {
                    encryption.decrypt(userId, encryptedText, iv)
                } catch (_: Exception) {
                    "[Encrypted Response Unavailable]"
                }
                responses.add(
                    ThinkingResponse(
                        id = cursor.getLong(0),
                        sessionId = cursor.getLong(1),
                        ownerUserId = cursor.getLong(2),
                        stepId = cursor.getString(3),
                        response = plaintext,
                        createdAt = cursor.getLong(6),
                        updatedAt = cursor.getLong(7)
                    )
                )
            }
        }
        return responses
    }

    private fun querySessions(userId: Long, selection: String, args: Array<String>, limit: String? = null): List<ThinkingSession> {
        val sessionsTable = DatabaseContract.ThinkingSessionTable
        val result = mutableListOf<ThinkingSession>()

        dbHelper.readableDatabase.query(
            sessionsTable.TABLE_NAME,
            arrayOf(
                sessionsTable.COLUMN_SESSION_ID,
                sessionsTable.COLUMN_OWNER_ID,
                sessionsTable.COLUMN_SOURCE_NOTE_ID,
                sessionsTable.COLUMN_FRAMEWORK_ID,
                sessionsTable.COLUMN_STATUS,
                sessionsTable.COLUMN_CREATED_AT,
                sessionsTable.COLUMN_UPDATED_AT,
                sessionsTable.COLUMN_COMPLETED_AT
            ),
            selection,
            args,
            null,
            null,
            "${sessionsTable.COLUMN_UPDATED_AT} DESC",
            limit
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val sessionId = cursor.getLong(0)
                val sourceNoteId = if (cursor.isNull(2)) null else cursor.getLong(2)
                val completedAt = if (cursor.isNull(7)) null else cursor.getLong(7)
                result.add(
                    ThinkingSession(
                        id = sessionId,
                        ownerUserId = cursor.getLong(1),
                        sourceNoteId = sourceNoteId,
                        frameworkId = cursor.getString(3),
                        status = cursor.getInt(4),
                        createdAt = cursor.getLong(5),
                        updatedAt = cursor.getLong(6),
                        completedAt = completedAt
                    )
                )
            }
        }
        return result
    }

    private fun requireOwner(userId: Long) = require(userId > 0) { "Sign in to access thinking sessions." }
}
