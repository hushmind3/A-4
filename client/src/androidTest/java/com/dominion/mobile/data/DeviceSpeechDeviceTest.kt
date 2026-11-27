package com.dominion.mobile.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dominion.mobile.platform.audio.DeviceSpeech
import com.dominion.mobile.platform.audio.VoiceDelivery
import com.dominion.mobile.platform.audio.VoiceTtsMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class DeviceSpeechDeviceTest {
    @Test(timeout = 30_000)
    fun installedDeviceTtsCanSpeakFallbackReply() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val availability = DeviceSpeech.availability(context)
        assertTrue("No usable device TTS engine", availability.available)
        val session = DeviceSpeech.open(context, "ko")
        assertNotNull("Korean device TTS unavailable", session)
        val ready = requireNotNull(session)
        try {
            assertTrue(ready.speak("도미니온 음성 테스트입니다.", AtomicBoolean(false),
                communication = false))
        } finally {
            ready.close()
        }
    }

    @Test fun fallbackPolicyNeverPrefersDeviceWhenCloudIsAvailable() {
        assertEquals(VoiceTtsMode.CLOUD, VoiceDelivery.choose(true, true))
        assertEquals(VoiceTtsMode.DEVICE, VoiceDelivery.choose(false, true))
        assertEquals(VoiceTtsMode.NONE, VoiceDelivery.choose(false, false))
    }
}
