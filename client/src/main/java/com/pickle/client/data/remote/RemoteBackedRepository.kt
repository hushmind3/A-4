package com.pickle.client.data.remote

import com.pickle.client.data.DemoData
import com.pickle.client.data.PickleRepository
import com.pickle.client.data.local.LocalStore
import com.pickle.client.data.model.AppState
import com.pickle.client.data.model.Message
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class RemoteBackedRepository(
    private val api: PickleApi,
    private val store: LocalStore
) : PickleRepository {

    private val _state = MutableStateFlow((store.loadState() ?: DemoData.initial(onboarded = true)).copy(onboarded = true))
    override val state: StateFlow<AppState> = _state.asStateFlow()

    override fun update(transform: (AppState) -> AppState) {
        _state.update(transform)
        store.saveState(_state.value)
    }

    override suspend fun refresh() {
        val account = runCatching { api.me().data?.toDomain() }.getOrNull()
        val characters = runCatching { api.characters().data.map { it.toDomain() } }.getOrNull()
        val conversations = runCatching { api.conversations().data.map { it.toDomain() } }.getOrNull()
        val tasks = runCatching { api.tasks().data.map { it.toDomain() } }.getOrNull()
        val schedules = runCatching { api.schedules().data.map { it.toDomain() } }.getOrNull()
        val memories = runCatching { api.memories().data.map { it.toDomain() } }.getOrNull()
        val friends = runCatching { api.friends().data.map { it.toDomain() } }.getOrNull()
        val connections = runCatching { api.connections().data.map { it.toDomain() } }.getOrNull()
        val explore = runCatching { api.explore().data.map { it.toDomain() } }.getOrNull()
        val vault = runCatching { api.vault().data.map { it.toDomain() } }.getOrNull()

        _state.update { current ->
            current.copy(
                account = account ?: current.account,
                characters = characters ?: current.characters,
                conversations = conversations?.map { serverConv ->
                    val existing = current.conversation(serverConv.id)
                    if (serverConv.messages.isEmpty() && existing != null) {
                        serverConv.copy(messages = existing.messages, updatedAt = maxOf(serverConv.updatedAt, existing.updatedAt))
                    } else {
                        serverConv
                    }
                } ?: current.conversations,
                tasks = tasks ?: current.tasks,
                schedules = schedules ?: current.schedules,
                memories = memories ?: current.memories,
                friends = friends ?: current.friends,
                connections = connections ?: current.connections,
                explore = explore ?: current.explore,
                vault = vault ?: current.vault
            )
        }
        store.saveState(_state.value)
    }

    override suspend fun sendRemote(conversationId: String, text: String): List<Message> {
        val beforeCount = _state.value.conversation(conversationId)?.messages?.size ?: 0
        runCatching { api.sendMessage(conversationId, SendMessageRequest(text)) }
        val deadline = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < deadline) {
            delay(1000)
            val rows = runCatching { api.messages(conversationId).data.map { it.toDomain() } }.getOrNull()
            if (rows != null && rows.size > beforeCount) {
                _state.update { s ->
                    s.copy(conversations = s.conversations.map { conv ->
                        if (conv.id == conversationId) conv.copy(messages = rows, updatedAt = System.currentTimeMillis()) else conv
                    })
                }
                store.saveState(_state.value)
                return rows.drop(beforeCount).filter { msg -> msg.sender == com.pickle.client.data.model.MessageSender.CHARACTER }
            }
        }
        return emptyList()
    }

    override fun reset() {
        store.clearState()
        _state.value = DemoData.initial(onboarded = true)
    }
}