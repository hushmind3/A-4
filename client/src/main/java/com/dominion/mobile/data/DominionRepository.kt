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

    suspend fun requestVoiceCall(conversationId: String, video: Boolean = false): com.dominion.mobile.data.remote.CallTokenResponse =
        error("Voice calls require a connected server")

    suspend fun searchMessages(query: String): List<Message> = state.value.conversations
        .flatMap { it.messages }.filter { query.isNotBlank() && it.text.contains(query, ignoreCase = true) }
        .sortedByDescending { it.createdAt }.take(30)

    suspend fun createConversation(title: String, characterId: String): String = error("Execution server not connected")

    fun roomSignals(roomId: String, sessionId: String): kotlinx.coroutines.flow.Flow<Unit> =
        kotlinx.coroutines.flow.emptyFlow()
    suspend fun engineTools(): List<com.dominion.mobile.data.remote.EngineTool> = error("Engine not connected")
    suspend fun roomExecution(roomId: String): com.dominion.mobile.data.remote.RoomExecution = error("Engine not connected")
    suspend fun decideApproval(roomId: String, approval: com.dominion.mobile.data.remote.EngineApproval,
        allow: Boolean): com.dominion.mobile.data.remote.ApprovalResult = error("Engine not connected")
    suspend fun cancelJob(roomId: String, jobId: String): com.dominion.mobile.data.remote.EngineJob = error("Engine not connected")
    suspend fun uploadAttachment(roomId: String, name: String, mimeType: String, bytes: ByteArray):
        com.dominion.mobile.data.remote.EngineAttachment = error("Engine not connected")

    suspend fun downloadAttachment(roomId: String, id: String): ByteArray = error("Engine not connected")

    suspend fun serviceAccounts(): List<com.dominion.mobile.data.remote.ServiceAccount> = error("본체 연결 필요")
    suspend fun serviceConnectors(): List<com.dominion.mobile.data.remote.ServiceConnector> = error("본체 연결 필요")
    suspend fun openServiceLogin(id: String): Unit = error("본체 연결 필요")
    suspend fun disconnectServiceAccount(id: String): Unit = error("본체 연결 필요")
    suspend fun registerServiceConnector(registration: com.dominion.mobile.data.remote.ConnectorRegistration,
        token: String): com.dominion.mobile.data.remote.ServiceConnector = error("본체 연결 필요")
    suspend fun setServiceConnectorEnabled(id: String, enabled: Boolean): Unit = error("본체 연결 필요")
    suspend fun openServiceWebsite(url: String): Unit = error("본체 연결 필요")

    /** True only when the name has been persisted and read back from the engine. */
    suspend fun saveOwnerProfile(name: String, birthday: String): Boolean {
        error("Profile storage unavailable")
    }

    suspend fun refreshMemories(): Unit = error("Engine not connected")
    suspend fun syncMemory(id: String): Unit = error("Engine not connected")
    suspend fun deleteMemory(id: String): Unit = error("Memory storage unavailable")
    suspend fun renameRoom(id: String, title: String): Unit = error("Engine not connected")

    suspend fun submitTask(id: String): Unit = error("Engine not connected")
    suspend fun refreshTask(id: String): Unit = error("Engine not connected")

    suspend fun changeSchedules(transform: (List<com.dominion.mobile.data.model.ScheduleItem>) ->
        List<com.dominion.mobile.data.model.ScheduleItem>): Unit = error("Reminder storage unavailable")

    fun update(transform: (AppState) -> AppState)
    suspend fun refresh() = Unit
    fun reset()
}
