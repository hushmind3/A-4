package com.dominion.mobile.util

import com.dominion.mobile.data.model.Lang
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun Long.asClock(lang: Lang = Lang.KO): String =
    SimpleDateFormat(if (lang == Lang.KO) "a h:mm" else "h:mm a", locale(lang)).format(Date(this))

fun Long.asShortClock(): String =
    SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(this))

fun Long.asDate(lang: Lang): String =
    SimpleDateFormat(if (lang == Lang.KO) "M월 d일" else "MMM d", if (lang == Lang.KO) Locale.KOREA else Locale.US)
        .format(Date(this))

fun Long.asRelative(lang: Lang): String {
    val diff = System.currentTimeMillis() - this
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    return when {
        minutes < 1 -> if (lang == Lang.KO) "방금" else "now"
        minutes < 60 -> if (lang == Lang.KO) "${minutes}분 전" else "${minutes}m ago"
        minutes < 60 * 24 -> asClock(lang)
        minutes < 60 * 48 -> if (lang == Lang.KO) "어제" else "Yesterday"
        else -> asDate(lang)
    }
}

fun Long.asDayHeader(lang: Lang): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = this@asDayHeader }
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    return when {
        isSameDay(now, then) -> if (lang == Lang.KO) "오늘" else "Today"
        isYesterday(now, then) -> if (lang == Lang.KO) "어제" else "Yesterday"
        sameYear -> SimpleDateFormat(if (lang == Lang.KO) "M월 d일 EEEE" else "EEE, MMM d", locale(lang))
            .format(Date(this))
        else -> SimpleDateFormat(if (lang == Lang.KO) "yyyy년 M월 d일" else "MMM d, yyyy", locale(lang))
            .format(Date(this))
    }
}

private fun locale(lang: Lang) = if (lang == Lang.KO) Locale.KOREA else Locale.US

private fun isSameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

private fun isYesterday(now: Calendar, then: Calendar): Boolean {
    val copy = now.clone() as Calendar
    copy.add(Calendar.DAY_OF_YEAR, -1)
    return isSameDay(copy, then)
}

fun formatDuration(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    return "%02d:%02d".format(minutes, seconds)
}

fun Long.asWon(): String = "₩" + String.format(Locale.KOREA, "%,d", this)
