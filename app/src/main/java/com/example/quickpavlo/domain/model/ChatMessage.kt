package com.example.quickpavlo.domain.model

data class ChatMessage(
    val id: String,
    val senderId: String,
    val messageText: String,
    val timestamp: Long,
    val isEmergency: Boolean = false
)
