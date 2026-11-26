package com.pickle.client.data.remote

import com.pickle.client.data.DemoData
import com.pickle.client.data.PickleRepository
import com.pickle.client.data.local.LocalStore
import com.pickle.client.data.model.AppState
import com.pickle.client.data.model.Message
import com.pickle.client.data.model.MessageSender
import kotlinx.coroutines.CancellationException
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

    override suspend fun deliverMessage(conversationId: String, message: Message): Message {
        // Keep the stable client id across retries so cooperating servers can deduplicate.
        val dto = api.sendMessage(
            conversationId,
            SendMessageRequest(text = message.text, client_message_id = message.id)
        ).data
        return dto?.toDomain()?.takeIf { it.id.isNotBlank() }
            ?: message.copy(status = com.pickle.client.data.model.MessageStatus.SENT)
    }

    override suspend fun loadMessages(conversationId: String): List<Message> =
        api.messages(conversationId).data.map { it.toDomain() }

    override suspend fun pollForReply(conversationId: String, knownIds: Set<String>): List<Message> {
        // Progressive polling: roughly one minute in total, no duplicate POST.
        for (waitMs in listOf(1500L, 2500L, 4000L, 6500L, 10000L, 15000L, 20000L)) {
            delay(waitMs)
            val rows = try {
                loadMessages(conversationId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (rows != null && rows.any {
                it.sender == MessageSender.CHARACTER && it.id.isNotBlank() && it.id !in knownIds
            }) return rows
        }
        return emptyList()
    }

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

    override fun reset() {
        store.clearState()
        _state.value = DemoData.initial(onboarded = true)
    }
}