package com.example.quickpavlo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getChatHistoryBySession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getChatHistory(): Flow<List<ChatMessageEntity>>

    @Query("UPDATE chat_messages SET bytesTransferred = :bytesTransferred, fileStatus = :status WHERE payloadId = :payloadId")
    suspend fun updateFileProgress(payloadId: Long, bytesTransferred: Long, status: String)

    @Query("UPDATE chat_messages SET bytesTransferred = :bytesTransferred, fileStatus = :status, fileUri = :fileUri WHERE payloadId = :payloadId")
    suspend fun updateFileCompleted(payloadId: Long, bytesTransferred: Long, status: String, fileUri: String)

    @Query("SELECT * FROM chat_messages WHERE payloadId = :payloadId LIMIT 1")
    suspend fun getMessageByPayloadId(payloadId: Long): ChatMessageEntity?

    @Query("SELECT * FROM chat_messages WHERE isFile = 1 AND fileStatus != 'COMPLETED'")
    suspend fun getPendingFileTransfers(): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages(): Int
}
