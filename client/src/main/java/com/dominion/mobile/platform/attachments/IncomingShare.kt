package com.dominion.mobile.platform.attachments

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/** Parse external shares without reading or uploading until the bounded import. */
data class IncomingShare(val text: String?, val uris: List<Uri>) {
    companion object {
        fun parse(intent: Intent): IncomingShare {
            require(intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE)
            val files = linkedSetOf<Uri>()
            if (intent.action == Intent.ACTION_SEND) {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(files::add)
            } else {
                files.addAll(IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty())
            }
            intent.clipData?.let { clip ->
                require(clip.itemCount <= 5)
                for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(files::add)
            }
            require(files.size <= 5 && files.all { it.scheme == "content" && !it.authority.isNullOrBlank() })
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.take(32000)
            return IncomingShare(text, files.toList())
        }
    }
}
