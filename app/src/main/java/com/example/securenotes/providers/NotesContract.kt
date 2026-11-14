package com.example.securenotes.providers

import android.net.Uri

object NotesContract {
    const val AUTHORITY = "com.example.securenotes.provider"
    val BASE_CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY")
    const val PATH_NOTES = "notes"
    val CONTENT_URI: Uri = BASE_CONTENT_URI.buildUpon().appendPath(PATH_NOTES).build()

    const val TABLE_NAME = "notes"
    const val COLUMN_NOTE_ID = "note_id"
    const val COLUMN_USER_ID = "user_id"
    const val COLUMN_ENCRYPTED_TITLE = "encrypted_title"
    const val COLUMN_TITLE_IV = "title_iv"
    const val COLUMN_ENCRYPTED_CONTENT = "encrypted_content"
    const val COLUMN_CONTENT_IV = "content_iv"
    const val COLUMN_CREATED_AT = "created_at"
    const val COLUMN_UPDATED_AT = "updated_at"
    const val COLUMN_IS_PINNED = "is_pinned"

    const val MIME_TYPE_NOTES = "vnd.android.cursor.dir/vnd.$AUTHORITY.note"
    const val MIME_TYPE_NOTE = "vnd.android.cursor.item/vnd.$AUTHORITY.note"
}
