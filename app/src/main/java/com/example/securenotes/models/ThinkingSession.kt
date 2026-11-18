package com.example.securenotes.models

data class ThinkingSession(
    val id: Long = 0,
    val ownerUserId: Long,
    val sourceNoteId: Long? = null,
    val frameworkId: String,
    val status: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val responses: List<ThinkingResponse> = emptyList()
)
