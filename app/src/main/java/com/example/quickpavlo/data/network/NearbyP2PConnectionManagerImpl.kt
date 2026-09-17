package com.example.quickpavlo.data.network

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.example.quickpavlo.domain.network.ConnectionState
import com.example.quickpavlo.domain.network.P2PConnectionManager
import com.example.quickpavlo.domain.network.PayloadEvent
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

class NearbyP2PConnectionManagerImpl(
    private val context: Context
) : P2PConnectionManager {

    private val p2pScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val strategy = Strategy.P2P_POINT_TO_POINT
    private val SERVICE_ID = "com.example.quickpavlo.P2P_SERVICE"

    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    override val connectionState = _connectionState.asStateFlow()

    private val _incomingPayloads = MutableSharedFlow<String>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val incomingPayloads = _incomingPayloads.asSharedFlow()

    private val _payloadEvents = MutableSharedFlow<PayloadEvent>(
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val payloadEvents: Flow<PayloadEvent> = _payloadEvents.asSharedFlow()

    private var connectedEndpointId: String? = null
    private var isHost = false
    private var currentAuthToken: String? = null
    private var currentUserName: String? = null

    // Track incoming file payloads and names
    private val incomingFilesMap = ConcurrentHashMap<Long, Payload.File>()
    private val fileNamesMap = ConcurrentHashMap<Long, String>()

    // 1. EL PAQUETE (Recepción de datos)
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val text = payload.asBytes()?.let { String(it) } ?: ""
                    if (text.startsWith("FILE_META|")) {
                        val parts = text.split("|")
                        if (parts.size >= 4) {
                            val payloadId = parts[1].toLongOrNull() ?: 0L
                            val fileName = parts[2]
                            val fileSize = parts[3].toLongOrNull() ?: 0L
                            fileNamesMap[payloadId] = fileName
                            _payloadEvents.tryEmit(PayloadEvent.FileMeta(payloadId, fileName, fileSize))
                        }
                    } else {
                        _incomingPayloads.tryEmit(text)
                        _payloadEvents.tryEmit(PayloadEvent.Text(text))
                    }
                }
                Payload.Type.FILE -> {
                    payload.asFile()?.let { payloadFile ->
                        incomingFilesMap[payload.id] = payloadFile
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val payloadId = update.payloadId
            val bytesTransferred = update.bytesTransferred
            val totalBytes = update.totalBytes

            val statusStr = when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS -> "COMPLETED"
                PayloadTransferUpdate.Status.IN_PROGRESS -> "IN_PROGRESS"
                PayloadTransferUpdate.Status.CANCELED -> "PAUSED"
                else -> "FAILED"
            }

            _payloadEvents.tryEmit(
                PayloadEvent.FileProgress(
                    payloadId = payloadId,
                    bytesTransferred = bytesTransferred,
                    totalBytes = totalBytes,
                    status = statusStr
                )
            )

            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                p2pScope.launch {
                    val payloadFile = incomingFilesMap.remove(payloadId)
                    if (payloadFile != null) {
                        val fileName = fileNamesMap.remove(payloadId) ?: "received_file_${payloadId}"
                        val savedPath = saveFileToDownloads(context, payloadFile, fileName)
                        if (savedPath != null) {
                            _payloadEvents.emit(PayloadEvent.FileReceived(payloadId, savedPath))
                        }
                    }
                }
            }
        }
    }

    // 2. EL ENLACE Y LA VALIDACIÓN CRIPTOGRÁFICA
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            _connectionState.value = ConnectionState.CONNECTING

            if (isHost) {
                val incomingData = info.endpointName.split("|")
                val clientToken = if (incomingData.size == 2) incomingData[1] else ""

                if (clientToken == currentAuthToken) {
                    connectionsClient.acceptConnection(endpointId, payloadCallback)
                } else {
                    connectionsClient.rejectConnection(endpointId)
                    _connectionState.value = ConnectionState.ERROR
                }
            } else {
                connectionsClient.acceptConnection(endpointId, payloadCallback)
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectedEndpointId = endpointId
                connectionsClient.stopAdvertising()
                connectionsClient.stopDiscovery()
                _connectionState.value = ConnectionState.CONNECTED
            } else {
                _connectionState.value = ConnectionState.ERROR
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpointId = null
            _connectionState.value = ConnectionState.IDLE
        }
    }

    // 3. EL RADAR (Buscando al anfitrión)
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
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

    override fun sendPayload(data: String) {
        connectedEndpointId?.let { id ->
            val payload = Payload.fromBytes(data.toByteArray())
            connectionsClient.sendPayload(id, payload)
        }
    }

    override fun sendFilePayload(uri: Uri, fileName: String, fileSize: Long): Long? {
        val id = connectedEndpointId ?: return null
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            val filePayload = Payload.fromFile(pfd)
            val payloadId = filePayload.id

            // First send file metadata header
            val metaString = "FILE_META|$payloadId|$fileName|$fileSize"
            connectionsClient.sendPayload(id, Payload.fromBytes(metaString.toByteArray()))

            // Then send file payload
            connectionsClient.sendPayload(id, filePayload)
            payloadId
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun disconnect() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedEndpointId = null
        incomingFilesMap.clear()
        fileNamesMap.clear()
        _connectionState.value = ConnectionState.IDLE
    }

    private suspend fun saveFileToDownloads(
        context: Context,
        payloadFile: Payload.File,
        fileName: String
    ): String? = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        try {
            val resolver = context.contentResolver

            // 1. Abrir InputStream desde la fuente temporal de Nearby (Uri, JavaFile o PFD)
            val tempUri: Uri? = payloadFile.asUri()
            val javaFile: File? = payloadFile.asJavaFile()
            val pfd: ParcelFileDescriptor? = payloadFile.asParcelFileDescriptor()

            inputStream = when {
                tempUri != null -> resolver.openInputStream(tempUri)
                javaFile != null && javaFile.exists() -> FileInputStream(javaFile)
                pfd != null -> FileInputStream(pfd.fileDescriptor)
                else -> null
            }

            if (inputStream == null) {
                return@withContext null
            }

            val subFolder = "QuickPavlo"

            // 2. Android 10+ (MediaStore.Downloads/QuickPavlo)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$subFolder")
                }
                val targetUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (targetUri != null) {
                    outputStream = resolver.openOutputStream(targetUri)
                    if (outputStream != null) {
                        inputStream.use { input ->
                            outputStream.use { output ->
                                input.copyTo(output)
                                output.flush()
                            }
                        }
                        if (javaFile != null && javaFile.exists()) {
                            javaFile.delete()
                        }
                        return@withContext targetUri.toString()
                    }
                }
            }

            // 3. Fallback para Android 9 e inferiores o File API
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val quickPavloDir = File(downloadsDir, subFolder)
            if (!quickPavloDir.exists()) {
                quickPavloDir.mkdirs()
            }

            var targetFile = File(quickPavloDir, fileName)
            var count = 1
            val nameWithoutExt = fileName.substringBeforeLast(".")
            val ext = fileName.substringAfterLast(".", "")
            val dotExt = if (ext.isNotEmpty()) ".$ext" else ""

            while (targetFile.exists()) {
                targetFile = File(quickPavloDir, "${nameWithoutExt}_$count$dotExt")
                count++
            }

            outputStream = FileOutputStream(targetFile)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }

            if (javaFile != null && javaFile.exists()) {
                javaFile.delete()
            }

            return@withContext targetFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            try { outputStream?.close() } catch (_: Exception) {}
        }
    }
}
