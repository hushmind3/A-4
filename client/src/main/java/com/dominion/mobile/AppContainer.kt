package com.dominion.mobile

import android.content.Context
import com.dominion.mobile.data.DominionRepository
import com.dominion.mobile.data.local.EncryptedRecordStore
import com.dominion.mobile.data.local.EngineConnection
import com.dominion.mobile.data.local.LocalStore
import com.dominion.mobile.data.remote.ApiFactory
import com.dominion.mobile.data.remote.RemoteBackedRepository
import com.dominion.mobile.platform.background.BackgroundJobMonitor
import java.security.MessageDigest

class AppContainer(context: Context) {
    private val app = context.applicationContext
    private val credentials = EncryptedRecordStore(app, "dominion_engine_connection", EngineConnection::class.java)
    val cloudAccount by lazy { com.dominion.mobile.data.remote.CloudAccountRepository(app) }
    /** PC is an optional execution target. Keep saved credentials but never
     * require a PC to start the primary mobile assistant. */
    @Volatile var rememberedEngine:EngineConnection?=runCatching{credentials.load()?.validated()}.getOrNull()
        private set
    @Volatile var engine: EngineConnection? = null
        private set
    private val stores = mutableMapOf<String, LocalStore>()
    private val scopeStore=EncryptedRecordStore(app,"dominion_engine_scopes",com.dominion.mobile.data.local.EngineScopeRegistry::class.java)
    private fun mobileScope(id:String?):String =
        id?.takeIf{it.isNotBlank()}?.let{account->
            val digest=MessageDigest.getInstance("SHA-256").digest(account.toByteArray(Charsets.UTF_8))
                .joinToString(""){"%02x".format(it.toInt() and 255)}
            "mobile-account-"+digest.take(24)
        }?:"unconfigured"
    private fun resolveScope(connection:EngineConnection?,legacy:Boolean=false):String{
        if(connection==null)return mobileScope(cloudAccount.savedAccountId())
        val registry=scopeStore.load()?:com.dominion.mobile.data.local.EngineScopeRegistry()
        val (next,scope)=registry.resolve(connection,legacy)
        if(next!=registry)scopeStore.save(next)
        return scope
    }
    @Volatile private var activeScope=resolveScope(engine,legacy=true)
    @Volatile var repository: DominionRepository = buildRepository(engine,activeScope)
        private set

    private fun restoreJobWatches() {
        val connection = engine ?: return
        com.dominion.mobile.data.remote.pendingJobWatches(repository.state.value).forEach { watch ->
            BackgroundJobMonitor.enqueue(app, connection, watch.roomId, watch.jobId)
        }
    }

    init {
        restoreJobWatches()
        com.dominion.mobile.platform.background.EngineSyncMonitor.install(app,engine)
        val cloudForJobs=if(engine==null)cloudAccount.savedAccountId() else null
        com.dominion.mobile.platform.background.CloudJobMonitor.install(app,cloudForJobs)
        com.dominion.mobile.platform.background.ReminderMonitor.install(app)
        com.dominion.mobile.platform.background.ReminderMonitor.schedule(app,
            reminderScope(), repository.state.value.schedules)
    }

    fun reminderScope(): String = activeScope

    private fun buildRepository(connection: EngineConnection?,scope:String): DominionRepository {
        val store = stores.getOrPut(scope) { LocalStore(app, scope) }
        return RemoteBackedRepository(connection?.let(ApiFactory::create), store,
            onRemindersChanged = { items ->
                com.dominion.mobile.platform.background.ReminderMonitor.schedule(app, scope, items)
            },mobileCloud=if(connection==null)cloudAccount else null,mobileContext=app) { roomId, jobId ->
            connection?.let { BackgroundJobMonitor.enqueue(app, it, roomId, jobId) }
        }
    }

    /**
     * Account changes swap the complete local database, encrypted journal and reminder scope.
     * The Activity must recreate its ViewModel after a true result so no stale repository is shown.
     * Unsigned-in legacy data remains in the separate "unconfigured" store, never copied
     * into an arbitrary Google account.
     */
    @Synchronized fun switchCloudAccount(accountId:String?):Boolean {
        if(accountId != cloudAccount.savedAccountId() || engine!=null)return false
        val newScope=mobileScope(accountId)
        if(newScope==activeScope)return false
        check(repository.state.value.call==null &&
            !com.dominion.mobile.platform.audio.AssistantVoiceService.state.value.active)
        stores.values.forEach{it.flush()}
        val next=buildRepository(null,newScope)
        activeScope=newScope
        repository=next
        com.dominion.mobile.platform.background.CloudJobMonitor.install(app,accountId)
        com.dominion.mobile.platform.background.ReminderMonitor.schedule(
            app,newScope,next.state.value.schedules)
        return true
    }

    /** Call after cancelling the old ViewModel and with no active call. */
    @Synchronized fun applyEngine(connection: EngineConnection?) {
        check(repository.state.value.call == null && !com.dominion.mobile.platform.audio.AssistantVoiceService.state.value.active)
        stores.values.forEach { it.flush() }
        val scope=resolveScope(connection)
        val next = buildRepository(connection,scope)
        if (connection == null) credentials.clear() else credentials.save(connection)
        rememberedEngine=connection
        engine = connection
        activeScope=scope
        repository = next
        com.dominion.mobile.platform.background.EngineSyncMonitor.install(app,connection)
        com.dominion.mobile.platform.background.CloudJobMonitor.install(app,
            if(connection==null)cloudAccount.savedAccountId() else null)
        restoreJobWatches()
        com.dominion.mobile.platform.background.ReminderMonitor.schedule(app,
            reminderScope(), next.state.value.schedules)
    }
}
