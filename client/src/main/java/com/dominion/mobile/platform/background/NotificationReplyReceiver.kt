package com.dominion.mobile.platform.background

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.work.*
import com.dominion.mobile.DominionApplication
import com.dominion.mobile.MainActivity
import com.dominion.mobile.R
import kotlinx.coroutines.*
import java.util.UUID

/** User-entered notification replies use the same native room submission as chat.
 * The PendingIntent is explicit, one-shot and authenticated. Work data contains IDs, not text. */
class NotificationReplyReceiver: BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action!=REPLY)return
        val id=intent.data?.lastPathSegment ?: return
        if(runCatching { UUID.fromString(id).toString()==id }.getOrDefault(false).not())return
        val origin=intent.getStringExtra("origin") ?: return
        val room=intent.getStringExtra("room") ?: return
        val session=intent.getStringExtra("session") ?: return
        val text=RemoteInput.getResultsFromIntent(intent)?.getCharSequence(INPUT)?.toString()?.trim() ?: return
        if(text.isBlank() || text.length>4000 || text.contains('\u0000'))return
        val notificationId=intent.getIntExtra("notification",0)
        val token=goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try {
                withTimeout(8000L) {
                    val manager=context.getSystemService(KeyguardManager::class.java)
                    check(!manager.isDeviceLocked)
                    val container=(context.applicationContext as DominionApplication).container
                    val engine=container.engine ?: error("Engine disconnected")
                    check(BackgroundJobMonitor.originKey(engine.origin)==origin)
                    val repo=container.repository
                    check(repo.reserveNotificationReply(room,session,"notification_"+id,text))
                    check(container.repository===repo && container.engine===engine)
                    val request=OneTimeWorkRequestBuilder<NotificationReplyWorker>()
                        .setInputData(workDataOf("origin" to origin,"room" to room,"session" to session,
                            "nonce" to "notification_"+id,"notification" to notificationId))
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .build()
                    WorkManager.getInstance(context).enqueueUniqueWork("dominion-notification-reply-"+id,ExistingWorkPolicy.KEEP,request)
                    updateNotification(context,origin,room,notificationId,"후속 요청 저장 · 서버 접수 확인 중")
                }
            } catch(_:Exception){ updateNotification(context,origin,room,notificationId,"후속 요청 저장·연결 확인 필요") }
            finally { token.finish() }
        }
    }
    companion object {
        private const val REPLY="com.dominion.mobile.action.NOTIFICATION_REPLY"
        private const val INPUT="dominion_reply"
        fun action(context:Context,origin:String,room:String,session:String,id:Int):NotificationCompat.Action {
            val intent=Intent(context,NotificationReplyReceiver::class.java).apply {
                action=REPLY
                data=Uri.Builder().scheme("dominion-notification").authority("reply")
                    .appendPath(UUID.randomUUID().toString()).build()
                putExtra("origin",origin);putExtra("room",room);putExtra("session",session);putExtra("notification",id)
            }
            val pending=PendingIntent.getBroadcast(context,id,intent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ONE_SHOT)
            return NotificationCompat.Action.Builder(0,"후속 요청",pending)
                .addRemoteInput(RemoteInput.Builder(INPUT).setLabel("본체에 이어서 요청할 내용").build())
                .setAuthenticationRequired(true)
                .setAllowGeneratedReplies(false)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY).build()
        }
        internal fun updateNotification(context:Context,origin:String,room:String,id:Int,status:String) {
            val manager=context.getSystemService(NotificationManager::class.java)
            val intent=Intent(context,MainActivity::class.java).apply {
                flags=Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("dominion_notification_origin",origin);putExtra("dominion_notification_room",room)
            }
            val pending=PendingIntent.getActivity(context,id,intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            runCatching {
                manager.notify(id,NotificationCompat.Builder(context,"dominion_job_updates")
                    .setSmallIcon(R.drawable.ic_notification).setContentTitle("DOMINION")
                    .setContentText(status).setContentIntent(pending)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(true)
                    .setOnlyAlertOnce(true).build())
            }
        }
    }
}
class NotificationReplyWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val origin=inputData.getString("origin") ?: return Result.failure()
        val room=inputData.getString("room") ?: return Result.failure()
        val session=inputData.getString("session") ?: return Result.failure()
        val nonce=inputData.getString("nonce") ?: return Result.failure()
        val notification=inputData.getInt("notification",0)
        val container=(applicationContext as DominionApplication).container
        val engine=container.engine ?: return Result.failure()
        if(BackgroundJobMonitor.originKey(engine.origin)!=origin)return Result.failure()
        val repo=container.repository
        val accepted=try { repo.dispatchNotificationReply(room,session,nonce) }
            catch(e:CancellationException){throw e}
            catch(_:Exception){false}
        if(container.engine===engine && container.repository===repo)
            NotificationReplyReceiver.updateNotification(applicationContext,origin,room,notification,
                if(accepted)"후속 요청 서버 접수 확인" else "후속 요청 미확인 · 대화에서 조회")
        return Result.success()
    }
}
