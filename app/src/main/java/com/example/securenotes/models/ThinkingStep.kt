package com.example.securenotes.models

data class ThinkingStep(
    val id: String,
    val promptTitle: String,
    val promptDescription: String,
    val hintText: String = ""
)
