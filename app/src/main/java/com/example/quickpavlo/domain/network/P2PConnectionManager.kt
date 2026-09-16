package com.example.quickpavlo.domain.network

import kotlinx.coroutines.flow.Flow

enum class ConnectionState {
    IDLE,
    ADVERTISING,
    DISCOVERING,
    CONNECTING,
    CONNECTED,
    ERROR
}

interface P2PConnectionManager {
    val connectionState: Flow<ConnectionState>
    val incomingPayloads: Flow<String>

    fun startHosting(userName: String, authToken: String)
    fun startDiscovering(userName: String, authToken: String)

    fun sendPayload(endpointId: String, data: String)
    fun disconnect()
}