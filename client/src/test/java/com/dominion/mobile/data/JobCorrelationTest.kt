package com.dominion.mobile.data

import com.dominion.mobile.data.model.Message
import com.dominion.mobile.data.model.MessageSender
import com.dominion.mobile.data.model.MessageStatus
import com.dominion.mobile.data.remote.*
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class JobCorrelationTest {
    @Test fun statusRequiresExactRoomSessionAndOwnerMessage() {
        val first = Message("one", "r", MessageSender.USER)
        val second = Message("two", "r", MessageSender.USER)
        val job = EngineJob("j", "running", "s", EngineJobInput("r", messageId = "one"))
        val rows = correlateRoomJobs(listOf(first, second), "r", "s",
            listOf(job.copy(sessionId = "other", status = "completed"), job))
        assertEquals("running", rows[0].jobStatus)
        assertEquals("j", rows[0].jobId)
        assertNull(rows[1].jobStatus)
    }
    @Test fun lateAcknowledgementRetainsJobIdentityAfterHistoryWinsRace() {
        val local = Message("local", "r", MessageSender.USER, status = MessageStatus.SENDING)
        val server = local.copy(id = "saved", clientMessageId = "local", status = MessageStatus.SENT)
        val afterHistory = MessageSync.reconcile(listOf(local), listOf(server))
        val afterAck = MessageSync.reconcile(afterHistory, listOf(server.copy(jobId = "j", jobStatus = "queued")))
        assertEquals(1, afterAck.size)
        assertEquals("j", afterAck.single().jobId)
        assertEquals("queued", afterAck.single().jobStatus)
    }
    @Test fun nativeReplyRetainsActualAgentAndSourceMessage() {
        val row = Gson().fromJson("""{"id":"m","role":"assistant","agent_id":"a",
            "content":"answer","metadata":{"replyToMessageId":"owner-1"}}""", NativeRoomMessage::class.java)
        val message = requireNotNull(row.toDomain("r"))
        assertEquals("a", message.authorAgentId)
        assertEquals("owner-1", message.replyTo)
    }
}
