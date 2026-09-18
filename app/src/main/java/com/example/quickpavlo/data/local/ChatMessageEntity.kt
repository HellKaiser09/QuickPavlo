package com.example.quickpavlo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.quickpavlo.domain.model.ChatMessage

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String = "",
    val senderName: String,
    val message: String,
    val timestamp: Long,
    val isFromMe: Boolean,
    val isFile: Boolean = false,
    val fileUri: String? = null,
    val fileName: String? = null,
    val fileSize: Long = 0L,
    val bytesTransferred: Long = 0L,
    val fileStatus: String = "COMPLETED", // "IN_PROGRESS", "COMPLETED", "FAILED", "PAUSED"
    val payloadId: Long = 0L
) {
    fun toDomain(): ChatMessage {
        return ChatMessage(
            id = id,
            senderName = senderName,
            messageText = message,
            timestamp = timestamp,
            isFromMe = isFromMe,
            isFile = isFile,
            fileUri = fileUri,
            fileName = fileName,
            fileSize = fileSize,
            bytesTransferred = bytesTransferred,
            fileStatus = fileStatus,
            payloadId = payloadId
        )
    }

    companion object {
        fun fromDomain(domain: ChatMessage, sessionId: String = ""): ChatMessageEntity {
            return ChatMessageEntity(
                id = domain.id,
                sessionId = sessionId,
                senderName = domain.senderName,
                message = domain.messageText,
                timestamp = domain.timestamp,
                isFromMe = domain.isFromMe,
                isFile = domain.isFile,
                fileUri = domain.fileUri,
                fileName = domain.fileName,
                fileSize = domain.fileSize,
                bytesTransferred = domain.bytesTransferred,
                fileStatus = domain.fileStatus,
                payloadId = domain.payloadId
            )
        }
    }
}
