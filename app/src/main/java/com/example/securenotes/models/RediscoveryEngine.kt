package com.example.securenotes.models

import java.util.concurrent.TimeUnit

object RediscoveryEngine {
    const val MAX_SCORE = 100
    const val MIN_SCORE = 0

    const val STATUS_PENDING = 0
    const val STATUS_REVIEWED = 1
    const val STATUS_DISMISSED = 2

    fun calculateScore(
        note: Note,
        connectionCount: Int = 0,
        lastReviewedAt: Long? = null,
        currentTime: Long = System.currentTimeMillis()
    ): Int {
        var score = 0
        val lastOpened = note.lastOpenedAt ?: note.updatedAt
        val daysSinceOpened = TimeUnit.MILLISECONDS.toDays((currentTime - lastOpened).coerceAtLeast(0))

        if (daysSinceOpened in 30..59) {
            score += 15
        } else if (daysSinceOpened in 60..89) {
            score += 25
        } else if (daysSinceOpened >= 90) {
            score += 35
        }

        when (note.ideaStage) {
            IdeaStage.CAPTURED -> score += 10
            IdeaStage.EXPLORING -> score += 8
            else -> {}
        }

        if (connectionCount > 0) {
            score += 10
        }

        if (note.lastOpenedAt == null) {
            score += 10
        }

        if (lastReviewedAt != null) {
            val daysSinceReviewed = TimeUnit.MILLISECONDS.toDays((currentTime - lastReviewedAt).coerceAtLeast(0))
            if (daysSinceReviewed < 7) {
                score -= 20
            }
        }

        return score.coerceIn(MIN_SCORE, MAX_SCORE)
    }

    fun isEligible(
        note: Note,
        ownerId: Long,
        reviewStatus: Int = STATUS_PENDING,
        dueAt: Long? = null,
        currentTime: Long = System.currentTimeMillis()
    ): Boolean {
        if (note.userId != ownerId) return false
        if (reviewStatus == STATUS_DISMISSED) return false
        if (dueAt != null && dueAt > currentTime) return false
        val lastOpened = note.lastOpenedAt ?: note.createdAt
        val daysSinceOpened = TimeUnit.MILLISECONDS.toDays((currentTime - lastOpened).coerceAtLeast(0))
        if (daysSinceOpened < 7 && note.lastOpenedAt != null) return false
        return true
    }

    fun generateReason(
        note: Note,
        connectionCount: Int = 0,
        currentTime: Long = System.currentTimeMillis()
    ): String {
        val lastOpened = note.lastOpenedAt
        val effectiveTime = lastOpened ?: note.createdAt
        val daysAgo = TimeUnit.MILLISECONDS.toDays((currentTime - effectiveTime).coerceAtLeast(0))

        val reasons = mutableListOf<String>()
        if (lastOpened == null) {
            reasons.add("Never revisited since creation ($daysAgo days ago)")
        } else if (daysAgo >= 30) {
            reasons.add("Not opened in $daysAgo days")
        }

        if (note.ideaStage == IdeaStage.CAPTURED) {
            reasons.add("Unfinished Captured idea")
        } else if (note.ideaStage == IdeaStage.EXPLORING) {
            reasons.add("Unfinished Exploring idea")
        }

        if (connectionCount > 0) {
            reasons.add("Connected to $connectionCount ${if (connectionCount == 1) "thought" else "thoughts"}")
        }

        return if (reasons.isNotEmpty()) reasons.joinToString(" • ")
        else "Eligible for review"
    }

    fun scoreAndRank(
        notes: List<Note>,
        ownerId: Long,
        connectionCounts: Map<Long, Int> = emptyMap(),
        reviewStatuses: Map<Long, Int> = emptyMap(),
        dueAts: Map<Long, Long?> = emptyMap(),
        lastReviewedAts: Map<Long, Long?> = emptyMap(),
        currentTime: Long = System.currentTimeMillis()
    ): List<RediscoveryCandidate> {
        return notes.filter { note ->
            val status = reviewStatuses[note.id] ?: STATUS_PENDING
            val dueAt = dueAts[note.id]
            isEligible(note, ownerId, status, dueAt, currentTime)
        }.map { note ->
            val count = connectionCounts[note.id] ?: 0
            val status = reviewStatuses[note.id] ?: STATUS_PENDING
            val dueAt = dueAts[note.id]
            val lastReviewedAt = lastReviewedAts[note.id]
            val score = calculateScore(note, count, lastReviewedAt, currentTime)
            val reason = generateReason(note, count, currentTime)
            RediscoveryCandidate(
                note = note,
                score = score,
                reason = reason,
                connectionCount = count,
                reviewStatus = status,
                dueAt = dueAt
            )
        }.sortedWith(
            compareByDescending<RediscoveryCandidate> { it.score }
                .thenBy { it.note.updatedAt }
                .thenBy { it.note.id }
        )
    }
}
