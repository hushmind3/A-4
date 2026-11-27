package com.dominion.mobile.data.remote

import com.dominion.mobile.data.model.TaskItem
import com.google.gson.JsonElement

data class NativeJobResult(val roomId: String?, val sessionId: String?, val status: String?,
    val outputs: List<NativeJobOutput>?, val pauseReason: String?,
    val cloudEvidence: List<CloudToolReceipt>? = null)

data class NativeJobOutput(val agentId: String?, val name: String?, val content: String?,
    val messageId: String?, val evidenceCount: Int?, val toolCalls: JsonElement?)

data class NativeExecutionIdentity(val id: String?, val sessionId: String?, val status: String?, val taskOutcome: String?)

data class TaskEvidence(val execution: NativeExecutionIdentity, val operations: List<EngineOperation>) {
    companion object {
        fun executionId(task: TaskItem, result: TaskReceipt, replyId: String, rows: List<NativeRoomMessage>): String {
            require(result.outputs.any { it.messageId == replyId })
            val row = rows.single { it.id == replyId && it.session_id == task.engineSessionId &&
                it.role == "assistant" && it.metadata?.replyToMessageId == task.engineMessageId }
            return requireNotNull(row.metadata?.executionId).also { require(it.isNotBlank()) }
        }
        fun verified(id: String, session: String, execution: NativeExecutionIdentity, operations: List<EngineOperation>): TaskEvidence {
            check(id.isNotBlank() && session.isNotBlank() && execution.id == id && execution.sessionId == session)
            check(operations.all { it.runId == id })
            return TaskEvidence(execution, operations)
        }
    }
}

data class TaskReceipt(val jobId: String, val status: String, val outputs: List<NativeJobOutput>,
    val reason: String?, val checkedAt: Long, val error: String?,
    val cloudEvidence: List<CloudToolReceipt>? = null) {
    companion object {
        fun from(task: TaskItem, job: EngineJob, now: Long = System.currentTimeMillis()): TaskReceipt {
            check(TaskDispatch.matches(task, job))
            val result = job.result
            if (result != null) {
                check(result.roomId == task.engineRoomId && result.sessionId == task.engineSessionId)
            }
            val outputs = result?.outputs.orEmpty().filter {
                !it.messageId.isNullOrBlank() && !it.content.isNullOrBlank()
            }.distinctBy { it.messageId }
            return TaskReceipt(requireNotNull(job.id), requireNotNull(job.status), outputs,
                result?.pauseReason, now, job.error?.takeUnless { it.isJsonNull }?.toString(),
                result?.cloudEvidence?.takeIf { task.engineJobId?.startsWith("cloud:") == true })
        }
    }
}
