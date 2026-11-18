package com.example.securenotes.models

data class ThinkingResponse(
    val id: Long = 0,
    val sessionId: Long,
    val ownerUserId: Long,
    val stepId: String,
    val response: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
