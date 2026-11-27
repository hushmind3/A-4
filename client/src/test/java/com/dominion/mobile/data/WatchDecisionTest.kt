package com.dominion.mobile.data
import com.dominion.mobile.data.model.TaskItem
import com.dominion.mobile.data.remote.*
import org.junit.Assert.*
import org.junit.Test
class WatchDecisionTest {
    private val task = TaskItem("t","watch","a",engineRoomId="r",engineJobId="j",engineSessionId="s",
        engineMessageId="m",enginePrompt="p",watchCondition="new notice")
    private fun job(text:String, evidence:Int) = EngineJob("j","completed","s",EngineJobInput("r","p","m"),
        result=NativeJobResult("r","s","completed",listOf(NativeJobOutput("a","agent",text,"reply",evidence,null)),null))
    @Test fun watchRequiresExactReceiptAndRecordedEvidence() {
        assertEquals(WatchDecision.MATCH,WatchDecision.evaluate(task,job("WATCH_MATCH\nsource",1)))
        assertEquals(WatchDecision.NO_MATCH,WatchDecision.evaluate(task,job("WATCH_NO_MATCH\nsource",1)))
        assertEquals(WatchDecision.UNKNOWN,WatchDecision.evaluate(task,job("WATCH_MATCH",0)))
        assertEquals(WatchDecision.UNKNOWN,WatchDecision.evaluate(task,job("WATCH_MATCH",1).copy(id="wrong")))
        assertEquals(WatchDecision.UNKNOWN,WatchDecision.evaluate(task,job("maybe WATCH_MATCH",1)))
    }
}
