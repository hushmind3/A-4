package com.dominion.mobile.data.remote

import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.data.model.MessageSender

internal data class JobWatch(val roomId: String, val jobId: String)

private val terminalJobStatuses = setOf("completed", "failed", "cancelled")
private val observedJobStatuses = terminalJobStatuses + setOf(
    "queued", "running", "needs_review", "paused", "interrupted", "stopped"
)

/** Re-register known unfinished jobs after a restart or return to an engine.
 * No new server execution is created; WorkManager uses stable unique keys.
 */
internal fun pendingJobWatches(state: AppState): Set<JobWatch> = buildSet {
    state.tasks.forEach { task ->
        val room = task.engineRoomId
        val job = task.engineJobId
        if (!room.isNullOrBlank() && !job.isNullOrBlank() &&
            task.engineStatus !in terminalJobStatuses && !task.engineSessionId.isNullOrBlank() &&
            !task.engineMessageId.isNullOrBlank()) add(JobWatch(room, job))
    }
    state.conversations.forEach { room ->
        room.messages.forEach { message ->
            val job = message.jobId
            if (message.sender == MessageSender.USER && message.conversationId == room.id &&
                !job.isNullOrBlank() && message.jobStatus !in terminalJobStatuses)
                add(JobWatch(room.id, job))
        }
    }
}

/** Update only records with matching server provenance; never infer task completion
 * from an assistant message or replace a terminal state with a stale poll.
 */
internal fun applyObservedJob(state: AppState, roomId: String, jobId: String, job: EngineJob): AppState {
    val status = job.status ?: return state
    if (!job.belongsToRoom(roomId, jobId) || status !in observedJobStatuses) return state
    val source = job.input?.messageId ?: job.input?.sourceMessageId
    return state.copy(
        tasks = state.tasks.map { task ->
            if (TaskDispatch.matches(task, job) && task.engineStatus !in terminalJobStatuses)
                task.copy(engineStatus = status)
            else task
        },
        conversations = state.conversations.map { room ->
            if (room.id != roomId || room.sessionId != job.sessionId || source.isNullOrBlank()) room
            else room.copy(messages = room.messages.map { message ->
                if (message.sender == MessageSender.USER && message.conversationId == roomId &&
                    message.id == source && message.jobId == jobId &&
                    message.jobStatus !in terminalJobStatuses)
                    message.copy(jobStatus = status)
                else message
            })
        }
    )
}
