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
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dominion.mobile.DominionApplication
import com.dominion.mobile.MainActivity
import com.dominion.mobile.R
import com.dominion.mobile.data.remote.ApiFactory
import com.dominion.mobile.data.remote.belongsToRoom
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Each job has durable, credential-free monitoring; all execution remains on DOMINION. */
object BackgroundJobMonitor {
    private const val PREFIX = "dominion-job-"
    internal const val ORIGIN = "origin"
    internal const val ROOM = "room"
    internal const val JOB = "job"

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { b -> "%02x".format(b.toInt() and 255) }

    internal fun originKey(origin: String) = digest(origin)
    internal fun workKey(origin: String, room: String, job: String) =
        PREFIX + digest(origin + "|" + room + "|" + job)

    fun enqueue(context: Context, origin: String, roomId: String, jobId: String) {
        if (roomId.isBlank() || jobId.isBlank()) return
        val name = workKey(origin, roomId, jobId)
        val work = PeriodicWorkRequestBuilder<BackgroundJobWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(ORIGIN to originKey(origin), ROOM to roomId, JOB to jobId))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.KEEP, work)
    }

    internal fun stop(context: Context, origin: String, room: String, job: String) =
        WorkManager.getInstance(context).cancelUniqueWork(workKey(origin, room, job))
}

/** Reads only the server's durable records; never starts or resumes an agent job. */
class BackgroundJobWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private var verifiedSession: String? = null
    override suspend fun doWork(): Result {
        val expected = inputData.getString(BackgroundJobMonitor.ORIGIN) ?: return Result.failure()
        val roomId = inputData.getString(BackgroundJobMonitor.ROOM) ?: return Result.failure()
        val jobId = inputData.getString(BackgroundJobMonitor.JOB) ?: return Result.failure()
        if (expected.length != 64 || roomId.isBlank() || jobId.isBlank()) return Result.failure()

        val container = (applicationContext as DominionApplication).container
        val engine = container.engine
        val repository = container.repository
        // Never use a new engine's owner token to fetch the previous engine's job.
        if (engine == null || BackgroundJobMonitor.originKey(engine.origin) != expected) {
            WorkManager.getInstance(applicationContext).cancelWorkById(id)
            return Result.success()
        }
        val api = ApiFactory.create(engine)
        val job = try { api.engineJob(jobId) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { return Result.success() } // next scheduled poll

        val currentEngine = (applicationContext as DominionApplication).container.engine
        if (currentEngine !== engine || container.repository !== repository) {
            if (currentEngine?.origin != engine.origin) WorkManager.getInstance(applicationContext).cancelWorkById(id)
            return Result.success()
        }
        if (!job.belongsToRoom(roomId, jobId)) {
            BackgroundJobMonitor.stop(applicationContext, engine.origin, roomId, jobId)
            return Result.success()
        }
        // Persist actual server status before announcing it or cancelling the durable watcher.
        try { repository.recordObservedJob(roomId, jobId, job) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return Result.retry() }
        if (container.engine !== engine || container.repository !== repository) return Result.success()
        verifiedSession = job.sessionId
        val key = BackgroundJobMonitor.workKey(engine.origin, roomId, jobId)
        when (job.status) {
            "completed" -> {
                val task = repository.state.value.tasks.firstOrNull {
                    com.dominion.mobile.data.remote.TaskDispatch.matches(it, job)
                }
                if (!task?.watchCondition.isNullOrBlank()) {
                    when (com.dominion.mobile.data.WatchDecision.evaluate(requireNotNull(task), job)) {
                        com.dominion.mobile.data.WatchDecision.MATCH ->
                            notifyOnce(key, "watch-match", "DOMINION 감시 조건 발견", "본체 AI가 조건 충족으로 판단했습니다. 출처와 근거를 확인하세요.")
                        com.dominion.mobile.data.WatchDecision.UNKNOWN ->
                            notifyOnce(key, "watch-unknown", "DOMINION 감시 확인 필요", "조건 충족 여부를 확인하지 못했습니다. 실제 조회 결과를 검토하세요.")
                        com.dominion.mobile.data.WatchDecision.NO_MATCH -> Unit
                    }
                } else notifyOnce(key, "completed", "DOMINION 작업 완료", "서버 작업이 완료됐습니다. 대화방에서 결과를 확인하세요.")
                BackgroundJobMonitor.stop(applicationContext, engine.origin, roomId, jobId)
            }
            "failed" -> {
                notifyOnce(key, "failed", "DOMINION 작업 실패", "대화방에서 실패 원인을 확인하세요.")
                BackgroundJobMonitor.stop(applicationContext, engine.origin, roomId, jobId)
            }
            "cancelled" -> {
                notifyOnce(key, "cancelled", "DOMINION 작업 중단", "서버에서 작업이 중단됐습니다.")
                BackgroundJobMonitor.stop(applicationContext, engine.origin, roomId, jobId)
            }
            "needs_review", "paused", "interrupted", "stopped" ->
                notifyOnce(key, job.status ?: "attention", "DOMINION 작업 확인 필요", "대화방에서 현재 상태를 확인하세요.")
            "queued", "running" -> {
                if (canNotify()) {
                    val approvals = try { api.pendingApprovals() }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { emptyList() }
                    val relevant = approvals.filter {
                        it.sessionId == job.sessionId && it.actionableIn(job.sessionId!!)
                    }.mapNotNull { it.id }.sorted()
                    if (relevant.isNotEmpty()) {
                        notifyOnce(key, "approval:" + relevant.joinToString(","), "DOMINION 도구 승인 대기",
                            "대화방에 검토할 도구 실행 요청이 있습니다.")
                    }
                }
            }
        }
        return Result.success()
    }

    private fun canNotify(): Boolean {
        val container = (applicationContext as DominionApplication).container
        val room = inputData.getString(BackgroundJobMonitor.ROOM)
        if (container.engine?.origin?.let(BackgroundJobMonitor::originKey) != inputData.getString(BackgroundJobMonitor.ORIGIN))
            return false
        val state = container.repository.state.value
        if (!state.settings.notifications || (room != null && state.conversation(room)?.muted == true)) return false
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return androidx.core.app.NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
    }

    private fun notifyOnce(key: String, phase: String, title: String, body: String) {
        if (!canNotify()) return
        val prefs = applicationContext.getSharedPreferences("dominion_job_alerts", Context.MODE_PRIVATE)
        if (prefs.getString(key, null) == phase) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val channelId = "dominion_job_updates"
        manager.createNotificationChannel(NotificationChannel(channelId, "DOMINION 작업 알림",
            NotificationManager.IMPORTANCE_DEFAULT))
        val open = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("dominion_notification_room", inputData.getString(BackgroundJobMonitor.ROOM))
            putExtra("dominion_notification_origin", inputData.getString(BackgroundJobMonitor.ORIGIN))
        }
        val id = key.hashCode() and 0x7fffffff
        val destination = PendingIntent.getActivity(applicationContext, id, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val settings = (applicationContext as DominionApplication).container.repository.state.value.settings
        val privateContent = settings.hideContent || !settings.messagePreview
        val notificationBuilder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setVisibility(if (privateContent) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PRIVATE)
            .setContentTitle(if (privateContent) "DOMINION" else title)
            .setContentText(if (privateContent) "새 작업 소식이 있습니다." else body)
            .setContentIntent(destination)
            .setAutoCancel(true)
        val roomId=inputData.getString(BackgroundJobMonitor.ROOM)
        val origin=inputData.getString(BackgroundJobMonitor.ORIGIN)
        val session=verifiedSession
        val container=(applicationContext as DominionApplication).container
        val repo=container.repository
        val engine=container.engine
        if(!privateContent && engine!=null && !settings.appLock && roomId!=null && origin!=null && !session.isNullOrBlank() &&
            repo.state.value.conversation(roomId)?.sessionId==session) {
            notificationBuilder.addAction(NotificationReplyReceiver.action(applicationContext,origin,NotificationReplyReceiver.credentialKey(engine),roomId,session,id))
        }
        notificationBuilder.addAction(0,if(phase.startsWith("approval:")) "승인 내용 검토" else "결과 확인",destination)
        try {
            manager.notify(id, notificationBuilder.build())
            prefs.edit().putString(key, phase).apply()
        } catch (_: SecurityException) {
            // Runtime permission may be revoked between inspection and posting.
        }
    }
}
