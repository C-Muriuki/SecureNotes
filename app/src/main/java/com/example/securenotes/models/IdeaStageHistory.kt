package com.example.securenotes.models

data class IdeaStageHistory(
    val id: Long = 0,
    val ownerUserId: Long,
    val noteId: Long,
    val fromStage: IdeaStage,
    val toStage: IdeaStage,
    val changedAt: Long
)
