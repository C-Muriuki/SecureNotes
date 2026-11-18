package com.example.securenotes.repositories

import android.content.ContentValues
import android.database.Cursor
import com.example.securenotes.database.DatabaseContract
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.models.User

class UserRepository(private val dbHelper: DatabaseHelper) {

    private val userProjection: Array<String>
        get() = arrayOf(
            DatabaseContract.UserTable.COLUMN_USER_ID,
            DatabaseContract.UserTable.COLUMN_FULL_NAME,
            DatabaseContract.UserTable.COLUMN_USERNAME,
            DatabaseContract.UserTable.COLUMN_EMAIL,
            DatabaseContract.UserTable.COLUMN_PASSWORD_HASH,
            DatabaseContract.UserTable.COLUMN_PASSWORD_SALT,
            DatabaseContract.UserTable.COLUMN_CREATED_AT
        )

    fun registerUser(user: User): Long {
        val cleanEmail = user.email.lowercase().trim()
        val cleanUsername = if (user.username.isBlank()) {
            cleanEmail.substringBefore('@')
        } else {
            user.username.lowercase().trim()
        }
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseContract.UserTable.COLUMN_FULL_NAME, user.fullName.trim())
            put(DatabaseContract.UserTable.COLUMN_USERNAME, cleanUsername)
            put(DatabaseContract.UserTable.COLUMN_EMAIL, cleanEmail)
            put(DatabaseContract.UserTable.COLUMN_PASSWORD_HASH, user.passwordHash)
            put(DatabaseContract.UserTable.COLUMN_PASSWORD_SALT, user.passwordSalt)
            put(DatabaseContract.UserTable.COLUMN_CREATED_AT, user.createdAt)
        }
        return db.insert(DatabaseContract.UserTable.TABLE_NAME, null, values)
    }

    fun getUserByEmail(email: String): User? {
        val db = dbHelper.readableDatabase
        val selection = "${DatabaseContract.UserTable.COLUMN_EMAIL} = ?"
        val selectionArgs = arrayOf(email.lowercase().trim())

        var cursor: Cursor? = null
        return try {
            cursor = db.query(
                DatabaseContract.UserTable.TABLE_NAME,
                userProjection,
                selection,
                selectionArgs,
                null,
                null,
                null
            )
            if (cursor.moveToFirst()) {
                cursorToUser(cursor)
            } else {
                null
            }
        } finally {
            cursor?.close()
        }
    }

    fun getUserByUsername(username: String): User? {
        if (username.isBlank()) return null
        val db = dbHelper.readableDatabase
        val selection = "${DatabaseContract.UserTable.COLUMN_USERNAME} = ?"
        val selectionArgs = arrayOf(username.lowercase().trim())

        var cursor: Cursor? = null
        return try {
            cursor = db.query(
                DatabaseContract.UserTable.TABLE_NAME,
                userProjection,
                selection,
                selectionArgs,
                null,
                null,
                null
            )
            if (cursor.moveToFirst()) {
                cursorToUser(cursor)
            } else {
                null
            }
        } finally {
            cursor?.close()
        }
    }

    fun getUserById(userId: Long): User? {
        val db = dbHelper.readableDatabase
        val selection = "${DatabaseContract.UserTable.COLUMN_USER_ID} = ?"
        val selectionArgs = arrayOf(userId.toString())

        var cursor: Cursor? = null
        return try {
            cursor = db.query(
                DatabaseContract.UserTable.TABLE_NAME,
                userProjection,
                selection,
                selectionArgs,
                null,
                null,
                null
            )
            if (cursor.moveToFirst()) {
                cursorToUser(cursor)
            } else {
                null
            }
        } finally {
            cursor?.close()
        }
    }

    fun isEmailRegistered(email: String): Boolean {
        return getUserByEmail(email) != null
    }

    fun isUsernameRegistered(username: String): Boolean {
        return getUserByUsername(username) != null
    }

    fun updateUserProfile(userId: Long, fullName: String, email: String): Boolean {
        val existing = getUserById(userId) ?: return false
        return updateUserCredentials(userId, fullName, existing.username, email, null, null)
    }

    fun updateUserCredentials(
        userId: Long,
        fullName: String,
        username: String,
        email: String,
        newPasswordHash: String? = null,
        newPasswordSalt: String? = null
    ): Boolean {
        require(userId > 0) { "Valid user ID is required." }
        val cleanEmail = email.lowercase().trim()
        val cleanName = fullName.trim()
        val cleanUsername = if (username.isBlank()) cleanEmail.substringBefore('@') else username.lowercase().trim()
        if (cleanName.isEmpty() || cleanEmail.isEmpty() || cleanUsername.isEmpty()) return false

        val db = dbHelper.writableDatabase

        // Check if email belongs to another user
        val existingEmailUser = getUserByEmail(cleanEmail)
        if (existingEmailUser != null && existingEmailUser.userId != userId) {
            return false
        }

        // Check if username belongs to another user
        val existingUsernameUser = getUserByUsername(cleanUsername)
        if (existingUsernameUser != null && existingUsernameUser.userId != userId) {
            return false
        }

        val values = ContentValues().apply {
            put(DatabaseContract.UserTable.COLUMN_FULL_NAME, cleanName)
            put(DatabaseContract.UserTable.COLUMN_USERNAME, cleanUsername)
            put(DatabaseContract.UserTable.COLUMN_EMAIL, cleanEmail)
            if (!newPasswordHash.isNullOrBlank() && !newPasswordSalt.isNullOrBlank()) {
                put(DatabaseContract.UserTable.COLUMN_PASSWORD_HASH, newPasswordHash)
                put(DatabaseContract.UserTable.COLUMN_PASSWORD_SALT, newPasswordSalt)
            }
        }
        return db.update(
            DatabaseContract.UserTable.TABLE_NAME,
            values,
            "${DatabaseContract.UserTable.COLUMN_USER_ID} = ?",
            arrayOf(userId.toString())
        ) == 1
    }

    private fun cursorToUser(cursor: Cursor): User {
        val userId = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseContract.UserTable.COLUMN_USER_ID))
        val fullName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.UserTable.COLUMN_FULL_NAME))
        val usernameIdx = cursor.getColumnIndex(DatabaseContract.UserTable.COLUMN_USERNAME)
        val username = if (usernameIdx >= 0 && !cursor.isNull(usernameIdx)) {
            cursor.getString(usernameIdx)
        } else {
            ""
        }
        val email = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.UserTable.COLUMN_EMAIL))
        val hash = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.UserTable.COLUMN_PASSWORD_HASH))
        val salt = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.UserTable.COLUMN_PASSWORD_SALT))
        val createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseContract.UserTable.COLUMN_CREATED_AT))

        return User(
            userId = userId,
            fullName = fullName,
            username = username.ifBlank { email.substringBefore('@') },
            email = email,
            passwordHash = hash,
            passwordSalt = salt,
            createdAt = createdAt
        )
    }
}
