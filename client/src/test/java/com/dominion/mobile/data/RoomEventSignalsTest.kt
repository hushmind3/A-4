package com.dominion.mobile.data

import com.dominion.mobile.data.remote.affectsRoom
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class RoomEventSignalsTest {
    private fun matches(json: String) =
        affectsRoom(JsonParser.parseString(json).asJsonObject, "r1", "s1")

    @Test fun onlyExactRoomAndSessionInvalidateTranscript() {
        assertTrue(matches("""{"type":"message","message":{"session_id":"s1","metadata":{"roomId":"r1"}}}"""))
        assertFalse(matches("""{"type":"message","message":{"session_id":"s2","metadata":{"roomId":"r1"}}}"""))
        assertFalse(matches("""{"type":"message","message":{"session_id":"s1","metadata":{"roomId":"r2"}}}"""))
        assertTrue(matches("""{"type":"record","kind":"room","record":{"id":"r1"}}"""))
    }
    @Test fun approvalsAndJobsRemainScoped() {
        assertTrue(matches("""{"type":"approval","approval":{"sessionId":"s1"}}"""))
        assertFalse(matches("""{"type":"approval","approval":{"sessionId":"s2"}}"""))
        assertTrue(matches("""{"type":"record","kind":"job","record":{"sessionId":"s1","input":{"roomId":"r1"}}}"""))
        assertFalse(matches("""{"type":"record","kind":"job","record":{"sessionId":"s1","input":{"roomId":"r2"}}}"""))
        assertFalse(matches("""{"type":"record","kind":"memory","record":{"sessionId":"s1"}}"""))
    }
}
