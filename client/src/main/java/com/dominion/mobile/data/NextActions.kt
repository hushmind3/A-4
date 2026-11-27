package com.dominion.mobile.data

import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.data.model.MessageSender
import com.dominion.mobile.data.model.MessageStatus

enum class NextActionKind { REVIEW, RETRY, FINISH_DRAFT, UPCOMING, CONTINUE_TASK, UNREAD }
data class NextAction(val key: String, val kind: NextActionKind, val title: String,
    val reason: String, val roomId: String?, val rank: Int, val time: Long)

/** Local, explainable suggestions from real saved state. No inference or hidden execution. */
object NextActions {
    fun build(state: AppState, now: Long): List<NextAction> {
        val results = mutableListOf<NextAction>()
        val rooms = state.conversations.filter { it.id !in state.hiddenRooms.orEmpty() &&
            state.character(it.characterId) != null && it.characterId !in state.settings.blockedUserIds }
        val validRooms = rooms.map { it.id }.toSet()
        fun roomId(id: String?) = id?.takeIf { it in validRooms }
        fun display(value: String, fallback: String) =
            if (state.settings.hideContent) fallback else value.take(80).ifBlank { fallback }

        state.tasks.filter { state.character(it.characterId) != null &&
            it.characterId !in state.settings.blockedUserIds }.forEach { task ->
            val room = roomId(task.engineRoomId)
            when (task.engineStatus) {
                "needs_review", "paused", "interrupted", "stopped", "failed" ->
                    results += NextAction("task:" + task.id, NextActionKind.REVIEW,
                        display(task.title,"확인할 작업"), "본체에 기록된 작업 상태: " + task.engineStatus,
                        room, 100, task.createdAt)
                "queued", "running" ->
                    results += NextAction("task:" + task.id, NextActionKind.CONTINUE_TASK,
                        display(task.title,"진행 중 작업"), "본체가 접수한 작업의 진행 상황 확인",
                        room, 60, task.createdAt)
                else -> if (!task.done && task.engineJobId == null) results += NextAction(
                    "task:" + task.id, NextActionKind.CONTINUE_TASK, display(task.title,"미접수 작업"),
                    "저장했지만 본체 접수 기록이 없는 할 일", null, 40, task.createdAt)
            }
        }
        rooms.forEach { room ->
            val title = display(room.title(state.characters),"대화")
            val failed = room.messages.lastOrNull { it.sender == MessageSender.USER && it.status == MessageStatus.FAILED }
            if (failed != null) results += NextAction("room:" + room.id, NextActionKind.RETRY, title,
                "전송 실패한 요청이 있습니다. 재전송 전 상태를 확인하세요.", room.id, 95, failed.createdAt)
            else if (room.draft.isNotBlank()) results += NextAction("room:" + room.id, NextActionKind.FINISH_DRAFT,
                title, "작성 중인 요청이 남아 있습니다.", room.id, 75, room.updatedAt)
            else if (room.unread > 0 && !room.muted) results += NextAction("room:" + room.id, NextActionKind.UNREAD,
                title, "읽지 않은 메시지 " + room.unread + "개", room.id, 35, room.updatedAt)
        }
        state.schedules.filter { it.enabled }.forEach { schedule ->
            val due = listOfNotNull(ReminderTiming.nextUndelivered(schedule, now),
                ScheduledDispatch.next(schedule, now)).minOrNull() ?: return@forEach
            if (due <= now + 24 * 60 * 60 * 1000L) {
                results += NextAction("schedule:" + schedule.id, NextActionKind.UPCOMING,
                    display(schedule.title,"다가오는 일정"),
                    if (due <= now) "예정 시간이 지났습니다. 접수·알림 상태 확인"
                    else ReminderTiming.format(due, schedule.timeZone) + " · 준비할 일정",
                    null, if (due <= now) 90 else 80, -due)
            }
        }
        return results.sortedWith(compareByDescending<NextAction> { it.rank }.thenByDescending { it.time }
            .thenBy { it.key }).distinctBy { it.roomId?.let { id -> "room:$id" } ?: it.key }.take(6)
    }
}
