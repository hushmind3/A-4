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

    private fun rows(state: AppState): Rows {
        val s = clean(state)
        val records = mutableListOf<RecordRow>()
        fun <T> add(kind: String, values: List<T>, id: (T) -> String) {
            values.forEachIndexed { index, value -> records += RecordRow(kind, id(value), index, gson.toJson(value)) }
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
        return Rows(MetadataRow(payload = gson.toJson(metadata)),
            s.conversations.mapIndexed { index, c ->
                ConversationRow(c.id, index, c.updatedAt, c.title(s.characters), gson.toJson(c.copy(messages = emptyList())))
            },
            s.conversations.flatMap { c -> c.messages.mapIndexed { index, m ->
                MessageRow(c.id, m.id, index, m.createdAt, m.text, gson.toJson(m))
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
                val payload = if (kind == "vault" && vaultCipher.isSealed(it.payload))
                    vaultCipher.open(it.id, it.payload) else it.payload
                gson.fromJson(payload, type)
            }
        val meta = gson.fromJson(saved.metadata.payload, AppState::class.java)
        val recovered = recoverInterrupted(clean(meta.copy(
            conversations = saved.conversations.map { row ->
                gson.fromJson(row.payload, Conversation::class.java).copy(messages =
                    grouped[row.id].orEmpty().map { gson.fromJson(it.payload, Message::class.java) })
            },
            tasks = records("task", TaskItem::class.java), schedules = records("schedule", ScheduleItem::class.java),
            memories = records("memory", MemoryItem::class.java), vault = records("vault", VaultItem::class.java),
            archive = records("archive", ArchiveItem::class.java), activity = records("activity", ActivityItem::class.java)
        )))
        // Rewrite legacy plaintext vault records before exposing them to the UI.
        // Existing records are retained; a failed migration aborts loading.
        if (saved.records.any { it.kind == "vault" && !vaultCipher.isSealed(it.payload) }) persist(recovered)
        return recovered
    }

    private fun recoverInterrupted(state: AppState): AppState = state.copy(conversations = state.conversations.map { c ->
        c.copy(messages = c.messages.map { if (it.status == MessageStatus.SENDING) it.copy(status = MessageStatus.FAILED) else it })
    })

    fun saveState(state: AppState) { enqueue(Write(clean(state))) }
    fun clearState() { enqueue(Write(null)) }

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
        if (query.isBlank()) emptyList() else dao.searchMessages(query.trim(), 30)
            .map { gson.fromJson(it.payload, Message::class.java).copy(conversationId = it.conversationId) }
    }

}
