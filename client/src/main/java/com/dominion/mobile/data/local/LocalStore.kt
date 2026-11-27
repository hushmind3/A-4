package com.dominion.mobile.data.local

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import com.dominion.mobile.data.model.*
import com.google.gson.GsonBuilder
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** One database per execution origin. Secrets stay outside the conversation database. */
class LocalStore(context: Context, scope: String = "unconfigured") {
    private val prefs = context.getSharedPreferences("dominion_mobile_$scope", Context.MODE_PRIVATE)
    private val gson = GsonBuilder().serializeNulls().create()
    private val vaultCipher = VaultRecordCipher(scope)
    private val mobileOperations = EncryptedRecordStore(context.applicationContext,
        "dominion_mobile_operations_$scope", com.dominion.mobile.data.remote.MobileJournal::class.java)
    internal suspend fun loadMobileJournal(): com.dominion.mobile.data.remote.MobileJournal =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            mobileOperations.load() ?: com.dominion.mobile.data.remote.MobileJournal()
        }
    internal suspend fun saveMobileJournal(value: com.dominion.mobile.data.remote.MobileJournal) =
        withContext(kotlinx.coroutines.Dispatchers.IO) { mobileOperations.save(value) }
    internal val offlineArchive = OfflineArchiveStore(context.applicationContext, scope)
    internal val deviceAttachments = DeviceAttachmentStore(context.applicationContext, scope)
    private val db = Room.databaseBuilder(context.applicationContext, MobileDatabase::class.java,
        "dominion_mobile_$scope.db").setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING).build()
    private val dao = db.local()
    private val writer = Executors.newSingleThreadExecutor()
    private val writerDispatcher = writer.asCoroutineDispatcher()
    private data class Write(val state: AppState?)
    private val pending = AtomicReference<Write?>(null)
    private val scheduled = AtomicBoolean(false)
    private var persisted: Rows? = null

    private data class Rows(val metadata: MetadataRow, val conversations: List<ConversationRow>,
        val messages: List<MessageRow>, val records: List<RecordRow>)

    private fun clean(state: AppState) = state.copy(call = null, typingConversationId = null, vaultUnlocked = false)

    /** Bind each ciphertext to its account, record kind and stable row identity. */
    private fun plaintext(id:String,value:String):String =
        if(vaultCipher.isSealed(value))vaultCipher.open(id,value) else value
    private fun ciphertext(id:String,json:String,previous:String?):String =
        if(previous!=null && vaultCipher.isSealed(previous) &&
            vaultCipher.open(id,previous)==json)previous else vaultCipher.seal(id,json)

    private fun rows(state: AppState): Rows {
        val s = clean(state)
        val records = mutableListOf<RecordRow>()
        val beforeRecords=persisted?.records.orEmpty().associateBy{it.kind to it.id}
        val beforeMessages=persisted?.messages.orEmpty().associateBy{it.conversationId to it.id}
        val beforeConversations=persisted?.conversations.orEmpty().associateBy{it.id}
        fun <T> add(kind: String, values: List<T>, id: (T) -> String) {
            values.forEachIndexed { index, value ->
                val key=id(value)
                val payload=ciphertext("record:"+kind+":"+key,gson.toJson(value),
                    beforeRecords[kind to key]?.payload)
                records+=RecordRow(kind,key,index,payload)
            }
        }
        add("task", s.tasks, TaskItem::id)
        add("schedule", s.schedules, ScheduleItem::id)
        add("memory", s.memories, MemoryItem::id)
        val previousVault = persisted?.records.orEmpty().filter { it.kind == "vault" }.associateBy { it.id }
        s.vault.forEachIndexed { index, item ->
            val json = gson.toJson(item)
            val prior = previousVault[item.id]?.payload
            val sealed = if (prior != null && vaultCipher.isSealed(prior) &&
                vaultCipher.open(item.id, prior) == json) prior else vaultCipher.seal(item.id, json)
            records += RecordRow("vault", item.id, index, sealed)
        }
        add("archive", s.archive, ArchiveItem::id)
        add("activity", s.activity, ActivityItem::id)
        val metadata = s.copy(conversations = emptyList(), tasks = emptyList(), schedules = emptyList(),
            memories = emptyList(), vault = emptyList(), archive = emptyList(), activity = emptyList())
        return Rows(MetadataRow(payload=ciphertext("metadata",gson.toJson(metadata),
                persisted?.metadata?.payload)),
            s.conversations.mapIndexed { index, c ->
                ConversationRow(c.id,index,c.updatedAt,"",ciphertext("conversation:"+c.id,
                    gson.toJson(c.copy(messages=emptyList())),
                    beforeConversations[c.id]?.payload))
            },
            s.conversations.flatMap { c -> c.messages.mapIndexed { index, m ->
                MessageRow(c.id,m.id,index,m.createdAt,"",
                    ciphertext("message:"+c.id+":"+m.id,gson.toJson(m),
                        beforeMessages[c.id to m.id]?.payload))
            } }, records)
    }

    /** Called during app initialization on Dispatchers.IO, before UI can mutate state. */
    fun loadState(): AppState? {
        val saved = db.runInTransaction<Rows?> {
            dao.metadata()?.let { Rows(it, dao.conversations(), dao.messages(), dao.records()) }
        }
        if (saved == null) {
            val legacy = prefs.getString("state_v1", null) ?: return null
            val state = clean(requireNotNull(gson.fromJson(legacy, AppState::class.java)))
            persist(state)
            // Remove the old snapshot only after the database transaction commits.
            prefs.edit().remove("state_v1").commit()
            return recoverInterrupted(state)
        }
        persisted = saved
        val grouped = saved.messages.groupBy { it.conversationId }
        fun <T> records(kind: String, type: Class<T>): List<T> =
            saved.records.filter { it.kind == kind }.map {
                val key=if(kind=="vault")it.id else "record:"+kind+":"+it.id
                gson.fromJson(plaintext(key,it.payload),type)
            }
        val meta = gson.fromJson(plaintext("metadata",saved.metadata.payload), AppState::class.java)
        val recovered = recoverInterrupted(clean(meta.copy(
            conversations = saved.conversations.map { row ->
                gson.fromJson(plaintext("conversation:"+row.id,row.payload),
                    Conversation::class.java).copy(messages =
                    grouped[row.id].orEmpty().map {
                        gson.fromJson(plaintext("message:"+row.id+":"+it.id,it.payload),
                            Message::class.java)
                    })
            },
            tasks = records("task", TaskItem::class.java), schedules = records("schedule", ScheduleItem::class.java),
            memories = records("memory", MemoryItem::class.java), vault = records("vault", VaultItem::class.java),
            archive = records("archive", ArchiveItem::class.java), activity = records("activity", ActivityItem::class.java)
        )))
        // Migrate legacy unencrypted rows in one transaction. Ciphertext from each
        // committed write stays stable for unchanged records.
        if (!vaultCipher.isSealed(saved.metadata.payload) ||
            saved.conversations.any{!vaultCipher.isSealed(it.payload)||it.title.isNotEmpty()} ||
            saved.messages.any{!vaultCipher.isSealed(it.payload)||it.body.isNotEmpty()} ||
            saved.records.any{!vaultCipher.isSealed(it.payload)}) {
            persist(recovered)
            // Reclaim plaintext pages left by SQLite/WAL updates after migration.
            val database=db.openHelper.writableDatabase
            database.query("PRAGMA wal_checkpoint(TRUNCATE)").use{}
            database.execSQL("VACUUM")
        }
        return recovered
    }

    private fun recoverInterrupted(state: AppState): AppState = state.copy(conversations = state.conversations.map { c ->
        c.copy(messages = c.messages.map {
            if(it.status==MessageStatus.SENDING)it.copy(status=MessageStatus.FAILED,
                jobStatus=if(it.jobId?.startsWith("mobile:")==true)"interrupted" else it.jobStatus)
            else it
        })
    })

    fun saveState(state: AppState) { enqueue(Write(clean(state))) }
    fun clearState() {
        offlineArchive.clear()
        enqueue(Write(null))
    }

    private fun enqueue(write: Write) {
        pending.set(write)
        scheduleDrain()
    }

    private fun scheduleDrain() {
        if (!scheduled.compareAndSet(false, true)) return
        writer.execute {
            try {
                while (true) {
                    val next = pending.getAndSet(null) ?: break
                    try { persist(next.state) }
                    catch (failure: Exception) { Log.e("DominionDatabase", "Local database write failed", failure) }
                }
            } finally {
                scheduled.set(false)
                if (pending.get() != null) scheduleDrain()
            }
        }
    }

    private fun <T, K> changes(old: List<T>, new: List<T>, key: (T) -> K): Pair<List<T>, List<T>> {
        val before = old.associateBy(key)
        val after = new.associateBy(key)
        return old.filter { key(it) !in after } to new.filter { before[key(it)] != it }
    }

    private fun persist(state: AppState?) {
        if (state == null) {
            db.runInTransaction {
                dao.clearConversations()
                dao.clearRecords()
                dao.clearMetadata()
            }
            persisted = null
            prefs.edit().remove("state_v1").commit()
            return
        }
        val next = rows(state)
        val old = persisted
        val (removedChats, changedChats) = changes(old?.conversations.orEmpty(), next.conversations, ConversationRow::id)
        val (removedMessages, changedMessages) = changes(old?.messages.orEmpty(), next.messages) { it.conversationId to it.id }
        val (removedRecords, changedRecords) = changes(old?.records.orEmpty(), next.records) { it.kind to it.id }
        db.runInTransaction {
            if (removedMessages.isNotEmpty()) dao.deleteMessages(removedMessages)
            if (removedChats.isNotEmpty()) dao.deleteConversations(removedChats)
            if (removedRecords.isNotEmpty()) dao.deleteRecords(removedRecords)
            if (changedChats.isNotEmpty()) dao.putConversations(changedChats)
            if (changedMessages.isNotEmpty()) dao.putMessages(changedMessages)
            if (changedRecords.isNotEmpty()) dao.putRecords(changedRecords)
            if (old?.metadata != next.metadata) dao.putMetadata(next.metadata)
        }
        persisted = next
    }

    suspend fun saveStateNow(state: AppState) = withContext(writerDispatcher) {
        pending.getAndSet(null)?.let { persist(it.state) }
        persist(clean(state))
    }

    fun flush() { writer.submit { pending.getAndSet(null)?.let { persist(it.state) } }.get() }

    suspend fun searchMessages(query: String): List<Message> = withContext(writerDispatcher) {
        pending.getAndSet(null)?.let { persist(it.state) }
        if(query.isBlank())emptyList() else dao.messages().map { row ->
            gson.fromJson(plaintext("message:"+row.conversationId+":"+row.id,row.payload),
                Message::class.java).copy(conversationId=row.conversationId)
        }.filter{it.text.contains(query.trim(),ignoreCase=true)}
            .sortedByDescending{it.createdAt}.take(30)
    }

}
