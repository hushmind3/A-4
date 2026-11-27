package com.dominion.mobile.platform.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.dominion.mobile.data.WaveAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class NoSpeechDetected : Exception("No sustained speech detected")

object EngineVoiceAudio {
    @SuppressLint("MissingPermission")
    suspend fun record(context: Context, stop: AtomicBoolean,
        autoEndpoint: Boolean = false): ByteArray = withContext(Dispatchers.IO) {
        require(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        val rate = WaveAudio.SAMPLE_RATE
        val minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        require(minimum > 0)
        val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum * 2, 6400))
        val samples = ShortArray(rate * WaveAudio.MAX_SECONDS)
        var count = 0
        val detector = com.dominion.mobile.data.SpeechEndpoint()
        val started = android.os.SystemClock.elapsedRealtime()
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED)
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING)
            while (!stop.get() && count < samples.size) {
                currentCoroutineContext().ensureActive()
                val n = recorder.read(samples, count, minOf(320, samples.size - count), AudioRecord.READ_NON_BLOCKING)
                check(n >= 0)
                if (autoEndpoint && n > 0) detector.accept(samples, count, n)
                count += n
                if (autoEndpoint && (detector.finished || detector.noSpeechTimeout)) break
                if (android.os.SystemClock.elapsedRealtime() - started > 65_000L) break
                if (n == 0) delay(10)
            }
            require(count >= rate / 4) { "Recording too short" }
            if (autoEndpoint && !detector.hasSpeech) throw NoSpeechDetected()
            WaveAudio.mono16(samples, count)
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }

    suspend fun play(context: Context, bytes: ByteArray) {
        require(WaveAudio.isWave(bytes))
        val file = withContext(Dispatchers.IO) {
            File.createTempFile("dominion-speech-", ".wav", context.cacheDir).also { it.writeBytes(bytes) }
        }
        try {
            withContext(Dispatchers.Main.immediate) {
                val player = MediaPlayer()
                val manager = context.getSystemService(AudioManager::class.java)
                val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                var focusLease: AudioFocusRequest? = null
                try {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(attributes)
                            .setOnAudioFocusChangeListener { change ->
                                if (change < 0 && continuation.isActive)
                                    continuation.resumeWithException(IllegalStateException("Audio focus lost"))
                            }.build()
                        continuation.invokeOnCancellation {
                            // Cleanup is performed on the main dispatcher by the finally block.
                        }
                        try {
                            check(manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                            player.setAudioAttributes(attributes)
                            player.setDataSource(file.absolutePath)
                            player.setOnPreparedListener { if (continuation.isActive) it.start() }
                            player.setOnCompletionListener { if (continuation.isActive) continuation.resume(Unit) }
                            player.setOnErrorListener { _, _, _ ->
                                if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Playback failed"))
                                true
                            }
                            player.prepareAsync()
                            // Store the focus lease until playback ends.
                            focusLease = focus
                        } catch (e: Exception) {
                            manager.abandonAudioFocusRequest(focus)
                            if (continuation.isActive) continuation.resumeWithException(e)
                        }
                    }
                } finally {
                    player.release()
                    focusLease?.let { manager.abandonAudioFocusRequest(it) }
                    focusLease = null
                }
            }
        } finally { file.delete() }
    }
}
