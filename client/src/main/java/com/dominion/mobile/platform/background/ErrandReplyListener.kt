package com.dominion.mobile.platform.background

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.dominion.mobile.DominionApplication
import com.dominion.mobile.data.KakaoMessageRequest
import kotlinx.coroutines.*

/** Opt-in Android notification listener. No history scan, no unrelated app
 * processing, no cloud transmission and no automatic message sending. */
class ErrandReplyListener:NotificationListenerService(){
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    override fun onNotificationPosted(sbn:StatusBarNotification?){
        val event=sbn?:return
        val pkg=event.packageName
        if(pkg !in KakaoMessageRequest.apps)return
        if(event.notification.flags and Notification.FLAG_GROUP_SUMMARY!=0)return
        val container=(application as DominionApplication).container
        val account=container.cloudAccount.savedAccountId()?:return
        val repo=container.repository
        if(!repo.localExecution||repo.state.value.tasks.none{it.phonePackage==pkg&&
                it.replyWatchUntil?.let{until->until>=System.currentTimeMillis()}==true})return
        val extras=event.notification.extras?:return
        val title=extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()?:return
        val body=extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()?:return
        if(title.length>100||body.length>500||title.isBlank()||body.isBlank())return
        scope.launch{
            if(container.repository!==repo||container.cloudAccount.savedAccountId()!=account)return@launch
            try{repo.recordMessageReply(pkg,title,body,event.postTime)}
            catch(e:CancellationException){throw e}
            catch(_:Exception){}
        }
    }
    override fun onDestroy(){scope.cancel();super.onDestroy()}
}
