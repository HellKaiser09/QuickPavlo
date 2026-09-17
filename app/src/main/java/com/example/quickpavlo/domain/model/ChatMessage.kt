package com.example.quickpavlo.domain.model

data class ChatMessage(
    val id: String,
    val senderName: String = "",
    val messageText: String,
    val timestamp: Long,
    val isFromMe: Boolean,
    val isFile: Boolean = false,
    val fileUri: String? = null,
    val fileName: String? = null,
    val fileSize: Long = 0L,
    val bytesTransferred: Long = 0L,
    val fileStatus: String = "COMPLETED", // "IN_PROGRESS", "COMPLETED", "FAILED", "PAUSED"
    val payloadId: Long = 0L
)
