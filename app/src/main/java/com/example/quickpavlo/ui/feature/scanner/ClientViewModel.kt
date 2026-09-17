package com.example.quickpavlo.ui.feature.scanner

import androidx.lifecycle.ViewModel
import com.example.quickpavlo.data.local.UserIdentityManager
import com.example.quickpavlo.domain.network.ConnectionState
import com.example.quickpavlo.domain.network.P2PConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ClientViewModel @Inject constructor(
    private val p2pManager: P2PConnectionManager,
    private val userIdentityManager: UserIdentityManager
) : ViewModel() {
    val connectionState = p2pManager.connectionState
    val incomingPayloads = p2pManager.incomingPayloads

    fun startConnecting(scannedToken: String) {
        if (connectionState.value != ConnectionState.IDLE) return
        
        // Si el QR tiene formato "deviceId|sessionToken", extraemos el sessionToken para Nearby
        val token = if (scannedToken.contains("|")) {
            scannedToken.split("|")[1]
        } else {
            scannedToken
        }
        
        val userName = userIdentityManager.getUserName()
        p2pManager.startDiscovering(userName, token)
    }

    fun stopConnecting() {
        p2pManager.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        if (connectionState.value != ConnectionState.CONNECTED) {
            stopConnecting()
        }
    }
}
