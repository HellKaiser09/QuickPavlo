package com.example.quickpavlo.ui.feature.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quickpavlo.data.local.UserIdentityManager
import com.example.quickpavlo.domain.network.ConnectionState
import com.example.quickpavlo.domain.network.P2PConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ClientViewModel @Inject constructor(
    private val p2pManager: P2PConnectionManager,
    private val userIdentityManager: UserIdentityManager
) : ViewModel() {
    val connectionState = p2pManager.connectionState
    val incomingPayloads = p2pManager.incomingPayloads

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting = _isConnecting.asStateFlow()

    init {
        viewModelScope.launch {
            connectionState.collect { state ->
                if (state == ConnectionState.CONNECTED || state == ConnectionState.ERROR || state == ConnectionState.IDLE) {
                    _isConnecting.value = false
                }
            }
        }
    }

    fun startConnecting(scannedToken: String) {
        if (connectionState.value == ConnectionState.CONNECTED) {
            _isConnecting.value = false
            return
        }
        if (_isConnecting.value || connectionState.value != ConnectionState.IDLE) return
        _isConnecting.value = true

        val token = if (scannedToken.contains("|")) {
            scannedToken.split("|")[1]
        } else {
            scannedToken
        }

        val userName = userIdentityManager.getUserName()
        p2pManager.startDiscovering(userName, token)
    }

    fun stopConnecting() {
        _isConnecting.value = false
        p2pManager.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        if (connectionState.value != ConnectionState.CONNECTED) {
            stopConnecting()
        }
    }
}
