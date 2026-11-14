package com.example.securenotes.models

enum class IdeaStage(val value: Int) {
    CAPTURED(0),
    EXPLORING(1),
    DEVELOPING(2),
    COMPLETED(3);

    companion object {
        fun fromInt(value: Int): IdeaStage = entries.find { it.value == value } ?: CAPTURED
    }
}
