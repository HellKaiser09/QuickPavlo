package com.example.quickpavlo.di

import android.app.Application
import androidx.room.Room
import com.example.quickpavlo.data.local.AppDatabase
import com.example.quickpavlo.data.local.ChatDao
import com.example.quickpavlo.data.local.FileTransferDao
import com.example.quickpavlo.data.repository.ChatRepositoryImpl
import com.example.quickpavlo.domain.network.P2PConnectionManager
import com.example.quickpavlo.domain.repository.ChatRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAppDatabase(app: Application): AppDatabase {
        return Room.databaseBuilder(
            app,
            AppDatabase::class.java,
            "quickpavlo_p2p_db"
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    @Singleton
    fun provideChatDao(db: AppDatabase): ChatDao {
        return db.chatDao
    }

    @Provides
    @Singleton
    fun provideFileTransferDao(db: AppDatabase): FileTransferDao {
        return db.fileTransferDao
    }

    @Provides
    @Singleton
    fun provideChatRepository(
        chatDao: ChatDao,
        fileTransferDao: FileTransferDao,
        p2pManager: P2PConnectionManager
    ): ChatRepository {
        return ChatRepositoryImpl(chatDao, fileTransferDao, p2pManager)
    }
}
