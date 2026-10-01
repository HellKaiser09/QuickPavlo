package com.example.quickpavlo.ui.feature.scanner

import android.graphics.BlurMaskFilter
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.quickpavlo.ui.theme.AccentBlue
import com.example.quickpavlo.ui.theme.DarkBlueBorder
import com.example.quickpavlo.ui.theme.DarkBlueSurface
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
                    .background(DarkBlueSurface.copy(alpha = 0.90f))
                    .border(1.dp, DarkBlueBorder, RoundedCornerShape(50))
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
                            .background(AccentBlue)
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

        // 4. Overlay de Estado "Conectando" con la Esfera de Luz Respiratoria (Azul + Ámbar)
        if (isConnecting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    GlowingOrbLoader(size = 80.dp)

                    Spacer(modifier = Modifier.height(28.dp))

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

/**
 * Cargador de Esfera de Luz con Halo Difuminado en combinación de Azul Eléctrico y Naranja Ámbar.
 */
@Composable
fun GlowingOrbLoader(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_loader_anim")

    // Pulsación suave de respiración
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Rotación suave continua de los colores Azul + Ámbar
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    Box(
        modifier = modifier.size(size * pulseScale),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerPx = Offset(this.size.width / 2f, this.size.height / 2f)
            val radiusPx = (this.size.minDimension / 2f) * 0.68f

            drawIntoCanvas { canvas ->
                // 1. Halo difuminado exterior en Azul
                val blueGlowPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.parseColor("#3B82F6")
                    alpha = (0.75f * 255).toInt()
                    maskFilter = BlurMaskFilter(18.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                }

                canvas.nativeCanvas.drawCircle(
                    centerPx.x,
                    centerPx.y,
                    radiusPx * 1.20f,
                    blueGlowPaint
                )

                // 2. Halo difuminado exterior en Naranja Ámbar
                val amberGlowPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.parseColor("#F97316")
                    alpha = (0.65f * 255).toInt()
                    maskFilter = BlurMaskFilter(14.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                }

                canvas.nativeCanvas.drawCircle(
                    centerPx.x,
                    centerPx.y,
                    radiusPx * 1.05f,
                    amberGlowPaint
                )

                // 3. Esfera sólida con degradado de barrido rotatorio (Azul + Ámbar)
                val sweepGradient = android.graphics.SweepGradient(
                    centerPx.x,
                    centerPx.y,
                    intArrayOf(
                        android.graphics.Color.parseColor("#3B82F6"), // Azul Eléctrico
                        android.graphics.Color.parseColor("#60A5FA"), // Azul Claro
                        android.graphics.Color.parseColor("#F97316"), // Naranja Ámbar
                        android.graphics.Color.parseColor("#FB923C"), // Ámbar Claro
                        android.graphics.Color.parseColor("#3B82F6")  // Retorno a Azul
                    ),
                    null
                )

                val matrix = android.graphics.Matrix()
                matrix.postRotate(rotationAngle, centerPx.x, centerPx.y)
                sweepGradient.setLocalMatrix(matrix)

                val orbPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    shader = sweepGradient
                    style = android.graphics.Paint.Style.FILL
                }

                canvas.nativeCanvas.drawCircle(
                    centerPx.x,
                    centerPx.y,
                    radiusPx,
                    orbPaint
                )
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
    val primaryColor = AccentBlue
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

        // 2. Esquinas resaltadas en Azul Frío
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

        // 3. Línea láser azul animada
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
