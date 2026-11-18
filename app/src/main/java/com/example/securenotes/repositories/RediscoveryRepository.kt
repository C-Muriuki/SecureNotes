package com.example.securenotes.repositories

import android.content.ContentValues
import com.example.securenotes.database.DatabaseContract
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.models.IdeaStage
import com.example.securenotes.models.RediscoveryCandidate
import com.example.securenotes.models.RediscoveryEngine

class RediscoveryRepository(
    private val dbHelper: DatabaseHelper,
    private val noteRepository: NoteRepository = NoteRepository(dbHelper)
) {
    fun getRediscoveryCandidates(userId: Long, limit: Int = 10): List<RediscoveryCandidate> {
        requireOwner(userId)
        val notes = noteRepository.getNotes(userId)
        if (notes.isEmpty()) return emptyList()

        val connectionCounts = getConnectionCountsMap(userId)
        val (statuses, dueAts, lastReviewedAts) = getReviewQueueMap(userId)

        val ranked = RediscoveryEngine.scoreAndRank(
            notes = notes,
            ownerId = userId,
            connectionCounts = connectionCounts,
            reviewStatuses = statuses,
            dueAts = dueAts,
            lastReviewedAts = lastReviewedAts
        )

        return if (limit > 0) ranked.take(limit) else ranked
    }

    fun getCandidatesByCategory(
        userId: Long,
        category: String,
        limit: Int = 20
    ): List<RediscoveryCandidate> {
        val all = getRediscoveryCandidates(userId, limit = 0)
        val filtered = when (category.uppercase()) {
            "TODAY" -> all.take(5)
            "FORGOTTEN" -> all.filter {
                val lastOpened = it.note.lastOpenedAt ?: it.note.createdAt
                (System.currentTimeMillis() - lastOpened) >= 30L * 86_400_000L
            }
            "UNFINISHED" -> all.filter {
                it.note.ideaStage == IdeaStage.CAPTURED || it.note.ideaStage == IdeaStage.EXPLORING
            }
            "CONNECTED" -> all.filter { it.connectionCount > 0 }
            "QUEUE" -> getDismissedOrSnoozedCandidates(userId)
            else -> all
        }
        return if (limit > 0) filtered.take(limit) else filtered
    }

    fun markAsRevisited(userId: Long, noteId: Long): Boolean {
        requireOwner(userId)
        noteRepository.touchNoteOpened(userId, noteId)
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(DatabaseContract.NoteReviewQueue.COLUMN_OWNER_ID, userId)
            put(DatabaseContract.NoteReviewQueue.COLUMN_NOTE_ID, noteId)
            put(DatabaseContract.NoteReviewQueue.COLUMN_STATUS, DatabaseContract.NoteReviewQueue.STATUS_REVIEWED)
            put(DatabaseContract.NoteReviewQueue.COLUMN_LAST_REVIEWED_AT, now)
            putNull(DatabaseContract.NoteReviewQueue.COLUMN_DUE_AT)
            put(DatabaseContract.NoteReviewQueue.COLUMN_CREATED_AT, now)
        }
        val db = dbHelper.writableDatabase
        return db.insertWithOnConflict(
            DatabaseContract.NoteReviewQueue.TABLE_NAME,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        ) >= 0
    }

    fun snoozeRediscovery(userId: Long, noteId: Long, snoozeDays: Int): Boolean {
        requireOwner(userId)
        val now = System.currentTimeMillis()
        val dueAt = now + (snoozeDays.toLong() * 86_400_000L)
        val values = ContentValues().apply {
            put(DatabaseContract.NoteReviewQueue.COLUMN_OWNER_ID, userId)
            put(DatabaseContract.NoteReviewQueue.COLUMN_NOTE_ID, noteId)
            put(DatabaseContract.NoteReviewQueue.COLUMN_STATUS, DatabaseContract.NoteReviewQueue.STATUS_PENDING)
            put(DatabaseContract.NoteReviewQueue.COLUMN_DUE_AT, dueAt)
            put(DatabaseContract.NoteReviewQueue.COLUMN_CREATED_AT, now)
        }
        val db = dbHelper.writableDatabase
        return db.insertWithOnConflict(
            DatabaseContract.NoteReviewQueue.TABLE_NAME,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        ) >= 0
    }

    fun dismissRediscovery(userId: Long, noteId: Long): Boolean {
        requireOwner(userId)
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(DatabaseContract.NoteReviewQueue.COLUMN_OWNER_ID, userId)
            put(DatabaseContract.NoteReviewQueue.COLUMN_NOTE_ID, noteId)
            put(DatabaseContract.NoteReviewQueue.COLUMN_STATUS, DatabaseContract.NoteReviewQueue.STATUS_DISMISSED)
            put(DatabaseContract.NoteReviewQueue.COLUMN_LAST_REVIEWED_AT, now)
            put(DatabaseContract.NoteReviewQueue.COLUMN_CREATED_AT, now)
        }
        val db = dbHelper.writableDatabase
        return db.insertWithOnConflict(
            DatabaseContract.NoteReviewQueue.TABLE_NAME,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        ) >= 0
    }

    fun restoreRediscovery(userId: Long, noteId: Long): Boolean {
        requireOwner(userId)
        val db = dbHelper.writableDatabase
        return db.delete(
            DatabaseContract.NoteReviewQueue.TABLE_NAME,
            "${DatabaseContract.NoteReviewQueue.COLUMN_OWNER_ID} = ? AND ${DatabaseContract.NoteReviewQueue.COLUMN_NOTE_ID} = ?",
            arrayOf(userId.toString(), noteId.toString())
        ) >= 0
    }

    fun getRediscoveryCounts(userId: Long): Map<String, Int> {
        requireOwner(userId)
        val candidates = getRediscoveryCandidates(userId, limit = 0)
        val dismissed = getDismissedOrSnoozedCandidates(userId)
        return mapOf(
            "total" to candidates.size,
            "today" to minOf(candidates.size, 5),
            "forgotten" to candidates.count {
                val lastOpened = it.note.lastOpenedAt ?: it.note.createdAt
                (System.currentTimeMillis() - lastOpened) >= 30L * 86_400_000L
            },
            "unfinished" to candidates.count {
                it.note.ideaStage == IdeaStage.CAPTURED || it.note.ideaStage == IdeaStage.EXPLORING
            },
            "dismissed" to dismissed.size
        )
    }

    private fun getDismissedOrSnoozedCandidates(userId: Long): List<RediscoveryCandidate> {
        val notes = noteRepository.getNotes(userId)
        val connectionCounts = getConnectionCountsMap(userId)
        val (statuses, dueAts, _) = getReviewQueueMap(userId)
        val result = mutableListOf<RediscoveryCandidate>()

        for (note in notes) {
            val status = statuses[note.id] ?: continue
            val dueAt = dueAts[note.id]
            val count = connectionCounts[note.id] ?: 0
            if (status == DatabaseContract.NoteReviewQueue.STATUS_DISMISSED || (dueAt != null && dueAt > System.currentTimeMillis())) {
                val reason = if (status == DatabaseContract.NoteReviewQueue.STATUS_DISMISSED) "Dismissed from rediscovery"
                else "Snoozed until review"
                result.add(
                    RediscoveryCandidate(
                        note = note,
                        score = 0,
                        reason = reason,
                        connectionCount = count,
                        reviewStatus = status,
                        dueAt = dueAt
                    )
                )
            }
        }
        return result
    }

    private fun getConnectionCountsMap(userId: Long): Map<Long, Int> {
        val map = mutableMapOf<Long, Int>()
        val sql = "SELECT source_note_id, target_note_id FROM ${DatabaseContract.NoteConnectionTable.TABLE_NAME} WHERE owner_user_id = ?"
        dbHelper.readableDatabase.rawQuery(sql, arrayOf(userId.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                val source = cursor.getLong(0)
                val target = cursor.getLong(1)
                map[source] = (map[source] ?: 0) + 1
                map[target] = (map[target] ?: 0) + 1
            }
        }
        return map
    }

    private data class ReviewQueueData(
        val statuses: Map<Long, Int>,
        val dueAts: Map<Long, Long?>,
        val lastReviewedAts: Map<Long, Long?>
    )

    private fun getReviewQueueMap(userId: Long): ReviewQueueData {
        val statuses = mutableMapOf<Long, Int>()
        val dueAts = mutableMapOf<Long, Long?>()
        val lastReviewedAts = mutableMapOf<Long, Long?>()
        val queue = DatabaseContract.NoteReviewQueue
        dbHelper.readableDatabase.query(
            queue.TABLE_NAME,
            arrayOf(
                queue.COLUMN_NOTE_ID,
                queue.COLUMN_STATUS,
                queue.COLUMN_DUE_AT,
                queue.COLUMN_LAST_REVIEWED_AT
            ),
            "${queue.COLUMN_OWNER_ID} = ?",
            arrayOf(userId.toString()),
            null,
            null,
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val noteId = cursor.getLong(0)
                val status = cursor.getInt(1)
                val dueAt = if (cursor.isNull(2)) null else cursor.getLong(2)
                val lastReviewed = if (cursor.isNull(3)) null else cursor.getLong(3)
                statuses[noteId] = status
                dueAts[noteId] = dueAt
                lastReviewedAts[noteId] = lastReviewed
            }
        }
        return ReviewQueueData(statuses, dueAts, lastReviewedAts)
    }

    private fun requireOwner(userId: Long) = require(userId > 0) { "Sign in to access rediscovery." }
}
