package com.dominion.mobile.platform.audio

import com.dominion.mobile.data.RemotePcm
import io.livekit.android.room.track.AudioTrack
import livekit.org.webrtc.AudioTrackSink
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

object RemoteSpeechCapture {
    suspend fun capture(track: AudioTrack, stop: AtomicBoolean,
        valid: () -> Boolean, onProgress: (Int) -> Unit,
        autoEndpoint: Boolean = false,
        onSpeechStarted: (() -> Unit)? = null): ByteArray = withContext(Dispatchers.Main.immediate) {
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
        var speechReported = false
        try {
            while (!stop.get() && android.os.SystemClock.elapsedRealtime() - started < 30_000L) {
                check(valid()) { "Call or audio track changed" }
                onProgress((audio.size() / 16000).coerceIn(0,30))
                check(!audio.failed)
                if (autoEndpoint && audio.endpointHasSpeech() && !speechReported) {
                    speechReported = true
                    onSpeechStarted?.invoke()
                }
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

    /** Use the existing microphone track while speech is playing. The output is
     * interrupted on sustained input; the caller must review the transcription
     * because speaker echo can look like a new utterance.
     */
    suspend fun playWithBargeIn(track: AudioTrack, stop: AtomicBoolean,
        valid: () -> Boolean, onBargeIn: () -> Unit,
        play: suspend () -> Unit): ByteArray? = coroutineScope {
        val heard = AtomicBoolean(false)
        val sound = launch(start = CoroutineStart.LAZY) { play() }
        sound.start()
        val listener = async {
            while (sound.isActive) {
                try {
                    val wav = capture(track, stop, valid, {}, autoEndpoint = true,
                        onSpeechStarted = {
                            if (sound.isActive && heard.compareAndSet(false, true)) {
                                sound.cancel()
                                onBargeIn()
                            }
                        })
                    if (heard.get()) return@async wav
                } catch (e: NoSpeechDetected) {
                    if (heard.get() || stop.get()) return@async null
                }
            }
            null
        }
        try {
            sound.join()
            if (heard.get()) listener.await()
            else { listener.cancelAndJoin(); null }
        } finally {
            sound.cancel()
            listener.cancel()
        }
    }
}
