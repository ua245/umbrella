package com.umbrella.parent.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object QrCodeGenerator {

    /**
     * Generates a high-quality Bitmap representing the given content string.
     */
    suspend fun generateQrBitmap(
        content: String,
        sizePx: Int = 512,
        foregroundColor: Color = Color.Black,
        backgroundColor: Color = Color.White
    ): Bitmap? = withContext(Dispatchers.Default) {
        if (content.isEmpty()) return@withContext null
        try {
            val hints = hashMapOf<EncodeHintType, Any>(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                EncodeHintType.MARGIN to 1
            )
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)

            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            val fgArgb = foregroundColor.toArgb()
            val bgArgb = backgroundColor.toArgb()

            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) fgArgb else bgArgb
                }
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

/**
 * Jetpack Compose UI component for rendering the pairing QR code.
 */
@Composable
fun QrCodeView(
    data: String,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    foregroundColor: Color = Color(0xFF0F172A),
    backgroundColor: Color = Color.White
) {
    val bitmapState = produceState<Bitmap?>(initialValue = null, key1 = data) {
        value = QrCodeGenerator.generateQrBitmap(
            content = data,
            sizePx = 512,
            foregroundColor = foregroundColor,
            backgroundColor = backgroundColor
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = bitmapState.value
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Device Pairing QR Code",
                modifier = Modifier.size(size)
            )
        } else {
            CircularProgressIndicator(
                color = Color(0xFF6366F1),
                strokeWidth = 3.dp
            )
        }
    }
}

