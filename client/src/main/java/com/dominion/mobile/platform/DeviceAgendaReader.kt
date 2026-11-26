package com.dominion.mobile.platform

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.dominion.mobile.data.AgendaEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DeviceAgendaReader {
    fun allowed(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED

    suspend fun read(context: Context, start: Long, end: Long): List<AgendaEntry> = withContext(Dispatchers.IO) {
        require(allowed(context))
        require(end > start && end - start <= 31L * 86400000)
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start); ContentUris.appendId(it, end)
        }.build()
        val columns = arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY)
        val output = mutableListOf<AgendaEntry>()
        // Only visible calendars; descriptions, attendees, accounts and addresses are not queried.
        requireNotNull(context.contentResolver.query(uri, columns, "visible=1", null,
            CalendarContract.Instances.BEGIN + " ASC")).use { cursor ->
            while (cursor.moveToNext() && output.size < 500) {
                val begin = cursor.getLong(2)
                output += AgendaEntry(cursor.getString(0) + ":" + begin,
                    cursor.getString(1)?.takeIf { it.isNotBlank() } ?: "제목 없는 일정",
                    begin, cursor.getLong(3), cursor.getInt(4) == 1)
            }
        }
        output.distinctBy { it.key }
    }
}
