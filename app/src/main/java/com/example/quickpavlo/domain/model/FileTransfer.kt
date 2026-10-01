package com.example.quickpavlo.domain.model

data class FileTransfer (
    val payloadId: Long,
    val fileName: String,
    val totalBytes: Long,
    val bytesTransferred: Long,
    val isIncoming: Boolean,
    val isCompleted: Boolean
)