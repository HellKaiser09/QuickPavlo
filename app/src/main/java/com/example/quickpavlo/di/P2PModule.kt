package com.example.quickpavlo.di

import android.content.Context
import com.example.quickpavlo.data.local.UserIdentityManager
import com.example.quickpavlo.data.network.NearbyP2PConnectionManagerImpl
import com.example.quickpavlo.domain.network.P2PConnectionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object P2PModule {

    @Provides
    @Singleton
    fun provideP2PConnectionManager(
        @ApplicationContext context: Context,
        userIdentityManager: UserIdentityManager
    ) : P2PConnectionManager {
        return NearbyP2PConnectionManagerImpl(context, userIdentityManager)
    }
}