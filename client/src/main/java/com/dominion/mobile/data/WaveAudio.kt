package com.dominion.mobile.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

object WaveAudio {
    const val SAMPLE_RATE = 16000
    const val MAX_SECONDS = 60
    fun isWave(bytes: ByteArray): Boolean = bytes.size >= 44 &&
        String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
        String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE"

    fun mono16(samples: ShortArray, count: Int = samples.size): ByteArray {
        require(count in 1..(SAMPLE_RATE * MAX_SECONDS) && count <= samples.size)
        return ByteBuffer.allocate(44 + count * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII)); putInt(36 + count * 2)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); putInt(16)
            putShort(1); putShort(1); putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2)
            putShort(2); putShort(16)
            put("data".toByteArray(Charsets.US_ASCII)); putInt(count * 2)
            for (i in 0 until count) putShort(samples[i])
        }.array()
    }

    /** Split on Unicode codepoints; no truncation of longer answers. */
    fun speechChunks(text: String): List<String> {
        require(text.isNotBlank() && text.length <= 32000)
        val output = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val remaining = text.codePointCount(start, text.length)
            var end = text.offsetByCodePoints(start, minOf(remaining, 1800))
            if (end < text.length) {
                val boundary = text.lastIndexOfAny(charArrayOf('\n', ' ', '.', '!', '?'), end - 1)
                if (boundary > start + 100) end = boundary + 1
            }
            text.substring(start, end).trim().takeIf { it.isNotBlank() }?.let(output::add)
            start = end
        }
        return output
    }
}
