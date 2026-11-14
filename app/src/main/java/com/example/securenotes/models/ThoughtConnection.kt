package com.example.securenotes.models

data class ConnectedThought(
    val note: Note,
    val connectedAt: Long
)

data class ThoughtConnection(
    val first: Note,
    val second: Note,
    val createdAt: Long
)

enum class CreateConnectionResult {
    CREATED,
    ALREADY_CONNECTED,
    SAME_NOTE,
    NOTE_NOT_AVAILABLE
}
