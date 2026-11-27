package com.dominion.mobile.platform.attachments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Bounded, metadata-free, local character image; never a remotely fetched URL. */
object AvatarPhoto {
    private const val MAX = 64 * 1024
    suspend fun read(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        require(uri.scheme == "content")
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            require(info.size.width > 0 && info.size.height > 0)
            val scale = minOf(1.0, 256.0 / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1),
                (info.size.height * scale).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        try {
            for (quality in listOf(90,75,60,45)) {
                val stream = ByteArrayOutputStream()
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream))
                val encoded = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                if (encoded.length <= MAX && valid(encoded)) return@withContext encoded
            }
            error("Photo too large")
        } finally { bitmap.recycle() }
    }
    fun valid(photo: String): Boolean {
        if (photo.isEmpty() || photo.length > MAX) return false
        val bytes = runCatching { Base64.decode(photo, Base64.NO_WRAP) }.getOrNull() ?: return false
        if (bytes.size < 4 || bytes[0] != 0xff.toByte() || bytes[1] != 0xd8.toByte()) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        return bounds.outWidth in 1..256 && bounds.outHeight in 1..256
    }
    fun decode(photo: String): Bitmap? = runCatching {
        require(valid(photo))
        val bytes = Base64.decode(photo, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes,0,bytes.size)
    }.getOrNull()
}
