package com.dominion.mobile.data

import com.dominion.mobile.data.model.Message
import com.dominion.mobile.data.model.MessageSender
import com.dominion.mobile.data.model.MessageStatus
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
        val optimistic = message("local_1", MessageSender.ME, "hello", status = MessageStatus.SENDING)
        val accepted = message("server_9", MessageSender.ME, "hello", clientId = "local_1")
        val result = MessageSync.reconcile(listOf(optimistic), listOf(accepted, accepted))
        assertEquals(1, result.size)
        assertEquals("server_9", result.single().id)
        assertEquals(MessageStatus.SENT, result.single().status)
    }

    @Test fun unrelatedPendingMessageSurvivesHistorySync() {
        val pending = message("local_2", MessageSender.ME, "next", createdAt = 4000L,
            status = MessageStatus.FAILED)
        val reply = message("server_reply", MessageSender.CHARACTER, "done", createdAt = 3000L)
        val result = MessageSync.reconcile(listOf(pending), listOf(reply))
        assertEquals(2, result.size)
        assertEquals("server_reply", result.first().id)
        assertEquals(MessageStatus.FAILED, result.last().status)
    }

    @Test fun emptyServerListPreservesLocalMessages() {
        val local = listOf(message("m", MessageSender.ME, "hi"))
        assertEquals(local, MessageSync.reconcile(local, emptyList()))
        assertTrue(MessageSync.reconcile(emptyList(), emptyList()).isEmpty())
    }
}
