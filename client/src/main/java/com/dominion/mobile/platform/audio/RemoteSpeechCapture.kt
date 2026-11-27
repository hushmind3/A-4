package com.dominion.mobile.platform.audio

import com.dominion.mobile.data.RemotePcm
import io.livekit.android.room.track.AudioTrack
import livekit.org.webrtc.AudioTrackSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

object RemoteSpeechCapture {
    suspend fun capture(track: AudioTrack, stop: AtomicBoolean,
        valid: () -> Boolean, onProgress: (Int) -> Unit,
        autoEndpoint: Boolean = false): ByteArray = withContext(Dispatchers.Main.immediate) {
        val audio = RemotePcm(30, if (autoEndpoint) com.dominion.mobile.data.SpeechEndpoint() else null)
        val collecting = AtomicBoolean(true)
        val sink = object : AudioTrackSink {
            override fun onData(audioData: ByteBuffer?, bitsPerSample: Int, sampleRate: Int,
                numberOfChannels: Int, numberOfFrames: Int, absoluteCaptureTimestampMs: Long) {
                if (collecting.get()) audio.accept(audioData, bitsPerSample, sampleRate, numberOfChannels, numberOfFrames)
            }
        }
        track.addSink(sink)
        val started = android.os.SystemClock.elapsedRealtime()
        try {
            while (!stop.get() && android.os.SystemClock.elapsedRealtime() - started < 30_000L) {
                check(valid()) { "Call or audio track changed" }
                onProgress((audio.size() / 16000).coerceIn(0,30))
                check(!audio.failed)
                if (autoEndpoint && (audio.endpointFinished() || audio.endpointTimedOut())) break
                delay(100)
            }
            check(valid())
        } finally {
            collecting.set(false)
            track.removeSink(sink)
        }
        if (autoEndpoint && !audio.endpointHasSpeech()) throw NoSpeechDetected()
        audio.wave()
    }
}
