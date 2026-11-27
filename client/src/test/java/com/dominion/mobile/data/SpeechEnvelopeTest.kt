package com.dominion.mobile.data

import org.junit.Assert.*
import org.junit.Test

class SpeechEnvelopeTest {
    @Test fun actualSoundOpensMouthAndSilenceClosesIt() {
        val samples = ShortArray(1920) { if (it in 640 until 1280) 12000 else 0 }
        val envelope = requireNotNull(SpeechEnvelope.fromWave(WaveAudio.mono16(samples)))
        assertEquals(0f, envelope.levelAt(0), 0f)
        assertTrue(envelope.levelAt(40) > 0.5f)
        assertEquals(0f, envelope.levelAt(80), 0f)
        assertEquals(0f, envelope.levelAt(120), 0f)
    }

    @Test fun malformedAndUnsupportedWaveDoNotAnimate() {
        val bytes = WaveAudio.mono16(ShortArray(640))
        assertNull(SpeechEnvelope.fromWave(bytes.copyOf(44)))
        bytes[20] = 3
        assertNull(SpeechEnvelope.fromWave(bytes))
        assertNull(SpeechEnvelope.fromWave(byteArrayOf()))
    }
}
