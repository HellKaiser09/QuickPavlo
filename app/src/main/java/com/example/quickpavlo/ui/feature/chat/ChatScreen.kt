package com.example.quickpavlo.ui.feature.chat

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.quickpavlo.domain.model.ChatMessage
import com.example.quickpavlo.domain.network.ConnectionState
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val messages by viewModel.messages.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState(initial = ConnectionState.CONNECTED)
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.sendFile(it, context) }
    }

    // PROBLEMA 4: Toast de desconexión usando applicationContext antes de expulsar al usuario
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.IDLE || connectionState == ConnectionState.ERROR) {
            Toast.makeText(applicationContext, "Conexión finalizada", Toast.LENGTH_SHORT).show()
            onNavigateUp()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conexión Segura P2P") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    // PROBLEMA 3: Se removió la función y el botón de Borrar Chat
                    Button(onClick = {
                        viewModel.disconnect()
                    }) {
                        Text("Desconectar")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            // 1. LISTA DE MENSAJES
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                reverseLayout = false
            ) {
                items(messages) { message ->
                    if (message.isFile) {
                        FileMessageBubble(
                            message = message,
                            onResume = { viewModel.resumeFileTransfer(message.payloadId, context) }
                        )
                    } else {
                        MessageBubble(message)
                    }
                }
            }

            // 2. BARRA DE TEXTO E INPUT CON ADJUNTO
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { filePickerLauncher.launch("*/*") }
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Adjuntar archivo",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Escribe un mensaje...") },
                    shape = RoundedCornerShape(24.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    },
                    modifier = Modifier.background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(50)
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Enviar",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val alignment = if (message.isFromMe) Alignment.CenterEnd else Alignment.CenterStart
    val color = if (message.isFromMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (message.isFromMe) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = color,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.messageText,
                color = textColor,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
fun FileMessageBubble(
    message: ChatMessage,
    onResume: () -> Unit
) {
    val context = LocalContext.current
    val alignment = if (message.isFromMe) Alignment.CenterEnd else Alignment.CenterStart
    val containerColor = if (message.isFromMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (message.isFromMe) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    val ext = (message.fileName ?: "").substringAfterLast('.', "").lowercase()
    val isImage = ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = containerColor,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.InsertDriveFile,
                        contentDescription = "Archivo",
                        tint = textColor,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = message.fileName ?: message.messageText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatFileSize(message.fileSize),
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.8f)
                        )
                    }
                }

                // Vista previa de imagen si aplica
                if (isImage && !message.fileUri.isNullOrEmpty() && message.fileStatus == "COMPLETED") {
                    Spacer(modifier = Modifier.height(8.dp))
                    FileImagePreview(fileUriString = message.fileUri)
                }

                Spacer(modifier = Modifier.height(8.dp))

                when (message.fileStatus) {
                    "IN_PROGRESS" -> {
                        val progress = if (message.fileSize > 0) {
                            (message.bytesTransferred.toFloat() / message.fileSize.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (message.isFromMe) Color.White else MaterialTheme.colorScheme.primary,
                            trackColor = textColor.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Transferiendo... ${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.8f)
                        )
                    }
                    "COMPLETED" -> {
                        Text(
                            text = "✓ Guardado en Downloads/QuickPavlo",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (message.isFromMe) Color.White else MaterialTheme.colorScheme.primary
                        )
                        // PROBLEMA 2: Botón de Abrir Archivo para ambos (Emisor y Receptor)
                        if (!message.fileUri.isNullOrEmpty()) {
                            TextButton(
                                onClick = { openFile(context, message.fileUri) },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Abrir archivo", color = textColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    else -> {
                        // PAUSED or FAILED
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Interrumpido",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Button(
                                onClick = onResume,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Text("Reanudar", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FileImagePreview(
    fileUriString: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageBitmapState = remember(fileUriString) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(fileUriString) {
        try {
            val bitmap = if (fileUriString.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(fileUriString))?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } else {
                val filePath = if (fileUriString.startsWith("file://")) {
                    Uri.parse(fileUriString).path ?: fileUriString
                } else {
                    fileUriString
                }
                val file = File(filePath)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)
                } else null
            }
            imageBitmapState.value = bitmap?.asImageBitmap()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val bitmap = imageBitmapState.value
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Previsualización de imagen",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(
        Locale.getDefault(),
        "%.1f %s",
        bytes / Math.pow(1024.0, digitGroups.toDouble()),
        units[digitGroups.coerceAtMost(3)]
    )
}

private fun openFile(context: Context, fileUriString: String) {
    try {
        val uri: Uri = if (fileUriString.startsWith("content://")) {
            Uri.parse(fileUriString)
        } else {
            val file = if (fileUriString.startsWith("file://")) {
                File(Uri.parse(fileUriString).path ?: fileUriString)
            } else {
                File(fileUriString)
            }
            if (file.exists()) {
                FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            } else {
                Uri.parse(fileUriString)
            }
        }

        val extension = MimeTypeMap.getFileExtensionFromUrl(fileUriString).lowercase(Locale.getDefault())
            .ifEmpty {
                val lastDot = fileUriString.lastIndexOf('.')
                if (lastDot != -1) fileUriString.substring(lastDot + 1).lowercase(Locale.getDefault()) else ""
            }

        val mimeType = context.contentResolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "*/*"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }
        context.startActivity(Intent.createChooser(intent, "Abrir archivo con..."))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context.applicationContext, "No se pudo abrir el archivo", Toast.LENGTH_SHORT).show()
    }
}
