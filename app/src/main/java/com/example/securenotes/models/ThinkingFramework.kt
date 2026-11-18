package com.example.securenotes.models

data class ThinkingFramework(
    val id: String,
    val title: String,
    val description: String,
    val steps: List<ThinkingStep>
)
