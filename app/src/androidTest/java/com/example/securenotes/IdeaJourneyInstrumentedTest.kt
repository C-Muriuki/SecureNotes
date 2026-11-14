package com.example.securenotes

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class IdeaJourneyInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val disposableDatabases = mutableSetOf<String>()
    private val disposableKeyAliases = mutableSetOf<String>()

    private lateinit var dbName: String
    private lateinit var helper: DatabaseHelper
    private lateinit var notes: NoteRepository
    private lateinit var users: UserRepository
    private var firstUser = -1L
    private var secondUser = -1L

    @Before
    fun setUp() {
        dbName = "idea_journey_${System.nanoTime()}.db"
        disposableDatabases += dbName
        helper = DatabaseHelper(context, dbName)
        users = UserRepository(helper)
        notes = NoteRepository(helper)
        val suffix = System.nanoTime()
        firstUser = users.registerUser(
            User(
                fullName = "Journey Owner One",
                email = "journey-one-$suffix@local.test",
                passwordHash = "hash",
                passwordSalt = "salt"
            )
        )
        secondUser = users.registerUser(
            User(
                fullName = "Journey Owner Two",
                email = "journey-two-$suffix@local.test",
                passwordHash = "hash",
                passwordSalt = "salt"
            )
        )
        disposableKeyAliases += "securenotes_user_$firstUser"
        disposableKeyAliases += "securenotes_user_$secondUser"
    }

    @After
    fun tearDown() {
        helper.close()
        disposableDatabases.forEach(context::deleteDatabase)
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        disposableKeyAliases.forEach { alias ->
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        }
    }

    @Test
    fun newNoteReceivesCapturedStageAndInitialHistoryEntry() {
        val noteId = notes.createNote(firstUser, "Fresh Idea", "Initial thought body")
        assertTrue(noteId > 0)

        val note = notes.getNote(firstUser, noteId)
        assertNotNull(note)
        assertEquals(IdeaStage.CAPTURED, note?.ideaStage)

        val history = notes.getIdeaStageHistory(firstUser, noteId)
        assertEquals(1, history.size)
        assertEquals(IdeaStage.CAPTURED, history[0].fromStage)
        assertEquals(IdeaStage.CAPTURED, history[0].toStage)
    }

    @Test
    fun stageTransitionsPersistAndTrackHistoryChronologically() {
        val noteId = notes.createNote(firstUser, "Evolving Project", "Research and roadmap")

        assertTrue(notes.updateIdeaStage(firstUser, noteId, IdeaStage.EXPLORING))
        val exploringNote = notes.getNote(firstUser, noteId)
        assertEquals(IdeaStage.EXPLORING, exploringNote?.ideaStage)

        assertTrue(notes.updateIdeaStage(firstUser, noteId, IdeaStage.DEVELOPING))
        val developingNote = notes.getNote(firstUser, noteId)
        assertEquals(IdeaStage.DEVELOPING, developingNote?.ideaStage)

        assertTrue(notes.updateIdeaStage(firstUser, noteId, IdeaStage.COMPLETED))
        val completedNote = notes.getNote(firstUser, noteId)
        assertEquals(IdeaStage.COMPLETED, completedNote?.ideaStage)

        // Reopen to DEVELOPING
        assertTrue(notes.updateIdeaStage(firstUser, noteId, IdeaStage.DEVELOPING))
        val reopenedNote = notes.getNote(firstUser, noteId)
        assertEquals(IdeaStage.DEVELOPING, reopenedNote?.ideaStage)

        val history = notes.getIdeaStageHistory(firstUser, noteId)
        assertEquals(5, history.size)
        assertEquals(IdeaStage.CAPTURED, history[0].toStage)
        assertEquals(IdeaStage.EXPLORING, history[1].toStage)
        assertEquals(IdeaStage.DEVELOPING, history[2].toStage)
        assertEquals(IdeaStage.COMPLETED, history[3].toStage)
        assertEquals(IdeaStage.DEVELOPING, history[4].toStage)
    }

    @Test
    fun duplicateStageUpdatesAreNoOpAndDoNotCreateDuplicateHistory() {
        val noteId = notes.createNote(firstUser, "Stable Idea", "Content")
        assertTrue(notes.updateIdeaStage(firstUser, noteId, IdeaStage.EXPLORING))
        val initialHistoryCount = notes.getIdeaStageHistory(firstUser, noteId).size

        assertTrue(notes.updateIdeaStage(firstUser, noteId, IdeaStage.EXPLORING))
        val afterDuplicateHistoryCount = notes.getIdeaStageHistory(firstUser, noteId).size

        assertEquals(initialHistoryCount, afterDuplicateHistoryCount)
    }

    @Test
    fun rejectsUnauthorizedCrossUserStageUpdatesAndHistoryAccess() {
        val ownerNoteId = notes.createNote(firstUser, "Private Idea", "Strictly owner")

        assertFalse(notes.updateIdeaStage(secondUser, ownerNoteId, IdeaStage.COMPLETED))
        assertEquals(IdeaStage.CAPTURED, notes.getNote(firstUser, ownerNoteId)?.ideaStage)

        assertTrue(notes.getIdeaStageHistory(secondUser, ownerNoteId).isEmpty())
        assertTrue(notes.getNotesByStage(secondUser, IdeaStage.CAPTURED).isEmpty())

        try {
            notes.updateIdeaStage(-1L, ownerNoteId, IdeaStage.DEVELOPING)
            fail("Unauthenticated update must throw")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun stageCountsReflectGenuineDatabaseRecords() {
        notes.createNote(firstUser, "Idea 1", "Body 1") // CAPTURED
        val n2 = notes.createNote(firstUser, "Idea 2", "Body 2")
        val n3 = notes.createNote(firstUser, "Idea 3", "Body 3")
        notes.updateIdeaStage(firstUser, n2, IdeaStage.EXPLORING)
        notes.updateIdeaStage(firstUser, n3, IdeaStage.COMPLETED)

        val counts = notes.getIdeaStageCounts(firstUser)
        assertEquals(1, counts[IdeaStage.CAPTURED])
        assertEquals(1, counts[IdeaStage.EXPLORING])
        assertEquals(0, counts[IdeaStage.DEVELOPING])
        assertEquals(1, counts[IdeaStage.COMPLETED])
    }

    @Test
    fun cascadeDeleteRemovesIdeaHistory() {
        val noteId = notes.createNote(firstUser, "Transient Idea", "Temporary body")
        notes.updateIdeaStage(firstUser, noteId, IdeaStage.EXPLORING)
        assertEquals(2, notes.getIdeaStageHistory(firstUser, noteId).size)

        assertTrue(notes.deleteNote(firstUser, noteId))
        assertTrue(notes.getIdeaStageHistory(firstUser, noteId).isEmpty())
    }

    @Test
    fun persistenceSurvivesDatabaseReopening() {
        val noteId = notes.createNote(firstUser, "Persistent Idea", "Survives reopen")
        notes.updateIdeaStage(firstUser, noteId, IdeaStage.DEVELOPING)

        helper.close()
        DatabaseHelper(context, dbName).use { reopened ->
            val repo = NoteRepository(reopened)
            val note = repo.getNote(firstUser, noteId)
            assertEquals(IdeaStage.DEVELOPING, note?.ideaStage)
            assertEquals(2, repo.getIdeaStageHistory(firstUser, noteId).size)
        }
        helper = DatabaseHelper(context, dbName)
        notes = NoteRepository(helper)
    }
}
