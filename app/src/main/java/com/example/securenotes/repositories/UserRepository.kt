package com.example.securenotes.repositories

import android.content.ContentValues
import android.database.Cursor
import com.example.securenotes.database.DatabaseContract
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.models.User

class UserRepository(private val dbHelper: DatabaseHelper) {

    fun registerUser(user: User): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseContract.UserTable.COLUMN_FULL_NAME, user.fullName)
            put(DatabaseContract.UserTable.COLUMN_EMAIL, user.email.lowercase().trim())
            put(DatabaseContract.UserTable.COLUMN_PASSWORD_HASH, user.passwordHash)
            put(DatabaseContract.UserTable.COLUMN_PASSWORD_SALT, user.passwordSalt)
            put(DatabaseContract.UserTable.COLUMN_CREATED_AT, user.createdAt)
        }
        return db.insert(DatabaseContract.UserTable.TABLE_NAME, null, values)
    }

    fun getUserByEmail(email: String): User? {
        val db = dbHelper.readableDatabase
        val projection = arrayOf(
            DatabaseContract.UserTable.COLUMN_USER_ID,
            DatabaseContract.UserTable.COLUMN_FULL_NAME,
            DatabaseContract.UserTable.COLUMN_EMAIL,
            DatabaseContract.UserTable.COLUMN_PASSWORD_HASH,
            DatabaseContract.UserTable.COLUMN_PASSWORD_SALT,
            DatabaseContract.UserTable.COLUMN_CREATED_AT
        )
        val selection = "${DatabaseContract.UserTable.COLUMN_EMAIL} = ?"
        val selectionArgs = arrayOf(email.lowercase().trim())

        var cursor: Cursor? = null
        return try {
            cursor = db.query(
                DatabaseContract.UserTable.TABLE_NAME,
                projection,
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
        val projection = arrayOf(
            DatabaseContract.UserTable.COLUMN_USER_ID,
            DatabaseContract.UserTable.COLUMN_FULL_NAME,
            DatabaseContract.UserTable.COLUMN_EMAIL,
            DatabaseContract.UserTable.COLUMN_PASSWORD_HASH,
            DatabaseContract.UserTable.COLUMN_PASSWORD_SALT,
            DatabaseContract.UserTable.COLUMN_CREATED_AT
        )
        val selection = "${DatabaseContract.UserTable.COLUMN_USER_ID} = ?"
        val selectionArgs = arrayOf(userId.toString())

        var cursor: Cursor? = null
        return try {
            cursor = db.query(
                DatabaseContract.UserTable.TABLE_NAME,
                projection,
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

    private fun cursorToUser(cursor: Cursor): User {
        val userId = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseContract.UserTable.COLUMN_USER_ID))
        val fullName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.UserTable.COLUMN_FULL_NAME))
        val email = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.UserTable.COLUMN_EMAIL))
        val hash = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.UserTable.COLUMN_PASSWORD_HASH))
        val salt = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.UserTable.COLUMN_PASSWORD_SALT))
        val createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseContract.UserTable.COLUMN_CREATED_AT))

        return User(
            userId = userId,
            fullName = fullName,
            email = email,
            passwordHash = hash,
            passwordSalt = salt,
            createdAt = createdAt
        )
    }
}
