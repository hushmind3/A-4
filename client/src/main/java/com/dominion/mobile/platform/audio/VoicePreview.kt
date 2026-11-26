package com.dominion.mobile.platform.audio

import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.dominion.mobile.data.model.Lang
import com.dominion.mobile.data.model.VoiceTone
import java.util.Locale
import java.util.UUID

/** Uses the installed device TTS service only for preview. The engine's call voice
 * remains controlled by its media agent, not by this local preview. */
@Composable
fun rememberVoicePreview(lang: Lang): (VoiceTone, () -> Unit) -> Unit {
    val context = LocalContext.current.applicationContext
    var engine by remember(context) { mutableStateOf<TextToSpeech?>(null) }
    var ready by remember(context) { mutableStateOf(false) }
    DisposableEffect(context) {
        var disposed = false
        val main = Handler(Looper.getMainLooper())
        val tts = TextToSpeech(context) { status ->
            main.post { if (!disposed) ready = status == TextToSpeech.SUCCESS }
        }
        engine = tts
        onDispose {
            disposed = true
            ready = false
            engine = null
            tts.stop()
            tts.shutdown()
        }
    }
    return { tone, onFailure ->
        val tts = engine
        if (!ready || tts == null) onFailure()
        else {
            val locale = if (lang == Lang.KO) Locale.KOREAN else Locale.US
            val (pitch, rate) = when (tone) {
                VoiceTone.SOFT -> 1.04f to 0.92f
                VoiceTone.BRIGHT -> 1.16f to 1.07f
                VoiceTone.CALM -> 0.88f to 0.84f
            }
            val phrase = if (lang == Lang.KO) "안녕하세요. 함께 이야기하고 필요한 일을 도와드릴게요."
                else "Hello. I'm here to talk with you and help with your tasks."
            val result = runCatching {
                val language = tts.setLanguage(locale)
                check(language >= TextToSpeech.LANG_AVAILABLE)
                check(tts.setPitch(pitch) == TextToSpeech.SUCCESS)
                check(tts.setSpeechRate(rate) == TextToSpeech.SUCCESS)
                tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString()) == TextToSpeech.SUCCESS
            }.getOrDefault(false)
            if (!result) onFailure()
        }
    }
}
