package com.example.securenotes.services

import android.content.Context
import com.example.securenotes.database.DatabaseContract
import com.example.securenotes.database.DatabaseHelper
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.Locale

/**
 * Writes only the authenticated user's already-encrypted SQLite fields.
 * It never calls EncryptionManager or materializes note plaintext.
 */
class NoteBackupFileWriter {
    fun writeBackup(context: Context, database: DatabaseHelper, userId: Long): File {
        require(userId > 0) { "A signed-in account is required." }
        val directory = File(context.applicationContext.filesDir, BACKUP_DIRECTORY)
        check(directory.exists() || directory.mkdirs()) { "Unable to create backup directory." }
        val timestamp = System.currentTimeMillis()
        val destination = File(directory, "secure-notes-$userId-$timestamp.json")
        val temporary = File(directory, "${destination.name}.tmp")
        directory.listFiles { _, name -> name.endsWith(".tmp") }?.forEach { it.delete() }
        try {
            val projection = arrayOf(
                DatabaseContract.NoteTable.COLUMN_ID,
                DatabaseContract.NoteTable.COLUMN_USER_ID,
                DatabaseContract.NoteTable.COLUMN_TITLE,
                DatabaseContract.NoteTable.COLUMN_TITLE_IV,
                DatabaseContract.NoteTable.COLUMN_CONTENT,
                DatabaseContract.NoteTable.COLUMN_CONTENT_IV,
                DatabaseContract.NoteTable.COLUMN_CREATED_AT,
                DatabaseContract.NoteTable.COLUMN_UPDATED_AT,
                DatabaseContract.NoteTable.COLUMN_IS_PINNED
            )
            val cursor = database.readableDatabase.query(
                DatabaseContract.NoteTable.TABLE_NAME,
                projection,
                "${DatabaseContract.NoteTable.COLUMN_USER_ID} = ?",
                arrayOf(userId.toString()),
                null,
                null,
                "${DatabaseContract.NoteTable.COLUMN_UPDATED_AT} DESC"
            )
            FileOutputStream(temporary).use { stream ->
                OutputStreamWriter(stream, Charsets.UTF_8).buffered().use { out ->
                    out.write("{\"formatVersion\":1,\"ownerId\":$userId,\"notes\":[")
                    var first = true
                    cursor.use { rows ->
                        while (rows.moveToNext()) {
                            if (Thread.currentThread().isInterrupted) throw InterruptedException("Backup interrupted")
                            if (!first) out.write(",")
                            first = false
                            out.write("{")
                            out.write("\"noteId\":${rows.getLong(0)},")
                            out.write("\"userId\":${rows.getLong(1)},")
                            out.write("\"encryptedTitle\":${JSONObject.quote(rows.getString(2))},")
                            out.write("\"titleIv\":${JSONObject.quote(rows.getString(3))},")
                            out.write("\"encryptedContent\":${JSONObject.quote(rows.getString(4))},")
                            out.write("\"contentIv\":${JSONObject.quote(rows.getString(5))},")
                            out.write("\"createdAt\":${rows.getLong(6)},")
                            out.write("\"updatedAt\":${rows.getLong(7)},")
                            out.write("\"isPinned\":${rows.getInt(8) == 1}")
                            out.write("}")
                        }
                    }
                    out.write("]}")
                    out.flush()
                    stream.fd.sync()
                }
            }
            check(temporary.renameTo(destination)) { "Unable to finalize backup file." }
            return destination
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    companion object {
        const val BACKUP_DIRECTORY = "backups"
    }
}
