package com.example.quickpavlo.di

import android.app.Application
import androidx.room.Room
import com.example.quickpavlo.data.local.AppDatabase
import com.example.quickpavlo.data.local.ChatDao
import com.example.quickpavlo.data.local.FileTransferDao
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
    fun provideAppDatabase(app: Application): AppDatabase{
        return Room.databaseBuilder(
            app,
            AppDatabase::class.java,
            "quickpavlo_p2p_db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideChatDao(db: AppDatabase): ChatDao {
        return db.chatDao
    }

    @Provides
    @Singleton
    fun provideFileTransferDao(db: AppDatabase): FileTransferDao{
        return db.fileTransferDao
    }
}
