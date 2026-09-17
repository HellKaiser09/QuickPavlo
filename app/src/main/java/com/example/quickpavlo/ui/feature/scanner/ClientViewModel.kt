package com.example.quickpavlo.ui.feature.scanner

import androidx.lifecycle.ViewModel
import com.example.quickpavlo.domain.network.P2PConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ClientViewModel @Inject constructor(
    private val p2pManager: P2PConnectionManager
) : ViewModel() {
    val connectionState = p2pManager.connectionState
    val incomingPayloads = p2pManager.incomingPayloads

    fun startConnecting(scannedToken: String, userName: String = "Cliente-QuickPavlo") {
        p2pManager.startDiscovering(userName, scannedToken)
    }

    fun stopConnecting() {
        p2pManager.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        stopConnecting()
    }
}