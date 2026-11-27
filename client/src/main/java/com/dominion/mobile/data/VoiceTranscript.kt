package com.dominion.mobile.data

import com.dominion.mobile.data.model.*

data class TranscriptLine(val messageId: String, val role: String, val text: String)
object VoiceTranscript {
    fun lines(room: Conversation, session: String, nonces: List<String>): List<TranscriptLine> {
        require(session.isNotBlank() && room.sessionId == session)
        val owners = nonces.distinct().mapNotNull { VoiceTurn.owner(room.messages, room.id, it) }
            .filter { it.status == MessageStatus.SENT && it.text.isNotBlank() }
        return owners.flatMap { owner ->
            listOf(TranscriptLine(owner.id, "사용자", owner.text)) +
                VoiceTurn.replies(room.messages, room.id, owner).map {
                    TranscriptLine(it.id, "AI", it.text)
                }
        }.distinctBy { it.messageId }
    }
    fun text(lines: List<TranscriptLine>): String = lines.joinToString("\n\n") { it.role + ": " + it.text }
}
