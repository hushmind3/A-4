package com.dominion.mobile.data.remote

import com.dominion.mobile.data.model.TaskItem
import com.google.gson.JsonElement

data class NativeJobResult(val roomId: String?, val sessionId: String?, val status: String?,
    val outputs: List<NativeJobOutput>?, val pauseReason: String?)

data class NativeJobOutput(val agentId: String?, val name: String?, val content: String?,
    val messageId: String?, val evidenceCount: Int?, val toolCalls: JsonElement?)

data class TaskReceipt(val jobId: String, val status: String, val outputs: List<NativeJobOutput>,
    val reason: String?, val checkedAt: Long, val error: String?) {
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
                result?.pauseReason, now, job.error?.takeUnless { it.isJsonNull }?.toString())
        }
    }
}
