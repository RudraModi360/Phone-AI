package com.example.memory

enum class MemoryType(val value: String, val description: String) {
    USER("user", "User's role, goals, responsibilities, knowledge"),
    FEEDBACK("feedback", "Corrections and confirmations from the user"),
    PROJECT("project", "Ongoing work not derivable from code"),
    REFERENCE("reference", "Pointers to external systems");

    companion object {
        fun fromString(value: String): MemoryType {
            return entries.find { it.value == value } ?: PROJECT
        }
    }
}
