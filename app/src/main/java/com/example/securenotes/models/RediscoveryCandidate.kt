package com.example.securenotes.models

data class RediscoveryCandidate(
    val note: Note,
    val score: Int,
    val reason: String,
    val connectionCount: Int = 0,
    val reviewStatus: Int = 0,
    val dueAt: Long? = null
)
