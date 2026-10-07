package com.nezzar.nfcattendance.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * A QR code drawn from a string, encoded on the phone with ZXing - no service, no
 * upload, nothing to reach. Used for the code the app prints for a student, which
 * carries that student's card UID and nothing else.
 *
 * Drawn white-on-... well, black-on-white deliberately: a camera reads contrast, and
 * a code in the app's dark palette is exactly what a scanner struggles with.
 */
@Composable
fun QrCodeImage(text: String, size: Dp, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        try {
            QRCodeWriter().encode(
                text,
                BarcodeFormat.QR_CODE,
                512,
                512,
                mapOf(EncodeHintType.MARGIN to 1),
            )
        } catch (t: Throwable) {
            null
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .background(Color.White, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (matrix == null) return@Box
        Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            val span = minOf(this.size.width, this.size.height)
            val cell = span / matrix.width
            val left = (this.size.width - span) / 2f
            val top = (this.size.height - span) / 2f
            for (x in 0 until matrix.width) {
                for (y in 0 until matrix.height) {
                    if (matrix.get(x, y)) {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(left + x * cell, top + y * cell),
                            // A hair of overlap so neighbouring modules cannot leave a seam
                            // that a camera reads as a white line.
                            size = Size(cell + 0.7f, cell + 0.7f),
                        )
                    }
                }
            }
        }
    }
}
