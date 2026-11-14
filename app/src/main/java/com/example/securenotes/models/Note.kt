package com.example.securenotes.models

data class Note(
    val id: Long = 0,
    val userId: Long,
    val title: String,
    val content: String,
    val iv: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val titleIv: String = iv,
    val isIdea: Boolean = true,
    val ideaStage: IdeaStage = IdeaStage.CAPTURED,
    val lastOpenedAt: Long? = null
)
