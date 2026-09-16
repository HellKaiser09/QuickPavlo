package com.example.quickpavlo.data.network

import android.content.Context
import com.example.quickpavlo.domain.network.ConnectionState
import com.example.quickpavlo.domain.network.P2PConnectionManager
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class NearbyP2PConnectionManagerImpl(
    private val context: Context
) : P2PConnectionManager {

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val strategy = Strategy.P2P_POINT_TO_POINT
    private val SERVICE_ID = "com.example.quickpavlo.P2P_SERVICE"

    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    override val connectionState = _connectionState.asStateFlow()

    private val _incomingPayloads = MutableSharedFlow<String>()
    override val incomingPayloads = _incomingPayloads.asSharedFlow()


    private var isHost = false
    private var currentAuthToken: String? = null
    private var currentUserName: String? = null

    // 1. EL PAQUETE (Recepción de datos)
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val text = payload.asBytes()?.let { String(it) } ?: ""
                _incomingPayloads.tryEmit(text)
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    // 2. EL ENLACE Y LA VALIDACIÓN CRIPTOGRÁFICA
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            _connectionState.value = ConnectionState.CONNECTING

            if (isHost) {
                // El cliente nos manda su nombre y el token como: "NombreDelCliente|Token"
                val incomingData = info.endpointName.split("|")
                val clientToken = if (incomingData.size == 2) incomingData[1] else ""

                if (clientToken == currentAuthToken) {
                    //  El cliente escaneó el QR físicamente.
                    connectionsClient.acceptConnection(endpointId, payloadCallback)
                } else {
                    // Rechazamos la conexión silenciosamente.
                    connectionsClient.rejectConnection(endpointId)
                    _connectionState.value = ConnectionState.ERROR
                }
            } else {
                // Somos el cliente. Aceptamos nuestra mitad del puente.
                connectionsClient.acceptConnection(endpointId, payloadCallback)
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) _connectionState.value = ConnectionState.CONNECTED
            else _connectionState.value = ConnectionState.ERROR
        }

        override fun onDisconnected(endpointId: String) {
            _connectionState.value = ConnectionState.IDLE
        }
    }

    // 3. EL RADAR (Buscando al anfitrión)
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            // El radar encontró la señal Bluetooth/WiFi de un anfitrión.
            // Para solicitar entrar, mandamos nuestro nombre fusionado con la clave que escaneamos
            val securityPayload = "${currentUserName ?: "Cliente"}|$currentAuthToken"

            connectionsClient.requestConnection(securityPayload, endpointId, connectionLifecycleCallback)
                .addOnFailureListener { _connectionState.value = ConnectionState.ERROR }
        }
        override fun onEndpointLost(endpointId: String) {}
    }

    // --- MÉTODOS PÚBLICOS ---

    override fun startHosting(userName: String, authToken: String) {
        isHost = true
        currentUserName = userName
        currentAuthToken = authToken

        // El anfitrión NO transmite el token por radio, solo se muestra en la UI como QR.
        val options = AdvertisingOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startAdvertising(userName, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnSuccessListener { _connectionState.value = ConnectionState.ADVERTISING }
            .addOnFailureListener { _connectionState.value = ConnectionState.ERROR }
    }

    override fun startDiscovering(userName: String, authToken: String) {
        isHost = false
        currentUserName = userName
        currentAuthToken = authToken

        val options = DiscoveryOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
            .addOnSuccessListener { _connectionState.value = ConnectionState.DISCOVERING }
            .addOnFailureListener { _connectionState.value = ConnectionState.ERROR }
    }

    override fun sendPayload(endpointId: String, data: String) {
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(data.toByteArray()))
    }

    override fun disconnect() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        _connectionState.value = ConnectionState.IDLE
    }
}
