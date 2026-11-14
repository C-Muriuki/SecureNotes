package com.example.securenotes.models

data class User(
    val userId: Long = 0,
    val fullName: String,
    val email: String,
    val passwordHash: String,
    val passwordSalt: String,
    val createdAt: Long = System.currentTimeMillis()
)
