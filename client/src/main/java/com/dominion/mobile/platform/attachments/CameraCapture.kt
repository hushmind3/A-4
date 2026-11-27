package com.dominion.mobile.platform.attachments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class CameraFileProvider : FileProvider()

/** Full-size camera capture in an isolated, temporary directory. */
object CameraCapture {
    data class Target(val key: String, val uri: Uri)
    private fun directory(context: Context) = File(context.cacheDir, "camera-capture").apply { mkdirs() }
    private fun file(context: Context, key: String): File {
        require(UUID.fromString(key).toString() == key)
        return File(directory(context), "$key.jpg")
    }

    fun create(context: Context): Target {
        val root = directory(context)
        root.listFiles()?.filter { it.isFile && it.name.endsWith(".jpg") &&
            System.currentTimeMillis() - it.lastModified() > 24L * 60 * 60 * 1000 }
            ?.forEach { it.delete() }
        val key = UUID.randomUUID().toString()
        val target = file(context, key)
        check(target.createNewFile())
        return try {
            Target(key, FileProvider.getUriForFile(context, context.packageName + ".camera", target))
        } catch (e: Exception) { target.delete(); throw e }
    }

    fun discard(context: Context, key: String) { runCatching { file(context, key).delete() } }

    suspend fun read(context: Context, key: String): LocalAttachment = withContext(Dispatchers.IO) {
        val file = file(context, key)
        require(file.isFile && file.length() in 1..100L * 1024 * 1024)
        // Re-encode the selected capture to remove metadata (including GPS).
        // Bound decoded dimensions to avoid huge bitmap allocations.
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val size = info.size
            require(size.width > 0 && size.height > 0)
            val scale = minOf(1.0, 4096.0 / maxOf(size.width, size.height),
                kotlin.math.sqrt(12_000_000.0 / (size.width.toDouble() * size.height)))
            decoder.setTargetSize((size.width * scale).toInt().coerceAtLeast(1),
                (size.height * scale).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        try {
            for (quality in listOf(95, 88, 80, 70, 55)) {
                val output = ByteArrayOutputStream()
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output))
                if (output.size() in 1..LocalAttachment.MAX_BYTES) {
                    return@withContext LocalAttachment("camera-$key.jpg", "image/jpeg", output.toByteArray())
                }
            }
            error("Captured image exceeds attachment limit")
        } finally { bitmap.recycle() }
    }

    fun thumbnail(bytes: ByteArray): Bitmap? = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outWidth > 0 && options.outHeight > 0)
        var sample = 1
        while (maxOf(options.outWidth, options.outHeight) / sample > 256) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}
