package com.dominion.mobile.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class WaveAudioTest {
    @Test fun pcmWaveHasCorrectLittleEndianHeaderAndSamples() {
        val wav = WaveAudio.mono16(shortArrayOf(0, -2, 300))
        val header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertTrue(WaveAudio.isWave(wav))
        assertEquals(42, header.getInt(4))
        assertEquals(16000, header.getInt(24))
        assertEquals(6, header.getInt(40))
        assertEquals((-2).toShort(), header.getShort(46))
    }
    @Test fun longUnicodeTextIsSplitWithoutLosingCodepoints() {
        val text = "가😀".repeat(2500)
        val chunks = WaveAudio.speechChunks(text)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.all { it.codePointCount(0, it.length) <= 1800 })
    }
}
