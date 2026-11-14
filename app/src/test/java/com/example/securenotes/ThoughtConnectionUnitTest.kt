package com.example.securenotes

import com.example.securenotes.models.ConnectedThought
import com.example.securenotes.models.CreateConnectionResult
import com.example.securenotes.models.Note
import com.example.securenotes.models.ThoughtConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThoughtConnectionUnitTest {

    @Test
    fun createConnectionResultEnumValues() {
        val results = CreateConnectionResult.entries
        assertEquals(4, results.size)
        assertTrue(results.contains(CreateConnectionResult.CREATED))
        assertTrue(results.contains(CreateConnectionResult.ALREADY_CONNECTED))
        assertTrue(results.contains(CreateConnectionResult.SAME_NOTE))
        assertTrue(results.contains(CreateConnectionResult.NOTE_NOT_AVAILABLE))
    }

    @Test
    fun connectedThoughtModelProperties() {
        val note = Note(
            id = 10L,
            userId = 1L,
            title = "Business idea",
            content = "Marketplace concept",
            iv = "iv1",
            createdAt = 1000L,
            updatedAt = 2000L,
            isPinned = false,
            titleIv = "iv2"
        )
        val connectedThought = ConnectedThought(note = note, connectedAt = 5000L)

        assertEquals(10L, connectedThought.note.id)
        assertEquals("Business idea", connectedThought.note.title)
        assertEquals(5000L, connectedThought.connectedAt)
    }

    @Test
    fun thoughtConnectionModelProperties() {
        val firstNote = Note(
            id = 1L,
            userId = 1L,
            title = "First Note",
            content = "Content 1",
            iv = "iv1",
            createdAt = 1000L,
            updatedAt = 1000L,
            isPinned = false,
            titleIv = "iv1"
        )
        val secondNote = Note(
            id = 2L,
            userId = 1L,
            title = "Second Note",
            content = "Content 2",
            iv = "iv2",
            createdAt = 2000L,
            updatedAt = 2000L,
            isPinned = false,
            titleIv = "iv2"
        )
        val connection = ThoughtConnection(first = firstNote, second = secondNote, createdAt = 3000L)

        assertEquals(1L, connection.first.id)
        assertEquals(2L, connection.second.id)
        assertEquals(3000L, connection.createdAt)
    }

    @Test
    fun canonicalSourceTargetOrderingLogic() {
        val noteId1 = 15L
        val noteId2 = 42L

        val sourceId = minOf(noteId1, noteId2)
        val targetId = maxOf(noteId1, noteId2)

        assertEquals(15L, sourceId)
        assertEquals(42L, targetId)
        assertTrue(sourceId < targetId)

        val reversedSource = minOf(noteId2, noteId1)
        val reversedTarget = maxOf(noteId2, noteId1)

        assertEquals(sourceId, reversedSource)
        assertEquals(targetId, reversedTarget)
    }
}

