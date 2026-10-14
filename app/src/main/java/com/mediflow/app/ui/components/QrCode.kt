package com.mediflow.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * The counter code, generated for real so a shop-floor scanner can read it.
 *
 * [value] is the reservation reference — the same string printed under the code, so the
 * pharmacist can type it when no scanner is to hand. A code that will not scan is worse
 * than no code, so a failed encode says so instead of drawing a decorative pattern.
 */
@Composable
fun QrCode(value: String, modifier: Modifier = Modifier) {
    val bitmap = remember(value) { encodeQr(value) }
    Surface(
        modifier = modifier.semantics { contentDescription = "QR code for $value" },
        shape = RoundedCornerShape(14.dp),
        color = Color.White
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap == null) {
                Text(
                    text = "No code to show",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B)
                )
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private const val SIDE_PX = 512
private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

private fun encodeQr(value: String): Bitmap? {
    if (value.isBlank()) return null
    return runCatching {
        val matrix = QRCodeWriter().encode(
            value.trim(),
            BarcodeFormat.QR_CODE,
            SIDE_PX,
            SIDE_PX,
            mapOf(
                EncodeHintType.MARGIN to 2,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
            )
        )
        val pixels = IntArray(SIDE_PX * SIDE_PX)
        for (y in 0 until SIDE_PX) {
            val row = y * SIDE_PX
            for (x in 0 until SIDE_PX) pixels[row + x] = if (matrix.get(x, y)) BLACK else WHITE
        }
        Bitmap.createBitmap(pixels, SIDE_PX, SIDE_PX, Bitmap.Config.ARGB_8888)
    }.getOrNull()
}
