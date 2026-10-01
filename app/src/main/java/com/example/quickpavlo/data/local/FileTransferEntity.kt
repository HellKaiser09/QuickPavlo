package com.example.quickpavlo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.quickpavlo.domain.model.FileTransfer

@Entity(tableName = "file_transfers")
data class FileTransferEntity (
    @PrimaryKey val payloadId: Long,
    val fileName: String,
    val totalBytes: Long,
    val bytesTransferred: Long,
    val isIncoming: Boolean,
    val isCompleted: Boolean
) {
    fun toDomain(): FileTransfer {
        return FileTransfer(payloadId, fileName, totalBytes, bytesTransferred, isIncoming, isCompleted)
    }
}
