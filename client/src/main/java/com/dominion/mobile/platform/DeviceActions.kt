package com.dominion.mobile.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import com.dominion.mobile.data.KoreanService
import com.dominion.mobile.data.KoreanServices
import com.dominion.mobile.data.model.Repeat

/** Device operations are explicit user actions, not fabricated server receipts. */
object DeviceActions {
    private fun launch(context: Context, intent: Intent): Boolean =
        runCatching { context.startActivity(intent); true }.getOrDefault(false)

    fun openOfficial(context: Context, service: KoreanService): Boolean =
        launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(KoreanServices.officialUrl(service))))

    fun dial(context: Context, phone: String): Boolean {
        require(phone.matches(Regex("[0-9]{3,15}")))
        return launch(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null)))
    }

    fun map(context: Context, query: String): Boolean {
        require(query.isNotBlank() && query.length <= 250)
        return launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query))))
    }

    fun calendar(context: Context, title: String, at: Long, note: String, repeat: Repeat): Boolean {
        val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.Events.DESCRIPTION, note)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, at)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, at + 30 * 60 * 1000L)
        val rule = when (repeat) {
            Repeat.NONE -> null
            Repeat.DAILY -> "FREQ=DAILY"
            Repeat.WEEKLY -> "FREQ=WEEKLY"
            Repeat.MONTHLY -> "FREQ=MONTHLY"
        }
        // Calendar app recurrence is editable by the user before saving.
        rule?.let { intent.putExtra(CalendarContract.Events.RRULE, it) }
        return launch(context, intent)
    }
}
