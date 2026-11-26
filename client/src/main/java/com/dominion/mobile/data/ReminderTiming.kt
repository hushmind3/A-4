package com.dominion.mobile.data

import com.dominion.mobile.data.model.Repeat
import com.dominion.mobile.data.model.ScheduleItem
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter

object ReminderTiming {
    private fun zone(id: String?): ZoneId = runCatching { ZoneId.of(id ?: ZoneId.systemDefault().id) }
        .getOrDefault(ZoneId.systemDefault())

    fun format(at: Long, zoneId: String?): String =
        Instant.ofEpochMilli(at).atZone(zone(zoneId)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z"))

    private fun anchor(item: ScheduleItem): ZonedDateTime? =
        item.scheduledAt?.let { Instant.ofEpochMilli(it).atZone(zone(item.timeZone)) }

    private fun occurrence(anchor: ZonedDateTime, repeat: Repeat, index: Long): Long =
        when (repeat) {
            Repeat.NONE -> anchor
            Repeat.DAILY -> anchor.plusDays(index)
            Repeat.WEEKLY -> anchor.plusWeeks(index)
            Repeat.MONTHLY -> anchor.plusMonths(index)
        }.toInstant().toEpochMilli()

    /** Most recent occurrence, anchored to the original calendar date (no monthly drift). */
    private fun latestIndex(anchor: ZonedDateTime, repeat: Repeat, now: Long): Long {
        val current = Instant.ofEpochMilli(now).atZone(anchor.zone)
        var n = when (repeat) {
            Repeat.NONE -> 0
            Repeat.DAILY -> ChronoUnit.DAYS.between(anchor.toLocalDate(), current.toLocalDate())
            Repeat.WEEKLY -> ChronoUnit.DAYS.between(anchor.toLocalDate(), current.toLocalDate()) / 7
            Repeat.MONTHLY -> ChronoUnit.MONTHS.between(anchor.toLocalDate().withDayOfMonth(1), current.toLocalDate().withDayOfMonth(1))
        }.coerceAtLeast(0)
        if (n > 0 && occurrence(anchor, repeat, n) > now) n--
        return n
    }

    fun nextUndelivered(item: ScheduleItem, now: Long): Long? {
        if (!item.enabled) return null
        val a = anchor(item) ?: return null
        if (now < a.toInstant().toEpochMilli()) return a.toInstant().toEpochMilli()
        val index = latestIndex(a, item.repeat, now)
        val latest = occurrence(a, item.repeat, index)
        if ((item.lastNotifiedAt ?: Long.MIN_VALUE) < latest) return latest
        return if (item.repeat == Repeat.NONE) null else occurrence(a, item.repeat, index + 1)
    }

    /** The worker may coalesce missed repeated occurrences into the latest due one. */
    fun due(item: ScheduleItem, now: Long): Long? = nextUndelivered(item, now)?.takeIf { it <= now }
}
