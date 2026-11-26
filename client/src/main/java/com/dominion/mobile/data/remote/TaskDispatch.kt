package com.dominion.mobile.data.remote

import com.dominion.mobile.data.model.TaskItem
import java.util.UUID

object TaskDispatch {
    fun roomId(id: String): String = UUID.nameUUIDFromBytes(("mobile-task:" + id).toByteArray(Charsets.UTF_8)).toString()
    fun messageNonce(id: String): String = roomId(id)
    fun prompt(task: TaskItem): String = buildString {
        append("다음 작업을 실제로 수행하고 실행 결과와 확인 근거를 보고하세요. 필요한 권한은 사용자에게 요청하세요.\n")
        append(task.title.trim())
        task.requestDetails?.takeIf { it.isNotBlank() }?.let { append("\n\n요청 상세 및 실행 범위:\n").append(it) }
        if (task.due.isNotBlank()) append("\n희망 기한: ").append(task.due)
        task.steps.forEach { append("\n- ").append(it.text) }
    }.take(32000)

    fun matches(task: TaskItem, job: EngineJob): Boolean =
        !task.engineJobId.isNullOrBlank() && job.id == task.engineJobId &&
        !task.engineSessionId.isNullOrBlank() && job.sessionId == task.engineSessionId &&
        job.input?.roomId == task.engineRoomId &&
        !task.engineMessageId.isNullOrBlank() &&
        (job.input?.messageId ?: job.input?.sourceMessageId) == task.engineMessageId &&
        job.input?.input == task.enginePrompt && !job.status.isNullOrBlank()
}
