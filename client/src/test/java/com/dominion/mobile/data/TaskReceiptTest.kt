package com.dominion.mobile.data

import com.dominion.mobile.data.model.TaskItem
import com.dominion.mobile.data.remote.*
import org.junit.Assert.*
import org.junit.Test

class TaskReceiptTest {
    private val task = TaskItem("t","work","a",engineRoomId="r",engineSessionId="s",
        engineMessageId="m",engineJobId="j",enginePrompt="prompt")
    private val job = EngineJob("j","completed","s",EngineJobInput("r","prompt","m"),
        result=NativeJobResult("r","s","completed",listOf(NativeJobOutput("a","agent","answer","reply",2,null)),null))
    @Test fun exactJobReceiptSurvivesRoomReset() {
        val receipt = TaskReceipt.from(task,job,123)
        assertEquals("reply",receipt.outputs.single().messageId)
        assertEquals(2,receipt.outputs.single().evidenceCount)
        assertEquals(123L,receipt.checkedAt)
    }
    @Test fun mismatchedJobOrResultIsRejected() {
        assertTrue(runCatching { TaskReceipt.from(task,job.copy(id="other")) }.isFailure)
        assertTrue(runCatching { TaskReceipt.from(task,job.copy(result=job.result?.copy(sessionId="other"))) }.isFailure)
    }
}
