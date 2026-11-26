package com.dominion.mobile.data

import com.dominion.mobile.data.model.Message
import com.dominion.mobile.data.model.MessageSender
import com.dominion.mobile.data.model.MessageStatus
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSyncTest {
    private fun message(
        id: String,
        sender: MessageSender,
        text: String,
        createdAt: Long = 1000L,
        status: MessageStatus = MessageStatus.SENT,
        clientId: String? = null
    ) = Message(
        id = id,
        conversationId = "conv_1",
        sender = sender,
        text = text,
        createdAt = createdAt,
        status = status,
        clientMessageId = clientId
    )

    @Test fun serverAcknowledgementReplacesOptimisticMessage() {
        val optimistic = message("local_1", MessageSender.USER, "hello", status = MessageStatus.SENDING)
        val accepted = message("server_9", MessageSender.USER, "hello", clientId = "local_1")
        val result = MessageSync.reconcile(listOf(optimistic), listOf(accepted, accepted))
        assertEquals(1, result.size)
        assertEquals("server_9", result.single().id)
        assertEquals(MessageStatus.SENT, result.single().status)
    }

    @Test fun unrelatedPendingMessageSurvivesHistorySync() {
        val pending = message("local_2", MessageSender.USER, "next", createdAt = 4000L,
            status = MessageStatus.FAILED)
        val reply = message("server_reply", MessageSender.ASSISTANT, "done", createdAt = 3000L)
        val result = MessageSync.reconcile(listOf(pending), listOf(reply))
        assertEquals(2, result.size)
        assertEquals("server_reply", result.first().id)
        assertEquals(MessageStatus.FAILED, result.last().status)
    }

    @Test fun emptyServerListPreservesLocalMessages() {
        val local = listOf(message("m", MessageSender.USER, "hi"))
        assertEquals(local, MessageSync.reconcile(local, emptyList()))
        assertTrue(MessageSync.reconcile(emptyList(), emptyList()).isEmpty())
    }
    @Test fun messageRolesUseStandardNamesWithoutLosingStoredHistory() {
        val gson = Gson()
        assertEquals("\"USER\"", gson.toJson(MessageSender.USER))
        assertEquals("\"ASSISTANT\"", gson.toJson(MessageSender.ASSISTANT))
        assertEquals(MessageSender.USER, gson.fromJson("\"ME\"", MessageSender::class.java))
        assertEquals(MessageSender.ASSISTANT, gson.fromJson("\"CHARACTER\"", MessageSender::class.java))
    }
}
