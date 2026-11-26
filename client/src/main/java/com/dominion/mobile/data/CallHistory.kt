package com.dominion.mobile.data

import com.dominion.mobile.data.model.*
import com.dominion.mobile.util.formatDuration

/** A local device receipt, not a generated agent message or server completion. */
object CallHistory {
    fun finish(state: AppState, endedAt: Long = System.currentTimeMillis()): AppState {
        val call = state.call ?: return state
        val connectedAt = call.connectedAt ?: return state.copy(call = null)
        val duration = formatDuration((endedAt - connectedAt).coerceAtLeast(0))
        val id = "call-receipt-${call.sessionId}"
        val title = if (call.hadVideo) "영상 통화 기록" else "음성 통화 기록"
        val receipt = Message(id = id, conversationId = call.conversationId,
            sender = MessageSender.SYSTEM, text = "기기 통화 기록", kind = MessageKind.CALL,
            createdAt = endedAt, card = MessageCard(CardKind.CALL, title, duration, "기기 기록"))
        val activity = ActivityItem(id, ActivityType.CALL, title,
            "${state.character(call.characterId)?.name ?: "DOMINION"} · 연결 후 경과 $duration", endedAt)
        return state.copy(call = null,
            conversations = state.conversations.map {
                if (it.id == call.conversationId && it.messages.none { message -> message.id == id })
                    it.copy(messages = it.messages + receipt, updatedAt = maxOf(it.updatedAt, endedAt)) else it
            },
            activity = if (state.activity.any { it.id == id }) state.activity else listOf(activity) + state.activity)
    }
}
