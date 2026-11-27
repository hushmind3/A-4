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
import com.dominion.mobile.DominionApplication
import com.dominion.mobile.MainActivity
import com.dominion.mobile.R
import com.dominion.mobile.data.remote.CloudAccountRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Runtime-initialized FCM client. Firebase app IDs are public identifiers
 * delivered by the authenticated Cloud account; no private key is in the APK. */
object FirebasePush {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    @Volatile private var registeredFor:String?=null

    suspend fun ensureRegistered(context:Context, repo:CloudAccountRepository):Boolean{
        val account=repo.savedAccountId()?:return false
        if(registeredFor==account)return true
        val config=try{repo.pushConfig()}catch(e:CancellationException){throw e}
        catch(_:Exception){null}?:return false
        if(FirebaseApp.getApps(context).isEmpty()){
            FirebaseApp.initializeApp(context,FirebaseOptions.Builder()
                .setApplicationId(config.application_id)
                .setApiKey(config.api_key)
                .setProjectId(config.project_id)
                .setGcmSenderId(config.sender_id)
                .build())
        }
        val token=suspendCancellableCoroutine<String>{continuation->
            FirebaseMessaging.getInstance().token.addOnCompleteListener{task->
                if(!continuation.isActive)return@addOnCompleteListener
                if(task.isSuccessful)task.result?.let{continuation.resume(it)}
                    ?:continuation.resumeWithException(IllegalStateException("Empty FCM token"))
                else continuation.resumeWithException(task.exception?:IllegalStateException("FCM token failed"))
            }
        }
        repo.registerPushToken(token)
        registeredFor=account
        return true
    }

    fun ensureEventually(context:Context,repo:CloudAccountRepository){
        scope.launch{runCatching{ensureRegistered(context,repo)}}
    }

    fun registerRefreshedToken(context:Context,token:String){
        val app=context.applicationContext as? DominionApplication ?: return
        scope.launch{
            runCatching{
                app.container.cloudAccount.registerPushToken(token)
                registeredFor=app.container.cloudAccount.savedAccountId()
            }
        }
    }
}

class DominionMessagingService:FirebaseMessagingService(){
    override fun onNewToken(token:String){
        FirebasePush.registerRefreshedToken(applicationContext,token)
    }

    override fun onMessageReceived(message:RemoteMessage){
        val data=message.data
        if(data["assistantMessage"]!="true"&&data["kind"]==null)return
        val kind=data["kind"].orEmpty()
        val manager=applicationContext.getSystemService(NotificationManager::class.java)
        val channel="dominion_cloud_jobs"
        manager.createNotificationChannel(NotificationChannel(channel,"DOMINION 작업·메시지",
            NotificationManager.IMPORTANCE_DEFAULT))
        if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(applicationContext,
                Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        if(!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled())return
        val intent=Intent(applicationContext,MainActivity::class.java).apply{
            flags=Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending=PendingIntent.getActivity(applicationContext,kind.hashCode(),
            intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title=when(kind){
            "agent"->"DOMINION Cloud 작업"
            "messenger"->"DOMINION 메신저"
            else->"DOMINION"
        }
        val body=when(kind){
            "agent"->"서버 작업 상태가 갱신되었습니다. 앱에서 실제 결과를 확인하세요."
            "messenger"->"새 메시지가 도착했습니다."
            else->"새 소식이 도착했습니다."
        }
        val notification=NotificationCompat.Builder(applicationContext,channel)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(body)
            .setContentIntent(pending).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        runCatching{manager.notify(("push:"+kind+":"+message.messageId).hashCode(),notification)}
    }
}
