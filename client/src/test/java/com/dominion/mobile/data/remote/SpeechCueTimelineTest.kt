package com.dominion.mobile.data.remote

import org.junit.Assert.*
import org.junit.Test

class SpeechCueTimelineTest {
    @Test fun indexedByActualPlayerTime() {
        val t=SpeechCueTimeline(listOf(SpeechCue(100,200,"a",0.8f),SpeechCue(250,400,"o",0.4f)),500)
        assertNull(t.at(0))
        assertEquals("a",t.at(100)?.viseme)
        assertNull(t.at(200))
        assertEquals("o",t.at(399)?.viseme)
        assertNull(t.at(400))
        assertNull(t.at(-1))
    }
    @Test fun rejectsMisalignedOrMalformedCues() {
        assertTrue(runCatching { SpeechCueTimeline(listOf(SpeechCue(0,600,"a",1f)),500) }.isFailure)
        assertTrue(runCatching { SpeechCueTimeline(listOf(SpeechCue(0,100,"a",1f),SpeechCue(99,200,"e",1f)),500) }.isFailure)
        assertTrue(runCatching { SpeechCueTimeline(listOf(SpeechCue(0,100,"unknown",1f)),500) }.isFailure)
    }
}
