package com.dominion.mobile.data

import com.dominion.mobile.data.model.*

/** Never speak an unrelated concurrent job, an old transcript, or an unverified response. */
object VoiceTurn {
    fun owner(messages: List<Message>, roomId: String, nonce: String): Message? =
        messages.firstOrNull { it.conversationId == roomId && it.sender == MessageSender.USER &&
            (it.id == nonce || it.clientMessageId == nonce) }

    fun replies(messages: List<Message>, roomId: String, owner: Message): List<Message> {
        if (owner.conversationId != roomId || owner.sender != MessageSender.USER ||
            owner.status != MessageStatus.SENT || owner.jobId.isNullOrBlank() ||
            owner.jobStatus != "completed") return emptyList()
        return messages.filter { it.conversationId == roomId &&
            it.sender == MessageSender.ASSISTANT && it.status == MessageStatus.SENT &&
            it.replyTo == owner.id && it.text.isNotBlank() && it.createdAt >= owner.createdAt }
            .distinctBy { it.id }
    }
}
