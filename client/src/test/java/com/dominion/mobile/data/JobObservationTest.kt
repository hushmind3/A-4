package com.dominion.mobile.data

import com.dominion.mobile.data.model.*
import com.dominion.mobile.data.remote.*
import org.junit.Assert.*
import org.junit.Test

class JobObservationTest {
    private val job = EngineJob("job", "completed", "session",
        EngineJobInput(roomId = "room", input = "request", messageId = "message"))
    private val task = TaskItem("task", "Title", "agent", engineRoomId = "room",
        engineJobId = "job", engineSessionId = "session", engineMessageId = "message",
        engineStatus = "running", enginePrompt = "request")
    private val message = Message("message", "room", MessageSender.USER, jobId = "job", jobStatus = "running")
    private val state = AppState(tasks = listOf(task), conversations = listOf(
        Conversation("room", "agent", messages = listOf(message), sessionId = "session")))

    @Test fun restartDeduplicatesTaskAndConversationWatch() {
        assertEquals(setOf(JobWatch("room", "job")), pendingJobWatches(state))
    }

    @Test fun verifiedCompletionUpdatesBothCachedRecordsWithoutMarkingManualChecklistDone() {
        val result = applyObservedJob(state, "room", "job", job)
        assertEquals("completed", result.tasks.single().engineStatus)
        assertFalse(result.tasks.single().done)
        assertEquals("completed", result.conversations.single().messages.single().jobStatus)
        assertTrue(pendingJobWatches(result).isEmpty())
    }

    @Test fun foreignRoomAndMismatchedSessionCannotUpdateRecords() {
        assertEquals(state, applyObservedJob(state, "other", "job", job))
        assertEquals(state, applyObservedJob(state, "room", "job", job.copy(sessionId = "other")))
        assertEquals(state, applyObservedJob(state, "room", "other", job))
    }

    @Test fun sourceMismatchCannotUpdateAnyRecord() {
        val different = job.copy(input = job.input?.copy(messageId = "other"))
        assertEquals(state, applyObservedJob(state, "room", "job", different))
    }

    @Test fun staleRunningPollCannotUndoCompletion() {
        val completed = applyObservedJob(state, "room", "job", job)
        assertEquals(completed, applyObservedJob(completed, "room", "job", job.copy(status = "running")))
    }

    @Test fun unknownStatusAndIncompleteTaskDoNotCreateFalseCompletion() {
        assertEquals(state, applyObservedJob(state, "room", "job", job.copy(status = "unknown")))
        val incomplete = AppState(tasks = listOf(task.copy(engineMessageId = null)))
        assertTrue(pendingJobWatches(incomplete).isEmpty())
        assertEquals(incomplete, applyObservedJob(incomplete, "room", "job", job))
    }
}
