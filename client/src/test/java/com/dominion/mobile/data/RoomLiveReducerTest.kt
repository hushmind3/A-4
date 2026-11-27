package com.dominion.mobile.data
import com.dominion.mobile.data.remote.RoomLiveReducer
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
class RoomLiveReducerTest {
    private fun event(seq: Int, payload: String, room: String = "r", session: String = "s") =
        JsonParser.parseString("""{"seq":$seq,"type":"room_event","roomId":"$room","sessionId":"$session","turnId":"t","agentId":"a","event":$payload}""").asJsonObject
    @Test fun realChunksAreJoinedWithoutReplayDuplication() {
        val reducer = RoomLiveReducer("r","s")
        assertNull(reducer.accept(event(1,"""{"type":"text","text":"unknown"}""")))
        reducer.accept(event(2,"""{"type":"turn_start","sourceMessageId":"m"}"""))
        reducer.accept(event(3,"""{"type":"text","text":"안녕"}"""))
        assertNull(reducer.accept(event(3,"""{"type":"text","text":"안녕"}""")))
        assertNull(reducer.accept(event(4,"""{"type":"text","text":"foreign"}""",session="other")))
        assertEquals("안녕하세요",reducer.accept(event(5,"""{"type":"text","text":"하세요"}"""))!!.turns.single().text)
        assertTrue(reducer.accept(event(6,"""{"type":"assistant_saved","messageId":"reply"}"""))!!.turns.isEmpty())
    }
    @Test fun toolRoundAndReasoningNotPresentedAsFinalAnswer() {
        val reducer = RoomLiveReducer("r","s")
        reducer.accept(event(1,"""{"type":"turn_start","sourceMessageId":"m"}"""))
        reducer.accept(event(2,"""{"type":"text","text":"draft"}"""))
        assertNull(reducer.accept(event(3,"""{"type":"reasoning","text":"private"}""")))
        assertEquals("",reducer.accept(event(4,"""{"type":"tool_start","name":"web"}"""))!!.turns.single().text)
        assertEquals("result",reducer.accept(event(5,"""{"type":"text","text":"result"}"""))!!.turns.single().text)
    }
}
