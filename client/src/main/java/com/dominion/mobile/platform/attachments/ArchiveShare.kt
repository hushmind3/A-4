package com.dominion.mobile.platform.attachments

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.dominion.mobile.data.model.ArchiveItem
import java.io.File
import java.util.UUID

class ArchiveFileProvider : FileProvider()

object ArchiveShare {
    fun safeName(value: String): String =
        value.filterNot { it.isISOControl() || it == '/' || it == '\\' }
            .take(160).trim().takeUnless { it.isBlank() || it == "." || it == ".." } ?: "attachment"

    fun fileIntent(context: Context, item: ArchiveItem, bytes: ByteArray): Intent =
        prepareIntent(context, item, bytes, false)

    fun viewIntent(context: Context, item: ArchiveItem, bytes: ByteArray): Intent =
        prepareIntent(context, item, bytes, true)

    private fun prepareIntent(context: Context, item: ArchiveItem, bytes: ByteArray, view: Boolean): Intent {
        require(bytes.isNotEmpty() && bytes.size <= LocalAttachment.MAX_BYTES)
        val root = File(context.cacheDir, "archive-share").apply { mkdirs() }
        root.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24L * 60 * 60 * 1000 }
            ?.forEach { it.deleteRecursively() }
        val dir = File(root, UUID.randomUUID().toString()).apply { check(mkdir()) }
        val file = File(dir, safeName(item.title))
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".archive", file)
        return Intent(if (view) Intent.ACTION_VIEW else Intent.ACTION_SEND).apply {
            val mime = item.mimeType?.takeIf { it.contains('/') } ?: "application/octet-stream"
            if (view) setDataAndType(uri, mime) else {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
            }
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
