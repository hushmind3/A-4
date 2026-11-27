package com.dominion.mobile.data

import com.dominion.mobile.data.model.ScheduleItem
import com.dominion.mobile.data.model.TaskItem
import java.util.UUID

object ScheduledDispatch {
    fun next(item: ScheduleItem, now: Long): Long? {
        if (!item.enabled || item.workRequest.isNullOrBlank()) return null
        return item.pendingDispatchAt ?: ReminderTiming.nextUndelivered(
            item.copy(lastNotifiedAt = item.lastDispatchedAt), now)
    }

    fun due(item: ScheduleItem, now: Long): Long? = next(item, now)?.takeIf { it <= now }

    fun task(item: ScheduleItem, occurrence: Long): TaskItem {
        require(!item.workRequest.isNullOrBlank())
        val id = UUID.nameUUIDFromBytes(("schedule:" + item.id + ":" + occurrence).toByteArray(Charsets.UTF_8)).toString()
        return TaskItem(id, item.title, item.characterId, due = ReminderTiming.format(occurrence, item.timeZone),
            requestDetails = item.workRequest.trim() + "\n예약 업무입니다. 외부 변경·제출·결제 등 추가 승인 필요한 행동은 본체의 승인 절차를 따르세요.")
    }
}
