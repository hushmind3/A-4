package com.dominion.mobile.platform.audio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import io.livekit.android.room.track.VideoTrack
import livekit.org.webrtc.VideoFrame
import livekit.org.webrtc.VideoSink
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/** A single explicit frame from a currently published local track, not remote surveillance. */
object CallFrameCapture {
    suspend fun capture(track: VideoTrack): ByteArray = withContext(Dispatchers.Main.immediate) {
        val result = CompletableDeferred<ByteArray>()
        val taken = AtomicBoolean(false)
        val sink = VideoSink { frame ->
            if (!taken.compareAndSet(false, true) || !result.isActive) return@VideoSink
            try { result.complete(jpeg(frame)) }
            catch (e: Exception) { result.completeExceptionally(e) }
        }
        track.addRenderer(sink)
        try { withTimeout(6000) { result.await() } }
        finally { result.cancel(); runCatching { track.removeRenderer(sink) } }
    }

    private fun jpeg(frame: VideoFrame): ByteArray {
        val input = frame.buffer
        val w = input.width
        val h = input.height
        require(w > 1 && h > 1)
        val ratio = minOf(1.0, 1600.0 / maxOf(w,h))
        val width = ((w * ratio).toInt() / 2 * 2).coerceAtLeast(2)
        val height = ((h * ratio).toInt() / 2 * 2).coerceAtLeast(2)
        // WebRTC owns the original frame. Only release the converted/scaled references.
        val scaled = input.cropAndScale(0, 0, w, h, width, height)
        try {
            val buffer = requireNotNull(scaled.toI420())
            try {
                val nv21 = ByteArray(width * height * 3 / 2)
                fun byteAt(data: ByteBuffer, row: Int, stride: Int, col: Int) =
                    data.get(data.position() + row * stride + col)
                val y = buffer.dataY
                val u = buffer.dataU
                val v = buffer.dataV
                for (row in 0 until height) for (col in 0 until width) {
                    nv21[row * width + col] = byteAt(y, row, buffer.strideY, col)
                }
                val offset = width * height
                for (row in 0 until height / 2) for (col in 0 until width / 2) {
                    val at = offset + row * width + col * 2
                    nv21[at] = byteAt(v, row, buffer.strideV, col)
                    nv21[at + 1] = byteAt(u, row, buffer.strideU, col)
                }
                val output = ByteArrayOutputStream()
                check(YuvImage(nv21, ImageFormat.NV21, width, height, null)
                    .compressToJpeg(Rect(0,0,width,height), 88, output))
                val bytes = output.toByteArray()
                val rotation = frame.rotation % 360
                if (rotation == 0) return bytes
                val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size))
                try {
                    val rotated = Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,
                        Matrix().apply { postRotate(rotation.toFloat()) },true)
                    try {
                        val corrected = ByteArrayOutputStream()
                        check(rotated.compress(Bitmap.CompressFormat.JPEG,88,corrected))
                        return corrected.toByteArray()
                    } finally { if (rotated !== bitmap) rotated.recycle() }
                } finally { bitmap.recycle() }
            } finally { buffer.release() }
        } finally { scaled.release() }
    }
}
