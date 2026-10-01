package com.example.quickpavlo.ui.host

import androidx.lifecycle.ViewModel
import com.example.quickpavlo.data.local.UserIdentityManager
import com.example.quickpavlo.domain.network.ConnectionState
import com.example.quickpavlo.domain.network.P2PConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HostViewModel @Inject constructor(
    private val p2pManager: P2PConnectionManager,
    private val userIdentityManager: UserIdentityManager
) : ViewModel() {

    private val _qrToken = MutableStateFlow<String?>(null)
    val qrToken = _qrToken.asStateFlow()

    val connectionState = p2pManager.connectionState
    val incomingPayloads = p2pManager.incomingPayloads

    fun startHosting() {
        if (_qrToken.value != null && connectionState.value != ConnectionState.IDLE) return
        
        val deviceId = userIdentityManager.getDeviceId()
        val sessionToken = UUID.randomUUID().toString().substring(0, 8)
        
        // Formato de token QR: "deviceId|sessionToken"
        val fullQrContent = "$deviceId|$sessionToken"
        _qrToken.value = fullQrContent

        val userName = userIdentityManager.getUserName()
        p2pManager.startHosting(userName, sessionToken)
    }

    fun stopHosting() {
        p2pManager.disconnect()
        _qrToken.value = null
    }

    override fun onCleared() {
        super.onCleared()
        if (connectionState.value != ConnectionState.CONNECTED) {
            stopHosting()
        }
    }
}
