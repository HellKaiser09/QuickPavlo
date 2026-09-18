package com.example.quickpavlo.data.network

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.system.Os
import android.system.OsConstants
import android.util.Log
import com.example.quickpavlo.data.local.UserIdentityManager
import com.example.quickpavlo.domain.network.ConnectionState
import com.example.quickpavlo.domain.network.P2PConnectionManager
import com.example.quickpavlo.domain.network.PayloadEvent
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
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
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

class NearbyP2PConnectionManagerImpl(
    private val context: Context,
    private val userIdentityManager: UserIdentityManager
) : P2PConnectionManager {

    private val TAG = "QuickPavloP2P"
    private val p2pScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val strategy = Strategy.P2P_POINT_TO_POINT
    private val SERVICE_ID = "com.example.quickpavlo.P2P_SERVICE"

    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    override val connectionState = _connectionState.asStateFlow()

    private val _activeSessionId = MutableStateFlow("")
    override val activeSessionId = _activeSessionId.asStateFlow()

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
    private var pendingRemoteDeviceId: String? = null
    private var isHost = false
    private var currentAuthToken: String? = null
    private var currentUserName: String? = null

    // Tracking for file descriptors, streams, offsets, payload mapping and saved URIs
    private val activeSendingPfds = ConcurrentHashMap<Long, ParcelFileDescriptor>()
    private val incomingFilesMap = ConcurrentHashMap<Long, Payload.File>()
    private val incomingStreamsMap = ConcurrentHashMap<Long, InputStream>()
    private val fileNamesMap = ConcurrentHashMap<Long, String>()
    private val fileSizesMap = ConcurrentHashMap<Long, Long>()
    private val payloadMapping = ConcurrentHashMap<Long, Long>() // newPayloadId -> originalPayloadId
    private val payloadOffsets = ConcurrentHashMap<Long, Long>() // rawPayloadId -> offset
    private val existingFileUrisMap = ConcurrentHashMap<Long, String>() // effectivePayloadId -> savedUri

    // 1. EL PAQUETE (Recepción de datos)
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val text = payload.asBytes()?.let { String(it) } ?: ""
                    Log.d(TAG, "[BYTES RECIBIDOS] Payload ID: ${payload.id}, Text: $text")

                    if (text.startsWith("CONTROL_RESUME|")) {
                        val parts = text.split("|")
                        if (parts.size >= 3) {
                            val payloadId = parts[1].toLongOrNull() ?: 0L
                            val offset = parts[2].toLongOrNull() ?: 0L
                            Log.i(TAG, "[EMISOR] Interceptado CONTROL_RESUME: oldPayloadId=$payloadId, bytesTransferred=$offset")
                            _payloadEvents.tryEmit(PayloadEvent.ControlResume(payloadId, offset))
                        }
                    } else if (text.startsWith("META_RESUME|")) {
                        val parts = text.split("|")
                        if (parts.size >= 3) {
                            val newPayloadId = parts[1].toLongOrNull() ?: 0L
                            val oldPayloadId = parts[2].toLongOrNull() ?: 0L
                            val offset = parts.getOrNull(3)?.toLongOrNull() ?: 0L

                            if (oldPayloadId > 0) {
                                payloadMapping[newPayloadId] = oldPayloadId
                            }
                            if (offset > 0) {
                                payloadOffsets[newPayloadId] = offset
                            }

                            Log.i(TAG, "[RECEPTOR] Interceptado META_RESUME: newPayloadId=$newPayloadId, oldPayloadId=$oldPayloadId, offset=$offset")
                            Log.d(TAG, "[RECEPTOR] Mapeo registrado: payloadMapping[$newPayloadId] -> $oldPayloadId, payloadOffsets[$newPayloadId] -> $offset")

                            _payloadEvents.tryEmit(PayloadEvent.MetaResume(newPayloadId, oldPayloadId, offset))
                        }
                    } else if (text.startsWith("FILE_META|")) {
                        val parts = text.split("|")
                        if (parts.size >= 4) {
                            val payloadId = parts[1].toLongOrNull() ?: 0L
                            val fileName = parts[2]
                            val fileSize = parts[3].toLongOrNull() ?: 0L
                            val offset = parts.getOrNull(4)?.toLongOrNull() ?: 0L
                            val originalPayloadId = parts.getOrNull(5)?.toLongOrNull() ?: 0L

                            val effectivePayloadId = if (originalPayloadId > 0) originalPayloadId else payloadId
                            fileNamesMap[payloadId] = fileName
                            fileSizesMap[payloadId] = fileSize

                            if (originalPayloadId > 0) {
                                payloadMapping[payloadId] = originalPayloadId
                            }
                            if (offset > 0) {
                                payloadOffsets[payloadId] = offset
                            }

                            Log.i(TAG, "[RECEPTOR] Interceptado FILE_META: payloadId=$payloadId, fileName=$fileName, fileSize=$fileSize, offset=$offset, originalId=$originalPayloadId")

                            _payloadEvents.tryEmit(
                                PayloadEvent.FileMeta(
                                    payloadId = effectivePayloadId,
                                    fileName = fileName,
                                    fileSize = fileSize,
                                    offset = offset,
                                    originalPayloadId = originalPayloadId
                                )
                            )
                        }
                    } else {
                        _incomingPayloads.tryEmit(text)
                        _payloadEvents.tryEmit(PayloadEvent.Text(text))
                    }
                }
                Payload.Type.FILE -> {
                    Log.i(TAG, "[FILE PAYLOAD INICIO] Payload ID: ${payload.id}")
                    payload.asFile()?.let { payloadFile ->
                        incomingFilesMap[payload.id] = payloadFile
                    }
                }
                Payload.Type.STREAM -> {
                    Log.i(TAG, "[STREAM PAYLOAD INICIO] Payload ID: ${payload.id}")
                    payload.asStream()?.asInputStream()?.let { inputStream ->
                        incomingStreamsMap[payload.id] = inputStream
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val rawPayloadId = update.payloadId
            val effectivePayloadId = payloadMapping[rawPayloadId] ?: rawPayloadId
            val offset = payloadOffsets[rawPayloadId] ?: 0L

            val knownFileSize = fileSizesMap[rawPayloadId] ?: fileSizesMap[effectivePayloadId] ?: 0L
            val totalBytes = if (update.totalBytes > 0) update.totalBytes else knownFileSize

            val totalBytesTransferred = offset + update.bytesTransferred

            val statusStr = when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS -> "COMPLETED"
                PayloadTransferUpdate.Status.IN_PROGRESS -> "IN_PROGRESS"
                PayloadTransferUpdate.Status.CANCELED -> "PAUSED"
                else -> "FAILED"
            }

            Log.d(TAG, "[UPDATE] rawId=$rawPayloadId, effectiveId=$effectivePayloadId, status=$statusStr (${update.status}), transferred=${update.bytesTransferred}, totalTransferred=$totalBytesTransferred, totalBytes=$totalBytes")

            _payloadEvents.tryEmit(
                PayloadEvent.FileProgress(
                    payloadId = effectivePayloadId,
                    bytesTransferred = totalBytesTransferred,
                    totalBytes = totalBytes,
                    status = statusStr
                )
            )

            // 1. GUARDADO PARCIAL SI LA TRANSFERENCIA SE INTERRUMPE (FAILED / CANCELED)
            if (update.status == PayloadTransferUpdate.Status.FAILURE || update.status == PayloadTransferUpdate.Status.CANCELED) {
                Log.e(TAG, "[ERROR NEARBY] Transferencia fallida o cancelada: rawId=$rawPayloadId, effectiveId=$effectivePayloadId, statusCode=${update.status}")

                p2pScope.launch {
                    try {
                        activeSendingPfds.remove(rawPayloadId)?.close()
                    } catch (_: Exception) {}

                    val payloadFile = incomingFilesMap.remove(rawPayloadId)
                    val streamInput = incomingStreamsMap.remove(rawPayloadId)

                    if (payloadFile != null || streamInput != null) {
                        val fileName = fileNamesMap[rawPayloadId] ?: "received_file_${effectivePayloadId}"
                        val existingUri = existingFileUrisMap[effectivePayloadId]

                        Log.w(TAG, "[GUARDADO PARCIAL PARALELO] Guardando bytes parciales acumulados ($totalBytesTransferred bytes) para $fileName...")
                        val savedPath = savePayloadStreamToDownloads(
                            context = context,
                            payloadFile = payloadFile,
                            streamInput = streamInput,
                            fileName = fileName,
                            offset = offset,
                            existingUriString = existingUri
                        )

                        if (savedPath != null) {
                            existingFileUrisMap[effectivePayloadId] = savedPath
                            Log.i(TAG, "[EXITO GUARDADO PARCIAL] Archivo parcial respaldado en disco: $savedPath")
                            _payloadEvents.emit(PayloadEvent.FileReceived(effectivePayloadId, savedPath))
                        }
                    }

                    payloadMapping.remove(rawPayloadId)
                    payloadOffsets.remove(rawPayloadId)
                }
            }

            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                p2pScope.launch {
                    try {
                        activeSendingPfds.remove(rawPayloadId)?.close()
                        Log.d(TAG, "[CLEANUP] Descriptor de archivo liberado para rawId=$rawPayloadId")
                    } catch (_: Exception) {}

                    val payloadFile = incomingFilesMap.remove(rawPayloadId)
                    val streamInput = incomingStreamsMap.remove(rawPayloadId)

                    // RETORNO TEMPRANO SI ES UN PAYLOAD DE TIPO BYTES (Evita falsos errores de archivo)
                    if (payloadFile == null && streamInput == null) {
                        Log.d(TAG, "[SUCCESS BYTES] Payload ID: $rawPayloadId es de tipo BYTES. Omitiendo guardado en disco.")
                        payloadMapping.remove(rawPayloadId)
                        payloadOffsets.remove(rawPayloadId)
                        fileSizesMap.remove(rawPayloadId)
                        return@launch
                    }

                    Log.i(TAG, "[SUCCESS FILE] Transferencia completada para rawId=$rawPayloadId (effectiveId=$effectivePayloadId). Guardando/Concatenando en Downloads...")
                    val fileName = fileNamesMap.remove(rawPayloadId) ?: "received_file_${effectivePayloadId}"
                    val existingUri = existingFileUrisMap[effectivePayloadId]

                    // 2. GUARDADO / CONCATENACIÓN (WRITE-APPEND) EN SUCCESS
                    val savedPath = savePayloadStreamToDownloads(
                        context = context,
                        payloadFile = payloadFile,
                        streamInput = streamInput,
                        fileName = fileName,
                        offset = offset,
                        existingUriString = existingUri
                    )

                    if (savedPath != null) {
                        existingFileUrisMap[effectivePayloadId] = savedPath
                        Log.i(TAG, "[EXITO GUARDADO COMPLETADO] Archivo final guardado/concatenado correctamente en: $savedPath")
                        _payloadEvents.emit(PayloadEvent.FileReceived(effectivePayloadId, savedPath))
                    } else {
                        Log.e(TAG, "[ERROR GUARDADO] No se pudo guardar/concatenar el archivo en Downloads/QuickPavlo")
                    }

                    payloadMapping.remove(rawPayloadId)
                    payloadOffsets.remove(rawPayloadId)
                    fileSizesMap.remove(rawPayloadId)
                }
            }
        }
    }

    // 2. EL ENLACE Y LA VALIDACIÓN CRIPTOGRÁFICA
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            _connectionState.value = ConnectionState.CONNECTING

            val nameParts = info.endpointName.split("|")
            val remoteDeviceId = if (nameParts.size >= 2) nameParts[1] else endpointId
            pendingRemoteDeviceId = remoteDeviceId
            Log.i(TAG, "[CONEXION INICIADA] Endpoint: $endpointId, RemoteDeviceId: $remoteDeviceId, EndpointName: ${info.endpointName}")

            if (isHost) {
                val clientToken = if (nameParts.size >= 3) nameParts[2] else if (nameParts.size == 2) nameParts[1] else ""

                if (clientToken == currentAuthToken) {
                    Log.i(TAG, "[HOST] Token valido. Aceptando conexion con $endpointId")
                    connectionsClient.acceptConnection(endpointId, payloadCallback)
                } else {
                    Log.e(TAG, "[HOST] Token invalido. Rechazando conexion. Esperado: $currentAuthToken, Recibido: $clientToken")
                    connectionsClient.rejectConnection(endpointId)
                    _connectionState.value = ConnectionState.ERROR
                }
            } else {
                Log.i(TAG, "[CLIENTE] Aceptando conexion con $endpointId")
                connectionsClient.acceptConnection(endpointId, payloadCallback)
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectedEndpointId = endpointId
                connectionsClient.stopAdvertising()
                connectionsClient.stopDiscovery()

                val myDeviceId = userIdentityManager.getDeviceId()
                val remoteDeviceId = pendingRemoteDeviceId?.ifBlank { null } ?: endpointId
                val persistentSessionId = listOf(myDeviceId, remoteDeviceId).sorted().joinToString("_")

                Log.i(TAG, "[CONECTADO EXITOSAMENTE] PeerId: $endpointId, SessionId: $persistentSessionId")
                _activeSessionId.value = persistentSessionId
                _connectionState.value = ConnectionState.CONNECTED
            } else {
                Log.e(TAG, "[ERROR CONEXION] Fallo el resultado de conexion para $endpointId: status=${result.status}")
                _connectionState.value = ConnectionState.ERROR
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.w(TAG, "[DESCONECTADO] Desconectado del endpoint: $endpointId")
            connectedEndpointId = null
            pendingRemoteDeviceId = null
            _activeSessionId.value = ""
            _connectionState.value = ConnectionState.IDLE
        }
    }

    // 3. EL RADAR (Buscando al anfitrión)
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val hostParts = info.endpointName.split("|")
            if (hostParts.size >= 2) {
                pendingRemoteDeviceId = hostParts[1]
            }

            val myDeviceId = userIdentityManager.getDeviceId()
            val securityPayload = "${currentUserName ?: "Cliente"}|$myDeviceId|$currentAuthToken"

            Log.i(TAG, "[RADAR] Encontrado Anfitrion: $endpointId, Name: ${info.endpointName}. Solicitando conexion...")
            connectionsClient.requestConnection(securityPayload, endpointId, connectionLifecycleCallback)
                .addOnFailureListener { e ->
                    Log.e(TAG, "[RADAR ERROR] Error solicitando conexion a $endpointId: ${e.message}", e)
                    _connectionState.value = ConnectionState.ERROR
                }
        }
        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "[RADAR] Perdido endpoint: $endpointId")
        }
    }

    // --- MÉTODOS PÚBLICOS ---

    override fun startHosting(userName: String, authToken: String) {
        isHost = true
        currentUserName = userName
        currentAuthToken = authToken

        val myDeviceId = userIdentityManager.getDeviceId()
        val advertisingName = "$userName|$myDeviceId"

        Log.i(TAG, "Iniciando modo Anfitrion (Advertising). Name: $advertisingName, Token: $authToken")
        val options = AdvertisingOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startAdvertising(advertisingName, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnSuccessListener {
                Log.i(TAG, "Advertising iniciado con exito.")
                _connectionState.value = ConnectionState.ADVERTISING
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error iniciando Advertising: ${e.message}", e)
                _connectionState.value = ConnectionState.ERROR
            }
    }

    override fun startDiscovering(userName: String, authToken: String) {
        isHost = false
        currentUserName = userName
        currentAuthToken = authToken

        Log.i(TAG, "Iniciando modo Cliente (Discovery). User: $userName, Token: $authToken")
        val options = DiscoveryOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
            .addOnSuccessListener {
                Log.i(TAG, "Discovery iniciado con exito.")
                _connectionState.value = ConnectionState.DISCOVERING
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error iniciando Discovery: ${e.message}", e)
                _connectionState.value = ConnectionState.ERROR
            }
    }

    override fun sendPayload(data: String) {
        connectedEndpointId?.let { id ->
            Log.d(TAG, "Enviando BYTES payload a $id: $data")
            val payload = Payload.fromBytes(data.toByteArray())
            connectionsClient.sendPayload(id, payload)
        } ?: run {
            Log.e(TAG, "No se puede enviar payload TEXT: connectedEndpointId es NULL")
        }
    }

    override fun sendFilePayload(uri: Uri, fileName: String, fileSize: Long): Long? {
        val id = connectedEndpointId ?: run {
            Log.e(TAG, "Error enviando archivo: connectedEndpointId es NULL")
            return null
        }
        return try {
            Log.i(TAG, "Preparando envio de archivo: uri=$uri, fileName=$fileName, size=$fileSize")
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: run {
                Log.e(TAG, "openFileDescriptor devolvio NULL para $uri")
                return null
            }
            val filePayload = Payload.fromFile(pfd)
            val payloadId = filePayload.id
            activeSendingPfds[payloadId] = pfd

            val metaString = "FILE_META|$payloadId|$fileName|$fileSize|0|0"
            Log.i(TAG, "Enviando FILE_META header: $metaString")
            connectionsClient.sendPayload(id, Payload.fromBytes(metaString.toByteArray()))

            Log.i(TAG, "Enviando File Payload ID: $payloadId")
            connectionsClient.sendPayload(id, filePayload)
            payloadId
        } catch (e: Exception) {
            Log.e(TAG, "ERROR enviando archivo: ${e.message}", e)
            null
        }
    }

    override fun sendFilePayloadWithOffset(
        uri: Uri,
        fileName: String,
        fileSize: Long,
        offset: Long,
        originalPayloadId: Long
    ): Long? {
        val id = connectedEndpointId ?: run {
            Log.e(TAG, "[EMISOR] Error reanudando archivo: connectedEndpointId es NULL")
            return null
        }
        Log.d(TAG, "[EMISOR] Intentando abrir PFD para reanudar con Os.lseek: uri=$uri, offset=$offset, originalPayloadId=$originalPayloadId")
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: run {
                Log.e(TAG, "[EMISOR] openFileDescriptor devolvio NULL para $uri")
                return null
            }

            if (offset > 0) {
                Os.lseek(pfd.fileDescriptor, offset, OsConstants.SEEK_SET)
                Log.d(TAG, "[EMISOR] Os.lseek aplicado exitosamente al PFD. Offset: $offset")
            }

            val filePayload = Payload.fromFile(pfd)
            val newPayloadId = filePayload.id
            activeSendingPfds[newPayloadId] = pfd

            // 1. ENVIAR PRIMERO META_RESUME|<newPayloadId>|<originalPayloadId>|<offset>
            val metaResumeString = "META_RESUME|$newPayloadId|$originalPayloadId|$offset"
            Log.i(TAG, "[EMISOR] Enviando META_RESUME: $metaResumeString")
            connectionsClient.sendPayload(id, Payload.fromBytes(metaResumeString.toByteArray()))

            // 2. ENVIAR EL FILE PAYLOAD REANUDADO
            Log.i(TAG, "[EMISOR] Enviando File Payload reanudado: newPayloadId=$newPayloadId")
            connectionsClient.sendPayload(id, filePayload)
            newPayloadId
        } catch (e: Exception) {
            Log.e(TAG, "[EMISOR] ERROR CRÍTICO en Os.lseek o envio de archivo reanudado: ${e.message}", e)
            null
        }
    }

    override fun disconnect() {
        Log.w(TAG, "Desconectando y cerrando todas las conexiones...")
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedEndpointId = null
        pendingRemoteDeviceId = null
        activeSendingPfds.values.forEach { try { it.close() } catch (_: Exception) {} }
        activeSendingPfds.clear()
        incomingFilesMap.clear()
        incomingStreamsMap.clear()
        fileNamesMap.clear()
        fileSizesMap.clear()
        payloadMapping.clear()
        payloadOffsets.clear()
        existingFileUrisMap.clear()
        _activeSessionId.value = ""
        _connectionState.value = ConnectionState.IDLE
    }

    private suspend fun savePayloadStreamToDownloads(
        context: Context,
        payloadFile: Payload.File?,
        streamInput: InputStream?,
        fileName: String,
        offset: Long,
        existingUriString: String? = null
    ): String? = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = streamInput
        var outputStream: OutputStream? = null
        try {
            val resolver = context.contentResolver

            if (inputStream == null && payloadFile != null) {
                val tempUri: Uri? = payloadFile.asUri()
                val javaFile: File? = payloadFile.asJavaFile()
                val pfd: ParcelFileDescriptor? = payloadFile.asParcelFileDescriptor()

                inputStream = when {
                    tempUri != null -> resolver.openInputStream(tempUri)
                    javaFile != null && javaFile.exists() -> FileInputStream(javaFile)
                    pfd != null -> FileInputStream(pfd.fileDescriptor)
                    else -> null
                }
            }

            if (inputStream == null) {
                Log.e(TAG, "[GUARDAR ARCHIVO] inputStream es NULL")
                return@withContext null
            }

            val subFolder = "QuickPavlo"

            // Android 10+ (MediaStore.Downloads/QuickPavlo)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val targetUri: Uri? = if (offset > 0 && !existingUriString.isNullOrEmpty() && existingUriString.startsWith("content://")) {
                    Uri.parse(existingUriString)
                } else {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$subFolder")
                    }
                    resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                }

                if (targetUri != null) {
                    // Modo "wa" (Write-Append) cuando offset > 0 para concatenar bytes al archivo existente
                    outputStream = resolver.openOutputStream(targetUri, if (offset > 0) "wa" else "w")
                    if (outputStream != null) {
                        inputStream.use { input ->
                            outputStream.use { output ->
                                input.copyTo(output)
                                output.flush()
                            }
                        }
                        payloadFile?.asJavaFile()?.let { file ->
                            if (file.exists()) file.delete()
                        }
                        return@withContext targetUri.toString()
                    }
                }
            }

            // Fallback API < 29
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val quickPavloDir = File(downloadsDir, subFolder)
            if (!quickPavloDir.exists()) {
                quickPavloDir.mkdirs()
            }

            var targetFile = if (!existingUriString.isNullOrEmpty() && !existingUriString.startsWith("content://")) {
                File(existingUriString)
            } else {
                File(quickPavloDir, fileName)
            }

            if (offset == 0L && !targetFile.exists()) {
                var count = 1
                val nameWithoutExt = fileName.substringBeforeLast(".")
                val ext = fileName.substringAfterLast(".", "")
                val dotExt = if (ext.isNotEmpty()) ".$ext" else ""

                while (targetFile.exists()) {
                    targetFile = File(quickPavloDir, "${nameWithoutExt}_$count$dotExt")
                    count++
                }
            }

            if (offset > 0 && targetFile.exists()) {
                val raf = RandomAccessFile(targetFile, "rw")
                raf.seek(offset)
                val buffer = ByteArray(8192)
                var bytesRead: Int
                inputStream.use { input ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        raf.write(buffer, 0, bytesRead)
                    }
                }
                raf.close()
            } else {
                outputStream = FileOutputStream(targetFile, offset > 0)
                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }
            }

            payloadFile?.asJavaFile()?.let { file ->
                if (file.exists()) file.delete()
            }

            return@withContext targetFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "[GUARDAR ARCHIVO EXCEPTION] Error guardando/concatenando archivo $fileName: ${e.message}", e)
            null
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            try { outputStream?.close() } catch (_: Exception) {}
        }
    }
}
