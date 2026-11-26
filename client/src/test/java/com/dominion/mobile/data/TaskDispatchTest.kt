package com.dominion.mobile.data

import com.dominion.mobile.data.model.TaskItem
import com.dominion.mobile.data.remote.*
import org.junit.Assert.*
import org.junit.Test

class TaskDispatchTest {
    @Test fun identitySurvivesRetry() {
        assertEquals(TaskDispatch.roomId("task1"), TaskDispatch.roomId("task1"))
        assertNotEquals(TaskDispatch.roomId("task1"), TaskDispatch.roomId("task2"))
    }
    @Test fun onlyOriginalJobCanUpdateTaskStatus() {
        val task = TaskItem("t","title","a", engineRoomId = "r", engineSessionId = "s",
            engineJobId = "j", engineMessageId = "m", enginePrompt = "work")
        val job = EngineJob("j","completed","s",EngineJobInput("r","work","m"))
        assertTrue(TaskDispatch.matches(task, job))
        assertFalse(TaskDispatch.matches(task, job.copy(sessionId = "other")))
        assertFalse(TaskDispatch.matches(task, job.copy(input = job.input?.copy(messageId = "other"))))
    }
}
