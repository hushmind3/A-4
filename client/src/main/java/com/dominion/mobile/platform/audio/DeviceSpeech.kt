package com.dominion.mobile.platform.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.audiofx.AcousticEchoCanceler
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * System TextToSpeech fallback for DOMINION Cloud replies when the hosted Korean
 * TTS provider is not configured. No speech model is bundled with the app and no
 * synthesized audio leaves the device. The engine lifecycle is explicit so the
 * caller can stop playback for barge-in and release resources on background.
 */
data class DeviceSpeechAvailability(val available: Boolean, val korean: Boolean, val english: Boolean)

enum class VoiceTtsMode { CLOUD, DEVICE, NONE }

object VoiceDelivery {
    fun choose(cloudAvailable: Boolean, deviceAvailable: Boolean): VoiceTtsMode = when {
        cloudAvailable -> VoiceTtsMode.CLOUD
        deviceAvailable -> VoiceTtsMode.DEVICE
        else -> VoiceTtsMode.NONE
    }
}

object DeviceSpeech {
    fun languageTag(language: String): String = if (language == "ko") "ko-KR" else "en-US"

    suspend fun availability(context: Context): DeviceSpeechAvailability =
        withContext(Dispatchers.Main.immediate) {
            val initialized = CompletableDeferred<Boolean>()
            val tts = TextToSpeech(context.applicationContext) { status ->
                initialized.complete(status == TextToSpeech.SUCCESS)
            }
            try {
                val ready = withTimeoutOrNull(6_000L) { initialized.await() } == true
                if (!ready) return@withContext DeviceSpeechAvailability(false, false, false)
                val korean = tts.isLanguageAvailable(Locale.KOREAN) >= TextToSpeech.LANG_AVAILABLE
                val english = tts.isLanguageAvailable(Locale.US) >= TextToSpeech.LANG_AVAILABLE
                DeviceSpeechAvailability(korean || english, korean, english)
            } finally {
                runCatching { tts.shutdown() }
            }
        }

    /** Returns null when the device has no usable TTS engine for the language. */
    suspend fun open(context: Context, language: String): Session? =
        withContext(Dispatchers.Main.immediate) {
            val initialized = CompletableDeferred<Boolean>()
            val tts = TextToSpeech(context.applicationContext) { status ->
                initialized.complete(status == TextToSpeech.SUCCESS)
            }
            val ready = withTimeoutOrNull(6_000L) { initialized.await() } == true
            if (!ready) {
                runCatching { tts.shutdown() }
                return@withContext null
            }
            val locale = if (language == "ko") Locale.KOREAN else Locale.US
            val result = tts.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                runCatching { tts.shutdown() }
                return@withContext null
            }
            tts.setSpeechRate(1.0f)
            Session(tts)
        }

    class Session internal constructor(private val tts: TextToSpeech) {
        private var closed = false

        /**
         * Speak one bounded chunk. Returns true only after the platform reports
         * completion, false on stop, cancellation, or an engine error.
         */
        suspend fun speak(text: String, stop: AtomicBoolean, communication: Boolean): Boolean =
            withContext(Dispatchers.Main.immediate) {
                if (closed || stop.get() || text.isBlank()) return@withContext false
                val done = CompletableDeferred<Boolean>()
                val utterance = UUID.randomUUID().toString()
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) = Unit
                    override fun onDone(id: String?) {
                        if (id == utterance) done.complete(true)
                    }

                    @Deprecated("Deprecated by Android")
                    override fun onError(id: String?) {
                        if (id == utterance) done.complete(false)
                    }

                    override fun onError(id: String?, errorCode: Int) {
                        if (id == utterance) done.complete(false)
                    }
                })
                tts.setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(if (communication) AudioAttributes.USAGE_VOICE_COMMUNICATION
                    else AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                val queued = tts.speak(text.take(3_000), TextToSpeech.QUEUE_FLUSH, null, utterance)
                if (queued != TextToSpeech.SUCCESS) return@withContext false
                val deadline = android.os.SystemClock.elapsedRealtime() +
                    maxOf(15_000L, text.length * 120L)
                while (!done.isCompleted) {
                    if (stop.get() || closed || android.os.SystemClock.elapsedRealtime() > deadline) {
                        runCatching { tts.stop() }
                        return@withContext false
                    }
                    delay(40)
                }
                done.await()
            }

        /**
         * Speak while capturing the microphone with device echo cancellation.
         * Returns the captured 16 kHz WAV only when sustained speech interrupted
         * playback; null means the reply finished normally or AEC is unavailable.
         */
        suspend fun speakListening(context: Context, text: String, stop: AtomicBoolean): ByteArray? =
            coroutineScope {
                if (closed || stop.get() || !AcousticEchoCanceler.isAvailable()) {
                    if (!closed && !stop.get()) speak(text, stop, communication = false)
                    return@coroutineScope null
                }
                val manager = context.getSystemService(AudioManager::class.java)
                val previousMode = manager.mode
                val heard = AtomicBoolean(false)
                val stopCapture = AtomicBoolean(false)
                val stopSpeech = AtomicBoolean(false)
                var capture: kotlinx.coroutines.Deferred<ByteArray?>? = null
                var playback: kotlinx.coroutines.Job? = null
                try {
                    manager.mode = AudioManager.MODE_IN_COMMUNICATION
                    capture = async {
                        try {
                            EngineVoiceAudio.record(context, stopCapture, autoEndpoint = true,
                                duringPlayback = true, speechStarted = heard)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                    }
                    playback = launch { runCatching { speak(text, stopSpeech, communication = true) } }
                    while (playback.isActive && !heard.get()) delay(40)
                    if (heard.get()) {
                        stopSpeech.set(true)
                        playback.cancelAndJoin()
                        capture.await()
                    } else {
                        playback.join()
                        stopCapture.set(true)
                        capture.cancelAndJoin()
                        null
                    }
                } finally {
                    stopSpeech.set(true)
                    stopCapture.set(true)
                    runCatching { playback?.cancel() }
                    runCatching { capture?.cancel() }
                    withContext(kotlinx.coroutines.NonCancellable) {
                        runCatching { playback?.join() }
                        runCatching { capture?.join() }
                        runCatching {
                            if (manager.mode == AudioManager.MODE_IN_COMMUNICATION) manager.mode = previousMode
                        }
                    }
                }
            }

        fun close() {
            if (closed) return
            closed = true
            runCatching { tts.stop() }
            runCatching { tts.shutdown() }
        }
    }
}
