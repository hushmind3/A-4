package com.dominion.mobile

import android.content.Context
import com.dominion.mobile.data.DominionRepository
import com.dominion.mobile.data.local.EncryptedRecordStore
import com.dominion.mobile.data.local.EngineConnection
import com.dominion.mobile.data.local.LocalStore
import com.dominion.mobile.data.remote.ApiFactory
import com.dominion.mobile.data.remote.RemoteBackedRepository
import com.dominion.mobile.platform.background.BackgroundJobMonitor

class AppContainer(context: Context) {
    private val app = context.applicationContext
    private val credentials = EncryptedRecordStore(app, "dominion_engine_connection", EngineConnection::class.java)
    val cloudAccount by lazy { com.dominion.mobile.data.remote.CloudAccountRepository(app) }
    @Volatile var engine: EngineConnection? = runCatching {
        credentials.load()?.validated()
    }.getOrNull()
        private set
    private val stores = mutableMapOf<String, LocalStore>()
    private val scopeStore=EncryptedRecordStore(app,"dominion_engine_scopes",com.dominion.mobile.data.local.EngineScopeRegistry::class.java)
    private fun resolveScope(connection:EngineConnection?,legacy:Boolean=false):String{
        if(connection==null)return "unconfigured"
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
            },mobileCloud=if(connection==null)cloudAccount else null) { roomId, jobId ->
            connection?.let { BackgroundJobMonitor.enqueue(app, it, roomId, jobId) }
        }
    }

    /** Call after cancelling the old ViewModel and with no active call. */
    @Synchronized fun applyEngine(connection: EngineConnection?) {
        check(repository.state.value.call == null && !com.dominion.mobile.platform.audio.AssistantVoiceService.state.value.active)
        stores.values.forEach { it.flush() }
        val scope=resolveScope(connection)
        val next = buildRepository(connection,scope)
        if (connection == null) credentials.clear() else credentials.save(connection)
        engine = connection
        activeScope=scope
        repository = next
        com.dominion.mobile.platform.background.EngineSyncMonitor.install(app,connection)
        restoreJobWatches()
        com.dominion.mobile.platform.background.ReminderMonitor.schedule(app,
            reminderScope(), next.state.value.schedules)
    }
}
