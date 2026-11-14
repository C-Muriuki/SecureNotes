package com.example.securenotes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.models.CreateConnectionResult
import com.example.securenotes.models.User
import com.example.securenotes.repositories.NoteRepository
import com.example.securenotes.repositories.UserRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class ThoughtConnectionsInstrumentedTest {
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
        dbName = "thought_connections_${System.nanoTime()}.db"
        disposableDatabases += dbName
        helper = DatabaseHelper(context, dbName)
        users = UserRepository(helper)
        notes = NoteRepository(helper)
        val suffix = System.nanoTime()
        firstUser = users.registerUser(
            User(
                fullName = "Owner One",
                email = "owner-one-$suffix@local.test",
                passwordHash = "hash",
                passwordSalt = "salt"
            )
        )
        secondUser = users.registerUser(
            User(
                fullName = "Owner Two",
                email = "owner-two-$suffix@local.test",
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
    fun createsRetrievesAndRemovesConnectionsInEitherDirection() {
        val idea = notes.createNote(firstUser, "Business idea", "Community marketplace")
        val research = notes.createNote(firstUser, "Market research", "Audience notes")

        assertEquals(CreateConnectionResult.CREATED, notes.connectNotes(firstUser, idea, research))
        assertTrue(notes.isConnected(firstUser, idea, research))
        assertTrue(notes.isConnected(firstUser, research, idea))

        val fromIdea = notes.getConnectedNotes(firstUser, idea)
        assertEquals(1, fromIdea.size)
        assertEquals(research, fromIdea[0].note.id)
        assertTrue(fromIdea[0].connectedAt > 0L)
        assertEquals("Market research", fromIdea[0].note.title)
        assertTrue(fromIdea[0].note.content.contains("Audience"))

        val fromResearch = notes.getConnectedNotes(firstUser, research)
        assertEquals(1, fromResearch.size)
        assertEquals(idea, fromResearch[0].note.id)

        assertTrue(notes.removeConnection(firstUser, research, idea))
        assertFalse(notes.isConnected(firstUser, idea, research))
        assertTrue(notes.getConnectedNotes(firstUser, idea).isEmpty())
    }

    @Test
    fun rejectsSelfDuplicateCrossUserAndUnauthenticatedOperations() {
        val first = notes.createNote(firstUser, "Idea", "Body one")
        val second = notes.createNote(firstUser, "Research", "Body two")
        val foreign = notes.createNote(secondUser, "Foreign", "Should stay private")

        assertEquals(CreateConnectionResult.SAME_NOTE, notes.connectNotes(firstUser, first, first))
        assertEquals(CreateConnectionResult.CREATED, notes.connectNotes(firstUser, first, second))
        assertEquals(CreateConnectionResult.ALREADY_CONNECTED, notes.connectNotes(firstUser, second, first))
        assertEquals(CreateConnectionResult.NOTE_NOT_AVAILABLE, notes.connectNotes(firstUser, first, foreign))
        assertEquals(CreateConnectionResult.NOTE_NOT_AVAILABLE, notes.connectNotes(secondUser, first, foreign))

        assertTrue(notes.getConnectedNotes(secondUser, foreign).isEmpty())
        assertTrue(notes.getRecentConnections(secondUser).isEmpty())
        assertFalse(notes.isConnected(secondUser, first, second))
        assertFalse(notes.removeConnection(secondUser, first, second))
        assertTrue(notes.isConnected(firstUser, first, second))

        try {
            notes.connectNotes(0L, first, second)
            fail("Unauthenticated connection must be rejected")
        } catch (_: IllegalArgumentException) {
        }

        try {
            notes.getConnectedNotes(-1L, first)
            fail("Unauthenticated retrieval must be rejected")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun candidatesExcludeCurrentAndAlreadyConnectedNotes() {
        val current = notes.createNote(firstUser, "Current", "Seed")
        val connected = notes.createNote(firstUser, "Connected", "Linked")
        val available = notes.createNote(firstUser, "Available", "Open")
        notes.createNote(secondUser, "Other user", "Hidden")

        assertEquals(CreateConnectionResult.CREATED, notes.connectNotes(firstUser, current, connected))

        val candidates = notes.getConnectionCandidates(firstUser, current)
        assertEquals(1, candidates.size)
        assertEquals(available, candidates[0].id)
        assertTrue(candidates.none { it.id == current })
        assertTrue(candidates.none { it.id == connected })
    }

    @Test
    fun cascadeDeleteRemovesConnectionsAndPersistenceSurvivesReopen() {
        val left = notes.createNote(firstUser, "Left", "A")
        val right = notes.createNote(firstUser, "Right", "B")
        val keep = notes.createNote(firstUser, "Keep", "C")
        assertEquals(CreateConnectionResult.CREATED, notes.connectNotes(firstUser, left, right))
        assertEquals(CreateConnectionResult.CREATED, notes.connectNotes(firstUser, left, keep))

        helper.close()
        DatabaseHelper(context, dbName).use { reopened ->
            val repo = NoteRepository(reopened)
            assertTrue(repo.isConnected(firstUser, left, right))
            assertEquals(2, repo.getConnectedNotes(firstUser, left).size)
            assertTrue(repo.deleteNote(firstUser, left))
            assertTrue(repo.getConnectedNotes(firstUser, right).isEmpty())
            assertTrue(repo.getConnectedNotes(firstUser, keep).isEmpty())
            assertTrue(repo.getRecentConnections(firstUser).isEmpty())
        }
        helper = DatabaseHelper(context, dbName)
        notes = NoteRepository(helper)
    }

    @Test
    fun rapidRepeatedConnectRequestsRemainSafe() {
        val one = notes.createNote(firstUser, "One", "First")
        val two = notes.createNote(firstUser, "Two", "Second")

        val first = notes.connectNotes(firstUser, one, two)
        val second = notes.connectNotes(firstUser, two, one)
        val third = notes.connectNotes(firstUser, one, two)

        assertEquals(CreateConnectionResult.CREATED, first)
        assertEquals(CreateConnectionResult.ALREADY_CONNECTED, second)
        assertEquals(CreateConnectionResult.ALREADY_CONNECTED, third)
        assertEquals(1, notes.getConnectedNotes(firstUser, one).size)
        assertEquals(1, notes.getRecentConnections(firstUser).size)
    }
}
