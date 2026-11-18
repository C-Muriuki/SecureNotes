package com.example.securenotes

import com.example.securenotes.models.IdeaStage
import com.example.securenotes.models.Note
import com.example.securenotes.models.RediscoveryEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class RediscoveryUnitTest {

    private val now = System.currentTimeMillis()
    private val dayMs = TimeUnit.DAYS.toMillis(1)

    @Test
    fun scoringFactorsCalculation() {
        val noteOpened30DaysAgo = Note(
            id = 1,
            userId = 100,
            title = "Old Idea",
            content = "Body",
            iv = "iv",
            createdAt = now - (40 * dayMs),
            updatedAt = now - (40 * dayMs),
            lastOpenedAt = now - (40 * dayMs),
            ideaStage = IdeaStage.CAPTURED
        )
        // Score: +15 (30-59 days) + 10 (CAPTURED) = 25
        val score = RediscoveryEngine.calculateScore(noteOpened30DaysAgo, connectionCount = 0, currentTime = now)
        assertEquals(25, score)

        val connectedNoteOpened90DaysAgo = Note(
            id = 2,
            userId = 100,
            title = "Very Old Connected Idea",
            content = "Body",
            iv = "iv",
            createdAt = now - (100 * dayMs),
            updatedAt = now - (100 * dayMs),
            lastOpenedAt = now - (100 * dayMs),
            ideaStage = IdeaStage.EXPLORING
        )
        // Score: +35 (90+ days) + 8 (EXPLORING) + 10 (connected) = 53
        val scoreConnected = RediscoveryEngine.calculateScore(connectedNoteOpened90DaysAgo, connectionCount = 2, currentTime = now)
        assertEquals(53, scoreConnected)
    }

    @Test
    fun scoreIsCappedAtMaximum() {
        val maxedNote = Note(
            id = 3,
            userId = 100,
            title = "Max Score Note",
            content = "Body",
            iv = "iv",
            createdAt = now - (200 * dayMs),
            updatedAt = now - (200 * dayMs),
            lastOpenedAt = null,
            ideaStage = IdeaStage.CAPTURED
        )
        // Score: +35 (90+d) + 10 (CAPTURED) + 10 (connected) + 10 (lastOpenedAt==null) = 65
        val score = RediscoveryEngine.calculateScore(maxedNote, connectionCount = 5, currentTime = now)
        assertTrue(score <= RediscoveryEngine.MAX_SCORE)
        assertTrue(score >= RediscoveryEngine.MIN_SCORE)
    }

    @Test
    fun recentReviewPenalizesScore() {
        val note = Note(
            id = 4,
            userId = 100,
            title = "Recently Reviewed Note",
            content = "Body",
            iv = "iv",
            createdAt = now - (40 * dayMs),
            updatedAt = now - (40 * dayMs),
            lastOpenedAt = now - (40 * dayMs),
            ideaStage = IdeaStage.CAPTURED
        )
        val scoreWithoutReview = RediscoveryEngine.calculateScore(note, currentTime = now)
        val scoreWithRecentReview = RediscoveryEngine.calculateScore(note, lastReviewedAt = now - (2 * dayMs), currentTime = now)

        assertEquals(25, scoreWithoutReview)
        assertEquals(5, scoreWithRecentReview) // 25 - 20 = 5
    }

    @Test
    fun eligibilityFiltering() {
        val ownerId = 100L
        val validNote = Note(
            id = 10,
            userId = ownerId,
            title = "Valid Note",
            content = "Body",
            iv = "iv",
            createdAt = now - (30 * dayMs),
            lastOpenedAt = now - (30 * dayMs)
        )

        assertTrue(RediscoveryEngine.isEligible(validNote, ownerId, currentTime = now))

        // Exclude cross-user note
        val wrongUserNote = validNote.copy(userId = 999L)
        assertFalse(RediscoveryEngine.isEligible(wrongUserNote, ownerId, currentTime = now))

        // Exclude dismissed note
        assertFalse(RediscoveryEngine.isEligible(validNote, ownerId, reviewStatus = RediscoveryEngine.STATUS_DISMISSED, currentTime = now))

        // Exclude snoozed note
        assertFalse(RediscoveryEngine.isEligible(validNote, ownerId, dueAt = now + (5 * dayMs), currentTime = now))

        // Include expired snooze
        assertTrue(RediscoveryEngine.isEligible(validNote, ownerId, dueAt = now - (1 * dayMs), currentTime = now))

        // Exclude note opened recently (< 7 days ago)
        val recentlyOpenedNote = validNote.copy(lastOpenedAt = now - (2 * dayMs))
        assertFalse(RediscoveryEngine.isEligible(recentlyOpenedNote, ownerId, currentTime = now))
    }

    @Test
    fun rankingAndTieBreaking() {
        val ownerId = 100L
        val note1 = Note(id = 1, userId = ownerId, title = "Note 1", content = "", iv = "", createdAt = now - (40 * dayMs), lastOpenedAt = now - (40 * dayMs), ideaStage = IdeaStage.CAPTURED)
        val note2 = Note(id = 2, userId = ownerId, title = "Note 2", content = "", iv = "", createdAt = now - (80 * dayMs), lastOpenedAt = now - (80 * dayMs), ideaStage = IdeaStage.CAPTURED)

        val ranked = RediscoveryEngine.scoreAndRank(
            notes = listOf(note1, note2),
            ownerId = ownerId,
            currentTime = now
        )

        assertEquals(2, ranked.size)
        // note2 has higher score because it was opened 80 days ago (+25) vs 40 days ago (+15)
        assertEquals(2L, ranked[0].note.id)
        assertEquals(1L, ranked[1].note.id)
    }

    @Test
    fun generateHumanReadableReasons() {
        val note = Note(
            id = 50,
            userId = 100,
            title = "Reason Test Note",
            content = "Body",
            iv = "iv",
            createdAt = now - (45 * dayMs),
            lastOpenedAt = now - (45 * dayMs),
            ideaStage = IdeaStage.CAPTURED
        )

        val reason = RediscoveryEngine.generateReason(note, connectionCount = 3, currentTime = now)
        assertTrue(reason.contains("Not opened in 45 days"))
        assertTrue(reason.contains("Unfinished Captured idea"))
        assertTrue(reason.contains("Connected to 3 thoughts"))
    }
}
