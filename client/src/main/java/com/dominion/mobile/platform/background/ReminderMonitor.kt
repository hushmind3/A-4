package com.dominion.mobile.platform.background

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.dominion.mobile.DominionApplication
import com.dominion.mobile.MainActivity
import com.dominion.mobile.R
import com.dominion.mobile.data.ReminderTiming
import com.dominion.mobile.data.model.Repeat
import com.dominion.mobile.data.model.ScheduleItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object ReminderMonitor {
    private const val REPAIR = "dominion-reminder-repair-v1"
    fun install(context: Context) {
        // No network constraint: personal reminders work with the server offline.
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(REPAIR, ExistingPeriodicWorkPolicy.KEEP, request)
    }
    fun schedule(context: Context, scope: String, rows: List<ScheduleItem>) {
        val now = System.currentTimeMillis()
        val next = rows.flatMap { item ->
            listOfNotNull(ReminderTiming.nextUndelivered(item, now),
                com.dominion.mobile.data.ScheduledDispatch.next(item, now))
        }.filter { it > now }.minOrNull() ?: return
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay((next - now).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("scope" to scope))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("dominion-reminder-" + scope + "-" + next,
            ExistingWorkPolicy.KEEP, request)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        deliveryLock.withLock {
            val container = (applicationContext as DominionApplication).container
            val scope = container.reminderScope()
            if (inputData.getString("scope")?.let { it != scope } == true) return@withLock Result.success()
            val repo = container.repository
            val now = System.currentTimeMillis()
            for (item in repo.state.value.schedules) {
                if (container.repository !== repo || container.reminderScope() != scope) break
                val dueWork = com.dominion.mobile.data.ScheduledDispatch.due(item, now)
                if (dueWork != null) {
                    try { repo.dispatchScheduledTask(item.id, dueWork) }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { /* Persisted reservation will be checked on next repair. */ }
                }
                if (container.repository !== repo || container.reminderScope() != scope) break
                val state = repo.state.value
                val live = state.schedules.firstOrNull { it.id == item.id } ?: continue
                val occurrence = ReminderTiming.due(live, now)
                if (occurrence != null && (live.watchCondition?.isNotBlank() == true ||
                    (state.settings.notifications && canNotify() &&
                    post(scope, live, occurrence, state.settings.hideContent || !state.settings.messagePreview)))) {
                    repo.changeSchedules { rows -> rows.map { row ->
                        if (row != live) row else row.copy(lastNotifiedAt = occurrence,
                            enabled = row.repeat != Repeat.NONE ||
                                (!row.workRequest.isNullOrBlank() && row.lastDispatchedAt != occurrence))
                    } }
                }
                // One-shot work is finished once submitted, independent of notification permission.
                repo.changeSchedules { rows -> rows.map { row ->
                    if (row.id == item.id && row.enabled && row.repeat == Repeat.NONE &&
                        !row.workRequest.isNullOrBlank() && row.lastDispatchedAt == row.scheduledAt &&
                        row.pendingDispatchAt == null) row.copy(enabled = false) else row
                } }
            }
            if (container.repository === repo) ReminderMonitor.schedule(applicationContext, scope, repo.state.value.schedules)
            Result.success()
        }
    }

    private fun canNotify(): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(applicationContext,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()

    private fun post(scope: String, item: ScheduleItem, at: Long, privateContent: Boolean): Boolean {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val channelId = "dominion_reminders"
        manager.createNotificationChannel(NotificationChannel(channelId, "DOMINION 일정 알림", NotificationManager.IMPORTANCE_DEFAULT))
        if (manager.getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val tag = "reminder:" + scope + ":" + item.id + ":" + at
        val destination = PendingIntent.getActivity(applicationContext, tag.hashCode(),
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (privateContent) "DOMINION 일정" else item.title)
            .setContentText(if (privateContent) "예약한 일정의 시간이 되었습니다." else item.whenText)
            .setVisibility(if (privateContent) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(destination)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        return try { manager.notify(tag, 1, notification); true } catch (_: SecurityException) { false }
    }

    private companion object { val deliveryLock = Mutex() }
}
