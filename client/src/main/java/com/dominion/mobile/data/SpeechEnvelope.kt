package com.dominion.mobile.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** 40ms RMS envelope of actual PCM16 WAV samples, indexed by player time.
 * Unknown formats still play normally; they simply have no mouth animation.
 */
internal class SpeechEnvelope private constructor(private val levels: FloatArray, val durationMs: Int) {
    fun levelAt(positionMs: Int): Float =
        if (positionMs < 0) 0f else levels.getOrElse(positionMs / 40) { 0f }

    companion object {
        fun fromWave(bytes: ByteArray): SpeechEnvelope? {
            if (!WaveAudio.isWave(bytes) || bytes.size > 8 * 1024 * 1024) return null
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            var at = 12
            var rate = 0
            var channels = 0
            var data = -1
            var size = 0
            while (at <= bytes.size - 8) {
                val length = b.getInt(at + 4)
                val start = at + 8
                if (length < 0 || length > bytes.size - start) return null
                when (String(bytes, at, 4, Charsets.US_ASCII)) {
                    "fmt " -> {
                        if (length < 16 || b.getShort(start).toInt() != 1 ||
                            b.getShort(start + 14).toInt() != 16) return null
                        channels = b.getShort(start + 2).toInt()
                        rate = b.getInt(start + 4)
                    }
                    "data" -> { data = start; size = length; break }
                }
                at = start + length + (length and 1)
            }
            if (rate !in 8000..96000 || channels !in 1..2 || data < 0 ||
                size < channels * 2 || size % (channels * 2) != 0) return null
            val frameBytes = channels * 2
            val frames = size / frameBytes
            val window = rate * 40 / 1000
            val levels = FloatArray((frames + window - 1) / window)
            for (i in levels.indices) {
                val end = minOf(frames, (i + 1) * window)
                var sum = 0.0
                for (frame in i * window until end) {
                    for (channel in 0 until channels) {
                        val sample = b.getShort(data + frame * frameBytes + channel * 2) / 32768.0
                        sum += sample * sample
                    }
                }
                val rms = sqrt(sum / ((end - i * window) * channels))
                levels[i] = ((rms - 0.012) * 5).toFloat().coerceIn(0f, 1f)
            }
            return SpeechEnvelope(levels, ((frames.toLong()*1000L + rate - 1) / rate).toInt())
        }
    }
}
