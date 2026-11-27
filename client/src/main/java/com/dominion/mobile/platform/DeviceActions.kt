package com.dominion.mobile.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import com.dominion.mobile.data.ContactTarget
import com.dominion.mobile.data.KoreanService
import com.dominion.mobile.data.KoreanServices
import com.dominion.mobile.data.model.Repeat

/** Device operations are explicit user actions, not fabricated server receipts. */
object DeviceActions {
    private fun launch(context: Context, intent: Intent): Boolean =
        runCatching {
            if(context !is android.app.Activity)intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent); true
        }.getOrDefault(false)

    fun openOfficial(context: Context, service: KoreanService): Boolean =
        launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(KoreanServices.officialUrl(service))))

    fun dial(context: Context, phone: String): Boolean {
        require(phone.matches(Regex("[0-9]{3,15}")))
        return launch(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null)))
    }

    fun composeSms(context: Context, phone: String, body: String): Boolean {
        val number = ContactTarget.phone(phone)
        require(body.length <= 10000)
        return launch(context, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
            .putExtra("sms_body", body))
    }

    fun composeEmail(context: Context, email: String, subject: String, body: String): Boolean {
        val address = ContactTarget.email(email)
        require(subject.length <= 200 && body.length <= 32000)
        val uri = Uri.parse("mailto:" + Uri.encode(address, "@") + "?subject=" + Uri.encode(subject) +
            "&body=" + Uri.encode(body))
        return launch(context, Intent(Intent.ACTION_SENDTO, uri))
    }

    fun dialContact(context: Context, phone: String): Boolean =
        launch(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", ContactTarget.phone(phone), null)))

    data class InstalledApp(val packageName: String, val label: String)

    fun launchableApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0).mapNotNull { row ->
            val pkg = row.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == context.packageName || !com.dominion.mobile.data.PhoneNavigationPlan.eligiblePackage(pkg))
                return@mapNotNull null
            InstalledApp(pkg, row.loadLabel(pm).toString().take(100).ifBlank { pkg })
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase(java.util.Locale.ROOT) }
    }

    fun isLaunchable(context: Context, packageName: String): Boolean =
        launchableApps(context).any { it.packageName == packageName }

    fun openInstalledApp(context: Context, packageName: String): Boolean {
        require(packageName.matches(Regex("[a-zA-Z0-9_.]{1,200}")))
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        // AccessibilityService is not an Activity; launch in its own task.
        return launch(context, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
