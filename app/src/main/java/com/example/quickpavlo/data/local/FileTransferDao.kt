package com.example.quickpavlo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FileTransferDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTransfer(transfer: FileTransferEntity)

    // Útil para buscar en qué byte nos quedamos si se corta la conexión (Tolerancia a fallos)
    @Query("SELECT * FROM file_transfers WHERE payloadId = :id")
    suspend fun getTransferById(id: Long): FileTransferEntity?

    @Query("SELECT * FROM file_transfers ORDER BY payloadId DESC")
    fun getAllTransfers(): Flow<List<FileTransferEntity>>

    // Limpieza de historial
    @Query("DELETE FROM file_transfers")
    suspend fun clearAllTransfers(): Int
}