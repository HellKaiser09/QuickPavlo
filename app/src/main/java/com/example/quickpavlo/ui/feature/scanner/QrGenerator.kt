package com.example.quickpavlo.ui.feature.scanner

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set

@Composable
fun QrCodeImage(
    content: String,
    modifier: Modifier = Modifier,
    size: Int = 512
) {
    val bitmap = remember(content) {
        generateQrBitmap(content, size)
    }

    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "Código QR de Conexión",
            modifier = modifier.size(250.dp)
        )
    }
}

private fun generateQrBitmap(content: String, size: Int): Bitmap? {
    if (content.isEmpty()) return null
    return try {
        val bitMatrix = QRCodeWriter().encode (content, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bmp = createBitmap(width, height, Bitmap.Config.RGB_565)

        for (x in 0 until width) {
            for (y in 0 until height){
                bmp[x, y] = if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
        }
        bmp
    } catch (e: Exception){
        null
    }
}