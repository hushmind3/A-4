package com.dominion.mobile.platform

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ContactQr {
    const val PROJECT_URL = "https://dominionai.kr"
    fun shareProject(context: Context) {
        share(context, "DOMINION 프로젝트: $PROJECT_URL")
    }
    fun share(context: Context, value: String) {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND)
            .setType("text/plain").putExtra(Intent.EXTRA_TEXT, value), "공유하기"))
    }
    fun matrix(value: String) = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 512, 512)
    fun bitmap(value: String): Bitmap {
        val matrix = matrix(value)
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            if (matrix[index % matrix.width, index / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    }
    fun decode(bitmap: Bitmap): String {
        require(bitmap.width > 0 && bitmap.height > 0 &&
            bitmap.width.toLong() * bitmap.height <= 16_000_000)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))),
            mapOf(DecodeHintType.TRY_HARDER to true)).text
    }
    suspend fun readImage(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        require(uri.scheme == "content")
        val image = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val scale = minOf(1.0, 2400.0 / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1),
                (info.size.height * scale).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        try { decode(image) } finally { image.recycle() }
    }
}
