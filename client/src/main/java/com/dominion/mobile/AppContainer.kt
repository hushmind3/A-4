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
        credentials.load()?.let { EngineConnection.parse(it.origin, it.ownerToken) }
    }.getOrNull()
        private set
    private val stores = mutableMapOf<String, LocalStore>()
    @Volatile var repository: DominionRepository = buildRepository(engine)
        private set

    init {
        com.dominion.mobile.platform.background.ReminderMonitor.install(app)
        com.dominion.mobile.platform.background.ReminderMonitor.schedule(app,
            reminderScope(), repository.state.value.schedules)
    }

    fun reminderScope(): String = engine?.origin?.let(BackgroundJobMonitor::originKey) ?: "unconfigured"

    private fun buildRepository(connection: EngineConnection?): DominionRepository {
        val scope = connection?.let {
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(it.origin.toByteArray(Charsets.UTF_8))
                .joinToString("") { b -> "%02x".format(b.toInt() and 255) }
        } ?: "unconfigured"
        val store = stores.getOrPut(scope) { LocalStore(app, scope) }
        return RemoteBackedRepository(connection?.let(ApiFactory::create), store,
            onRemindersChanged = { items ->
                com.dominion.mobile.platform.background.ReminderMonitor.schedule(app, scope, items)
            }) { roomId, jobId ->
            connection?.let { BackgroundJobMonitor.enqueue(app, it.origin, roomId, jobId) }
        }
    }

    /** Call after cancelling the old ViewModel and with no active call. */
    @Synchronized fun applyEngine(connection: EngineConnection?) {
        check(repository.state.value.call == null)
        stores.values.forEach { it.flush() }
        val next = buildRepository(connection)
        if (connection == null) credentials.clear() else credentials.save(connection)
        engine = connection
        repository = next
        com.dominion.mobile.platform.background.ReminderMonitor.schedule(app,
            reminderScope(), next.state.value.schedules)
    }
}
