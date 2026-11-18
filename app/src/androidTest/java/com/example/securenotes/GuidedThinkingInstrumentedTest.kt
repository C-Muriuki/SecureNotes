package com.example.securenotes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.securenotes.database.DatabaseContract
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.models.User
import com.example.securenotes.repositories.GuidedThinkingRepository
import com.example.securenotes.repositories.NoteRepository
import com.example.securenotes.repositories.UserRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class GuidedThinkingInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val disposableDatabases = mutableSetOf<String>()
    private val disposableKeyAliases = mutableSetOf<String>()

    private lateinit var dbName: String
    private lateinit var helper: DatabaseHelper
    private lateinit var notesRepo: NoteRepository
    private lateinit var thinkingRepo: GuidedThinkingRepository
    private lateinit var usersRepo: UserRepository
    private var firstUser = -1L
    private var secondUser = -1L

    @Before
    fun setUp() {
        dbName = "guided_thinking_test_${System.nanoTime()}.db"
        disposableDatabases += dbName
        helper = DatabaseHelper(context, dbName)
        usersRepo = UserRepository(helper)
        notesRepo = NoteRepository(helper)
        thinkingRepo = GuidedThinkingRepository(helper, noteRepository = notesRepo)

        val suffix = System.nanoTime()
        firstUser = usersRepo.registerUser(
            User(fullName = "Thinking Owner One", email = "t1-$suffix@test.local", passwordHash = "hash", passwordSalt = "salt")
        )
        secondUser = usersRepo.registerUser(
            User(fullName = "Thinking Owner Two", email = "t2-$suffix@test.local", passwordHash = "hash", passwordSalt = "salt")
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
    fun createSessionAndSaveEncryptedResponses() {
        val sourceNoteId = notesRepo.createNote(firstUser, "Project Idea", "Initial thought content")
        val sessionId = thinkingRepo.createSession(firstUser, "FIVE_WHYS", sourceNoteId)
        assertTrue(sessionId > 0)

        assertTrue(thinkingRepo.saveResponse(firstUser, sessionId, "WHY_1", "High server latency"))
        assertTrue(thinkingRepo.saveResponse(firstUser, sessionId, "WHY_2", "Database lock contention"))

        val session = thinkingRepo.getSession(firstUser, sessionId)
        assertNotNull(session)
        assertEquals("FIVE_WHYS", session?.frameworkId)
        assertEquals(sourceNoteId, session?.sourceNoteId)
        assertEquals(2, session?.responses?.size)
        assertEquals("High server latency", session?.responses?.find { it.stepId == "WHY_1" }?.response)

        // Verify SQLite raw database contains encrypted response string, not plaintext
        val db = helper.readableDatabase
        db.query(
            DatabaseContract.ThinkingResponseTable.TABLE_NAME,
            arrayOf(DatabaseContract.ThinkingResponseTable.COLUMN_ENCRYPTED_RESPONSE),
            "${DatabaseContract.ThinkingResponseTable.COLUMN_SESSION_ID} = ?",
            arrayOf(sessionId.toString()),
            null, null, null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            val rawCiphertext = cursor.getString(0)
            assertFalse(rawCiphertext.contains("High server latency"))
        }
    }

    @Test
    fun ownerIsolationAndCrossUserSecurity() {
        val sessionId = thinkingRepo.createSession(firstUser, "SWOT")
        thinkingRepo.saveResponse(firstUser, sessionId, "SWOT_STRENGTHS", "Strong architecture")

        // Second user cannot access first user's thinking session
        val crossUserSession = thinkingRepo.getSession(secondUser, sessionId)
        assertTrue(crossUserSession == null)

        val secondUserSessions = thinkingRepo.getSessionHistory(secondUser)
        assertTrue(secondUserSessions.isEmpty())
    }

    @Test
    fun createNoteFromCompletedSessionLinksToSourceNote() {
        val sourceNoteId = notesRepo.createNote(firstUser, "Original Thought", "Initial content")
        val sessionId = thinkingRepo.createSession(firstUser, "PROS_CONS", sourceNoteId)
        thinkingRepo.saveResponse(firstUser, sessionId, "PROS", "High throughput and scalability")
        thinkingRepo.saveResponse(firstUser, sessionId, "CONS", "Increased operational cost")
        thinkingRepo.completeSession(firstUser, sessionId)

        val createdNoteId = thinkingRepo.createNoteFromSession(firstUser, sessionId)
        assertTrue(createdNoteId > 0)

        val createdNote = notesRepo.getNote(firstUser, createdNoteId)
        assertNotNull(createdNote)
        assertTrue(createdNote?.title?.contains("Guided Reflection") == true)
        assertTrue(createdNote?.content?.contains("High throughput and scalability") == true)

        // Verify connected thoughts relationship was created
        val connections = notesRepo.getConnectedNotes(firstUser, sourceNoteId)
        assertEquals(1, connections.size)
        assertEquals(createdNoteId, connections[0].note.id)
    }

    @Test
    fun cascadeDeleteRemovesThinkingSessionAndResponses() {
        val sessionId = thinkingRepo.createSession(firstUser, "IDEA_EXPANSION")
        thinkingRepo.saveResponse(firstUser, sessionId, "EXPAND_1", "Autonomous agent")

        assertTrue(thinkingRepo.deleteSession(firstUser, sessionId))
        assertTrue(thinkingRepo.getSession(firstUser, sessionId) == null)

        val db = helper.readableDatabase
        db.query(
            DatabaseContract.ThinkingResponseTable.TABLE_NAME,
            arrayOf(DatabaseContract.ThinkingResponseTable.COLUMN_RESPONSE_ID),
            "${DatabaseContract.ThinkingResponseTable.COLUMN_SESSION_ID} = ?",
            arrayOf(sessionId.toString()),
            null, null, null
        ).use { cursor ->
            assertFalse(cursor.moveToFirst())
        }
    }
}
