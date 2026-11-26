package com.dominion.mobile.data

import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.data.model.Message
import kotlinx.coroutines.flow.StateFlow

/** Repository contract shared by the native UI and execution server. */
enum class ConnectionState { CONNECTING, CONNECTED, OFFLINE, AUTH_REQUIRED, UNAVAILABLE }

interface DominionRepository {
    val state: StateFlow<AppState>
    val connection: StateFlow<ConnectionState>

    /** Acknowledge one outbound message; only the remote implementation performs HTTP. */
    suspend fun deliverMessage(conversationId: String, message: Message): Message = error("Execution server not connected")

    /** Fetch messages after a successful send; null means not supported. */
    suspend fun loadMessages(conversationId: String): List<Message>? = null

    /** Wait for an asynchronous server reply, without sending the message again. */
    suspend fun pollForReply(conversationId: String, knownIds: Set<String>): List<Message> =
        emptyList()

    suspend fun requestVoiceCall(conversationId: String): com.dominion.mobile.data.remote.CallTokenResponse =
        error("Voice calls require a connected server")

    suspend fun searchMessages(query: String): List<Message> = state.value.conversations
        .flatMap { it.messages }.filter { query.isNotBlank() && it.text.contains(query, ignoreCase = true) }
        .sortedByDescending { it.createdAt }.take(30)

    suspend fun createConversation(title: String, characterId: String): String = error("Execution server not connected")

    fun update(transform: (AppState) -> AppState)
    suspend fun refresh() = Unit
    fun reset()
}
