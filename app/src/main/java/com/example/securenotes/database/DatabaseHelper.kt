package com.example.securenotes.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(
    context: Context,
    databaseName: String = DatabaseContract.DATABASE_NAME
) : SQLiteOpenHelper(
    context.applicationContext,
    databaseName,
    null,
    DatabaseContract.DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(DatabaseContract.UserTable.CREATE_TABLE)
        db.execSQL(DatabaseContract.NoteTable.CREATE_TABLE)
        createV4SupportingSchema(db)
        createV5SupportingSchema(db)
        createV6SupportingSchema(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            // Preserve the old table intact: its placeholder ciphertext has no verifiable key/format.
            // New code uses a fresh authenticated schema and never attempts to show legacy values as plaintext.
            val legacy = "notes_legacy_v$oldVersion"
            db.execSQL("ALTER TABLE ${DatabaseContract.NoteTable.TABLE_NAME} RENAME TO $legacy")
            db.execSQL(DatabaseContract.NoteTable.CREATE_TABLE)
            createV4SupportingSchema(db)
            createV5SupportingSchema(db)
            createV6SupportingSchema(db)
            return
        }

        if (oldVersion <= 3) {
            migrateVersion3To4(db)
        }
        if (oldVersion <= 4) {
            migrateVersion4To5(db)
        }
        if (oldVersion <= 5 && newVersion >= 6) {
            migrateVersion5To6(db)
        }
        if (oldVersion <= 6 && newVersion >= 7) {
            migrateVersion6To7(db)
        }
    }

    private fun migrateVersion6To7(db: SQLiteDatabase) {
        val userTable = DatabaseContract.UserTable.TABLE_NAME
        val colUsername = DatabaseContract.UserTable.COLUMN_USERNAME
        val colEmail = DatabaseContract.UserTable.COLUMN_EMAIL

        var hasUsername = false
        db.rawQuery("PRAGMA table_info($userTable)", null).use { cursor ->
            val nameIdx = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIdx >= 0 && cursor.getString(nameIdx).equals(colUsername, ignoreCase = true)) {
                    hasUsername = true
                    break
                }
            }
        }

        if (!hasUsername) {
            db.execSQL(
                "ALTER TABLE $userTable " +
                    "ADD COLUMN $colUsername TEXT NOT NULL DEFAULT '' COLLATE NOCASE"
            )
        }

        db.execSQL(
            "UPDATE $userTable SET $colUsername = " +
                "CASE WHEN INSTR($colEmail, '@') > 1 " +
                "THEN LOWER(SUBSTR($colEmail, 1, INSTR($colEmail, '@') - 1)) " +
                "ELSE 'user' END " +
                "WHERE $colUsername = '' OR $colUsername IS NULL"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username ON $userTable($colUsername COLLATE NOCASE) WHERE $colUsername != ''")
    }

    /**
     * SQLiteOpenHelper invokes onUpgrade inside its version-change transaction. Do not start a
     * nested transaction here: any failed ALTER/CREATE/index statement propagates so the helper
     * rolls back the complete schema change and leaves user_version unchanged.
     */
    private fun migrateVersion3To4(db: SQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE ${DatabaseContract.NoteTable.TABLE_NAME} " +
                "ADD COLUMN ${DatabaseContract.NoteTable.COLUMN_IS_IDEA} INTEGER NOT NULL DEFAULT 0 " +
                "CHECK (${DatabaseContract.NoteTable.COLUMN_IS_IDEA} IN (0, 1))"
        )
        db.execSQL(
            "ALTER TABLE ${DatabaseContract.NoteTable.TABLE_NAME} " +
                "ADD COLUMN ${DatabaseContract.NoteTable.COLUMN_IDEA_STAGE} INTEGER NOT NULL DEFAULT 0 " +
                "CHECK (${DatabaseContract.NoteTable.COLUMN_IDEA_STAGE} BETWEEN 0 AND 3)"
        )
        db.execSQL(
            "ALTER TABLE ${DatabaseContract.NoteTable.TABLE_NAME} " +
                "ADD COLUMN ${DatabaseContract.NoteTable.COLUMN_LAST_OPENED_AT} INTEGER"
        )
        createV4SupportingSchema(db)
    }

    private fun migrateVersion4To5(db: SQLiteDatabase) {
        createV5SupportingSchema(db)
    }

    private fun migrateVersion5To6(db: SQLiteDatabase) {
        createV6SupportingSchema(db)
    }

    private fun createV4SupportingSchema(db: SQLiteDatabase) {
        // SQLite requires a unique parent key for the composite owner/note foreign keys.
        db.execSQL(
            "CREATE UNIQUE INDEX ${DatabaseContract.Index.NOTES_OWNER_NOTE} " +
                "ON ${DatabaseContract.NoteTable.TABLE_NAME}(" +
                "${DatabaseContract.NoteTable.COLUMN_USER_ID}, ${DatabaseContract.NoteTable.COLUMN_ID})"
        )
        db.execSQL(
            DatabaseContract.NoteConnectionTable.CREATE_TABLE
        )
        db.execSQL(
            DatabaseContract.NoteReviewQueue.CREATE_TABLE
        )
        db.execSQL(
            "CREATE INDEX ${DatabaseContract.Index.NOTES_OWNER_IDEA_STAGE} " +
                "ON ${DatabaseContract.NoteTable.TABLE_NAME}(" +
                "${DatabaseContract.NoteTable.COLUMN_USER_ID}, ${DatabaseContract.NoteTable.COLUMN_IS_IDEA}, " +
                "${DatabaseContract.NoteTable.COLUMN_IDEA_STAGE})"
        )
        // The connection primary key already indexes (owner, source); this covers reverse lookups.
        db.execSQL(
            "CREATE INDEX ${DatabaseContract.Index.CONNECTIONS_OWNER_TARGET} " +
                "ON ${DatabaseContract.NoteConnectionTable.TABLE_NAME}(" +
                "${DatabaseContract.NoteConnectionTable.COLUMN_OWNER_ID}, " +
                "${DatabaseContract.NoteConnectionTable.COLUMN_TARGET_NOTE_ID})"
        )
        db.execSQL(
            "CREATE INDEX ${DatabaseContract.Index.REVIEW_OWNER_STATUS_DUE} " +
                "ON ${DatabaseContract.NoteReviewQueue.TABLE_NAME}(" +
                "${DatabaseContract.NoteReviewQueue.COLUMN_OWNER_ID}, " +
                "${DatabaseContract.NoteReviewQueue.COLUMN_STATUS}, " +
                "${DatabaseContract.NoteReviewQueue.COLUMN_DUE_AT})"
        )
    }

    private fun createV5SupportingSchema(db: SQLiteDatabase) {
        db.execSQL(DatabaseContract.IdeaStageHistoryTable.CREATE_TABLE)
        db.execSQL(
            "CREATE INDEX ${DatabaseContract.Index.STAGE_HISTORY_OWNER_NOTE} " +
                "ON ${DatabaseContract.IdeaStageHistoryTable.TABLE_NAME}(" +
                "${DatabaseContract.IdeaStageHistoryTable.COLUMN_OWNER_ID}, " +
                "${DatabaseContract.IdeaStageHistoryTable.COLUMN_NOTE_ID})"
        )
    }

    private fun createV6SupportingSchema(db: SQLiteDatabase) {
        db.execSQL(DatabaseContract.ThinkingSessionTable.CREATE_TABLE)
        db.execSQL(DatabaseContract.ThinkingResponseTable.CREATE_TABLE)
        db.execSQL(
            "CREATE INDEX ${DatabaseContract.Index.THINKING_SESSIONS_OWNER_STATUS} " +
                "ON ${DatabaseContract.ThinkingSessionTable.TABLE_NAME}(" +
                "${DatabaseContract.ThinkingSessionTable.COLUMN_OWNER_ID}, " +
                "${DatabaseContract.ThinkingSessionTable.COLUMN_STATUS})"
        )
        db.execSQL(
            "CREATE INDEX ${DatabaseContract.Index.THINKING_RESPONSES_SESSION_STEP} " +
                "ON ${DatabaseContract.ThinkingResponseTable.TABLE_NAME}(" +
                "${DatabaseContract.ThinkingResponseTable.COLUMN_SESSION_ID}, " +
                "${DatabaseContract.ThinkingResponseTable.COLUMN_STEP_ID})"
        )
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }
}
