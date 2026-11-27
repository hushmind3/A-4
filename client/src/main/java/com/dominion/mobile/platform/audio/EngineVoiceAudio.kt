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
import android.media.audiofx.AcousticEchoCanceler
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
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
        autoEndpoint: Boolean = false, duringPlayback: Boolean = false,
        speechStarted: AtomicBoolean? = null): ByteArray = withContext(Dispatchers.IO) {
        require(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        val rate = WaveAudio.SAMPLE_RATE
        val minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        require(minimum > 0)
        val recorder = AudioRecord(if (duringPlayback) MediaRecorder.AudioSource.VOICE_COMMUNICATION
            else MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum * 2, 6400))
        val samples = ShortArray(rate * WaveAudio.MAX_SECONDS)
        var count = 0
        val detector = com.dominion.mobile.data.SpeechEndpoint()
        val started = android.os.SystemClock.elapsedRealtime()
        var echo: AcousticEchoCanceler? = null
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED)
            if (duringPlayback) {
                check(AcousticEchoCanceler.isAvailable()) { "Echo cancellation unavailable" }
                echo = requireNotNull(AcousticEchoCanceler.create(recorder.audioSessionId))
                check(echo.setEnabled(true) == android.media.audiofx.AudioEffect.SUCCESS && echo.enabled)
            }
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING)
            while (!stop.get() && count < samples.size) {
                currentCoroutineContext().ensureActive()
                val n = recorder.read(samples, count, minOf(320, samples.size - count), AudioRecord.READ_NON_BLOCKING)
                check(n >= 0)
                if (autoEndpoint && n > 0) detector.accept(samples, count, n)
                if (detector.hasSpeech) speechStarted?.set(true)
                count += n
                if (autoEndpoint && (detector.finished || (!duringPlayback && detector.noSpeechTimeout))) break
                if (android.os.SystemClock.elapsedRealtime() - started > 65_000L) break
                if (n == 0) delay(10)
            }
            require(count >= rate / 4) { "Recording too short" }
            if (autoEndpoint && !detector.hasSpeech) throw NoSpeechDetected()
            WaveAudio.mono16(samples, count)
        } finally {
            runCatching { recorder.stop() }
            echo?.release()
            recorder.release()
        }
    }

    fun supportsBargeIn(): Boolean = AcousticEchoCanceler.isAvailable()

    /**
     * Capture while audio plays only after explicit opt-in. If echo cancellation is
     * unavailable at runtime, finish ordinary playback without an auto interruption.
     * Audio is retained in RAM, not uploaded here.
     */
    suspend fun playListening(context: Context, bytes: ByteArray): ByteArray? = coroutineScope {
        val manager = context.getSystemService(AudioManager::class.java)
        val previousMode = manager.mode
        val heard = AtomicBoolean(false)
        val stop = AtomicBoolean(false)
        var recording: kotlinx.coroutines.Deferred<ByteArray?>? = null
        var playback: kotlinx.coroutines.Job? = null
        try {
            manager.mode = AudioManager.MODE_IN_COMMUNICATION
            val capture = async {
                try { record(context, stop, autoEndpoint = true, duringPlayback = true, speechStarted = heard) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { null }
            }
            recording = capture
            val sound = launch { play(context, bytes, communication = true) }
            playback = sound
            while (sound.isActive && !heard.get()) delay(40)
            if (heard.get()) {
                sound.cancelAndJoin()
                capture.await()
            } else {
                sound.join()
                capture.cancelAndJoin()
                null
            }
        } finally {
            stop.set(true)
            recording?.cancel()
            playback?.cancel()
            withContext(kotlinx.coroutines.NonCancellable) {
                recording?.join()
                playback?.join()
                runCatching {
                    if (manager.mode == AudioManager.MODE_IN_COMMUNICATION) manager.mode = previousMode
                }
            }
        }
    }

    suspend fun play(context: Context, bytes: ByteArray, communication: Boolean = false,
        existingCallFocus: Boolean = false, onSpeechLevel: ((Float) -> Unit)? = null) {
        require(!existingCallFocus || communication)
        require(WaveAudio.isWave(bytes))
        val envelope = if (onSpeechLevel != null) withContext(Dispatchers.Default) {
            com.dominion.mobile.data.SpeechEnvelope.fromWave(bytes)
        } else null
        val file = File.createTempFile("dominion-speech-", ".wav", context.cacheDir)
        try {
            withContext(Dispatchers.IO) { file.writeBytes(bytes) }
            withContext(Dispatchers.Main.immediate) {
                val player = MediaPlayer()
                val manager = context.getSystemService(AudioManager::class.java)
                val attributes = AudioAttributes.Builder().setUsage(if (communication) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                var focusLease: AudioFocusRequest? = null
                var animation: kotlinx.coroutines.Job? = null
                val animationScope = kotlinx.coroutines.CoroutineScope(currentCoroutineContext())
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
                            if (!existingCallFocus) {
                                check(manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                                focusLease = focus
                            }
                            player.setAudioAttributes(attributes)
                            player.setDataSource(file.absolutePath)
                            player.setOnPreparedListener {
                                if (continuation.isActive) {
                                    it.start()
                                    if (onSpeechLevel != null && envelope != null) animation = animationScope.launch {
                                        while (isActive && continuation.isActive) {
                                            val position = runCatching { player.currentPosition }.getOrNull() ?: break
                                            onSpeechLevel.invoke(envelope.levelAt(position))
                                            delay(40)
                                        }
                                    }
                                }
                            }
                            player.setOnCompletionListener { if (continuation.isActive) continuation.resume(Unit) }
                            player.setOnErrorListener { _, _, _ ->
                                if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Playback failed"))
                                true
                            }
                            player.prepareAsync()
                        } catch (e: Exception) {
                            if (!existingCallFocus) manager.abandonAudioFocusRequest(focus)
                            focusLease = null
                            if (continuation.isActive) continuation.resumeWithException(e)
                        }
                    }
                } finally {
                    animation?.cancel()
                    onSpeechLevel?.invoke(0f)
                    player.release()
                    focusLease?.let { manager.abandonAudioFocusRequest(it) }
                    focusLease = null
                }
            }
        } finally { file.delete() }
    }
}
