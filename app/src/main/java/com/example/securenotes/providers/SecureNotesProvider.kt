package com.example.securenotes.providers

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.utils.SessionManager

class SecureNotesProvider : ContentProvider() {
    private lateinit var database: DatabaseHelper

    override fun onCreate(): Boolean {
        val appContext = context?.applicationContext ?: return false
        database = DatabaseHelper(appContext)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val ownerId = requireSignedInUser()
        val matcher = uriMatcher.match(uri)
        require(matcher == NOTES || matcher == NOTE_ID) { "Unsupported notes URI." }
        val allowed = projection?.map { column ->
            require(column in COLUMNS) { "Unsupported notes column." }
            column
        }?.toTypedArray() ?: COLUMNS
        val where = StringBuilder("${NotesContract.COLUMN_USER_ID} = ?")
        val args = mutableListOf(ownerId.toString())
        if (matcher == NOTE_ID) {
            where.append(" AND ${NotesContract.COLUMN_NOTE_ID} = ?")
            args += ContentUris.parseId(uri).toString()
        }
        val safeSort = when (sortOrder) {
            null, "${NotesContract.COLUMN_UPDATED_AT} DESC" -> sortOrder
            "${NotesContract.COLUMN_UPDATED_AT} ASC" -> sortOrder
            else -> null
        }
        return database.readableDatabase.query(
            NotesContract.TABLE_NAME,
            allowed,
            where.toString(),
            args.toTypedArray(),
            null,
            null,
            safeSort
        ).also { cursor ->
            cursor.setNotificationUri(requireNotNull(context).contentResolver, uri)
        }
    }

    override fun getType(uri: Uri): String = when (uriMatcher.match(uri)) {
        NOTES -> NotesContract.MIME_TYPE_NOTES
        NOTE_ID -> NotesContract.MIME_TYPE_NOTE
        else -> throw IllegalArgumentException("Unsupported notes URI.")
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        require(uriMatcher.match(uri) == NOTES) { "Insert requires the notes collection URI." }
        val ownerId = requireSignedInUser()
        val supplied = requireNotNull(values) { "Note values are required." }
        REQUIRED_ENCRYPTED_COLUMNS.forEach { column ->
            require(!supplied.getAsString(column).isNullOrBlank()) { "Encrypted note data is incomplete." }
        }
        val sanitized = ContentValues().apply {
            REQUIRED_ENCRYPTED_COLUMNS.forEach { put(it, supplied.getAsString(it)) }
            put(NotesContract.COLUMN_CREATED_AT, supplied.getAsLong(NotesContract.COLUMN_CREATED_AT) ?: System.currentTimeMillis())
            put(NotesContract.COLUMN_UPDATED_AT, supplied.getAsLong(NotesContract.COLUMN_UPDATED_AT) ?: System.currentTimeMillis())
            put(NotesContract.COLUMN_IS_PINNED, supplied.getAsInteger(NotesContract.COLUMN_IS_PINNED) ?: 0)
            put(NotesContract.COLUMN_USER_ID, ownerId)
        }
        val id = database.writableDatabase.insertOrThrow(NotesContract.TABLE_NAME, null, sanitized)
        val insertedUri = ContentUris.withAppendedId(NotesContract.CONTENT_URI, id)
        context?.contentResolver?.notifyChange(insertedUri, null)
        return insertedUri
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        require(uriMatcher.match(uri) == NOTE_ID) { "Update requires a single-note URI." }
        val ownerId = requireSignedInUser()
        val supplied = requireNotNull(values) { "Note values are required." }
        val sanitized = ContentValues()
        ENCRYPTED_COLUMNS.forEach { column -> supplied.getAsString(column)?.let { sanitized.put(column, it) } }
        supplied.getAsLong(NotesContract.COLUMN_UPDATED_AT)?.let { sanitized.put(NotesContract.COLUMN_UPDATED_AT, it) }
        supplied.getAsInteger(NotesContract.COLUMN_IS_PINNED)?.let { sanitized.put(NotesContract.COLUMN_IS_PINNED, it) }
        require(sanitized.size() > 0) { "No supported note fields were supplied." }
        val changed = database.writableDatabase.update(
            NotesContract.TABLE_NAME, sanitized,
            "${NotesContract.COLUMN_NOTE_ID} = ? AND ${NotesContract.COLUMN_USER_ID} = ?",
            arrayOf(ContentUris.parseId(uri).toString(), ownerId.toString())
        )
        if (changed > 0) context?.contentResolver?.notifyChange(uri, null)
        return changed
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        require(uriMatcher.match(uri) == NOTE_ID) { "Delete requires a single-note URI." }
        val ownerId = requireSignedInUser()
        val deleted = database.writableDatabase.delete(
            NotesContract.TABLE_NAME,
            "${NotesContract.COLUMN_NOTE_ID} = ? AND ${NotesContract.COLUMN_USER_ID} = ?",
            arrayOf(ContentUris.parseId(uri).toString(), ownerId.toString())
        )
        if (deleted > 0) context?.contentResolver?.notifyChange(uri, null)
        return deleted
    }

    private fun requireSignedInUser(): Long {
        val appContext = requireNotNull(context).applicationContext
        val session = SessionManager(appContext)
        return session.getCurrentUserId().takeIf { session.isLoggedIn() && it > 0 }
            ?: throw SecurityException("Sign in to access notes.")
    }

    companion object {
        private const val NOTES = 1
        private const val NOTE_ID = 2
        private val COLUMNS = arrayOf(
            NotesContract.COLUMN_NOTE_ID,
            NotesContract.COLUMN_USER_ID,
            NotesContract.COLUMN_ENCRYPTED_TITLE,
            NotesContract.COLUMN_TITLE_IV,
            NotesContract.COLUMN_ENCRYPTED_CONTENT,
            NotesContract.COLUMN_CONTENT_IV,
            NotesContract.COLUMN_CREATED_AT,
            NotesContract.COLUMN_UPDATED_AT,
            NotesContract.COLUMN_IS_PINNED
        )
        private val REQUIRED_ENCRYPTED_COLUMNS = arrayOf(
            NotesContract.COLUMN_ENCRYPTED_TITLE,
            NotesContract.COLUMN_TITLE_IV,
            NotesContract.COLUMN_ENCRYPTED_CONTENT,
            NotesContract.COLUMN_CONTENT_IV
        )
        private val ENCRYPTED_COLUMNS = arrayOf(
            NotesContract.COLUMN_ENCRYPTED_TITLE,
            NotesContract.COLUMN_TITLE_IV,
            NotesContract.COLUMN_ENCRYPTED_CONTENT,
            NotesContract.COLUMN_CONTENT_IV
        )
        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(NotesContract.AUTHORITY, NotesContract.PATH_NOTES, NOTES)
            addURI(NotesContract.AUTHORITY, "${NotesContract.PATH_NOTES}/#", NOTE_ID)
        }
    }
}
