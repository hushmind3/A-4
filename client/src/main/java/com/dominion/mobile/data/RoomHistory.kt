package com.dominion.mobile.data

import com.dominion.mobile.data.model.Conversation
import com.dominion.mobile.data.model.Message
import com.dominion.mobile.data.model.MessageStatus

object RoomHistory {
    fun reconcile(room: Conversation, session: String, server: List<Message>): Conversation {
        require(session.isNotBlank())
        // A reset from another client must not merge the archived transcript into
        // the new model context. Unsent local messages remain recoverable.
        val sameSession = room.sessionId == null || room.sessionId == session
        val local = if (sameSession) room.messages else room.messages.filter { it.status != MessageStatus.SENT }
        val merged = MessageSync.reconcile(local, server)
        return room.copy(sessionId = session, messages = merged,
            updatedAt = if (merged == room.messages) room.updatedAt else System.currentTimeMillis())
    }
}
