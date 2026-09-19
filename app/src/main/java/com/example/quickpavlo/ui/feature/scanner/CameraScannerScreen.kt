package com.example.quickpavlo.ui.feature.scanner

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.quickpavlo.ui.theme.AccentGreen
import com.example.quickpavlo.ui.theme.DarkGreenBorder
import com.example.quickpavlo.ui.theme.DarkGreenSurface
import com.example.quickpavlo.ui.theme.TextPrimary
import java.util.concurrent.Executors

@Composable
fun CameraScannerScreen(
    onQrScanned: (String) -> Unit,
    isConnecting: Boolean = false,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Vista previa de la cámara (CameraX)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor, QrCodeAnalyzer { qrValue ->
                                if (!isConnecting) {
                                    onQrScanned(qrValue)
                                }
                            })
                        }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()

                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            onRelease = {
                cameraExecutor.shutdown()
            }
        )

        // 2. Capa visual de máscara y objetivo con animación láser
        QrScannerOverlay(modifier = Modifier.fillMaxSize())

        // 3. Indicaciones superiores para guía del usuario (UI/UX)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .systemBarsPadding()
                .padding(top = 16.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Badge de Estado
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(DarkGreenSurface.copy(alpha = 0.90f))
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
                        text = "ESCANEAR CÓDIGO P2P",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Escanear QR de Conexión",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Alinea el código dentro del recuadro para vincularte.",
                fontSize = 13.5.sp,
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.85f)
            )
        }

        // 4. Overlay de Estado "Conectando"
        if (isConnecting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    CircularProgressIndicator(
                        color = AccentGreen,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(52.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Estableciendo conexión segura P2P...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun QrScannerOverlay(
    modifier: Modifier = Modifier,
    boxSize: Dp = 270.dp
) {
    val boxSizePx = with(LocalDensity.current) { boxSize.toPx() }
    val primaryColor = AccentGreen
    val cornerLength = with(LocalDensity.current) { 32.dp.toPx() }
    val strokeWidth = with(LocalDensity.current) { 4.5.dp.toPx() }

    // Animación de la línea láser de escaneo
    val infiniteTransition = rememberInfiniteTransition(label = "scanner_laser_transition")
    val laserYRatio by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y_ratio"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val left = (width - boxSizePx) / 2f
        val top = (height - boxSizePx) / 2f
        val right = left + boxSizePx
        val bottom = top + boxSizePx

        // 1. Capa oscura exterior con recorte transparente central
        val overlayPath = Path().apply {
            addRect(Rect(0f, 0f, width, height))
            addRoundRect(
                RoundRect(
                    rect = Rect(left, top, right, bottom),
                    cornerRadius = CornerRadius(24f, 24f)
                )
            )
            fillType = PathFillType.EvenOdd
        }
        drawPath(
            path = overlayPath,
            color = Color.Black.copy(alpha = 0.70f)
        )

        // 2. Esquinas resaltadas en verde esmeralda
        // Esquina Superior Izquierda
        drawPath(
            path = Path().apply {
                moveTo(left, top + cornerLength)
                lineTo(left, top)
                lineTo(left + cornerLength, top)
            },
            color = primaryColor,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Esquina Superior Derecha
        drawPath(
            path = Path().apply {
                moveTo(right - cornerLength, top)
                lineTo(right, top)
                lineTo(right, top + cornerLength)
            },
            color = primaryColor,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Esquina Inferior Izquierda
        drawPath(
            path = Path().apply {
                moveTo(left, bottom - cornerLength)
                lineTo(left, bottom)
                lineTo(left + cornerLength, bottom)
            },
            color = primaryColor,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Esquina Inferior Derecha
        drawPath(
            path = Path().apply {
                moveTo(right - cornerLength, bottom)
                lineTo(right, bottom)
                lineTo(right, bottom - cornerLength)
            },
            color = primaryColor,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // 3. Línea láser verde animada
        val laserY = top + (boxSizePx * laserYRatio)
        drawLine(
            color = primaryColor,
            start = Offset(left + 12f, laserY),
            end = Offset(right - 12f, laserY),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}
