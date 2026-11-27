package com.dominion.mobile.platform.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.AcousticEchoCanceler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Lightweight audio decoder only; no speech model, inference or persistent audio. */
object CloudVoicePlayback {
    suspend fun play(context:Context,mp3:ByteArray,communication:Boolean=false){
        require(mp3.size in 64..3000000)
        val temporary=File.createTempFile("dominion-cloud-voice-", ".mp3",context.cacheDir)
        try{
            withContext(Dispatchers.IO){temporary.writeBytes(mp3)}
            withContext(Dispatchers.Main.immediate){
                val player=MediaPlayer()
                val manager=context.getSystemService(AudioManager::class.java)
                val audio=AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(if(communication) AudioAttributes.USAGE_VOICE_COMMUNICATION
                        else AudioAttributes.USAGE_MEDIA).build()
                var focus:AudioFocusRequest?=null
                var obtained=false
                try{
                    suspendCancellableCoroutine<Unit>{continuation->
                        try{
                            val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                                .setAudioAttributes(audio).setOnAudioFocusChangeListener{change->
                                    if(change<0&&continuation.isActive)
                                        continuation.resumeWithException(IllegalStateException("Audio focus lost"))
                                }.build()
                            focus=request
                            check(manager.requestAudioFocus(request)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                            obtained=true
                            player.setAudioAttributes(audio)
                            player.setDataSource(temporary.absolutePath)
                            player.setOnPreparedListener{
                                if(continuation.isActive)it.start()
                            }
                            player.setOnCompletionListener{
                                if(continuation.isActive)continuation.resume(Unit)
                            }
                            player.setOnErrorListener{_,_,_->
                                if(continuation.isActive)
                                    continuation.resumeWithException(IllegalStateException("Cloud voice playback failed"))
                                true
                            }
                            player.prepareAsync()
                        }catch(failure:Exception){
                            if(continuation.isActive)continuation.resumeWithException(failure)
                        }
                    }
                }finally{
                    player.release()
                    if(obtained)focus?.let{manager.abandonAudioFocusRequest(it)}
                }
            }
        }finally{temporary.delete()}
    }

    /**
     * Play a cloud MP3 while capturing the microphone with platform echo
     * cancellation. Returns the captured 16 kHz WAV only when sustained speech
     * interrupted playback. If AEC is unavailable the reply finishes normally
     * and the caller keeps the manual interruption control.
     */
    suspend fun playListening(context: Context, mp3: ByteArray): ByteArray? = coroutineScope {
        require(mp3.size in 64..3000000)
        if (!AcousticEchoCanceler.isAvailable()) {
            play(context, mp3, communication = false)
            return@coroutineScope null
        }
        val manager = context.getSystemService(AudioManager::class.java)
        val previousMode = manager.mode
        val heard = AtomicBoolean(false)
        val stopCapture = AtomicBoolean(false)
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
            playback = launch { play(context, mp3, communication = true) }
            while (playback.isActive && !heard.get()) delay(40)
            if (heard.get()) {
                playback.cancelAndJoin()
                capture.await()
            } else {
                playback.join()
                stopCapture.set(true)
                capture.cancelAndJoin()
                null
            }
        } finally {
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
}
