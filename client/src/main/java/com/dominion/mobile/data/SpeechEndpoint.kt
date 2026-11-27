package com.dominion.mobile.data

/**
 * Bounded PCM energy endpointing. Not a learned VAD: background sounds can
 * trigger it. Keep the entire captured turn (including initial consonants).
 */
class SpeechEndpoint(private val rate: Int = WaveAudio.SAMPLE_RATE) {
    private val frame = rate / 50
    private var samples = 0
    private var sum = 0.0
    private var frames = 0
    private var voiced = 0
    private var quiet = 0
    private var floor = 100.0
    var hasSpeech = false
        private set
    var finished = false
        private set
    val noSpeechTimeout get() = !hasSpeech && frames >= 500

    fun accept(pcm: ShortArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset <= pcm.size - length)
        for (i in offset until offset + length) {
            val sample = pcm[i].toDouble()
            sum += sample * sample
            if (++samples < frame) continue
            val rms = kotlin.math.sqrt(sum / samples)
            samples = 0; sum = 0.0; frames++
            val loud = rms > maxOf(450.0, floor * 3.0)
            if (!hasSpeech && !loud) floor = (floor * 0.96 + rms * 0.04).coerceIn(40.0, 350.0)
            if (loud) {
                voiced++; quiet = 0
                if (voiced >= 8) hasSpeech = true
            } else {
                voiced = 0
                if (hasSpeech && ++quiet >= 45) finished = true
            }
        }
    }
}
