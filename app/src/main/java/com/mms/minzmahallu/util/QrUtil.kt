package com.mms.minzmahallu.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** Minimal QR encoder (ZXing core) for certificate verification codes. */
object QrUtil {

    fun make(text: String, size: Int = 512): Bitmap? {
        if (text.isBlank()) return null
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1
            )
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
            val w = matrix.width
            val h = matrix.height
            val dark = Color.parseColor("#0F172A")
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    pixels[y * w + x] = if (matrix.get(x, y)) dark else Color.WHITE
                }
            }
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                it.setPixels(pixels, 0, w, 0, 0, w, h)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Payload printed under the QR and embedded in certificate PDFs. */
    fun certificatePayload(orgName: String, certNo: String, code: String): String =
        "MINZ-MAHALLU|ORG:$orgName|CERT:$certNo|VERIFY:$code"
}
