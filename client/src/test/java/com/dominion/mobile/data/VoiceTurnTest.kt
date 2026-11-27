package com.dominion.mobile.data

import com.dominion.mobile.data.model.*
import org.junit.Assert.*
import org.junit.Test

class VoiceTurnTest {
    @Test fun onlyConfirmedJobAndExactSourceAreSpoken() {
        val owner = Message("owner","room",MessageSender.USER,createdAt = 100,
            clientMessageId = "nonce",jobId = "job",jobStatus = "completed")
        val reply = Message("reply","room",MessageSender.ASSISTANT,"answer",createdAt = 101,replyTo = "owner")
        val rows = listOf(owner,reply,reply.copy(id = "foreign",conversationId = "other"),
            reply.copy(id = "unrelated",replyTo = "other"),reply.copy(id = "old",createdAt = 99))
        assertEquals(owner,VoiceTurn.owner(rows,"room","nonce"))
        assertEquals(listOf(reply),VoiceTurn.replies(rows,"room",owner))
        assertTrue(VoiceTurn.replies(rows,"room",owner.copy(jobStatus = "running")).isEmpty())
    }
    @Test fun endpointRequiresSustainedSpeechAndSilence() {
        val endpoint = SpeechEndpoint()
        endpoint.accept(ShortArray(320*7){2000},0,320*7)
        assertFalse(endpoint.hasSpeech)
        endpoint.accept(ShortArray(320){2000},0,320)
        assertTrue(endpoint.hasSpeech)
        endpoint.accept(ShortArray(320*44),0,320*44)
        assertFalse(endpoint.finished)
        endpoint.accept(ShortArray(320),0,320)
        assertTrue(endpoint.finished)
    }
    @Test fun silenceDoesNotCreateVoiceRequest() {
        val detector = SpeechEndpoint()
        val silence = ShortArray(160000)
        detector.accept(silence,0,silence.size)
        assertTrue(detector.noSpeechTimeout)
        assertFalse(detector.hasSpeech)
    }
}
