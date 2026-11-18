package com.example.securenotes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.models.User
import com.example.securenotes.repositories.NoteRepository
import com.example.securenotes.repositories.RediscoveryRepository
import com.example.securenotes.repositories.UserRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class RediscoveryInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val disposableDatabases = mutableSetOf<String>()
    private val disposableKeyAliases = mutableSetOf<String>()

    private lateinit var dbName: String
    private lateinit var helper: DatabaseHelper
    private lateinit var notesRepo: NoteRepository
    private lateinit var rediscoveryRepo: RediscoveryRepository
    private lateinit var usersRepo: UserRepository
    private var firstUser = -1L
    private var secondUser = -1L

    @Before
    fun setUp() {
        dbName = "rediscovery_test_${System.nanoTime()}.db"
        disposableDatabases += dbName
        helper = DatabaseHelper(context, dbName)
        usersRepo = UserRepository(helper)
        notesRepo = NoteRepository(helper)
        rediscoveryRepo = RediscoveryRepository(helper, notesRepo)

        val suffix = System.nanoTime()
        firstUser = usersRepo.registerUser(
            User(fullName = "Rediscover Owner One", email = "r1-$suffix@test.local", passwordHash = "hash", passwordSalt = "salt")
        )
        secondUser = usersRepo.registerUser(
            User(fullName = "Rediscover Owner Two", email = "r2-$suffix@test.local", passwordHash = "hash", passwordSalt = "salt")
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
    fun createNoteIsEligibleForRediscoveryAndCandidatesAreOwned() {
        val noteId = notesRepo.createNote(firstUser, "Older Idea", "Body text")
        assertTrue(noteId > 0)

        val candidates = rediscoveryRepo.getRediscoveryCandidates(firstUser)
        assertEquals(1, candidates.size)
        assertEquals(noteId, candidates[0].note.id)

        // Cross-user isolation check
        val secondUserCandidates = rediscoveryRepo.getRediscoveryCandidates(secondUser)
        assertTrue(secondUserCandidates.isEmpty())
    }

    @Test
    fun markAsRevisitedUpdatesQueueAndLastOpenedTimestamp() {
        val noteId = notesRepo.createNote(firstUser, "Revisited Note", "Body")
        assertTrue(rediscoveryRepo.markAsRevisited(firstUser, noteId))

        val updatedNote = notesRepo.getNote(firstUser, noteId)
        assertTrue((updatedNote?.lastOpenedAt ?: 0) > 0)
    }

    @Test
    fun snoozeHidesCandidateUntilExpiration() {
        val noteId = notesRepo.createNote(firstUser, "Snoozed Note", "Body")
        assertTrue(rediscoveryRepo.snoozeRediscovery(firstUser, noteId, snoozeDays = 7))

        val candidates = rediscoveryRepo.getRediscoveryCandidates(firstUser)
        assertTrue("Snoozed note must be hidden from active candidates", candidates.none { it.note.id == noteId })

        val queueCandidates = rediscoveryRepo.getCandidatesByCategory(firstUser, "QUEUE")
        assertEquals(1, queueCandidates.size)
        assertEquals(noteId, queueCandidates[0].note.id)
    }

    @Test
    fun dismissAndRestoreCycle() {
        val noteId = notesRepo.createNote(firstUser, "Dismissable Note", "Body")
        assertTrue(rediscoveryRepo.dismissRediscovery(firstUser, noteId))

        val activeCandidates = rediscoveryRepo.getRediscoveryCandidates(firstUser)
        assertTrue(activeCandidates.none { it.note.id == noteId })

        val queueCandidates = rediscoveryRepo.getCandidatesByCategory(firstUser, "QUEUE")
        assertEquals(1, queueCandidates.size)

        // Restore
        assertTrue(rediscoveryRepo.restoreRediscovery(firstUser, noteId))
        val restoredActive = rediscoveryRepo.getRediscoveryCandidates(firstUser)
        assertEquals(1, restoredActive.size)
        assertEquals(noteId, restoredActive[0].note.id)
    }

    @Test
    fun cascadeDeleteRemovesReviewQueueEntry() {
        val noteId = notesRepo.createNote(firstUser, "Transient Note", "Body")
        rediscoveryRepo.snoozeRediscovery(firstUser, noteId, 7)
        assertEquals(1, rediscoveryRepo.getCandidatesByCategory(firstUser, "QUEUE").size)

        assertTrue(notesRepo.deleteNote(firstUser, noteId))
        assertTrue(rediscoveryRepo.getCandidatesByCategory(firstUser, "QUEUE").isEmpty())
    }
}
