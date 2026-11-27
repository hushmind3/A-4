package com.dominion.mobile.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class CallSpeechEndpointTest {
    @Test fun detectsActualSpeechAndSilenceOnExistingTrack() {
        val pcm = RemotePcm(30, SpeechEndpoint())
        fun feed(value: Short) {
            val data = ByteBuffer.allocate(640).order(ByteOrder.nativeOrder())
            repeat(320) { data.putShort(value) }
            data.flip()
            pcm.accept(data, 16, 16000, 1, 320)
        }
        repeat(12) { feed(3000) }
        assertTrue(pcm.endpointHasSpeech())
        repeat(46) { feed(0) }
        assertTrue(pcm.endpointFinished())
        assertTrue(WaveAudio.isWave(pcm.wave()))
    }
    @Test fun silenceDoesNotCreateCommand() {
        val pcm = RemotePcm(30, SpeechEndpoint())
        repeat(501) {
            pcm.accept(ByteBuffer.allocate(640), 16, 16000, 1, 320)
        }
        assertTrue(pcm.endpointTimedOut())
        assertFalse(pcm.endpointHasSpeech())
    }
}
