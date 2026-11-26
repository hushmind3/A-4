package com.dominion.mobile.platform.attachments

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Only user-selected content is read. No broad storage scan or implicit upload. */
data class LocalAttachment(val name: String, val mimeType: String, val bytes: ByteArray) {
    companion object {
        const val MAX_BYTES = 8 * 1024 * 1024

        fun readBounded(input: InputStream): ByteArray {
            val buffer = ByteArray(32 * 1024)
            val result = ByteArrayOutputStream()
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(result.size() + count <= MAX_BYTES) { "Attachment exceeds 8 MiB" }
                result.write(buffer, 0, count)
            }
            return result.toByteArray().also { require(it.isNotEmpty()) }
        }

        suspend fun fromUri(context: Context, uri: Uri): LocalAttachment = withContext(Dispatchers.IO) {
            require(uri.scheme == "content")
            val resolver = context.contentResolver
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) null else {
                    val size = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (size >= 0 && !cursor.isNull(size)) require(cursor.getLong(size) <= MAX_BYTES)
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0) cursor.getString(column) else null
                }
            }.orEmpty().filterNot { it.isISOControl() || it == '/' || it == '\\' }
                .take(240).ifBlank { "attachment" }
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val bytes = requireNotNull(resolver.openInputStream(uri)).use(::readBounded)
            LocalAttachment(name, mime, bytes)
        }

        suspend fun fromCameraPreview(bitmap: Bitmap): LocalAttachment = withContext(Dispatchers.IO) {
            val out = ByteArrayOutputStream()
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out))
            val bytes = out.toByteArray()
            require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES)
            LocalAttachment("camera-${System.currentTimeMillis()}.jpg", "image/jpeg", bytes)
        }
    }
}
