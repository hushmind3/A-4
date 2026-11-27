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
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Monitor server-side jobs for a signed-in mobile account without needing a PC.
 * WorkManager credentials contain only a hash of the verified account identity.
 * The server executes work; the mobile watcher only verifies and reports status.
 */
object CloudJobMonitor {
    private const val NAME="dominion-cloud-jobs-v1"
    private const val FAST="dominion-cloud-jobs-fast-v1"
    private const val SCOPE="account_hash"
    private const val FAST_ATTEMPT="fast_attempt"
    private const val FAST_FLAG="fast_watch"
    internal const val MAX_FAST_ATTEMPTS=20
    internal const val FAST_DELAY_MS=60_000L

    internal fun accountHash(accountId:String):String =
        MessageDigest.getInstance("SHA-256")
            .digest(accountId.toByteArray(Charsets.UTF_8))
            .joinToString(""){"%02x".format(it.toInt() and 255)}
    internal fun isTerminal(status:String?)=status in setOf("done","failed","cancelled")
    internal fun isActive(status:String?)=status in setOf("queued","running")
    internal fun matchesAccount(accountId:String?,expectedHash:String?):Boolean =
        accountId!=null&&expectedHash!=null&&accountHash(accountId)==expectedHash
    internal fun shouldContinueFast(attempt:Int,hasActiveWork:Boolean):Boolean =
        hasActiveWork&&attempt in 0 until MAX_FAST_ATTEMPTS

    fun install(context:Context,accountId:String?) {
        val worker=WorkManager.getInstance(context)
        if(accountId.isNullOrBlank()){
            worker.cancelUniqueWork(NAME)
            worker.cancelUniqueWork(FAST)
            return
        }
        val request=PeriodicWorkRequestBuilder<CloudJobWorker>(15,TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(SCOPE to accountHash(accountId)))
            .build()
        worker.enqueueUniquePeriodicWork(NAME,ExistingPeriodicWorkPolicy.UPDATE,request)
        // The first periodic run may be delayed by the OS. A bounded one-time
        // chain checks soon after submission and while the app is backgrounded.
        pollNow(context,accountId,0,0L)
    }

    /**
     * Schedule one immediate short-interval check. The worker appends at most
     * [MAX_FAST_ATTEMPTS] follow-ups, then the existing 15-minute periodic work
     * remains the durable fallback. No job is executed on the device.
     */
    fun pollNow(context:Context,accountId:String?,attempt:Int=0,delayMs:Long=FAST_DELAY_MS) {
        val worker=WorkManager.getInstance(context)
        if(accountId.isNullOrBlank()){worker.cancelUniqueWork(FAST);return}
        val request=OneTimeWorkRequestBuilder<CloudJobWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(delayMs.coerceAtLeast(0L),TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(SCOPE to accountHash(accountId),FAST_FLAG to true,
                FAST_ATTEMPT to attempt.coerceIn(0,MAX_FAST_ATTEMPTS)))
            .build()
        worker.enqueueUniqueWork(FAST,ExistingWorkPolicy.REPLACE,request)
    }

    /** Called from a running worker or a repository update: appending cannot
     * cancel the current check. */
    internal fun scheduleNext(context:Context,accountId:String,attempt:Int,
        delayMs:Long=FAST_DELAY_MS) {
        if(accountId.isBlank()||attempt !in 0 until MAX_FAST_ATTEMPTS)return
        val request=OneTimeWorkRequestBuilder<CloudJobWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(delayMs.coerceAtLeast(0L),TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(SCOPE to accountHash(accountId),FAST_FLAG to true,FAST_ATTEMPT to attempt))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(FAST,ExistingWorkPolicy.APPEND_OR_REPLACE,request)
    }

    internal fun cancelFast(context:Context)=
        WorkManager.getInstance(context).cancelUniqueWork(FAST)

    internal fun expectedScope(data:Data)=data.getString(SCOPE)
}

/** Periodic fallback until server push is wired. Not realtime and not a job executor. */
class CloudJobWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters){
    override suspend fun doWork():Result {
        val container=(applicationContext as DominionApplication).container
        val repo=container.repository
        val account=container.cloudAccount.savedAccountId()
        val expected=CloudJobMonitor.expectedScope(inputData)
        val fast=inputData.getBoolean("fast_watch",false)
        val attempt=inputData.getInt("fast_attempt",0)
        if(container.engine!=null||account==null||expected!=CloudJobMonitor.accountHash(account)){
            WorkManager.getInstance(applicationContext).cancelWorkById(id)
            if(fast)CloudJobMonitor.cancelFast(applicationContext)
            return Result.success()
        }
        val pending=repo.state.value.tasks.filter{
            com.dominion.mobile.data.remote.TaskDispatch.needsCloudRefresh(it,
                repo.state.value.conversation(it.engineRoomId.orEmpty()))
        }.take(16)
        var transientFailure=false
        for(task in pending){
            if(container.repository!==repo||container.engine!=null||
                container.cloudAccount.savedAccountId()!=account)return Result.success()
            try {
                repo.refreshTask(task.id)
                if(container.repository!==repo||container.engine!=null||
                    container.cloudAccount.savedAccountId()!=account)return Result.success()
                val updated=repo.state.value.tasks.firstOrNull{it.id==task.id}?:continue
                if(CloudJobMonitor.isTerminal(updated.engineStatus)){
                    notifyOnce(account,updated.id,updated.engineStatus.orEmpty())
                }
            }catch(e:CancellationException){throw e}
            catch(_:Exception){transientFailure=true}
        }
        if(container.repository===repo&&container.engine==null&&container.cloudAccount.savedAccountId()==account){
            try{repo.advanceTaskWorkflows()}
            catch(e:CancellationException){throw e}
            catch(_:Exception){transientFailure=true}
        }
        if(container.repository===repo&&container.cloudAccount.savedAccountId()==account){
            repo.state.value.tasks.filter{it.phonePackage!=null&&com.dominion.mobile.data.TaskHandoff.ready(it)&&
                it.engineStatus=="needs_review"}.take(8).forEach{notifyOnce(account,it.id,"needs_review")}
        }
        if(fast){
            val scoped=container.repository===repo&&container.engine==null&&
                container.cloudAccount.savedAccountId()==account
            val active=scoped&&repo.state.value.tasks.any{
                com.dominion.mobile.data.remote.TaskDispatch.needsCloudRefresh(it,
                    repo.state.value.conversation(it.engineRoomId.orEmpty()))
            }
            if(CloudJobMonitor.shouldContinueFast(attempt,active))
                CloudJobMonitor.scheduleNext(applicationContext,account,attempt+1)
            else CloudJobMonitor.cancelFast(applicationContext)
        }
        return if(transientFailure)Result.retry() else Result.success()
    }

    private fun notifyOnce(account:String,taskId:String,status:String){
        val container=(applicationContext as DominionApplication).container
        if(container.engine!=null || container.cloudAccount.savedAccountId()!=account)return
        val settings=container.repository.state.value.settings
        if(!settings.notifications ||
            (Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(
                applicationContext,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) ||
            !NotificationManagerCompat.from(applicationContext).areNotificationsEnabled())return
        val key=CloudJobMonitor.accountHash(account)+":"+taskId
        val preferences=applicationContext.getSharedPreferences("dominion_cloud_job_alerts",Context.MODE_PRIVATE)
        if(preferences.getString(key,null)==status)return
        val manager=applicationContext.getSystemService(NotificationManager::class.java)
        val channel="dominion_cloud_jobs"
        manager.createNotificationChannel(NotificationChannel(channel,"DOMINION 클라우드 작업",NotificationManager.IMPORTANCE_DEFAULT))
        if(manager.getNotificationChannel(channel)?.importance==NotificationManager.IMPORTANCE_NONE)return
        val title=when(status){
            "done"->"DOMINION 클라우드 작업 완료"
            "failed"->"DOMINION 클라우드 작업 실패"
            "needs_review"->"DOMINION 다음 앱 업무 실행 가능"
            else->"DOMINION 클라우드 작업 중단"
        }
        val privateContent=settings.hideContent||!settings.messagePreview
        val requestCode=key.hashCode() and 0x7fffffff
        val room=container.repository.state.value.tasks.firstOrNull{it.id==taskId}?.engineRoomId
        val destination=PendingIntent.getActivity(applicationContext,requestCode,
            Intent(applicationContext,MainActivity::class.java).apply{
                flags=Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if(room!=null){
                    putExtra("dominion_notification_room",room)
                    putExtra("dominion_notification_cloud",CloudJobMonitor.accountHash(account))
                }
            },PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val message=if(privateContent)"작업 상태가 갱신되었습니다." else if(status=="needs_review")
            "선행 작업 확인 완료. 앱에서 다음 실행을 검토하세요." else "앱에서 작업 결과와 상태를 확인하세요."
        val notification=NotificationCompat.Builder(applicationContext,channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if(privateContent)"DOMINION" else title)
            .setContentText(message)
            .setContentIntent(destination)
            .setVisibility(if(privateContent)NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true).setAutoCancel(true).build()
        try{
            manager.notify(requestCode,notification)
            preferences.edit().putString(key,status).apply()
        }catch(_:SecurityException){}
    }
}
