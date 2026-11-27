package com.dominion.mobile.platform.background

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.work.*
import com.dominion.mobile.DominionApplication
import com.dominion.mobile.data.local.EngineConnection
import com.dominion.mobile.data.remote.pendingJobWatches
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import java.util.concurrent.TimeUnit

/** WorkManager survives ordinary process death and reboot. No model or job is
 * submitted here: this only reads server-owned conversation and work state. */
object EngineSyncMonitor {
    private const val NAME="dominion-engine-sync"
    fun install(context:Context,engine:EngineConnection?) {
        val manager=WorkManager.getInstance(context)
        if(engine==null){manager.cancelUniqueWork(NAME);return}
        val request=PeriodicWorkRequestBuilder<EngineSyncWorker>(15,TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf("credential" to NotificationReplyReceiver.credentialKey(engine)))
            .build()
        manager.enqueueUniquePeriodicWork(NAME,ExistingPeriodicWorkPolicy.UPDATE,request)
    }
}
class EngineSyncWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val container=(applicationContext as DominionApplication).container
        val engine=container.engine ?: return Result.success()
        val repo=container.repository
        if(inputData.getString("credential")!=NotificationReplyReceiver.credentialKey(engine))return Result.success()
        try{
            repo.synchronizeEngine()
            if(container.engine!==engine || container.repository!==repo)return Result.success()
            pendingJobWatches(repo.state.value).forEach{watch->
                BackgroundJobMonitor.enqueue(applicationContext,engine,watch.roomId,watch.jobId)
            }
        }catch(e:CancellationException){throw e}
        catch(_:Exception){return Result.retry()}
        return Result.success()
    }
}
fun networkRecoverySignals(context:Context)=callbackFlow<Unit>{
    val manager=context.applicationContext.getSystemService(ConnectivityManager::class.java)
    val callback=object:ConnectivityManager.NetworkCallback(){
        override fun onAvailable(network:Network){trySend(Unit)}
        override fun onCapabilitiesChanged(network:Network,caps:NetworkCapabilities){
            if(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))trySend(Unit)
        }
    }
    try{manager.registerDefaultNetworkCallback(callback)}
    catch(_:Exception){close();return@callbackFlow}
    awaitClose{runCatching{manager.unregisterNetworkCallback(callback)}}
}.conflate()
