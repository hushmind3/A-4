package com.dominion.mobile.data

import com.dominion.mobile.data.model.Message
import com.dominion.mobile.data.model.MessageStatus

/**
 * Merge server history into the local conversation without discarding in-flight
 * messages. A server may replace a temporary id using client_message_id.
 */
object MessageSync {
    fun reconcile(local: List<Message>, server: List<Message>): List<Message> {
        val merged = local.toMutableList()
        for (remote in server) {
            if (remote.id.isBlank()) continue
            val existingIndex = merged.indexOfFirst {
                it.id == remote.id ||
                    (!remote.clientMessageId.isNullOrBlank() && it.id == remote.clientMessageId)
            }
            if (existingIndex >= 0) {
                val original = merged[existingIndex]
                merged[existingIndex] = remote.copy(
                    status = MessageStatus.SENT,
                    card = remote.card ?: original.card,
                    kind = if (remote.card == null && original.card != null) original.kind else remote.kind,
                    attachmentIds = remote.attachmentIds ?: original.attachmentIds,
                    replyTo = remote.replyTo ?: original.replyTo,
                    jobId = remote.jobId ?: original.jobId,
                    clientMessageId = remote.clientMessageId ?: original.clientMessageId,
                    modelId = remote.modelId ?: original.modelId
                )
            } else {
                merged.add(remote.copy(status = MessageStatus.SENT))
            }
        }
        return merged.sortedWith(compareBy<Message> { it.createdAt }.thenBy { it.id })
    }
}
