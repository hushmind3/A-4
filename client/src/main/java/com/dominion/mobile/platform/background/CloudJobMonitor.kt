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
    private const val SCOPE="account_hash"

    internal fun accountHash(accountId:String):String =
        MessageDigest.getInstance("SHA-256")
            .digest(accountId.toByteArray(Charsets.UTF_8))
            .joinToString(""){"%02x".format(it.toInt() and 255)}
    internal fun isTerminal(status:String?)=status in setOf("done","failed","cancelled")
    internal fun isActive(status:String?)=status in setOf("queued","running")

    fun install(context:Context,accountId:String?) {
        val worker=WorkManager.getInstance(context)
        if(accountId.isNullOrBlank()){worker.cancelUniqueWork(NAME);return}
        val request=PeriodicWorkRequestBuilder<CloudJobWorker>(15,TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(SCOPE to accountHash(accountId)))
            .build()
        worker.enqueueUniquePeriodicWork(NAME,ExistingPeriodicWorkPolicy.UPDATE,request)
    }
    internal fun expectedScope(data:Data)=data.getString(SCOPE)
}

/** Periodic fallback until server push is wired. Not realtime and not a job executor. */
class CloudJobWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters){
    override suspend fun doWork():Result {
        val container=(applicationContext as DominionApplication).container
        val repo=container.repository
        val account=container.cloudAccount.savedAccountId()
        val expected=CloudJobMonitor.expectedScope(inputData)
        if(container.engine!=null||account==null||expected!=CloudJobMonitor.accountHash(account)){
            WorkManager.getInstance(applicationContext).cancelWorkById(id)
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
            else->"DOMINION 클라우드 작업 중단"
        }
        val privateContent=settings.hideContent||!settings.messagePreview
        val requestCode=key.hashCode() and 0x7fffffff
        val destination=PendingIntent.getActivity(applicationContext,requestCode,
            Intent(applicationContext,MainActivity::class.java).apply{
                flags=Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val message=if(privateContent)"작업 상태가 갱신되었습니다." else "앱에서 작업 결과와 상태를 확인하세요."
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
