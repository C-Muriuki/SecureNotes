package com.example.securenotes

import android.content.Context

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.securenotes.database.DatabaseContract
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.models.IdeaStage
import com.example.securenotes.models.User
import com.example.securenotes.repositories.NoteRepository
import com.example.securenotes.repositories.UserRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class IdeaJourneyUiTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val suffix = System.nanoTime()
    private val email = "journey-db-$suffix@example.test"

    private val dbName = "journey_test_$suffix.db"
    private lateinit var helper: DatabaseHelper
    private lateinit var notes: NoteRepository
    private var userId = -1L
    private var noteId = -1L

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
        helper = DatabaseHelper(context, dbName)
        val users = UserRepository(helper)
        userId = users.registerUser(
            User(
                fullName = "Journey DB QA",
                email = email,
                passwordHash = "hash",
                passwordSalt = "salt"
            )
        )
        notes = NoteRepository(helper)
        noteId = notes.createNote(userId, "Marketplace Concept", "Platform idea notes")
    }

    @After
    fun tearDown() {
        try {
            context.deleteDatabase(dbName)
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val alias = "securenotes_user_$userId"
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        } finally {
            helper.close()
        }
    }

    @Test
    fun databasePreservesIdeaStageFieldAndHistoryRecordCompatibility() {
        val initialNote = notes.getNote(userId, noteId)
        assertNotNull(initialNote)

        val updated = notes.updateIdeaStage(userId, noteId, IdeaStage.EXPLORING)
        assertTrue(updated)

        val counts = notes.getIdeaStageCounts(userId)
        assertEquals(1, counts[IdeaStage.EXPLORING])

        val history = notes.getIdeaStageHistory(userId, noteId)
        assertTrue(history.isNotEmpty())
        assertEquals(IdeaStage.EXPLORING, history.last().toStage)
    }

    @Test
    fun databaseCountsStagesCorrectlyWithoutUiInterference() {
        notes.updateIdeaStage(userId, noteId, IdeaStage.DEVELOPING)
        val counts = notes.getIdeaStageCounts(userId)
        assertEquals(1, counts[IdeaStage.DEVELOPING])
        assertEquals(0, counts[IdeaStage.EXPLORING])
    }
}
