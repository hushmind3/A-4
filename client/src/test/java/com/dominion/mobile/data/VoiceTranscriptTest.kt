package com.dominion.mobile.data
import com.dominion.mobile.data.model.*
import org.junit.Assert.*
import org.junit.Test
class VoiceTranscriptTest {
    @Test fun transcriptDoesNotIncludeUnrelatedRoomMessages() {
        val owner = Message("u","r",MessageSender.USER,"hello",createdAt=1,clientMessageId="n",jobId="j",jobStatus="completed")
        val reply = Message("a","r",MessageSender.ASSISTANT,"answer",createdAt=2,replyTo="u")
        val room = Conversation(id="r",characterId="c",sessionId="s",messages=listOf(owner,reply,reply.copy(id="bad",replyTo="other")))
        assertEquals(listOf("u","a"),VoiceTranscript.lines(room,"s",listOf("n")).map { it.messageId })
        assertTrue(runCatching { VoiceTranscript.lines(room,"other",listOf("n")) }.isFailure)
    }
}
