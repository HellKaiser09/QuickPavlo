package com.example.quickpavlo.ui.feature.chat

import android.content.Context
import android.content.Intent
import android.graphics.BlurMaskFilter
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.quickpavlo.domain.model.ChatMessage
import com.example.quickpavlo.domain.network.ConnectionState
import com.example.quickpavlo.ui.theme.AccentBlue
import com.example.quickpavlo.ui.theme.AccentBlueLight
import com.example.quickpavlo.ui.theme.AccentOrange
import com.example.quickpavlo.ui.theme.DarkBackground
import com.example.quickpavlo.ui.theme.DarkBlueBorder
import com.example.quickpavlo.ui.theme.DarkBlueChip
import com.example.quickpavlo.ui.theme.DarkBlueSurface
import com.example.quickpavlo.ui.theme.QuickPavloTheme
import com.example.quickpavlo.ui.theme.TextBlueLight
import com.example.quickpavlo.ui.theme.TextMuted
import com.example.quickpavlo.ui.theme.TextPrimary
import com.example.quickpavlo.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Paleta Azul Frío + Ámbar
private val ChatOutgoingBubble = Color(0xFF0284C7)  // Azul Frío Vivo
private val ChatOutgoingBorder = Color(0xFF0369A1)
private val ChatIncomingBubble = Color(0xFF1E293B)  // Azul Pizarra Oscuro
private val ChatIncomingBorder = Color(0xFF334155)

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

    // Control de Notificación de Éxito a prueba de fallos:
    // Solo rastrea archivos que realmente estuvieron EN PROGRESO durante esta sesión activa.
    val activeSessionFileIds = remember { mutableSetOf<String>() }
    var showCompletionSuccess by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.sendFile(it, context) }
    }

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

    // Procesa los mensajes para activar el popup ÚNICAMENTE cuando una transferencia en curso finaliza
    LaunchedEffect(messages) {
        messages.forEach { message ->
            if (message.isFile) {
                if (message.fileStatus == "IN_PROGRESS" || message.fileStatus == "PAUSED") {
                    activeSessionFileIds.add(message.id)
                } else if (message.fileStatus == "COMPLETED" && activeSessionFileIds.contains(message.id)) {
                    activeSessionFileIds.remove(message.id)
                    showCompletionSuccess = true
                }
            }
        }
    }

    LaunchedEffect(showCompletionSuccess) {
        if (showCompletionSuccess) {
            delay(3500)
            showCompletionSuccess = false
        }
    }

    // Animación de Rotación para el Resplandor Multicolor Frío
    val infiniteTransition = rememberInfiniteTransition(label = "completion_glow_anim")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    // Animación de Intensidad de Iluminación suave del Chat al enviar un archivo
    val chatGlowAlpha by animateFloatAsState(
        targetValue = if (showCompletionSuccess) 0.65f else 0f,
        animationSpec = tween(
            durationMillis = if (showCompletionSuccess) 700 else 1200,
            easing = LinearOutSlowInEasing
        ),
        label = "chatGlowAlpha"
    )

    // Identificar la transferencia de archivo activa (si la hay)
    val activeTransfer = messages.firstOrNull {
        it.isFile && (it.fileStatus == "IN_PROGRESS" || it.fileStatus == "PAUSED")
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .imePadding()
            ) {
                // 1. BARRA SUPERIOR
                TopSessionBar(onNavigateUp = onNavigateUp)

                Spacer(modifier = Modifier.height(14.dp))

                // 2. TARJETA DE TRANSFERENCIA ACTIVA (Sin botones de pausar/cancelar por solicitud)
                if (activeTransfer != null) {
                    ActiveTransferCard(
                        message = activeTransfer
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // 3. TARJETA PRINCIPAL DEL CHAT P2P (Mismo Resplandor Multicolor al enviar archivo)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .movingCoolMulticolorGlow(
                            rotationAngle = rotationAngle,
                            glowAlpha = chatGlowAlpha,
                            glowRadius = 26.dp,
                            spread = 4.dp,
                            cornerRadius = 24.dp
                        )
                        .clip(RoundedCornerShape(24.dp))
                        .background(DarkBlueSurface)
                        .border(
                            1.dp,
                            if (chatGlowAlpha > 0f) Color(0xFF00E5FF).copy(alpha = chatGlowAlpha) else DarkBlueBorder,
                            RoundedCornerShape(24.dp)
                        )
                        .padding(14.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Encabezado del Canal P2P
                        ChatChannelHeader()

                        Spacer(modifier = Modifier.height(10.dp))

                        // Línea divisoria
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(DarkBlueBorder)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Lista de mensajes conversacionales
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(messages, key = { it.id }) { message ->
                                if (message.isFile && message.fileStatus == "COMPLETED") {
                                    FileMessageBubble(message = message)
                                } else if (!message.isFile) {
                                    MessageBubble(message = message)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Barra de entrada de texto e íconos adjuntos
                        ChatInputBar(
                            inputText = inputText,
                            onInputChange = { inputText = it },
                            onSendClick = {
                                if (inputText.isNotBlank()) {
                                    viewModel.sendMessage(inputText)
                                    inputText = ""
                                }
                            },
                            onAttachClick = { filePickerLauncher.launch("*/*") }
                        )
                    }
                }
            }

            // 4. CÁPSULA FLOTANTE DE NOTIFICACIÓN CON EL MISMO GLOW MULTICOLOR
            AnimatedVisibility(
                visible = showCompletionSuccess,
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = tween(400)
                ) + fadeOut(animationSpec = tween(300)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .systemBarsPadding()
                    .padding(top = 56.dp)
            ) {
                Box(
                    modifier = Modifier
                        .movingCoolMulticolorGlow(
                            rotationAngle = rotationAngle,
                            glowAlpha = 0.70f,
                            glowRadius = 18.dp,
                            spread = 2.dp,
                            cornerRadius = 50.dp
                        )
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF0C192C))
                        .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(50))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(18.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "El archivo se envió correctamente",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopSessionBar(
    onNavigateUp: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onNavigateUp,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Volver",
                tint = TextPrimary
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = "Sesión de Transferencia",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}

@Composable
private fun ActiveTransferCard(
    message: ChatMessage
) {
    val progress = if (message.fileSize > 0) {
        (message.bytesTransferred.toFloat() / message.fileSize.toFloat()).coerceIn(0f, 1f)
    } else 0.65f

    val percentInt = (progress * 100).toInt()
    val isPaused = message.fileStatus == "PAUSED"

    // Animación de Rotación con Colores Fríos girando continuamente alrededor de la tarjeta
    val infiniteTransition = rememberInfiniteTransition(label = "rotating_glow_anim")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .movingCoolMulticolorGlow(
                rotationAngle = rotationAngle,
                glowRadius = 26.dp,
                spread = 4.dp,
                cornerRadius = 24.dp
            )
            .clip(RoundedCornerShape(24.dp))
            .background(DarkBlueSurface)
            .border(
                1.dp,
                if (isPaused) AccentBlue else Color(0xFF00E5FF).copy(alpha = 0.55f),
                RoundedCornerShape(24.dp)
            )
            .padding(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Fila superior: Ícono, Nombre y Porcentaje
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ícono del archivo con insignia
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkBlueChip),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(AccentOrange),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = message.fileName ?: "video_campo.mp4",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = if (message.isFromMe) "Enviando a Usuario_..." else "Recibiendo de Usuario_...",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = AccentBlueLight
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "$percentInt%",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Barra de progreso con gradiente de colores fríos a ámbar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF131A28))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(50))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    AccentBlue,
                                    AccentBlueLight,
                                    AccentOrange
                                )
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Fila de velocidad, tiempo restante y bytes transferidos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "12.4 MB/s • 45s restantes",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Text(
                    text = "${formatFileSize(message.bytesTransferred)} / ${formatFileSize(message.fileSize)}",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun ChatChannelHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(AccentBlue)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "Canal Local Directo",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isMe = message.isFromMe
    val formattedTime = remember(message.timestamp) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMe) {
            // Badge del remitente
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(DarkBlueChip)
                    .border(1.dp, AccentBlue, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (message.senderName.isNotBlank()) message.senderName.take(3) else "U99",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentBlueLight
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isMe) ChatOutgoingBubble else ChatIncomingBubble
                    )
                    .border(
                        1.dp,
                        if (isMe) ChatOutgoingBorder else ChatIncomingBorder,
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = message.messageText,
                    fontSize = 14.5.sp,
                    color = TextPrimary,
                    lineHeight = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formattedTime,
                    fontSize = 11.sp,
                    color = TextMuted
                )

                if (isMe) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = "Enviado",
                        tint = AccentBlueLight,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FileMessageBubble(
    message: ChatMessage
) {
    val context = LocalContext.current
    val isMe = message.isFromMe

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (isMe) ChatOutgoingBubble else ChatIncomingBubble)
                .border(
                    1.dp,
                    if (isMe) ChatOutgoingBorder else ChatIncomingBorder,
                    RoundedCornerShape(20.dp)
                )
                .padding(14.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = if (isMe) Color.White else AccentBlue,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = message.fileName ?: message.messageText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatFileSize(message.fileSize),
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "✓ Guardado en Downloads/QuickPavlo",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextBlueLight
                )

                if (!message.fileUri.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = { openFile(context, message.fileUri) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Abrir archivo", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onAttachClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(DarkBlueChip)
            .border(1.dp, DarkBlueBorder, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Botón Adjuntar (Paperclip)
            IconButton(
                onClick = onAttachClick,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Adjuntar",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Campo de Texto de Entrada
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .border(1.dp, DarkBlueBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (inputText.isEmpty()) {
                    Text(
                        text = "Escribe un mensaje...",
                        fontSize = 13.5.sp,
                        color = TextMuted
                    )
                }

                BasicTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 13.5.sp
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(AccentBlue),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSendClick() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Botón Enviar Naranja Neón
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .cardGlow(
                        glowColor = AccentOrange,
                        glowAlpha = 0.50f,
                        glowRadius = 10.dp,
                        spread = 1.dp,
                        cornerRadius = 20.dp
                    )
                    .clip(CircleShape)
                    .background(AccentOrange)
                    .clickable(onClick = onSendClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Enviar",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Resplandor Multicolor Giratorio con Colores Fríos alrededor de la tarjeta al compartir un archivo
 */
private fun Modifier.movingCoolMulticolorGlow(
    rotationAngle: Float,
    glowAlpha: Float = 0.75f,
    glowRadius: Dp = 26.dp,
    spread: Dp = 4.dp,
    cornerRadius: Dp = 24.dp
) = this.drawBehind {
    val glowRadiusPx = glowRadius.toPx()
    val spreadPx = spread.toPx()
    val cornerRadiusPx = cornerRadius.toPx()

    if (glowAlpha > 0f) {
        drawIntoCanvas { canvas ->
            val sweepGradient = android.graphics.SweepGradient(
                size.width / 2f,
                size.height / 2f,
                intArrayOf(
                    android.graphics.Color.parseColor("#00E5FF"), // Cian Hielo
                    android.graphics.Color.parseColor("#3B82F6"), // Azul Eléctrico
                    android.graphics.Color.parseColor("#6366F1"), // Índigo
                    android.graphics.Color.parseColor("#A855F7"), // Violeta Neón
                    android.graphics.Color.parseColor("#38BDF8"), // Azul Cielo
                    android.graphics.Color.parseColor("#00E5FF")  // Retorno a Cian
                ),
                null
            )

            val matrix = android.graphics.Matrix()
            matrix.postRotate(rotationAngle, size.width / 2f, size.height / 2f)
            sweepGradient.setLocalMatrix(matrix)

            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                shader = sweepGradient
                alpha = (glowAlpha.coerceIn(0f, 1f) * 255).toInt()
                maskFilter = BlurMaskFilter(glowRadiusPx, BlurMaskFilter.Blur.NORMAL)
            }

            canvas.nativeCanvas.drawRoundRect(
                -spreadPx,
                -spreadPx,
                size.width + spreadPx,
                size.height + spreadPx,
                cornerRadiusPx,
                cornerRadiusPx,
                paint
            )
        }
    }
}

private fun Modifier.cardGlow(
    glowColor: Color = Color(0xFF3B82F6),
    glowAlpha: Float = 0.28f,
    glowRadius: Dp = 24.dp,
    spread: Dp = 4.dp,
    cornerRadius: Dp = 24.dp
) = this.drawBehind {
    val glowRadiusPx = glowRadius.toPx()
    val spreadPx = spread.toPx()
    val cornerRadiusPx = cornerRadius.toPx()

    if (glowRadiusPx > 0f) {
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = glowColor.toArgb()
                alpha = (glowAlpha.coerceIn(0f, 1f) * 255).toInt()
                maskFilter = BlurMaskFilter(glowRadiusPx, BlurMaskFilter.Blur.NORMAL)
            }

            canvas.nativeCanvas.drawRoundRect(
                -spreadPx,
                -spreadPx,
                size.width + spreadPx,
                size.height + spreadPx,
                cornerRadiusPx,
                cornerRadiusPx,
                paint
            )
        }
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

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    QuickPavloTheme {
        ChatScreen(onNavigateUp = {})
    }
}
