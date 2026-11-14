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
            return
        }

        if (oldVersion == 3) {
            migrateVersion3To4(db)
            migrateVersion4To5(db)
        } else if (oldVersion == 4 && newVersion >= 5) {
            migrateVersion4To5(db)
        }
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

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }
}
