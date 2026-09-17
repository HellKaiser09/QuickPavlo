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
    data class FileMeta(val payloadId: Long, val fileName: String, val fileSize: Long) : PayloadEvent()
    data class FileProgress(
        val payloadId: Long,
        val bytesTransferred: Long,
        val totalBytes: Long,
        val status: String // "IN_PROGRESS", "COMPLETED", "FAILED", "PAUSED"
    ) : PayloadEvent()
    data class FileReceived(val payloadId: Long, val fileUri: String) : PayloadEvent()
}

interface P2PConnectionManager {
    val connectionState: StateFlow<ConnectionState>
    val incomingPayloads: Flow<String>
    val payloadEvents: Flow<PayloadEvent>

    fun startHosting(userName: String, authToken: String)
    fun startDiscovering(userName: String, authToken: String)

    fun sendPayload(data: String)
    fun sendFilePayload(uri: Uri, fileName: String, fileSize: Long): Long?
    fun disconnect()
}
