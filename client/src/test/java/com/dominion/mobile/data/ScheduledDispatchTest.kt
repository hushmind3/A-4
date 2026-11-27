package com.dominion.mobile.data
import com.dominion.mobile.data.model.*
import org.junit.Assert.*
import org.junit.Test

class ScheduledDispatchTest {
    private val item = ScheduleItem("s","research","a","",Repeat.DAILY,
        scheduledAt = 1000, timeZone = "Asia/Seoul", workRequest = "공고 조사")
    @Test fun workIndependentOfReminderAndStableOnRetry() {
        assertEquals(1000L, ScheduledDispatch.due(item.copy(lastNotifiedAt = 1000), 2000))
        val pending = item.copy(pendingDispatchAt = 1000)
        assertEquals(1000L, ScheduledDispatch.due(pending, 86402000))
        assertEquals(ScheduledDispatch.task(item, 1000).id, ScheduledDispatch.task(pending, 1000).id)
        assertNotEquals(ScheduledDispatch.task(item, 1000).id, ScheduledDispatch.task(item, 86401000).id)
    }
    @Test fun disabledOrNotificationOnlyDoesNotExecute() {
        assertNull(ScheduledDispatch.due(item.copy(enabled = false), 2000))
        assertNull(ScheduledDispatch.due(item.copy(workRequest = null), 2000))
    }
}
