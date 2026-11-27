package com.dominion.mobile.data
import com.dominion.mobile.data.model.TaskItem
import com.dominion.mobile.data.remote.*
import org.junit.Assert.*
import org.junit.Test

class TaskEvidenceTest {
    @Test fun exactSessionReplyAndRunOnly() {
        val task = TaskItem("t","title","a",engineSessionId="s",engineMessageId="m")
        val receipt = TaskReceipt("j","completed",listOf(NativeJobOutput("a","agent","response","reply",1,null)),null,0,null)
        val message = NativeRoomMessage("reply","assistant","response",null,NativeRoomMetadata(null,null,"m","run"),
            session_id="s")
        assertEquals("run",TaskEvidence.executionId(task,receipt,"reply",listOf(message)))
        assertTrue(runCatching { TaskEvidence.executionId(task,receipt,"reply",listOf(message.copy(session_id="other"))) }.isFailure)
        val execution = NativeExecutionIdentity("run","s","completed",null)
        val op = EngineOperation("op","web","completed",null,null,"run")
        assertEquals(listOf(op),TaskEvidence.verified("run","s",execution,listOf(op)).operations)
        assertTrue(runCatching { TaskEvidence.verified("run","s",execution,listOf(op.copy(runId="foreign"))) }.isFailure)
    }
}
