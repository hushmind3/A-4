package com.dominion.mobile.platform.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Lightweight audio decoder only; no speech model, inference or persistent audio. */
object CloudVoicePlayback {
    suspend fun play(context:Context,mp3:ByteArray){
        require(mp3.size in 64..3000000)
        val temporary=File.createTempFile("dominion-cloud-voice-", ".mp3",context.cacheDir)
        try{
            withContext(Dispatchers.IO){temporary.writeBytes(mp3)}
            withContext(Dispatchers.Main.immediate){
                val player=MediaPlayer()
                val manager=context.getSystemService(AudioManager::class.java)
                val audio=AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA).build()
                val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(audio).setOnAudioFocusChangeListener{}.build()
                var obtained=false
                try{
                    suspendCancellableCoroutine<Unit>{continuation->
                        try{
                            check(manager.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
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
                    if(obtained)manager.abandonAudioFocusRequest(focus)
                }
            }
        }finally{temporary.delete()}
    }
}
