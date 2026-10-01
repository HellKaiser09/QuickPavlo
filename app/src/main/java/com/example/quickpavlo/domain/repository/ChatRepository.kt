package com.example.quickpavlo.domain.repository

import android.content.Context
import android.net.Uri
import com.example.quickpavlo.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getChatHistory(): Flow<List<ChatMessage>>
    suspend fun sendTextMessage(text: String, senderName: String = "Me")
    suspend fun sendFileMessage(uri: Uri, context: Context, senderName: String = "Me")
    suspend fun resumeFileTransfer(payloadId: Long, context: Context)
    suspend fun exportChatHistory(context: Context): String?
    suspend fun clearHistory()
}
