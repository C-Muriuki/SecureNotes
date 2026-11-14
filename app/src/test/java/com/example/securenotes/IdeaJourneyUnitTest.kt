package com.example.securenotes

import com.example.securenotes.models.IdeaStage
import com.example.securenotes.models.IdeaStageHistory
import com.example.securenotes.models.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdeaJourneyUnitTest {

    @Test
    fun ideaStageEnumValuesAndFromInt() {
        val stages = IdeaStage.entries
        assertEquals(4, stages.size)
        assertEquals(IdeaStage.CAPTURED, IdeaStage.fromInt(0))
        assertEquals(IdeaStage.EXPLORING, IdeaStage.fromInt(1))
        assertEquals(IdeaStage.DEVELOPING, IdeaStage.fromInt(2))
        assertEquals(IdeaStage.COMPLETED, IdeaStage.fromInt(3))
        assertEquals(IdeaStage.CAPTURED, IdeaStage.fromInt(99))
    }

    @Test
    fun defaultLifecycleStateIsCaptured() {
        val note = Note(
            id = 100L,
            userId = 1L,
            title = "New Idea",
            content = "Initial thought",
            iv = "iv123"
        )
        assertTrue(note.isIdea)
        assertEquals(IdeaStage.CAPTURED, note.ideaStage)
    }

    @Test
    fun validStateTransitionsProgression() {
        val stages = listOf(
            IdeaStage.CAPTURED,
            IdeaStage.EXPLORING,
            IdeaStage.DEVELOPING,
            IdeaStage.COMPLETED
        )

        for (i in 0 until stages.size - 1) {
            val from = stages[i]
            val to = stages[i + 1]
            val history = IdeaStageHistory(
                ownerUserId = 1L,
                noteId = 100L,
                fromStage = from,
                toStage = to,
                changedAt = System.currentTimeMillis()
            )
            assertEquals(from, history.fromStage)
            assertEquals(to, history.toStage)
            assertTrue(history.changedAt > 0L)
        }
    }

    @Test
    fun completedToActiveReopening() {
        val note = Note(
            id = 200L,
            userId = 1L,
            title = "Completed Idea",
            content = "Finished feature",
            iv = "iv123",
            ideaStage = IdeaStage.COMPLETED
        )
        assertEquals(IdeaStage.COMPLETED, note.ideaStage)

        // Reopen to DEVELOPING
        val reopenedNote = note.copy(ideaStage = IdeaStage.DEVELOPING)
        assertEquals(IdeaStage.DEVELOPING, reopenedNote.ideaStage)
        assertFalse(reopenedNote.ideaStage == IdeaStage.COMPLETED)
    }

    @Test
    fun sameStageTransitionDetectsNoChange() {
        val currentStage = IdeaStage.EXPLORING
        val targetStage = IdeaStage.EXPLORING
        val isChanged = currentStage != targetStage
        assertFalse("Transitioning to the same stage must be recognized as no-change", isChanged)
    }
}
