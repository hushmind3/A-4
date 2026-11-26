package com.dominion.mobile.data

import com.dominion.mobile.data.model.Repeat
import com.dominion.mobile.data.model.ScheduleItem
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class ReminderTimingTest {
    private fun at(text: String) = ZonedDateTime.parse(text + "+09:00[Asia/Seoul]").toInstant().toEpochMilli()
    private val anchor = at("2027-01-31T09:00:00")
    private fun item(repeat: Repeat) = ScheduleItem("id", "test", "a", "", repeat,
        scheduledAt = anchor, timeZone = "Asia/Seoul")

    @Test fun monthlyRemindersKeepOriginalDayAfterShortMonth() {
        val feb = at("2027-02-28T09:00:00")
        val mar = at("2027-03-31T09:00:00")
        val monthly = item(Repeat.MONTHLY).copy(lastNotifiedAt = anchor)
        assertEquals(feb, ReminderTiming.due(monthly, feb))
        assertEquals(mar, ReminderTiming.nextUndelivered(monthly.copy(lastNotifiedAt = feb), feb))
    }
    @Test fun legacyRowsAndDisabledRowsDoNotPretendToHaveScheduledTime() {
        assertNull(ReminderTiming.nextUndelivered(item(Repeat.NONE).copy(scheduledAt = null), anchor))
        assertNull(ReminderTiming.due(item(Repeat.DAILY).copy(enabled = false), anchor))
    }
    @Test fun deliveredOnceAndMissedRepeatAreReconciled() {
        assertNull(ReminderTiming.nextUndelivered(item(Repeat.NONE).copy(lastNotifiedAt = anchor), anchor + 1000))
        val next = at("2027-02-03T09:00:00")
        assertEquals(next, ReminderTiming.due(item(Repeat.DAILY), next + 1000))
    }
}
