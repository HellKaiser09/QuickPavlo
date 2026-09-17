package com.example.quickpavlo.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.quickpavlo.data.local.ChatDao
import com.example.quickpavlo.data.local.ChatMessageEntity
import com.example.quickpavlo.data.local.FileTransferDao
import com.example.quickpavlo.data.local.FileTransferEntity
import com.example.quickpavlo.domain.model.ChatMessage
import com.example.quickpavlo.domain.network.ConnectionState
import com.example.quickpavlo.domain.network.P2PConnectionManager
import com.example.quickpavlo.domain.network.PayloadEvent
import com.example.quickpavlo.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val fileTransferDao: FileTransferDao,
    private val p2pManager: P2PConnectionManager
) : ChatRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        repositoryScope.launch {
            p2pManager.connectionState.collect { state ->
                if (state == ConnectionState.CONNECTED) {
                    val pendingList = chatDao.getPendingFileTransfers()
                    for (item in pendingList) {
                        if (item.fileStatus == "IN_PROGRESS") {
                            chatDao.updateFileProgress(item.payloadId, item.bytesTransferred, "PAUSED")
                        }
                    }
                }
            }
        }

        repositoryScope.launch {
            p2pManager.payloadEvents.collect { event ->
                when (event) {
                    is PayloadEvent.Text -> {
                        val entity = ChatMessageEntity(
                            id = UUID.randomUUID().toString(),
                            senderName = "Peer",
                            message = event.text,
                            timestamp = System.currentTimeMillis(),
                            isFromMe = false,
                            isFile = false
                        )
                        chatDao.insertMessage(entity)
                    }
                    is PayloadEvent.FileMeta -> {
                        val entity = ChatMessageEntity(
                            id = event.payloadId.toString(),
                            senderName = "Peer",
                            message = event.fileName,
                            timestamp = System.currentTimeMillis(),
                            isFromMe = false,
                            isFile = true,
                            fileName = event.fileName,
                            fileSize = event.fileSize,
                            bytesTransferred = 0L,
                            fileStatus = "IN_PROGRESS",
                            payloadId = event.payloadId
                        )
                        chatDao.insertMessage(entity)
                        fileTransferDao.insertOrUpdateTransfer(
                            FileTransferEntity(
                                payloadId = event.payloadId,
                                fileName = event.fileName,
                                totalBytes = event.fileSize,
                                bytesTransferred = 0L,
                                isIncoming = true,
                                isCompleted = false
                            )
                        )
                    }
                    is PayloadEvent.FileProgress -> {
                        chatDao.updateFileProgress(event.payloadId, event.bytesTransferred, event.status)
                    }
                    is PayloadEvent.FileReceived -> {
                        val existingMsg = chatDao.getMessageByPayloadId(event.payloadId)
                        val totalBytes = existingMsg?.fileSize ?: 0L
                        chatDao.updateFileCompleted(
                            payloadId = event.payloadId,
                            bytesTransferred = totalBytes,
                            status = "COMPLETED",
                            fileUri = event.fileUri
                        )
                        fileTransferDao.getTransferById(event.payloadId)?.let { transfer ->
                            fileTransferDao.insertOrUpdateTransfer(
                                transfer.copy(isCompleted = true, bytesTransferred = transfer.totalBytes)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun getChatHistory(): Flow<List<ChatMessage>> {
        return chatDao.getChatHistory().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun sendTextMessage(text: String, senderName: String) {
        if (text.isBlank()) return
        val entity = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            senderName = senderName,
            message = text,
            timestamp = System.currentTimeMillis(),
            isFromMe = true,
            isFile = false
        )
        chatDao.insertMessage(entity)
        p2pManager.sendPayload(text)
    }

    override suspend fun sendFileMessage(uri: Uri, context: Context, senderName: String) {
        val fileDetails = getFileDetails(context, uri)
        val fileName = fileDetails.first
        val fileSize = fileDetails.second

        val payloadId = p2pManager.sendFilePayload(uri, fileName, fileSize)
        if (payloadId != null) {
            val entity = ChatMessageEntity(
                id = payloadId.toString(),
                senderName = senderName,
                message = fileName,
                timestamp = System.currentTimeMillis(),
                isFromMe = true,
                isFile = true,
                fileUri = uri.toString(),
                fileName = fileName,
                fileSize = fileSize,
                bytesTransferred = 0L,
                fileStatus = "IN_PROGRESS",
                payloadId = payloadId
            )
            chatDao.insertMessage(entity)
            fileTransferDao.insertOrUpdateTransfer(
                FileTransferEntity(
                    payloadId = payloadId,
                    fileName = fileName,
                    totalBytes = fileSize,
                    bytesTransferred = 0L,
                    isIncoming = false,
                    isCompleted = false
                )
            )
        }
    }

    override suspend fun resumeFileTransfer(payloadId: Long, context: Context) {
        val message = chatDao.getMessageByPayloadId(payloadId)
        if (message != null && message.fileUri != null) {
            val uri = Uri.parse(message.fileUri)
            val newPayloadId = p2pManager.sendFilePayload(uri, message.fileName ?: "file", message.fileSize)
            if (newPayloadId != null) {
                chatDao.insertMessage(
                    message.copy(payloadId = newPayloadId, fileStatus = "IN_PROGRESS")
                )
            }
        }
    }

    override suspend fun clearHistory() {
        chatDao.clearAllMessages()
        fileTransferDao.clearAllTransfers()
    }

    override suspend fun exportChatHistory(context: Context): String? {
        val messages = chatDao.getChatHistory().firstOrNull() ?: emptyList()
        if (messages.isEmpty()) return null

        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        val builder = StringBuilder()
        builder.append("=== Historial de Chat QuickPavlo ===\n")
        builder.append("Exportado el: ${dateFormat.format(java.util.Date())}\n\n")

        for (msg in messages) {
            val time = dateFormat.format(java.util.Date(msg.timestamp))
            val sender = if (msg.isFromMe) "Yo" else msg.senderName
            if (msg.isFile) {
                builder.append("[$time] $sender [Archivo]: ${msg.fileName} (${msg.fileSize} bytes)\n")
            } else {
                builder.append("[$time] $sender: ${msg.message}\n")
            }
        }

        val exportFileName = "QuickPavlo_Chat_${System.currentTimeMillis()}.txt"
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, exportFileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(builder.toString().toByteArray())
                    }
                    uri.toString()
                } else null
            } else {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val file = java.io.File(downloadsDir, exportFileName)
                file.writeText(builder.toString())
                file.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getFileDetails(context: Context, uri: Uri): Pair<String, Long> {
        var name = "unknown_file"
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                    if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Pair(name, size)
    }
}
