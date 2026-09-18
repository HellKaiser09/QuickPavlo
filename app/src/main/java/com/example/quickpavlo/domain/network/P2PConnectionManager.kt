package com.example.quickpavlo.domain.network

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class ConnectionState {
    IDLE,
    ADVERTISING,
    DISCOVERING,
    CONNECTING,
    CONNECTED,
    ERROR
}

sealed class PayloadEvent {
    data class Text(val text: String) : PayloadEvent()
    data class FileMeta(
        val payloadId: Long,
        val fileName: String,
        val fileSize: Long,
        val offset: Long = 0L,
        val originalPayloadId: Long = 0L
    ) : PayloadEvent()
    data class FileProgress(
        val payloadId: Long,
        val bytesTransferred: Long,
        val totalBytes: Long,
        val status: String
    ) : PayloadEvent()
    data class FileReceived(val payloadId: Long, val fileUri: String) : PayloadEvent()
    data class ControlResume(val payloadId: Long, val offset: Long) : PayloadEvent()
    data class MetaResume(val newPayloadId: Long, val oldPayloadId: Long, val offset: Long) : PayloadEvent()
}

interface P2PConnectionManager {
    val connectionState: StateFlow<ConnectionState>
    val activeSessionId: StateFlow<String>
    val incomingPayloads: Flow<String>
    val payloadEvents: Flow<PayloadEvent>

    fun startHosting(userName: String, authToken: String)
    fun startDiscovering(userName: String, authToken: String)

    fun sendPayload(data: String)
    fun sendFilePayload(uri: Uri, fileName: String, fileSize: Long): Long?
    fun sendFilePayloadWithOffset(
        uri: Uri,
        fileName: String,
        fileSize: Long,
        offset: Long,
        originalPayloadId: Long
    ): Long?

    fun disconnect()
}
