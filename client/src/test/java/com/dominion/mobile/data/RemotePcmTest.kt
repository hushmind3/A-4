package com.dominion.mobile.data
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
class RemotePcmTest {
    @Test fun stereo48kToMono16kAcrossFrames() {
        val pcm = RemotePcm(1)
        repeat(100) {
            val b = ByteBuffer.allocate(480 * 4).order(ByteOrder.nativeOrder())
            repeat(480) { b.putShort(1200); b.putShort(800) }
            b.flip()
            pcm.accept(b,16,48000,2,480)
        }
        assertEquals(16000,pcm.size())
        val wav = ByteBuffer.wrap(pcm.wave()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(32044,wav.capacity())
        assertEquals(1000,wav.getShort(44).toInt())
    }
    @Test fun rejectFormatChangeAndSilence() {
        val pcm = RemotePcm(1)
        pcm.accept(ByteBuffer.allocate(20),8,48000,1,10)
        assertTrue(pcm.failed)
        assertTrue(runCatching { pcm.wave() }.isFailure)
    }
}
