package com.example.securenotes.database

object DatabaseContract {
    const val DATABASE_NAME = "secure_notes.db"
    const val DATABASE_VERSION = 5

    object UserTable {
        const val TABLE_NAME = "users"
        const val COLUMN_USER_ID = "user_id"
        const val COLUMN_FULL_NAME = "full_name"
        const val COLUMN_EMAIL = "email"
        const val COLUMN_PASSWORD_HASH = "password_hash"
        const val COLUMN_PASSWORD_SALT = "password_salt"
        const val COLUMN_CREATED_AT = "created_at"
        const val CREATE_TABLE = """CREATE TABLE $TABLE_NAME (
            $COLUMN_USER_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COLUMN_FULL_NAME TEXT NOT NULL,
            $COLUMN_EMAIL TEXT NOT NULL COLLATE NOCASE UNIQUE,
            $COLUMN_PASSWORD_HASH TEXT NOT NULL,
            $COLUMN_PASSWORD_SALT TEXT NOT NULL,
            $COLUMN_CREATED_AT INTEGER NOT NULL
        )"""
    }

    object NoteTable {
        const val TABLE_NAME = "notes"
        const val COLUMN_ID = "note_id"
        const val COLUMN_USER_ID = "user_id"
        const val COLUMN_TITLE = "encrypted_title"
        const val COLUMN_TITLE_IV = "title_iv"
        const val COLUMN_CONTENT = "encrypted_content"
        const val COLUMN_CONTENT_IV = "content_iv"
        const val COLUMN_CREATED_AT = "created_at"
        const val COLUMN_UPDATED_AT = "updated_at"
        const val COLUMN_IS_PINNED = "is_pinned"
        const val COLUMN_IS_IDEA = "is_idea"
        const val COLUMN_IDEA_STAGE = "idea_stage"
        const val COLUMN_LAST_OPENED_AT = "last_opened_at"

        const val CREATE_TABLE = """CREATE TABLE $TABLE_NAME (
            $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COLUMN_USER_ID INTEGER NOT NULL,
            $COLUMN_TITLE TEXT NOT NULL,
            $COLUMN_TITLE_IV TEXT NOT NULL,
            $COLUMN_CONTENT TEXT NOT NULL,
            $COLUMN_CONTENT_IV TEXT NOT NULL,
            $COLUMN_CREATED_AT INTEGER NOT NULL,
            $COLUMN_UPDATED_AT INTEGER NOT NULL,
            $COLUMN_IS_PINNED INTEGER NOT NULL DEFAULT 0,
            $COLUMN_IS_IDEA INTEGER NOT NULL DEFAULT 1 CHECK ($COLUMN_IS_IDEA IN (0, 1)),
            $COLUMN_IDEA_STAGE INTEGER NOT NULL DEFAULT 0 CHECK ($COLUMN_IDEA_STAGE BETWEEN 0 AND 3),
            $COLUMN_LAST_OPENED_AT INTEGER,
            FOREIGN KEY ($COLUMN_USER_ID) REFERENCES ${UserTable.TABLE_NAME}(${UserTable.COLUMN_USER_ID}) ON DELETE CASCADE
        )"""
    }

    object NoteConnectionTable {
        const val TABLE_NAME = "note_connections"
        const val COLUMN_OWNER_ID = "owner_user_id"
        const val COLUMN_SOURCE_NOTE_ID = "source_note_id"
        const val COLUMN_TARGET_NOTE_ID = "target_note_id"
        const val COLUMN_CREATED_AT = "created_at"

        const val CREATE_TABLE = """CREATE TABLE $TABLE_NAME (
            $COLUMN_OWNER_ID INTEGER NOT NULL,
            $COLUMN_SOURCE_NOTE_ID INTEGER NOT NULL,
            $COLUMN_TARGET_NOTE_ID INTEGER NOT NULL,
            $COLUMN_CREATED_AT INTEGER NOT NULL,
            PRIMARY KEY ($COLUMN_OWNER_ID, $COLUMN_SOURCE_NOTE_ID, $COLUMN_TARGET_NOTE_ID),
            CHECK ($COLUMN_SOURCE_NOTE_ID < $COLUMN_TARGET_NOTE_ID),
            FOREIGN KEY ($COLUMN_OWNER_ID, $COLUMN_SOURCE_NOTE_ID)
                REFERENCES ${NoteTable.TABLE_NAME}(${NoteTable.COLUMN_USER_ID}, ${NoteTable.COLUMN_ID})
                ON DELETE CASCADE,
            FOREIGN KEY ($COLUMN_OWNER_ID, $COLUMN_TARGET_NOTE_ID)
                REFERENCES ${NoteTable.TABLE_NAME}(${NoteTable.COLUMN_USER_ID}, ${NoteTable.COLUMN_ID})
                ON DELETE CASCADE
        )"""
    }

    object NoteReviewQueue {
        const val TABLE_NAME = "note_review_queue"
        const val COLUMN_OWNER_ID = "owner_user_id"
        const val COLUMN_NOTE_ID = "note_id"
        const val COLUMN_REASON = "review_reason"
        const val COLUMN_STATUS = "review_status"
        const val COLUMN_CREATED_AT = "created_at"
        const val COLUMN_LAST_REVIEWED_AT = "last_reviewed_at"
        const val COLUMN_DUE_AT = "due_at"

        const val REASON_USER_MARKED = 0
        const val STATUS_PENDING = 0
        const val STATUS_REVIEWED = 1
        const val STATUS_DISMISSED = 2

        const val CREATE_TABLE = """CREATE TABLE $TABLE_NAME (
            $COLUMN_OWNER_ID INTEGER NOT NULL,
            $COLUMN_NOTE_ID INTEGER NOT NULL,
            $COLUMN_REASON INTEGER NOT NULL DEFAULT $REASON_USER_MARKED CHECK ($COLUMN_REASON >= 0),
            $COLUMN_STATUS INTEGER NOT NULL DEFAULT $STATUS_PENDING
                CHECK ($COLUMN_STATUS IN ($STATUS_PENDING, $STATUS_REVIEWED, $STATUS_DISMISSED)),
            $COLUMN_CREATED_AT INTEGER NOT NULL,
            $COLUMN_LAST_REVIEWED_AT INTEGER,
            $COLUMN_DUE_AT INTEGER,
            PRIMARY KEY ($COLUMN_OWNER_ID, $COLUMN_NOTE_ID),
            FOREIGN KEY ($COLUMN_OWNER_ID, $COLUMN_NOTE_ID)
                REFERENCES ${NoteTable.TABLE_NAME}(${NoteTable.COLUMN_USER_ID}, ${NoteTable.COLUMN_ID})
                ON DELETE CASCADE
        )"""
    }

    object IdeaStageHistoryTable {
        const val TABLE_NAME = "idea_stage_history"
        const val COLUMN_ID = "history_id"
        const val COLUMN_OWNER_ID = "owner_user_id"
        const val COLUMN_NOTE_ID = "note_id"
        const val COLUMN_FROM_STAGE = "from_stage"
        const val COLUMN_TO_STAGE = "to_stage"
        const val COLUMN_CHANGED_AT = "changed_at"

        const val CREATE_TABLE = """CREATE TABLE $TABLE_NAME (
            $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COLUMN_OWNER_ID INTEGER NOT NULL,
            $COLUMN_NOTE_ID INTEGER NOT NULL,
            $COLUMN_FROM_STAGE INTEGER NOT NULL CHECK ($COLUMN_FROM_STAGE BETWEEN 0 AND 3),
            $COLUMN_TO_STAGE INTEGER NOT NULL CHECK ($COLUMN_TO_STAGE BETWEEN 0 AND 3),
            $COLUMN_CHANGED_AT INTEGER NOT NULL,
            FOREIGN KEY ($COLUMN_OWNER_ID, $COLUMN_NOTE_ID)
                REFERENCES ${NoteTable.TABLE_NAME}(${NoteTable.COLUMN_USER_ID}, ${NoteTable.COLUMN_ID})
                ON DELETE CASCADE
        )"""
    }

    object Index {
        const val NOTES_OWNER_NOTE = "index_notes_owner_note"
        const val NOTES_OWNER_IDEA_STAGE = "index_notes_owner_idea_stage"
        const val CONNECTIONS_OWNER_TARGET = "index_note_connections_owner_target"
        const val REVIEW_OWNER_STATUS_DUE = "index_note_review_owner_status_due"
        const val STAGE_HISTORY_OWNER_NOTE = "index_idea_stage_history_owner_note"
    }
}
