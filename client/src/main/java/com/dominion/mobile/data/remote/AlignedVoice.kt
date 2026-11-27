package com.dominion.mobile.data.remote

import com.dominion.mobile.data.SpeechEnvelope
import java.util.Base64

data class AlignedVoiceRequest(val accountId: String, val text: String)
data class SpeechCue(val startMs: Int = 0, val endMs: Int = 0,
    val viseme: String? = null, val openness: Float = 0f)
data class AlignedVoiceResponse(val accountId: String? = null, val wavBase64: String? = null,
    val cues: List<SpeechCue>? = null) {
    fun verified(owner: String): AlignedSpeech {
        check(accountId == owner)
        val encoded = requireNotNull(wavBase64)
        require(encoded.length in 1..11_184_812)
        val wav = Base64.getDecoder().decode(encoded)
        val envelope = requireNotNull(SpeechEnvelope.fromWave(wav))
        return AlignedSpeech(wav, SpeechCueTimeline(requireNotNull(cues), envelope.durationMs))
    }
}
data class AlignedSpeech(val wav: ByteArray, val timeline: SpeechCueTimeline)
class SpeechCueTimeline(private val cues: List<SpeechCue>, durationMs: Int) {
    init {
        require(durationMs in 1..300000 && cues.size in 1..6000)
        var end = 0
        for (cue in cues) {
            require(cue.startMs >= end && cue.endMs > cue.startMs && cue.endMs <= durationMs &&
                cue.viseme in shapes && cue.openness.isFinite() && cue.openness in 0f..1f)
            end = cue.endMs
        }
    }
    fun at(positionMs: Int): SpeechCue? {
        if (positionMs < 0) return null
        var lo = 0; var hi = cues.lastIndex
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val cue = cues[mid]
            when {
                positionMs < cue.startMs -> hi = mid - 1
                positionMs >= cue.endMs -> lo = mid + 1
                else -> return cue
            }
        }
        return null
    }
    companion object { val shapes = setOf("a","e","i","o","u","closed") }
}
