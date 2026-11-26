package com.dominion.mobile.data.remote

import android.content.Context
import com.dominion.mobile.data.local.LocalStore
import com.dominion.mobile.data.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

/** General conversation only. No tools or agent execution are simulated here. */
class CloudChatSession internal constructor(context: Context, private val accountId: String,
    private val cloud: CloudAccountRepository) {
    private val scope = "cloud_" + MessageDigest.getInstance("SHA-256")
        .digest(accountId.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    private val store = LocalStore(context, scope)
    private val emptyState = AppState(onboarded = true, account = Account(name = "DOMINION Cloud",
        birthday = "", phone = "", email = "", plan = Plan.FREE, jelly = 0, energy = 0,
        energyMax = 0, tickets = 0, credits = 0, inviteCode = "", level = 1, xp = 0))
    private val current = MutableStateFlow(store.loadState() ?: emptyState)
    val state = current.asStateFlow()
    private val gate = Mutex()

    private suspend fun commit(state: AppState) = withContext(NonCancellable) {
        store.saveStateNow(state)
        current.value = state
    }

    suspend fun newConversation(): String {
        check(gate.tryLock()) { "A response is in progress" }
        try {
            val id = UUID.randomUUID().toString()
            commit(current.value.copy(conversations = listOf(Conversation(id = id,
                characterId = "cloud", titleOverride = "Cloud")) + current.value.conversations))
            return id
        } finally { gate.unlock() }
    }

    suspend fun send(conversationId: String, text: String, model: String) {
        val input = text.trim()
        require(input.isNotBlank() && input.length <= 16_000)
        check(gate.tryLock()) { "A response is in progress" }
        val id = UUID.randomUUID().toString()
        var saved = false
        suspend fun update(transform: (Conversation) -> Conversation) {
            val snapshot = current.value
            check(snapshot.conversation(conversationId) != null)
            commit(snapshot.copy(conversations = snapshot.conversations.map {
                if (it.id == conversationId) transform(it) else it
            }))
        }
        try {
            update { c -> c.copy(
                titleOverride = if (c.messages.isEmpty()) input.take(40) else c.titleOverride,
                messages = c.messages + Message(id, conversationId, MessageSender.USER, input,
                    status = MessageStatus.SENDING, modelId = model), updatedAt = System.currentTimeMillis()) }
            saved = true
            val messages = requireNotNull(current.value.conversation(conversationId)).messages
                .filter { it.status == MessageStatus.SENT || it.id == id }
                .filter { it.sender == MessageSender.USER || it.sender == MessageSender.ASSISTANT }
            var remaining = 24_000
            val history = messages.asReversed().take(24).mapNotNull {
                if (remaining <= 0) null else {
                    val body = it.text.takeLast(remaining)
                    remaining -= body.length
                    CloudChatMessage(if (it.sender == MessageSender.USER) "user" else "assistant", body)
                }
            }.asReversed()
            val reply = cloud.completeChat(accountId, model, history)
            update { c -> c.copy(messages = c.messages.map {
                if (it.id == id) it.copy(status = MessageStatus.SENT) else it
            } + Message(UUID.randomUUID().toString(), conversationId, MessageSender.ASSISTANT,
                reply.content, modelId = reply.model), updatedAt = System.currentTimeMillis()) }
        } catch (failure: Exception) {
            if (saved) withContext(NonCancellable) {
                update { c -> c.copy(messages = c.messages.map {
                    if (it.id == id) it.copy(status = MessageStatus.FAILED) else it
                }) }
            }
            if (failure is CancellationException) throw failure
            throw failure
        } finally { gate.unlock() }
    }
}
