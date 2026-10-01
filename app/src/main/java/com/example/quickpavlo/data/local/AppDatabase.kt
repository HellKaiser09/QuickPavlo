package com.example.quickpavlo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

// Base de datos local Room de la aplicación
@Database(
    entities = [ChatMessageEntity::class, FileTransferEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract val chatDao: ChatDao
    abstract val fileTransferDao: FileTransferDao
}
