package com.example.securenotes.repositories

import android.content.Context
import android.database.Cursor
import com.example.securenotes.providers.NotesContract

object NotesProviderClient {
    /** Returns ciphertext-only rows. The provider enforces the currently signed-in owner. */
    fun queryOwnedNotes(context: Context): Cursor? =
        context.applicationContext.contentResolver.query(
            NotesContract.CONTENT_URI,
            arrayOf(NotesContract.COLUMN_NOTE_ID, NotesContract.COLUMN_ENCRYPTED_TITLE, NotesContract.COLUMN_ENCRYPTED_CONTENT),
            null,
            null,
            "${NotesContract.COLUMN_UPDATED_AT} DESC"
        )
}
