package com.dominion.mobile.data

import com.dominion.mobile.data.model.*
import org.junit.Assert.*
import org.junit.Test

class RoomHistoryTest {
    @Test fun resetDoesNotMergeOldServerTranscriptIntoNewSession() {
        val old = Message("old","r",MessageSender.USER)
        val pending = Message("pending","r",MessageSender.USER,status = MessageStatus.FAILED)
        val room = Conversation("r","a",messages = listOf(old,pending),sessionId = "s1")
        val fresh = RoomHistory.reconcile(room,"s2",emptyList())
        assertEquals("s2",fresh.sessionId)
        assertEquals(listOf(pending),fresh.messages)
        assertEquals(room.messages,RoomHistory.reconcile(room,"s1",emptyList()).messages)
    }
}
