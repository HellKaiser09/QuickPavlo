package com.example.quickpavlo.ui.host

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.quickpavlo.domain.network.ConnectionState
import com.example.quickpavlo.ui.feature.scanner.QrCodeImage
import com.example.quickpavlo.ui.theme.AccentGreen
import com.example.quickpavlo.ui.theme.DarkBackground
import com.example.quickpavlo.ui.theme.DarkGreenBorder
import com.example.quickpavlo.ui.theme.DarkGreenSurface
import com.example.quickpavlo.ui.theme.QuickPavloTheme
import com.example.quickpavlo.ui.theme.TextGreenLight
import com.example.quickpavlo.ui.theme.TextPrimary
import com.example.quickpavlo.ui.theme.TextSecondary

@Composable
fun HostQrScreen(
    modifier: Modifier = Modifier,
    onConnected: () -> Unit = {},
    viewModel: HostViewModel = hiltViewModel()
) {
    val qrToken by viewModel.qrToken.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState(initial = ConnectionState.IDLE)

    LaunchedEffect(Unit) {
        viewModel.startHosting()
    }

    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            onConnected()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Badge de Estado de Conexión ("ESPERANDO CONEXIÓN")
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(DarkGreenSurface)
                    .border(1.dp, DarkGreenBorder, RoundedCornerShape(50))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(AccentGreen)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "ESPERANDO CONEXIÓN",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Título Principal
            Text(
                text = "Preparado para enviar",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Subtítulo e Instrucciones
            Text(
                text = "Pide al receptor que escanee este código o ingrese las credenciales seguras.",
                fontSize = 13.5.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 4. Tarjeta del Código QR con visor, esquinas verdes y centro destacado (Sin láser)
            val token = qrToken
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .aspectRatio(1f)
                    .cardGlow(
                        glowColor = AccentGreen,
                        glowAlpha = 0.28f,
                        glowRadius = 26.dp,
                        spread = 4.dp,
                        cornerRadius = 28.dp
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .background(DarkGreenSurface)
                    .border(1.dp, DarkGreenBorder, RoundedCornerShape(28.dp))
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF09120E))
                        .border(1.dp, Color(0xFF1B352A), RoundedCornerShape(20.dp))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (token != null) {
                        Box(contentAlignment = Alignment.Center) {
                            QrCodeImage(
                                content = token,
                                modifier = Modifier.fillMaxSize()
                            )


                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = AccentGreen,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Generando código seguro...",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    // Marco de enfoque con esquinas verdes
                    TargetFrameOverlay(modifier = Modifier.fillMaxSize())
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 5. Estado de la Red P2P
            val stateText = when (connectionState) {
                ConnectionState.IDLE -> "Inicializando radar..."
                ConnectionState.ADVERTISING -> "Esperando escáner cercano..."
                ConnectionState.DISCOVERING -> "Buscando dispositivos..."
                ConnectionState.CONNECTING -> "Estableciendo conexión P2P..."
                ConnectionState.CONNECTED -> "¡Dispositivo vinculado!"
                ConnectionState.ERROR -> "Error de conexión"
            }

            Text(
                text = stateText,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = when (connectionState) {
                    ConnectionState.CONNECTED -> AccentGreen
                    ConnectionState.ERROR -> Color(0xFFEF4444)
                    else -> TextGreenLight
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TargetFrameOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cornerLen = 22.dp.toPx()
        val strokeW = 3.5.dp.toPx()
        val color = Color(0xFF22C55E)

        // Esquina Superior Izquierda
        drawPath(
            path = Path().apply {
                moveTo(0f, cornerLen)
                lineTo(0f, 0f)
                lineTo(cornerLen, 0f)
            },
            color = color,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

        // Esquina Superior Derecha
        drawPath(
            path = Path().apply {
                moveTo(w - cornerLen, 0f)
                lineTo(w, 0f)
                lineTo(w, cornerLen)
            },
            color = color,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

        // Esquina Inferior Izquierda
        drawPath(
            path = Path().apply {
                moveTo(0f, h - cornerLen)
                lineTo(0f, h)
                lineTo(cornerLen, h)
            },
            color = color,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

        // Esquina Inferior Derecha
        drawPath(
            path = Path().apply {
                moveTo(w - cornerLen, h)
                lineTo(w, h)
                lineTo(w, h - cornerLen)
            },
            color = color,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )
    }
}

private fun Modifier.cardGlow(
    glowColor: Color = Color(0xFF22C55E),
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

@Preview(showBackground = true)
@Composable
fun HostQrScreenPreview() {
    QuickPavloTheme {
        HostQrScreen()
    }
}
