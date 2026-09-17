package com.example.quickpavlo.ui.host

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quickpavlo.domain.network.P2PConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HostViewModel @Inject constructor(
    private val p2pManager: P2PConnectionManager
) : ViewModel() {

    // 1. EL ESTADO DEL QR: Aquí guardaremos la contraseña secreta que se volverá imagen
    private val _qrToken = MutableStateFlow<String?>(null)
    val qrToken = _qrToken.asStateFlow()

    // 2. EL ESTADO DEL MOTOR: Escuchamos si estamos conectando, conectados o con error
    val connectionState = p2pManager.connectionState

    // 3. LOS MENSAJES: Lo que recibiremos del cliente
    val incomingPayloads = p2pManager.incomingPayloads

    fun startHosting(userName: String = "Anfitrión-QuickPavlo") {
        // Generamos un código único aleatorio corto (8 caracteres son suficientes para un QR rápido)
        val token = UUID.randomUUID().toString().substring(0, 8)
        _qrToken.value = token


        p2pManager.startHosting(userName, token)
    }

    fun stopHosting() {
        p2pManager.disconnect()
        _qrToken.value = null
    }

    // Regla de limpieza: Si el usuario cierra la app o cambia de pantalla, apagamos la antena
    override fun onCleared() {
        super.onCleared()
        stopHosting()
    }
}