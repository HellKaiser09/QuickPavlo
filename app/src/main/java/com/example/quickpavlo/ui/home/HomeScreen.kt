package com.example.quickpavlo.ui.home

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quickpavlo.ui.theme.AccentGreen
import com.example.quickpavlo.ui.theme.AccentOrange
import com.example.quickpavlo.ui.theme.DarkBackground
import com.example.quickpavlo.ui.theme.DarkGreenBorder
import com.example.quickpavlo.ui.theme.DarkGreenChip
import com.example.quickpavlo.ui.theme.DarkGreenSurface
import com.example.quickpavlo.ui.theme.DarkOrangeBorder
import com.example.quickpavlo.ui.theme.DarkOrangeChip
import com.example.quickpavlo.ui.theme.DarkOrangeSurface
import com.example.quickpavlo.ui.theme.QuickPavloTheme
import com.example.quickpavlo.ui.theme.TextGreenLight
import com.example.quickpavlo.ui.theme.TextMuted
import com.example.quickpavlo.ui.theme.TextPrimary
import com.example.quickpavlo.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    onHostClick: () -> Unit,
    onClientClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Header de la Aplicación
            HeaderSection(
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 2. Indicador de Estado y Descripción
            StatusSection()

            Spacer(modifier = Modifier.height(32.dp))

            // 3. Tarjetas de Acción
            // Tarjeta 1: Enviar (Verde Esmeralda)
            ActionCard(
                title = "Enviar",
                description = "Envía fotos, videos o carpetas completas",
                glowColor = AccentGreen,
                surfaceColor = DarkGreenSurface,
                borderColor = DarkGreenBorder,
                icon = {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkGreenChip)
                            .border(1.dp, DarkGreenBorder, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Enviar",
                            tint = AccentGreen,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                actionIcon = {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(DarkGreenChip)
                            .border(1.dp, DarkGreenBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                onClick = onClientClick
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Tarjeta 2: Recibir (Naranja / Ámbar)
            ActionCard(
                title = "Recibir",
                description = "Esperar código o escanear para aceptar",
                glowColor = AccentOrange,
                surfaceColor = DarkOrangeSurface,
                borderColor = DarkOrangeBorder,
                icon = {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkOrangeChip)
                            .border(1.dp, DarkOrangeBorder, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Recibir",
                            tint = AccentOrange,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                actionIcon = {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(DarkOrangeChip)
                            .border(1.dp, DarkOrangeBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = AccentOrange,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                onClick = onHostClick
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HeaderSection(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DarkGreenSurface)
                .border(1.dp, DarkGreenBorder, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Sensors,
                contentDescription = "App Logo",
                tint = AccentGreen,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "QuickPavlo",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "•",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGreen
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "P2P MESH DISCOVERY",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
                letterSpacing = 1.2.sp
            )
        }
    }
}

@Composable
private fun StatusSection() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(DarkGreenSurface)
                .border(1.dp, DarkGreenBorder, RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(AccentGreen)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Listo para conectar",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextGreenLight
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Selecciona una acción para transferir archivos de forma rápida y cifrada sin internet.",
            fontSize = 13.5.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun ActionCard(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    actionIcon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glowColor: Color = AccentGreen,
    surfaceColor: Color = DarkGreenSurface,
    borderColor: Color = DarkGreenBorder
) {
    // Animación continua de respiración en la intensidad del glow
    val infiniteTransition = rememberInfiniteTransition(label = "card_glow_anim")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.38f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .cardGlow(
                glowColor = glowColor,
                glowAlpha = glowAlpha,
                glowRadius = 24.dp,
                spread = 4.dp,
                cornerRadius = 24.dp
            )
            .clip(RoundedCornerShape(24.dp))
            .background(surfaceColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(onClick = onClick)
            .padding(22.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Ícono superior
            icon()

            Spacer(modifier = Modifier.height(20.dp))

            // Título, Descripción y Botón Acción
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = description,
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                actionIcon()
            }
        }
    }
}

/**
 * Modificador personalizado que dibuja un resplandor (Glow) desenfocado
 * 100% orgánico utilizando BlurMaskFilter en el Canvas de Android.
 */
private fun Modifier.cardGlow(
    glowColor: Color = AccentGreen,
    glowAlpha: Float = 0.30f,
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
fun HomeScreenPreview() {
    QuickPavloTheme {
        HomeScreen(
            onHostClick = {},
            onClientClick = {}
        )
    }
}
