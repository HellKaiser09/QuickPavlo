package com.example.quickpavlo.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val fileTransferDao: FileTransferDao,
    private val p2pManager: P2PConnectionManager
) : ChatRepository {

    private val TAG = "QuickPavloP2P"
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
                val currentSessionId = p2pManager.activeSessionId.value
                when (event) {
                    is PayloadEvent.Text -> {
                        val entity = ChatMessageEntity(
                            id = UUID.randomUUID().toString(),
                            sessionId = currentSessionId,
                            senderName = "Peer",
                            message = event.text,
                            timestamp = System.currentTimeMillis(),
                            isFromMe = false,
                            isFile = false
                        )
                        chatDao.insertMessage(entity)
                    }
                    is PayloadEvent.FileMeta -> {
                        Log.i(TAG, "[REPO] Evento FileMeta recibido: payloadId=${event.payloadId}, name=${event.fileName}, size=${event.fileSize}, offset=${event.offset}")
                        val existingMessage = chatDao.getMessageByPayloadId(event.payloadId)
                        if (existingMessage == null) {
                            val entity = ChatMessageEntity(
                                id = event.payloadId.toString(),
                                sessionId = currentSessionId,
                                senderName = "Peer",
                                message = event.fileName,
                                timestamp = System.currentTimeMillis(),
                                isFromMe = false,
                                isFile = true,
                                fileName = event.fileName,
                                fileSize = event.fileSize,
                                bytesTransferred = event.offset,
                                fileStatus = "IN_PROGRESS",
                                payloadId = event.payloadId
                            )
                            chatDao.insertMessage(entity)
                        } else {
                            chatDao.updateFileProgress(event.payloadId, event.offset, "IN_PROGRESS")
                        }
                    }
                    is PayloadEvent.MetaResume -> {
                        Log.i(TAG, "[REPO] Evento MetaResume recibido: newPayloadId=${event.newPayloadId}, oldPayloadId=${event.oldPayloadId}, offset=${event.offset}")
                        chatDao.updateFileProgress(event.oldPayloadId, event.offset, "IN_PROGRESS")
                    }
                    is PayloadEvent.FileProgress -> {
                        chatDao.updateFileProgress(event.payloadId, event.bytesTransferred, event.status)
                    }
                    is PayloadEvent.FileReceived -> {
                        Log.i(TAG, "[REPO] Evento FileReceived recibido: payloadId=${event.payloadId}, path=${event.fileUri}")
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
                    is PayloadEvent.ControlResume -> {
                        Log.i(TAG, "[REPO] Evento ControlResume recibido en Emisor: oldPayloadId=${event.payloadId}, offset=${event.offset}")
                        val message = chatDao.getMessageByPayloadId(event.payloadId)
                        if (message != null && message.isFromMe && message.fileUri != null) {
                            val uri = Uri.parse(message.fileUri)
                            Log.i(TAG, "[EMISOR REANUDANDO POR CONTROL] Reanudando envio desde URI: $uri con offset: ${event.offset}")
                            val newPayloadId = p2pManager.sendFilePayloadWithOffset(
                                uri = uri,
                                fileName = message.fileName ?: "file",
                                fileSize = message.fileSize,
                                offset = event.offset,
                                originalPayloadId = event.payloadId
                            )
                            if (newPayloadId != null) {
                                Log.i(TAG, "[EMISOR REANUDANDO EXITOSO] Nuevo payloadId=$newPayloadId para originalId=${event.payloadId}")
                                chatDao.updateFileProgress(event.payloadId, event.offset, "IN_PROGRESS")
                            } else {
                                Log.e(TAG, "[EMISOR REANUDANDO ERROR] sendFilePayloadWithOffset devolvio NULL")
                            }
                        } else {
                            Log.e(TAG, "[EMISOR CONTROL ERROR] No se encontro el mensaje original o fileUri es NULL para payloadId=${event.payloadId}")
                        }
                    }
                }
            }
        }
    }

    override fun getChatHistory(): Flow<List<ChatMessage>> {
        return p2pManager.activeSessionId.flatMapLatest { sessionId ->
            if (sessionId.isBlank()) {
                chatDao.getChatHistory().map { entities -> entities.map { it.toDomain() } }
            } else {
                chatDao.getChatHistoryBySession(sessionId).map { entities -> entities.map { it.toDomain() } }
            }
        }
    }

    override suspend fun sendTextMessage(text: String, senderName: String) {
        if (text.isBlank()) return
        val currentSessionId = p2pManager.activeSessionId.value
        val entity = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = currentSessionId,
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
            val currentSessionId = p2pManager.activeSessionId.value
            val entity = ChatMessageEntity(
                id = payloadId.toString(),
                sessionId = currentSessionId,
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
        val message = chatDao.getMessageByPayloadId(payloadId) ?: run {
            Log.e(TAG, "[REANUDAR ERROR] No se encontro el mensaje con payloadId=$payloadId en Room")
            return
        }
        if (message.isFromMe) {
            // Emisor: Abrir archivo local y enviar con offset y META_RESUME
            if (message.fileUri != null) {
                val uri = Uri.parse(message.fileUri)
                val offset = message.bytesTransferred
                Log.i(TAG, "[EMISOR BOTON REANUDAR] Presionado por Emisor: payloadId=$payloadId, uri=$uri, offset=$offset")
                val newPayloadId = p2pManager.sendFilePayloadWithOffset(
                    uri = uri,
                    fileName = message.fileName ?: "file",
                    fileSize = message.fileSize,
                    offset = offset,
                    originalPayloadId = payloadId
                )
                if (newPayloadId != null) {
                    Log.i(TAG, "[EMISOR BOTON REANUDAR EXITO] Nuevo payloadId=$newPayloadId iniciado")
                    chatDao.updateFileProgress(payloadId, offset, "IN_PROGRESS")
                } else {
                    Log.e(TAG, "[EMISOR BOTON REANUDAR ERROR] sendFilePayloadWithOffset devolvio NULL")
                }
            } else {
                Log.e(TAG, "[EMISOR BOTON REANUDAR ERROR] fileUri es NULL para payloadId=$payloadId")
            }
        } else {
            // Receptor: Enviar mensaje de control CONTROL_RESUME|<payloadId>|<bytesTransferred> al Emisor
            val controlMessage = "CONTROL_RESUME|$payloadId|${message.bytesTransferred}"
            Log.i(TAG, "[RECEPTOR BOTON REANUDAR] Presionado por Receptor. Enviando comando exacto: $controlMessage")
            p2pManager.sendPayload(controlMessage)
            chatDao.updateFileProgress(payloadId, message.bytesTransferred, "IN_PROGRESS")
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
