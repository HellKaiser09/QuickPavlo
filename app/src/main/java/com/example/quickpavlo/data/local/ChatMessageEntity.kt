package com.example.quickpavlo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.quickpavlo.domain.model.ChatMessage
import java.sql.Timestamp

@Entity(tableName = "chat_messages")
data class ChatMessageEntity (
    @PrimaryKey val id: String,
    val senderName: String,
    val message: String,
    val timestamp: Long,
    val isFromMe: Boolean
) {
    // Función de extensión útil para mapear hacia el dominio
    fun toDomain(): ChatMessage {
        return ChatMessage(id, senderName, message, timestamp, isFromMe)
    }
}
